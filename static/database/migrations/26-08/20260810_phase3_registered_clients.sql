-- Phase 3 registered-client catalogue. Forward-only MySQL 8 migration.
-- Take a verified backup before execution. The preflight intentionally accepts only
-- the complete Phase 1 catalogue so a partially edited deployment is never patched
-- into an unknown authentication state.

DELIMITER $$
DROP PROCEDURE IF EXISTS `phase3_registered_clients_preflight`$$
CREATE PROCEDURE `phase3_registered_clients_preflight`()
BEGIN
    DECLARE v_table_count INT DEFAULT 0;
    DECLARE v_required_column_count INT DEFAULT 0;
    DECLARE v_catalog_count INT DEFAULT 0;
    DECLARE v_expected_client_count INT DEFAULT 0;
    DECLARE v_expected_id_count INT DEFAULT 0;
    DECLARE v_internal_legacy_count INT DEFAULT 0;
    DECLARE v_core_legacy_count INT DEFAULT 0;
    DECLARE v_reserved_legacy_count INT DEFAULT 0;

    SELECT COUNT(*) INTO v_table_count
    FROM information_schema.tables
    WHERE table_schema = DATABASE()
      AND table_name = 'oauth2_registered_client';
    IF v_table_count <> 1 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Phase 3 registered-client migration requires oauth2_registered_client exactly once';
    END IF;

    SELECT COUNT(*) INTO v_required_column_count
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'oauth2_registered_client'
      AND column_name IN (
          'id', 'client_id', 'client_secret', 'client_authentication_methods',
          'authorization_grant_types', 'redirect_uris', 'token_settings', 'status'
      );
    IF v_required_column_count <> 8 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Phase 3 registered-client migration found an incompatible OAuth client schema';
    END IF;

    SELECT COUNT(*) INTO v_catalog_count
    FROM `oauth2_registered_client`;
    SELECT COUNT(*) INTO v_expected_client_count
    FROM `oauth2_registered_client`
    WHERE BINARY `client_id` IN ('user', 'admin', 'core_agent', 'authorization_client');
    SELECT COUNT(*) INTO v_expected_id_count
    FROM `oauth2_registered_client`
    WHERE (BINARY `client_id` = 'user' AND BINARY `id` = 'e7cf5b30-8e43-4db2-bc53-000000000001')
       OR (BINARY `client_id` = 'admin' AND BINARY `id` = 'e7cf5b30-8e43-4db2-bc53-000000000002')
       OR (BINARY `client_id` = 'core_agent' AND BINARY `id` = 'e7cf5b30-8e43-4db2-bc53-000000000003')
       OR (BINARY `client_id` = 'authorization_client' AND BINARY `id` = 'e7cf5b30-8e43-4db2-bc53-000000000004');
    IF v_catalog_count <> 4 OR v_expected_client_count <> 4 OR v_expected_id_count <> 4 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Phase 3 registered-client migration requires the exact four-client legacy catalogue';
    END IF;

    SELECT COUNT(*) INTO v_internal_legacy_count
    FROM `oauth2_registered_client`
    WHERE `client_secret` IS NULL
      AND BINARY `status` = 'disabled'
      AND (
          (BINARY `client_id` = 'user'
           AND BINARY `client_authentication_methods` = 'client_secret_post'
           AND BINARY `authorization_grant_types` = 'password,user_password,refresh_token')
          OR
          (BINARY `client_id` = 'admin'
           AND BINARY `client_authentication_methods` = 'client_secret_post'
           AND BINARY `authorization_grant_types` = 'admin_password,refresh_token')
      );
    IF v_internal_legacy_count <> 2 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Phase 3 registered-client migration found an unexpected user/admin legacy state';
    END IF;

    SELECT COUNT(*) INTO v_core_legacy_count
    FROM `oauth2_registered_client`
    WHERE BINARY `client_id` = 'core_agent'
      AND `client_secret` IS NULL
      AND BINARY `client_authentication_methods` = 'none'
      AND BINARY `authorization_grant_types` = 'agent_client,authorization_code'
      AND BINARY `redirect_uris` = 'http://127.0.0.1:9090/oauth/callback'
      AND BINARY `status` = 'disabled';
    IF v_core_legacy_count <> 1 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Phase 3 registered-client migration found an unexpected core_agent legacy state';
    END IF;

    SELECT COUNT(*) INTO v_reserved_legacy_count
    FROM `oauth2_registered_client`
    WHERE BINARY `client_id` = 'authorization_client'
      AND `client_secret` IS NULL
      AND BINARY `client_authentication_methods` = 'client_secret_post'
      AND BINARY `authorization_grant_types` = 'authorization_code'
      AND BINARY `status` = 'disabled';
    IF v_reserved_legacy_count <> 1 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Phase 3 registered-client migration found an unexpected authorization_client legacy state';
    END IF;
END$$
DELIMITER ;

CALL `phase3_registered_clients_preflight`();
DROP PROCEDURE `phase3_registered_clients_preflight`;

-- Internal clients are trusted only by the server-side /auth/login boundary; they
-- intentionally have no shared secret and are not OAuth public clients.
UPDATE `oauth2_registered_client`
SET `client_secret` = NULL,
    `client_authentication_methods` = 'internal',
    `authorization_grant_types` = 'password,email-code,refresh_token',
    `status` = 'active'
WHERE BINARY `client_id` IN ('user', 'admin');

-- core_agent remains public and disabled until Phase 4. Refresh-token rotation is
-- enabled here while its registered redirect and other client metadata stay intact.
UPDATE `oauth2_registered_client`
SET `client_secret` = NULL,
    `client_authentication_methods` = 'none',
    `authorization_grant_types` = 'authorization_code,refresh_token',
    `token_settings` = '{"@class":"java.util.Collections$UnmodifiableMap","settings.token.reuse-refresh-tokens":false,"settings.token.x509-certificate-bound-access-tokens":false,"settings.token.id-token-signature-algorithm":["org.springframework.security.oauth2.jose.jws.SignatureAlgorithm","RS256"],"settings.token.access-token-time-to-live":["java.time.Duration","PT1H"],"settings.token.access-token-format":{"@class":"org.springframework.security.oauth2.server.authorization.settings.OAuth2TokenFormat","value":"self-contained"},"settings.token.refresh-token-time-to-live":["java.time.Duration","PT24H"],"settings.token.authorization-code-time-to-live":["java.time.Duration","PT10M"],"settings.token.device-code-time-to-live":["java.time.Duration","PT5M"]}'
WHERE BINARY `client_id` = 'core_agent';

DELETE FROM `oauth2_registered_client`
WHERE BINARY `client_id` = 'authorization_client';

DELIMITER $$
DROP PROCEDURE IF EXISTS `phase3_registered_clients_postflight`$$
CREATE PROCEDURE `phase3_registered_clients_postflight`()
BEGIN
    DECLARE v_catalog_count INT DEFAULT 0;
    DECLARE v_internal_count INT DEFAULT 0;
    DECLARE v_core_count INT DEFAULT 0;

    SELECT COUNT(*) INTO v_catalog_count
    FROM `oauth2_registered_client`;
    SELECT COUNT(*) INTO v_internal_count
    FROM `oauth2_registered_client`
    WHERE BINARY `client_id` IN ('user', 'admin')
      AND `client_secret` IS NULL
      AND BINARY `client_authentication_methods` = 'internal'
      AND BINARY `authorization_grant_types` = 'password,email-code,refresh_token'
      AND BINARY `status` = 'active';
    SELECT COUNT(*) INTO v_core_count
    FROM `oauth2_registered_client`
    WHERE BINARY `client_id` = 'core_agent'
      AND `client_secret` IS NULL
      AND BINARY `client_authentication_methods` = 'none'
      AND BINARY `authorization_grant_types` = 'authorization_code,refresh_token'
      AND BINARY `redirect_uris` = 'http://127.0.0.1:9090/oauth/callback'
      AND BINARY `status` = 'disabled'
      AND BINARY `token_settings` = '{"@class":"java.util.Collections$UnmodifiableMap","settings.token.reuse-refresh-tokens":false,"settings.token.x509-certificate-bound-access-tokens":false,"settings.token.id-token-signature-algorithm":["org.springframework.security.oauth2.jose.jws.SignatureAlgorithm","RS256"],"settings.token.access-token-time-to-live":["java.time.Duration","PT1H"],"settings.token.access-token-format":{"@class":"org.springframework.security.oauth2.server.authorization.settings.OAuth2TokenFormat","value":"self-contained"},"settings.token.refresh-token-time-to-live":["java.time.Duration","PT24H"],"settings.token.authorization-code-time-to-live":["java.time.Duration","PT10M"],"settings.token.device-code-time-to-live":["java.time.Duration","PT5M"]}';
    IF v_catalog_count <> 3 OR v_internal_count <> 2 OR v_core_count <> 1 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Phase 3 registered-client migration postflight validation failed';
    END IF;
END$$
DELIMITER ;

CALL `phase3_registered_clients_postflight`();
DROP PROCEDURE `phase3_registered_clients_postflight`;
