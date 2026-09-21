package com.jacolp.document.application.migration;

/** 历史文档节点身份迁移无法安全完成时抛出。 */
public class DocumentNodeIdentityMigrationException extends RuntimeException {

    /** 创建迁移失败异常。 */
    public DocumentNodeIdentityMigrationException(String message) {
        super(message);
    }

    /** 保留下游存储或 Yjs 服务的失败原因。 */
    public DocumentNodeIdentityMigrationException(String message, Throwable cause) {
        super(message, cause);
    }
}
