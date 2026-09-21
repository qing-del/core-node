package com.jacolp.system.infrastructure.authorization;

import com.jacolp.system.application.authorization.model.EmailLoginCodeIssueRateLimitRequest;
import com.jacolp.system.application.port.out.EmailLoginCodeIssueRateLimitDecision;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RedisEmailLoginCodeIssueRateLimiterTest {

    private static final String EMAIL_FINGERPRINT = "A".repeat(43);
    private static final String IP_FINGERPRINT = "B".repeat(43);

    @Test
    void submitsExactGlobalFingerprintKeysAndMillisecondsArguments() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        RedisEmailLoginCodeIssueRateLimiter limiter = new RedisEmailLoginCodeIssueRateLimiter(redis, script);
        List<String> keys = List.of(
                "user:email_code:cooldown:email:" + EMAIL_FINGERPRINT,
                "user:email_code:cooldown:ip:" + IP_FINGERPRINT,
                "user:email_code:window:email:" + EMAIL_FINGERPRINT,
                "user:email_code:window:ip:" + IP_FINGERPRINT);
        when(redis.execute(eq(script), eq(keys), eq("60000"), eq("3600000"), eq("5"))).thenReturn(1L);

        assertThat(limiter.tryAcquire(request())).isEqualTo(EmailLoginCodeIssueRateLimitDecision.ALLOWED);
        verify(redis).execute(eq(script), eq(keys), eq("60000"), eq("3600000"), eq("5"));
        assertThat(keys).allSatisfy(key -> assertThat(key).doesNotContain("alice@example.test", "192.0.2.10"));
    }

    @Test
    void mapsTheThreeExpectedScriptDecisions() {
        assertThat(decisionFor(1L)).isEqualTo(EmailLoginCodeIssueRateLimitDecision.ALLOWED);
        assertThat(decisionFor(0L)).isEqualTo(EmailLoginCodeIssueRateLimitDecision.COOLDOWN);
        assertThat(decisionFor(-1L)).isEqualTo(EmailLoginCodeIssueRateLimitDecision.WINDOW_LIMIT);
    }

    @Test
    void rejectsNullPollutedAndUnknownScriptResults() {
        assertThatIllegalStateException().isThrownBy(() -> decisionFor(null));
        assertThatIllegalStateException().isThrownBy(() -> decisionFor(-2L));
        assertThatIllegalStateException().isThrownBy(() -> decisionFor(99L));
    }

    @Test
    void redisFailuresPropagate() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        IllegalStateException failure = new IllegalStateException("redis unavailable");
        when(redis.execute(eq(script), anyList(), any(), any(), any())).thenThrow(failure);

        assertThatThrownBy(() -> new RedisEmailLoginCodeIssueRateLimiter(redis, script).tryAcquire(request()))
                .isSameAs(failure);
    }

    @Test
    void productionConstructorIsExplicitlyAutowiredAndLuaValidatesBeforeWriting()
            throws IOException, ReflectiveOperationException {
        assertThat(RedisEmailLoginCodeIssueRateLimiter.class.getConstructor(StringRedisTemplate.class)
                .isAnnotationPresent(Autowired.class)).isTrue();

        String lua = new String(new ClassPathResource("lua/email_login_code_issue_rate_limit.lua")
                .getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertThat(lua).contains(
                "KEYS[1]", "KEYS[2]", "KEYS[3]", "KEYS[4]",
                "GET", "PTTL", "SET", "INCR", "PEXPIRE", "return -2",
                "value == '0' or string.match(value, '^[1-9][0-9]*$')",
                "local cooldown = ARGV[1]", "local window = ARGV[2]",
                "local limit_exceeded = false",
                "if tonumber(value) >= maximum then limit_exceeded = true end",
                "if limit_exceeded then return -1 end");
        assertThat(lua).doesNotContain("(0|");
        assertThat(lua.indexOf("redis.call('GET'"))
                .isLessThan(lua.indexOf("redis.call('SET'"));
        assertThat(lua.indexOf("redis.call('PTTL'"))
                .isLessThan(lua.indexOf("redis.call('SET'"));
        assertThat(lua.indexOf("if tonumber(value) >= maximum then limit_exceeded = true end"))
                .isLessThan(lua.indexOf("if limit_exceeded then return -1 end"));
        assertThat(lua.indexOf("if limit_exceeded then return -1 end"))
                .isLessThan(lua.indexOf("redis.call('SET'"));
    }

    private static EmailLoginCodeIssueRateLimitDecision decisionFor(Long scriptResult) {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        when(redis.execute(eq(script), anyList(), any(), any(), any())).thenReturn(scriptResult);
        return new RedisEmailLoginCodeIssueRateLimiter(redis, script).tryAcquire(request());
    }

    private static EmailLoginCodeIssueRateLimitRequest request() {
        return new EmailLoginCodeIssueRateLimitRequest(
                EMAIL_FINGERPRINT,
                IP_FINGERPRINT,
                Duration.ofMinutes(1),
                Duration.ofHours(1),
                5);
    }
}
