package com.jacolp.system.infrastructure.authorization;

import com.jacolp.system.application.authorization.model.EmailLoginCodeState;
import com.jacolp.system.application.port.out.EmailLoginCodeFailureDecision;
import com.jacolp.system.application.port.out.EmailLoginCodeStateStore;
import com.jacolp.system.application.authorization.EmailLoginCodeStateCodec;
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
import java.util.Set;
import java.util.regex.Pattern;

/** Redis Hash adapter for strictly decoded protected email-code state. */
@Repository
public class RedisEmailLoginCodeStateStore implements EmailLoginCodeStateStore {

    private static final Set<String> CLIENTS = Set.of("user", "admin");
    private static final Pattern BCRYPT = Pattern.compile("\\$2[aby]?\\$[0-9]{2}\\$[./A-Za-z0-9]{53}");

    private final StringRedisTemplate redis;
    private final EmailLoginCodeStateCodec codec;
    private final Clock clock;
    private final DefaultRedisScript<Long> replaceScript;
    private final DefaultRedisScript<Long> consumeScript;
    private final DefaultRedisScript<Long> recordFailureScript;

    @Autowired
    public RedisEmailLoginCodeStateStore(StringRedisTemplate redis) {
        this(redis, new EmailLoginCodeStateCodec(), Clock.systemUTC(), replaceScript(), consumeScript(), recordFailureScript());
    }

    RedisEmailLoginCodeStateStore(
            StringRedisTemplate redis,
            EmailLoginCodeStateCodec codec,
            Clock clock,
            DefaultRedisScript<Long> replaceScript,
            DefaultRedisScript<Long> consumeScript,
            DefaultRedisScript<Long> recordFailureScript) {
        this.redis = Objects.requireNonNull(redis, "redis");
        this.codec = Objects.requireNonNull(codec, "codec");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.replaceScript = Objects.requireNonNull(replaceScript, "replaceScript");
        this.consumeScript = Objects.requireNonNull(consumeScript, "consumeScript");
        this.recordFailureScript = Objects.requireNonNull(recordFailureScript, "recordFailureScript");
    }

    @Override
    public Optional<EmailLoginCodeState> find(String clientId, Long userId) {
        String key = key(clientId, userId);
        Map<Object, Object> values = redis.opsForHash().entries(key);
        if (values == null || values.isEmpty()) {
            return Optional.empty();
        }
        Map<String, String> map = new LinkedHashMap<>();
        for (Map.Entry<Object, Object> entry : values.entrySet()) {
            if (!(entry.getKey() instanceof String field) || !(entry.getValue() instanceof String value)) {
                throw invalid();
            }
            map.put(field, value);
        }
        EmailLoginCodeState state = codec.decode(map);
        if (!clientId.equals(state.clientId()) || !userId.equals(state.userId())) {
            throw invalid();
        }
        return Optional.of(state);
    }

    @Override
    public void delete(String clientId, Long userId) {
        redis.delete(key(clientId, userId));
    }

    @Override
    public void replace(EmailLoginCodeState state) {
        Objects.requireNonNull(state, "state");
        Instant now = clock.instant();
        if (state.issuedAt().isAfter(now)) {
            throw invalid();
        }
        long ttlMilliseconds = ttlMilliseconds(now, state.expiresAt());
        Map<String, String> encoded = codec.encode(state);
        List<Object> arguments = new ArrayList<>(1 + encoded.size() * 2);
        arguments.add(Long.toString(ttlMilliseconds));
        for (String fieldName : codec.fieldNames()) {
            arguments.add(fieldName);
            arguments.add(encoded.get(fieldName));
        }
        Long result = redis.execute(
                replaceScript,
                List.of(key(state.clientId(), state.userId())),
                arguments.toArray());
        if (result == null || result != 1L) {
            throw new IllegalStateException("Invalid email-code state replace result");
        }
    }

    @Override
    public boolean consume(String clientId, Long userId, String expectedVerifierHash) {
        if (expectedVerifierHash == null || !BCRYPT.matcher(expectedVerifierHash).matches()) {
            throw invalid();
        }
        Long result = redis.execute(
                consumeScript,
                List.of(key(clientId, userId)),
                expectedVerifierHash);
        if (Long.valueOf(1L).equals(result)) {
            return true;
        }
        if (Long.valueOf(0L).equals(result)) {
            return false;
        }
        throw new IllegalStateException("Invalid email-code state consume result");
    }

    @Override
    public EmailLoginCodeFailureDecision recordFailure(
            String clientId,
            Long userId,
            String expectedVerifierHash,
            Integer maxFailedAttempts) {
        if (expectedVerifierHash == null || !BCRYPT.matcher(expectedVerifierHash).matches()
                || maxFailedAttempts == null || maxFailedAttempts < 1 || maxFailedAttempts > 5) {
            throw invalid();
        }
        Long result = redis.execute(
                recordFailureScript,
                List.of(key(clientId, userId)),
                expectedVerifierHash,
                maxFailedAttempts.toString());
        if (Long.valueOf(1L).equals(result)) {
            return EmailLoginCodeFailureDecision.RECORDED;
        }
        if (Long.valueOf(2L).equals(result)) {
            return EmailLoginCodeFailureDecision.INVALIDATED;
        }
        if (Long.valueOf(0L).equals(result)) {
            return EmailLoginCodeFailureDecision.STALE;
        }
        throw new IllegalStateException("Invalid email-code state failure result");
    }

    private static long ttlMilliseconds(Instant now, Instant expiresAt) {
        try {
            long ttlMilliseconds = Duration.between(now, expiresAt).toMillis();
            if (ttlMilliseconds <= 0) {
                throw invalid();
            }
            return ttlMilliseconds;
        } catch (ArithmeticException exception) {
            throw invalid();
        }
    }

    private static DefaultRedisScript<Long> replaceScript() {
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setLocation(new ClassPathResource("lua/email_login_code_state_replace.lua"));
        script.setResultType(Long.class);
        return script;
    }

    private static DefaultRedisScript<Long> consumeScript() {
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setLocation(new ClassPathResource("lua/email_login_code_state_consume.lua"));
        script.setResultType(Long.class);
        return script;
    }

    private static DefaultRedisScript<Long> recordFailureScript() {
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setLocation(new ClassPathResource("lua/email_login_code_state_record_failure.lua"));
        script.setResultType(Long.class);
        return script;
    }

    private static String key(String clientId, Long userId) {
        if (!CLIENTS.contains(clientId) || userId == null || userId <= 0) {
            throw invalid();
        }
        return "user:email_code:" + clientId + ":" + userId;
    }

    private static IllegalArgumentException invalid() {
        return new IllegalArgumentException("Invalid email-code state key or data");
    }
}
