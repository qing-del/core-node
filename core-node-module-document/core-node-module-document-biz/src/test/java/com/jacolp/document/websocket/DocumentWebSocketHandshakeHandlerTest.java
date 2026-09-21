package com.jacolp.document.websocket;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.web.socket.handler.TextWebSocketHandler;

class DocumentWebSocketHandshakeHandlerTest {

    @Test
    void selectsTheSingleBearerProtocolSoTheBrowserCanCompleteHandshake() {
        ExposedHandshakeHandler handler = new ExposedHandshakeHandler();

        assertThat(handler.select(List.of("bearer.valid-jwt")))
                .isEqualTo("bearer.valid-jwt");
    }

    @Test
    void doesNotSelectUnexpectedOrMultipleProtocols() {
        ExposedHandshakeHandler handler = new ExposedHandshakeHandler();

        assertThat(handler.select(List.of())).isNull();
        assertThat(handler.select(List.of("document-v1"))).isNull();
        assertThat(handler.select(List.of("bearer.one", "bearer.two"))).isNull();
    }

    private static final class ExposedHandshakeHandler extends DocumentWebSocketHandshakeHandler {

        private String select(List<String> requestedProtocols) {
            return selectProtocol(requestedProtocols, new TextWebSocketHandler());
        }
    }
}
