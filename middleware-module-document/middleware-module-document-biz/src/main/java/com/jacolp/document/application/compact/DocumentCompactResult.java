package com.jacolp.document.application.compact;

/** 一次由 CAS 保护的不可变快照压缩尝试结果。 */
public record DocumentCompactResult(
        /** 被压缩的文档 ID。<p>example: {@code 42}</p> */
        long documentId,
        /** 本次压缩的结果状态。<p>example: {@code COMPACTED}</p> */
        Status status,
        /** 本次快照包含的最大操作日志 ID；没有压缩或 CAS 失败时为空。<p>example: {@code 128}</p> */
        Long cutoffLogId,
        /** 新快照在 MinIO 中的对象键；没有生成快照时为空。<p>example: {@code document/42/state/550e8400-e29b-41d4-a716-446655440000.bin}</p> */
        String objectKey) {

    public enum Status {
        NO_UPDATES,
        COMPACTED,
        CAS_LOST
    }

    /** 构造“没有待压缩更新”的幂等结果。 */
    public static DocumentCompactResult noUpdates(long documentId) {
        return new DocumentCompactResult(documentId, Status.NO_UPDATES, null, null);
    }
}
