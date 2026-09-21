package com.jacolp.common.security.oauth2.token;

import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.core.io.ClassPathResource;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;

/** Redis adapter for OAuth2 state; refresh rotation is deliberately separate. */
public final class RedisOAuth2TokenStateStore implements OAuth2TokenStateStore {
    private static final Pattern FP = Pattern.compile("[A-Za-z0-9_-]{43}");
    private static final Pattern CLIENT = Pattern.compile("[A-Za-z0-9_-]{1,100}");
    private static final RedisScript<Long> REPLACE_CURRENT_SESSION = replaceCurrentSessionScript();
    private static final RedisScript<Long> ROTATE_CURRENT_SESSION = rotateCurrentSessionScript();

    private final StringRedisTemplate redis;
    private final OAuth2TokenStateCodec codec;
    private final Clock clock;

    public RedisOAuth2TokenStateStore(StringRedisTemplate redis, OAuth2TokenStateCodec codec) {
        this(redis, codec, Clock.systemUTC());
    }

    public RedisOAuth2TokenStateStore(StringRedisTemplate redis, OAuth2TokenStateCodec codec, Clock clock) {
        this.redis = Objects.requireNonNull(redis);
        this.codec = Objects.requireNonNull(codec);
        this.clock = Objects.requireNonNull(clock);
    }

    @Override
    public void replaceCurrentSession(RefreshTokenState refreshState, OAuth2SessionState sessionState) {
        Objects.requireNonNull(refreshState, "refreshState must not be null");
        Objects.requireNonNull(sessionState, "sessionState must not be null");
        validateIssuanceState(refreshState, sessionState);

        long ttlMillis = positiveTtlMillis(refreshState.expiresAt());
        Object[] arguments = stateWriteArguments(ttlMillis, refreshState, sessionState);
        Long outcome = redis.execute(REPLACE_CURRENT_SESSION,
                List.of(refreshKey(refreshState.fingerprint()), sessionKey(sessionState.clientId(), sessionState.userId())),
                arguments);
        if (!Long.valueOf(1L).equals(outcome)) {
            throw new IllegalStateException("OAuth2 session replacement failed");
        }
    }

    @Override
    public boolean rotate(String expectedOldFingerprint, RefreshTokenState nextRefreshState, OAuth2SessionState nextSessionState) {
        validateFingerprint(expectedOldFingerprint);
        Objects.requireNonNull(nextRefreshState, "nextRefreshState must not be null");
        Objects.requireNonNull(nextSessionState, "nextSessionState must not be null");
        if (expectedOldFingerprint.equals(nextRefreshState.fingerprint())) {
            throw new IllegalArgumentException("next refresh fingerprint must differ from expected old fingerprint");
        }
        validateIssuanceState(nextRefreshState, nextSessionState);

        long ttlMillis = positiveTtlMillis(nextRefreshState.expiresAt());
        Object[] stateArguments = stateWriteArguments(ttlMillis, nextRefreshState, nextSessionState);
        List<Object> arguments = new ArrayList<>(stateArguments.length + 1);
        arguments.add(expectedOldFingerprint);
        arguments.addAll(List.of(stateArguments));
        Long outcome = redis.execute(ROTATE_CURRENT_SESSION,
                List.of(refreshKey(expectedOldFingerprint), refreshKey(nextRefreshState.fingerprint()),
                        sessionKey(nextSessionState.clientId(), nextSessionState.userId())),
                arguments.toArray());
        if (Long.valueOf(1L).equals(outcome)) return true;
        if (Long.valueOf(0L).equals(outcome)) return false;
        throw new IllegalStateException("OAuth2 refresh rotation failed");
    }

    @Override
    public Optional<RefreshTokenState> findRefreshByFingerprint(String fingerprint) {
        validateFingerprint(fingerprint);
        Map<String, String> values = entries(refreshKey(fingerprint));
        if (values.isEmpty()) return Optional.empty();
        RefreshTokenState state = codec.decodeRefresh(values);
        if (!fingerprint.equals(state.fingerprint())) throw new IllegalArgumentException("refresh state key mismatch");
        return Optional.of(state);
    }

    @Override
    public Optional<OAuth2SessionState> findSession(String clientId, long userId) {
        validateSessionInput(clientId, userId);
        Map<String, String> values = entries(sessionKey(clientId, userId));
        if (values.isEmpty()) return Optional.empty();
        OAuth2SessionState state = codec.decodeSession(values);
        if (!clientId.equals(state.clientId()) || userId != state.userId()) throw new IllegalArgumentException("session state key mismatch");
        return Optional.of(state);
    }

    @Override public void deleteRefresh(String fingerprint) { validateFingerprint(fingerprint); redis.delete(refreshKey(fingerprint)); }
    @Override public void deleteSession(String clientId, long userId) { validateSessionInput(clientId, userId); redis.delete(sessionKey(clientId, userId)); }

    private static RedisScript<Long> replaceCurrentSessionScript() {
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setLocation(new ClassPathResource("oauth2/token/replace-current-session.lua"));
        script.setResultType(Long.class);
        return script;
    }

    private static RedisScript<Long> rotateCurrentSessionScript() {
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setLocation(new ClassPathResource("oauth2/token/rotate-current-session.lua"));
        script.setResultType(Long.class);
        return script;
    }

    private void validateIssuanceState(RefreshTokenState refreshState, OAuth2SessionState sessionState) {
        if (refreshState.userId() != sessionState.userId()
                || !refreshState.clientId().equals(sessionState.clientId())
                || !refreshState.fingerprint().equals(sessionState.currentRefreshFingerprint())
                || !refreshState.expiresAt().equals(sessionState.refreshExpiresAt())) {
            throw new IllegalArgumentException("refresh and session state must describe the same client-user refresh session");
        }
        if (sessionState.accessExpiresAt().isAfter(refreshState.expiresAt())) {
            throw new IllegalArgumentException("access expiry must not exceed refresh expiry");
        }
    }

    private long positiveTtlMillis(Instant expiresAt) {
        long ttlMillis = Duration.between(clock.instant(), expiresAt).toMillis();
        if (ttlMillis <= 0) throw new IllegalArgumentException("refresh and session TTL must be positive");
        return ttlMillis;
    }

    private Object[] stateWriteArguments(long ttlMillis, RefreshTokenState refreshState, OAuth2SessionState sessionState) {
        Map<String, String> refreshValues = codec.encode(refreshState);
        Map<String, String> sessionValues = codec.encode(sessionState);
        List<Object> arguments = new ArrayList<>(2 + refreshValues.size() * 2 + sessionValues.size() * 2);
        appendHashArguments(arguments, ttlMillis, codec.refreshFieldNames(), refreshValues);
        appendHashArguments(arguments, ttlMillis, codec.sessionFieldNames(), sessionValues);
        return arguments.toArray();
    }

    private static void appendHashArguments(List<Object> arguments, long ttlMillis, List<String> fields, Map<String, String> values) {
        arguments.add(Long.toString(ttlMillis));
        for (String field : fields) {
            arguments.add(field);
            arguments.add(values.get(field));
        }
    }

    private Map<String, String> entries(String key) {
        HashOperations<String, Object, Object> hash = redis.opsForHash();
        Map<Object, Object> source = hash.entries(key);
        Map<String, String> result = new LinkedHashMap<>();
        source.forEach((field, value) -> {
            if (!(field instanceof String) || !(value instanceof String)) throw new IllegalArgumentException("invalid OAuth2 token state hash");
            result.put((String) field, (String) value);
        });
        return result;
    }

    private static String refreshKey(String fingerprint) { return "user:refresh:" + fingerprint; }
    private static String sessionKey(String clientId, long userId) { return "user:session:" + clientId + ":" + userId; }
    private static void validateFingerprint(String value) { if (value == null || !FP.matcher(value).matches()) throw new IllegalArgumentException("fingerprint must be Base64URL"); }
    private static void validateSessionInput(String clientId, long userId) { if (userId <= 0 || clientId == null || !CLIENT.matcher(clientId).matches()) throw new IllegalArgumentException("invalid session key input"); }
}
