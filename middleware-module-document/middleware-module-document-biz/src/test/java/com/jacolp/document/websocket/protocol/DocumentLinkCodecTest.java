package com.jacolp.document.websocket.protocol;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class DocumentLinkCodecTest {

    private static final UUID REF_ID = UUID.fromString("123e4567-e89b-12d3-a456-426614174000");

    @Test
    void roundTripsBindingEnvelopeAndOpaqueYjsUpdate() {
        DocumentBindingEnvelope envelope = new DocumentBindingEnvelope(
                1, DocumentBindingCommandType.BIND, REF_ID, DocumentBindingTargetType.DOCUMENT, 42L);
        byte[] rawYjsUpdate = {(byte) 0x80, 0, 1, -1};

        DocumentLinkPayload decoded = DocumentLinkCodec.decode(DocumentLinkCodec.encode(envelope, rawYjsUpdate));

        assertThat(decoded.envelope()).isEqualTo(envelope);
        assertThat(decoded.rawYjsUpdate()).containsExactly(rawYjsUpdate);
    }

    @Test
    void encodesUnbindWithTheSameEnvelopeShape() {
        DocumentBindingEnvelope envelope = new DocumentBindingEnvelope(
                1, DocumentBindingCommandType.UNBIND, REF_ID, DocumentBindingTargetType.DOCUMENT, 42L);

        DocumentLinkPayload decoded = DocumentLinkCodec.decode(DocumentLinkCodec.encode(envelope, new byte[] {7}));

        assertThat(decoded.envelope().commandType()).isEqualTo(DocumentBindingCommandType.UNBIND);
        assertThat(DocumentLinkCodec.fixedPayloadBytes()).isEqualTo(31);
    }

    @Test
    void decodesTheBindingStreamBodyWithoutAnOuterLengthPrefix() {
        DocumentBindingEnvelope envelope = new DocumentBindingEnvelope(
                1, DocumentBindingCommandType.BIND, REF_ID, DocumentBindingTargetType.DOCUMENT, 42L);

        assertThat(DocumentBindingEnvelope.decodeBody(envelope.encodeBody())).isEqualTo(envelope);
    }

    @Test
    void roundTripsAllSupportedResourceTargetTypesWithoutChangingEnvelopeShape() {
        assertThat(DocumentBindingTargetType.DOCUMENT.wireValue()).isEqualTo(0x01);
        assertThat(DocumentBindingTargetType.NOTE.wireValue()).isEqualTo(0x02);
        assertThat(DocumentBindingTargetType.IMAGE.wireValue()).isEqualTo(0x03);

        for (DocumentBindingTargetType targetType : DocumentBindingTargetType.values()) {
            DocumentBindingEnvelope envelope = new DocumentBindingEnvelope(
                    1, DocumentBindingCommandType.BIND, REF_ID, targetType, 42L);

            assertThat(DocumentBindingEnvelope.decodeBody(envelope.encodeBody())).isEqualTo(envelope);
            assertThat(DocumentLinkCodec.fixedPayloadBytes()).isEqualTo(31);
        }
    }

    @Test
    void rejectsMalformedEnvelopeAndEmptyUpdate() {
        DocumentBindingEnvelope envelope = new DocumentBindingEnvelope(
                1, DocumentBindingCommandType.BIND, REF_ID, DocumentBindingTargetType.DOCUMENT, 42L);
        byte[] valid = DocumentLinkCodec.encode(envelope, new byte[] {7});
        valid[3] = 26;
        assertThatThrownBy(() -> DocumentLinkCodec.decode(valid))
                .isInstanceOf(DocumentWsProtocolException.class)
                .hasMessageContaining("length");

        assertThatThrownBy(() -> DocumentLinkCodec.encode(envelope, new byte[0]))
                .isInstanceOf(DocumentWsProtocolException.class)
                .hasMessageContaining("raw Yjs");
    }

    @Test
    void rejectsUnsupportedSchemaCommandTargetAndZeroRefId() {
        assertThatThrownBy(() -> new DocumentBindingEnvelope(
                2, DocumentBindingCommandType.BIND, REF_ID, DocumentBindingTargetType.DOCUMENT, 42L))
                .isInstanceOf(DocumentWsProtocolException.class);
        assertThatThrownBy(() -> new DocumentBindingEnvelope(
                1, DocumentBindingCommandType.BIND, new UUID(0L, 0L), DocumentBindingTargetType.DOCUMENT, 42L))
                .isInstanceOf(DocumentWsProtocolException.class);
        assertThatThrownBy(() -> new DocumentBindingEnvelope(
                1, DocumentBindingCommandType.BIND, REF_ID, DocumentBindingTargetType.DOCUMENT, 0L))
                .isInstanceOf(DocumentWsProtocolException.class);
    }
}
