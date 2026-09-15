-- Synchronous audit submit/cancel migration.
ALTER TABLE `biz_note_audit_record`
    MODIFY COLUMN `status` tinyint NOT NULL DEFAULT 0
    COMMENT 'audit status: 0 pending, 1 approved, 2 rejected, 3 cancelled';

ALTER TABLE `biz_tag_audit_record`
    MODIFY COLUMN `status` tinyint NOT NULL DEFAULT 0
    COMMENT 'audit status: 0 waiting, 1 auditing, 2 approved, 3 rejected, 4 deleted, 5 cancelled';

ALTER TABLE `biz_image_audit_record`
    MODIFY COLUMN `status` tinyint NOT NULL DEFAULT 0
    COMMENT 'audit status: 0 waiting, 1 auditing, 2 approved, 3 rejected, 4 deleted, 5 cancelled';

-- Preserve every ambiguous legacy WAITING row before resolving it. The owner
-- aggregate is the only available evidence of whether an application was still
-- active when synchronous submit/cancel replaced the async command flow.
CREATE TABLE IF NOT EXISTS `audit_zero_status_migration_backup` (
    `target_type`        varchar(16) NOT NULL,
    `audit_id`           bigint      NOT NULL,
    `applicant_user_id`  bigint      NOT NULL,
    `target_id`          bigint      NOT NULL,
    `previous_status`    tinyint     NOT NULL,
    `target_audit_status` tinyint    DEFAULT NULL,
    `resolved_status`    tinyint     NOT NULL,
    `resolution_reason`  varchar(64) NOT NULL,
    `source_update_time` datetime    DEFAULT NULL,
    `migrated_time`      datetime    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`target_type`, `audit_id`),
    KEY `idx_audit_zero_target` (`target_type`, `target_id`),
    KEY `idx_audit_zero_resolution` (`target_type`, `resolved_status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
  COMMENT='Backup and resolution ledger for legacy zero-status audit rows';

-- Restore at most one active application per tag: the newest WAITING row is
-- promoted only when the aggregate says AUDITING and no status=1 row exists.
INSERT INTO `audit_zero_status_migration_backup` (
    `target_type`, `audit_id`, `applicant_user_id`, `target_id`,
    `previous_status`, `target_audit_status`, `resolved_status`,
    `resolution_reason`, `source_update_time`
)
SELECT
    'TAG', r.`id`, r.`applicant_user_id`, r.`target_id`, r.`status`,
    t.`audit_status`,
    CASE
        WHEN t.`audit_status` = 1
             AND NOT EXISTS (
                 SELECT 1
                 FROM `biz_tag_audit_record` active
                 WHERE active.`target_id` = r.`target_id`
                   AND active.`status` = 1
             )
             AND r.`id` = (
                 SELECT MAX(latest.`id`)
                 FROM `biz_tag_audit_record` latest
                 WHERE latest.`target_id` = r.`target_id`
                   AND latest.`status` = 0
             )
            THEN 1
        ELSE 5
    END,
    CASE
        WHEN t.`id` IS NULL THEN 'TARGET_MISSING'
        WHEN t.`audit_status` <> 1 THEN 'TARGET_NOT_AUDITING'
        WHEN EXISTS (
            SELECT 1
            FROM `biz_tag_audit_record` active
            WHERE active.`target_id` = r.`target_id`
              AND active.`status` = 1
        ) THEN 'ACTIVE_APPLICATION_EXISTS'
        WHEN r.`id` <> (
            SELECT MAX(latest.`id`)
            FROM `biz_tag_audit_record` latest
            WHERE latest.`target_id` = r.`target_id`
              AND latest.`status` = 0
        ) THEN 'OLDER_WAITING_DUPLICATE'
        ELSE 'RESTORED_ACTIVE_APPLICATION'
    END,
    r.`update_time`
FROM `biz_tag_audit_record` r
LEFT JOIN `biz_tag` t ON t.`id` = r.`target_id`
WHERE r.`status` = 0
ON DUPLICATE KEY UPDATE
    `target_audit_status` = VALUES(`target_audit_status`),
    `resolved_status` = VALUES(`resolved_status`),
    `resolution_reason` = VALUES(`resolution_reason`),
    `source_update_time` = VALUES(`source_update_time`),
    `migrated_time` = CURRENT_TIMESTAMP;

-- Apply only the status recorded in the ledger, so every changed row remains
-- explainable and recoverable from previous_status.
UPDATE `biz_tag_audit_record` r
JOIN `audit_zero_status_migration_backup` b
  ON b.`target_type` = 'TAG' AND b.`audit_id` = r.`id`
SET r.`status` = b.`resolved_status`,
    r.`update_time` = CURRENT_TIMESTAMP
WHERE r.`status` = 0;

-- Images use the same evidence-based rule and retain their own ledger rows.
INSERT INTO `audit_zero_status_migration_backup` (
    `target_type`, `audit_id`, `applicant_user_id`, `target_id`,
    `previous_status`, `target_audit_status`, `resolved_status`,
    `resolution_reason`, `source_update_time`
)
SELECT
    'IMAGE', r.`id`, r.`applicant_user_id`, r.`image_id`, r.`status`,
    i.`audit_status`,
    CASE
        WHEN i.`audit_status` = 1
             AND NOT EXISTS (
                 SELECT 1
                 FROM `biz_image_audit_record` active
                 WHERE active.`image_id` = r.`image_id`
                   AND active.`status` = 1
             )
             AND r.`id` = (
                 SELECT MAX(latest.`id`)
                 FROM `biz_image_audit_record` latest
                 WHERE latest.`image_id` = r.`image_id`
                   AND latest.`status` = 0
             )
            THEN 1
        ELSE 5
    END,
    CASE
        WHEN i.`id` IS NULL THEN 'TARGET_MISSING'
        WHEN i.`audit_status` <> 1 THEN 'TARGET_NOT_AUDITING'
        WHEN EXISTS (
            SELECT 1
            FROM `biz_image_audit_record` active
            WHERE active.`image_id` = r.`image_id`
              AND active.`status` = 1
        ) THEN 'ACTIVE_APPLICATION_EXISTS'
        WHEN r.`id` <> (
            SELECT MAX(latest.`id`)
            FROM `biz_image_audit_record` latest
            WHERE latest.`image_id` = r.`image_id`
              AND latest.`status` = 0
        ) THEN 'OLDER_WAITING_DUPLICATE'
        ELSE 'RESTORED_ACTIVE_APPLICATION'
    END,
    r.`update_time`
FROM `biz_image_audit_record` r
LEFT JOIN `biz_image` i ON i.`id` = r.`image_id`
WHERE r.`status` = 0
ON DUPLICATE KEY UPDATE
    `target_audit_status` = VALUES(`target_audit_status`),
    `resolved_status` = VALUES(`resolved_status`),
    `resolution_reason` = VALUES(`resolution_reason`),
    `source_update_time` = VALUES(`source_update_time`),
    `migrated_time` = CURRENT_TIMESTAMP;

UPDATE `biz_image_audit_record` r
JOIN `audit_zero_status_migration_backup` b
  ON b.`target_type` = 'IMAGE' AND b.`audit_id` = r.`id`
SET r.`status` = b.`resolved_status`,
    r.`update_time` = CURRENT_TIMESTAMP
WHERE r.`status` = 0;

-- Retain the no-longer-written async command table for one rollout as forensic
-- evidence. It can be removed by a later, separately reviewed cleanup migration.
SET @async_command_state_exists = (
    SELECT COUNT(*)
    FROM information_schema.tables
    WHERE table_schema = DATABASE()
      AND table_name = 'sys_async_command_state'
);
SET @archive_async_command_state_sql = IF(
    @async_command_state_exists = 1,
    'ALTER TABLE `sys_async_command_state` COMMENT = ''Archived async command correlation state retained after synchronous audit migration''',
    'SELECT 1'
);
PREPARE archive_async_command_state FROM @archive_async_command_state_sql;
EXECUTE archive_async_command_state;
DEALLOCATE PREPARE archive_async_command_state;
