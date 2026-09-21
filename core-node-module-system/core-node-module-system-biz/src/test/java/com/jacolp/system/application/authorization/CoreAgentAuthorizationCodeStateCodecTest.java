package com.jacolp.system.application.authorization;

import com.jacolp.system.application.authorization.model.CoreAgentAuthorizationAccountSnapshot;
import com.jacolp.system.application.authorization.model.CoreAgentAuthorizationCodeState;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class CoreAgentAuthorizationCodeStateCodecTest {

    @Test
    void fixedV1SnakeCaseMapRoundTripsNullableEmailWithoutPersistingTheRawCode() {
        CoreAgentAuthorizationCodeStateCodec codec = new CoreAgentAuthorizationCodeStateCodec();
        CoreAgentAuthorizationCodeState state = state(null);

        Map<String, String> values = codec.encode(state);

        assertThat(codec.fieldNames()).containsExactly(
                "schema_version", "client_id", "redirect_uri", "scopes", "code_challenge", "code_challenge_method",
                "original_socket_address", "oauth_state", "issued_at_epoch_millis", "expires_at_epoch_millis",
                "user_id", "username", "role_id", "password_hash", "email_present", "email", "extra_grant_types",
                "status");
        assertThat(values.keySet()).containsExactlyElementsOf(codec.fieldNames());
        assertThat(values).containsEntry("schema_version", "1").containsEntry("email_present", "0")
                .containsEntry("email", "").doesNotContainValue(state.rawCode());
        Assertions.assertThat(codec.decode(state.rawCode(), values)).isEqualTo(state);
        assertThat(codec.toString()).doesNotContain(state.rawCode(), state.codeChallenge(), state.oauthState(),
                state.originalSocketAddress(), state.accountSnapshot().passwordHash());
    }

    @Test
    void codecFailsClosedForSchemaVersionFieldsCanonicalNumbersAndEmailEncodingPollution() {
        CoreAgentAuthorizationCodeStateCodec codec = new CoreAgentAuthorizationCodeStateCodec();
        Map<String, String> baseline = codec.encode(state("private@example.test"));

        Map<String, String> extraField = new LinkedHashMap<>(baseline);
        extraField.put("raw_code", rawCode());
        assertThatIllegalArgumentException().isThrownBy(() -> codec.decode(rawCode(), extraField));
        Map<String, String> version = new LinkedHashMap<>(baseline);
        version.put("schema_version", "01");
        assertThatIllegalArgumentException().isThrownBy(() -> codec.decode(rawCode(), version));
        Map<String, String> number = new LinkedHashMap<>(baseline);
        number.put("user_id", "042");
        assertThatIllegalArgumentException().isThrownBy(() -> codec.decode(rawCode(), number));
        Map<String, String> nullEmail = new LinkedHashMap<>(baseline);
        nullEmail.put("email_present", "0");
        assertThatIllegalArgumentException().isThrownBy(() -> codec.decode(rawCode(), nullEmail));
        Map<String, String> missing = new LinkedHashMap<>(baseline);
        missing.remove("password_hash");
        assertThatIllegalArgumentException().isThrownBy(() -> codec.decode(rawCode(), missing));
    }

    private static CoreAgentAuthorizationCodeState state(String email) {
        Instant issuedAt = Instant.parse("2026-08-11T04:00:00Z");
        return new CoreAgentAuthorizationCodeState(rawCode(), "core_agent", "http://127.0.0.1:9090/oauth/callback",
                List.of("note:read", "sys:read"), challenge(), "S256", "127.0.0.1", "opaque-state", issuedAt,
                issuedAt.plusSeconds(600), new CoreAgentAuthorizationAccountSnapshot(7L, "alice", 2L,
                "$2a$10$" + "a".repeat(53), email, "agent_client", 0));
    }

    private static String rawCode() {
        return code((byte) 3);
    }

    private static String challenge() {
        return code((byte) 4);
    }

    private static String code(byte fill) {
        byte[] bytes = new byte[32];
        java.util.Arrays.fill(bytes, fill);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
