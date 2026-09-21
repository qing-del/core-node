-- Asynchronous command correlation state upgrade.
CREATE TABLE IF NOT EXISTS `sys_async_command_state` (
    `id`             bigint       NOT NULL AUTO_INCREMENT,
    `owner_module`   varchar(32)  NOT NULL,
    `aggregate_type` varchar(64)  NOT NULL,
    `aggregate_id`   bigint       NOT NULL,
    `command_id`     varchar(64)  NOT NULL,
    `command_type`   varchar(64)  NOT NULL,
    `state`          varchar(16)  NOT NULL,
    `update_time`    datetime(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_async_command_aggregate` (`owner_module`, `aggregate_type`, `aggregate_id`),
    KEY `idx_async_command_id` (`command_id`),
    KEY `idx_async_command_state_time` (`state`, `update_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Current asynchronous command correlation state';


-- Audio task management and storage accounting upgrade.
-- The historical 20260519 bootstrap script now creates this column for new
-- environments. Existing installations that applied the older bootstrap still
-- need this upgrade, so make the migration safe for both schemas.
SET @audio_size_column_exists = (
    SELECT COUNT(*)
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'audio_tasks'
      AND column_name = 'audio_size'
);
SET @audio_size_upgrade_sql = IF(
    @audio_size_column_exists = 0,
    'ALTER TABLE `audio_tasks` ADD COLUMN `audio_size` bigint DEFAULT NULL COMMENT ''成功后音频文件大小（字节）'' AFTER `result_url`',
    'SELECT 1'
);
PREPARE audio_size_upgrade FROM @audio_size_upgrade_sql;
EXECUTE audio_size_upgrade;
DEALLOCATE PREPARE audio_size_upgrade;

ALTER TABLE `audio_tasks`
    MODIFY COLUMN `status` tinyint NOT NULL DEFAULT 0
    COMMENT '状态：0-PENDING，1-PROCESSING，2-SUCCESS，-1-FAILED，-2-RETRIED，-3-CANCELLED';


-- audit_query_projection upgrade.
CREATE TABLE IF NOT EXISTS `audit_query_user_projection` (
                                                             `user_id`     bigint       NOT NULL,
                                                             `username`    varchar(128) NOT NULL,
    `nickname`    varchar(128) DEFAULT NULL,
    `update_time` datetime(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (`user_id`)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Audit-owned current user projection used when taking snapshots';

CREATE TABLE IF NOT EXISTS `audit_query_record_projection` (
    `target_type`        varchar(16)   NOT NULL,
    `audit_id`           bigint        NOT NULL,
    `target_id`          bigint        NOT NULL,
    `applicant_username` varchar(128)  DEFAULT NULL,
    `reviewer_username`  varchar(128)  DEFAULT NULL,
    `target_name`        varchar(500)  DEFAULT NULL,
    `target_url`         varchar(1000) DEFAULT NULL,
    `update_time`        datetime(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (`target_type`, `audit_id`),
    KEY `idx_audit_query_record_target` (`target_type`, `target_id`)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Immutable display snapshot per audit record';

-- One-time compatibility backfill. Runtime audit queries never join owner-module tables.
INSERT INTO audit_query_user_projection (user_id, username, nickname)
SELECT id, username, nickname FROM sys_user
    ON DUPLICATE KEY UPDATE username=VALUES(username), nickname=VALUES(nickname), update_time=NOW();

INSERT INTO audit_query_record_projection
(target_type, audit_id, target_id, applicant_username, reviewer_username, target_name, target_url)
SELECT 'NOTE', audit.id, audit.note_id, applicant.username, reviewer.username, note.title, NULL
FROM biz_note_audit_record audit
         LEFT JOIN sys_user applicant ON applicant.id = audit.applicant_user_id
         LEFT JOIN sys_user reviewer ON reviewer.id = audit.reviewer_user_id
         LEFT JOIN biz_note note ON note.id = audit.note_id
UNION ALL
SELECT 'TAG', audit.id, audit.target_id, applicant.username, reviewer.username, tag.tag_name, NULL
FROM biz_tag_audit_record audit
         LEFT JOIN sys_user applicant ON applicant.id = audit.applicant_user_id
         LEFT JOIN sys_user reviewer ON reviewer.id = audit.reviewer_user_id
         LEFT JOIN biz_tag tag ON tag.id = audit.target_id
WHERE audit.apply_type = 2
UNION ALL
SELECT 'IMAGE', audit.id, audit.image_id, applicant.username, reviewer.username, image.filename, image.oss_url
FROM biz_image_audit_record audit
         LEFT JOIN sys_user applicant ON applicant.id = audit.applicant_user_id
         LEFT JOIN sys_user reviewer ON reviewer.id = audit.reviewer_user_id
         LEFT JOIN biz_image image ON image.id = audit.image_id
    ON DUPLICATE KEY UPDATE audit_id=VALUES(audit_id);


-- Preserve the legacy physical-delete table as an observable migration/tracking ledger.
-- Statuses: 0 legacy waiting, 1 completed, 2 queued through Outbox, 3 terminal failure.
ALTER TABLE `biz_image_delete_dead_letter`
    ADD COLUMN `resource_id` varchar(64) DEFAULT NULL AFTER `id`,
    ADD COLUMN `event_id` varchar(64) DEFAULT NULL AFTER `image_url`,
    ADD COLUMN `last_error` varchar(500) DEFAULT NULL AFTER `retry_count`,
    ADD COLUMN `completed_time` datetime DEFAULT NULL AFTER `update_time`,
    ADD KEY `idx_delete_event` (`event_id`),
    ADD KEY `idx_delete_resource` (`resource_id`);


-- Reliable at-least-once domain event delivery.
CREATE TABLE IF NOT EXISTS `sys_event_outbox` (
                                                  `id`              bigint       NOT NULL AUTO_INCREMENT,
                                                  `event_id`        varchar(64)  NOT NULL,
    `event_type`      varchar(128) NOT NULL,
    `routing_key`     varchar(128) NOT NULL,
    `payload`         longtext     NOT NULL,
    `status`          varchar(16)  NOT NULL DEFAULT 'PENDING',
    `retry_count`     int          NOT NULL DEFAULT 0,
    `next_retry_time` datetime(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `last_error`      varchar(2000) DEFAULT NULL,
    `claimed_by`      varchar(64)  DEFAULT NULL,
    `claim_until`     datetime(3)  DEFAULT NULL,
    `create_time`     datetime(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `published_time`  datetime(3)  DEFAULT NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_event_outbox_event_id` (`event_id`),
    KEY `idx_event_outbox_ready` (`status`, `next_retry_time`, `id`),
    KEY `idx_event_outbox_claim` (`status`, `claim_until`)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Transactional domain event outbox';

CREATE TABLE IF NOT EXISTS `sys_event_inbox` (
                                                 `id`            bigint       NOT NULL AUTO_INCREMENT,
                                                 `event_id`      varchar(64)  NOT NULL,
    `consumer_name` varchar(128) NOT NULL,
    `event_type`    varchar(128) NOT NULL,
    `status`        varchar(16)  NOT NULL,
    `last_error`    varchar(2000) DEFAULT NULL,
    `create_time`   datetime(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `consumed_time` datetime(3)  DEFAULT NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_event_inbox_consumer` (`event_id`, `consumer_name`),
    KEY `idx_event_inbox_consumer_time` (`consumer_name`, `consumed_time`)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Idempotent domain event consumption records';

CREATE TABLE IF NOT EXISTS `sys_event_projection_version` (
                                                              `id`             bigint       NOT NULL AUTO_INCREMENT,
                                                              `consumer_name`  varchar(128) NOT NULL,
    `aggregate_type` varchar(64)  NOT NULL,
    `aggregate_id`   bigint       NOT NULL,
    `last_sequence`  bigint       NOT NULL,
    `update_time`    datetime(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_event_projection_aggregate`
(`consumer_name`, `aggregate_type`, `aggregate_id`)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Last applied sequence for ordered event projections';
