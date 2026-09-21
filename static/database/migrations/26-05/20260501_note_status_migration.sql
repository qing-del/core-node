-- =====================================================
-- 笔记状态字段迁移脚本
-- 版本: 1.0
-- 日期: 2026-05-01
-- =====================================================

-- 阶段1: 添加新字段和索引
ALTER TABLE `biz_note`
ADD COLUMN `status` tinyint NOT NULL DEFAULT 0
COMMENT '笔记状态(0:已创建,1:缺失信息,2:待转换,3:已转换,4:审核中,5:已通过,6:已公开,7:已拒绝,8:已删除)'
AFTER `is_missing_info`,
ADD COLUMN `missing_info_mask` tinyint NOT NULL DEFAULT 0
COMMENT '缺失信息掩码(1:标签,2:图片,4:内联笔记)'
AFTER `status`,
ADD COLUMN `missing_count` tinyint NOT NULL DEFAULT 0
COMMENT '缺失信息数量,为0时自动扫描并触发状态转换'
AFTER `missing_info_mask`;

CREATE INDEX `idx_status` ON `biz_note` (`status`);
CREATE INDEX `idx_user_status` ON `biz_note` (`user_id`, `status`);

-- 阶段2: 数据迁移
UPDATE `biz_note` SET `status` =
    CASE
        WHEN `is_deleted` = 1 THEN 8
        WHEN `is_pass` = 2 THEN 7
        WHEN `is_published` = 1 AND `is_pass` = 1 THEN 6
        WHEN `is_pass` = 1 THEN 5
        WHEN `is_missing_info` = 1 THEN 1
        ELSE 0
    END;

-- 验证数据迁移结果
SELECT
    `is_pass`,
    `is_published`,
    `is_deleted`,
    `status`,
    COUNT(*) as count
FROM `biz_note`
GROUP BY `is_pass`, `is_published`, `is_deleted`, `status`;

-- =====================================================
-- 阶段3: 删除旧字段（验证后执行）
-- =====================================================
ALTER TABLE `biz_note`
DROP COLUMN `is_pass`,
DROP COLUMN `is_published`,
DROP COLUMN `is_deleted`,
DROP COLUMN `is_missing_info`;

-- 阶段4: biz_note_change_diff 添加 scan_json 列
ALTER TABLE `biz_note_change_diff`
ADD COLUMN `scan_json` longtext DEFAULT NULL COMMENT '新文本扫描结果JSON(tags/imageNames/noteLinks)' AFTER `diff_json`;
