package com.jacolp.document.infrastructure.redis;

import java.util.Objects;

/** 待持久化更新及其 Redis Stream 条目 ID，后者用于确定截断范围和执行 XDEL。 */
public record StoredDocumentPendingUpdate(
        /** Redis Stream 条目 ID，用于按顺序确认和删除记录。<p>example: {@code 1756080000000-0}</p> */
        String redisOpId,
        /** 从该 Stream 条目恢复出的待刷盘更新。<p>example: {@code DocumentPendingUpdate(documentId=42, ...)}</p> */
        DocumentPendingUpdate update) {

    public StoredDocumentPendingUpdate {
        if (redisOpId == null || redisOpId.isBlank()) {
            throw new IllegalArgumentException("redisOpId must not be blank");
        }
        Objects.requireNonNull(update, "update must not be null");
    }
}
