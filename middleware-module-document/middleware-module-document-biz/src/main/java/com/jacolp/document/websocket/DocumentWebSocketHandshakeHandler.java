package com.jacolp.document.websocket;

import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.support.DefaultHandshakeHandler;

/**
 * 协商浏览器用于携带 JWT 的唯一 WebSocket 子协议。
 *
 * <p>浏览器在请求中发送非空 {@code Sec-WebSocket-Protocol} 后，要求服务端在
 * 101 响应中选择并回写其中一个子协议。JWT 的合法性和权限仍由
 * {@link DocumentWebSocketHandshakeInterceptor} 在握手前完成校验。</p>
 */
@Component
@ConditionalOnProperty(prefix = "jacolp.document", name = "enabled", havingValue = "true")
public class DocumentWebSocketHandshakeHandler extends DefaultHandshakeHandler {

    private static final String BEARER_PROTOCOL_PREFIX = "bearer.";

    /** 选择唯一的 bearer 子协议，让浏览器在 101 响应中完成协议回显。 */
    @Override
    protected String selectProtocol(List<String> requestedProtocols, WebSocketHandler webSocketHandler) {
        if (requestedProtocols.size() == 1) {
            // 只回显唯一候选协议，确保浏览器和服务端对 bearer token 的协议协商结果一致。
            String requestedProtocol = requestedProtocols.get(0);
            if (requestedProtocol.startsWith(BEARER_PROTOCOL_PREFIX)
                    && requestedProtocol.length() > BEARER_PROTOCOL_PREFIX.length()) {
                // HandshakeInterceptor 已完成 JWT 验证；这里负责让浏览器完成协议协商。
                return requestedProtocol;
            }
        }
        return super.selectProtocol(requestedProtocols, webSocketHandler);
    }
}
