package com.jacolp.document.infrastructure.redis;

import java.util.Objects;

/** Binding Stream 条目及其 Redis Stream ID；ID 用于事务成功后的 XDEL。 */
public record StoredDocumentPendingBinding(
        /** Binding Stream 条目 ID。 */
        String redisOpId,
        /** 从 Stream 条目恢复出的 BindingEnvelope 二进制记录。 */
        DocumentPendingBinding binding) {

    public StoredDocumentPendingBinding {
        if (redisOpId == null || redisOpId.isBlank()) {
            throw new IllegalArgumentException("redisOpId must not be blank");
        }
        Objects.requireNonNull(binding, "binding must not be null");
    }
}
