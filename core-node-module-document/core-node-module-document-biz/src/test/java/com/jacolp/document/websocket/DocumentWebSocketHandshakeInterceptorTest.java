package com.jacolp.document.websocket;

import static org.assertj.core.api.Assertions.assertThat;

import com.jacolp.common.security.context.CurrentPrincipal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.http.server.ServletServerHttpResponse;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.socket.handler.TextWebSocketHandler;

class DocumentWebSocketHandshakeInterceptorTest {

    @Test
    void acceptsExistingJwtFromTheSingleBearerSubprotocol() {
        DocumentWebSocketHandshakeInterceptor interceptor = new DocumentWebSocketHandshakeInterceptor(token -> jwt());
        Map<String, Object> attributes = new ConcurrentHashMap<>();

        boolean accepted = interceptor.beforeHandshake(request("bearer.valid-jwt"), response(),
                new TextWebSocketHandler(), attributes);

        assertThat(accepted).isTrue();
        assertThat(DocumentWebSocketHandshakeInterceptor.requirePrincipal(attributes))
                .isEqualTo(new CurrentPrincipal(42L, "alice", "user", "password", List.of("USER"), List.of("document:write")));
    }

    @Test
    void rejectsMissingMultipleAndInvalidBearerSubprotocols() {
        DocumentWebSocketHandshakeInterceptor interceptor = new DocumentWebSocketHandshakeInterceptor(token -> {
            throw new BadJwtException("invalid JWT");
        });

        assertThat(interceptor.beforeHandshake(request(null), response(), new TextWebSocketHandler(), new ConcurrentHashMap<>())).isFalse();
        assertThat(interceptor.beforeHandshake(request("bearer.one, bearer.two"), response(),
                new TextWebSocketHandler(), new ConcurrentHashMap<>())).isFalse();
        assertThat(interceptor.beforeHandshake(request("bearer.invalid"), response(),
                new TextWebSocketHandler(), new ConcurrentHashMap<>())).isFalse();
    }

    @Test
    void acceptsReadOnlyDocumentScopeButRejectsMissingScopeOrWrongClientBoundary() {
        DocumentWebSocketHandshakeInterceptor readOnly = new DocumentWebSocketHandshakeInterceptor(token ->
                jwt("user", List.of("document:read")));
        DocumentWebSocketHandshakeInterceptor missingDocumentScope = new DocumentWebSocketHandshakeInterceptor(token ->
                jwt("user", List.of("note:read")));
        DocumentWebSocketHandshakeInterceptor wrongClient = new DocumentWebSocketHandshakeInterceptor(token ->
                jwt("core_agent", List.of("document:write")));

        assertThat(readOnly.beforeHandshake(request("bearer.read-only"), response(), new TextWebSocketHandler(),
                new ConcurrentHashMap<>())).isTrue();
        assertThat(missingDocumentScope.beforeHandshake(request("bearer.missing-scope"), response(),
                new TextWebSocketHandler(), new ConcurrentHashMap<>())).isFalse();
        assertThat(wrongClient.beforeHandshake(request("bearer.wrong-client"), response(), new TextWebSocketHandler(),
                new ConcurrentHashMap<>())).isFalse();
    }

    private static ServletServerHttpRequest request(String protocol) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        if (protocol != null) {
            request.addHeader("Sec-WebSocket-Protocol", protocol);
        }
        return new ServletServerHttpRequest(request);
    }

    private static ServletServerHttpResponse response() {
        return new ServletServerHttpResponse(new MockHttpServletResponse());
    }

    private static Jwt jwt() {
        return jwt("user", List.of("document:write"));
    }

    private static Jwt jwt(String clientId, List<String> scopes) {
        Instant now = Instant.now();
        return Jwt.withTokenValue("valid-jwt")
                .header("alg", "RS256")
                .subject("42")
                .claim("username", "alice")
                .claim("client_id", clientId)
                .claim("grant_type", "password")
                .claim("roles", List.of("USER"))
                .claim("scope", scopes)
                .issuedAt(now)
                .expiresAt(now.plusSeconds(300))
                .build();
    }
}
