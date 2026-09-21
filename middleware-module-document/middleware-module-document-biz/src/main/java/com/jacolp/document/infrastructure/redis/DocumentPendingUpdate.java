package com.jacolp.document.infrastructure.redis;

import java.util.Objects;
import java.util.UUID;

/** 在 FLUSH_LOG 写入 MySQL 前暂存在 Redis 中的一条 Yjs 更新。 */
public record DocumentPendingUpdate(
        /** 待刷盘更新所属的文档 ID。<p>example: {@code 42}</p> */
        long documentId,
        /** Yjs 二进制增量，保持原始字节，不经过 UTF-8 转换。<p>example: {@code [0x01, 0x02, 0x7f]}</p> */
        byte[] updateData,
        /** 客户端生成的更新幂等 UUID。<p>example: {@code 550e8400-e29b-41d4-a716-446655440000}</p> */
        String clientUpdateId,
        /** 产生更新的用户 ID；后台任务写入时为空。<p>example: {@code 10001}</p> */
        Long operatorId,
        /** 更新来源类型。<p>example: {@code USER}</p> */
        String operatorType,
        /** Redis 接受更新的服务端时间戳，单位为 Unix 毫秒。<p>example: {@code 1756080000000}</p> */
        long createdAt) {

    public DocumentPendingUpdate {
        if (documentId <= 0) {
            throw new IllegalArgumentException("documentId must be positive");
        }
        Objects.requireNonNull(updateData, "updateData must not be null");
        if (updateData.length == 0) {
            throw new IllegalArgumentException("updateData must not be empty");
        }
        updateData = updateData.clone();
        try {
            UUID.fromString(Objects.requireNonNull(clientUpdateId, "clientUpdateId must not be null"));
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("clientUpdateId must be a UUID", exception);
        }
        if (operatorType == null || operatorType.isBlank()) {
            throw new IllegalArgumentException("operatorType must not be blank");
        }
        if (createdAt < 0) {
            throw new IllegalArgumentException("createdAt must not be negative");
        }
    }

    /** 返回更新数据副本，避免 Redis 写入前后出现可变数组别名。 */
    @Override
    public byte[] updateData() {
        return updateData.clone();
    }
}
