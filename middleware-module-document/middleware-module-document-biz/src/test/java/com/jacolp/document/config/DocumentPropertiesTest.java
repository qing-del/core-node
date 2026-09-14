package com.jacolp.document.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class DocumentPropertiesTest {
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(DocumentModuleConfiguration.class);

    @Test
    void bindsDocumentRuntimeLimits() {
        contextRunner.withPropertyValues(
                "jacolp.document.enabled=true",
                "jacolp.document.websocket.max-update-bytes=1024",
                "jacolp.document.flush-log.batch-size=10",
                "jacolp.document.compact.max-unmerged-ops=20",
                "jacolp.document.snapshot.max-bytes=4096")
                .run(context -> {
                    DocumentProperties properties = context.getBean(DocumentProperties.class);
                    assertThat(properties.isEnabled()).isTrue();
                    assertThat(properties.getWebsocket().getMaxUpdateBytes()).isEqualTo(1024);
                    assertThat(properties.getFlushLog().getBatchSize()).isEqualTo(10);
                    assertThat(properties.getCompact().getMaxUnmergedOps()).isEqualTo(20);
                    assertThat(properties.getSnapshot().getMaxBytes()).isEqualTo(4096);
                });
    }

    @Test
    void bindsV07MigrationAndCanalRuntimeProperties() {
        contextRunner.withPropertyValues(
                "jacolp.document.node-migration.run-on-start=true",
                "jacolp.document.node-migration.page-size=25",
                "jacolp.document.node-migration.max-update-batch-bytes=65536",
                "jacolp.document.node-migration.max-cas-retries=5",
                "jacolp.document.file-index.canal.enabled=true",
                "jacolp.document.file-index.canal.host=canal",
                "jacolp.document.file-index.canal.port=11111",
                "jacolp.document.file-index.canal.destination=example",
                "jacolp.document.file-index.canal.filter=personal_saas[.].*")
                .run(context -> {
                    DocumentProperties documentProperties = context.getBean(DocumentProperties.class);
                    FileIndexCanalProperties canalProperties = context.getBean(FileIndexCanalProperties.class);
                    assertThat(documentProperties.getNodeMigration().isRunOnStart()).isTrue();
                    assertThat(documentProperties.getNodeMigration().getPageSize()).isEqualTo(25);
                    assertThat(documentProperties.getNodeMigration().getMaxUpdateBatchBytes()).isEqualTo(65536);
                    assertThat(documentProperties.getNodeMigration().getMaxCasRetries()).isEqualTo(5);
                    assertThat(canalProperties.isEnabled()).isTrue();
                    assertThat(canalProperties.getHost()).isEqualTo("canal");
                    assertThat(canalProperties.getPort()).isEqualTo(11111);
                    assertThat(canalProperties.getDestination()).isEqualTo("example");
                    assertThat(canalProperties.getFilter()).isEqualTo("personal_saas[.].*");
                });
    }
}
