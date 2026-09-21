package com.jacolp.document.infrastructure.redis;

import java.util.Arrays;
import java.util.Objects;

/** LINK BindingEnvelope 在 Redis Binding Stream 中等待投影的一条二进制记录。 */
public record DocumentPendingBinding(
        /** Binding Stream 所属的源文档 ID。 */
        long documentId,
        /** 已编码的 BindingEnvelope；不包含 18 字节 WebSocket 外层头。 */
        byte[] envelopeData) {

    public DocumentPendingBinding {
        if (documentId <= 0) {
            throw new IllegalArgumentException("documentId must be positive");
        }
        Objects.requireNonNull(envelopeData, "envelopeData must not be null");
        if (envelopeData.length == 0) {
            throw new IllegalArgumentException("envelopeData must not be empty");
        }
        envelopeData = Arrays.copyOf(envelopeData, envelopeData.length);
    }

    /** 返回 Envelope 字节副本，避免 Redis 入队前后的数组别名。 */
    @Override
    public byte[] envelopeData() {
        return Arrays.copyOf(envelopeData, envelopeData.length);
    }
}
