package com.jacolp.middleware.migration;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class MigrationScriptTest {

    private static final Path MIGRATION_DIRECTORY = locateMigrationDirectory();

    @Test
    void audioSizeUpgradeShouldBeGuardedWhenBootstrapAlreadyContainsColumn()
            throws IOException {
        String bootstrap = readMigration("20260519_audio_tasks.sql");
        String refactorMigration = readMigration("20260730_refactor_mq.sql");

        assertThat(bootstrap).containsPattern(
                "`audio_size`\\s+bigint\\s+DEFAULT NULL");
        assertThat(refactorMigration)
                .contains("FROM information_schema.columns")
                .contains("table_schema = DATABASE()")
                .contains("table_name = 'audio_tasks'")
                .contains("column_name = 'audio_size'")
                .contains("@audio_size_column_exists = 0")
                .contains("PREPARE audio_size_upgrade FROM @audio_size_upgrade_sql")
                .contains("EXECUTE audio_size_upgrade")
                .contains("DEALLOCATE PREPARE audio_size_upgrade");
        assertThat(refactorMigration.indexOf("SET @audio_size_column_exists"))
                .isLessThan(refactorMigration.indexOf("ADD COLUMN `audio_size`"));
    }

    @Test
    void zeroStatusAuditRowsShouldBeBackedUpAndResolvedFromOwnerState()
            throws IOException {
        String migration = readMigration("20260807_sync_audit_submit_cancel.sql");

        assertThat(migration)
                .contains("CREATE TABLE IF NOT EXISTS `audit_zero_status_migration_backup`")
                .contains("`previous_status`")
                .contains("`resolved_status`")
                .contains("`resolution_reason`")
                .contains("LEFT JOIN `biz_tag` t ON t.`id` = r.`target_id`")
                .contains("LEFT JOIN `biz_image` i ON i.`id` = r.`image_id`")
                .contains("t.`audit_status` = 1")
                .contains("i.`audit_status` = 1")
                .contains("active.`status` = 1")
                .contains("SELECT MAX(latest.`id`)")
                .contains("'RESTORED_ACTIVE_APPLICATION'")
                .contains("JOIN `audit_zero_status_migration_backup` b")
                .contains("SET r.`status` = b.`resolved_status`")
                .contains("table_name = 'sys_async_command_state'")
                .contains("PREPARE archive_async_command_state")
                .contains("Archived async command correlation state retained")
                .doesNotContain("UPDATE `biz_tag_audit_record` SET `status` = 5 WHERE `status` = 0")
                .doesNotContain("UPDATE `biz_image_audit_record` SET `status` = 5 WHERE `status` = 0")
                .doesNotContain("DROP TABLE IF EXISTS `sys_async_command_state`");
        assertThat(migration.indexOf("audit_zero_status_migration_backup"))
                .isLessThan(migration.indexOf("SET r.`status` = b.`resolved_status`"));
    }

    private static String readMigration(String fileName) throws IOException {
        Path migration = MIGRATION_DIRECTORY.resolve(fileName);
        assertThat(migration)
                .as("migration must be resolved from the Maven reactor root")
                .isRegularFile();
        return Files.readString(migration);
    }

    private static Path locateMigrationDirectory() {
        Path directory = Path.of(System.getProperty("user.dir")).toAbsolutePath();
        while (directory != null) {
            Path candidate = directory.resolve(Path.of(
                    "static", "database", "migrations"));
            if (Files.isDirectory(candidate)) {
                return candidate;
            }
            directory = directory.getParent();
        }
        throw new IllegalStateException("static/database/migrations directory not found");
    }
}
