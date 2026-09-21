package com.jacolp.system.application.authorization;

import com.jacolp.system.application.authorization.model.EmailLoginCodeDeliveryRequest;
import com.jacolp.system.application.port.out.EmailLoginCodeDeliveryPort;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class EmailLoginCodeDeliveryRequestTest {

    @Test
    void acceptsWholeMinuteSafeDeliveryInputWithoutLeakingCodeOrEmail() {
        EmailLoginCodeDeliveryRequest request = request(Duration.ofMinutes(10));

        assertThat(request).isNotNull();
        assertThat(request.toString()).doesNotContain("alice@example.test", "012345");
        assertThat(EmailLoginCodeDeliveryPort.class.getDeclaredMethods()[0].toGenericString())
                .doesNotContain(".infrastructure.", "dataobject");
    }

    @Test
    void rejectsInvalidIdentityCredentialAndTtlValues() {
        assertThatIllegalArgumentException().isThrownBy(() -> new EmailLoginCodeDeliveryRequest(
                "core_agent", 7L, "alice@example.test", "alice", "012345", Duration.ofMinutes(1)));
        assertThatIllegalArgumentException().isThrownBy(() -> new EmailLoginCodeDeliveryRequest(
                "user", 0L, "alice@example.test", "alice", "012345", Duration.ofMinutes(1)));
        assertThatIllegalArgumentException().isThrownBy(() -> new EmailLoginCodeDeliveryRequest(
                "user", 7L, " ", "alice", "012345", Duration.ofMinutes(1)));
        assertThatIllegalArgumentException().isThrownBy(() -> new EmailLoginCodeDeliveryRequest(
                "user", 7L, "alice @example.test", "alice", "012345", Duration.ofMinutes(1)));
        assertThatIllegalArgumentException().isThrownBy(() -> new EmailLoginCodeDeliveryRequest(
                "user", 7L, "alice@example.test", " ", "012345", Duration.ofMinutes(1)));
        assertThatIllegalArgumentException().isThrownBy(() -> new EmailLoginCodeDeliveryRequest(
                "user", 7L, "alice@example.test", "alice", "12345", Duration.ofMinutes(1)));
        assertThatIllegalArgumentException().isThrownBy(() -> request(Duration.ofSeconds(30)));
        assertThatIllegalArgumentException().isThrownBy(() -> request(Duration.ofMinutes(11)));
        assertThatIllegalArgumentException().isThrownBy(() -> request(Duration.ofSeconds(Long.MAX_VALUE)));
    }

    private static EmailLoginCodeDeliveryRequest request(Duration ttl) {
        return new EmailLoginCodeDeliveryRequest("user", 7L, "alice@example.test", "alice", "012345", ttl);
    }
}
