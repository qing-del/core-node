-- Document module OAuth scope supplement. This migration follows the Phase 5
-- route-scope catalogue and intentionally changes only the first-party user
-- client; documents do not expose an admin or core_agent API in v0.3.

DELIMITER $$
DROP PROCEDURE IF EXISTS `document_oauth_scopes_preflight`$$
CREATE PROCEDURE `document_oauth_scopes_preflight`()
BEGIN
    DECLARE v_user_client_count INT DEFAULT 0;

    SELECT COUNT(*) INTO v_user_client_count
    FROM `oauth2_registered_client`
    WHERE BINARY `client_id` = 'user'
      AND BINARY `status` = 'active';
    IF v_user_client_count <> 1 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Document OAuth scope migration requires one active user client';
    END IF;
END$$
DELIMITER ;

CALL `document_oauth_scopes_preflight`();
DROP PROCEDURE `document_oauth_scopes_preflight`;

INSERT INTO `sys_permission` (`code`, `oauth_scope`, `resource`, `action`, `status`, `description`) VALUES
    ('document:read', NULL, 'document', 'read', 'active', 'Read collaborative documents and metadata'),
    ('document:write', NULL, 'document', 'write', 'active', 'Create and edit own collaborative documents')
ON DUPLICATE KEY UPDATE
    `oauth_scope` = VALUES(`oauth_scope`),
    `resource` = VALUES(`resource`),
    `action` = VALUES(`action`),
    `status` = VALUES(`status`),
    `description` = VALUES(`description`);

UPDATE `oauth2_registered_client`
SET `scopes` = 'account:read,account:write,audio:read,audio:write,audit:read,audit:write,document:read,document:write,media:read,media:write,note:read,note:write',
    `auto_approve` = 'account:read,account:write,audio:read,audio:write,audit:read,audit:write,document:read,document:write,media:read,media:write,note:read,note:write'
WHERE BINARY `client_id` = 'user';

DELIMITER $$
DROP PROCEDURE IF EXISTS `document_oauth_scopes_postflight`$$
CREATE PROCEDURE `document_oauth_scopes_postflight`()
BEGIN
    DECLARE v_document_permission_count INT DEFAULT 0;
    DECLARE v_user_client_count INT DEFAULT 0;

    SELECT COUNT(*) INTO v_document_permission_count
    FROM `sys_permission`
    WHERE BINARY `code` IN ('document:read', 'document:write')
      AND BINARY `resource` = 'document'
      AND BINARY `status` = 'active';
    SELECT COUNT(*) INTO v_user_client_count
    FROM `oauth2_registered_client`
    WHERE BINARY `client_id` = 'user'
      AND BINARY `scopes` = 'account:read,account:write,audio:read,audio:write,audit:read,audit:write,document:read,document:write,media:read,media:write,note:read,note:write'
      AND BINARY `auto_approve` = 'account:read,account:write,audio:read,audio:write,audit:read,audit:write,document:read,document:write,media:read,media:write,note:read,note:write'
      AND BINARY `status` = 'active';
    IF v_document_permission_count <> 2 OR v_user_client_count <> 1 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Document OAuth scope migration postflight validation failed';
    END IF;
END$$
DELIMITER ;

CALL `document_oauth_scopes_postflight`();
DROP PROCEDURE `document_oauth_scopes_postflight`;
