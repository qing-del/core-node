package com.jacolp.document.application.migration;

/** 单个文档的节点身份迁移结果。 */
public record DocumentNodeIdentityMigrationOutcome(long documentId, Status status, long cutoffLogId,
                                                    String objectKey) {

    /** 迁移结果状态。 */
    public enum Status {
        MIGRATED,
        NO_CHANGES,
        SKIPPED_ACTIVE_SESSIONS,
        CAS_LOST
    }
}
