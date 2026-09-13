package com.jacolp.document.websocket.protocol;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jacolp.document.config.DocumentProperties;
import java.nio.ByteBuffer;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.TextMessage;

/** 解析文档协议头，同时保持 Yjs 字节内容原样不动。 */
@Component
public class DocumentWsCodec {

    public static final int BINARY_HEADER_BYTES = 18;

    private final ObjectMapper objectMapper;
    private final DocumentProperties properties;

    /** 创建使用统一 JSON 序列化器和协议版本配置的 codec。 */
    public DocumentWsCodec(ObjectMapper objectMapper, DocumentProperties properties) {
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
        this.properties = Objects.requireNonNull(properties, "properties must not be null");
    }

    /** 从 WebSocket 二进制消息中解码协议头和原始 payload。 */
    public DocumentWsBinaryFrame decodeBinary(BinaryMessage message) {
        return decodeBinary(message.getPayload());
    }

    /** 校验版本、帧类型和 UUID 后读取剩余的原始 Yjs/awareness 字节。 */
    public DocumentWsBinaryFrame decodeBinary(ByteBuffer payload) {
        ByteBuffer source = Objects.requireNonNull(payload, "payload must not be null").asReadOnlyBuffer();
        if (source.remaining() < BINARY_HEADER_BYTES) {
            // 不足固定头时无法安全读取版本、类型和 UUID，不能把任意字节交给协同层。
            throw new DocumentWsProtocolException("binary frame is shorter than the protocol header");
        }

        int protocolVersion = Byte.toUnsignedInt(source.get());
        if (protocolVersion != properties.getWebsocket().getProtocolVersion()) {
            // 版本不一致时头部布局可能已经变化，继续解析会把错误数据误当成 Yjs payload。
            throw new DocumentWsProtocolException("unsupported document WebSocket protocol version: " + protocolVersion);
        }
        // 只有固定长度的外层头由 Java 解析；剩余字节保持透明，交给客户端 Yjs 绑定处理。
        DocumentWsFrameType type = DocumentWsFrameType.fromWireValue(Byte.toUnsignedInt(source.get()));
        UUID eventId = new UUID(source.getLong(), source.getLong());
        byte[] rawYjsPayload = new byte[source.remaining()];
        source.get(rawYjsPayload);
        return new DocumentWsBinaryFrame(type, eventId, rawYjsPayload);
    }

    /** 将帧类型、事件 UUID 和原始 payload 编码为固定格式二进制消息。 */
    public BinaryMessage encodeBinary(DocumentWsBinaryFrame frame) {
        Objects.requireNonNull(frame, "frame must not be null");
        byte[] rawYjsPayload = frame.payload();
        ByteBuffer wire = ByteBuffer.allocate(BINARY_HEADER_BYTES + rawYjsPayload.length);
        wire.put((byte) properties.getWebsocket().getProtocolVersion());
        wire.put((byte) frame.type().wireValue());
        wire.putLong(frame.eventId().getMostSignificantBits());
        wire.putLong(frame.eventId().getLeastSignificantBits());
        wire.put(rawYjsPayload);
        wire.flip();
        return new BinaryMessage(wire);
    }

    /** 解析并校验 JSON 控制帧。 */
    public DocumentWsControlMessage decodeControl(TextMessage message) {
        try {
            DocumentWsControlMessage control = objectMapper.readValue(message.getPayload(), DocumentWsControlMessage.class);
            validateControl(control);
            return control;
        } catch (JsonProcessingException exception) {
            throw new DocumentWsProtocolException("invalid document WebSocket control JSON", exception);
        }
    }

    /** 校验控制帧后编码为 JSON 文本消息。 */
    public TextMessage encodeControl(DocumentWsControlMessage control) {
        validateControl(control);
        try {
            return new TextMessage(objectMapper.writeValueAsString(control));
        } catch (JsonProcessingException exception) {
            throw new DocumentWsProtocolException("could not encode document WebSocket control JSON", exception);
        }
    }

    /** 编码服务端下发的 Awareness Session 元数据控制帧。 */
    public TextMessage encodeAwarenessMeta(DocumentWsAwarenessMeta metadata) {
        validateAwarenessMeta(metadata);
        try {
            return new TextMessage(objectMapper.writeValueAsString(metadata));
        } catch (JsonProcessingException exception) {
            throw new DocumentWsProtocolException("could not encode document Awareness metadata", exception);
        }
    }

    /** 编码 LINK 双 Stream 原子入队后的专用确认消息。 */
    public TextMessage encodeLinkAccepted(DocumentWsLinkAcceptedMessage accepted) {
        Objects.requireNonNull(accepted, "LINK accepted message must not be null");
        if (accepted.protocolVersion() != properties.getWebsocket().getProtocolVersion()) {
            throw new DocumentWsProtocolException("unsupported document WebSocket protocol version: "
                    + accepted.protocolVersion());
        }
        try {
            return new TextMessage(objectMapper.writeValueAsString(accepted));
        } catch (JsonProcessingException exception) {
            throw new DocumentWsProtocolException("could not encode document LINK accepted message", exception);
        }
    }

    /** 解码并校验 Awareness 元数据控制帧，供协议测试和后续消费方复用。 */
    public DocumentWsAwarenessMeta decodeAwarenessMeta(TextMessage message) {
        try {
            DocumentWsAwarenessMeta metadata = objectMapper.readValue(message.getPayload(), DocumentWsAwarenessMeta.class);
            validateAwarenessMeta(metadata);
            return metadata;
        } catch (JsonProcessingException | IllegalArgumentException exception) {
            throw new DocumentWsProtocolException("invalid document Awareness metadata", exception);
        }
    }

    /** 统一校验控制帧版本、类型和请求关联 ID。 */
    private void validateControl(DocumentWsControlMessage control) {
        if (control == null) {
            // Jackson 可能返回 null；后续访问字段前先阻止空控制帧进入业务分派。
            throw new DocumentWsProtocolException("control frame must not be null");
        }
        if (control.protocolVersion() != properties.getWebsocket().getProtocolVersion()) {
            // 控制帧版本必须与当前服务端一致，否则字段语义和处理分支可能不兼容。
            throw new DocumentWsProtocolException("unsupported document WebSocket protocol version: "
                    + control.protocolVersion());
        }
        if (control.type() == null) {
            // 没有 type 就无法决定 JOIN、UPDATE ACK 或心跳等控制行为。
            throw new DocumentWsProtocolException("control frame type is required");
        }
        if (control.requestId() == null) {
            // requestId 是客户端重试和服务端响应关联的基础，所有控制帧都必须携带。
            throw new DocumentWsProtocolException("control frame requestId is required");
        }
    }

    /** 校验元数据帧使用当前协议版本和固定控制类型。 */
    private void validateAwarenessMeta(DocumentWsAwarenessMeta metadata) {
        if (metadata == null) {
            throw new DocumentWsProtocolException("Awareness metadata must not be null");
        }
        if (metadata.protocolVersion() != properties.getWebsocket().getProtocolVersion()) {
            throw new DocumentWsProtocolException("unsupported document WebSocket protocol version: "
                    + metadata.protocolVersion());
        }
        if (metadata.type() != DocumentWsControlType.AWARENESS_META) {
            throw new DocumentWsProtocolException("Awareness metadata type must be AWARENESS_META");
        }
    }
}
