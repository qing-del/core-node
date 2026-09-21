package com.jacolp.document.application.flush;

/** 一次幂等地将 Redis Stream 更新写入 MySQL 操作日志的结果。 */
public record DocumentFlushLogResult(
        /** 被刷盘的文档 ID。<p>example: {@code 42}</p> */
        long documentId,
        /** 本次成功写入 MySQL 的更新条数。<p>example: {@code 500}</p> */
        int processedCount,
        /** 本次写入的 Yjs 二进制总字节数。<p>example: {@code 131072}</p> */
        long processedBytes,
        /** 本次从 Redis Stream 删除的条目数。<p>example: {@code 500}</p> */
        long deletedCount) {

    /** 创建表示当前 Redis Stream 没有待刷盘更新的结果。 */
    public static DocumentFlushLogResult empty(long documentId) {
        return new DocumentFlushLogResult(documentId, 0, 0L, 0L);
    }
}
