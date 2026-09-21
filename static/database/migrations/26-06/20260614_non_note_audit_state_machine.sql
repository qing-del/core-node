USE `personal_saas`;

-- Topic is now only a directory. Remove topic audit metadata.
ALTER TABLE `biz_topic` DROP INDEX `idx_user_pass`;
ALTER TABLE `biz_topic` DROP COLUMN `is_pass`;

-- Tags use the non-note audit state machine:
-- 0=WAIT, 1=AUDITING, 2=APPROVED, 3=REJECTED, 4=DELETED.
ALTER TABLE `biz_tag`
    DROP INDEX `idx_user_pass`,
    CHANGE COLUMN `is_pass` `audit_status` tinyint NOT NULL DEFAULT 0 COMMENT '审核状态(0:待审核,1:审核中,2:已通过,3:已拒绝,4:已删除)';

UPDATE `biz_tag`
SET `audit_status` = CASE `audit_status`
    WHEN 1 THEN 2
    WHEN 2 THEN 3
    ELSE 0
END;

CREATE INDEX `idx_user_audit_status` ON `biz_tag` (`user_id`, `audit_status`);

-- Images use the same state machine.
ALTER TABLE `biz_image`
    DROP INDEX `idx_user_pass`,
    CHANGE COLUMN `is_pass` `audit_status` tinyint NOT NULL DEFAULT 0 COMMENT '审核状态(0:待审核,1:审核中,2:已通过,3:已拒绝,4:已删除)';

UPDATE `biz_image`
SET `audit_status` = CASE `audit_status`
    WHEN 1 THEN 2
    WHEN 2 THEN 3
    ELSE 0
END;

CREATE INDEX `idx_user_audit_status` ON `biz_image` (`user_id`, `audit_status`);

-- Migrate tag-only audit records from the old topic/tag shared audit table.
CREATE TABLE `biz_tag_audit_record` (
    `id`                bigint       NOT NULL AUTO_INCREMENT COMMENT '审核记录ID',
    `applicant_user_id` bigint       NOT NULL COMMENT '申请者用户ID',
    `target_id`         bigint       NOT NULL COMMENT '关联的标签ID(biz_tag.id)',
    `apply_reason`      varchar(500) DEFAULT NULL COMMENT '申请说明(可选)',
    `status`            tinyint      NOT NULL DEFAULT 0 COMMENT '审核状态(0:待审核,1:审核中,2:已通过,3:已拒绝,4:已删除)',
    `reviewer_user_id`  bigint       DEFAULT NULL COMMENT '审核员用户ID(NULL=尚未审核)',
    `reject_reason`     varchar(500) DEFAULT NULL COMMENT '拒绝原因(status=3时填写)',
    `create_time`       datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '申请提交时间',
    `review_time`       datetime     DEFAULT NULL COMMENT '审核完成时间',
    `update_time`       datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最后更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_applicant_status` (`applicant_user_id`, `status`),
    KEY `idx_status`           (`status`),
    KEY `idx_target`           (`target_id`),
    KEY `idx_status_update`    (`status`, `update_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='标签审核表';

INSERT INTO `biz_tag_audit_record` (
    `id`, `applicant_user_id`, `target_id`, `apply_reason`, `status`,
    `reviewer_user_id`, `reject_reason`, `create_time`, `review_time`, `update_time`
)
SELECT
    `id`,
    `applicant_user_id`,
    `target_id`,
    `apply_reason`,
    CASE `status`
        WHEN 0 THEN 1
        WHEN 1 THEN 2
        WHEN 2 THEN 3
        ELSE 0
    END,
    `reviewer_user_id`,
    `reject_reason`,
    `create_time`,
    `review_time`,
    `update_time`
FROM `biz_meta_audit_record`
WHERE `apply_type` = 2;

UPDATE `biz_tag` t
JOIN `biz_tag_audit_record` r ON r.`target_id` = t.`id` AND r.`status` = 1
SET t.`audit_status` = 1
WHERE t.`audit_status` = 0;

DROP TABLE `biz_meta_audit_record`;

-- Image audit records also move to the new state machine.
UPDATE `biz_image_audit_record`
SET `status` = CASE `status`
    WHEN 0 THEN 1
    WHEN 1 THEN 2
    WHEN 2 THEN 3
    ELSE 0
END;

UPDATE `biz_image` i
JOIN `biz_image_audit_record` r ON r.`image_id` = i.`id` AND r.`status` = 1
SET i.`audit_status` = 1
WHERE i.`audit_status` = 0;

-- Relation snapshot rows keep the column name is_pass, but values follow the new tag/image audit codes.
UPDATE `biz_note_tag_mapping`
SET `is_pass` = CASE `is_pass`
    WHEN 1 THEN 2
    WHEN 2 THEN 3
    ELSE 0
END;

UPDATE `biz_note_tag_mapping` m
JOIN `biz_tag` t ON t.`id` = m.`tag_id`
SET m.`is_pass` = t.`audit_status`
WHERE m.`is_deleted` = 0;

UPDATE `biz_note_image_mapping` m
JOIN `biz_image` i ON i.`id` = m.`image_id`
SET m.`is_pass` = i.`audit_status`
WHERE m.`is_deleted` = 0;

UPDATE `biz_note_image_mapping`
SET `is_pass` = CASE `is_pass`
    WHEN 1 THEN 2
    WHEN 2 THEN 3
    ELSE 0
END;
