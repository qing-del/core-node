USE `personal_saas`;

# -- ==========================================
# -- 1. 角色表 (sys_role)
# -- 脚本中已包含初始化，这里补充一个过期/特殊的角色作为测试 (可选)
# -- ==========================================
# INSERT INTO `sys_role` (`role_name`, `role_code`, `daily_api_limit`) VALUES
#     ('访客', 'GUEST', 1);


-- ==========================================
-- 2. 用户表 (sys_user)
-- 密码统一使用 BCrypt 加密的 "123456": $2a$10$8K1p/a0dxE.Y8L.oUIn8Lu7Yl18f5pI1O3vS7l6f9Y.f9Uo7i8I6K
-- ==========================================
INSERT INTO `sys_user` (`id`, `username`, `password`, `nickname`, `email`, `role_id`, `status`) VALUES
                                                                                                    (1, 'creator_admin', '$2a$10$8K1p/a0dxE.Y8L.oUIn8Lu7Yl18f5pI1O3vS7l6f9Y.f9Uo7i8I6K', '系统创建者', 'creator@example.com', 1, 1),
                                                                                                    (2, 'zhangsan', '$2a$10$8K1p/a0dxE.Y8L.oUIn8Lu7Yl18f5pI1O3vS7l6f9Y.f9Uo7i8I6K', '张三', 'zhangsan@test.com', 3, 1),
                                                                                                    (3, 'lisi_vip', '$2a$10$8K1p/a0dxE.Y8L.oUIn8Lu7Yl18f5pI1O3vS7l6f9Y.f9Uo7i8I6K', '李四VIP', 'lisi@test.com', 4, 1),
                                                                                                    (4, 'disabled_user', '$2a$10$8K1p/a0dxE.Y8L.oUIn8Lu7Yl18f5pI1O3vS7l6f9Y.f9Uo7i8I6K', '被禁用用户', 'badboy@test.com', 3, 0),
                                                                                                    (5, 'new_user_wait_active', '$2a$10$8K1p/a0dxE.Y8L.oUIn8Lu7Yl18f5pI1O3vS7l6f9Y.f9Uo7i8I6K', '未激活用户', 'wait_active@test.com', 3, 2);


-- ==========================================
-- 3. 主题/分类表 (biz_topic)
-- 为不同用户创建不同的分类
-- ==========================================
INSERT INTO `biz_topic` (`id`, `user_id`, `topic_name`, `sort_order`) VALUES
                                                                          (1, 1, 'MySQL 深度解析', 1),
                                                                          (2, 1, 'Spring Boot 实战', 2),
                                                                          (3, 1, 'Redis 基础', 3),
                                                                          (4, 1, 'Docker 容器化', 4),
                                                                          (5, 1, 'Vue3 前端开发', 5),
                                                                          (6, 1, 'Linux 运维', 6),
                                                                          (7, 1, '算法与数据结构', 7),
                                                                          (8, 2, '我的日常随笔', 1),
                                                                          (9, 2, 'LeetCode 刷题', 2),
                                                                          (10, 3, '架构设计', 1);


-- ==========================================
-- 4. 标签表 (biz_tag)
-- 为不同用户创建更多标签，用于测试标签接口的增删改查
-- ==========================================
INSERT INTO `biz_tag` (`id`, `user_id`, `tag_name`) VALUES
                                                        (1, 1, '性能优化'),
                                                        (2, 1, '源码阅读'),
                                                        (3, 1, '面试必考'),
                                                        (4, 1, '实战案例'),
                                                        (5, 1, '底层原理'),
                                                        (6, 1, '最佳实践'),
                                                        (7, 1, '避坑指南'),
                                                        (8, 1, '架构模式'),
                                                        (9, 1, '中间件'),
                                                        (10, 1, '工具使用'),
                                                        (11, 2, '踩坑记录'),
                                                        (12, 2, '算法'),
                                                        (13, 2, '学习笔记'),
                                                        (14, 2, '日常总结'),
                                                        (15, 2, '代码片段'),
                                                        (16, 3, '高并发'),
                                                        (17, 3, '分布式'),
                                                        (18, 3, '系统设计'),
                                                        (19, 3, '性能调优'),
                                                        (20, 3, 'DevOps');


-- ==========================================
-- 5. 笔记/文章主表 (biz_note)
-- ==========================================
INSERT INTO `biz_note` (`id`, `user_id`, `topic_id`, `title`, `description`, `is_published`, `storage_type`, `is_missing_info`, `is_pass`, `is_deleted`, `md_file_size`) VALUES
(1, 1, 1, 'MySQL 索引失效的 10 种场景', '详细分析各种失效场景及底层逻辑', 1, 0, 0, 1, 0, 10240),
(2, 1, 1, 'InnoDB 锁机制详述', NULL, 1, 0, 1, 1, 0, 5000),
(3, 2, 8, '2024 年第一篇随笔', '', 0, 0, 0, 0, 1, 300),
(4, 3, 10, '分布式事务一致性方案', '深入讨论两阶段提交、TCC等', 1, 1, 0, 1, 0, 8900),
(5, 1, 3, 'Redis 持久化机制详解', 'RDB 与 AOF 大比拼', 1, 0, 0, 1, 0, 12000),
(6, 1, 2, 'Spring Boot 3.x.x 新特性', '升级指南与特性一览', 1, 0, 0, 1, 0, 4500),
(7, 1, 7, '动态规划解题模板', '附带LeetCode高频题解', 1, 0, 0, 1, 0, 3200),
(8, 1, 10, '微服务架构设计模式', '包含 API网关、服务发现等模式', 1, 0, 0, 1, 0, 15000),
(9, 1, 4, 'Docker 容器网络配置', 'bridge, host, none模式解析', 1, 0, 0, 1, 0, 4100),
(10, 1, 6, 'Linux 性能监控工具', 'top, vmstat, iostat实战', 1, 0, 0, 1, 0, 6000),
(11, 1, 1, 'MySQL 慢查询优化', '开启与分析慢查询日志', 1, 0, 0, 1, 0, 8000),
(12, 1, 2, 'Spring Security 实战', '从零搭建认证授权中心', 1, 0, 0, 1, 0, 9500),
(13, 1, 5, 'Vue3 Composition API', 'setup 函数的最佳实践', 1, 0, 0, 1, 0, 2000),
-- 边界用例与异常数据
(14, 2, NULL, '未分类的孤独笔记', '没有归属主题的笔记', 0, 0, 0, 0, 0, 100),
(15, 3, 10, '非常长的标题测试非常长的标题测试非常长的标题测试非常长的标题测试非常长的标题测试标题长度极限测试', '测试超长标题情况', 1, 2, 0, 2, 0, 123456789),
(16, 1, 1, '特殊字符标题!@#$%^&*()_+{}|:<>?-=[]\\;,./', '测试各种特殊字符和emoji 🚀', 0, 0, 1, 0, 0, 50),
(17, 1, 3, '已删除的Redis笔记', '应该在查询通常时过滤掉', 1, 0, 0, 1, 1, 800);


-- ==========================================
-- 5.1 笔记转换缓存表 (biz_note_converted)
-- ==========================================
INSERT INTO `biz_note_converted` (`note_id`, `title`, `tags_json`, `create_time_str`, `toc_html`, `body_html`) VALUES
(1, 'MySQL 索引失效的 10 种场景', '["性能优化", "面试必考"]', '2024-05-10', '<ul><li><a href="#id1">失效场景一</a></li></ul>', '<h1 id="id1">失效场景一：不满足最左匹配</h1><p>详细内容...</p>'),
(2, 'InnoDB 锁机制详述', '["底层原理"]', '2024-05-11', NULL, '<h1>行锁与表锁</h1><p>这是部分内容...</p>'),
(4, '分布式事务一致性方案', '["高并发", "分布式"]', '2024-05-12', '<ul><li><a href="#1">2PC</a></li></ul>', '<h1 id="1">2PC两阶段提交</h1><p>...</p>'),
(17, '已删除的Redis笔记', '[]', '2024-01-01', NULL, '<p>此笔记已被删除的缓存内容</p>');


-- ==========================================
-- 5.2 笔记覆盖上传 Diff 记录表 (biz_note_change_diff)
-- ==========================================
INSERT INTO `biz_note_change_diff` (`note_id`, `status`, `diff_json`, `old_file_size`, `new_file_size`) VALUES
(1, 0, '{"additions": 10, "deletions": 2}', 10000, 10240),
(5, 1, '{"additions": 5, "deletions": 5}', 12000, 12000),
(6, 2, '{"additions": 0, "deletions": 40}', 5000, 4500);


-- ==========================================
-- 5.3 笔记内容表 (biz_note_context)
-- ==========================================
INSERT INTO `biz_note_context` (`note_id`, `markdown_content`, `markdown_content_new`) VALUES
(1, '# 失效场景一：不满足最左匹配\n不要用 `SELECT *`', '# 失效场景一：不满足最左匹配\n不要用 `SELECT *`并且注意联合索引顺序。'),
(2, '# 行锁与表锁\nInnoDB默认是行锁。', NULL),
(4, '# 2PC两阶段提交\n引入协调者角色。', NULL),
(14, '# 孤独笔记\n没有任何主题', NULL),
(16, '# 特殊字符\n!@#$%', NULL);


-- ==========================================
-- 5.4 笔记双链映射表 (biz_note_each_mapping)
-- ==========================================
INSERT INTO `biz_note_each_mapping` (`source_note_id`, `target_note_id`, `parsed_note_name`, `anchor`, `nickname`, `is_pass`, `is_deleted`) VALUES
(1, 2, 'InnoDB 锁机制详述', '行锁', '锁的知识点', 1, 0),
(1, 11, 'MySQL 慢查询优化', NULL, NULL, 1, 0),
(4, NULL, '未创建的微服务治理笔记', '限流降级', '微服务限流', 0, 0),
(5, 17, '已删除的Redis笔记', NULL, '软删引用', 1, 1),
(8, 4, '分布式事务一致性方案', NULL, '事务方案复用', 1, 0),
(12, NULL, '空白笔记引用', '', '', 0, 0),
(13, NULL, '引用带着极其反常长长长长长长名称的笔记测试', NULL, '极长别名极长别名极长别名极长别名极长别名极长别名', 0, 0);


-- ==========================================
-- 6. 笔记-标签 多对多关联 (biz_note_tag_mapping)
-- ==========================================
INSERT INTO `biz_note_tag_mapping` (`note_id`, `tag_id`, `parsed_tag_name`, `is_pass`, `is_deleted`) VALUES
(1, 1, '性能优化', 1, 0),
(1, 2, '源码阅读', 1, 0),
(1, 3, '面试必考', 1, 0),
(2, 1, '性能优化', 1, 0),
(2, 5, '底层原理', 1, 0),
(2, 3, '面试必考', 1, 0),
(3, 11, '踩坑记录', 0, 1),
(3, 14, '日常总结', 0, 1),
(4, 16, '高并发', 1, 0),
(4, 17, '分布式', 1, 0),
(4, 18, '系统设计', 1, 0),
(5, 1, '性能优化', 1, 0),
(5, 2, '源码阅读', 1, 0),
(5, 9, '中间件', 1, 0),
(6, 3, '面试必考', 1, 0),
(6, 7, '避坑指南', 1, 0),
(7, 12, '算法', 1, 0),
(7, 3, '面试必考', 1, 0),
(8, 8, '架构模式', 1, 0),
(8, 17, '分布式', 1, 0),
(8, 18, '系统设计', 1, 0),
(9, 6, '最佳实践', 1, 0),
(9, 10, '工具使用', 1, 0),
(10, 1, '性能优化', 1, 0),
(10, 10, '工具使用', 1, 0),
(11, 1, '性能优化', 1, 0),
(11, 7, '避坑指南', 1, 0),
(12, 3, '面试必考', 1, 0),
(12, 4, '实战案例', 1, 0),
(13, 6, '最佳实践', 1, 0),
(13, 10, '工具使用', 1, 0),
-- 测试孤儿标签（即文本解析出来但并没有创建在biz_tag中，tag_id为空）
(16, NULL, '未收录的自定义神仙标签', 0, 0),
(17, 1, '性能优化', 1, 1); -- 笔记已被删除的标签


-- ==========================================
-- 7. 图片资源映射表 (biz_image)
-- ==========================================
INSERT INTO `biz_image` (`id`, `user_id`, `topic_id`, `filename`, `oss_url`, `storage_type`, `file_size`, `is_public`, `is_pass`) VALUES
(1, 1, 1, 'mysql_b_tree.png', 'https://oss.example.com/u1/mysql_b_tree.png', 1, 102455, 1, 1),
(2, 1, 1, 'explain_result.jpg', 'https://oss.example.com/u1/explain_result.jpg', 1, 56789, 0, 1),
(3, 3, 10, 'raft_protocol.png', 'https://oss.example.com/u3/raft_protocol.png', 1, 204800, 1, 1),
(4, 2, 8, 'daily_note_cover.png', 'https://oss.example.com/u2/daily_note_cover.png', 1, 40960, 0, 0),
(5, 3, 10, 'eventual_consistency.png', 'https://r2.example.com/u3/eventual_consistency.png', 2, 99888, 1, 0),
(6, 1, NULL, '未分类孤儿图___#$!~.png', 'https://oss.example.com/u1/weird_name.png', 1, 10, 1, 1);


-- ==========================================
-- 8. 每日 API 调用统计表 (biz_api_daily_usage)
-- ==========================================
INSERT INTO `biz_api_daily_usage` (`user_id`, `record_date`, `used_count`) VALUES
(1, CURDATE(), 50),
(2, CURDATE(), 5),
(3, CURDATE(), 10),
(2, DATE_SUB(CURDATE(), INTERVAL 1 DAY), 3),
(3, DATE_SUB(CURDATE(), INTERVAL 1 DAY), 35),
(1, DATE_SUB(CURDATE(), INTERVAL 365 DAY), 9999); -- 一年前的边界数据


-- ==========================================
-- 9. API 异步任务明细表 (biz_api_task_log)
-- ==========================================
INSERT INTO `biz_api_task_log` (`user_id`, `task_type`, `task_status`, `result_url`, `error_msg`, `finish_time`) VALUES
(1, 'analyze_text', 1, 'https://oss.example.com/res/report_001.pdf', NULL, NOW()),
(2, 'generate_audio', 2, NULL, 'Python Script Timeout: Connection refused', NOW()),
(3, 'analyze_text', 0, NULL, NULL, NULL),
(1, 'unknown_task_edge_case', 2, NULL, 'Max retries exceeded with Exception: NullPointer', NOW());


-- ==========================================
-- 10. 笔记-图片引用映射表 (biz_note_image_mapping)
-- ==========================================
INSERT INTO `biz_note_image_mapping`
(`note_id`, `image_id`, `note_user_id`, `image_user_id`, `parsed_image_name`, `note_title`, `is_cross_user`, `is_pass`, `is_deleted`) VALUES
(1, 1, 1, 1, 'mysql_b_tree.png', 'MySQL 索引失效的 10 种场景', 0, 1, 0),
(1, 2, 1, 1, 'explain_result.jpg', 'MySQL 索引失效的 10 种场景', 0, 1, 0),
(4, 3, 3, 3, 'raft_protocol.png', '分布式事务一致性方案', 0, 1, 0),
(8, 3, 1, 3, 'raft_protocol.png', '微服务架构设计模式', 1, 1, 0),
(3, 4, 2, 2, 'daily_note_cover.png', '2024 年第一篇随笔', 0, 0, 1),
-- 图片尚未绑定的情况(解析出了图片名字，但是未找到相应的资源)
(2, NULL, 1, NULL, 'not_exist_image.gif', 'InnoDB 锁机制详述', 0, 0, 0);


-- ==========================================
-- 11. 元数据审核表 (biz_meta_audit_record)
-- ==========================================
INSERT INTO `biz_meta_audit_record`
(`applicant_user_id`, `apply_type`, `target_id`, `apply_reason`, `status`, `reviewer_user_id`, `reject_reason`, `review_time`) VALUES
(2, 1, 8, '申请将主题公开给团队共享', 1, 1, NULL, NOW()),
(2, 2, 11, '该标签用于项目复盘', 2, 1, '标签描述过于模糊，请补充语义', NOW()),
(3, 1, 10, '申请加急审核架构主题', 0, NULL, NULL, NULL),
(1, 2, 9999, '测试不存在的审核目标ID', 0, NULL, NULL, NULL);


-- ==========================================
-- 12. 图片审核表 (biz_image_audit_record)
-- ==========================================
INSERT INTO `biz_image_audit_record`
(`applicant_user_id`, `image_id`, `apply_reason`, `status`, `reviewer_user_id`, `reject_reason`, `review_time`) VALUES
(2, 4, '封面图用于公开笔记，申请通过审核', 0, NULL, NULL, NULL),
(3, 5, '该图为原创架构图，申请公开引用', 2, 1, '当前图片清晰度不足，请重新上传高清版本', NOW()),
(1, 1, '用于公共知识库示例图', 1, 1, NULL, NOW());


-- ==========================================
-- 13. 笔记审核表 (biz_note_audit_record)
-- ==========================================
INSERT INTO `biz_note_audit_record`
(`applicant_user_id`, `note_id`, `apply_reason`, `status`, `reviewer_user_id`, `reject_reason`, `review_time`) VALUES
(2, 3, '内容已补充完整，申请发布', 0, NULL, NULL, NULL),
(1, 2, '技术笔记申请公开', 1, 1, NULL, NOW()),
(3, 4, '申请首页推荐', 2, 1, '内容缺少可复现步骤，请补充后重提', NOW());


-- ==========================================
-- 14. 图片删除死信队列表 (biz_image_delete_dead_letter)
-- ==========================================
INSERT INTO `biz_image_delete_dead_letter` (`image_url`, `status`, `retry_count`) VALUES
('https://oss.example.com/u2/expired_audit_001.png', 0, 0),
('https://oss.example.com/u1/temp/deprecated_cover.png', 0, 2),
('https://r2.example.com/u3/legacy/old_architecture.png', 1, 1),
('https://invalid-url.com/what/ever/test', 0, 999); -- 重试次数爆炸的死信边界