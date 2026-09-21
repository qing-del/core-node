package com.jacolp.system.application.authorization;

import com.jacolp.system.application.authorization.model.CoreAgentPendingAuthorizationState;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class CoreAgentPendingAuthorizationStateCodecTest {

    private static final Instant NOW = Instant.parse("2026-08-11T08:00:00Z");

    @Test
    void roundTripsScopeOmissionWithACompleteFixedFieldSetAndNoHandleOrCode() {
        CoreAgentPendingAuthorizationState state = state(null);
        CoreAgentPendingAuthorizationStateCodec codec = new CoreAgentPendingAuthorizationStateCodec();

        Map<String, String> values = codec.encode(state);

        assertThat(values).containsExactlyInAnyOrderEntriesOf(Map.ofEntries(
                Map.entry("schema_version", "1"), Map.entry("client_id", "core_agent"),
                Map.entry("redirect_uri", "http://127.0.0.1:9090/oauth/callback"),
                Map.entry("requested_scopes_present", "0"), Map.entry("requested_scopes", ""),
                Map.entry("code_challenge", opaque((byte) 3)), Map.entry("code_challenge_method", "S256"),
                Map.entry("oauth_state", "browser-state"), Map.entry("original_socket_address", "192.0.2.4"),
                Map.entry("user_id", "7"), Map.entry("session_id", "session-id"),
                Map.entry("issued_at_epoch_millis", Long.toString(NOW.toEpochMilli())),
                Map.entry("expires_at_epoch_millis", Long.toString(NOW.plus(Duration.ofMinutes(10)).toEpochMilli()))));
        assertThat(values.keySet()).containsExactlyElementsOf(codec.fieldNames());
        assertThat(values.keySet()).noneMatch(field -> field.contains("code") && field.contains("raw"));
        Assertions.assertThat(codec.decode(values)).isEqualTo(state);
    }

    @Test
    void canonicalizesSpecifiedScopesAndFailsClosedForPollutionOrAmbiguousNullability() {
        CoreAgentPendingAuthorizationStateCodec codec = new CoreAgentPendingAuthorizationStateCodec();
        CoreAgentPendingAuthorizationState state = state(List.of("sys:read", "note:read"));
        Assertions.assertThat(codec.decode(codec.encode(state)).requestedScopes()).containsExactly("note:read", "sys:read");

        Map<String, String> polluted = new LinkedHashMap<>(codec.encode(state));
        polluted.put("raw_code", opaque((byte) 9));
        assertThatIllegalArgumentException().isThrownBy(() -> codec.decode(polluted));

        Map<String, String> ambiguous = new LinkedHashMap<>(codec.encode(state));
        ambiguous.put("requested_scopes_present", "0");
        assertThatIllegalArgumentException().isThrownBy(() -> codec.decode(ambiguous));
    }

    @Test
    void stateRejectsUnboundSessionAndItsStringFormRedactsSensitiveBindings() {
        assertThatIllegalArgumentException().isThrownBy(() -> new CoreAgentPendingAuthorizationState("core_agent",
                "http://127.0.0.1:9090/oauth/callback", null, opaque((byte) 3), "S256", "state", "192.0.2.4",
                7L, "\n", NOW, NOW.plus(Duration.ofMinutes(10))));
        assertThat(state(null).toString()).doesNotContain("browser-state", "192.0.2.4", "session-id", opaque((byte) 3));
    }

    private static CoreAgentPendingAuthorizationState state(List<String> requestedScopes) {
        return new CoreAgentPendingAuthorizationState("core_agent", "http://127.0.0.1:9090/oauth/callback",
                requestedScopes, opaque((byte) 3), "S256", "browser-state", "192.0.2.4", 7L, "session-id", NOW,
                NOW.plus(Duration.ofMinutes(10)));
    }

    private static String opaque(byte fill) {
        byte[] bytes = new byte[32];
        java.util.Arrays.fill(bytes, fill);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
