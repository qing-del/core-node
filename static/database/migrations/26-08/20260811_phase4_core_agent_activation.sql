-- Phase 4 core_agent activation. Forward-only MySQL 8 migration.
-- The preflight accepts only the complete Phase 3 catalogue, so this migration
-- never turns an edited or unknown client record into an active public client.

DELIMITER $$
DROP PROCEDURE IF EXISTS `phase4_core_agent_activation_preflight`$$
CREATE PROCEDURE `phase4_core_agent_activation_preflight`()
BEGIN
    DECLARE v_catalog_count INT DEFAULT 0;
    DECLARE v_core_count INT DEFAULT 0;

    SELECT COUNT(*) INTO v_catalog_count
    FROM `oauth2_registered_client`;
    IF v_catalog_count <> 3 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Phase 4 core_agent activation requires the exact three-client catalogue';
    END IF;

    SELECT COUNT(*) INTO v_core_count
    FROM `oauth2_registered_client`
    WHERE BINARY `id` = 'e7cf5b30-8e43-4db2-bc53-000000000003'
      AND BINARY `client_id` = 'core_agent'
      AND `client_secret` IS NULL
      AND `client_secret_expires_at` IS NULL
      AND BINARY `client_authentication_methods` = 'none'
      AND BINARY `authorization_grant_types` = 'authorization_code,refresh_token'
      AND BINARY `redirect_uris` = 'http://127.0.0.1:9090/oauth/callback'
      AND `post_logout_redirect_uris` IS NULL
      AND BINARY `scopes` = 'note:read,note:write,sys:read,media:read'
      AND BINARY `client_settings` = '{"@class":"java.util.Collections$UnmodifiableMap","settings.client.require-proof-key":true,"settings.client.require-authorization-consent":true}'
      AND BINARY `token_settings` = '{"@class":"java.util.Collections$UnmodifiableMap","settings.token.reuse-refresh-tokens":false,"settings.token.x509-certificate-bound-access-tokens":false,"settings.token.id-token-signature-algorithm":["org.springframework.security.oauth2.jose.jws.SignatureAlgorithm","RS256"],"settings.token.access-token-time-to-live":["java.time.Duration","PT1H"],"settings.token.access-token-format":{"@class":"org.springframework.security.oauth2.server.authorization.settings.OAuth2TokenFormat","value":"self-contained"},"settings.token.refresh-token-time-to-live":["java.time.Duration","PT24H"],"settings.token.authorization-code-time-to-live":["java.time.Duration","PT10M"],"settings.token.device-code-time-to-live":["java.time.Duration","PT5M"]}'
      AND BINARY `auto_approve` = 'note:read,sys:read'
      AND BINARY `status` = 'disabled'
      AND BINARY `allowed_ips` = '0.0.0.0/0';
    IF v_core_count <> 1 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Phase 4 core_agent activation found an unexpected public-client state';
    END IF;
END$$
DELIMITER ;

CALL `phase4_core_agent_activation_preflight`();
DROP PROCEDURE `phase4_core_agent_activation_preflight`;

UPDATE `oauth2_registered_client`
SET `status` = 'active'
WHERE BINARY `client_id` = 'core_agent'
  AND BINARY `status` = 'disabled';

DELIMITER $$
DROP PROCEDURE IF EXISTS `phase4_core_agent_activation_postflight`$$
CREATE PROCEDURE `phase4_core_agent_activation_postflight`()
BEGIN
    DECLARE v_active_core_count INT DEFAULT 0;

    SELECT COUNT(*) INTO v_active_core_count
    FROM `oauth2_registered_client`
    WHERE BINARY `id` = 'e7cf5b30-8e43-4db2-bc53-000000000003'
      AND BINARY `client_id` = 'core_agent'
      AND `client_secret` IS NULL
      AND BINARY `client_authentication_methods` = 'none'
      AND BINARY `authorization_grant_types` = 'authorization_code,refresh_token'
      AND BINARY `redirect_uris` = 'http://127.0.0.1:9090/oauth/callback'
      AND BINARY `status` = 'active';
    IF v_active_core_count <> 1 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Phase 4 core_agent activation postflight validation failed';
    END IF;
END$$
DELIMITER ;

CALL `phase4_core_agent_activation_postflight`();
DROP PROCEDURE `phase4_core_agent_activation_postflight`;
