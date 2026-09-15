-- Phase 3 account-grant rename. Forward-only MySQL 8 migration.
-- Take a verified backup before execution. Any unexpected legacy value or schema state
-- must be investigated instead of bypassing this preflight.

DELIMITER $$
DROP PROCEDURE IF EXISTS `phase3_account_grants_preflight`$$
CREATE PROCEDURE `phase3_account_grants_preflight`()
BEGIN
    DECLARE v_legacy_column_count INT DEFAULT 0;
    DECLARE v_extra_column_count INT DEFAULT 0;
    DECLARE v_invalid_legacy_value_count INT DEFAULT 0;

    SELECT COUNT(*) INTO v_legacy_column_count
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'sys_user'
      AND column_name = 'grant_types';
    IF v_legacy_column_count <> 1 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Phase 3 account-grant migration requires sys_user.grant_types exactly once';
    END IF;

    SELECT COUNT(*) INTO v_extra_column_count
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'sys_user'
      AND column_name = 'extra_grant_types';
    IF v_extra_column_count <> 0 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Phase 3 account-grant migration found sys_user.extra_grant_types already present';
    END IF;

    -- Anchored, case-sensitive MySQL 8 REGEXP rejects empty CSV items, whitespace,
    -- unknown values, and substring lookalikes such as "not_password".
    SELECT COUNT(*) INTO v_invalid_legacy_value_count
    FROM `sys_user`
    WHERE NOT REGEXP_LIKE(
            COALESCE(`grant_types`, ''),
            '^(password|user_password|admin_password|agent_client)(,(password|user_password|admin_password|agent_client))*$',
            'c');
    IF v_invalid_legacy_value_count <> 0 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Phase 3 account-grant migration found an unsupported sys_user.grant_types CSV value';
    END IF;
END$$
DELIMITER ;

CALL `phase3_account_grants_preflight`();
DROP PROCEDURE `phase3_account_grants_preflight`;

ALTER TABLE `sys_user`
    CHANGE COLUMN `grant_types` `extra_grant_types` varchar(255) NOT NULL DEFAULT ''
        COMMENT '仅保存账号显式附加授权方式（CSV）；默认授权方式来自配置；不得包含 refresh_token';

-- Historical agent_client is no longer an account grant. Defaults are configuration-owned,
-- therefore every migrated account starts without an explicit addition.
UPDATE `sys_user`
SET `extra_grant_types` = '';
