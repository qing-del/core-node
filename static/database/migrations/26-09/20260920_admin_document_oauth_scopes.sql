USE `personal_saas`;

-- 管理端协作文档目录和历史快照清理沿用既有 document:read/document:write 精确 scope。
UPDATE `oauth2_registered_client`
SET `scopes` = 'account:read,account:manage,audio:read,audio:manage,audit:read,audit:manage,document:read,document:write,media:read,media:manage,note:read,note:manage',
    `auto_approve` = 'account:read,account:manage,audio:read,audio:manage,audit:read,audit:manage,document:read,document:write,media:read,media:manage,note:read,note:manage'
WHERE BINARY `client_id` = 'admin';
