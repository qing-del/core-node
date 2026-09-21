package com.jacolp.document.websocket.protocol;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Objects;

/** 解析 LINK payload 的固定 Envelope，同时保持 raw Yjs update 完全透明。 */
public final class DocumentLinkCodec {

    private static final int ENVELOPE_LENGTH_BYTES = Integer.BYTES;
    private static final int LINK_FIXED_PAYLOAD_BYTES = ENVELOPE_LENGTH_BYTES + DocumentBindingEnvelope.BODY_BYTES;

    private DocumentLinkCodec() {
    }

    /** 将 BindingEnvelope 和 raw Yjs update 编码为 LINK 外层 payload。 */
    public static byte[] encode(DocumentBindingEnvelope envelope, byte[] rawYjsUpdate) {
        Objects.requireNonNull(envelope, "envelope must not be null");
        DocumentLinkPayload payload = new DocumentLinkPayload(envelope, rawYjsUpdate);
        byte[] envelopeBody = envelope.encodeBody();
        ByteBuffer wire = ByteBuffer.allocate(LINK_FIXED_PAYLOAD_BYTES + payload.rawYjsUpdate().length)
                .order(ByteOrder.BIG_ENDIAN);
        wire.putInt(DocumentBindingEnvelope.BODY_BYTES);
        wire.put(envelopeBody);
        wire.put(payload.rawYjsUpdate());
        return wire.array();
    }

    /** 解码 LINK payload；payload 不包含现有 18 字节 WebSocket 外层头。 */
    public static DocumentLinkPayload decode(byte[] payload) {
        if (payload == null || payload.length < LINK_FIXED_PAYLOAD_BYTES + 1) {
            throw new DocumentWsProtocolException("document LINK payload is shorter than its fixed envelope");
        }

        ByteBuffer source = ByteBuffer.wrap(payload).asReadOnlyBuffer().order(ByteOrder.BIG_ENDIAN);
        int envelopeLength = source.getInt();
        if (envelopeLength != DocumentBindingEnvelope.BODY_BYTES) {
            throw new DocumentWsProtocolException("document LINK envelope length must be 27");
        }
        if (source.remaining() < envelopeLength + 1) {
            throw new DocumentWsProtocolException("document LINK payload does not contain a raw Yjs update");
        }

        byte[] envelopeBody = new byte[envelopeLength];
        source.get(envelopeBody);
        DocumentBindingEnvelope envelope = DocumentBindingEnvelope.decodeBody(envelopeBody);

        byte[] rawYjsUpdate = new byte[source.remaining()];
        source.get(rawYjsUpdate);
        return new DocumentLinkPayload(envelope, rawYjsUpdate);
    }

    /** 返回 LINK payload 在 raw Yjs update 之前的固定字节数。 */
    public static int fixedPayloadBytes() {
        return LINK_FIXED_PAYLOAD_BYTES;
    }
}
