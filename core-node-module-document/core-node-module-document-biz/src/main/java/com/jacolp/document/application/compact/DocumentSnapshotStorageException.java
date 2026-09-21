package com.jacolp.document.application.compact;

/** 文档不可变快照无法从 MinIO 读取或写入时抛出。 */
public class DocumentSnapshotStorageException extends RuntimeException {

    /** 创建不带底层原因的快照存储异常。 */
    public DocumentSnapshotStorageException(String message) {
        super(message);
    }

    /** 保留 MinIO 或配置异常作为根因。 */
    public DocumentSnapshotStorageException(String message, Throwable cause) {
        super(message, cause);
    }
}
