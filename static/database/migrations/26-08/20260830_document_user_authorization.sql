USE `personal_saas`;

-- Forward-only v0.4 document ownership and direct user authorization schema.
-- Keep team_id during the compatibility rollout; a later migration will retire it
-- after all document readers and writers use owner_user_id.
ALTER TABLE `biz_document`
    ADD COLUMN `owner_user_id` bigint NULL
        COMMENT '文档所有者用户ID'
        AFTER `id`;

UPDATE `biz_document`
SET `owner_user_id` = `team_id`
WHERE `owner_user_id` IS NULL;

ALTER TABLE `biz_document`
    MODIFY COLUMN `owner_user_id` bigint NOT NULL
        COMMENT '文档所有者用户ID',
    ADD KEY `idx_document_owner_deleted_time`
        (`owner_user_id`, `deleted`, `last_modify_time`);

CREATE TABLE `biz_document_user` (
    `document_id` bigint      NOT NULL COMMENT '关联 biz_document.id',
    `user_id`     bigint      NOT NULL COMMENT '被授权用户ID',
    `permission`  varchar(16) NOT NULL
        COMMENT 'READ 或 WRITE；WRITE 隐含 READ',
    `enabled`     tinyint     NOT NULL DEFAULT 1
        COMMENT '授权是否生效(0:已撤销,1:生效)',
    `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
        ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (`document_id`, `user_id`),
    KEY `idx_document_user_visible`
        (`user_id`, `enabled`, `document_id`)
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COMMENT='协作文档用户直接授权';

-- Retire the v0.3 personal-scope column only after the owner column is complete
-- and all document readers/writers have been deployed.  This section is merged
-- here because the 08-30 migrations have not been executed independently.
DELIMITER $$
DROP PROCEDURE IF EXISTS `document_user_authorization_retire_team_preflight`$$
CREATE PROCEDURE `document_user_authorization_retire_team_preflight`()
BEGIN
    DECLARE v_owner_column_count INT DEFAULT 0;
    DECLARE v_owner_null_count INT DEFAULT 0;
    DECLARE v_owner_index_count INT DEFAULT 0;
    DECLARE v_team_column_count INT DEFAULT 0;
    DECLARE v_team_index_count INT DEFAULT 0;

    SELECT COUNT(*) INTO v_owner_column_count
    FROM `information_schema`.`columns`
    WHERE `table_schema` = DATABASE()
      AND `table_name` = 'biz_document'
      AND `column_name` = 'owner_user_id';
    SELECT COUNT(*) INTO v_owner_null_count
    FROM `biz_document`
    WHERE `owner_user_id` IS NULL;
    SELECT COUNT(*) INTO v_owner_index_count
    FROM `information_schema`.`statistics`
    WHERE `table_schema` = DATABASE()
      AND `table_name` = 'biz_document'
      AND `index_name` = 'idx_document_owner_deleted_time';
    SELECT COUNT(*) INTO v_team_column_count
    FROM `information_schema`.`columns`
    WHERE `table_schema` = DATABASE()
      AND `table_name` = 'biz_document'
      AND `column_name` = 'team_id';
    SELECT COUNT(*) INTO v_team_index_count
    FROM `information_schema`.`statistics`
    WHERE `table_schema` = DATABASE()
      AND `table_name` = 'biz_document'
      AND `index_name` = 'idx_document_scope_deleted_time';

    IF v_owner_column_count <> 1 OR v_owner_null_count <> 0
            OR v_owner_index_count = 0 OR v_team_column_count <> 1
            OR v_team_index_count <> 1 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Document authorization team retirement preflight validation failed';
    END IF;
END$$
DELIMITER ;

CALL `document_user_authorization_retire_team_preflight`();
DROP PROCEDURE `document_user_authorization_retire_team_preflight`;

ALTER TABLE `biz_document`
    DROP INDEX `idx_document_scope_deleted_time`,
    DROP COLUMN `team_id`;

DELIMITER $$
DROP PROCEDURE IF EXISTS `document_user_authorization_retire_team_postflight`$$
CREATE PROCEDURE `document_user_authorization_retire_team_postflight`()
BEGIN
    DECLARE v_owner_column_count INT DEFAULT 0;
    DECLARE v_owner_index_count INT DEFAULT 0;
    DECLARE v_team_column_count INT DEFAULT 0;
    DECLARE v_team_index_count INT DEFAULT 0;

    SELECT COUNT(*) INTO v_owner_column_count
    FROM `information_schema`.`columns`
    WHERE `table_schema` = DATABASE()
      AND `table_name` = 'biz_document'
      AND `column_name` = 'owner_user_id';
    SELECT COUNT(*) INTO v_owner_index_count
    FROM `information_schema`.`statistics`
    WHERE `table_schema` = DATABASE()
      AND `table_name` = 'biz_document'
      AND `index_name` = 'idx_document_owner_deleted_time';
    SELECT COUNT(*) INTO v_team_column_count
    FROM `information_schema`.`columns`
    WHERE `table_schema` = DATABASE()
      AND `table_name` = 'biz_document'
      AND `column_name` = 'team_id';
    SELECT COUNT(*) INTO v_team_index_count
    FROM `information_schema`.`statistics`
    WHERE `table_schema` = DATABASE()
      AND `table_name` = 'biz_document'
      AND `index_name` = 'idx_document_scope_deleted_time';

    IF v_owner_column_count <> 1 OR v_owner_index_count = 0
            OR v_team_column_count <> 0 OR v_team_index_count <> 0 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Document authorization team retirement postflight validation failed';
    END IF;
END$$
DELIMITER ;

CALL `document_user_authorization_retire_team_postflight`();
DROP PROCEDURE `document_user_authorization_retire_team_postflight`;
