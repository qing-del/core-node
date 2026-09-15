USE `personal_saas`;

-- Forward-only schema for document permission sharing links. The token itself
-- is returned only at creation time; the database stores its SHA-256 digest.
-- 迁移顺序：先确认文档表存在且目标表不存在，再创建短链主表和兑换幂等台账。
DELIMITER $$
DROP PROCEDURE IF EXISTS `document_share_link_preflight`$$
CREATE PROCEDURE `document_share_link_preflight`()
BEGIN
    DECLARE v_document_table_count INT DEFAULT 0;
    DECLARE v_share_link_table_count INT DEFAULT 0;
    DECLARE v_redemption_table_count INT DEFAULT 0;

    SELECT COUNT(*) INTO v_document_table_count
    FROM `information_schema`.`tables`
    WHERE `table_schema` = DATABASE()
      AND `table_name` = 'biz_document'
      AND `table_type` = 'BASE TABLE';
    SELECT COUNT(*) INTO v_share_link_table_count
    FROM `information_schema`.`tables`
    WHERE `table_schema` = DATABASE()
      AND `table_name` = 'biz_document_share_link';
    SELECT COUNT(*) INTO v_redemption_table_count
    FROM `information_schema`.`tables`
    WHERE `table_schema` = DATABASE()
      AND `table_name` = 'biz_document_share_link_redemption';

    -- 目标表已存在时停止迁移，避免重复执行造成索引或数据结构歧义。
    IF v_document_table_count <> 1
            OR v_share_link_table_count <> 0
            OR v_redemption_table_count <> 0 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Document share link migration preflight validation failed';
    END IF;
END$$
DELIMITER ;

CALL `document_share_link_preflight`();
DROP PROCEDURE `document_share_link_preflight`;

CREATE TABLE `biz_document_share_link` (
    -- token_hash 是唯一摘要，原始短链 code 不落库；其余列记录权限、有效期和限次状态。
    `id`              bigint       NOT NULL AUTO_INCREMENT COMMENT '分享短链ID',
    `document_id`     bigint       NOT NULL COMMENT '关联 biz_document.id',
    `creator_user_id` bigint       NOT NULL COMMENT '短链生成者用户ID',
    `token_hash`      binary(32)   NOT NULL COMMENT '原始短链令牌的SHA-256摘要',
    `permission`      varchar(16)  NOT NULL COMMENT 'READ 或 WRITE；WRITE 隐含 READ',
    `expires_at`      datetime(3)  NOT NULL COMMENT '短链有效期截止时间',
    `max_uses`        int unsigned NOT NULL COMMENT '短链最大有效兑换次数',
    `used_count`      int unsigned NOT NULL DEFAULT 0 COMMENT '已完成的有效兑换次数',
    `enabled`         tinyint      NOT NULL DEFAULT 1 COMMENT '短链是否有效(0:已取消,1:有效)',
    `revoked_at`      datetime(3)  DEFAULT NULL COMMENT '短链取消时间',
    `create_time`     datetime(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `update_time`     datetime(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
                                      ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_document_share_token_hash` (`token_hash`),
    KEY `idx_document_share_owner`
        (`document_id`, `creator_user_id`, `enabled`),
    KEY `idx_document_share_expiry`
        (`enabled`, `expires_at`)
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COMMENT='协作文档分享短链';

CREATE TABLE `biz_document_share_link_redemption` (
    -- 联合主键按“短链 + 用户”去重，使重复兑换只返回当前权限而不重复计数。
    `share_link_id` bigint      NOT NULL COMMENT '关联 biz_document_share_link.id',
    `user_id`       bigint      NOT NULL COMMENT '兑换用户ID',
    `permission`    varchar(16) NOT NULL COMMENT '本次兑换实际授予的权限',
    `redeemed_at`   datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '首次有效兑换时间',
    PRIMARY KEY (`share_link_id`, `user_id`),
    KEY `idx_share_redemption_user` (`user_id`, `redeemed_at`)
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COMMENT='文档分享短链兑换记录';

DELIMITER $$
DROP PROCEDURE IF EXISTS `document_share_link_postflight`$$
CREATE PROCEDURE `document_share_link_postflight`()
BEGIN
    DECLARE v_share_link_table_count INT DEFAULT 0;
    DECLARE v_redemption_table_count INT DEFAULT 0;
    DECLARE v_token_index_count INT DEFAULT 0;
    DECLARE v_owner_index_count INT DEFAULT 0;
    DECLARE v_expiry_index_count INT DEFAULT 0;
    DECLARE v_redemption_pk_count INT DEFAULT 0;

    SELECT COUNT(*) INTO v_share_link_table_count
    FROM `information_schema`.`tables`
    WHERE `table_schema` = DATABASE()
      AND `table_name` = 'biz_document_share_link'
      AND `table_type` = 'BASE TABLE';
    SELECT COUNT(*) INTO v_redemption_table_count
    FROM `information_schema`.`tables`
    WHERE `table_schema` = DATABASE()
      AND `table_name` = 'biz_document_share_link_redemption'
      AND `table_type` = 'BASE TABLE';
    SELECT COUNT(*) INTO v_token_index_count
    FROM `information_schema`.`statistics`
    WHERE `table_schema` = DATABASE()
      AND `table_name` = 'biz_document_share_link'
      AND `index_name` = 'uk_document_share_token_hash';
    SELECT COUNT(*) INTO v_owner_index_count
    FROM `information_schema`.`statistics`
    WHERE `table_schema` = DATABASE()
      AND `table_name` = 'biz_document_share_link'
      AND `index_name` = 'idx_document_share_owner';
    SELECT COUNT(*) INTO v_expiry_index_count
    FROM `information_schema`.`statistics`
    WHERE `table_schema` = DATABASE()
      AND `table_name` = 'biz_document_share_link'
      AND `index_name` = 'idx_document_share_expiry';
    SELECT COUNT(*) INTO v_redemption_pk_count
    FROM `information_schema`.`statistics`
    WHERE `table_schema` = DATABASE()
      AND `table_name` = 'biz_document_share_link_redemption'
      AND `index_name` = 'PRIMARY';

    -- 建表完成后校验主表、台账和关键索引，防止迁移半成功被误认为完成。
    IF v_share_link_table_count <> 1 OR v_redemption_table_count <> 1
            OR v_token_index_count = 0 OR v_owner_index_count = 0
            OR v_expiry_index_count = 0 OR v_redemption_pk_count <> 2 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Document share link migration postflight validation failed';
    END IF;
END$$
DELIMITER ;

CALL `document_share_link_postflight`();
DROP PROCEDURE `document_share_link_postflight`;
