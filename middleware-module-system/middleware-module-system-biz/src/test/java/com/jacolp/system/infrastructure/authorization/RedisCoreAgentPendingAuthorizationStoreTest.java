package com.jacolp.system.infrastructure.authorization;

import com.jacolp.system.application.authorization.CoreAgentPendingAuthorizationStateCodec;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class RedisCoreAgentPendingAuthorizationStoreTest {

    private static final Instant NOW = Instant.parse("2026-08-11T08:00:00Z");
    private static final String HANDLE = opaque((byte) 1);

    @Test
    void savesExactKeyTtlAndFixedHashFieldsWithoutPuttingHandleInTheHash() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        DefaultRedisScript<Long> save = new DefaultRedisScript<>();
        CoreAgentPendingAuthorizationState state = state(NOW.minusMillis(100));
        Object[] arguments = arguments(state, "599900");
        when(redis.execute(eq(save), eq(List.of(key())), eq(arguments))).thenReturn(1L);

        store(redis, save, NOW).save(handle(state), state);

        verify(redis).execute(eq(save), eq(List.of(key())), eq(arguments));
        assertThat(arguments).contains(HANDLE).doesNotContain("raw_code", "authorization_code");
        Assertions.assertThat(new CoreAgentPendingAuthorizationStateCodec().encode(state)).doesNotContainValue(HANDLE);
    }

    @Test
    void findsStrictRoundTripAndFailsClosedForPollutionOrExpiredInput() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        HashOperations<String, Object, Object> hashes = mock(HashOperations.class);
        when(redis.opsForHash()).thenReturn(hashes);
        CoreAgentPendingAuthorizationState state = state(NOW);
        when(hashes.entries(key())).thenReturn(new LinkedHashMap<>(new CoreAgentPendingAuthorizationStateCodec().encode(state)));
        RedisCoreAgentPendingAuthorizationStore store = store(redis, new DefaultRedisScript<>(), NOW);
        Assertions.assertThat(store.find(HANDLE)).contains(state);
        when(hashes.entries(key())).thenReturn(Map.of("schema_version", "1"));
        assertThatIllegalArgumentException().isThrownBy(() -> store.find(HANDLE));

        StringRedisTemplate unusedRedis = mock(StringRedisTemplate.class);
        RedisCoreAgentPendingAuthorizationStore unusedStore = store(unusedRedis, new DefaultRedisScript<>(), NOW);
        CoreAgentPendingAuthorizationState expired = state(NOW.minus(Duration.ofMinutes(10)));
        assertThatIllegalArgumentException().isThrownBy(() -> unusedStore.save(handle(expired), expired));
        verifyNoInteractions(unusedRedis);
    }

    @Test
    void rejectsNonSuccessLuaResultAndScriptValidatesBeforeMutation() throws IOException {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        DefaultRedisScript<Long> save = new DefaultRedisScript<>();
        when(redis.execute(eq(save), eq(List.of(key())), org.mockito.ArgumentMatchers.any(Object[].class))).thenReturn(-1L);
        CoreAgentPendingAuthorizationState state = state(NOW);
        assertThatIllegalStateException().isThrownBy(() -> store(redis, save, NOW).save(handle(state), state));

        String lua = new String(new ClassPathResource("lua/core_agent_pending_authorization_save.lua")
                .getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertThat(lua).contains("#KEYS ~= 1", "#ARGV ~= 28", "oauth2:authorize:pending:{", "HSET", "PEXPIRE");
        assertThat(lua.indexOf("if #KEYS ~= 1")).isLessThan(lua.indexOf("redis.call('DEL'"));
        assertThat(lua.indexOf("for index, field")).isLessThan(lua.indexOf("redis.call('DEL'"));
        assertThat(lua).doesNotContain("raw_code");
    }

    private static RedisCoreAgentPendingAuthorizationStore store(StringRedisTemplate redis, DefaultRedisScript<Long> save,
                                                                   Instant now) {
        return new RedisCoreAgentPendingAuthorizationStore(redis, new CoreAgentPendingAuthorizationStateCodec(),
                Clock.fixed(now, ZoneOffset.UTC), save);
    }

    private static Object[] arguments(CoreAgentPendingAuthorizationState state, String ttl) {
        Map<String, String> values = new CoreAgentPendingAuthorizationStateCodec().encode(state);
        List<Object> arguments = new ArrayList<>(List.of(ttl, HANDLE));
        for (String field : new CoreAgentPendingAuthorizationStateCodec().fieldNames()) {
            arguments.add(field);
            arguments.add(values.get(field));
        }
        return arguments.toArray();
    }

    private static CoreAgentPendingAuthorizationState state(Instant issuedAt) {
        return new CoreAgentPendingAuthorizationState("core_agent", "http://127.0.0.1:9090/oauth/callback", null,
                opaque((byte) 2), "S256", "state", "192.0.2.5", 7L, "session-id", issuedAt,
                issuedAt.plus(Duration.ofMinutes(10)));
    }

    private static IssuedCoreAgentAuthorizationPendingHandle handle(CoreAgentPendingAuthorizationState state) {
        return new IssuedCoreAgentAuthorizationPendingHandle(HANDLE, state.expiresAt());
    }

    private static String key() {
        return "oauth2:authorize:pending:{" + HANDLE + '}';
    }

    private static String opaque(byte fill) {
        byte[] bytes = new byte[32];
        java.util.Arrays.fill(bytes, fill);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
