package com.jacolp.corenode.migration;

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

    @Test
    void phaseFiveRouteScopesShouldKeepWildcardRolesAndNarrowFirstPartyClients()
            throws IOException {
        String migration = readMigration("20260812_phase5_business_route_scopes.sql");
        String bootstrap = Files.readString(locateMigrationDirectory().getParent().resolve("createDatabase.sql"));
        String phaseFiveUserScopes = "account:read,account:write,audio:read,audio:write,audit:read,audit:write,media:read,media:write,note:read,note:write";
        String bootstrapUserScopes = "account:read,account:write,agent:chat,audio:read,audio:write,audit:read,audit:write,"
                + "document:read,document:write,media:read,media:write,note:read,note:write";
        String adminScopes = "account:read,account:manage,audio:read,audio:manage,audit:read,audit:manage,document:read,document:write,media:read,media:manage,note:read,note:manage";

        assertThat(migration)
                .contains("phase5_business_route_scopes_preflight")
                .contains("phase5_business_route_scopes_postflight")
                .contains("v_permission_count <> 19")
                .contains("v_exact_permission_count <> 15")
                .contains("'account:read'")
                .contains("'audit:manage'")
                .contains("BINARY `client_id` = 'core_agent'")
                .contains("BINARY `scopes` = 'note:read,note:write,sys:read,media:read'")
                .contains("'*:read', '*:write', '*:manage', '*:super'")
                .contains(phaseFiveUserScopes)
                .contains(adminScopes);
        assertThat(bootstrap)
                .contains(bootstrapUserScopes)
                .contains(adminScopes)
                .contains("('account:read', NULL, 'account', 'read', 'active'")
                .contains("('audit:manage', NULL, 'audit', 'manage', 'active'")
                .contains("'core_agent',")
                .contains("'note:read,note:write,sys:read,media:read'");
    }

    @Test
    void internalClientDefaultsShouldAllowIpv4AndIpv6WithoutChangingCoreAgent() throws IOException {
        String bootstrap = Files.readString(locateMigrationDirectory().getParent().resolve("createDatabase.sql"));
        String migration = readMigration("20260822_internal_client_ipv6_allowed_ips.sql");

        assertThat(bootstrap)
                .containsPattern("'user',[\\s\\S]*?'active',\\s*'0\\.0\\.0\\.0/0,::/0'")
                .containsPattern("'admin',[\\s\\S]*?'active',\\s*'0\\.0\\.0\\.0/0,::/0'")
                .containsPattern("'core_agent',[\\s\\S]*?'active',\\s*'0\\.0\\.0\\.0/0'");
        assertThat(migration)
                .contains("BINARY `client_id` IN ('user', 'admin')")
                .contains("BINARY `allowed_ips` = '0.0.0.0/0'")
                .contains("SET `allowed_ips` = '0.0.0.0/0,::/0'")
                .doesNotContain("core_agent");
    }

    @Test
    void documentPersistenceFreshSchemaShouldUseOwnerAndAuthorizationTables() throws IOException {
        String bootstrap = Files.readString(locateMigrationDirectory().getParent().resolve("createDatabase.sql"));
        String historicalMigration = readMigration("20260823_document_persistence_model.sql");
        String authorizationMigration = readMigration("20260830_document_user_authorization.sql");

        assertThat(bootstrap)
                .contains("CREATE TABLE `biz_document`")
                .contains("`owner_user_id`       bigint       NOT NULL")
                .contains("`persisted_log_id`    bigint       NOT NULL DEFAULT 0")
                .contains("KEY `idx_document_owner_deleted_time` (`owner_user_id`, `deleted`, `last_modify_time`)")
                .doesNotContain("`team_id`")
                .contains("CREATE TABLE `biz_document_user`")
                .contains("PRIMARY KEY (`document_id`, `user_id`)")
                .contains("KEY `idx_document_user_visible` (`user_id`, `enabled`, `document_id`)")
                .contains("CREATE TABLE `document_op_log`")
                .contains("UNIQUE KEY `uk_document_redis_op` (`document_id`, `redis_op_id`)")
                .contains("UNIQUE KEY `uk_document_client_update` (`document_id`, `client_update_id`)");
        int documentUserStart = bootstrap.indexOf("CREATE TABLE `biz_document_user`");
        int documentOpLogStart = bootstrap.indexOf("CREATE TABLE `document_op_log`", documentUserStart);
        assertThat(documentUserStart).isGreaterThanOrEqualTo(0);
        assertThat(documentOpLogStart).isGreaterThan(documentUserStart);
        assertThat(bootstrap.substring(documentUserStart, documentOpLogStart))
                .contains("KEY `idx_document_user_visible` (`user_id`, `enabled`, `document_id`)")
                .doesNotContain("KEY `idx_user_id` (`user_id`)");
        assertThat(historicalMigration)
                .contains("USE `personal_saas`;");
        assertThat(authorizationMigration)
                .contains("ADD COLUMN `owner_user_id`")
                .contains("SET `owner_user_id` = `team_id`")
                .contains("CREATE TABLE `biz_document_user`")
                .contains("document_user_authorization_retire_team_preflight")
                .contains("document_user_authorization_retire_team_postflight")
                .doesNotContain("ADD KEY `idx_user_id` (`user_id`)");
    }

    @Test
    void documentAuthorizationMigrationShouldRetireTeamAfterOwnerBackfill()
            throws IOException {
        String migration = readMigration("20260830_document_user_authorization.sql");

        assertThat(migration)
                .contains("document_user_authorization_retire_team_preflight")
                .contains("document_user_authorization_retire_team_postflight")
                .contains("owner_user_id")
                .contains("owner_user_id` IS NULL")
                .contains("idx_document_owner_deleted_time")
                .contains("idx_document_scope_deleted_time")
                .contains("DROP INDEX `idx_document_scope_deleted_time`")
                .contains("DROP COLUMN `team_id`");
        assertThat(migration.indexOf("CALL `document_user_authorization_retire_team_preflight`()"))
                .isLessThan(migration.indexOf("DROP COLUMN `team_id`"));
        assertThat(migration.indexOf("DROP COLUMN `team_id`"))
                .isLessThan(migration.indexOf("CALL `document_user_authorization_retire_team_postflight`()"));
        int ownerBackfill = migration.indexOf("SET `owner_user_id` = `team_id`");
        int ownerConstraint = migration.indexOf("MODIFY COLUMN `owner_user_id`");
        int authorizationTable = migration.indexOf("CREATE TABLE `biz_document_user`");
        int retirePreflight = migration.indexOf("CALL `document_user_authorization_retire_team_preflight`()");
        assertThat(ownerBackfill).isGreaterThanOrEqualTo(0);
        assertThat(ownerConstraint).isGreaterThan(ownerBackfill);
        assertThat(authorizationTable).isGreaterThan(ownerConstraint);
        assertThat(retirePreflight).isGreaterThan(authorizationTable);
        assertThat(migration)
                .doesNotContain("document_user_authorization_idx_user_id")
                .doesNotContain("ADD KEY `idx_user_id` (`user_id`)");
    }

    @Test
    void documentScopesShouldBeAvailableToTheUserClientAndPermissionCatalogue() throws IOException {
        String bootstrap = Files.readString(locateMigrationDirectory().getParent().resolve("createDatabase.sql"));
        String migration = readMigration("20260824_document_oauth_scopes.sql");
        String bootstrapUserScopes = "account:read,account:write,agent:chat,audio:read,audio:write,audit:read,audit:write,"
                + "document:read,document:write,media:read,media:write,note:read,note:write";
        String migrationUserScopes = "account:read,account:write,audio:read,audio:write,audit:read,audit:write,"
                + "document:read,document:write,media:read,media:write,note:read,note:write";

        assertThat(bootstrap)
                .contains(bootstrapUserScopes)
                .contains("('document:read', NULL, 'document', 'read', 'active'")
                .contains("('document:write', NULL, 'document', 'write', 'active'");
        assertThat(migration)
                .contains("document_oauth_scopes_preflight")
                .contains("document_oauth_scopes_postflight")
                .contains("'document:read'")
                .contains("'document:write'")
                .contains(migrationUserScopes)
                .contains("v_document_permission_count <> 2")
                .contains("v_user_client_count <> 1");
    }

    @Test
    void agentChatScopeShouldBeAvailableToTheUserClientAndUserRole() throws IOException {
        String bootstrap = Files.readString(locateMigrationDirectory().getParent().resolve("createDatabase.sql"));
        String migration = readMigration("20260921_agent_chat_oauth_scope.sql");
        String userScopes = "account:read,account:write,agent:chat,audio:read,audio:write,audit:read,audit:write,"
                + "document:read,document:write,media:read,media:write,note:read,note:write";

        assertThat(bootstrap)
                .contains(userScopes)
                .contains("('agent:chat', NULL, 'agent', 'chat', 'active'")
                .contains("IN ('*:read', '*:write', 'agent:chat')");
        assertThat(migration)
                .contains("INSERT INTO `sys_permission`")
                .contains("'agent:chat'")
                .contains("INSERT INTO `sys_role_perm`")
                .contains("r.`role_code` = 'USER'")
                .contains(userScopes)
                .contains("BINARY `client_id` = 'user'");
    }

    @Test
    void adminDocumentScopesShouldBeEnabledForTheInternalAdminClient() throws IOException {
        String bootstrap = Files.readString(locateMigrationDirectory().getParent().resolve("createDatabase.sql"));
        String migration = readMigration("20260920_admin_document_oauth_scopes.sql");
        String bootstrapAdminScopes = "account:read,account:manage,audio:read,audio:manage,audit:read,audit:manage,"
                + "document:read,document:write,media:read,media:manage,note:read,note:manage";
        String migrationAdminScopes = "account:read,account:manage,audio:read,audio:manage,audit:read,audit:manage,"
                + "document:read,document:manage,media:read,media:manage,note:read,note:manage";

        assertThat(bootstrap).contains(bootstrapAdminScopes);
        assertThat(migration)
                .contains("BINARY `client_id` = 'admin'")
                .contains(migrationAdminScopes);
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
