package com.jacolp.system.infrastructure.authorization;

import com.jacolp.system.application.authorization.EmailLoginCodeStateCodec;
import com.jacolp.system.application.port.out.EmailLoginCodeFailureDecision;
import org.assertj.core.api.Assertions;
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RedisEmailLoginCodeStateStoreFailureTest {

    private static final String KEY = "user:email_code:user:7";
    private static final String VERIFIER_HASH = "$2a$10$" + "a".repeat(53);

    @Test
    void sendsExactKeyAndArgumentsAndMapsAllDecisions() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        when(redis.execute(eq(script), eq(List.of(KEY)), eq(VERIFIER_HASH), eq("5")))
                .thenReturn(1L, 2L, 0L);
        RedisEmailLoginCodeStateStore store = store(redis, script);

        Assertions.assertThat(store.recordFailure("user", 7L, VERIFIER_HASH, 5))
                .isEqualTo(EmailLoginCodeFailureDecision.RECORDED);
        Assertions.assertThat(store.recordFailure("user", 7L, VERIFIER_HASH, 5))
                .isEqualTo(EmailLoginCodeFailureDecision.INVALIDATED);
        Assertions.assertThat(store.recordFailure("user", 7L, VERIFIER_HASH, 5))
                .isEqualTo(EmailLoginCodeFailureDecision.STALE);
        verify(redis, org.mockito.Mockito.times(3)).execute(
                eq(script), eq(List.of(KEY)), eq(VERIFIER_HASH), eq("5"));
    }

    @Test
    void rejectsInvalidInputsAndUnexpectedResultsAndPropagatesRedisFailure() {
        RedisEmailLoginCodeStateStore invalidStore = store(mock(StringRedisTemplate.class), new DefaultRedisScript<>());
        assertThatIllegalArgumentException().isThrownBy(() -> invalidStore.recordFailure("user", 7L, null, 5));
        assertThatIllegalArgumentException().isThrownBy(() -> invalidStore.recordFailure("user", 7L, "bad", 5));
        assertThatIllegalArgumentException().isThrownBy(() -> invalidStore.recordFailure("user", 7L, VERIFIER_HASH, 0));
        assertThatIllegalArgumentException().isThrownBy(() -> invalidStore.recordFailure("user", 7L, VERIFIER_HASH, 6));
        assertThatIllegalArgumentException().isThrownBy(() -> invalidStore.recordFailure("core_agent", 7L, VERIFIER_HASH, 5));

        assertThatIllegalStateException().isThrownBy(() -> withResult(null));
        assertThatIllegalStateException().isThrownBy(() -> withResult(-1L));
        assertThatIllegalStateException().isThrownBy(() -> withResult(9L));

        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        IllegalStateException failure = new IllegalStateException("redis unavailable");
        when(redis.execute(eq(script), anyList(), any(), any())).thenThrow(failure);
        assertThatThrownBy(() -> store(redis, script).recordFailure("user", 7L, VERIFIER_HASH, 5))
                .isSameAs(failure);
    }

    @Test
    void failureLuaChecksLiveCounterAndTtlBeforeMutatingWithoutRawValues() throws IOException {
        String lua = new String(new ClassPathResource("lua/email_login_code_state_record_failure.lua")
                .getInputStream().readAllBytes(), StandardCharsets.UTF_8);

        assertThat(lua).contains("#KEYS ~= 1", "#ARGV ~= 2", "HGET", "failed_attempts", "PTTL", "HSET", "DEL", "return 0", "return 1", "return 2");
        assertThat(lua.indexOf("redis.call('PTTL'"))
                .isLessThan(lua.indexOf("redis.call('HSET'"));
        assertThat(lua.indexOf("redis.call('PTTL'"))
                .isLessThan(lua.indexOf("redis.call('DEL'"));
        assertThat(lua).doesNotContain("raw_code", "email", "password", "token", "secret");
        assertThat(lua).doesNotContain("PEXPIRE", "EXPIRE");
    }

    private static void withResult(Long result) {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        when(redis.execute(eq(script), anyList(), any(), any())).thenReturn(result);
        store(redis, script).recordFailure("user", 7L, VERIFIER_HASH, 5);
    }

    private static RedisEmailLoginCodeStateStore store(
            StringRedisTemplate redis,
            DefaultRedisScript<Long> failureScript) {
        return new RedisEmailLoginCodeStateStore(
                redis,
                new EmailLoginCodeStateCodec(),
                Clock.fixed(Instant.EPOCH, ZoneOffset.UTC),
                new DefaultRedisScript<>(),
                new DefaultRedisScript<>(),
                failureScript);
    }
}
