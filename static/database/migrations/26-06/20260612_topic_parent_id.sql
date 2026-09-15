ALTER TABLE `biz_topic`
    ADD COLUMN `parent_id` bigint DEFAULT NULL COMMENT '父级主题ID(NULL=一级主题)' AFTER `topic_name`;

CREATE INDEX `idx_user_parent_sort_update`
    ON `biz_topic` (`user_id`, `parent_id`, `sort_order`, `update_time`);
