package com.jacolp.system.infrastructure.authorization;

import com.jacolp.system.application.authorization.CoreAgentAuthorizationCodeStateCodec;
import com.jacolp.system.application.authorization.model.CoreAgentAuthorizationAccountSnapshot;
import com.jacolp.system.application.authorization.model.CoreAgentAuthorizationCodeState;
import com.jacolp.system.application.authorization.model.CoreAgentPendingAuthorizationState;
import com.jacolp.system.application.authorization.model.IssuedCoreAgentAuthorizationPendingHandle;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class RedisCoreAgentAuthorizationCodeStoreTest {

    private static final Instant NOW = Instant.parse("2026-08-11T04:00:00Z");
    private static final String RAW_CODE = code((byte) 7);
    private static final String OLD_CODE = code((byte) 8);
    private static final long USER_ID = 7L;

    @Test
    void replaceUsesExactTwoKeysTtlAndFixedCodecFieldsWithoutRawCodeInTheHash() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        DefaultRedisScript<Long> replace = new DefaultRedisScript<>();
        CoreAgentAuthorizationCodeState state = state(RAW_CODE, NOW.minusMillis(100));
        Object[] expectedArguments = replaceArguments(state, "599900");
        when(redis.execute(eq(replace), eq(List.of(codeKey(RAW_CODE), userKey())), eq(expectedArguments))).thenReturn(1L);

        Assertions.assertThat(store(redis, replace, new DefaultRedisScript<>(), new DefaultRedisScript<>(), NOW)
                .replaceCurrent(state).rawCode()).isEqualTo(RAW_CODE);

        verify(redis).execute(eq(replace), eq(List.of(codeKey(RAW_CODE), userKey())), eq(expectedArguments));
        assertThat(expectedArguments).containsExactly("599900", RAW_CODE, "7", "core_agent",
                "schema_version", "1", "client_id", "core_agent", "redirect_uri", "http://127.0.0.1:9090/oauth/callback",
                "scopes", "note:read,sys:read", "code_challenge", code((byte) 9), "code_challenge_method", "S256",
                "original_socket_address", "127.0.0.1", "oauth_state", "opaque-state", "issued_at_epoch_millis",
                Long.toString(NOW.minusMillis(100).toEpochMilli()), "expires_at_epoch_millis",
                Long.toString(NOW.plus(Duration.ofMinutes(10)).minusMillis(100).toEpochMilli()), "user_id", "7",
                "username", "alice", "role_id", "2", "password_hash", "$2a$10$" + "a".repeat(53), "email_present", "0",
                "email", "", "extra_grant_types", "agent_client", "status", "0");
        Assertions.assertThat(new CoreAgentAuthorizationCodeStateCodec().encode(state)).doesNotContainValue(RAW_CODE);
    }

    @Test
    void replaceRejectsExpiredFutureIssuedAndOverflowBeforeRedisAndPropagatesFailures() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        RedisCoreAgentAuthorizationCodeStore store = store(redis, new DefaultRedisScript<>(), new DefaultRedisScript<>(),
                new DefaultRedisScript<>(), NOW);
        assertThatIllegalArgumentException().isThrownBy(() -> store.replaceCurrent(
                state(RAW_CODE, NOW.minus(Duration.ofMinutes(10)))));
        assertThatIllegalArgumentException().isThrownBy(() -> store.replaceCurrent(state(RAW_CODE, NOW.plusMillis(1))));
        RedisCoreAgentAuthorizationCodeStore overflow = store(redis, new DefaultRedisScript<>(), new DefaultRedisScript<>(),
                new DefaultRedisScript<>(), Instant.MIN);
        assertThatIllegalArgumentException().isThrownBy(() -> overflow.replaceCurrent(
                state(RAW_CODE, Instant.MAX.minus(Duration.ofMinutes(10)))));
        verifyNoInteractions(redis);

        DefaultRedisScript<Long> replace = new DefaultRedisScript<>();
        IllegalStateException failure = new IllegalStateException("redis unavailable");
        when(redis.execute(eq(replace), anyList(), any(Object[].class))).thenThrow(failure);
        assertThatThrownBy(() -> store(redis, replace, new DefaultRedisScript<>(), new DefaultRedisScript<>(), NOW)
                .replaceCurrent(state(RAW_CODE, NOW))).isSameAs(failure);
    }

    @Test
    void findRoundTripsTheExactCodeHashAndFailsClosedForPollutionOrNonStrings() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        HashOperations<String, Object, Object> hashes = mock(HashOperations.class);
        when(redis.opsForHash()).thenReturn(hashes);
        CoreAgentAuthorizationCodeState state = state(RAW_CODE, NOW);
        Map<Object, Object> entries = new LinkedHashMap<>(new CoreAgentAuthorizationCodeStateCodec().encode(state));
        when(hashes.entries(codeKey(RAW_CODE))).thenReturn(entries);
        RedisCoreAgentAuthorizationCodeStore store = store(redis, new DefaultRedisScript<>(), new DefaultRedisScript<>(),
                new DefaultRedisScript<>(), NOW);

        Assertions.assertThat(store.findByCode(RAW_CODE)).contains(state);
        verify(hashes).entries(codeKey(RAW_CODE));

        when(hashes.entries(codeKey(OLD_CODE))).thenReturn(Map.of());
        Assertions.assertThat(store.findByCode(OLD_CODE)).isEmpty();

        Map<Object, Object> polluted = new LinkedHashMap<>(entries);
        polluted.put("raw_code", RAW_CODE);
        when(hashes.entries(codeKey(RAW_CODE))).thenReturn(polluted);
        assertThatIllegalArgumentException().isThrownBy(() -> store.findByCode(RAW_CODE));
        Map<Object, Object> nonString = new LinkedHashMap<>(entries);
        nonString.put("status", 0);
        when(hashes.entries(codeKey(RAW_CODE))).thenReturn(nonString);
        assertThatIllegalArgumentException().isThrownBy(() -> store.findByCode(RAW_CODE));
    }

    @Test
    void consumeUsesPointerCasSoTwoValidAttemptsProduceExactlyOneWinner() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        DefaultRedisScript<Long> consume = new DefaultRedisScript<>();
        when(redis.execute(eq(consume), eq(List.of(codeKey(RAW_CODE), userKey())), eq(RAW_CODE), eq("7"),
                eq("core_agent")))
                .thenReturn(1L, 0L);
        RedisCoreAgentAuthorizationCodeStore store = store(redis, new DefaultRedisScript<>(), consume,
                new DefaultRedisScript<>(), NOW);

        assertThat(store.consume(RAW_CODE, USER_ID, "core_agent")).isTrue();
        assertThat(store.consume(RAW_CODE, USER_ID, "core_agent")).isFalse();
        verify(redis, times(2)).execute(eq(consume), eq(List.of(codeKey(RAW_CODE), userKey())), eq(RAW_CODE), eq("7"),
                eq("core_agent"));
        assertThatIllegalStateException().isThrownBy(() -> {
            when(redis.execute(eq(consume), eq(List.of(codeKey(OLD_CODE), userKey())), eq(OLD_CODE), eq("7"),
                    eq("core_agent")))
                    .thenReturn(-1L);
            store.consume(OLD_CODE, USER_ID, "core_agent");
        });
    }

    @Test
    void invalidateIsIdempotentAndUsesTheExactPointerKey() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        DefaultRedisScript<Long> invalidate = new DefaultRedisScript<>();
        when(redis.execute(eq(invalidate), eq(List.of(userKey())), eq("7"), eq("core_agent"),
                eq("oauth2:auth_code:"))).thenReturn(1L, 1L);
        RedisCoreAgentAuthorizationCodeStore store = store(redis, new DefaultRedisScript<>(), new DefaultRedisScript<>(),
                invalidate, NOW);

        store.invalidateCurrent(USER_ID, "core_agent");
        store.invalidateCurrent(USER_ID, "core_agent");

        verify(redis, times(2)).execute(eq(invalidate), eq(List.of(userKey())), eq("7"), eq("core_agent"),
                eq("oauth2:auth_code:"));
        assertThatIllegalArgumentException().isThrownBy(() -> store.invalidateCurrent(0L, "core_agent"));
    }

    @Test
    void luaScriptsValidateBeforeAnyMutationAndKeepRawDataOutOfHashFields() throws IOException {
        String replace = lua("lua/core_agent_authorization_code_replace.lua");
        assertThat(replace).contains("#KEYS ~= 2", "#ARGV ~= 40", "oauth2:auth_code:{", "user:auth_code:{",
                "HSET", "PEXPIRE", "SET", "schema_version", "password_hash", "email_present",
                "return 1");
        assertThat(replace.indexOf("for index, field")).isLessThan(replace.indexOf("redis.call('DEL'"));
        assertThat(replace).doesNotContain("raw_code");

        String consume = lua("lua/core_agent_authorization_code_consume.lua");
        assertThat(consume).contains("#KEYS ~= 2", "#ARGV ~= 3", "HGET", "user_id", "client_id", "DEL", "return 0", "return 1");
        assertThat(consume.indexOf("if #KEYS ~= 2")).isLessThan(consume.indexOf("redis.call('GET'"));
        assertThat(consume.indexOf("redis.call('GET'"))
                .isLessThan(consume.indexOf("redis.call('DEL'"));

        String invalidate = lua("lua/core_agent_authorization_code_invalidate.lua");
        assertThat(invalidate).contains("#KEYS ~= 1", "#ARGV ~= 3", "oauth2:auth_code:", "GET", "DEL", "return 1");
        assertThat(invalidate.indexOf("if #KEYS ~= 1")).isLessThan(invalidate.indexOf("redis.call('GET'"));
        assertThat(invalidate).contains("if not current_code then return 1 end");
    }

    @Test
    void transitionUsesExactThreeKeysAndOnlyConsumesBoundPendingStateBeforeWritingCodeAndPointer() throws IOException {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        DefaultRedisScript<Long> transition = new DefaultRedisScript<>();
        CoreAgentPendingAuthorizationState pending = pending(NOW);
        CoreAgentAuthorizationCodeState state = state(RAW_CODE, NOW);
        IssuedCoreAgentAuthorizationPendingHandle handle = new IssuedCoreAgentAuthorizationPendingHandle(OLD_CODE,
                pending.expiresAt());
        Object[] arguments = transitionArguments(handle, pending, state, "600000");
        when(redis.execute(eq(transition), eq(List.of(pendingKey(), codeKey(RAW_CODE), userKey())), eq(arguments)))
                .thenReturn(1L, 0L);

        assertThat(store(redis, new DefaultRedisScript<>(), new DefaultRedisScript<>(), new DefaultRedisScript<>(),
                transition, NOW).consumePendingAndStoreCode(handle, pending, state)).isTrue();
        RedisCoreAgentAuthorizationCodeStore store = store(redis, new DefaultRedisScript<>(), new DefaultRedisScript<>(),
                new DefaultRedisScript<>(), transition, NOW);
        assertThat(store.consumePendingAndStoreCode(handle, pending, state)).isFalse();
        verify(redis, times(2)).execute(eq(transition), eq(List.of(pendingKey(), codeKey(RAW_CODE), userKey())),
                eq(arguments));
        assertThat(arguments).containsExactly("600000", OLD_CODE, RAW_CODE, "core_agent", "7", "session-id",
                "schema_version", "1", "client_id", "core_agent", "redirect_uri", "http://127.0.0.1:9090/oauth/callback",
                "scopes", "note:read,sys:read", "code_challenge", code((byte) 9), "code_challenge_method", "S256",
                "original_socket_address", "127.0.0.1", "oauth_state", "opaque-state", "issued_at_epoch_millis",
                Long.toString(NOW.toEpochMilli()), "expires_at_epoch_millis",
                Long.toString(NOW.plus(Duration.ofMinutes(10)).toEpochMilli()), "user_id", "7", "username", "alice",
                "role_id", "2", "password_hash", "$2a$10$" + "a".repeat(53), "email_present", "0", "email", "",
                "extra_grant_types", "agent_client", "status", "0");

        String lua = lua("lua/core_agent_pending_authorization_to_code.lua");
        assertThat(lua).contains("#KEYS ~= 3", "#ARGV ~= 42", "oauth2:authorize:pending:{", "oauth2:auth_code:{",
                "user:auth_code:{", "HGET", "HSET", "PEXPIRE", "SET", "DEL");
        assertThat(lua.indexOf("for index, field")).isLessThan(lua.indexOf("redis.call('HGET'"));
        assertThat(lua.indexOf("redis.call('HGET'")).isLessThan(lua.indexOf("redis.call('DEL'"));
        assertThat(lua).containsSubsequence("redis.call('DEL', KEYS[1])", "redis.call('HSET', KEYS[2]",
                "redis.call('PEXPIRE', KEYS[2]", "redis.call('SET', KEYS[3]");
        assertThat(lua).doesNotContain("redis.call('DEL', KEYS[2])", "redis.call('KEYS'", "SCAN", "math.random", "sha", "bcrypt");
        assertThat(countOccurrences(lua, "redis.call('DEL'")).isEqualTo(1);
        assertThat(countOccurrences(lua, "redis.call('HSET'")).isEqualTo(1);
        assertThat(countOccurrences(lua, "redis.call('PEXPIRE'")).isEqualTo(1);
        assertThat(countOccurrences(lua, "redis.call('SET'")).isEqualTo(1);
    }

    private static RedisCoreAgentAuthorizationCodeStore store(StringRedisTemplate redis, DefaultRedisScript<Long> replace,
                                                               DefaultRedisScript<Long> consume,
                                                               DefaultRedisScript<Long> invalidate, Instant now) {
        return store(redis, replace, consume, invalidate, new DefaultRedisScript<>(), now);
    }

    private static int countOccurrences(String value, String needle) {
        int count = 0;
        int index = 0;
        while ((index = value.indexOf(needle, index)) >= 0) {
            count++;
            index += needle.length();
        }
        return count;
    }

    private static RedisCoreAgentAuthorizationCodeStore store(StringRedisTemplate redis, DefaultRedisScript<Long> replace,
                                                               DefaultRedisScript<Long> consume,
                                                               DefaultRedisScript<Long> invalidate,
                                                               DefaultRedisScript<Long> transition, Instant now) {
        return new RedisCoreAgentAuthorizationCodeStore(redis, new CoreAgentAuthorizationCodeStateCodec(),
                Clock.fixed(now, ZoneOffset.UTC), replace, consume, invalidate, transition);
    }

    private static Object[] replaceArguments(CoreAgentAuthorizationCodeState state, String ttlMillis) {
        Map<String, String> values = new CoreAgentAuthorizationCodeStateCodec().encode(state);
        List<Object> arguments = new ArrayList<>();
        arguments.add(ttlMillis);
        arguments.add(state.rawCode());
        arguments.add(Long.toString(state.accountSnapshot().userId()));
        arguments.add(state.clientId());
        for (String field : new CoreAgentAuthorizationCodeStateCodec().fieldNames()) {
            arguments.add(field);
            arguments.add(values.get(field));
        }
        return arguments.toArray();
    }

    private static CoreAgentAuthorizationCodeState state(String rawCode, Instant issuedAt) {
        return new CoreAgentAuthorizationCodeState(rawCode, "core_agent", "http://127.0.0.1:9090/oauth/callback",
                List.of("note:read", "sys:read"), code((byte) 9), "S256", "127.0.0.1", "opaque-state", issuedAt,
                issuedAt.plus(Duration.ofMinutes(10)), new CoreAgentAuthorizationAccountSnapshot(USER_ID, "alice", 2L,
                "$2a$10$" + "a".repeat(53), null, "agent_client", 0));
    }

    private static String codeKey(String rawCode) {
        return "oauth2:auth_code:{" + rawCode + '}';
    }

    private static String userKey() {
        return "user:auth_code:{7}:{core_agent}";
    }

    private static String pendingKey() {
        return "oauth2:authorize:pending:{" + OLD_CODE + '}';
    }

    private static CoreAgentPendingAuthorizationState pending(Instant issuedAt) {
        return new CoreAgentPendingAuthorizationState("core_agent", "http://127.0.0.1:9090/oauth/callback", null,
                code((byte) 9), "S256", "opaque-state", "127.0.0.1", USER_ID, "session-id", issuedAt,
                issuedAt.plus(Duration.ofMinutes(10)));
    }

    private static Object[] transitionArguments(IssuedCoreAgentAuthorizationPendingHandle handle,
                                                CoreAgentPendingAuthorizationState pending,
                                                CoreAgentAuthorizationCodeState state,
                                                String ttlMillis) {
        Map<String, String> values = new CoreAgentAuthorizationCodeStateCodec().encode(state);
        List<Object> arguments = new ArrayList<>(List.of(ttlMillis, handle.rawHandle(), state.rawCode(),
                pending.clientId(), Long.toString(pending.authenticatedUserId()), pending.sessionId()));
        for (String field : new CoreAgentAuthorizationCodeStateCodec().fieldNames()) {
            arguments.add(field);
            arguments.add(values.get(field));
        }
        return arguments.toArray();
    }

    private static String code(byte fill) {
        byte[] bytes = new byte[32];
        java.util.Arrays.fill(bytes, fill);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String lua(String path) throws IOException {
        return new String(new ClassPathResource(path).getInputStream().readAllBytes(), StandardCharsets.UTF_8);
    }
}
