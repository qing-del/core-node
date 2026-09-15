-- Phase 1 authorization foundation. This is a forward-only MySQL 8 migration.
-- Take a verified database backup before execution. The preflight below fails closed
-- for an unexpected legacy state or a partially applied prior run.

DELIMITER $$
DROP PROCEDURE IF EXISTS `phase1_auth_rbac_preflight`$$
CREATE PROCEDURE `phase1_auth_rbac_preflight`()
BEGIN
    DECLARE v_role_count INT DEFAULT 0;
    DECLARE v_expected_role_count INT DEFAULT 0;
    DECLARE v_orphan_user_count INT DEFAULT 0;
    DECLARE v_new_table_count INT DEFAULT 0;
    DECLARE v_new_column_count INT DEFAULT 0;

    SELECT COUNT(*) INTO v_role_count FROM `sys_role`;
    SELECT COUNT(*) INTO v_expected_role_count
    FROM `sys_role`
    WHERE `role_code` IN ('CREATOR', 'ADMIN', 'USER', 'VIP');
    IF v_role_count <> 4 OR v_expected_role_count <> 4 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Phase 1 auth migration requires exactly CREATOR, ADMIN, USER, and VIP roles';
    END IF;

    SELECT COUNT(*) INTO v_orphan_user_count
    FROM `sys_user` u
    LEFT JOIN `sys_role` r ON r.`id` = u.`role_id`
    WHERE r.`id` IS NULL;
    IF v_orphan_user_count <> 0 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Phase 1 auth migration found sys_user rows with an unknown role_id';
    END IF;

    SELECT COUNT(*) INTO v_new_column_count
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND ((table_name = 'sys_role' AND column_name = 'rank')
        OR (table_name = 'sys_user' AND column_name = 'grant_types'));
    IF v_new_column_count <> 0 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Phase 1 auth migration was already started; restore or inspect before retrying';
    END IF;

    SELECT COUNT(*) INTO v_new_table_count
    FROM information_schema.tables
    WHERE table_schema = DATABASE()
      AND table_name IN (
          'oauth2_registered_client',
          'oauth2_authorization_consent',
          'sys_permission',
          'sys_role_perm'
      );
    IF v_new_table_count <> 0 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Phase 1 auth metadata tables already exist; restore or inspect before retrying';
    END IF;
END$$
DELIMITER ;

CALL `phase1_auth_rbac_preflight`();
DROP PROCEDURE `phase1_auth_rbac_preflight`;

-- Every legacy VIP account becomes a USER before the VIP role is deleted.
UPDATE `sys_user` u
JOIN `sys_role` vip ON vip.`role_code` = 'VIP'
JOIN `sys_role` user_role ON user_role.`role_code` = 'USER'
SET u.`role_id` = user_role.`id`
WHERE u.`role_id` = vip.`id`;

-- The preflight rejects an existing sys_role_perm table, so no persisted role
-- permission relation can still reference VIP in this migration baseline.
DELETE FROM `sys_role` WHERE `role_code` = 'VIP';

ALTER TABLE `sys_role`
    ADD COLUMN `rank` int unsigned DEFAULT NULL COMMENT '角色等级；数值越小等级越高' AFTER `role_code`;

UPDATE `sys_role`
SET `rank` = CASE `role_code`
    WHEN 'CREATOR' THEN 1
    WHEN 'ADMIN' THEN 2
    WHEN 'USER' THEN 3
    ELSE NULL
END;

-- No legacy role-ID behavior is changed in this migration. Later code switches to rank.
ALTER TABLE `sys_role`
    MODIFY COLUMN `rank` int unsigned NOT NULL COMMENT '角色等级；数值越小等级越高',
    ADD UNIQUE KEY `uk_sys_role_rank` (`rank`);

ALTER TABLE `sys_user`
    ADD COLUMN `grant_types` varchar(255) NOT NULL DEFAULT 'password,user_password,agent_client'
        COMMENT '账号允许的授权方式，逗号分隔；不包含 authorization_code 或 refresh_token' AFTER `role_id`;

UPDATE `sys_user` u
JOIN `sys_role` r ON r.`id` = u.`role_id`
SET u.`grant_types` = CASE r.`role_code`
    WHEN 'USER' THEN 'password,user_password,agent_client'
    WHEN 'ADMIN' THEN 'admin_password,agent_client'
    WHEN 'CREATOR' THEN 'admin_password,agent_client'
    ELSE u.`grant_types`
END;

CREATE TABLE `sys_role_perm` (
    `role_id` bigint NOT NULL COMMENT '角色ID',
    `perm_id` bigint NOT NULL COMMENT '权限ID',
    `grant_time` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '授予时间',
    PRIMARY KEY (`role_id`, `perm_id`),
    KEY `idx_sys_role_perm_perm_id` (`perm_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='角色权限关联表';

-- Spring Authorization Server 7.0.4 JDBC RegisteredClient schema plus the
-- application-owned auto-approve, status, and allowed-IP metadata.
CREATE TABLE `oauth2_registered_client` (
    `id` varchar(100) NOT NULL,
    `client_id` varchar(100) NOT NULL,
    `client_id_issued_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `client_secret` varchar(200) DEFAULT NULL,
    `client_secret_expires_at` timestamp NULL DEFAULT NULL,
    `client_name` varchar(200) NOT NULL,
    `client_authentication_methods` varchar(1000) NOT NULL,
    `authorization_grant_types` varchar(1000) NOT NULL,
    `redirect_uris` varchar(1000) DEFAULT NULL,
    `post_logout_redirect_uris` varchar(1000) DEFAULT NULL,
    `scopes` varchar(1000) NOT NULL,
    `client_settings` varchar(2000) NOT NULL,
    `token_settings` varchar(2000) NOT NULL,
    `auto_approve` varchar(1000) NOT NULL COMMENT '默认同意的权限，逗号分隔',
    `status` varchar(16) NOT NULL COMMENT 'active 或 disabled',
    `allowed_ips` varchar(1000) NOT NULL COMMENT '允许来源 CIDR，逗号分隔',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_oauth2_registered_client_client_id` (`client_id`),
    KEY `idx_oauth2_registered_client_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='OAuth2 注册客户端';

CREATE TABLE `oauth2_authorization_consent` (
    `registered_client_id` varchar(100) NOT NULL,
    `principal_name` varchar(200) NOT NULL COMMENT 'sys_user.id 的十进制字符串',
    `authorities` varchar(1000) NOT NULL,
    PRIMARY KEY (`registered_client_id`, `principal_name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='OAuth2 用户授权同意';

CREATE TABLE `sys_permission` (
    `id` bigint NOT NULL AUTO_INCREMENT,
    `code` varchar(100) NOT NULL COMMENT '唯一权限标识，例如 note:read 或 *:read',
    `oauth_scope` varchar(100) DEFAULT NULL COMMENT '兼容映射字段，不参与核心权限判定',
    `resource` varchar(50) NOT NULL COMMENT '权限资源，例如 note 或 *',
    `action` varchar(32) NOT NULL COMMENT '权限动作，例如 read 或 *',
    `status` varchar(16) NOT NULL COMMENT 'active 或 disabled',
    `description` varchar(255) DEFAULT NULL,
    `create_time` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    `update_time` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_sys_permission_code` (`code`),
    KEY `idx_sys_permission_resource_action_status` (`resource`, `action`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='系统权限目录';

-- SAS 7.0.4 JsonMapper-compatible settings. user/admin refresh-token rotation
-- is enabled by reuse-refresh-tokens=false. core_agent does not list refresh_token.
-- user/admin remain disabled until Phase 3 provisions their credentials;
-- core_agent remains disabled until Phase 4 enables its registered redirect flow.
-- authorization_client is a disabled reserved client in the first release.
INSERT INTO `oauth2_registered_client` (
    `id`, `client_id`, `client_name`, `client_authentication_methods`,
    `authorization_grant_types`, `redirect_uris`, `post_logout_redirect_uris`,
    `scopes`, `client_settings`, `token_settings`, `auto_approve`, `status`, `allowed_ips`
) VALUES
(
    'e7cf5b30-8e43-4db2-bc53-000000000001',
    'user',
    'CORE NODE User Client',
    'client_secret_post',
    'password,user_password,refresh_token',
    NULL,
    NULL,
    '*:read,*:write',
    '{"@class":"java.util.Collections$UnmodifiableMap","settings.client.require-proof-key":false,"settings.client.require-authorization-consent":false}',
    '{"@class":"java.util.Collections$UnmodifiableMap","settings.token.reuse-refresh-tokens":false,"settings.token.x509-certificate-bound-access-tokens":false,"settings.token.id-token-signature-algorithm":["org.springframework.security.oauth2.jose.jws.SignatureAlgorithm","RS256"],"settings.token.access-token-time-to-live":["java.time.Duration","PT3H"],"settings.token.access-token-format":{"@class":"org.springframework.security.oauth2.server.authorization.settings.OAuth2TokenFormat","value":"self-contained"},"settings.token.refresh-token-time-to-live":["java.time.Duration","PT72H"],"settings.token.authorization-code-time-to-live":["java.time.Duration","PT5M"],"settings.token.device-code-time-to-live":["java.time.Duration","PT5M"]}',
    '*:read,*:write',
    'disabled',
    '0.0.0.0/0'
),
(
    'e7cf5b30-8e43-4db2-bc53-000000000002',
    'admin',
    'CORE NODE Admin Client',
    'client_secret_post',
    'admin_password,refresh_token',
    NULL,
    NULL,
    '*:read,*:manage,*:super',
    '{"@class":"java.util.Collections$UnmodifiableMap","settings.client.require-proof-key":false,"settings.client.require-authorization-consent":false}',
    '{"@class":"java.util.Collections$UnmodifiableMap","settings.token.reuse-refresh-tokens":false,"settings.token.x509-certificate-bound-access-tokens":false,"settings.token.id-token-signature-algorithm":["org.springframework.security.oauth2.jose.jws.SignatureAlgorithm","RS256"],"settings.token.access-token-time-to-live":["java.time.Duration","PT3H"],"settings.token.access-token-format":{"@class":"org.springframework.security.oauth2.server.authorization.settings.OAuth2TokenFormat","value":"self-contained"},"settings.token.refresh-token-time-to-live":["java.time.Duration","PT72H"],"settings.token.authorization-code-time-to-live":["java.time.Duration","PT5M"],"settings.token.device-code-time-to-live":["java.time.Duration","PT5M"]}',
    '*:read,*:manage',
    'disabled',
    '0.0.0.0/0'
),
(
    'e7cf5b30-8e43-4db2-bc53-000000000003',
    'core_agent',
    'CORE AGENT',
    'none',
    'agent_client,authorization_code',
    'http://127.0.0.1:9090/oauth/callback',
    NULL,
    'note:read,note:write,sys:read,media:read',
    '{"@class":"java.util.Collections$UnmodifiableMap","settings.client.require-proof-key":true,"settings.client.require-authorization-consent":true}',
    '{"@class":"java.util.Collections$UnmodifiableMap","settings.token.reuse-refresh-tokens":true,"settings.token.x509-certificate-bound-access-tokens":false,"settings.token.id-token-signature-algorithm":["org.springframework.security.oauth2.jose.jws.SignatureAlgorithm","RS256"],"settings.token.access-token-time-to-live":["java.time.Duration","PT1H"],"settings.token.access-token-format":{"@class":"org.springframework.security.oauth2.server.authorization.settings.OAuth2TokenFormat","value":"self-contained"},"settings.token.refresh-token-time-to-live":["java.time.Duration","PT1H"],"settings.token.authorization-code-time-to-live":["java.time.Duration","PT5M"],"settings.token.device-code-time-to-live":["java.time.Duration","PT5M"]}',
    'note:read,sys:read',
    'disabled',
    '0.0.0.0/0'
),
(
    'e7cf5b30-8e43-4db2-bc53-000000000004',
    'authorization_client',
    'Authorization Client (Reserved)',
    'client_secret_post',
    'authorization_code',
    NULL,
    NULL,
    '',
    '{"@class":"java.util.Collections$UnmodifiableMap","settings.client.require-proof-key":false,"settings.client.require-authorization-consent":false}',
    '{"@class":"java.util.Collections$UnmodifiableMap","settings.token.reuse-refresh-tokens":true,"settings.token.x509-certificate-bound-access-tokens":false,"settings.token.id-token-signature-algorithm":["org.springframework.security.oauth2.jose.jws.SignatureAlgorithm","RS256"],"settings.token.access-token-time-to-live":["java.time.Duration","PT5M"],"settings.token.access-token-format":{"@class":"org.springframework.security.oauth2.server.authorization.settings.OAuth2TokenFormat","value":"self-contained"},"settings.token.refresh-token-time-to-live":["java.time.Duration","PT1H"],"settings.token.authorization-code-time-to-live":["java.time.Duration","PT5M"],"settings.token.device-code-time-to-live":["java.time.Duration","PT5M"]}',
    '',
    'disabled',
    '0.0.0.0/0'
);

-- Keep wildcard permissions as data and preserve them in JWT scope claims. A
-- later unified authorization utility performs the resource/action matching.
INSERT INTO `sys_permission` (`code`, `oauth_scope`, `resource`, `action`, `status`, `description`) VALUES
    ('*:read', NULL, '*', 'read', 'active', '读取权限通配符'),
    ('*:write', NULL, '*', 'write', 'active', '写入权限通配符'),
    ('*:manage', NULL, '*', 'manage', 'active', '管理权限通配符'),
    ('*:super', NULL, '*', 'super', 'active', '创建者专属权限通配符');

-- Direct grants are intentionally minimal. Effective permissions inherit lower
-- role behavior through sys_role.rank in the later authorization resolver.
INSERT INTO `sys_role_perm` (`role_id`, `perm_id`)
SELECT r.`id`, p.`id`
FROM `sys_role` r
JOIN `sys_permission` p
  ON (r.`role_code` = 'USER' AND p.`code` IN ('*:read', '*:write'))
  OR (r.`role_code` = 'ADMIN' AND p.`code` = '*:manage')
  OR (r.`role_code` = 'CREATOR' AND p.`code` = '*:super');

DELIMITER $$
DROP PROCEDURE IF EXISTS `phase1_auth_rbac_postflight`$$
CREATE PROCEDURE `phase1_auth_rbac_postflight`()
BEGIN
    DECLARE v_count INT DEFAULT 0;

    SELECT COUNT(*) INTO v_count FROM `sys_role`;
    IF v_count <> 3 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Phase 1 auth migration did not leave exactly three roles';
    END IF;

    SELECT COUNT(*) INTO v_count
    FROM `sys_user` u
    JOIN `sys_role` r ON r.`id` = u.`role_id`
    WHERE r.`role_code` = 'VIP';
    IF v_count <> 0 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Phase 1 auth migration left VIP users';
    END IF;

    SELECT COUNT(*) INTO v_count
    FROM `oauth2_registered_client`;
    IF v_count <> 4 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Phase 1 auth migration did not seed four OAuth clients';
    END IF;

    SELECT COUNT(*) INTO v_count FROM `sys_permission`;
    IF v_count <> 4 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Phase 1 auth migration did not seed four wildcard permissions';
    END IF;

    SELECT COUNT(*) INTO v_count FROM `sys_role_perm`;
    IF v_count <> 4 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Phase 1 auth migration did not seed four direct role permissions';
    END IF;
END$$
DELIMITER ;

CALL `phase1_auth_rbac_postflight`();
DROP PROCEDURE `phase1_auth_rbac_postflight`;
