CREATE DATABASE IF NOT EXISTS `personal_saas` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

USE `personal_saas`;

-- ==========================================
-- 1. 权限/角色表 (sys_role)
-- 满足需求：来一个权限表，以及对应的能调用api的次数上限、存储空间上限
-- ==========================================
CREATE TABLE `sys_role` (
                            `id` bigint NOT NULL AUTO_INCREMENT COMMENT '角色ID',
                            `role_name` varchar(50) NOT NULL COMMENT '角色名称(如：普通用户、VIP、管理员)',
                            `role_code` varchar(50) NOT NULL COMMENT '角色标识(如：USER, VIP, ADMIN)',
                            `daily_api_limit` int NOT NULL DEFAULT 5 COMMENT '该角色每日允许调用外部API的最高次数',
                            `max_storage_bytes` bigint NOT NULL DEFAULT 104857600 COMMENT '该角色默认最大存储空间(字节, 默认100MB=104857600)',
                            `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                            `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                            PRIMARY KEY (`id`),
                            UNIQUE KEY `uk_role_code` (`role_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='系统角色与额度配置表';

-- 初始化角色数据 (max_storage_bytes: CREATOR=100GB, ADMIN=1GB, USER=100MB, VIP=500MB)
INSERT INTO `sys_role` (`role_name`, `role_code`, `daily_api_limit`, `max_storage_bytes`) VALUES
                                                                         ('创建者', 'CREATOR', 999999, 107374182400),
                                                                         ('管理员', 'ADMIN', 1000, 1073741824),
                                                                         ('普通用户', 'USER', 15, 104857600),
                                                                         ('高级VIP', 'VIP', 90, 524288000);


-- ==========================================
-- 2. 用户表 (sys_user)
-- 满足需求：用户要登录，密码采用加密存储，用户有权限等级
-- ==========================================
CREATE TABLE `sys_user` (
                            `id` bigint NOT NULL AUTO_INCREMENT COMMENT '用户ID',
                            `username` varchar(50) UNIQUE NOT NULL COMMENT '登录账号',
                            `password` char(60) NOT NULL COMMENT '加密密码(使用 BCrypt 加密存储)',
                            `nickname` varchar(50) DEFAULT NULL COMMENT '昵称',
                            `email` varchar(100) UNIQUE DEFAULT NULL COMMENT '邮箱地址(唯一)',
                            `role_id` bigint NOT NULL DEFAULT 3 COMMENT '关联的角色ID',
                            `max_storage_bytes` bigint DEFAULT NULL COMMENT '用户个性化最大存储空间(字节, NULL=使用角色默认值, 管理员可单独设置)',
                            `used_storage_bytes` bigint NOT NULL DEFAULT 0 COMMENT '用户当前已用存储空间(字节, 笔记与图片合计)',
                            `status` tinyint NOT NULL DEFAULT 2 COMMENT '状态(2:未激活, 1:正常, 0:禁用)',
                            `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '注册时间',
                            `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                            PRIMARY KEY (`id`),
                            UNIQUE KEY `uk_username` (`username`),
                            UNIQUE KEY `idx_email` (`email`),
                            KEY `idx_nickname` (`nickname`, `status`),
                            -- KEY `idx_role_id` (`role_id`), “废索引”
                            KEY `idx_status_role` (`status`, `role_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户信息表';
-- 服务器启动时会默认创建（若存在则会进行覆盖该用户）一个id=1的创建者用户


-- ==========================================
-- 3. 主题/分类表 (biz_topic)
-- 满足需求：将笔记归类为MySQL主题的笔记，主题可以后续新增 (1对多)
-- ==========================================
CREATE TABLE `biz_topic` (
                             `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主题ID',
                             `user_id` bigint NOT NULL COMMENT '创建者ID(隔离不同用户的主题)',
                             `topic_name` varchar(25) NOT NULL COMMENT '主题名称(如: MySQL, Redis, 架构设计)',
                             `parent_id` bigint DEFAULT NULL COMMENT '父级主题ID(NULL=一级主题)',
                             `sort_order` int DEFAULT 0 COMMENT '排序字段(用于左侧菜单栏排序)',
                             `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                             `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                             PRIMARY KEY (`id`),
                             KEY `idx_user_parent_sort_update` (`user_id`, `parent_id`, `sort_order`, `update_time`),
                             KEY `idx_user_sort_update` (`user_id`, `sort_order`, `update_time`),
                             UNIQUE KEY `uk_topic_user` (`topic_name`, `user_id`) -- 同一个用户不能创建同名的主题
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='笔记主题/分类表';


-- ==========================================
-- 4. 标签表 (biz_tag)
-- 满足需求：类似obsidian中的标签，可被多篇笔记复用 (多对多)
-- ==========================================
CREATE TABLE `biz_tag` (
                           `id` bigint NOT NULL AUTO_INCREMENT COMMENT '标签ID',
                           `user_id` bigint NOT NULL COMMENT '创建者ID',
                           `tag_name` varchar(20) NOT NULL COMMENT '标签名称(如: 踩坑, 性能优化, 源码解析)',
                           `audit_status` tinyint NOT NULL DEFAULT 0 COMMENT '审核状态(0:待审核,1:审核中,2:已通过,3:已拒绝,4:已删除)',
                           `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                           PRIMARY KEY (`id`),
                           KEY `idx_user_audit_status` (`user_id`, `audit_status`),  -- 用于统计
                           UNIQUE KEY `uk_tag_user` (`tag_name`, `user_id`) -- 同一个用户不能创建同名的标签
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='笔记标签表';


-- ==========================================
-- 5. 笔记/文章主表 (biz_note)
-- 满足需求：关联主题ID，存储HTML路径
-- ==========================================
CREATE TABLE `biz_note` (
                            `id` bigint NOT NULL AUTO_INCREMENT COMMENT '笔记ID',
                            `user_id` bigint NOT NULL COMMENT '作者(用户ID)',
                            `topic_id` bigint DEFAULT NULL COMMENT '所属主题ID(允许为空，即未分类笔记)',
                            `title` varchar(100) NOT NULL COMMENT '笔记标题',
                            `description` varchar(255) DEFAULT NULL COMMENT '笔记描述',
                            `storage_type` tinyint NOT NULL COMMENT '存储方式-0:本地存储, 1:阿里云OSS, 2:Cloudflare R2(预留)',
                            `status` tinyint NOT NULL DEFAULT 0 COMMENT '笔记状态(0:已创建,1:缺失信息,2:待转换,3:已转换,4:审核中,5:已通过,6:已公开,7:已拒绝,8:已删除)',
                            `is_changing` tinyint NOT NULL DEFAULT 0 COMMENT '笔记正在转换中(0:否,1:是)',
                            `missing_info_mask` tinyint NOT NULL DEFAULT 0 COMMENT '缺失信息掩码(1:标签,2:图片,4:内联笔记)',
                            `missing_count` tinyint NOT NULL DEFAULT 0 COMMENT '缺失信息数量,为0时自动扫描并触发状态转换',
                            `md_file_size` bigint NOT NULL DEFAULT 0 COMMENT 'MD文件大小合计(字节, 上传时由服务端写入, 用于存储配额统计)',
                            `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                            `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                            PRIMARY KEY (`id`),
                            KEY `idx_user_id` (`user_id`),
                            -- 这个索引逻辑唯一二级索引 在Java层完成唯一性校验
                            KEY `idx_user_topic_title_status` (`user_id`, `topic_id`, title(30), `status`),
                            KEY `idx_topic_status` (`topic_id`, `status`),
                            KEY `idx_status_user` (`status`, `user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='笔记存储记录表';


-- ==========================================
-- 5.1 笔记转换缓存表 (biz_note_converted)
-- ==========================================
CREATE TABLE `biz_note_converted` (
        `id`             bigint       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
        `note_id`        bigint       NOT NULL COMMENT '关联笔记ID(biz_note.id, 唯一)',
        `title`          varchar(200) NOT NULL COMMENT '引擎解析得到的标题',
        `tags_json`      text         DEFAULT NULL COMMENT '标签列表JSON',
        `create_time_str` varchar(50)  DEFAULT NULL COMMENT '原始create_time字符串',
        `toc_html`       mediumtext   DEFAULT NULL COMMENT 'TOC侧边栏HTML片段',
        `body_html`      longtext     NOT NULL COMMENT '正文HTML片段(已替换图片URL)(已解析双链)',
        `convert_time`   datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最近转换时间',
        PRIMARY KEY (`id`),
        UNIQUE KEY `uk_note_id` (`note_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='笔记转换结果缓存表';


-- ==========================================
-- 5.2 笔记覆盖上传 Diff 记录表 (biz_note_change_diff)
-- ==========================================
CREATE TABLE `biz_note_change_diff` (
    `id`            bigint       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `note_id`       bigint       NOT NULL COMMENT '关联笔记ID(biz_note.id, 唯一)',
    `status`        tinyint      NOT NULL DEFAULT 0 COMMENT '状态(0:等待确认, 1:已接受, 2:已拒绝)',
    `diff_json`     longtext     NOT NULL COMMENT 'Diff内容(JSON)',
    `scan_json`     longtext     DEFAULT NULL COMMENT '新文本扫描结果JSON(tags/imageNames/noteLinks)',
    `old_file_size` bigint       NOT NULL COMMENT '覆盖前文件大小',
    `new_file_size` bigint       NOT NULL COMMENT '覆盖后文件大小',
    `create_time`   datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`   datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_note_id` (`note_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='笔记覆盖上传Diff记录表';


-- ==========================================
-- 5.3 笔记内容表 (biz_note_context)
-- 满足需求：存储笔记的 Markdown 原文，与 biz_note 一对一关联
-- ==========================================
CREATE TABLE `biz_note_context` (
    `id`                     bigint       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `note_id`                bigint       NOT NULL COMMENT '关联笔记ID(biz_note.id, 唯一)',
    `markdown_content`       longtext     NOT NULL COMMENT 'Markdown 原始内容',
    `markdown_content_new`   longtext     DEFAULT NULL COMMENT 'Markdown 新版本内容（修改上传时临时存储，待确认后覆盖）',
    `create_time`            datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`            datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_note_id` (`note_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='笔记内容表(存储 Markdown 原文)';


-- ==========================================
-- 5.4 笔记双链映射表 (biz_note_each_mapping)
-- 满足需求：记录源笔记与目标笔记之间的双链关系
-- ==========================================
CREATE TABLE `biz_note_each_mapping` (
    `id`               bigint       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `source_note_id`   bigint       NOT NULL COMMENT '源笔记ID(biz_note.id)',
    `target_note_id`   bigint       DEFAULT NULL COMMENT '目标笔记ID(biz_note.id, 未命中时可为空)',
    `parsed_note_name` varchar(255) NOT NULL COMMENT '从双链中解析出来的笔记名(对应内联笔记的title)',
    `anchor`           varchar(255) DEFAULT NULL COMMENT '笔记锚点(双链中 # 之后的片段, 如 [[note.md#标题]] 中的"标题")',
    `nickname`         varchar(255) DEFAULT NULL COMMENT '笔记别名(双链中 | 之后的自定义显示名, 如 [[note.md|别名]] 中的"别名")',
    `status`           tinyint      NOT NULL DEFAULT 0 COMMENT '目标笔记审核快照(0:待审核,1:已通过,2:已拒绝)',
    `is_deleted`       tinyint      NOT NULL DEFAULT 0 COMMENT '是否删除(1:删除, 0:正常)',
    `create_time`      datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`      datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    -- 四元唯一性由 Java 应用层保证，此处仅作普通查询索引
    KEY `idx_source_target` (`source_note_id`, `target_note_id`),
    KEY `idx_target` (`target_note_id`),
    KEY `idx_delete` (`is_deleted`),     -- 方便后续要做软删除
    KEY `idx_note_deleted_status` (`source_note_id`, `is_deleted`, `status`) -- 方便发布前要做count(1)做优化
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='笔记双链映射表';


-- ==========================================
-- 6. 笔记-标签 多对多关联表 (biz_note_tag_mapping)
-- 满足需求：一篇笔记对应多个标签，一个标签对应多篇笔记
-- ==========================================
CREATE TABLE `biz_note_tag_mapping` (
                                        `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
                                        `note_id` bigint NOT NULL COMMENT '笔记ID',
                                        `tag_id` bigint DEFAULT NULL COMMENT '标签ID(NULL=未绑定)',
                                        `parsed_tag_name` varchar(20) NOT NULL COMMENT '从笔记中解析出的标签名',
                                        `status` tinyint NOT NULL DEFAULT 0 COMMENT '标签审核快照(0:待审核,1:审核中,2:已通过,3:已拒绝,4:已删除)',
                                        `is_deleted` tinyint NOT NULL DEFAULT 0 COMMENT '是否删除(1:删除, 0:正常)',
                                        `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '关联时间',
                                        `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                                        PRIMARY KEY (`id`),
                                        UNIQUE KEY `idx_note_tag` (`note_id`, `tag_id`),    -- 用于统计
                                        KEY `uk_note_tag_name` (`note_id`, parsed_tag_name),
                                        KEY `idx_tag_deleted` (`tag_id`, `is_deleted`), -- 方便通过 tag_id 反查所有 note_id
                                        KEY `idx_note_deleted_status` (`note_id`, `is_deleted`, `status`) -- 方便发布前要做count(1)做优化
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='笔记与标签多对多关联表';


-- ==========================================
-- 7. 图片资源映射表 (biz_image)
-- ==========================================
CREATE TABLE `biz_image` (
                             `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
                             `user_id` bigint NOT NULL COMMENT '上传者ID',
                             `topic_id` bigint DEFAULT NULL COMMENT '所属主题ID,0-默认(未分类)',
                             `filename` varchar(255) NOT NULL COMMENT '文件名',
                             `oss_url` varchar(1000) NOT NULL COMMENT '图片在OSS(或云存储)中的完整访问URL',
                             `storage_type` tinyint NOT NULL COMMENT '存储方式-1:阿里云OSS, 2:Cloudflare R2(预留)',
                             `file_size` int DEFAULT NULL COMMENT '文件大小(字节, 选填)',
                             `is_public` tinyint NOT NULL DEFAULT 0 COMMENT '是否公开(0:私有仅本人可引用, 1:公开可被其他用户搜索复用)',
                             `audit_status` tinyint NOT NULL DEFAULT 0 COMMENT '审核状态(0:待审核,1:审核中,2:已通过,3:已拒绝,4:已删除)',
                             `upload_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '上传时间',
                             PRIMARY KEY (`id`),
                             -- 对于 (uk_user_topic_filename) 采取 Java 层逻辑唯一索引
                             KEY `uk_user_topic_filename` (`user_id`, `topic_id`, filename(40)), -- 用户查询自己有哪些图片可以使用
                             KEY `idx_topic_public_filename` (`topic_id`, `is_public`, filename(40)), -- 用于查询某个主题下的所有图片(同时支持前缀模糊查询文件名)
                             KEY `idx_public_filename` (`is_public`, filename(40)), -- 用于公共图片库按文件名搜索
                             KEY `idx_user_audit_status` (`user_id`, `audit_status`) -- 方便统计
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='图片资源映射表';


-- ==========================================
-- 8. 笔记-图片引用映射表 (biz_note_image_mapping)
-- 满足需求：笔记解析时通过图片文件名查找图片URL，支持跨用户图片复用节省存储
-- ==========================================
CREATE TABLE `biz_note_image_mapping` (
    `id`                bigint       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `note_id`           bigint       NOT NULL COMMENT '关联的笔记ID',
    `image_id`          bigint       DEFAULT NULL COMMENT '关联的图片ID(biz_image.id, NULL=图片尚未绑定)',
    `note_user_id`      bigint       NOT NULL COMMENT '笔记所属用户ID(冗余字段，避免JOIN查询)',
    `image_user_id`     bigint       DEFAULT NULL COMMENT '图片所属用户ID(冗余字段，标识是否跨用户引用)',
    `parsed_image_name` varchar(255) NOT NULL COMMENT '笔记中解析出的原始图片名称(如: 架构图.png)，用于建立名称到URL的映射',
    `note_title`        varchar(100) NOT NULL COMMENT '笔记标题(冗余字段，用于后续按笔记标题搜索图片)',
    `is_cross_user`     tinyint      NOT NULL DEFAULT 0 COMMENT '是否跨用户引用(0:同一用户, 1:引用了其他用户的公开图片)',
    `status`            tinyint      NOT NULL DEFAULT 0 COMMENT '图片审核快照(0:待审核,1:审核中,2:已通过,3:已拒绝,4:已删除)',
    `is_deleted`        tinyint      NOT NULL DEFAULT 0 COMMENT '是否已删除(0:正常, 1:已删除，删除时软删)',
    `create_time`       datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '映射创建时间(首次解析时间)',
    `update_time`       datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_note_image` (`note_id`, `image_id`),                          -- 同一笔记不重复绑定同一张图片
    KEY `idx_image_id` (`image_id`),                                             -- 按图片反查所有引用笔记(删图前检查用)
    KEY `idx_note_user_name` (`note_user_id`, `parsed_image_name`, `is_deleted`),-- 解析时按用户+文件名快速定位已有映射
    KEY `idx_note_deleted_status` (`note_id`, `is_deleted`, `status`),           -- 用于发布时统计数据行count(1)优化
    KEY `idx_is_deleted` (`is_deleted`)                                          -- 方便懒删除时查找笔记
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='笔记-图片引用映射表';


-- ==========================================
-- 9. 每日 API 调用统计表 (biz_api_daily_usage)
-- ==========================================
CREATE TABLE `biz_api_daily_usage` (
                                       `id` bigint NOT NULL AUTO_INCREMENT,
                                       `user_id` bigint NOT NULL COMMENT '用户ID',
                                       `record_date` date NOT NULL COMMENT '记录日期(只存年月日,如 2024-05-20)',
                                       `used_count` int NOT NULL DEFAULT 0 COMMENT '当日已调用次数',
                                       `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '首次调用时间',
                                       `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最后更新时间',
                                       PRIMARY KEY (`id`),
                                       UNIQUE KEY `uk_user_date` (`user_id`, `record_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户每日API调用次数统计表';


-- ==========================================
-- 10. API 异步任务明细表 (biz_api_task_log)
-- ==========================================
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


-- ==========================================
-- 11. 标签审核表 (biz_tag_audit_record)
-- status: 0=待审核, 1=审核中, 2=已通过, 3=已拒绝, 4=已删除
-- ==========================================
CREATE TABLE `biz_tag_audit_record` (
    `id`                bigint       NOT NULL AUTO_INCREMENT COMMENT '审核记录ID',
    `applicant_user_id` bigint       NOT NULL COMMENT '申请者用户ID',
    `target_id`         bigint       NOT NULL COMMENT '关联的标签ID(biz_tag.id)',
    `apply_reason`      varchar(500) DEFAULT NULL COMMENT '申请说明(可选)',
    `status`            tinyint      NOT NULL DEFAULT 0 COMMENT '审核状态(0:待审核,1:审核中,2:已通过,3:已拒绝,4:已删除)',
    `reviewer_user_id`  bigint       DEFAULT NULL COMMENT '审核员用户ID(NULL=尚未审核)',
    `reject_reason`     varchar(500) DEFAULT NULL COMMENT '拒绝原因(status=3时填写)',
    `create_time`       datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '申请提交时间',
    `review_time`       datetime     DEFAULT NULL COMMENT '审核完成时间',
    `update_time`       datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最后更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_applicant_status` (`applicant_user_id`, `status`), -- 用户查自己的申请
    KEY `idx_status`           (`status`),                       -- 管理员批量处理
    KEY `idx_target`           (`target_id`),                    -- 按标签ID反查审核记录
    KEY `idx_status_update`    (`status`, `update_time`)         -- 管理员按更新时间拉取
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='标签审核表';

-- ==========================================
-- 12. 图片审核表 (biz_image_audit_record)
-- ==========================================
CREATE TABLE `biz_image_audit_record` (
    `id`                bigint       NOT NULL AUTO_INCREMENT COMMENT '审核记录ID',
    `applicant_user_id` bigint       NOT NULL COMMENT '申请者用户ID',
    `image_id`          bigint       NOT NULL COMMENT '关联的图片ID(biz_image.id)',
    `apply_reason`      varchar(500) DEFAULT NULL COMMENT '申请说明(可选)',
    `status`            tinyint      NOT NULL DEFAULT 0 COMMENT '审核状态(0:待审核,1:审核中,2:已通过,3:已拒绝,4:已删除)',
    `reviewer_user_id`  bigint       DEFAULT NULL COMMENT '审核员用户ID(NULL=尚未审核)',
    `reject_reason`     varchar(500) DEFAULT NULL COMMENT '拒绝原因(status=3时填写)',
    `create_time`       datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '申请提交时间',
    `review_time`       datetime     DEFAULT NULL COMMENT '审核完成时间',
    `update_time`       datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最后更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_applicant_status` (`applicant_user_id`, `status`), -- 用户查自己的申请
    KEY `idx_status`           (`status`),                      -- 管理员批量拉待审队列
    KEY `idx_image_id`         (`image_id`),                     -- 按图片反查审核记录
    KEY `idx_status_update`    (`status`, `update_time`)         -- 管理员按更新时间拉取
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='图片审核表';

-- ==========================================
-- 13. 笔记审核表 (biz_note_audit_record)
-- ==========================================
CREATE TABLE `biz_note_audit_record` (
    `id`                bigint       NOT NULL AUTO_INCREMENT COMMENT '审核记录ID',
    `applicant_user_id` bigint       NOT NULL COMMENT '申请者用户ID',
    `note_id`           bigint       NOT NULL COMMENT '关联的笔记ID(biz_note.id)',
    `apply_reason`      varchar(500) DEFAULT NULL COMMENT '申请说明(可选)',
    `status`            tinyint      NOT NULL DEFAULT 0 COMMENT '审核状态(0:待审核, 1:已通过, 2:已拒绝)',
    `reviewer_user_id`  bigint       DEFAULT NULL COMMENT '审核员用户ID(NULL=尚未审核)',
    `reject_reason`     varchar(500) DEFAULT NULL COMMENT '拒绝原因(status=2时填写)',
    `create_time`       datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '申请提交时间',
    `review_time`       datetime     DEFAULT NULL COMMENT '审核完成时间',
    `update_time`       datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最后更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_applicant_status` (`applicant_user_id`, `status`), -- 用户查自己的申请
    KEY `idx_status`           (`status`),                      -- 管理员批量拉待审队列
    KEY `idx_note_id`          (`note_id`),                      -- 按笔记反查审核记录
    KEY `idx_status_update`    (`status`, `update_time`)         -- 管理员按更新时间拉取
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='笔记审核表';


-- ==========================================
-- 14. 图片删除死信队列表 (biz_image_delete_dead_letter)
-- 用于记录需要异步重试删除的图片URL
-- status: 0=等待删除, 1=删除完成
-- ==========================================
CREATE TABLE `biz_image_delete_dead_letter` (
    `id`           bigint       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `image_url`    varchar(1000) NOT NULL COMMENT '待删除图片URL(OSS/R2等完整地址)',
    `status`       tinyint      NOT NULL DEFAULT 0 COMMENT '删除状态(0:等待删除, 1:删除完成)',
    `retry_count`  int          NOT NULL DEFAULT 0 COMMENT '重试次数',
    `create_time`  datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '入队时间',
    `update_time`  datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '状态更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_status_update` (`status`, `update_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='图片删除死信队列表';
