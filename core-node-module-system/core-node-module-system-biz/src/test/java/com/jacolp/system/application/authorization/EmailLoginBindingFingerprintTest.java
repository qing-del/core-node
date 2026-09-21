package com.jacolp.system.application.authorization;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class EmailLoginBindingFingerprintTest {
    private final EmailLoginBindingFingerprint fingerprints = new EmailLoginBindingFingerprint();

    @Test
    void emailNormalizationAndDomainSeparationProduceStableOpaqueFingerprints() {
        String email = fingerprints.email(" Alice@Example.Test ");
        String ip = fingerprints.socketAddress("2001:DB8::1");
        assertThat(email).isEqualTo(fingerprints.email("alice@example.test")).hasSize(43);
        assertThat(ip).isEqualTo(fingerprints.socketAddress("2001:db8:0:0:0:0:0:1")).hasSize(43);
        assertThat(email).isNotEqualTo(ip);
        assertThat(fingerprints.socketAddress("192.0.2.1")).isNotEqualTo(fingerprints.socketAddress("::ffff:192.0.2.1"));
        assertThat(fingerprints.toString()).doesNotContain("alice", "2001");
    }

    @Test
    void rejectsInvalidEmailAndSocketInputsWithoutResolvingNames() {
        assertInvalidEmail(" ");
        assertInvalidEmail("a b@example.test");
        assertInvalidEmail("a\u0000@example.test");
        assertInvalidEmail("a".repeat(101));
        assertInvalidIp("example.test");
        assertInvalidIp("fe80::1%eth0");
        assertInvalidIp("[2001:db8::1]");
        assertInvalidIp("192.168.001.1");
    }

    private void assertInvalidEmail(String value) {
        assertThatIllegalArgumentException().isThrownBy(() -> fingerprints.email(value));
    }
    private void assertInvalidIp(String value) {
        assertThatIllegalArgumentException().isThrownBy(() -> fingerprints.socketAddress(value));
    }
}
