package com.jacolp.document.websocket;

import com.jacolp.common.security.context.CurrentPrincipal;
import com.jacolp.common.security.context.SecurityContextCurrentPrincipalAccessor;
import com.jacolp.common.security.oauth2.authorization.PermissionScopeMatcher;
import java.util.Map;
import java.util.Objects;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

/**
 * 使用现有 RS256 access token 对浏览器 WebSocket 握手进行认证。
 *
 * <p>浏览器原生 WebSocket 无法附加 Authorization 请求头，因此令牌作为唯一的
 * {@code bearer.&lt;JWT&gt;} Sec-WebSocket-Protocol 值传递；不会从查询参数读取令牌。</p>
 */
@Component
public class DocumentWebSocketHandshakeInterceptor implements HandshakeInterceptor {

    public static final String PRINCIPAL_ATTRIBUTE =
            DocumentWebSocketHandshakeInterceptor.class.getName() + ".principal";
    private static final String BEARER_PROTOCOL_PREFIX = "bearer.";
    private static final String SEC_WEBSOCKET_PROTOCOL = "Sec-WebSocket-Protocol";

    private final JwtDecoder jwtDecoder;

    /** 创建使用现有 JWT decoder 和 scope matcher 的握手认证器。 */
    public DocumentWebSocketHandshakeInterceptor(JwtDecoder jwtDecoder) {
        this.jwtDecoder = Objects.requireNonNull(jwtDecoder, "jwtDecoder must not be null");
    }

    /** 从唯一 bearer 子协议取 token，验签并把当前用户主体写入 WebSocket attributes。 */
    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                   WebSocketHandler webSocketHandler, Map<String, Object> attributes) {
        String token = extractAccessToken(request.getHeaders());
        if (token == null) {
            // 浏览器不能可靠地附加 Authorization header；没有唯一 bearer 子协议就拒绝升级连接。
            return false;
        }
        try {
            Jwt jwt = jwtDecoder.decode(token);
            CurrentPrincipal principal = SecurityContextCurrentPrincipalAccessor.fromJwt(jwt);
            boolean canReadDocument = PermissionScopeMatcher.grants(principal.scopes(), "document:read");
            boolean canWriteDocument = PermissionScopeMatcher.grants(principal.scopes(), "document:write");
            if (!"user".equals(principal.clientId()) || (!canReadDocument && !canWriteDocument)) {
                // 握手只校验账号级文档能力；具体 documentId 的读写 ACL 在 JOIN 和更新前再校验。
                return false;
            }
            attributes.put(PRINCIPAL_ATTRIBUTE, principal);
            return true;
        } catch (RuntimeException exception) {
            return false;
        }
    }

    /** 握手已经在升级前完成认证，连接建立后无需额外资源清理。 */
    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                               WebSocketHandler webSocketHandler, Exception exception) {
        // 认证在协议升级前已经完成，这里没有需要清理的 WebSocket 会话。
    }

    /** 从握手 attributes 取出已认证主体，缺失时拒绝继续处理消息。 */
    public static CurrentPrincipal requirePrincipal(Map<String, Object> attributes) {
        Object principal = attributes.get(PRINCIPAL_ATTRIBUTE);
        if (principal instanceof CurrentPrincipal currentPrincipal) {
            return currentPrincipal;
        }
        // 只有握手拦截器成功写入的主体才可信，不能从消息内容或客户端字段补造身份。
        throw new IllegalStateException("authenticated document WebSocket principal is missing");
    }

    /** 只接受单个 {@code bearer.<JWT>} 子协议，不从 query 或 Cookie 读取令牌。 */
    private static String extractAccessToken(HttpHeaders headers) {
        String protocolHeader = headers.getFirst(SEC_WEBSOCKET_PROTOCOL);
        if (protocolHeader == null) {
            // 不接受没有子协议的连接，避免 token 通过 query、Cookie 等未约定渠道进入协同链路。
            return null;
        }
        String[] protocols = protocolHeader.split(",", -1);
        if (protocols.length != 1) {
            // 协议约定只携带一个 bearer.<JWT> 值，多个子协议会造成认证值选择歧义。
            return null;
        }
        String protocol = protocols[0].trim();
        if (!protocol.startsWith(BEARER_PROTOCOL_PREFIX) || protocol.length() == BEARER_PROTOCOL_PREFIX.length()) {
            // 既要匹配 bearer 前缀，也要确保前缀后存在实际 token，空 token 不能送入 JWT decoder。
            return null;
        }
        return protocol.substring(BEARER_PROTOCOL_PREFIX.length());
    }
}
