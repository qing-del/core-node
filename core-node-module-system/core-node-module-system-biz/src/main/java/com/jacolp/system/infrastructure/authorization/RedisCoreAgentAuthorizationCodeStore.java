package com.jacolp.system.infrastructure.authorization;

import com.jacolp.system.application.authorization.model.CoreAgentAuthorizationCodeState;
import com.jacolp.system.application.authorization.model.CoreAgentPendingAuthorizationState;
import com.jacolp.system.application.authorization.model.IssuedCoreAgentAuthorizationCode;
import com.jacolp.system.application.authorization.model.IssuedCoreAgentAuthorizationPendingHandle;
import com.jacolp.system.application.port.out.CoreAgentAuthorizationCodeStore;
import com.jacolp.system.application.port.out.CoreAgentPendingAuthorizationCodeTransitionStore;
import com.jacolp.system.application.authorization.CoreAgentAuthorizationCodeStateCodec;
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

/**
 * Redis Hash implementation of one-time CORE AGENT authorization-code storage.
 *
 * <p>Code hashes use {@code oauth2:auth_code:{rawCode}} and account pointers use
 * {@code user:auth_code:{userId}:{clientId}}. The multi-key Lua scripts assume the existing
 * single-node Redis deployment. A Redis Cluster deployment needs
 * Phase 7 real-Redis validation and a shared hash-tag strategy before this adapter is enabled there.</p>
 */
@Repository
public class RedisCoreAgentAuthorizationCodeStore implements CoreAgentAuthorizationCodeStore,
        CoreAgentPendingAuthorizationCodeTransitionStore {

    private static final String CODE_KEY_PREFIX = "oauth2:auth_code:";
    private static final String USER_POINTER_KEY_PREFIX = "user:auth_code:";

    private final StringRedisTemplate redis;
    private final CoreAgentAuthorizationCodeStateCodec codec;
    private final Clock clock;
    private final DefaultRedisScript<Long> replaceScript;
    private final DefaultRedisScript<Long> consumeScript;
    private final DefaultRedisScript<Long> invalidateScript;
    private final DefaultRedisScript<Long> pendingTransitionScript;

    @Autowired
    public RedisCoreAgentAuthorizationCodeStore(StringRedisTemplate redis) {
        this(redis, new CoreAgentAuthorizationCodeStateCodec(), Clock.systemUTC(), replaceScript(), consumeScript(),
                invalidateScript(), pendingTransitionScript());
    }

    RedisCoreAgentAuthorizationCodeStore(
            StringRedisTemplate redis,
            CoreAgentAuthorizationCodeStateCodec codec,
            Clock clock,
            DefaultRedisScript<Long> replaceScript,
            DefaultRedisScript<Long> consumeScript,
            DefaultRedisScript<Long> invalidateScript,
            DefaultRedisScript<Long> pendingTransitionScript) {
        this.redis = Objects.requireNonNull(redis, "redis");
        this.codec = Objects.requireNonNull(codec, "codec");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.replaceScript = Objects.requireNonNull(replaceScript, "replaceScript");
        this.consumeScript = Objects.requireNonNull(consumeScript, "consumeScript");
        this.invalidateScript = Objects.requireNonNull(invalidateScript, "invalidateScript");
        this.pendingTransitionScript = Objects.requireNonNull(pendingTransitionScript, "pendingTransitionScript");
    }

    @Override
    public IssuedCoreAgentAuthorizationCode replaceCurrent(CoreAgentAuthorizationCodeState state) {
        Objects.requireNonNull(state, "state");
        Instant now = clock.instant();
        if (state.issuedAt().isAfter(now)) {
            throw invalid();
        }
        long ttlMillis = ttlMillis(now, state.expiresAt());
        Map<String, String> values = codec.encode(state);
        List<Object> arguments = new ArrayList<>(4 + values.size() * 2);
        arguments.add(Long.toString(ttlMillis));
        arguments.add(state.rawCode());
        arguments.add(state.accountSnapshot().userId().toString());
        arguments.add(state.clientId());
        for (String field : codec.fieldNames()) {
            arguments.add(field);
            arguments.add(values.get(field));
        }
        Long outcome = redis.execute(replaceScript,
                List.of(codeKey(state.rawCode()), userPointerKey(state.accountSnapshot().userId(), state.clientId())),
                arguments.toArray());
        if (!Long.valueOf(1L).equals(outcome)) {
            throw new IllegalStateException("Invalid CORE AGENT authorization-code replace result");
        }
        return new IssuedCoreAgentAuthorizationCode(state.rawCode(), state.expiresAt());
    }

    @Override
    public Optional<CoreAgentAuthorizationCodeState> findByCode(String rawCode) {
        rawCode = requireRawCode(rawCode);
        Map<Object, Object> entries = redis.opsForHash().entries(codeKey(rawCode));
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
        CoreAgentAuthorizationCodeState state = codec.decode(rawCode, values);
        if (!rawCode.equals(state.rawCode())) {
            throw invalid();
        }
        return Optional.of(state);
    }

    @Override
    public boolean consume(String rawCode, Long expectedUserId, String expectedClientId) {
        rawCode = requireRawCode(rawCode);
        long userId = requireUserId(expectedUserId);
        String clientId = requireClientId(expectedClientId);
        Long outcome = redis.execute(consumeScript, List.of(codeKey(rawCode), userPointerKey(userId, clientId)), rawCode,
                Long.toString(userId), clientId);
        if (Long.valueOf(1L).equals(outcome)) {
            return true;
        }
        if (Long.valueOf(0L).equals(outcome)) {
            return false;
        }
        throw new IllegalStateException("Invalid CORE AGENT authorization-code consume result");
    }

    @Override
    public void invalidateCurrent(Long userId, String clientId) {
        long requiredUserId = requireUserId(userId);
        String requiredClientId = requireClientId(clientId);
        Long outcome = redis.execute(invalidateScript, List.of(userPointerKey(requiredUserId, requiredClientId)),
                Long.toString(requiredUserId), requiredClientId, CODE_KEY_PREFIX);
        if (!Long.valueOf(1L).equals(outcome)) {
            throw new IllegalStateException("Invalid CORE AGENT authorization-code invalidate result");
        }
    }

    static String codeKey(String rawCode) {
        return CODE_KEY_PREFIX + '{' + requireRawCode(rawCode) + '}';
    }

    @Override
    public boolean consumePendingAndStoreCode(
            IssuedCoreAgentAuthorizationPendingHandle pendingHandle,
            CoreAgentPendingAuthorizationState expectedPending,
            CoreAgentAuthorizationCodeState codeState) {
        if (pendingHandle == null || expectedPending == null || codeState == null) {
            throw invalid();
        }
        verifyTransitionBinding(pendingHandle, expectedPending, codeState);
        Instant now = clock.instant();
        if (codeState.issuedAt().isAfter(now)) {
            throw invalid();
        }
        long ttlMillis = ttlMillis(now, codeState.expiresAt());
        Map<String, String> values = codec.encode(codeState);
        List<Object> arguments = new ArrayList<>(6 + values.size() * 2);
        arguments.add(Long.toString(ttlMillis));
        arguments.add(pendingHandle.rawHandle());
        arguments.add(codeState.rawCode());
        arguments.add(expectedPending.clientId());
        arguments.add(Long.toString(expectedPending.authenticatedUserId()));
        arguments.add(expectedPending.sessionId());
        for (String field : codec.fieldNames()) {
            arguments.add(field);
            arguments.add(values.get(field));
        }
        Long outcome = redis.execute(pendingTransitionScript,
                List.of(pendingKey(pendingHandle.rawHandle()), codeKey(codeState.rawCode()),
                        userPointerKey(codeState.accountSnapshot().userId(), codeState.clientId())), arguments.toArray());
        if (Long.valueOf(1L).equals(outcome)) {
            return true;
        }
        if (Long.valueOf(0L).equals(outcome)) {
            return false;
        }
        throw new IllegalStateException("Invalid CORE AGENT pending authorization transition result");
    }

    static String userPointerKey(long userId, String clientId) {
        if (userId <= 0) {
            throw invalid();
        }
        return USER_POINTER_KEY_PREFIX + '{' + userId + "}:{" + requireClientId(clientId) + '}';
    }

    static String pendingKey(String rawHandle) {
        return "oauth2:authorize:pending:{" + IssuedCoreAgentAuthorizationPendingHandle.requireRawHandle(rawHandle) + '}';
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

    private static String requireRawCode(String rawCode) {
        new IssuedCoreAgentAuthorizationCode(rawCode, Instant.EPOCH);
        return rawCode;
    }

    private static long requireUserId(Long userId) {
        if (userId == null || userId <= 0) {
            throw invalid();
        }
        return userId;
    }

    private static String requireClientId(String clientId) {
        if (!CoreAgentAuthorizationCodeState.CORE_AGENT_CLIENT_ID.equals(clientId)) {
            throw invalid();
        }
        return clientId;
    }

    private static void verifyTransitionBinding(
            IssuedCoreAgentAuthorizationPendingHandle pendingHandle,
            CoreAgentPendingAuthorizationState pending,
            CoreAgentAuthorizationCodeState codeState) {
        if (!pendingHandle.expiresAt().equals(pending.expiresAt())
                || !codeState.clientId().equals(pending.clientId())
                || !codeState.redirectUri().equals(pending.redirectUri())
                || !codeState.codeChallenge().equals(pending.codeChallenge())
                || !codeState.codeChallengeMethod().equals(pending.codeChallengeMethod())
                || !codeState.oauthState().equals(pending.oauthState())
                || !codeState.originalSocketAddress().equals(pending.originalSocketAddress())
                || codeState.accountSnapshot().userId() != pending.authenticatedUserId()) {
            throw invalid();
        }
    }

    private static DefaultRedisScript<Long> replaceScript() {
        return script("lua/core_agent_authorization_code_replace.lua");
    }

    private static DefaultRedisScript<Long> consumeScript() {
        return script("lua/core_agent_authorization_code_consume.lua");
    }

    private static DefaultRedisScript<Long> invalidateScript() {
        return script("lua/core_agent_authorization_code_invalidate.lua");
    }

    private static DefaultRedisScript<Long> pendingTransitionScript() {
        return script("lua/core_agent_pending_authorization_to_code.lua");
    }

    private static DefaultRedisScript<Long> script(String resource) {
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setLocation(new ClassPathResource(resource));
        script.setResultType(Long.class);
        return script;
    }

    private static IllegalArgumentException invalid() {
        return new IllegalArgumentException("Invalid CORE AGENT authorization-code key or data");
    }
}
