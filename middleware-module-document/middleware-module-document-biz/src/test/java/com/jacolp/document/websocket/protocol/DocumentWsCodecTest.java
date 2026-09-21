package com.jacolp.document.websocket.protocol;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jacolp.document.config.DocumentProperties;
import java.nio.ByteBuffer;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.web.socket.TextMessage;

class DocumentWsCodecTest {

    private final DocumentWsCodec codec = new DocumentWsCodec(new ObjectMapper(), new DocumentProperties());

    @Test
    void roundTripsTheOuterHeaderWithoutChangingOpaqueYjsBytes() {
        UUID eventId = UUID.fromString("123e4567-e89b-12d3-a456-426614174000");
        byte[] yjsUpdate = new byte[] {0, 1, -1, 127, -128};

        DocumentWsBinaryFrame decoded = codec.decodeBinary(codec.encodeBinary(
                new DocumentWsBinaryFrame(DocumentWsFrameType.CLIENT_UPDATE, eventId, yjsUpdate)));

        assertThat(decoded.type()).isEqualTo(DocumentWsFrameType.CLIENT_UPDATE);
        assertThat(decoded.eventId()).isEqualTo(eventId);
        assertThat(decoded.payload()).containsExactly(yjsUpdate);
    }

    @Test
    void roundTripsTheLinkOuterHeaderWithoutInterpretingItsPayload() {
        UUID eventId = UUID.fromString("123e4567-e89b-12d3-a456-426614174001");
        byte[] linkPayload = new byte[] {0, 0, 0, 27, 1, 1, 7};

        DocumentWsBinaryFrame decoded = codec.decodeBinary(codec.encodeBinary(
                new DocumentWsBinaryFrame(DocumentWsFrameType.LINK, eventId, linkPayload)));

        assertThat(decoded.type()).isEqualTo(DocumentWsFrameType.LINK);
        assertThat(decoded.eventId()).isEqualTo(eventId);
        assertThat(decoded.payload()).containsExactly(linkPayload);
    }

    @Test
    void rejectsShortUnknownAndIncompatibleBinaryFrames() {
        assertThatThrownBy(() -> codec.decodeBinary(ByteBuffer.allocate(DocumentWsCodec.BINARY_HEADER_BYTES - 1)))
                .isInstanceOf(DocumentWsProtocolException.class);

        ByteBuffer unknownType = ByteBuffer.allocate(DocumentWsCodec.BINARY_HEADER_BYTES);
        unknownType.put((byte) 1).put((byte) 99).putLong(0L).putLong(0L).flip();
        assertThatThrownBy(() -> codec.decodeBinary(unknownType))
                .isInstanceOf(DocumentWsProtocolException.class)
                .hasMessageContaining("unknown");

        ByteBuffer wrongVersion = ByteBuffer.allocate(DocumentWsCodec.BINARY_HEADER_BYTES);
        wrongVersion.put((byte) 2).put((byte) 1).putLong(0L).putLong(0L).flip();
        assertThatThrownBy(() -> codec.decodeBinary(wrongVersion))
                .isInstanceOf(DocumentWsProtocolException.class)
                .hasMessageContaining("unsupported");
    }

    @Test
    void validatesVersionAndRequestIdForEveryControlFrame() {
        DocumentWsControlMessage control = new DocumentWsControlMessage(1, DocumentWsControlType.UPDATE_ACCEPTED,
                UUID.fromString("123e4567-e89b-12d3-a456-426614174000"), 7L,
                UUID.fromString("123e4567-e89b-12d3-a456-426614174000"), "1-0", null, null);

        TextMessage encoded = codec.encodeControl(control);

        assertThat(codec.decodeControl(encoded)).isEqualTo(control);
        assertThatThrownBy(() -> codec.encodeControl(new DocumentWsControlMessage(1,
                DocumentWsControlType.ERROR, null, null, null, null, "BAD_FRAME", "missing request")))
                .isInstanceOf(DocumentWsProtocolException.class)
                .hasMessageContaining("requestId");
    }

    @Test
    void roundTripsJoinAwarenessClientIdAndTypedAwarenessMetadata() {
        UUID requestId = UUID.fromString("123e4567-e89b-12d3-a456-426614174010");
        DocumentWsControlMessage join = new DocumentWsControlMessage(1, DocumentWsControlType.JOIN_DOCUMENT,
                requestId, 7L, null, null, null, null, 123456789L);

        assertThat(codec.decodeControl(codec.encodeControl(join))).isEqualTo(join);

        DocumentWsAwarenessMeta upsert = new DocumentWsAwarenessMeta(1,
                DocumentWsControlType.AWARENESS_META, requestId, DocumentWsAwarenessAction.UPSERT,
                7L, 123456789L, "session-a", 42L, "alice", "#3B82F6");
        DocumentWsAwarenessMeta decoded = codec.decodeAwarenessMeta(codec.encodeAwarenessMeta(upsert));

        assertThat(decoded).isEqualTo(upsert);
        assertThat(codec.decodeAwarenessMeta(codec.encodeAwarenessMeta(new DocumentWsAwarenessMeta(1,
                DocumentWsControlType.AWARENESS_META, requestId, DocumentWsAwarenessAction.REMOVE,
                7L, 123456789L, "session-a", null, null, null))).action())
                .isEqualTo(DocumentWsAwarenessAction.REMOVE);
    }

    @Test
    void encodesLinkAcceptedWithBothRedisOperationIds() {
        UUID requestId = UUID.fromString("123e4567-e89b-12d3-a456-426614174010");
        TextMessage encoded = codec.encodeLinkAccepted(new DocumentWsLinkAcceptedMessage(
                1, DocumentWsControlType.LINK_ACCEPTED, requestId, 7L, requestId,
                "100-0", "200-0", "QUEUED"));

        assertThat(encoded.getPayload()).contains("LINK_ACCEPTED", "updatesRedisOpId", "bindingRedisOpId", "QUEUED");
    }
}
