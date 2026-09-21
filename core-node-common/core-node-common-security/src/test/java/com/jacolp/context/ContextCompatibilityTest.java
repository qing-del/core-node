package com.jacolp.context;

import com.jacolp.common.core.exception.AuthenticationException;
import com.jacolp.common.security.context.BaseContext;
import com.jacolp.common.security.context.PermissionContext;
import com.jacolp.common.security.context.SecurityIdentity;
import com.jacolp.common.security.context.SecurityPrincipal;
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

class ContextCompatibilityTest {

    @AfterEach
    void clearContexts() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void activationSecurityContextIsVisibleThroughLegacyFacade() {
        authenticate(new SecurityPrincipal(101L, SecurityIdentity.ACTIVATION), SecurityIdentity.ACTIVATION);

        assertThat(BaseContext.getCurrentId()).isEqualTo(101L);
        assertThat(PermissionContext.isAdmin()).isFalse();
    }

    @Test
    void absentSecurityContextRejectsIdentityReads() {
        assertThat(BaseContext.getCurrentIdWithoutValid()).isNull();
        assertThat(PermissionContext.isAdmin()).isFalse();
        assertThatThrownBy(BaseContext::getCurrentId)
                .isInstanceOf(AuthenticationException.class)
                .hasMessage("当前登录信息已失效");
    }

    @Test
    void holderAuthorityOverridesPrincipalIdentity() {
        authenticate(new SecurityPrincipal(1L, SecurityIdentity.ADMIN), SecurityIdentity.USER);
        assertThat(PermissionContext.isAdmin()).isFalse();

        authenticate(new SecurityPrincipal(2L, SecurityIdentity.USER), SecurityIdentity.ADMIN);
        assertThat(PermissionContext.isAdmin()).isTrue();
    }

    @Test
    void completeJwtHolderIdentityIsVisibleThroughLegacyFacades() {
        authenticateJwt(jwt("101", "ADMIN"));
        assertThat(BaseContext.getCurrentId()).isEqualTo(101L);
        assertThat(PermissionContext.isAdmin()).isTrue();
        authenticateJwt(jwt("102", "USER"));
        assertThat(BaseContext.getCurrentId()).isEqualTo(102L);
        assertThat(PermissionContext.isAdmin()).isFalse();
    }

    @Test
    void malformedJwtFailsClosed() {
        authenticateJwt(jwt("not-a-number", "ADMIN"));

        assertThatThrownBy(BaseContext::getCurrentId).isInstanceOf(AuthenticationException.class);
        assertThatThrownBy(PermissionContext::isAdmin).isInstanceOf(AuthenticationException.class);
    }

    private static void authenticateJwt(Jwt jwt) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                jwt, null, List.of(new SimpleGrantedAuthority("ROLE_USER"))));
    }

    private static void authenticate(SecurityPrincipal principal, SecurityIdentity authority) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                principal, null, List.of(new SimpleGrantedAuthority(authority.authority()))));
    }

    private static Jwt jwt(String subject, String role) {
        return Jwt.withTokenValue("opaque.jwt")
                .header("alg", "RS256")
                .subject(subject)
                .claim("username", "alice")
                .claim("client_id", "user_client")
                .claim("grant_type", "password")
                .claim("roles", List.of(role))
                .claim("scope", List.of("*:read"))
                .issuedAt(Instant.parse("2026-08-10T00:00:00Z"))
                .expiresAt(Instant.parse("2026-08-10T01:00:00Z"))
                .build();
    }
}
