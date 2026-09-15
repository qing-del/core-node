alter table `biz_note`
add column `is_changing` tinyint
not null default 0
comment '笔记正在转换中(0:否,1:是)';

update `biz_note` as n
set `is_changing` = if (exists(
select 1 from `biz_note_change_diff` as c where c.`note_id` = n.`id`), 1, 0);

alter table `biz_note_change_diff`
add column `scan_json` longtext
DEFAULT NULL
COMMENT '新文本扫描结果JSON(tags/imageNames/noteLinks)'