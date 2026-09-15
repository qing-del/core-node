package com.jacolp.system.application.authorization;

import com.jacolp.system.application.authorization.model.EmailLoginCodeState;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EmailLoginCodeStateCodecTest {
    private static final String FINGERPRINT = "A".repeat(43);
    private static final String VERIFIER = "$2a$10$" + "a".repeat(53);
    private final EmailLoginCodeStateCodec codec = new EmailLoginCodeStateCodec();

    @Test
    void roundTripsBoundaryAttemptsWithStableImmutableAndSecretFreeMap() {
        for (int attempts : new int[]{0, 4}) {
            EmailLoginCodeState state = state(attempts);
            Map<String, String> map = codec.encode(state);
            assertThat(map.keySet()).containsExactlyElementsOf(codec.fieldNames());
            assertThat(codec.decode(map)).isEqualTo(state);
            assertThatThrownBy(() -> map.put("x", "y")).isInstanceOf(UnsupportedOperationException.class);
            assertThat(map.keySet()).doesNotContain("raw_code", "code", "email", "password", "token", "secret");
            assertThat(state.toString()).doesNotContain(FINGERPRINT, VERIFIER);
        }
    }

    @Test
    void modelRejectsInvalidFieldsAndTimeOrdering() {
        assertThatIllegalArgumentException().isThrownBy(() -> new EmailLoginCodeState("core", 1L, FINGERPRINT, VERIFIER, 0, Instant.EPOCH, Instant.ofEpochMilli(1)));
        assertThatIllegalArgumentException().isThrownBy(() -> new EmailLoginCodeState("user", -1L, FINGERPRINT, VERIFIER, 0, Instant.EPOCH, Instant.ofEpochMilli(1)));
        assertThatIllegalArgumentException().isThrownBy(() -> new EmailLoginCodeState("user", 1L, "bad", VERIFIER, 0, Instant.EPOCH, Instant.ofEpochMilli(1)));
        assertThatIllegalArgumentException().isThrownBy(() -> new EmailLoginCodeState("user", 1L, FINGERPRINT, "bad", 0, Instant.EPOCH, Instant.ofEpochMilli(1)));
        assertThatIllegalArgumentException().isThrownBy(() -> new EmailLoginCodeState("user", 1L, FINGERPRINT, VERIFIER, -1, Instant.EPOCH, Instant.ofEpochMilli(1)));
        assertThatIllegalArgumentException().isThrownBy(() -> new EmailLoginCodeState("user", 1L, FINGERPRINT, VERIFIER, 5, Instant.EPOCH, Instant.ofEpochMilli(1)));
        assertThatIllegalArgumentException().isThrownBy(() -> new EmailLoginCodeState("user", 1L, FINGERPRINT, VERIFIER, 0, Instant.EPOCH, Instant.EPOCH));
    }

    @Test
    void malformedMapsFailClosed() {
        assertThatIllegalArgumentException().isThrownBy(() -> codec.decode(null));
        assertInvalid("user_id", "01");
        assertInvalid("user_id", "-1");
        assertInvalid("user_id", "999999999999999999999999");
        assertInvalid("failed_attempts", "5");
        assertInvalid("failed_attempts", "abc");
        assertInvalid("issued_at_epoch_millis", "-0");
        assertInvalid("expires_at_epoch_millis", "not-epoch");
        Map<String, String> missing = map(); missing.remove("client_id");
        assertThatIllegalArgumentException().isThrownBy(() -> codec.decode(missing));
        Map<String, String> extra = map(); extra.put("extra", "x");
        assertThatIllegalArgumentException().isThrownBy(() -> codec.decode(extra));
        Map<String, String> nullValue = map(); nullValue.put("client_id", null);
        assertThatIllegalArgumentException().isThrownBy(() -> codec.decode(nullValue));
    }

    private void assertInvalid(String field, String value) {
        Map<String, String> map = map(); map.put(field, value);
        assertThatIllegalArgumentException().isThrownBy(() -> codec.decode(map));
    }
    private Map<String, String> map() { return new LinkedHashMap<>(codec.encode(state(0))); }
    private static EmailLoginCodeState state(int attempts) { return new EmailLoginCodeState("user", 7L, FINGERPRINT, VERIFIER, attempts, Instant.ofEpochMilli(1_000), Instant.ofEpochMilli(2_000)); }
}
