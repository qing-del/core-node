package com.jacolp.security.oauth2.jwt;

import com.jacolp.common.security.oauth2.jwt.CoreNodeAccessTokenClaimsValidator;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CoreNodeAccessTokenClaimsValidatorTest {

    private final CoreNodeAccessTokenClaimsValidator validator = new CoreNodeAccessTokenClaimsValidator();

    @Test
    void acceptsAllFixedClientGrantAndRoleCombinations() {
        assertValid(jwt("user", "password", "USER", List.of("*:read")).build());
        assertValid(jwt("user", "email-code", "USER", List.of()).build());
        assertValid(jwt("user", "refresh_token", "USER", List.of("note:read")).build());
        assertValid(jwt("user", "password", "ADMIN", List.of("*:read")).build());
        assertValid(jwt("user", "refresh_token", "CREATOR", List.of("*:manage")).build());
        assertValid(jwt("admin", "password", "ADMIN", List.of("*:manage")).build());
        assertValid(jwt("admin", "email-code", "CREATOR", List.of("*:super")).build());
        assertValid(jwt("admin", "refresh_token", "ADMIN", List.of()).build());
        assertValid(jwt("core_agent", "authorization_code", "USER", List.of("note:read")).build());
        assertValid(jwt("core_agent", "refresh_token", "CREATOR", List.of("*:read")).build());
    }

    @Test
    void rejectsUnknownOrCrossClientGrantAndRoleCombinations() {
        assertInvalid(jwt("unknown", "password", "USER", List.of()).build());
        assertInvalid(jwt("user", "authorization_code", "USER", List.of()).build());
        assertInvalid(jwt("admin", "password", "USER", List.of()).build());
        assertInvalid(jwt("core_agent", "password", "USER", List.of()).build());
        assertInvalid(jwt("core_agent", "authorization_code", "VIP", List.of()).build());
    }

    @Test
    void rejectsMalformedIdentityRoleAndScopeClaimsFailClosed() {
        assertInvalid(jwt("user", "password", "USER", List.of(" note:read")).build());
        assertInvalid(jwt("user", "password", "USER", List.of("note:read", "note:read")).build());
        assertInvalid(jwt("user", "password", "USER", List.of("note:read:more")).build());
        assertInvalid(jwt("user", "password", "USER", List.of("note:*read")).build());
        assertInvalid(jwt("user", "password", "USER", List.of("")).build());
        assertInvalid(jwt("user", "password", "USER", List.of()).claim("roles", List.of("USER", "ADMIN")).build());
        assertInvalid(jwt("user", "password", "USER", List.of()).claim("scope", "note:read").build());
        assertInvalid(jwt("user", "password", "USER", List.of()).subject("0").build());
        assertInvalid(jwt("user", "password", "USER", List.of()).claim("username", " ").build());
    }

    private static Jwt.Builder jwt(String clientId, String grantType, String role, List<String> scopes) {
        return Jwt.withTokenValue("test-token")
                .header("alg", "RS256")
                .subject("42")
                .claim("username", "alice")
                .claim("client_id", clientId)
                .claim("grant_type", grantType)
                .claim("roles", List.of(role))
                .claim("scope", scopes)
                .issuedAt(Instant.parse("2026-01-01T00:00:00Z"))
                .expiresAt(Instant.parse("2026-01-01T01:00:00Z"));
    }

    private void assertValid(Jwt jwt) {
        assertThat(validator.validate(jwt).hasErrors()).isFalse();
    }

    private void assertInvalid(Jwt jwt) {
        OAuth2TokenValidatorResult result = validator.validate(jwt);
        assertThat(result.hasErrors()).isTrue();
        assertThat(result.getErrors()).singleElement()
                .extracting(OAuth2Error::getErrorCode, OAuth2Error::getDescription, OAuth2Error::getUri)
                .containsExactly("invalid_token", "JWT access token claims are invalid", null);
    }
}
