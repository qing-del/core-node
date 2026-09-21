-- Rename note relation mapping snapshot columns from is_pass to status.
-- Execute after 20260614_non_note_audit_state_machine.sql so tag/image
-- mapping values already follow AuditStatus: 0=待审核,1=审核中,2=已通过,3=已拒绝,4=已删除.

ALTER TABLE `biz_note_each_mapping`
    DROP INDEX `idx_note_deleted_pass`;
ALTER TABLE `biz_note_each_mapping`
    CHANGE COLUMN `is_pass` `status` tinyint NOT NULL DEFAULT 0 COMMENT '目标笔记审核快照(0:待审核,1:已通过,2:已拒绝)';
ALTER TABLE `biz_note_each_mapping`
    ADD INDEX `idx_note_deleted_status` (`source_note_id`, `is_deleted`, `status`);

ALTER TABLE `biz_note_tag_mapping`
    DROP INDEX `idx_note_deleted_pass`;
ALTER TABLE `biz_note_tag_mapping`
    CHANGE COLUMN `is_pass` `status` tinyint NOT NULL DEFAULT 0 COMMENT '标签审核快照(0:待审核,1:审核中,2:已通过,3:已拒绝,4:已删除)';
ALTER TABLE `biz_note_tag_mapping`
    ADD INDEX `idx_note_deleted_status` (`note_id`, `is_deleted`, `status`);

ALTER TABLE `biz_note_image_mapping`
    DROP INDEX `idx_note_deleted_pass`;
ALTER TABLE `biz_note_image_mapping`
    CHANGE COLUMN `is_pass` `status` tinyint NOT NULL DEFAULT 0 COMMENT '图片审核快照(0:待审核,1:审核中,2:已通过,3:已拒绝,4:已删除)';
ALTER TABLE `biz_note_image_mapping`
    ADD INDEX `idx_note_deleted_status` (`note_id`, `is_deleted`, `status`);
