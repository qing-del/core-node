-- Phase 5 route-scope catalogue. Forward-only MySQL 8 migration.
-- Preserve wildcard role permissions for compatibility, but make user/admin
-- client issuance resource-specific so a default first-party token is not a
-- cross-resource wildcard token.

DELIMITER $$
DROP PROCEDURE IF EXISTS `phase5_business_route_scopes_preflight`$$
CREATE PROCEDURE `phase5_business_route_scopes_preflight`()
BEGIN
    DECLARE v_permission_count INT DEFAULT 0;
    DECLARE v_wildcard_count INT DEFAULT 0;
    DECLARE v_client_count INT DEFAULT 0;
    DECLARE v_user_legacy_count INT DEFAULT 0;
    DECLARE v_admin_legacy_count INT DEFAULT 0;
    DECLARE v_core_count INT DEFAULT 0;

    SELECT COUNT(*) INTO v_permission_count FROM `sys_permission`;
    SELECT COUNT(*) INTO v_wildcard_count
    FROM `sys_permission`
    WHERE BINARY `code` IN ('*:read', '*:write', '*:manage', '*:super');
    IF v_permission_count <> 4 OR v_wildcard_count <> 4 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Phase 5 route-scope migration requires the exact four-wildcard permission catalogue';
    END IF;

    SELECT COUNT(*) INTO v_client_count FROM `oauth2_registered_client`;
    SELECT COUNT(*) INTO v_user_legacy_count
    FROM `oauth2_registered_client`
    WHERE BINARY `client_id` = 'user'
      AND BINARY `scopes` = '*:read,*:write'
      AND BINARY `auto_approve` = '*:read,*:write'
      AND BINARY `status` = 'active';
    SELECT COUNT(*) INTO v_admin_legacy_count
    FROM `oauth2_registered_client`
    WHERE BINARY `client_id` = 'admin'
      AND BINARY `scopes` = '*:read,*:manage,*:super'
      AND BINARY `auto_approve` = '*:read,*:manage'
      AND BINARY `status` = 'active';
    SELECT COUNT(*) INTO v_core_count
    FROM `oauth2_registered_client`
    WHERE BINARY `client_id` = 'core_agent'
      AND BINARY `scopes` = 'note:read,note:write,sys:read,media:read'
      AND BINARY `auto_approve` = 'note:read,sys:read'
      AND BINARY `status` = 'active';
    IF v_client_count <> 3 OR v_user_legacy_count <> 1 OR v_admin_legacy_count <> 1 OR v_core_count <> 1 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Phase 5 route-scope migration found an unexpected registered-client state';
    END IF;
END$$
DELIMITER ;

CALL `phase5_business_route_scopes_preflight`();
DROP PROCEDURE `phase5_business_route_scopes_preflight`;

INSERT INTO `sys_permission` (`code`, `oauth_scope`, `resource`, `action`, `status`, `description`) VALUES
    ('account:read', NULL, 'account', 'read', 'active', 'Read account data'),
    ('account:write', NULL, 'account', 'write', 'active', 'Write own account data'),
    ('account:manage', NULL, 'account', 'manage', 'active', 'Manage accounts'),
    ('note:read', NULL, 'note', 'read', 'active', 'Read notes and note metadata'),
    ('note:write', NULL, 'note', 'write', 'active', 'Write own notes and note metadata'),
    ('note:manage', NULL, 'note', 'manage', 'active', 'Manage notes and note metadata'),
    ('media:read', NULL, 'media', 'read', 'active', 'Read media and media metadata'),
    ('media:write', NULL, 'media', 'write', 'active', 'Write own media and media metadata'),
    ('media:manage', NULL, 'media', 'manage', 'active', 'Manage media and media metadata'),
    ('audio:read', NULL, 'audio', 'read', 'active', 'Read audio tasks'),
    ('audio:write', NULL, 'audio', 'write', 'active', 'Create and modify own audio tasks'),
    ('audio:manage', NULL, 'audio', 'manage', 'active', 'Manage audio tasks'),
    ('audit:read', NULL, 'audit', 'read', 'active', 'Read audit records'),
    ('audit:write', NULL, 'audit', 'write', 'active', 'Submit or cancel own audit applications'),
    ('audit:manage', NULL, 'audit', 'manage', 'active', 'Review audit applications');

UPDATE `oauth2_registered_client`
SET `scopes` = 'account:read,account:write,audio:read,audio:write,audit:read,audit:write,media:read,media:write,note:read,note:write',
    `auto_approve` = 'account:read,account:write,audio:read,audio:write,audit:read,audit:write,media:read,media:write,note:read,note:write'
WHERE BINARY `client_id` = 'user';

UPDATE `oauth2_registered_client`
SET `scopes` = 'account:read,account:manage,audio:read,audio:manage,audit:read,audit:manage,media:read,media:manage,note:read,note:manage',
    `auto_approve` = 'account:read,account:manage,audio:read,audio:manage,audit:read,audit:manage,media:read,media:manage,note:read,note:manage'
WHERE BINARY `client_id` = 'admin';

DELIMITER $$
DROP PROCEDURE IF EXISTS `phase5_business_route_scopes_postflight`$$
CREATE PROCEDURE `phase5_business_route_scopes_postflight`()
BEGIN
    DECLARE v_permission_count INT DEFAULT 0;
    DECLARE v_exact_permission_count INT DEFAULT 0;
    DECLARE v_user_count INT DEFAULT 0;
    DECLARE v_admin_count INT DEFAULT 0;
    DECLARE v_core_count INT DEFAULT 0;

    SELECT COUNT(*) INTO v_permission_count FROM `sys_permission`;
    SELECT COUNT(*) INTO v_exact_permission_count
    FROM `sys_permission`
    WHERE BINARY `code` IN (
        'account:read', 'account:write', 'account:manage',
        'note:read', 'note:write', 'note:manage',
        'media:read', 'media:write', 'media:manage',
        'audio:read', 'audio:write', 'audio:manage',
        'audit:read', 'audit:write', 'audit:manage'
    ) AND BINARY `status` = 'active';
    SELECT COUNT(*) INTO v_user_count
    FROM `oauth2_registered_client`
    WHERE BINARY `client_id` = 'user'
      AND BINARY `scopes` = 'account:read,account:write,audio:read,audio:write,audit:read,audit:write,media:read,media:write,note:read,note:write'
      AND BINARY `auto_approve` = 'account:read,account:write,audio:read,audio:write,audit:read,audit:write,media:read,media:write,note:read,note:write';
    SELECT COUNT(*) INTO v_admin_count
    FROM `oauth2_registered_client`
    WHERE BINARY `client_id` = 'admin'
      AND BINARY `scopes` = 'account:read,account:manage,audio:read,audio:manage,audit:read,audit:manage,media:read,media:manage,note:read,note:manage'
      AND BINARY `auto_approve` = 'account:read,account:manage,audio:read,audio:manage,audit:read,audit:manage,media:read,media:manage,note:read,note:manage';
    SELECT COUNT(*) INTO v_core_count
    FROM `oauth2_registered_client`
    WHERE BINARY `client_id` = 'core_agent'
      AND BINARY `scopes` = 'note:read,note:write,sys:read,media:read'
      AND BINARY `auto_approve` = 'note:read,sys:read';
    IF v_permission_count <> 19 OR v_exact_permission_count <> 15
       OR v_user_count <> 1 OR v_admin_count <> 1 OR v_core_count <> 1 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Phase 5 route-scope migration postflight validation failed';
    END IF;
END$$
DELIMITER ;

CALL `phase5_business_route_scopes_postflight`();
DROP PROCEDURE `phase5_business_route_scopes_postflight`;
