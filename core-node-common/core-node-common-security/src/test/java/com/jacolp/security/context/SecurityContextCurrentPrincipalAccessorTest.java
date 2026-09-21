package com.jacolp.security.context;

import com.jacolp.common.core.exception.AuthenticationException;
import com.jacolp.common.security.context.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SecurityContextCurrentPrincipalAccessorTest {
    private final CurrentPrincipalAccessor accessor = new SecurityContextCurrentPrincipalAccessor();

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void mapsCompleteJwtClaimsAndPreservesWildcardScopesInImmutableCollections() {
        authenticate(jwt("123", "alice", "user_client", "password", List.of("USER"), List.of("*:read", "note:read")));

        CurrentPrincipal principal = accessor.currentPrincipal().orElseThrow();

        assertThat(principal.userId()).isEqualTo(123L);
        assertThat(principal.username()).isEqualTo("alice");
        assertThat(principal.clientId()).isEqualTo("user_client");
        assertThat(principal.grantType()).isEqualTo("password");
        assertThat(principal.roles()).containsExactly("USER");
        assertThat(principal.scopes()).containsExactly("*:read", "note:read");
        assertThat(principal.isAdministrative()).isFalse();
        assertThat(principal.toString()).doesNotContain("opaque.jwt");
        assertThatThrownBy(() -> principal.roles().add("ADMIN")).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> principal.scopes().add("other:read")).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void identifiesCreatorAndAdminJwtRolesCaseInsensitively() {
        authenticate(jwt("1", "creator", "admin_client", "admin_password", List.of("creator"), List.of()));
        assertThat(accessor.currentPrincipal().orElseThrow().isAdministrative()).isTrue();
        authenticate(jwt("2", "admin", "admin_client", "admin_password", List.of("ADMIN"), List.of()));
        assertThat(accessor.currentPrincipal().orElseThrow().isAdministrative()).isTrue();
    }

    @Test
    void legacyAuthoritiesOverrideSecurityPrincipalIdentity() {
        authenticate(new SecurityPrincipal(1L, SecurityIdentity.ADMIN), List.of(new SimpleGrantedAuthority("ROLE_USER")));
        CurrentPrincipal userAuthority = accessor.currentPrincipal().orElseThrow();
        assertThat(userAuthority.roles()).containsExactly("USER");
        assertThat(userAuthority.isAdministrative()).isFalse();
        assertThat(userAuthority.username()).isNull();
        assertThat(userAuthority.clientId()).isNull();
        assertThat(userAuthority.grantType()).isNull();

        authenticate(new SecurityPrincipal(2L, SecurityIdentity.USER), List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
        assertThat(accessor.currentPrincipal().orElseThrow().isAdministrative()).isTrue();
        authenticate(new SecurityPrincipal(3L, SecurityIdentity.ADMIN), List.of(new SimpleGrantedAuthority("ROLE_ACTIVATION")));
        assertThat(accessor.currentPrincipal().orElseThrow().isAdministrative()).isFalse();
    }

    @Test
    void returnsEmptyForAbsentUnauthenticatedAndUnsupportedPrincipals() {
        assertThat(accessor.currentPrincipal()).isEmpty();
        SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.unauthenticated(
                new SecurityPrincipal(1L, SecurityIdentity.USER), null));
        assertThat(accessor.currentPrincipal()).isEmpty();
        authenticate("unsupported", List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
        assertThat(accessor.currentPrincipal()).isEmpty();
    }

    @Test
    void rejectsMalformedJwtClaimsFailClosedWithoutLegacyFallback() {
        assertMalformed(jwt("not-a-number", "alice", "client", "password", List.of("USER"), List.of()));
        assertMalformed(jwt("1", "alice", "client", "password", List.of("USER", "ADMIN"), List.of()));
        assertMalformed(jwtWithClaim("scope", "note:read"));
        assertMalformed(jwtWithClaim("username", 123));
    }

    private void assertMalformed(Jwt jwt) {
        authenticate(jwt);
        assertThatThrownBy(accessor::currentPrincipal).isInstanceOf(AuthenticationException.class);
    }

    private static Jwt jwt(String subject, String username, String clientId, String grantType, List<String> roles, List<String> scopes) {
        return Jwt.withTokenValue("opaque.jwt")
                .header("alg", "RS256")
                .subject(subject)
                .claim("username", username)
                .claim("client_id", clientId)
                .claim("grant_type", grantType)
                .claim("roles", roles)
                .claim("scope", scopes)
                .issuedAt(Instant.parse("2026-08-10T00:00:00Z"))
                .expiresAt(Instant.parse("2026-08-10T01:00:00Z"))
                .build();
    }

    private static Jwt jwtWithClaim(String name, Object value) {
        Jwt.Builder builder = Jwt.withTokenValue("opaque.jwt")
                .header("alg", "RS256")
                .subject("1")
                .claim("username", "alice")
                .claim("client_id", "client")
                .claim("grant_type", "password")
                .claim("roles", List.of("USER"))
                .claim("scope", List.of("note:read"))
                .issuedAt(Instant.parse("2026-08-10T00:00:00Z"))
                .expiresAt(Instant.parse("2026-08-10T01:00:00Z"));
        return builder.claim(name, value).build();
    }

    private static void authenticate(Jwt jwt) {
        authenticate(jwt, List.of(new SimpleGrantedAuthority("ROLE_USER")));
    }

    private static void authenticate(Object principal, List<SimpleGrantedAuthority> authorities) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(principal, null, authorities));
    }
}
