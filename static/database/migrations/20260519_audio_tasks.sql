-- ==========================================
-- 音频生成任务表迁移脚本
-- 1. 删除旧的通用任务表 biz_api_task_log
-- 2. 新建专用 audio_tasks 表
-- ==========================================

USE `personal_saas`;

-- 删除旧表
DROP TABLE IF EXISTS `biz_api_task_log`;

-- 新建音频任务表
CREATE TABLE `audio_tasks` (
    `id`             bigint        NOT NULL AUTO_INCREMENT COMMENT '任务唯一主键',
    `user_id`        bigint        NOT NULL COMMENT '提交该任务的用户ID',
    `source_text`    text          NOT NULL COMMENT '需要转换为音频的原始文本',
    `speed`          decimal(3,2)  NOT NULL COMMENT '语速倍率(0.50~3.00)',
    `noise_type`     varchar(32)   NOT NULL COMMENT '背景音枚举(PURE/WHITE_NOISE/PINK_NOISE/BROWN_NOISE/CAFE/AIRPORT/SUBWAY)',
    `noise_factor`   decimal(3,2)  NOT NULL DEFAULT 0.50 COMMENT '背景音量因子(0.00~2.00, 默认0.50)',
    `status`         tinyint       NOT NULL DEFAULT 0 COMMENT '状态: 0-待处理(PENDING), 1-处理中(PROCESSING), 2-成功(SUCCESS), -1-失败(FAILED)',
    `result_url`     varchar(255)  DEFAULT NULL COMMENT '成功后音频下载链接',
    `error_msg`      varchar(500)  DEFAULT NULL COMMENT '失败时的错误信息',
    `create_time`    datetime      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '任务创建时间',
    `update_time`    datetime      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '状态最后更新时间',
    `completed_date` date          DEFAULT NULL COMMENT '任务完成日期(用于定时清理)',
    PRIMARY KEY (`id`),
    KEY `idx_user_id` (`user_id`),
    KEY `idx_status_create` (`status`, `create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='音频生成任务表';
