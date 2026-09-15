package com.jacolp.system.infrastructure.authorization;

import com.jacolp.system.application.authorization.model.EmailLoginCodeIssueRateLimitRequest;
import com.jacolp.system.application.port.out.EmailLoginCodeIssueRateLimitDecision;
import com.jacolp.system.application.port.out.EmailLoginCodeIssueRateLimiter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Repository;

import java.time.Duration;
import java.util.List;
import java.util.Objects;

@Repository
public class RedisEmailLoginCodeIssueRateLimiter implements EmailLoginCodeIssueRateLimiter {

    private static final String COOLDOWN_EMAIL_KEY_PREFIX = "user:email_code:cooldown:email:";
    private static final String COOLDOWN_IP_KEY_PREFIX = "user:email_code:cooldown:ip:";
    private static final String WINDOW_EMAIL_KEY_PREFIX = "user:email_code:window:email:";
    private static final String WINDOW_IP_KEY_PREFIX = "user:email_code:window:ip:";

    private final StringRedisTemplate redis;
    private final DefaultRedisScript<Long> script;

    @Autowired
    public RedisEmailLoginCodeIssueRateLimiter(StringRedisTemplate redis) {
        this(redis, script());
    }

    RedisEmailLoginCodeIssueRateLimiter(StringRedisTemplate redis, DefaultRedisScript<Long> script) {
        this.redis = Objects.requireNonNull(redis, "redis must not be null");
        this.script = Objects.requireNonNull(script, "script must not be null");
    }

    @Override
    public EmailLoginCodeIssueRateLimitDecision tryAcquire(EmailLoginCodeIssueRateLimitRequest request) {
        Objects.requireNonNull(request, "request must not be null");
        long cooldownMilliseconds = milliseconds(request.cooldown());
        long windowMilliseconds = milliseconds(request.window());
        List<String> keys = List.of(
                COOLDOWN_EMAIL_KEY_PREFIX + request.emailFingerprint(),
                COOLDOWN_IP_KEY_PREFIX + request.ipFingerprint(),
                WINDOW_EMAIL_KEY_PREFIX + request.emailFingerprint(),
                WINDOW_IP_KEY_PREFIX + request.ipFingerprint());

        Long result = redis.execute(
                script,
                keys,
                Long.toString(cooldownMilliseconds),
                Long.toString(windowMilliseconds),
                request.maxIssues().toString());
        return toDecision(result);
    }

    private static EmailLoginCodeIssueRateLimitDecision toDecision(Long result) {
        if (result == null || result == -2L) {
            throw new IllegalStateException("Invalid email-code rate-limit state");
        }
        if (result == 1L) {
            return EmailLoginCodeIssueRateLimitDecision.ALLOWED;
        }
        if (result == 0L) {
            return EmailLoginCodeIssueRateLimitDecision.COOLDOWN;
        }
        if (result == -1L) {
            return EmailLoginCodeIssueRateLimitDecision.WINDOW_LIMIT;
        }
        throw new IllegalStateException("Invalid email-code rate-limit result");
    }

    private static long milliseconds(Duration duration) {
        try {
            long milliseconds = duration.toMillis();
            if (milliseconds <= 0) {
                throw new IllegalArgumentException("Invalid email-code rate-limit duration");
            }
            return milliseconds;
        } catch (ArithmeticException exception) {
            throw new IllegalArgumentException("Invalid email-code rate-limit duration", exception);
        }
    }

    private static DefaultRedisScript<Long> script() {
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setLocation(new ClassPathResource("lua/email_login_code_issue_rate_limit.lua"));
        script.setResultType(Long.class);
        return script;
    }
}
