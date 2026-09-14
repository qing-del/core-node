package com.jacolp.document.websocket.protocol;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.UUID;

/** LINK 中携带资源绑定意图的固定 27 字节 Envelope body。 */
public record DocumentBindingEnvelope(
        /** v0.6 Envelope schema 版本；固定为 1。 */
        int schemaVersion,
        /** 绑定或解除绑定命令。 */
        DocumentBindingCommandType commandType,
        /** 正文 resourceReference 节点的稳定身份。 */
        UUID refId,
        /** 被引用资源的线上类型；支持 DOCUMENT、NOTE 和 IMAGE。 */
        DocumentBindingTargetType targetType,
        /** 被引用资源的 MySQL BIGINT ID。 */
        long targetId) {

    public static final int SCHEMA_VERSION = 1;
    public static final int BODY_BYTES = 27;

    public DocumentBindingEnvelope {
        if (schemaVersion != SCHEMA_VERSION) {
            throw new DocumentWsProtocolException("unsupported document LINK envelope schema version: " + schemaVersion);
        }
        if (commandType == null) {
            throw new DocumentWsProtocolException("document LINK binding command type is required");
        }
        if (refId == null || refId.equals(new UUID(0L, 0L))) {
            throw new DocumentWsProtocolException("document LINK refId must be a non-zero UUID");
        }
        if (targetType == null) {
            throw new DocumentWsProtocolException("document LINK binding target type is required");
        }
        if (targetId <= 0) {
            throw new DocumentWsProtocolException("document LINK targetId must be positive");
        }
    }

    /** 将已验证 Envelope 编码为固定 27 字节 body。 */
    public byte[] encodeBody() {
        ByteBuffer body = ByteBuffer.allocate(BODY_BYTES).order(ByteOrder.BIG_ENDIAN);
        body.put((byte) schemaVersion);
        body.put((byte) commandType.wireValue());
        body.putLong(refId.getMostSignificantBits());
        body.putLong(refId.getLeastSignificantBits());
        body.put((byte) targetType.wireValue());
        body.putLong(targetId);
        return body.array();
    }

    /** 从 Binding Stream 中保存的固定 27 字节 body 解码并执行同一套字段校验。 */
    public static DocumentBindingEnvelope decodeBody(byte[] body) {
        if (body == null || body.length != BODY_BYTES) {
            throw new DocumentWsProtocolException("document LINK binding envelope body must be exactly 27 bytes");
        }
        ByteBuffer source = ByteBuffer.wrap(body).asReadOnlyBuffer().order(ByteOrder.BIG_ENDIAN);
        return new DocumentBindingEnvelope(
                Byte.toUnsignedInt(source.get()),
                DocumentBindingCommandType.fromWireValue(Byte.toUnsignedInt(source.get())),
                new UUID(source.getLong(), source.getLong()),
                DocumentBindingTargetType.fromWireValue(Byte.toUnsignedInt(source.get())),
                source.getLong());
    }
}
