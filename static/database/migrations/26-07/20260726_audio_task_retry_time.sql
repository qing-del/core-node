# The modification about the retry mechanism for audio tasks.
ALTER TABLE `audio_tasks`
    ADD COLUMN `retry_time` int NOT NULL DEFAULT 0 COMMENT '超时重试次数' AFTER `status`;

ALTER TABLE `audio_tasks`
    MODIFY COLUMN `status` tinyint NOT NULL DEFAULT 0 COMMENT '状态: 0-待处理(PENDING), 1-处理中(PROCESSING), 2-成功(SUCCESS), -1-失败(FAILED), -2-已重试(RETRIED)';

