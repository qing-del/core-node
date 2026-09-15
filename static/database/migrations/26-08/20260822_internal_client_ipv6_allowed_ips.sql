-- Extend USER/ADMIN internal-client defaults to both IPv4 and IPv6.
-- Forward-only MySQL 8 migration. Custom allowed_ips values are preserved.

DELIMITER $$
DROP PROCEDURE IF EXISTS `internal_client_ipv6_allowed_ips_preflight`$$
CREATE PROCEDURE `internal_client_ipv6_allowed_ips_preflight`()
BEGIN
    DECLARE v_table_count INT DEFAULT 0;
    DECLARE v_internal_count INT DEFAULT 0;

    SELECT COUNT(*) INTO v_table_count
    FROM information_schema.tables
    WHERE table_schema = DATABASE()
      AND table_name = 'oauth2_registered_client';
    IF v_table_count <> 1 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Internal client IPv6 migration requires oauth2_registered_client exactly once';
    END IF;

    SELECT COUNT(*) INTO v_internal_count
    FROM `oauth2_registered_client`
    WHERE BINARY `client_id` IN ('user', 'admin')
      AND `client_secret` IS NULL
      AND BINARY `client_authentication_methods` = 'internal'
      AND BINARY `authorization_grant_types` = 'password,email-code,refresh_token'
      AND BINARY `status` = 'active'
      AND `allowed_ips` IS NOT NULL
      AND TRIM(`allowed_ips`) <> '';
    IF v_internal_count <> 2 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Internal client IPv6 migration found an unexpected USER/ADMIN state';
    END IF;
END$$
DELIMITER ;

CALL `internal_client_ipv6_allowed_ips_preflight`();
DROP PROCEDURE `internal_client_ipv6_allowed_ips_preflight`;

UPDATE `oauth2_registered_client`
SET `allowed_ips` = '0.0.0.0/0,::/0'
WHERE BINARY `client_id` IN ('user', 'admin')
  AND BINARY `allowed_ips` = '0.0.0.0/0';

DELIMITER $$
DROP PROCEDURE IF EXISTS `internal_client_ipv6_allowed_ips_postflight`$$
CREATE PROCEDURE `internal_client_ipv6_allowed_ips_postflight`()
BEGIN
    DECLARE v_legacy_default_count INT DEFAULT 0;

    SELECT COUNT(*) INTO v_legacy_default_count
    FROM `oauth2_registered_client`
    WHERE BINARY `client_id` IN ('user', 'admin')
      AND BINARY `allowed_ips` = '0.0.0.0/0';
    IF v_legacy_default_count <> 0 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Internal client IPv6 migration left a legacy IPv4-only default';
    END IF;
END$$
DELIMITER ;

CALL `internal_client_ipv6_allowed_ips_postflight`();
DROP PROCEDURE `internal_client_ipv6_allowed_ips_postflight`;
