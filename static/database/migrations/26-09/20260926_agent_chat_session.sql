USE `personal_saas`;

-- Agent 聊天消息持久化 v1：目标表已存在时停止，不覆盖已有数据或结构。
CREATE TABLE `biz_agent_chat_session` (
    `id`           bigint      NOT NULL AUTO_INCREMENT COMMENT '聊天会话ID',
    `session_uuid` char(36)    NOT NULL COMMENT '前端传入的会话UUID',
    `user_id`      bigint      NOT NULL COMMENT '所属用户ID，由后端登录态确定',
    `context`      json        NOT NULL COMMENT '后端维护的当前聊天上下文有序记录数组',
    `history`      json        NOT NULL COMMENT '用于恢复历史聊天展示的有序记录数组',
    `create_time`  datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    `update_time`  datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_agent_chat_session_uuid` (`session_uuid`),
    KEY `idx_agent_chat_session_user` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Agent聊天会话与消息持久化';
