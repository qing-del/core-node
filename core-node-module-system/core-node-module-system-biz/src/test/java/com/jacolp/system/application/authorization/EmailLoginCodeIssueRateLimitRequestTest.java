package com.jacolp.system.application.authorization;

import com.jacolp.system.application.authorization.model.EmailLoginCodeIssueRateLimitRequest;
import com.jacolp.system.application.port.out.EmailLoginCodeIssueRateLimitDecision;
import com.jacolp.system.application.port.out.EmailLoginCodeIssueRateLimiter;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class EmailLoginCodeIssueRateLimitRequestTest {

    private static final String EMAIL = "A".repeat(43);
    private static final String IP = "B".repeat(43);

    @Test
    void acceptsDefaultAndStricterLimits() {
        Assertions.assertThat(request(Duration.ofSeconds(60), Duration.ofHours(1), 5)).isNotNull();
        Assertions.assertThat(request(Duration.ofMinutes(2), Duration.ofHours(2), 1)).isNotNull();
    }

    @Test
    void rejectsInvalidFingerprintFields() {
        assertThatIllegalArgumentException().isThrownBy(() ->
                new EmailLoginCodeIssueRateLimitRequest(null, IP,
                        Duration.ofMinutes(1), Duration.ofHours(1), 1));
        assertThatIllegalArgumentException().isThrownBy(() ->
                new EmailLoginCodeIssueRateLimitRequest("bad", IP,
                        Duration.ofMinutes(1), Duration.ofHours(1), 1));
        assertThatIllegalArgumentException().isThrownBy(() ->
                new EmailLoginCodeIssueRateLimitRequest(EMAIL, null,
                        Duration.ofMinutes(1), Duration.ofHours(1), 1));
        assertThatIllegalArgumentException().isThrownBy(() ->
                new EmailLoginCodeIssueRateLimitRequest(EMAIL, "bad",
                        Duration.ofMinutes(1), Duration.ofHours(1), 1));
    }

    @Test
    void rejectsUnsafeDurationsAndLimits() {
        assertThatIllegalArgumentException().isThrownBy(() ->
                request(Duration.ofSeconds(59), Duration.ofHours(1), 1));
        assertThatIllegalArgumentException().isThrownBy(() ->
                request(Duration.ofMinutes(1), Duration.ofMinutes(59), 1));
        assertThatIllegalArgumentException().isThrownBy(() ->
                request(Duration.ofHours(2), Duration.ofHours(1), 1));
        assertThatIllegalArgumentException().isThrownBy(() ->
                request(Duration.ofMinutes(1), Duration.ofHours(1), null));
        assertThatIllegalArgumentException().isThrownBy(() ->
                request(Duration.ofMinutes(1), Duration.ofHours(1), 0));
        assertThatIllegalArgumentException().isThrownBy(() ->
                request(Duration.ofMinutes(1), Duration.ofHours(1), 6));
        assertThatIllegalArgumentException().isThrownBy(() ->
                request(Duration.ofSeconds(Long.MAX_VALUE), Duration.ofSeconds(Long.MAX_VALUE), 1));
    }

    @Test
    void doesNotLeakFingerprintsAndKeepsThePortInfrastructureNeutral() {
        assertThat(request(Duration.ofMinutes(1), Duration.ofHours(1), 1).toString())
                .doesNotContain(EMAIL, IP);
        assertThat(EmailLoginCodeIssueRateLimiter.class.getDeclaredMethods()[0].toGenericString())
                .doesNotContain(".infrastructure.", "dataobject");
        Assertions.assertThat(EmailLoginCodeIssueRateLimitDecision.values()).containsExactly(
                EmailLoginCodeIssueRateLimitDecision.ALLOWED,
                EmailLoginCodeIssueRateLimitDecision.COOLDOWN,
                EmailLoginCodeIssueRateLimitDecision.WINDOW_LIMIT);
    }

    private static EmailLoginCodeIssueRateLimitRequest request(Duration cooldown, Duration window, Integer max) {
        return new EmailLoginCodeIssueRateLimitRequest(EMAIL, IP, cooldown, window, max);
    }
}
