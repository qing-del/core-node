package com.jacolp.common.security.oauth2.token;

import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.regex.Pattern;

/** Redis implementation that records only a fixed revocation marker for a JTI. */
public final class RedisAccessTokenBlacklistStore implements AccessTokenBlacklistStore {

    private static final Pattern JTI_PATTERN = Pattern.compile("[A-Za-z0-9_-]{22}");
    private static final String KEY_PREFIX = "user:blacklist:access:";
    private static final String BLACKLIST_MARKER = "1";

    private final StringRedisTemplate redis;
    private final Clock clock;

    public RedisAccessTokenBlacklistStore(StringRedisTemplate redis) {
        this(redis, Clock.systemUTC());
    }

    public RedisAccessTokenBlacklistStore(StringRedisTemplate redis, Clock clock) {
        this.redis = Objects.requireNonNull(redis, "redis must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    @Override
    public void blacklist(String jti, Instant expiresAt) {
        validateJti(jti);
        Objects.requireNonNull(expiresAt, "expiresAt must not be null");
        Duration ttl = Duration.between(clock.instant(), expiresAt);
        if (ttl.isZero() || ttl.isNegative()) {
            return;
        }
        redis.opsForValue().set(key(jti), BLACKLIST_MARKER, ttl);
    }

    @Override
    public boolean isBlacklisted(String jti) {
        validateJti(jti);
        return Boolean.TRUE.equals(redis.hasKey(key(jti)));
    }

    private static String key(String jti) {
        return KEY_PREFIX + jti;
    }

    private static void validateJti(String jti) {
        if (jti == null || !JTI_PATTERN.matcher(jti).matches()) {
            throw new IllegalArgumentException("jti must be a 22-character Base64URL value");
        }
    }
}
