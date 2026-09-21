USE `personal_saas`;

-- The agent chat route has an explicit action because *:read and *:write do not cover agent:chat.
INSERT INTO `sys_permission` (`code`, `oauth_scope`, `resource`, `action`, `status`, `description`)
VALUES ('agent:chat', NULL, 'agent', 'chat', 'active', 'Use basic AI chat');

INSERT INTO `sys_role_perm` (`role_id`, `perm_id`)
SELECT r.`id`, p.`id`
FROM `sys_role` r
JOIN `sys_permission` p ON p.`code` = 'agent:chat'
WHERE r.`role_code` = 'USER';

UPDATE `oauth2_registered_client`
SET `scopes` = 'account:read,account:write,agent:chat,audio:read,audio:write,audit:read,audit:write,document:read,document:write,media:read,media:write,note:read,note:write',
    `auto_approve` = 'account:read,account:write,agent:chat,audio:read,audio:write,audit:read,audit:write,document:read,document:write,media:read,media:write,note:read,note:write'
WHERE BINARY `client_id` = 'user';
