USE `personal_saas`;

-- 管理端协作文档目录使用 document:read，历史快照清理使用 document:manage。
UPDATE `oauth2_registered_client`
SET `scopes` = 'account:read,account:manage,audio:read,audio:manage,audit:read,audit:manage,document:read,document:manage,media:read,media:manage,note:read,note:manage',
    `auto_approve` = 'account:read,account:manage,audio:read,audio:manage,audit:read,audit:manage,document:read,document:manage,media:read,media:manage,note:read,note:manage'
WHERE BINARY `client_id` = 'admin';
