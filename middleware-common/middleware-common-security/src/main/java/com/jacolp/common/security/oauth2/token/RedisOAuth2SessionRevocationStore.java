package com.jacolp.common.security.oauth2.token;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Redis implementation of atomic access-token and current-session revocation. */
public final class RedisOAuth2SessionRevocationStore implements OAuth2SessionRevocationStore {

    private static final String ACCESS_BLACKLIST_PREFIX = "user:blacklist:access:";
    private static final String SESSION_PREFIX = "user:session:";
    private static final String REFRESH_PREFIX = "user:refresh:";

    private final StringRedisTemplate redis;
    private final Clock clock;
    private final DefaultRedisScript<Long> script;

    @Autowired
    public RedisOAuth2SessionRevocationStore(StringRedisTemplate redis) {
        this(redis, Clock.systemUTC(), revokeScript());
    }

    public RedisOAuth2SessionRevocationStore(StringRedisTemplate redis, Clock clock, DefaultRedisScript<Long> script) {
        this.redis = Objects.requireNonNull(redis, "redis must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
        this.script = Objects.requireNonNull(script, "script must not be null");
    }

    @Override
    public boolean revoke(OAuth2SessionRevocationRequest request) {
        Objects.requireNonNull(request, "request must not be null");
        long ttlMillis = positiveTtlMillis(request.accessExpiresAt());
        List<String> keys = new ArrayList<>(3);
        keys.add(ACCESS_BLACKLIST_PREFIX + request.accessJti());
        keys.add(SESSION_PREFIX + request.clientId() + ':' + request.userId());
        if (request.refreshFingerprint() != null) {
            keys.add(REFRESH_PREFIX + request.refreshFingerprint());
        }
        Long outcome = redis.execute(script, keys, Long.toString(ttlMillis),
                request.refreshFingerprint() == null ? "" : request.refreshFingerprint());
        if (Long.valueOf(1L).equals(outcome)) {
            return true;
        }
        if (Long.valueOf(0L).equals(outcome)) {
            return false;
        }
        throw new IllegalStateException("OAuth2 session revocation failed");
    }

    private long positiveTtlMillis(Instant expiresAt) {
        try {
            long ttlMillis = Duration.between(clock.instant(), expiresAt).toMillis();
            if (ttlMillis <= 0) {
                throw new IllegalArgumentException("OAuth2 session revocation TTL must be positive");
            }
            return ttlMillis;
        } catch (ArithmeticException exception) {
            throw new IllegalArgumentException("OAuth2 session revocation TTL is out of range", exception);
        }
    }

    private static DefaultRedisScript<Long> revokeScript() {
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setLocation(new ClassPathResource("oauth2/token/revoke-current-session.lua"));
        script.setResultType(Long.class);
        return script;
    }
}
