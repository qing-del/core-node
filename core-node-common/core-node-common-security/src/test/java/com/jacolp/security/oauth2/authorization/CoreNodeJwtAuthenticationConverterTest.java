package com.jacolp.security.oauth2.authorization;

import com.jacolp.common.security.oauth2.authorization.CoreNodeJwtAuthenticationConverter;
import com.jacolp.common.security.oauth2.jwt.CoreNodeAccessTokenClaimsValidator;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CoreNodeJwtAuthenticationConverterTest {

    private final CoreNodeJwtAuthenticationConverter converter =
            new CoreNodeJwtAuthenticationConverter(new CoreNodeAccessTokenClaimsValidator());

    @Test
    void mapsOneRoleAndRawWildcardScopesToSpringAuthorities() {
        JwtAuthenticationToken authentication = (JwtAuthenticationToken) converter.convert(
                jwt("admin", "refresh_token", "CREATOR", List.of("note:read", "*:manage")).build());

        assertThat(authentication.getName()).isEqualTo("42");
        assertThat(authentication.getPrincipal()).isInstanceOf(Jwt.class);
        assertThat(authentication.getAuthorities()).extracting(authority -> authority.getAuthority())
                .containsExactly("ROLE_CREATOR", "SCOPE_*:manage", "SCOPE_note:read");
    }

    @Test
    void rejectsMalformedClaimsEvenWhenUsedOutsideTheDecoder() {
        assertThatThrownBy(() -> converter.convert(jwt("user", "password", "USER", List.of("note:read"))
                .claim("roles", List.of("USER", "ADMIN")).build()))
                .isInstanceOf(OAuth2AuthenticationException.class)
                .satisfies(exception -> assertThat(((OAuth2AuthenticationException) exception).getError().getDescription())
                        .isEqualTo("JWT access token claims are invalid"));
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
}
