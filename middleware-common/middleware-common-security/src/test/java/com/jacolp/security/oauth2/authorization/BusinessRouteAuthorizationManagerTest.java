package com.jacolp.security.oauth2.authorization;

import com.jacolp.common.security.oauth2.authorization.*;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;

class BusinessRouteAuthorizationManagerTest {

    private final BusinessRouteAuthorizationManager manager = new BusinessRouteAuthorizationManager(
            new ImmutableBusinessRouteAuthorizationPolicy(List.of(
                    new BusinessRouteAuthorizationEntry(HttpMethod.GET, "/user/note/{id}", Set.of("note:read"), "user"),
                    new BusinessRouteAuthorizationEntry(HttpMethod.PUT, "/admin/user/user", Set.of("account:manage"), "admin"))));

    @Test
    void allowsOnlyValidatedJwtIdentityWithTheRequiredScope() {
        assertThat(decision(jwt("user", "USER", List.of("note:read")), HttpMethod.GET, "/user/note/9")).isTrue();
        assertThat(decision(jwt("user", "USER", List.of("*:read")), HttpMethod.GET, "/user/note/9")).isTrue();
        assertThat(decision(jwt("user", "ADMIN", List.of("note:read")), HttpMethod.GET, "/user/note/9")).isTrue();
        assertThat(decision(jwt("user", "CREATOR", List.of("note:read")), HttpMethod.GET, "/user/note/9")).isTrue();
        assertThat(decision(jwt("user", "USER", List.of("media:read")), HttpMethod.GET, "/user/note/9")).isFalse();
    }

    @Test
    void failsClosedForWrongClientMissingPolicyOrMalformedJwtClaims() {
        assertThat(decision(jwt("core_agent", "USER", List.of("note:read")), HttpMethod.GET, "/user/note/9")).isFalse();
        assertThat(decision(jwt("user", "USER", List.of("note:read")), HttpMethod.GET, "/user/no-catalogue")).isFalse();
        assertThat(decision(jwt("user", List.of("USER", "ADMIN"), List.of("note:read")), HttpMethod.GET, "/user/note/9")).isFalse();
        assertThat(manager.authorize(() -> null, context(HttpMethod.GET, "/user/note/9")).isGranted()).isFalse();
    }

    @Test
    void keepsClientRoleBoundarySeparateFromScopeAuthorization() {
        assertThat(decision(jwt("admin", "ADMIN", List.of("account:manage")), HttpMethod.PUT, "/admin/user/user")).isTrue();
        assertThat(decision(jwt("admin", "CREATOR", List.of("account:manage")), HttpMethod.PUT, "/admin/user/user")).isTrue();
        assertThat(decision(jwt("admin", "ADMIN", List.of("account:read")), HttpMethod.PUT, "/admin/user/user")).isFalse();
        assertThat(decision(jwt("user", "USER", List.of("*:manage")), HttpMethod.PUT, "/admin/user/user")).isFalse();
    }

    @Test
    void writesGenericJsonErrorsWithoutExceptionOrPolicyDetails() throws Exception {
        MockHttpServletRequest request = request(HttpMethod.GET, "/user/note/9");
        MockHttpServletResponse unauthorized = new MockHttpServletResponse();
        new CoreNodeJsonAuthenticationEntryPoint().commence(request, unauthorized,
                new InsufficientAuthenticationException("raw token validation detail"));
        assertThat(unauthorized.getStatus()).isEqualTo(401);
        assertThat(unauthorized.getContentType()).startsWith("application/json");
        assertThat(unauthorized.getContentAsString()).contains("\"code\":0", "认证失败")
                .doesNotContain("raw token validation detail");

        MockHttpServletResponse forbidden = new MockHttpServletResponse();
        AccessDeniedHandler deniedHandler = new CoreNodeJsonAccessDeniedHandler();
        deniedHandler.handle(request, forbidden, new org.springframework.security.access.AccessDeniedException("scope mismatch"));
        assertThat(forbidden.getStatus()).isEqualTo(403);
        assertThat(forbidden.getContentAsString()).contains("\"code\":0", "无权访问")
                .doesNotContain("scope mismatch");
    }

    private boolean decision(Authentication authentication, HttpMethod method, String path) {
        Supplier<Authentication> supplier = () -> authentication;
        return manager.authorize(supplier, context(method, path)).isGranted();
    }

    private static RequestAuthorizationContext context(HttpMethod method, String path) {
        return new RequestAuthorizationContext(request(method, path));
    }

    private static MockHttpServletRequest request(HttpMethod method, String path) {
        return new MockHttpServletRequest(method.name(), path);
    }

    private static JwtAuthenticationToken jwt(String clientId, String role, List<String> scopes) {
        return jwt(clientId, List.of(role), scopes);
    }

    private static JwtAuthenticationToken jwt(String clientId, List<String> roles, List<String> scopes) {
        Jwt jwt = Jwt.withTokenValue("test-token")
                .header("alg", "RS256")
                .subject("42")
                .claim("username", "alice")
                .claim("client_id", clientId)
                .claim("grant_type", "authorization_code")
                .claim("roles", roles)
                .claim("scope", scopes)
                .issuedAt(Instant.parse("2026-01-01T00:00:00Z"))
                .expiresAt(Instant.parse("2026-01-01T01:00:00Z"))
                .build();
        return new JwtAuthenticationToken(jwt, List.of());
    }
}
