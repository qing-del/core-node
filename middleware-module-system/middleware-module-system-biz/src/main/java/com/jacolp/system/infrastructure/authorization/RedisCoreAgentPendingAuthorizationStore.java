package com.jacolp.system.infrastructure.authorization;

import com.jacolp.system.application.authorization.model.CoreAgentPendingAuthorizationState;
import com.jacolp.system.application.authorization.model.IssuedCoreAgentAuthorizationPendingHandle;
import com.jacolp.system.application.port.out.CoreAgentPendingAuthorizationStore;
import com.jacolp.system.application.authorization.CoreAgentPendingAuthorizationStateCodec;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Repository;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Redis Hash persistence for full, ten-minute CORE AGENT pending authorization state. */
@Repository
public class RedisCoreAgentPendingAuthorizationStore implements CoreAgentPendingAuthorizationStore {

    private static final String KEY_PREFIX = "oauth2:authorize:pending:";

    private final StringRedisTemplate redis;
    private final CoreAgentPendingAuthorizationStateCodec codec;
    private final Clock clock;
    private final DefaultRedisScript<Long> saveScript;

    @Autowired
    public RedisCoreAgentPendingAuthorizationStore(StringRedisTemplate redis) {
        this(redis, new CoreAgentPendingAuthorizationStateCodec(), Clock.systemUTC(), saveScript());
    }

    RedisCoreAgentPendingAuthorizationStore(
            StringRedisTemplate redis,
            CoreAgentPendingAuthorizationStateCodec codec,
            Clock clock,
            DefaultRedisScript<Long> saveScript) {
        this.redis = Objects.requireNonNull(redis, "redis");
        this.codec = Objects.requireNonNull(codec, "codec");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.saveScript = Objects.requireNonNull(saveScript, "saveScript");
    }

    @Override
    public void save(IssuedCoreAgentAuthorizationPendingHandle handle, CoreAgentPendingAuthorizationState state) {
        if (handle == null || state == null || !handle.expiresAt().equals(state.expiresAt())) {
            throw invalid();
        }
        Instant now = clock.instant();
        if (state.issuedAt().isAfter(now)) {
            throw invalid();
        }
        long ttlMillis = ttlMillis(now, state.expiresAt());
        Map<String, String> values = codec.encode(state);
        List<Object> arguments = new ArrayList<>(2 + values.size() * 2);
        arguments.add(Long.toString(ttlMillis));
        arguments.add(handle.rawHandle());
        for (String field : codec.fieldNames()) {
            arguments.add(field);
            arguments.add(values.get(field));
        }
        Long outcome = redis.execute(saveScript, List.of(key(handle.rawHandle())), arguments.toArray());
        if (!Long.valueOf(1L).equals(outcome)) {
            throw new IllegalStateException("Invalid CORE AGENT pending authorization save result");
        }
    }

    @Override
    public Optional<CoreAgentPendingAuthorizationState> find(String rawHandle) {
        rawHandle = IssuedCoreAgentAuthorizationPendingHandle.requireRawHandle(rawHandle);
        Map<Object, Object> entries = redis.opsForHash().entries(key(rawHandle));
        if (entries == null || entries.isEmpty()) {
            return Optional.empty();
        }
        Map<String, String> values = new LinkedHashMap<>();
        for (Map.Entry<Object, Object> entry : entries.entrySet()) {
            if (!(entry.getKey() instanceof String field) || !(entry.getValue() instanceof String value)
                    || values.put(field, value) != null) {
                throw invalid();
            }
        }
        return Optional.of(codec.decode(values));
    }

    @Override
    public void delete(String rawHandle) {
        redis.delete(key(rawHandle));
    }

    static String key(String rawHandle) {
        return KEY_PREFIX + '{' + IssuedCoreAgentAuthorizationPendingHandle.requireRawHandle(rawHandle) + '}';
    }

    private static long ttlMillis(Instant now, Instant expiresAt) {
        try {
            long value = Duration.between(now, expiresAt).toMillis();
            if (value <= 0) {
                throw invalid();
            }
            return value;
        } catch (ArithmeticException exception) {
            throw invalid();
        }
    }

    private static DefaultRedisScript<Long> saveScript() {
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setLocation(new ClassPathResource("lua/core_agent_pending_authorization_save.lua"));
        script.setResultType(Long.class);
        return script;
    }

    private static IllegalArgumentException invalid() {
        return new IllegalArgumentException("Invalid CORE AGENT pending authorization key or data");
    }
}
