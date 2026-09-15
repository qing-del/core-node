
---

## 个人 SaaS 笔记系统数据库设计文档

### 表关系梳理

```mermaid
flowchart TD
    A[角色表 sys_role] --> |1对N| B[用户表 sys_user]
    B --> |1对N| C[主题/分类表 biz_topic]
    B --> |1对N| D[标签表 biz_tag]
    B --> |1对N| E[笔记/文章主表 biz_note]
    B --> |1对N| F[图片映射表 biz_image]
    E --> |1对1| N1[缓存表 biz_note_converted]
    E --> |1对1| N2[Diff表 biz_note_change_diff]
    E --> |1对1| N3[内容表 biz_note_context]
    E --> |1对N| N4[双链映射表 biz_note_each_mapping]
    C --> |1对N| E
    C --> |1对N| F
    E <--> |N对N 映射表| D
    E <--> |N对N 映射表| F
    B --> |1对N| G[每日API使用表 biz_api_daily_usage]
    B --> |1对N| H[API任务记录表 biz_api_task_log]
    F --> |1对N| I[图片删除死信队列表 biz_image_delete_dead_letter]
```

### 数据表汇总

| 序号 | 数据表名               | 中文名称               |
| ---- | ---------------------- | ---------------------- |
| 1    | sys_role               | 系统角色与额度配置表   |
| 2    | sys_user               | 用户信息表             |
| 3    | biz_topic              | 笔记主题/分类表        |
| 4    | biz_tag                | 笔记标签表             |
| 5    | biz_note               | 笔记存储记录表         |
| 6    | biz_note_converted     | 笔记转换结果缓存表     |
| 7    | biz_note_change_diff   | 笔记覆盖上传Diff记录表 |
| 8    | biz_note_context       | 笔记内容表             |
| 9    | biz_note_each_mapping  | 笔记双链映射表         |
| 10   | biz_note_tag_mapping   | 笔记与标签映射表       |
| 11   | biz_image              | 图片资源映射表         |
| 12   | biz_note_image_mapping | 笔记与图片映射表       |
| 13   | biz_api_daily_usage    | 每日API调用统计表      |
| 14   | biz_api_task_log       | API异步任务明细表      |
| 15   | biz_meta_audit_record  | 元数据审核记录表       |
| 16   | biz_image_audit_record | 图片审核记录表         |
| 17   | biz_note_audit_record  | 笔记审核记录表         |
| 18   | biz_image_delete_dead_letter | 图片删除死信队列表   |

---

### 1. sys_role (系统角色与额度配置表)

用于存储系统角色信息，并统一管控各个角色的 API 调用上限与存储空间配额。

| 字段名            | 数据类型    | 说明                 | 备注                                           |
| ----------------- | ----------- | -------------------- | ---------------------------------------------- |
| id                | bigint      | 主键                 | 自增                                           |
| role_name         | varchar(50) | 角色名称             | 如：普通用户、VIP、管理员                      |
| role_code         | varchar(50) | 角色标识             | 唯一键 (`uk_role_code`)，如：USER, VIP         |
| daily_api_limit   | int         | 每日外部API调用上限  | 默认 5                                         |
| max_storage_bytes | bigint      | 默认最大存储空间     | 单位：字节，默认 104857600 (100MB)             |
| create_time       | datetime    | 创建时间             | 默认 CURRENT_TIMESTAMP                         |
| update_time       | datetime    | 更新时间             | 默认 CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP |

#### 索引与约束

| 索引名 | 类型 | 字段 | 说明 |
|--------|------|------|------|
| PRIMARY | 主键 | id | 自增主键 |
| uk_role_code | 唯一索引 | role_code | 角色标识唯一 |

### 2. sys_user (用户信息表)

用于存储登录用户的基本信息及个体资源使用情况。

| 字段名             | 数据类型    | 说明                 | 备注                                           |
| ------------------ | ----------- | -------------------- | ---------------------------------------------- |
| id                 | bigint      | 主键                 | 自增                                           |
| username           | varchar(50) | 登录账号             | 唯一键 (`uk_username`)                         |
| password           | char(60)    | 加密密码             | 使用 BCrypt 加密存储                           |
| nickname           | varchar(50) | 昵称                 | 普通索引 (`idx_nickname` 与 status 联合)       |
| email              | varchar(100)| 邮箱地址             | 唯一键 (`idx_email`)，可为空                   |
| role_id            | bigint      | 关联的角色ID         | 默认 3 (普通用户)                               |
| max_storage_bytes  | bigint      | 个性化最大存储空间   | 可为空 (NULL表示使用角色默认值)                |
| used_storage_bytes | bigint      | 当前已用存储空间 | 单位：字节，默认 0                             |
| status             | tinyint     | 状态                 | 2:未激活, 1:正常, 0:禁用。联合索引 (`idx_status_role`) |
| create_time        | datetime    | 注册时间             | 默认 CURRENT_TIMESTAMP                         |
| update_time        | datetime    | 更新时间             | 默认 CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP |

#### 索引与约束

| 索引名 | 类型 | 字段 | 说明 |
|--------|------|------|------|
| PRIMARY | 主键 | id | 自增主键 |
| uk_username | 唯一索引 | username | 登录账号唯一 |
| idx_email | 唯一索引 | email | 邮箱地址唯一 |
| idx_nickname | 普通索引 | nickname, status | 昵称与状态联合索引 |
| idx_status_role | 普通索引 | status, role_id | 状态与角色联合索引 |

### 3. biz_topic (笔记主题/分类表)

用于存储用户创建的笔记分类体系，不同用户的数据相互隔离。

| 字段名      | 数据类型    | 说明                 | 备注                                           |
| ----------- | ----------- | -------------------- | ---------------------------------------------- |
| id          | bigint      | 主键                 | 自增                                           |
| user_id     | bigint      | 创建者ID             | 普通索引 (`idx_user_id`)                       |
| topic_name  | varchar(25) | 主题名称             | 联合唯一键 (`uk_topic_user`：topic_name+user_id) |
| sort_order  | int         | 排序字段             | 默认 0。联合索引 (`idx_user_sort_update`)      |
| is_pass     | tinyint     | 审核状态             | 0:待审核, 1:已通过, 2:已拒绝。默认 0           |
| create_time | datetime    | 创建时间             | 默认 CURRENT_TIMESTAMP                         |
| update_time | datetime    | 更新时间             | 默认 CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP |

#### 索引与约束

| 索引名 | 类型 | 字段 | 说明 |
|--------|------|------|------|
| PRIMARY | 主键 | id | 自增主键 |
| idx_user_id | 普通索引 | user_id | 创建者ID索引 |
| idx_user_sort_update | 普通索引 | user_id, sort_order, update_time | 用户、排序、更新时间联合索引 |
| uk_topic_user | 唯一索引 | topic_name, user_id | 主题名称与用户联合唯一 |

### 4. biz_tag (笔记标签表)

用于存储用户创建的标签体系，可被多篇笔记复用。

| 字段名      | 数据类型    | 说明                 | 备注                                           |
| ----------- | ----------- | -------------------- | ---------------------------------------------- |
| id          | bigint      | 主键                 | 自增                                           |
| user_id     | bigint      | 创建者ID             | 普通索引 (`idx_user_id`)                       |
| tag_name    | varchar(20) | 标签名称             | 联合唯一键 (`uk_tag_user`：tag_name+user_id)   |
| is_pass     | tinyint     | 审核状态             | 0:待审核, 1:已通过, 2:已拒绝。默认 0           |
| create_time | datetime    | 创建时间             | 默认 CURRENT_TIMESTAMP                         |

#### 索引与约束

| 索引名 | 类型 | 字段 | 说明 |
|--------|------|------|------|
| PRIMARY | 主键 | id | 自增主键 |
| idx_user_id | 普通索引 | user_id | 创建者ID索引 |
| uk_tag_user | 唯一索引 | tag_name, user_id | 标签名称与用户联合唯一 |

### 5. biz_note (笔记存储记录表)

系统的核心业务主表，记录每一篇笔记的基础元数据与安全属性。

| 字段名           | 数据类型     | 说明                 | 备注                                           |
| ---------------- | ------------ | -------------------- | ---------------------------------------------- |
| id               | bigint       | 主键                 | 自增                                           |
| user_id          | bigint       | 作者(用户ID)         | 普通索引 (`idx_user_id`)                       |
| topic_id         | bigint       | 所属主题ID           | 可为空 (未分类)。联合索引 (`idx_topic_deleted`) |
| title            | varchar(100) | 笔记标题             |                                                |
| description      | varchar(255) | 笔记描述             | 可为空                                         |
| is_published     | tinyint      | 是否发布             | 1:公开, 0:私密。默认 0                         |
| storage_type     | tinyint      | 存储方式             | 0:本地存储, 1:阿里云OSS, 2:Cloudflare R2       |
| is_missing_info  | tinyint      | 信息是否缺失         | 0:正常, 1:标签或图片未完整绑定。默认 0         |
| is_pass          | tinyint      | 审核状态             | 0:待审核, 1:已通过, 2:已拒绝。默认 0           |
| is_deleted       | tinyint      | 是否删除(软删)       | 1:删除, 0:正常。默认 0                         |
| md_file_size     | bigint       | MD文件大小合计       | 单位：字节，默认 0                             |
| create_time      | datetime     | 创建时间             | 默认 CURRENT_TIMESTAMP                         |
| update_time      | datetime     | 更新时间             | 默认 CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP |

#### 索引与约束

| 索引名 | 类型 | 字段 | 说明 |
|--------|------|------|------|
| PRIMARY | 主键 | id | 自增主键 |
| idx_user_id | 普通索引 | user_id | 作者ID索引 |
| idx_user_topic_title_deleted | 普通索引 | user_id, topic_id, title(30), is_deleted | 用户、主题、标题、删除状态联合索引 |
| idx_topic_deleted | 普通索引 | topic_id, is_deleted | 主题与删除状态联合索引 |

### 5.1 biz_note_converted (笔记转换缓存表)

用于缓存 Markdown 引擎解析得到的持久化 HTML 片段与元数据。

| 字段名          | 数据类型     | 说明             | 备注                                       |
| --------------- | ------------ | ---------------- | ------------------------------------------ |
| id              | bigint       | 主键             | 自增                                       |
| note_id         | bigint       | 关联笔记ID       | 唯一键 (`uk_note_id`)                      |
| title           | varchar(200) | 引擎解析标题     |                                            |
| tags_json       | text         | 标签列表JSON     | 可为空                                     |
| create_time_str | varchar(50)  | 原始创建时间     | 可为空                                     |
| toc_html        | mediumtext   | TOC侧边栏HTML    | 可为空                                     |
| body_html       | longtext     | 正文HTML片段     | 已替换图片URL及双链解析                    |
| convert_time    | datetime     | 最近转换时间     | 默认 CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP |

#### 索引与约束

| 索引名 | 类型 | 字段 | 说明 |
|--------|------|------|------|
| PRIMARY | 主键 | id | 自增主键 |
| uk_note_id | 唯一索引 | note_id | 关联笔记ID唯一 |

### 5.2 biz_note_change_diff (笔记覆盖上传 Diff 记录表)

处理笔记覆盖上传时的文件比对记录，供用户在 Web 面板确认或拒绝。

| 字段名        | 数据类型 | 说明         | 备注                                       |
| ------------- | -------- | ------------ | ------------------------------------------ |
| id            | bigint   | 主键         | 自增                                       |
| note_id       | bigint   | 关联笔记ID   | 唯一键 (`uk_note_id`)                      |
| status        | tinyint  | 状态         | 0:等待确认, 1:已接受, 2:已拒绝。默认 0     |
| diff_json     | longtext | Diff内容JSON |                                            |
| old_file_size | bigint   | 旧文件大小   | 覆盖前大小                                 |
| new_file_size | bigint   | 新文件大小   | 覆盖后大小                                 |
| create_time   | datetime | 创建时间     | 默认 CURRENT_TIMESTAMP                     |
| update_time   | datetime | 更新时间     | 默认 CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP |

#### 索引与约束

| 索引名 | 类型 | 字段 | 说明 |
|--------|------|------|------|
| PRIMARY | 主键 | id | 自增主键 |
| uk_note_id | 唯一索引 | note_id | 关联笔记ID唯一 |

### 5.3 biz_note_context (笔记内容表)

将原本存储在本地的 Markdown 原文挪入数据库结构化持久保存。

| 字段名               | 数据类型 | 说明           | 备注                                       |
| -------------------- | -------- | -------------- | ------------------------------------------ |
| id                   | bigint   | 主键           | 自增                                       |
| note_id              | bigint   | 关联笔记ID     | 唯一键 (`uk_note_id`)                      |
| markdown_content     | longtext | MD原始内容     |                                            |
| markdown_content_new | longtext | MD新版本内容   | 可为空，覆盖上传时临时存储                 |
| create_time          | datetime | 创建时间       | 默认 CURRENT_TIMESTAMP                     |
| update_time          | datetime | 更新时间       | 默认 CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP |

#### 索引与约束

| 索引名 | 类型 | 字段 | 说明 |
|--------|------|------|------|
| PRIMARY | 主键 | id | 自增主键 |
| uk_note_id | 唯一索引 | note_id | 关联笔记ID唯一 |

### 5.4 biz_note_each_mapping (笔记双链映射表)

记录源笔记与目标笔记之间的双链引用关系，支持 Obsidian-style 的锚点和别名特性。

| 字段名           | 数据类型     | 说明           | 备注                                       |
| ---------------- | ------------ | -------------- | ------------------------------------------ |
| id               | bigint       | 主键           | 自增                                       |
| source_note_id   | bigint       | 源笔记ID       | 联合索引 (`idx_source_target`)             |
| target_note_id   | bigint       | 目标笔记ID     | 联合索引。可为空（未命中时为空）           |
| parsed_note_name | varchar(255) | 解析出的笔记名 |                                            |
| anchor           | varchar(255) | 笔记锚点       | 可为空                                     |
| nickname         | varchar(255) | 笔记别名       | 可为空                                     |
| is_pass          | tinyint      | 审核状态       | 0:未通过, 1:已通过。默认 0                 |
| is_deleted       | tinyint      | 是否删除(软删) | 1:删除, 0:正常。默认 0                     |
| create_time      | datetime     | 创建时间       | 默认 CURRENT_TIMESTAMP                     |
| update_time      | datetime     | 更新时间       | 默认 CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP |

#### 索引与约束

| 索引名 | 类型 | 字段 | 说明 |
|--------|------|------|------|
| PRIMARY | 主键 | id | 自增主键 |
| idx_source_target | 普通索引 | source_note_id, target_note_id | 源笔记与目标笔记联合索引 |
| idx_target | 普通索引 | target_note_id | 目标笔记ID索引 |
| idx_delete | 普通索引 | is_deleted | 删除状态索引 |
| idx_note_deleted_pass | 普通索引 | source_note_id, is_deleted, is_pass | 源笔记、删除状态、审核状态联合索引 |

### 6. biz_note_tag_mapping (笔记与标签多对多关联表)

维护单篇笔记与多个标签之间的映射关系。增加冗余解析名，便于在用户新建尚未绑定的标签前临时占位或做 UI 上的标记。

| 字段名          | 数据类型    | 说明                 | 备注                                          |
| --------------- | ----------- | -------------------- | --------------------------------------------- |
| id              | bigint      | 主键                 | 自增                                          |
| note_id         | bigint      | 笔记ID               | 唯一联合键 (`idx_note_tag`)                   |
| tag_id          | bigint      | 标签ID               | 可为空 (NULL=未绑定)                          |
| parsed_tag_name | varchar(20) | 解析出的标签名       | 普通联合索引 (`uk_note_tag_name`)             |
| is_pass         | tinyint     | 审核状态             | 0:未通过, 1:已通过。默认 0                    |
| is_deleted      | tinyint     | 是否删除(软删)       | 1:删除, 0:正常。默认 0                        |
| create_time     | datetime    | 关联时间             | 默认 CURRENT_TIMESTAMP                        |
| update_time     | datetime    | 更新时间             | 默认 CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP |

#### 索引与约束

| 索引名 | 类型 | 字段 | 说明 |
|--------|------|------|------|
| PRIMARY | 主键 | id | 自增主键 |
| idx_note_tag | 唯一索引 | note_id, tag_id | 笔记与标签联合唯一 |
| uk_note_tag_name | 普通索引 | note_id, parsed_tag_name | 笔记与解析标签名联合索引 |
| idx_tag_deleted | 普通索引 | tag_id, is_deleted | 标签与删除状态联合索引 |
| idx_note_deleted_pass | 普通索引 | note_id, is_deleted, is_pass | 笔记、删除状态、审核状态联合索引 |

### 7. biz_image (图片资源映射表)

集中管理系统内所有的图片资源，支持公共图片池设定。

| 字段名       | 数据类型      | 说明                 | 备注                                           |
| ------------ | ------------- | -------------------- | ---------------------------------------------- |
| id           | bigint        | 主键                 | 自增                                           |
| user_id      | bigint        | 上传者ID             |                                                |
| topic_id     | bigint        | 所属主题ID           | NULL代表未分类                                 |
| filename     | varchar(255)  | 文件名               | 联合索引 (`uk_user_topic_filename`)            |
| oss_url      | varchar(1000) | 云端完整访问URL      |                                                |
| storage_type | tinyint       | 存储方式             | 1:阿里云OSS, 2:Cloudflare R2(预留)             |
| file_size    | int           | 文件大小             | 单位：字节，可为空                             |
| is_public    | tinyint       | 是否公开             | 0:私有仅本人可引用, 1:公开可被其他用户搜索复用。联合索引 (`idx_public_filename`) |
| is_pass      | tinyint       | 审核状态             | 0:待审核, 1:已通过, 2:已拒绝。默认 0           |
| upload_time  | datetime      | 上传时间             | 默认 CURRENT_TIMESTAMP                         |

#### 索引与约束

| 索引名 | 类型 | 字段 | 说明 |
|--------|------|------|------|
| PRIMARY | 主键 | id | 自增主键 |
| uk_user_topic_filename | 普通索引 | user_id, topic_id, filename(40) | 用户、主题、文件名联合索引 |
| idx_topic_public_filename | 普通索引 | topic_id, is_public, filename(40) | 主题、公开状态、文件名联合索引 |
| idx_public_filename | 普通索引 | is_public, filename(40) | 公开状态与文件名联合索引 |

### 8. biz_note_image_mapping (笔记与图片映射表)

实现图片跨笔记、跨用户复用的核心表，关联笔记与具体的图片资产。

| 字段名            | 数据类型     | 说明                 | 备注                                           |
| ----------------- | ------------ | -------------------- | ---------------------------------------------- |
| id                | bigint       | 主键                 | 自增                                           |
| note_id           | bigint       | 关联的笔记ID         | 唯一键 (`uk_note_image` 与 image_id 联合)      |
| image_id          | bigint       | 关联的图片ID         |                                                |
| note_user_id      | bigint       | 笔记所属用户ID       | 冗余字段，避免JOIN。联合索引 (`idx_note_user_name`) |
| image_user_id     | bigint       | 图片所属用户ID       | 冗余字段，用于标识是否跨用户引用               |
| parsed_image_name | varchar(255) | MD中解析出的原始图名 |                                                |
| note_title        | varchar(100) | 笔记标题             | 冗余字段，用于按笔记标题检索图片               |
| is_cross_user     | tinyint      | 是否跨用户引用       | 0:同一用户, 1:引用他人。默认 0                 |
| is_pass           | tinyint      | 审核状态             | 0:未通过, 1:已通过。默认 0                 |
| is_deleted        | tinyint      | 是否已删除(软删)     | 0:正常, 1:删除。相关普通联合索引防止回表       |
| create_time       | datetime     | 映射创建时间         | 默认 CURRENT_TIMESTAMP                         |
| update_time       | datetime     | 更新时间             | 默认 CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP |

#### 索引与约束

| 索引名 | 类型 | 字段 | 说明 |
|--------|------|------|------|
| PRIMARY | 主键 | id | 自增主键 |
| uk_note_image | 唯一索引 | note_id, image_id | 笔记与图片联合唯一 |
| idx_image_id | 普通索引 | image_id | 图片ID索引 |
| idx_note_user_name | 普通索引 | note_user_id, parsed_image_name, is_deleted | 笔记用户、解析图片名、删除状态联合索引 |
| idx_note_deleted_pass | 普通索引 | note_id, is_deleted, is_pass | 笔记、删除状态、审核状态联合索引 |
| idx_is_deleted | 普通索引 | is_deleted | 删除状态索引 |

### 9. biz_api_daily_usage (每日API调用次数统计表)

按日统计用户的 API 调用情况，便于执行限流逻辑。

| 字段名      | 数据类型 | 说明             | 备注                                           |
| ----------- | -------- | ---------------- | ---------------------------------------------- |
| id          | bigint   | 主键             | 自增                                           |
| user_id     | bigint   | 用户ID           | 联合唯一键 (`uk_user_date`：user_id+record_date) |
| record_date | date     | 记录日期         | 仅存年月日 (如 2024-05-20)                     |
| used_count  | int      | 当日已调用次数   | 默认 0                                         |
| create_time | datetime | 首次调用时间     | 默认 CURRENT_TIMESTAMP                         |
| update_time | datetime | 最后更新时间     | 默认 CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP |

#### 索引与约束

| 索引名 | 类型 | 字段 | 说明 |
|--------|------|------|------|
| PRIMARY | 主键 | id | 自增主键 |
| uk_user_date | 唯一索引 | user_id, record_date | 用户与日期联合唯一 |

### 10. biz_api_task_log (API异步任务明细表)

用于追踪耗时型 API（如大模型调用、Python脚本处理）的异步执行状态。

| 字段名      | 数据类型      | 说明             | 备注                                           |
| ----------- | ------------- | ---------------- | ---------------------------------------------- |
| id          | bigint        | 主键             | 自增                                           |
| user_id     | bigint        | 调用者ID         | 普通索引 (`idx_user_id`)                       |
| task_type   | varchar(50)   | 任务类型         | 如: generate_audio, analyze_text               |
| task_status | tinyint       | 任务状态         | 0:处理中, 1:成功, 2:失败。默认 0               |
| result_url  | varchar(1000) | 执行成功的文件链接 | 可为空                                         |
| error_msg   | text          | 失败时的错误信息 | 可为空                                         |
| create_time | datetime      | 任务提交时间     | 默认 CURRENT_TIMESTAMP                         |
| finish_time | datetime      | 任务完成时间     | 可为空                                         |

#### 索引与约束

| 索引名 | 类型 | 字段 | 说明 |
|--------|------|------|------|
| PRIMARY | 主键 | id | 自增主键 |
| idx_user_id | 普通索引 | user_id | 调用者ID索引 |

### 11. biz_meta_audit_record (元数据审核记录表)

用于审核主题和标签的申请记录，统一管理轻量级元数据的审核流程。

| 字段名            | 数据类型      | 说明                 | 备注                                           |
| ----------------- | ------------- | -------------------- | ---------------------------------------------- |
| id                | bigint        | 主键                 | 自增                                           |
| applicant_user_id | bigint        | 申请者用户ID         | 普通索引 (`idx_applicant_status`)              |
| apply_type        | tinyint       | 申请类型             | 1:主题, 2:标签。联合索引 (`idx_target`)       |
| target_id         | bigint        | 关联的主题ID或标签ID | 由 apply_type 决定                              |
| apply_reason      | varchar(500)  | 申请说明             | 可为空                                         |
| status            | tinyint       | 审核状态             | 0:待审核, 1:已通过, 2:已拒绝。联合索引 (`idx_status_type`) |
| reviewer_user_id  | bigint        | 审核员用户ID         | 可为空 (NULL=尚未审核)                         |
| reject_reason     | varchar(500)  | 拒绝原因             | 可为空 (status=2时填写)                        |
| create_time       | datetime      | 申请提交时间         | 默认 CURRENT_TIMESTAMP                         |
| review_time       | datetime      | 审核完成时间         | 可为空                                         |

#### 索引与约束

| 索引名 | 类型 | 字段 | 说明 |
|--------|------|------|------|
| PRIMARY | 主键 | id | 自增主键 |
| idx_applicant_status | 普通索引 | applicant_user_id, status | 申请者与状态联合索引 |
| idx_status_type | 普通索引 | status, apply_type | 状态与申请类型联合索引 |
| idx_target | 普通索引 | apply_type, target_id | 申请类型与目标ID联合索引 |
| idx_status_update | 普通索引 | status, update_time | 状态与更新时间联合索引 |

### 12. biz_image_audit_record (图片审核记录表)

用于管理图片的公开/私有申请审核流程。

| 字段名            | 数据类型      | 说明                 | 备注                                           |
| ----------------- | ------------- | -------------------- | ---------------------------------------------- |
| id                | bigint        | 主键                 | 自增                                           |
| applicant_user_id | bigint        | 申请者用户ID         | 普通索引 (`idx_applicant_status`)              |
| image_id          | bigint        | 关联的图片ID         | 普通索引 (`idx_image_id`)                      |
| apply_reason      | varchar(500)  | 申请说明             | 可为空                                         |
| status            | tinyint       | 审核状态             | 0:待审核, 1:已通过, 2:已拒绝。普通索引 (`idx_status`) |
| reviewer_user_id  | bigint        | 审核员用户ID         | 可为空 (NULL=尚未审核)                         |
| reject_reason     | varchar(500)  | 拒绝原因             | 可为空 (status=2时填写)                        |
| create_time       | datetime      | 申请提交时间         | 默认 CURRENT_TIMESTAMP                         |
| review_time       | datetime      | 审核完成时间         | 可为空                                         |

#### 索引与约束

| 索引名 | 类型 | 字段 | 说明 |
|--------|------|------|------|
| PRIMARY | 主键 | id | 自增主键 |
| idx_applicant_status | 普通索引 | applicant_user_id, status | 申请者与状态联合索引 |
| idx_status | 普通索引 | status | 状态索引 |
| idx_image_id | 普通索引 | image_id | 图片ID索引 |
| idx_status_update | 普通索引 | status, update_time | 状态与更新时间联合索引 |

### 13. biz_note_audit_record (笔记审核记录表)

用于管理笔记的发布申请审核流程。

| 字段名            | 数据类型      | 说明                 | 备注                                           |
| ----------------- | ------------- | -------------------- | ---------------------------------------------- |
| id                | bigint        | 主键                 | 自增                                           |
| applicant_user_id | bigint        | 申请者用户ID         | 普通索引 (`idx_applicant_status`)              |
| note_id           | bigint        | 关联的笔记ID         | 普通索引 (`idx_note_id`)                       |
| apply_reason      | varchar(500)  | 申请说明             | 可为空                                         |
| status            | tinyint       | 审核状态             | 0:待审核, 1:已通过, 2:已拒绝。普通索引 (`idx_status`) |
| reviewer_user_id  | bigint        | 审核员用户ID         | 可为空 (NULL=尚未审核)                         |
| reject_reason     | varchar(500)  | 拒绝原因             | 可为空 (status=2时填写)                        |
| create_time       | datetime      | 申请提交时间         | 默认 CURRENT_TIMESTAMP                         |
| review_time       | datetime      | 审核完成时间         | 可为空                                         |

#### 索引与约束

| 索引名 | 类型 | 字段 | 说明 |
|--------|------|------|------|
| PRIMARY | 主键 | id | 自增主键 |
| idx_applicant_status | 普通索引 | applicant_user_id, status | 申请者与状态联合索引 |
| idx_status | 普通索引 | status | 状态索引 |
| idx_note_id | 普通索引 | note_id | 笔记ID索引 |
| idx_status_update | 普通索引 | status, update_time | 状态与更新时间联合索引 |

### 14. biz_image_delete_dead_letter (图片删除死信队列表)

用于记录图片删除失败后的重试任务，支撑异步补偿删除。

| 字段名      | 数据类型      | 说明                 | 备注                                           |
| ----------- | ------------- | -------------------- | ---------------------------------------------- |
| id          | bigint        | 主键                 | 自增                                           |
| image_url   | varchar(1000) | 待删除图片URL        | 保存 OSS/R2 等完整访问地址                     |
| status      | tinyint       | 删除状态             | 0:等待删除, 1:删除完成。默认 0                 |
| retry_count | int           | 重试次数             | 默认 0                                         |
| create_time | datetime      | 入队时间             | 默认 CURRENT_TIMESTAMP                         |
| update_time | datetime      | 状态更新时间         | 默认 CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP |

#### 索引与约束

| 索引名 | 类型 | 字段 | 说明 |
|--------|------|------|------|
| PRIMARY | 主键 | id | 自增主键 |
| idx_status_update | 普通索引 | status, update_time | 状态与更新时间联合索引 |