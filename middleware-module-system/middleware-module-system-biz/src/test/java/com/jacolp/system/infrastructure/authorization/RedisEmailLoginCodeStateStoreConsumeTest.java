package com.jacolp.system.infrastructure.authorization;

import com.jacolp.system.application.authorization.EmailLoginCodeStateCodec;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

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
import static org.mockito.Mockito.when;

class RedisEmailLoginCodeStateStoreConsumeTest {

    private static final String KEY = "user:email_code:user:7";
    private static final String VERIFIER_HASH = "$2a$10$" + "a".repeat(53);

    @Test
    void consumesTheExactKeyAndExpectedVerifierThenReturnsFalseOnSecondAttempt() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        DefaultRedisScript<Long> consumeScript = new DefaultRedisScript<>();
        when(redis.execute(eq(consumeScript), eq(List.of(KEY)), eq(VERIFIER_HASH))).thenReturn(1L, 0L);
        RedisEmailLoginCodeStateStore store = store(redis, consumeScript);

        assertThat(store.consume("user", 7L, VERIFIER_HASH)).isTrue();
        assertThat(store.consume("user", 7L, VERIFIER_HASH)).isFalse();
        verify(redis, times(2)).execute(eq(consumeScript), eq(List.of(KEY)), eq(VERIFIER_HASH));
    }

    @Test
    void mapsMissingAndMismatchedVerifierResultsToFalse() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        DefaultRedisScript<Long> consumeScript = new DefaultRedisScript<>();
        when(redis.execute(eq(consumeScript), anyList(), any())).thenReturn(0L);
        RedisEmailLoginCodeStateStore store = store(redis, consumeScript);

        assertThat(store.consume("user", 7L, VERIFIER_HASH)).isFalse();
        assertThat(store.consume("admin", 9L, VERIFIER_HASH)).isFalse();
    }

    @Test
    void rejectsInvalidInputAndUnexpectedResultsWhilePropagatingRedisFailures() {
        RedisEmailLoginCodeStateStore invalidStore = store(mock(StringRedisTemplate.class), new DefaultRedisScript<>());
        assertThatIllegalArgumentException().isThrownBy(() -> invalidStore.consume("user", 7L, null));
        assertThatIllegalArgumentException().isThrownBy(() -> invalidStore.consume("user", 7L, "not-a-hash"));
        assertThatIllegalArgumentException().isThrownBy(() -> invalidStore.consume("core_agent", 7L, VERIFIER_HASH));
        assertThatIllegalArgumentException().isThrownBy(() -> invalidStore.consume("user", 0L, VERIFIER_HASH));

        assertThatIllegalStateException().isThrownBy(() -> consumeWithResult(null));
        assertThatIllegalStateException().isThrownBy(() -> consumeWithResult(-1L));
        assertThatIllegalStateException().isThrownBy(() -> consumeWithResult(2L));

        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        DefaultRedisScript<Long> consumeScript = new DefaultRedisScript<>();
        IllegalStateException failure = new IllegalStateException("redis unavailable");
        when(redis.execute(eq(consumeScript), anyList(), any())).thenThrow(failure);
        assertThatThrownBy(() -> store(redis, consumeScript).consume("user", 7L, VERIFIER_HASH))
                .isSameAs(failure);
    }

    @Test
    void consumeLuaReadsVerifierBeforeDeleteAndDoesNotContainRawSecrets() throws IOException {
        String lua = new String(new ClassPathResource("lua/email_login_code_state_consume.lua")
                .getInputStream().readAllBytes(), StandardCharsets.UTF_8);

        assertThat(lua).contains("#KEYS ~= 1", "#ARGV ~= 1", "HGET", "verifier_hash", "DEL", "return 0", "return 1");
        assertThat(lua.indexOf("redis.call('HGET'"))
                .isLessThan(lua.indexOf("redis.call('DEL'"));
        assertThat(lua).doesNotContain("raw_code", "email", "password", "token", "secret");
    }

    private static void consumeWithResult(Long result) {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        DefaultRedisScript<Long> consumeScript = new DefaultRedisScript<>();
        when(redis.execute(eq(consumeScript), anyList(), any())).thenReturn(result);
        store(redis, consumeScript).consume("user", 7L, VERIFIER_HASH);
    }

    private static RedisEmailLoginCodeStateStore store(
            StringRedisTemplate redis,
            DefaultRedisScript<Long> consumeScript) {
        return new RedisEmailLoginCodeStateStore(
                redis,
                new EmailLoginCodeStateCodec(),
                Clock.fixed(Instant.EPOCH, ZoneOffset.UTC),
                new DefaultRedisScript<>(),
                consumeScript,
                new DefaultRedisScript<>());
    }
}
