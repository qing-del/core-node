package com.jacolp.system.infrastructure.authorization;

import com.jacolp.system.application.authorization.EmailLoginCodeStateCodec;
import com.jacolp.system.application.authorization.model.EmailLoginCodeState;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class RedisEmailLoginCodeStateStoreReplaceTest {

    private static final Instant NOW = Instant.ofEpochMilli(1_000L);
    private static final String USER_KEY = "user:email_code:user:7";

    @Test
    void replacesTheExactKeyWithTtlAndFixedCodecFieldsWithoutRawValues() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        EmailLoginCodeState state = state(Instant.ofEpochMilli(900L), Instant.ofEpochMilli(11_000L));
        Object[] expectedArguments = arguments(state, "10000");
        when(redis.execute(eq(script), eq(List.of(USER_KEY)), eq(expectedArguments))).thenReturn(1L);
        RedisEmailLoginCodeStateStore store = store(redis, script, NOW);

        store.replace(state);

        verify(redis).execute(eq(script), eq(List.of(USER_KEY)), eq(expectedArguments));
        assertThat(expectedArguments).containsExactly(
                "10000",
                "schema_version", "1",
                "client_id", "user",
                "user_id", "7",
                "email_fingerprint", "A".repeat(43),
                "verifier_hash", "$2a$10$" + "a".repeat(53),
                "failed_attempts", "0",
                "issued_at_epoch_millis", "900",
                "expires_at_epoch_millis", "11000");
        assertThat(expectedArguments).doesNotContain("123456", "alice@example.test", "password", "token");
    }

    @Test
    void rejectsExpiredFutureIssuedAndOverflowTtlsBeforeRedis() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        RedisEmailLoginCodeStateStore store = store(redis, new DefaultRedisScript<>(), NOW);

        assertThatIllegalArgumentException().isThrownBy(() ->
                store.replace(state(Instant.ofEpochMilli(900L), NOW)));
        assertThatIllegalArgumentException().isThrownBy(() ->
                store.replace(state(Instant.ofEpochMilli(1_001L), Instant.ofEpochMilli(2_000L))));

        RedisEmailLoginCodeStateStore overflowStore = store(
                redis,
                new DefaultRedisScript<>(),
                Instant.MIN);
        assertThatIllegalArgumentException().isThrownBy(() ->
                overflowStore.replace(state(Instant.MIN, Instant.MAX)));
        verifyNoInteractions(redis);
    }

    @Test
    void rejectsNullAndUnexpectedReplaceResultsAndPropagatesRedisFailures() {
        assertThatIllegalStateException().isThrownBy(() -> replaceWithResult(null));
        assertThatIllegalStateException().isThrownBy(() -> replaceWithResult(0L));

        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        IllegalStateException failure = new IllegalStateException("redis unavailable");
        when(redis.execute(eq(script), anyList(), any(Object[].class))).thenThrow(failure);

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> store(redis, script, NOW).replace(validState()))
                .isSameAs(failure);
    }

    @Test
    void replaceLuaValidatesSchemaBeforeDeletingWritingAndExpiring() throws IOException {
        String lua = new String(new ClassPathResource("lua/email_login_code_state_replace.lua")
                .getInputStream().readAllBytes(), StandardCharsets.UTF_8);

        assertThat(lua).contains(
                "#KEYS ~= 1", "#ARGV ~= 17", "^[1-9][0-9]*$",
                "schema_version", "client_id", "user_id", "email_fingerprint", "verifier_hash",
                "failed_attempts", "issued_at_epoch_millis", "expires_at_epoch_millis",
                "DEL", "HSET", "PEXPIRE", "return 1");
        assertThat(lua.indexOf("if #KEYS ~= 1")).isLessThan(lua.indexOf("redis.call('DEL'"));
        assertThat(lua.indexOf("if ARGV[index * 2] ~= field")).isLessThan(lua.indexOf("redis.call('DEL'"));
        assertThat(lua.indexOf("redis.call('DEL'"))
                .isLessThan(lua.indexOf("redis.call('HSET'"));
        assertThat(lua.indexOf("redis.call('HSET'"))
                .isLessThan(lua.indexOf("redis.call('PEXPIRE'"));
    }

    private static void replaceWithResult(Long result) {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        when(redis.execute(eq(script), anyList(), any(Object[].class))).thenReturn(result);
        store(redis, script, NOW).replace(validState());
    }

    private static RedisEmailLoginCodeStateStore store(
            StringRedisTemplate redis,
            DefaultRedisScript<Long> script,
            Instant now) {
        return new RedisEmailLoginCodeStateStore(
                redis,
                new EmailLoginCodeStateCodec(),
                Clock.fixed(now, ZoneOffset.UTC),
                script,
                new DefaultRedisScript<>(),
                new DefaultRedisScript<>());
    }

    private static Object[] arguments(EmailLoginCodeState state, String ttl) {
        Map<String, String> encoded = new EmailLoginCodeStateCodec().encode(state);
        List<Object> arguments = new ArrayList<>();
        arguments.add(ttl);
        for (String fieldName : new EmailLoginCodeStateCodec().fieldNames()) {
            arguments.add(fieldName);
            arguments.add(encoded.get(fieldName));
        }
        return arguments.toArray();
    }

    private static EmailLoginCodeState validState() {
        return state(Instant.ofEpochMilli(900L), Instant.ofEpochMilli(11_000L));
    }

    private static EmailLoginCodeState state(Instant issuedAt, Instant expiresAt) {
        return new EmailLoginCodeState(
                "user",
                7L,
                "A".repeat(43),
                "$2a$10$" + "a".repeat(53),
                0,
                issuedAt,
                expiresAt);
    }
}
