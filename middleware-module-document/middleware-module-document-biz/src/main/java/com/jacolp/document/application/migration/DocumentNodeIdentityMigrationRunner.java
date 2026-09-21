package com.jacolp.document.application.migration;

import com.jacolp.document.config.DocumentProperties;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** 由一次性配置开关触发的历史节点身份迁移入口。 */
@Component
@ConditionalOnProperty(prefix = "jacolp.document.node-migration", name = "run-on-start", havingValue = "true")
public class DocumentNodeIdentityMigrationRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DocumentNodeIdentityMigrationRunner.class);

    private final DocumentNodeIdentityMigrationService migrationService;
    private final DocumentProperties properties;

    /** 创建启动迁移任务；正常服务启动默认不会装配该 Bean。 */
    public DocumentNodeIdentityMigrationRunner(DocumentNodeIdentityMigrationService migrationService,
                                               DocumentProperties properties) {
        this.migrationService = Objects.requireNonNull(migrationService, "migrationService must not be null");
        this.properties = Objects.requireNonNull(properties, "properties must not be null");
    }

    /** 扫描全部文档并输出可重试的结构化汇总。 */
    @Override
    public void run(ApplicationArguments args) {
        DocumentNodeIdentityMigrationSummary summary = migrationService.migrateAll();
        log.info("document node identity migration runner finished runOnStart={} scanned={} migrated={} skipped={} "
                        + "failures={}", properties.getNodeMigration().isRunOnStart(), summary.scanned(),
                summary.migrated(), summary.skipped(), summary.failures());
    }
}
