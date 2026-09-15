USE `personal_saas`;

-- Forward-only schema for the v0.3 collaborative document persistence pipeline.
CREATE TABLE `biz_document` (
    `id`                  bigint       NOT NULL AUTO_INCREMENT COMMENT '文档ID',
    `team_id`             bigint       NOT NULL COMMENT 'v0.3个人域ID，固定为owner用户ID',
    `title`               varchar(255) NOT NULL COMMENT '文档标题',
    `content_object_key`  varchar(512) DEFAULT NULL COMMENT '当前MinIO Yjs snapshot对象键',
    `persisted_log_id`    bigint       NOT NULL DEFAULT 0 COMMENT '当前snapshot已包含的最大document_op_log.id',
    `last_modify_time`    datetime(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '最近一次接受文档更新的时间',
    `last_modify_user_id` bigint       DEFAULT NULL COMMENT '最近修改用户ID',
    `deleted`             tinyint      NOT NULL DEFAULT 0 COMMENT '逻辑删除(0:正常,1:删除)',
    `version`             bigint       NOT NULL DEFAULT 0 COMMENT '文档Meta/Snapshot版本',
    `create_time`         datetime(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    `update_time`         datetime(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_document_scope_deleted_time` (`team_id`, `deleted`, `last_modify_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='协作文档元数据与当前Snapshot指针';

CREATE TABLE `document_op_log` (
    `id`               bigint       NOT NULL AUTO_INCREMENT COMMENT '持久化操作日志ID',
    `document_id`      bigint       NOT NULL COMMENT '关联biz_document.id',
    `redis_op_id`      varchar(64)  NOT NULL COMMENT 'Redis Stream entry ID',
    `client_update_id` char(36)     NOT NULL COMMENT '客户端Yjs update UUID',
    `update_data`      longblob     NOT NULL COMMENT 'Yjs binary update',
    `operator_id`      bigint       DEFAULT NULL COMMENT '操作用户ID',
    `operator_type`    varchar(16)  NOT NULL COMMENT '操作主体类型',
    `create_time`      datetime(3)  NOT NULL COMMENT 'Redis接受update的服务端时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_document_redis_op` (`document_id`, `redis_op_id`),
    UNIQUE KEY `uk_document_client_update` (`document_id`, `client_update_id`),
    KEY `idx_document_log` (`document_id`, `id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='协作文档已可靠转存的Yjs增量日志';
