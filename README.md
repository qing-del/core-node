# CORE NODE

Architecture reference: [Reliable cross-module domain events](static/document/architecture/reliable-domain-events.md).

基于 Spring Boot 4.x + JDK 21 的多模块中台服务，提供笔记（Markdown）管理、图片对象存储、标签/主题管理、审核工作流、邮件通知，以及基于 Redis Streams 的异步音频生成等能力。前端采用 Vue 3 + Arco Design + Tailwind CSS。

接口分为 **管理端 (`/admin/**`)**、**用户端 (`/user/**`)** 与 **访客端 (`/guest/**`)**：管理端和用户端通过无状态 Spring Security Filter 验证 JWT 并写入 `SecurityContext`，访客端提供公开笔记阅读能力；另有 **回调接口 (`/common/**`)** 供 Python 音频引擎内网调用。

---

## 一、环境要求

1. **JDK 21**
2. **Maven 3.8+**
3. **MySQL 8.x**，使用 `static/database/createDatabase.sql` 初始化库表
4. **Redis**（JWT 令牌存储、音频任务队列依赖 Redis Streams）
5. **阿里云 OSS** 账号及 AccessKey（图片对象存储）
6. **SMTP 邮箱**（账号激活、管理员群发邮件）
7. **Python FastAPI 音频引擎**（音频生成功能，可选，详见下文 §九）

---

## 二、项目结构

### 多模块 Maven 布局

| 模块 | 用途 |
| --- | --- |
| `middleware-server` | 运行时纯装配：启动类、基础配置与资源配置；不承载 Controller、Service、Mapper、Aspect 或业务任务 |
| `middleware-common` | core、web、security 通用能力（结果、异常、Web 支持与认证上下文） |
| `middleware-framework` | Markdown 与 OSS 的 starter / autoconfigure |
| `middleware-module-system` | 用户、角色、登录、邮件、配额与监控 |
| `middleware-module-note` | 笔记、主题、标签、关系与转换 |
| `middleware-module-media` | 图片与对象存储业务 |
| `middleware-module-audit` | 审核工作流 |
| `middleware-module-audio` | 音频任务与 Redis Stream 工作流 |

### 前端

| 目录 | 技术栈 |
| --- | --- |
| `frontend/` | Vue 3 + TypeScript + Vite、Arco Design、Pinia、Tailwind CSS、ECharts、Vue Router |

---

## 三、配置阿里云 OSS 环境变量

OSS 凭证从环境变量读取，**不要写入配置文件**。

Windows（cmd）临时设置：

```cmd
set OSS_ACCESS_KEY_ID=yourAccessKeyId
set OSS_ACCESS_KEY_SECRET=yourAccessKeySecret
```

Windows 永久持久化：

```cmd
setx OSS_ACCESS_KEY_ID "%OSS_ACCESS_KEY_ID%"
setx OSS_ACCESS_KEY_SECRET "%OSS_ACCESS_KEY_SECRET%"
```

验证：

```cmd
echo %OSS_ACCESS_KEY_ID%
echo %OSS_ACCESS_KEY_SECRET%
```

---

## 四、应用配置项

至少需要在运行时配置以下值（生产环境通过环境变量或外部配置覆盖）：

| 配置项 | 说明 |
| --- | --- |
| `jacolp.datasource.*` | MySQL 主机 / 端口 / 库名 / 用户名 / 密码 |
| `jacolp.admin.username` | 内置创建者账号（用于初始化） |
| `jacolp.admin.password` | 内置创建者密码 |
| `jacolp.admin.email` | 管理员邮箱 |
| `jacolp.jwt.*` | JWT 密钥、过期时长等 |
| `jacolp.redis.*` | Redis 主机 / 端口 / 用户名 / 密码 |
| `jacolp.cloudflare.*` | Cloudflare R2（S3 兼容对象存储，通过 `\${CLOUDFLARE_ACCOUNT_ID}` 环境变量注入） |
| `jacolp.mail.*` | SMTP 主机 / 端口 / 发件邮箱 / 授权码 |
| `jacolp.audio.callback-token` | 音频引擎回调认证令牌 |
| `jacolp.audio.queue-type` | 音频生成与删除队列：`redis-stream`（默认）或 `rabbitmq`，可由 `AUDIO_QUEUE_TYPE` 注入 |

本地开发可直接使用 `middleware-server/src/main/resources/application-dev.yaml` 中的默认值。

---

## 五、创建者账号初始化（重要）

应用启动时 `DataInitializer` 会自动执行，保证存在一个有效的「创建者」账号：

1. 优先按 `id = 1` 查找用户；
2. 找不到则按 `jacolp.admin.username` 查找；
3. 仍找不到时，按配置插入一条创建者账号（启用状态）；
4. 找到时，强制将其更新为：
   - 配置中的用户名（若与库内不同）
   - 配置中的密码（若变更则重新加密）
   - 创建者角色
   - 启用状态

> 因此修改 `jacolp.admin.username` 或 `jacolp.admin.password` 后再次启动，会同步覆盖数据库中已有的创建者账号信息，请谨慎修改。

---

## 六、构建与启动

项目根目录下：

```bash
mvn clean install
```

启动服务端模块：

```bash
mvn -pl middleware-server spring-boot:run
```

启动前端开发服务器（`frontend/` 目录下）：

```bash
npm run dev
```

启动成功后：

- Knife4j 接口文档：<http://localhost:8080/doc.html>
- OpenAPI JSON：`/v3/api-docs/admin 端接口`、`/v3/api-docs/user 端接口`、`/v3/api-docs/guest 端接口`

---

## 七、用户端与访客接口概览

所有用户端接口需通过 `Authorization: Bearer <token>` 携带 JWT 鉴权（登录、注册、邮件激活等公开接口除外）。

### 7.1 用户认证（`/user/user`）
- 注册、登录、退出登录
- 通过邮件激活链接（`GET /active/{token}`）或 6 位激活码（`POST /active-code`）激活账号
- 未激活用户可匿名重新发送激活邮件
- 查询 / 更新当前用户信息（昵称、邮箱、密码）
- 软删除当前账户
- 获取用户概览（基本信息，不含资源统计）

### 7.2 邮箱管理（`/user/email`）
- 获取邮箱地址与激活状态
- 重新发送激活邮件
- 发起邮箱修改：校验原邮箱后向新邮箱发送 6 位验证码
- 验证邮箱修改码并完成邮箱换绑

### 7.3 主题管理（`/user/topic`）
- 条件分页查询主题，支持 `scope` 控制当前用户 / 全局范围
- 新增 / 修改（排序）/ 批量删除主题
- 发起 / 撤销主题审核申请
- 主题数量统计

### 7.4 标签管理（`/user/tag`）
- 查询当前用户标签列表（`GET /user/tag`）
- 条件分页查询标签，支持 `scope` 控制当前用户 / 全局范围
- 单个新增 / 批量新增 / 批量删除标签
- 绑定 / 解绑标签与笔记或主题
- 发起 / 撤销标签审核申请
- 标签数量统计

### 7.5 图片管理（`/user/image`）
- 条件分页查询图片，支持 `scope` 控制当前用户 / 全局范围
- 上传 / 查看详情 / 删除图片
- 修改图片元信息（名称、所属主题）
- 替换图片源文件
- 发起 / 撤销图片审核申请
- 图片数量统计

### 7.6 笔记管理（`/user/note`）
- 上传 Markdown 笔记，自动扫描标签、图片、双链引用并建立映射，返回缺失项
- 修改笔记源文件：先生成 Diff 与临时版本，再通过「确认 / 取消」接口决定是否覆盖
- 修改笔记元信息（描述、主题）
- 条件分页查询、全文搜索、详情查看
- Markdown → HTML 转换、删除转换缓存
- 设置发布 / 下架状态（要求审核通过且关联完整）
- 查询变更 Diff 详情、笔记 Markdown 源内容、转换后的 HTML 内容
- 条件分页 / 全文搜索支持 `scope` 控制当前用户 / 全局范围
- 发起 / 撤销笔记审核申请、删除笔记
- 笔记数量统计（总数 / 公开 / 已通过）

### 7.7 笔记关联管理（`/user/note/relation`）
- 查询笔记的标签、图片、双链笔记映射
- 绑定 / 解绑标签、图片、双链笔记映射
- 校验笔记关联完整性，自动流转笔记状态
- 查询笔记关联图片
- 查询笔记、标签、图片的反向引用笔记

### 7.8 音频生成（`/user/audio`）
- 提交文本转语音任务（指定语速、背景音类型、噪音因子），异步执行
- 查询任务状态与结果链接
- 分页查询当前用户的任务列表

### 7.9 访客公开笔记（`/guest/note`）
- 无需 JWT 分页查询公开笔记，支持关键词、主题和分页参数
- 查看公开笔记详情，返回主题、标签、关联图片、双链引用，以及转换后的目录 HTML 和正文 HTML

---

## 八、管理端接口概览（`/admin/**`）

所有管理端接口需要管理员或创建者角色 JWT。

### 8.1 用户管理（`/admin/user`）
- 管理员登录、退出登录
- 获取当前用户信息（`GET /me`）、按 ID 查询用户（`GET /user`）、分页查询用户列表（`POST /list`）
- 新增（`POST /user`）/ 修改（`PUT /user`）/ 批量删除（`DELETE /user`）
- 封禁 / 解封账号（`POST /status/{status}`）
- 修改用户信息时需通过 `@RequireSuperiorRole` 角色层级校验

### 8.2 主题管理（`/admin/topic`）
- 分页查询主题、按 ID 查询主题详情
- 批量删除主题（存在引用即拒绝）

### 8.3 标签管理（`/admin/tag`）
- 分页查询标签、修改标签名称、批量删除标签

### 8.4 图片管理（`/admin/image`）
- 分页查询图片、修改图片元信息
- 设置图片公开状态、迁移图片到云存储
- 查询图片关联的笔记、批量删除图片（存在引用即拒绝）
- 审核图片（已合并到统一审核流程，开发时不建议直接调用）

### 8.5 笔记管理（`/admin/note`）
- 分页查询笔记、查询笔记详情
- 修改笔记元信息、批量删除笔记
- 转换 Markdown 为 HTML、删除转换缓存
- 获取笔记 Markdown 源内容、打开他人笔记
- 强制设置笔记状态（预留接口，当前未实现）

### 8.6 审核管理（`/admin/audit`）
- 分页查询笔记 / 主题-标签 / 图片审核记录（按状态、申请人筛选）
- 批量审核笔记 / 主题-标签 / 图片（通过或拒绝，需附拒绝原因）

### 8.7 邮件管理（`/admin/email`）
- 发送自定义邮件（单用户或按角色群发，支持纯文本与 HTML）

### 8.8 音频任务管理（`/admin/audio`）
- 分页查询全平台音频任务列表

---

## 九、音频生成业务架构

音频生成采用 **Java（生产者）→ MySQL（任务兜底）→ Redis Streams 或 RabbitMQ（队列派发）→ Python FastAPI（消费者 / 音频引擎）→ Nginx（资源代理）** 的异步链路。`AUDIO_QUEUE_TYPE` 选择 `redis-stream`（默认）或 `rabbitmq`，同一 Java 实例只启用一种实现。

> 使用 docker 部署的时候需要将 `/data/drills/audio/` 挂载到宿主机，并配置 Nginx 静态资源代理。
> 然后将 Redis 的端口 6379 映射到宿主机，并配置 Redis 密码（建议使用强密码）。（或者使用 Cloudflare 的 Tunnel 的端口映射方式）

### 9.1 流程概览

1. 用户调用 `POST /user/audio/generate` 提交文本、语速、背景音类型、噪音因子。
2. Java 端落库 `audio_tasks`（状态 `0-待处理`），将精简任务（`taskId / attempt / userId / speed / noiseType / noiseFactor / text`）推入所选队列。新任务 `attempt=0`，每次自动超时重试递增。
3. Python 消费者从当前启用的 Redis Consumer Group 或 RabbitMQ Queue 读取任务：
   - 通过 `POST /common/audio/callback/start`（携带 `X-Callback-Token` 头）回传 `taskId + attempt`，通知 Java 本轮任务进入处理中（状态 `1`）；
   - 调用 Edge-TTS 生成人声 → 通过 FFmpeg `volume` 滤镜挂载 `noiseFactor` 后与背景噪音混音 → 输出 `final_{taskId}.mp3`；
   - 将文件移至 Nginx 暴露目录（如 `/data/drills/audio/`）；
   - 通过 `POST /common/audio/callback/finish` 原样回传 `attempt` 并提交结果（成功 `2` / 失败 `-1`）；
   - 若 Java 响应 `data == false`，Python **必须立即 `os.remove` 删除本地文件**回收空间。
4. 用户轮询 `GET /user/audio/status/{taskId}` 获取最终状态与下载链接。

### 9.2 回调安全机制

回调接口（`/common/audio/callback/**`）需要在请求头携带 `X-Callback-Token`，与配置项 `jacolp.audio.callback-token` 匹配，防止匿名伪造回调。同时在网关 / 防火墙层面限制仅内网来源可访问。

### 9.3 背景音类型字典

| 标识符 (`noiseType`) | 展示名称 | FFmpeg 底层滤波参数 |
| --- | --- | --- |
| `PURE` | 纯净（无背景音） | （空，跳过混音） |
| `WHITE_NOISE` | 白噪音 | `anoisesrc=c=white:r=44100` |
| `PINK_NOISE` | 粉色噪音 | `anoisesrc=c=pink:r=44100` |
| `BROWN_NOISE` | 褐噪音 | `anoisesrc=c=brown:r=44100` |
| `CAFE` | 咖啡厅 | `anoisesrc=c=pink:r=44100,lowpass=f=2000` |
| `AIRPORT` | 飞机场 | `anoisesrc=c=brown:r=44100,lowpass=f=500,tremolo=f=0.5:d=0.3` |
| `SUBWAY` | 地铁车厢 | `anoisesrc=c=brown:r=44100,lowpass=f=300,tremolo=f=4.0:d=0.8` |

### 9.4 参数范围

| 参数 | 类型 | 范围 | 默认 / 说明 |
| --- | --- | --- | --- |
| `speed` | Float | `0.5 ~ 3.0` | 常用：1.0 / 1.25 / 1.5 / 2.0；Python 端会转换为 Edge-TTS 的 `--rate` 值 |
| `noiseFactor` | Float | `0.0 ~ 2.0` | 默认 `0.5`，建议 0.2 ~ 0.5，避免盖过人声 |

### 9.5 任务状态

`0`-排队中、`1`-合成中、`2`-已完成、`-1`-失败、`-2`-已由新任务重试、`-3`-已取消。

Python 对接以 [2026-08-09 最新对接文档](static/document/python-audio-module/音频模块-Python服务器对接文档-20260809.md)为准；完整业务接口见 [音频生成业务接口规范](static/document/python-audio-module/音频生成业务接口规范.md)。

---

## 十、核心架构要点

### 10.1 路由与鉴权
- 显式 stateless `SecurityFilterChain` 使用 admin、user 与 activation 三类 Filter；`/admin/**` 与 `/user/**` 的公开路径、401 JSON、JWT/Redis 单 Token 与 QPS 合同保持兼容。
- `/guest/**` 为公开访问入口，用于访客阅读公开笔记。
- Filter 解析 JWT 后将身份与 authority 写入 `SecurityContext`；`BaseContext` / `PermissionContext` 优先从 holder 读取，并保留 legacy fallback。

### 10.2 角色层级

- 当前角色：CREATOR、ADMIN、USER。
- 目标角色等级以 `sys_role.rank` 为准；数值越小等级越高，不依赖角色 ID。
- 当前 `@RequireSuperiorRole` / `SuperiorRoleAspect` 仍使用 legacy `roleId` 比较；计划在 Phase 5 切换为 `rank` 比较。

### 10.3 审核工作流
`isPass` 字段：`0`-待审核、`1`-已通过、`2`-已拒绝。

### 10.4 软删除策略
- `biz_note` 及其映射表（`biz_note_tag_mapping`、`biz_note_image_mapping`、`biz_note_each_mapping`）使用 `is_deleted` 字段（`0`-正常，`1`-已删除）。
- `biz_topic`、`biz_tag`、`biz_image` 为物理删除。
- `sys_user` 通过 `status` 控制账户状态（`2`-未激活、`1`-正常、`0`-禁用、`-1`-已删除）。

### 10.5 存储配额（`@StorageHandler`）
- UPLOAD / MODIFY：先做一致性查询（笔记 + 图片实际占用），再配额校验，业务执行后写回。
- DELETE / BATCH_DELETE：业务代码通过 `StorageUpdateContext`（ThreadLocal）传递删除的字节数，由切面统一更新。
- 使用用户级 `ReentrantLock`（ConcurrentHashMap 实现，300ms 超时），`CleanAspectLockTask` 周期清理空闲锁。

### 10.6 关键 AOP 注解

| 注解 | 切面 | 用途 |
| --- | --- | --- |
| `@StorageHandler(operationType)` | `StorageHandlerAspect` @Order(2) | 存储配额校验与更新（UPLOAD / MODIFY / DELETE / BATCH_DELETE） |
| `@ImageLimit` | `ImageLimitAspect` @Order(1) | 图片大小（5MB）、格式（jpg/jpeg/png/gif/webp/svg/bmp）、非空校验 |
| `@NoteFileLimit` | `NoteSizeLimitAspect` @Order(1) | 笔记文件大小（300KB）、格式（`.md`）校验 |
| `@RequireSuperiorRole` | `SuperiorRoleAspect` | 角色层级校验 |
| `@RequireValidRole` | `RoleValidationAspect` | 角色存在性与数据完整性校验 |

### 10.7 ThreadLocal 上下文

| 类 | 用途 | 生命周期 |
| --- | --- | --- |
| `BaseContext` | 当前登录用户 ID | 认证 Filter 写入的 `SecurityContext` holder 优先；兼容 legacy fallback |
| `StorageUpdateContext` | 业务代码向切面传递存储变更数据（`Map<Long, Long>`） | 切面 `finally` 中清除 |
| `NoteImageResolveContext` | 笔记图片解析时的当前笔记 ID | 业务方法内清除 |

### 10.8 定时任务

| 任务 | 默认周期 | 用途 |
| --- | --- | --- |
| `AliyunOSSClientKeepLiveTask` | 45 s | 阿里云 OSS 客户端保活；默认关闭，通过 `OSS_KEEP_LIVE_ENABLED=true` 显式启用 |
| `ImageDeleteTask` | 60 min | 处理图片删除死信队列 |
| `NoteCleanupTask` | 24 h | 物理清理已软删除的笔记映射记录 |
| `CleanAspectLockTask` | 60 min | 清理存储切面中的空闲用户锁 |

---

## 十一、详细接口文档

`static/document/` 目录提供按端拆分的详细接口规范：

- [admin 端接口.md](static/document/admin%20端接口.md)
- [user 端接口.md](static/document/user%20端接口.md)
- [guest 端接口.md](static/document/guest%20端接口.md)
- [Python 音频模块文档索引](static/document/python-audio-module/README.md)
- [音频模块 Python 服务器对接文档（2026-08-09）](static/document/python-audio-module/音频模块-Python服务器对接文档-20260809.md)
- [音频生成业务接口规范.md](static/document/python-audio-module/音频生成业务接口规范.md)

---

## 十二、安全建议

1. 生产环境**严禁**使用默认管理员账号密码（`jacolp.admin.username` / `jacolp.admin.password`），开发环境建议使用强账号密码。
2. JWT 密钥、数据库密码、SMTP 凭证、OSS AccessKey、Cloudflare Account ID 均通过环境变量或密钥管理服务下发，避免明文入库或提交到版本控制。
3. 数据库运行账号按最小权限原则授权，禁用 DROP / GRANT 等高危权限。
4. 音频回调接口（`/common/audio/callback/**`）需同时依赖 `X-Callback-Token` 请求头验证与网络层访问控制。
