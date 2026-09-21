package com.jacolp.document.websocket;

import com.jacolp.common.web.config.CorsProperties;
import java.util.Objects;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

/** 仅在文档模块启用时注册需要认证的文档协作 WebSocket 端点。 */
@Configuration(proxyBeanMethods = false)
@EnableWebSocket
@ConditionalOnProperty(prefix = "jacolp.document", name = "enabled", havingValue = "true")
public class DocumentWebSocketConfiguration implements WebSocketConfigurer {

    private final DocumentWebSocketHandler handler;
    private final DocumentWebSocketHandshakeInterceptor handshakeInterceptor;
    private final DocumentWebSocketHandshakeHandler handshakeHandler;
    private final CorsProperties corsProperties;

    /** 创建复用现有 CORS 配置和 JWT 握手组件的 WebSocket 配置。 */
    public DocumentWebSocketConfiguration(DocumentWebSocketHandler handler,
                                          DocumentWebSocketHandshakeInterceptor handshakeInterceptor,
                                          DocumentWebSocketHandshakeHandler handshakeHandler,
                                          CorsProperties corsProperties) {
        this.handler = Objects.requireNonNull(handler, "handler must not be null");
        this.handshakeInterceptor = Objects.requireNonNull(handshakeInterceptor, "handshakeInterceptor must not be null");
        this.handshakeHandler = Objects.requireNonNull(handshakeHandler, "handshakeHandler must not be null");
        this.corsProperties = Objects.requireNonNull(corsProperties, "corsProperties must not be null");
    }

    /** 注册唯一的文档协作端点，并限制其允许来源。 */
    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(handler, "/ws/document")
                .setHandshakeHandler(handshakeHandler)
                .addInterceptors(handshakeInterceptor)
                .setAllowedOriginPatterns(corsProperties.getAllowedOriginPatterns().toArray(String[]::new));
    }
}
