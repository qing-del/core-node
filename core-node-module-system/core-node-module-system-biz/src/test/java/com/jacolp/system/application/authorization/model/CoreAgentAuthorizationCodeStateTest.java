package com.jacolp.system.application.authorization.model;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CoreAgentAuthorizationCodeStateTest {

    private static final Instant ISSUED_AT = Instant.parse("2026-08-11T04:00:00Z");
    private static final String RAW_CODE = base64Url((byte) 1);
    private static final String CHALLENGE = base64Url((byte) 2);
    private static final String OAUTH_STATE = "opaque-client-state";
    private static final String SOCKET_IP = "2001:db8::8";
    private static final String PASSWORD_HASH = "$2a$10$never-log-this-password-hash";
    private static final String EMAIL = "private@example.test";

    @Test
    void validStateSortsAndDefensivelyCopiesScopesWhilePreservingTheRawSocketLiteral() {
        CoreAgentAuthorizationCodeState state = validState(List.of("sys:read", "note:read", "media:read"));

        assertThat(state.scopes()).containsExactly("media:read", "note:read", "sys:read");
        assertThatThrownBy(() -> state.scopes().add("note:write"))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThat(state.originalSocketAddress()).isEqualTo(SOCKET_IP);
        assertThat(state.expiresAt()).isEqualTo(ISSUED_AT.plus(Duration.ofMinutes(10)));
    }

    @Test
    void codeClientRedirectScopesPkceSocketStateAndTtlAreStrict() {
        assertThatIllegalArgumentException().isThrownBy(() -> state("too-short", "core_agent", redirectUri(),
                validScopes(), CHALLENGE, "S256", SOCKET_IP, OAUTH_STATE, expiresAt(), snapshot()));
        assertThatIllegalArgumentException().isThrownBy(() -> state(RAW_CODE, "user", redirectUri(), validScopes(),
                CHALLENGE, "S256", SOCKET_IP, OAUTH_STATE, expiresAt(), snapshot()));
        assertThatIllegalArgumentException().isThrownBy(() -> state(RAW_CODE, "core_agent", "https://user@host.test/cb",
                validScopes(), CHALLENGE, "S256", SOCKET_IP, OAUTH_STATE, expiresAt(), snapshot()));
        assertThatIllegalArgumentException().isThrownBy(() -> state(RAW_CODE, "core_agent", "https://host.test/cb#fragment",
                validScopes(), CHALLENGE, "S256", SOCKET_IP, OAUTH_STATE, expiresAt(), snapshot()));
        assertThatIllegalArgumentException().isThrownBy(() -> state(RAW_CODE, "core_agent", redirectUri(),
                List.of("note:read", "note:read"), CHALLENGE, "S256", SOCKET_IP, OAUTH_STATE, expiresAt(), snapshot()));
        assertThatIllegalArgumentException().isThrownBy(() -> state(RAW_CODE, "core_agent", redirectUri(),
                List.of("note:read:more"), CHALLENGE, "S256", SOCKET_IP, OAUTH_STATE, expiresAt(), snapshot()));
        assertThatIllegalArgumentException().isThrownBy(() -> state(RAW_CODE, "core_agent", redirectUri(), validScopes(),
                "invalid", "S256", SOCKET_IP, OAUTH_STATE, expiresAt(), snapshot()));
        assertThatIllegalArgumentException().isThrownBy(() -> state(RAW_CODE, "core_agent", redirectUri(), validScopes(),
                CHALLENGE, "plain", SOCKET_IP, OAUTH_STATE, expiresAt(), snapshot()));
        assertThatIllegalArgumentException().isThrownBy(() -> state(RAW_CODE, "core_agent", redirectUri(), validScopes(),
                CHALLENGE, "S256", "proxy.example.test", OAUTH_STATE, expiresAt(), snapshot()));
        assertThatIllegalArgumentException().isThrownBy(() -> state(RAW_CODE, "core_agent", redirectUri(), validScopes(),
                CHALLENGE, "S256", SOCKET_IP, "state\nheader", expiresAt(), snapshot()));
        assertThatIllegalArgumentException().isThrownBy(() -> state(RAW_CODE, "core_agent", redirectUri(), validScopes(),
                CHALLENGE, "S256", SOCKET_IP, "x".repeat(8193), expiresAt(), snapshot()));
        assertThatIllegalArgumentException().isThrownBy(() -> state(RAW_CODE, "core_agent", redirectUri(), validScopes(),
                CHALLENGE, "S256", SOCKET_IP, OAUTH_STATE, ISSUED_AT.plus(Duration.ofMinutes(9)), snapshot()));
    }

    @Test
    void snapshotAndAllDiagnosticRepresentationsRedactSensitiveValues() {
        assertThatIllegalArgumentException().isThrownBy(() -> new CoreAgentAuthorizationAccountSnapshot(0L, "user", 1L,
                PASSWORD_HASH, EMAIL, "", 0));
        assertThatIllegalArgumentException().isThrownBy(() -> new CoreAgentAuthorizationAccountSnapshot(1L, "user", 1L,
                PASSWORD_HASH, " ", "", 0));

        CoreAgentAuthorizationAccountSnapshot account = snapshot();
        CoreAgentAuthorizationCodeState state = validState(validScopes());
        IssuedCoreAgentAuthorizationCode issued = new IssuedCoreAgentAuthorizationCode(RAW_CODE, expiresAt());

        assertRedacted(account.toString(), PASSWORD_HASH, EMAIL);
        assertRedacted(state.toString(), RAW_CODE, CHALLENGE, SOCKET_IP, OAUTH_STATE, PASSWORD_HASH, EMAIL);
        assertRedacted(issued.toString(), RAW_CODE);
        IllegalArgumentException exception = org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> state("sensitive-invalid-code", "core_agent", redirectUri(), validScopes(), CHALLENGE, "S256",
                        SOCKET_IP, OAUTH_STATE, expiresAt(), snapshot()));
        assertThat(exception.toString()).doesNotContain("sensitive-invalid-code", CHALLENGE, SOCKET_IP, OAUTH_STATE);
    }

    @Test
    void issuedCodeRequiresCanonical256BitBase64UrlAndExpiry() {
        assertThatIllegalArgumentException().isThrownBy(() -> new IssuedCoreAgentAuthorizationCode("not-a-code", expiresAt()));
        assertThatIllegalArgumentException().isThrownBy(() -> new IssuedCoreAgentAuthorizationCode(RAW_CODE, null));
    }

    private static CoreAgentAuthorizationCodeState validState(List<String> scopes) {
        return state(RAW_CODE, "core_agent", redirectUri(), scopes, CHALLENGE, "S256", SOCKET_IP, OAUTH_STATE,
                expiresAt(), snapshot());
    }

    private static CoreAgentAuthorizationCodeState state(String rawCode, String clientId, String redirectUri,
                                                          List<String> scopes, String challenge, String method,
                                                          String socketIp, String oauthState, Instant expiresAt,
                                                          CoreAgentAuthorizationAccountSnapshot snapshot) {
        return new CoreAgentAuthorizationCodeState(rawCode, clientId, redirectUri, scopes, challenge, method, socketIp,
                oauthState, ISSUED_AT, expiresAt, snapshot);
    }

    private static List<String> validScopes() {
        return List.of("note:read", "sys:read");
    }

    private static CoreAgentAuthorizationAccountSnapshot snapshot() {
        return new CoreAgentAuthorizationAccountSnapshot(42L, "alice", 2L, PASSWORD_HASH, EMAIL, "agent_client", 0);
    }

    private static Instant expiresAt() {
        return ISSUED_AT.plus(Duration.ofMinutes(10));
    }

    private static String redirectUri() {
        return "http://127.0.0.1:9090/oauth/callback";
    }

    private static String base64Url(byte fill) {
        byte[] bytes = new byte[32];
        java.util.Arrays.fill(bytes, fill);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static void assertRedacted(String value, String... secrets) {
        assertThat(value).contains("<redacted>");
        assertThat(value).doesNotContain(secrets);
    }
}
