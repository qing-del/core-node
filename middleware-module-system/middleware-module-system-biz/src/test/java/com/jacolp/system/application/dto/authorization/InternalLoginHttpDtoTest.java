package com.jacolp.system.application.dto.authorization;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jacolp.system.application.authorization.model.EmailLoginCodeIssueRequest;
import com.jacolp.system.application.authorization.model.InternalIssuedTokens;
import com.jacolp.system.application.authorization.model.InternalLoginRequest;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class InternalLoginHttpDtoTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void serializesAndConvertsPasswordLoginWithStrictScope() throws Exception {
        InternalLoginHttpRequest request = objectMapper.readValue("""
                {"client_id":"user","grant_type":"password","username":"alice","password":"secret",
                "email":null,"code":null,"scope":"note:read profile:read"}
                """, InternalLoginHttpRequest.class);

        JsonNode json = objectMapper.readTree(objectMapper.writeValueAsString(request));
        assertThat(json.fieldNames()).toIterable()
                .containsExactly("client_id", "grant_type", "username", "password", "email", "code", "scope",
                        "refresh_token");
        assertThat(json.get("client_id").asText()).isEqualTo("user");
        assertThat(json.get("grant_type").asText()).isEqualTo("password");
        assertThat(json.get("password").asText()).isEqualTo("secret");
        assertThat(json.get("scope").asText()).isEqualTo("note:read profile:read");

        InternalLoginRequest domain = request.toDomain("192.0.2.1");
        assertThat(domain.clientId()).isEqualTo("user");
        assertThat(domain.grantType()).isEqualTo("password");
        assertThat(domain.username()).isEqualTo("alice");
        assertThat(domain.rawPassword()).isEqualTo("secret");
        assertThat(domain.email()).isNull();
        assertThat(domain.rawEmailCode()).isNull();
        assertThat(domain.rawRefreshToken()).isNull();
        assertThat(domain.requestedScopes()).containsExactlyInAnyOrder("note:read", "profile:read");
        assertThat(request.toString()).doesNotContain("alice", "secret", "note:read", "192.0.2.1");
    }

    @Test
    void convertsEmailCodeLoginAndDistinguishesOmittedFromEmptyScope() {
        InternalLoginHttpRequest codeRequest = new InternalLoginHttpRequest(
                "admin", "email-code", null, null, "alice@example.test", "012345", null);
        InternalLoginRequest codeDomain = codeRequest.toDomain("2001:db8::1");

        assertThat(codeDomain.email()).isEqualTo("alice@example.test");
        assertThat(codeDomain.rawEmailCode()).isEqualTo("012345");
        assertThat(codeDomain.username()).isNull();
        assertThat(codeDomain.rawPassword()).isNull();
        assertThat(codeDomain.rawRefreshToken()).isNull();
        assertThat(codeDomain.requestedScopes()).isNull();

        InternalLoginRequest emptyScopes = new InternalLoginHttpRequest(
                "user", "password", "alice", "secret", null, null, "").toDomain("192.0.2.1");
        assertThat(emptyScopes.requestedScopes()).isEqualTo(Set.of());
    }

    @Test
    void rejectsMalformedOrDuplicateScopeAndGrantMutualExclusivity() {
        for (String invalidScope : List.of(" note:read", "note:read ", "note:read  profile:read", "note:read\tprofile:read",
                "note:read\nprofile:read", "note:read\u00A0profile:read", "note:read note:read")) {
            InternalLoginHttpRequest request = new InternalLoginHttpRequest(
                    "user", "password", "alice", "secret", null, null, invalidScope);
            assertThatIllegalArgumentException().isThrownBy(() -> request.toDomain("192.0.2.1"));
        }

        InternalLoginHttpRequest conflicting = new InternalLoginHttpRequest(
                "user", "password", "alice", "secret", "alice@example.test", null, null);
        assertThatIllegalArgumentException().isThrownBy(() -> conflicting.toDomain("192.0.2.1"));
    }

    @Test
    void convertsOnlyRefreshTokenCredentialsAndRedactsThem() {
        String refreshToken = "a".repeat(43);
        InternalLoginHttpRequest request = new InternalLoginHttpRequest(
                "user", "refresh_token", null, null, null, null, "note:read", refreshToken);

        InternalLoginRequest domain = request.toDomain("192.0.2.1");

        assertThat(domain.rawRefreshToken()).isEqualTo(refreshToken);
        assertThat(domain.username()).isNull();
        assertThat(domain.rawPassword()).isNull();
        assertThat(domain.email()).isNull();
        assertThat(domain.rawEmailCode()).isNull();
        assertThat(request.toString()).doesNotContain(refreshToken);
        for (InternalLoginHttpRequest conflicting : List.of(
                new InternalLoginHttpRequest("user", "refresh_token", "alice", null, null, null, null, refreshToken),
                new InternalLoginHttpRequest("user", "refresh_token", null, "secret", null, null, null, refreshToken),
                new InternalLoginHttpRequest("user", "refresh_token", null, null, "a@example.test", null, null, refreshToken),
                new InternalLoginHttpRequest("user", "refresh_token", null, null, null, "012345", null, refreshToken),
                new InternalLoginHttpRequest("user", "refresh_token", null, null, null, null, null, "bad"))) {
            assertThatIllegalArgumentException().isThrownBy(() -> conflicting.toDomain("192.0.2.1"));
        }
    }

    @Test
    void serializesAndConvertsEmailCodeIssueRequestWithoutLeakingEmail() throws Exception {
        EmailLoginCodeHttpRequest request = objectMapper.readValue(
                "{\"client_id\":\"admin\",\"email\":\"Alice@Example.test\"}", EmailLoginCodeHttpRequest.class);

        JsonNode json = objectMapper.readTree(objectMapper.writeValueAsString(request));
        assertThat(json.fieldNames()).toIterable().containsExactly("client_id", "email");
        assertThat(json.get("client_id").asText()).isEqualTo("admin");
        assertThat(json.get("email").asText()).isEqualTo("Alice@Example.test");

        EmailLoginCodeIssueRequest domain = request.toDomain("192.0.2.1");
        assertThat(domain.clientId()).isEqualTo("admin");
        assertThat(domain.email()).isEqualTo("Alice@Example.test");
        assertThat(request.toString()).doesNotContain("Alice@Example.test", "192.0.2.1");
    }

    @Test
    void serializesTokenResponseWithSecondsAndRedactedDiagnostics() throws Exception {
        Instant issuedAt = Instant.parse("2026-08-11T00:00:00Z");
        InternalIssuedTokens tokens = new InternalIssuedTokens(
                "access.raw.token", "refresh.raw.token", "Bearer", issuedAt, issuedAt.plusSeconds(10_800),
                issuedAt.plusSeconds(259_200), List.of("profile:read", "note:read"));

        InternalTokenHttpResponse response = InternalTokenHttpResponse.from(tokens);
        JsonNode json = objectMapper.readTree(objectMapper.writeValueAsString(response));
        assertThat(json.fieldNames()).toIterable()
                .containsExactly("access_token", "token_type", "expires_in", "refresh_token", "scope");
        assertThat(json.get("access_token").asText()).isEqualTo("access.raw.token");
        assertThat(json.get("token_type").asText()).isEqualTo("Bearer");
        assertThat(json.get("expires_in").asLong()).isEqualTo(10_800L);
        assertThat(json.get("refresh_token").asText()).isEqualTo("refresh.raw.token");
        assertThat(json.get("scope").asText()).isEqualTo("note:read profile:read");
        assertThat(json.has("refresh_expires_at")).isFalse();
        assertThat(response.toString()).doesNotContain("access.raw.token", "refresh.raw.token", "note:read");
    }

    @Test
    void rejectsTokenExpiryThatCannotProducePositiveSeconds() {
        Instant issuedAt = Instant.parse("2026-08-11T00:00:00Z");
        InternalIssuedTokens tokens = new InternalIssuedTokens(
                "access", "refresh", "Bearer", issuedAt, issuedAt.plusMillis(999), issuedAt.plusSeconds(1), List.of());

        assertThatIllegalArgumentException().isThrownBy(() -> InternalTokenHttpResponse.from(tokens));
    }
}
