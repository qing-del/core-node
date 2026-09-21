package com.jacolp.document.application.migration;

/** 一次启动迁移任务的汇总结果。 */
public record DocumentNodeIdentityMigrationSummary(int scanned, int migrated, int skipped, int failures) {

    /** 校验汇总计数不能为负数。 */
    public DocumentNodeIdentityMigrationSummary {
        if (scanned < 0 || migrated < 0 || skipped < 0 || failures < 0) {
            throw new IllegalArgumentException("migration summary counts must not be negative");
        }
    }
}
