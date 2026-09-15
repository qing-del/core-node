package com.jacolp.system.application.authorization.model;

import java.time.Duration;
import java.util.regex.Pattern;

/** Immutable two-dimensional email-code issuance rate-limit request. */
public record EmailLoginCodeIssueRateLimitRequest(
        String emailFingerprint,
        String ipFingerprint,
        Duration cooldown,
        Duration window,
        Integer maxIssues) {

    private static final Pattern FINGERPRINT = Pattern.compile("[A-Za-z0-9_-]{43}");

    public EmailLoginCodeIssueRateLimitRequest {
        if (emailFingerprint == null || !FINGERPRINT.matcher(emailFingerprint).matches()
                || ipFingerprint == null || !FINGERPRINT.matcher(ipFingerprint).matches()
                || cooldown == null || cooldown.compareTo(Duration.ofSeconds(60)) < 0
                || window == null || window.compareTo(Duration.ofHours(1)) < 0 || window.compareTo(cooldown) < 0
                || maxIssues == null || maxIssues < 1 || maxIssues > 5) {
            throw new IllegalArgumentException("Invalid email-code issue rate-limit request");
        }
        requirePositiveMilliseconds(cooldown);
        requirePositiveMilliseconds(window);
    }

    @Override
    public String toString() {
        return "EmailLoginCodeIssueRateLimitRequest[cooldown=" + cooldown + ", window=" + window
                + ", maxIssues=" + maxIssues + ']';
    }

    private static void requirePositiveMilliseconds(Duration duration) {
        try {
            if (duration.toMillis() <= 0) {
                throw new IllegalArgumentException("Invalid email-code issue rate-limit request");
            }
        } catch (ArithmeticException exception) {
            throw new IllegalArgumentException("Invalid email-code issue rate-limit request", exception);
        }
    }
}
