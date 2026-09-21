# Document v0.3｜实施决策记录

> 记录范围：本文件记录实现过程中无法由仓库或 v0.3 基线唯一确定、但为保证开发连续性而作出的决策。它是对 Implementation Plan 的补充，不替代该计划。
>
> 临时授权：用户于 2026-08-23 指示，在北京时间 2026-08-24 05:00 前，遇到不确定项不再阻塞；实施者应先在本文件记录推荐决策和影响，再按推荐方案继续。该授权到期后，恢复 Goal Prompt 中的「不确定即 BLOCKED」规则。

## D-001：第一版的文档归属范围

- **背景**：仓库不存在 Team、组织或成员关系模型，无法安全校验客户端给出的 `teamId`。
- **决策**：第一版采用个人文档域；服务端将 `teamId` 恒等于当前认证主体的 `owner_user_id`（`CurrentPrincipal.userId`）。客户端不得提交或覆盖该字段。
- **影响**：HTTP、WebSocket、Redis Room Meta、MySQL 查询和 Elasticsearch 读写都必须从已认证用户派生范围；所有者以外的用户不能访问文档。后续引入团队时，需要成员关系解析器，并明确 `team_id` 的迁移/兼容策略，不能直接开放客户端传参。
- **依据**：用户于 2026-08-23 确认。

## D-002：MinIO 与 Elasticsearch 的通用接入

- **背景**：仓库已有 OSS 风格的基础设施模块，但 MinIO 和 Elasticsearch 没有对应的通用自动配置；用户已提供本地 `jacolp` 配置。
- **决策**：新增 framework 层的 MinIO 与 Elasticsearch starter/auto-configuration，由业务模块按需注入客户端；业务模块不持有连接创建逻辑。
- **影响**：部署环境通过配置切换地址、凭证、索引和 bucket；本地 `application-home.yaml` 仅作运行配置，不纳入 Git。Elasticsearch Java Client 与集群使用的 8.17.x 保持兼容。
- **依据**：用户于 2026-08-23 要求按 OSS 风格模块通用化。

## D-003：Document 持久化表的第一版约束

- **背景**：现有仓库 MyBatis / migration 风格使用 MySQL 自增主键及 `datetime(3)` 审计时间，且更新日志需要支持 Redis 到 MySQL 的至少一次重放。
- **决策**：`biz_document` 和 `document_op_log` 使用自增主键；`document_op_log.client_update_id` 非空，并同时以 `(document_id, redis_op_id)`、`(document_id, client_update_id)` 建立唯一约束。
- **影响**：刷盘 worker 可在写库成功、删除 Redis Stream 条目失败后安全重放；同一客户端更新不会重复持久化。表定义同时写入初始化 SQL 与独立 migration，并由测试校验一致性。

## D-004：Redis 待刷盘数据的编码边界

- **背景**：Yjs update 是任意二进制，不能经过字符集转换；Redis 需要保存房间元数据和可恢复的待刷盘操作。
- **决策**：使用 Spring Data Redis 原始连接 API 保存 Stream 中的 `update` 字节；Room Meta 使用 `document:meta:{documentId}`，更新流使用 `document:updates:{documentId}`。Redis 不保存文档正文。
- **影响**：Java 层、后续 WebSocket codec 和 Yjs Merge Client 必须维持字节透明；持久正文只在快照阶段进入 MinIO。

## D-005：Yjs Merge Service 的仓库与部署边界

- **背景**：v0.3 要求独立、无状态的 TypeScript Yjs Merge Service，但当前仓库只有 Java 后端的 Docker/Compose 部署规范，没有既有 Node/TypeScript 服务约定。配置中的 Merge Service 地址指向数据中心机器 `192.168.31.100:3100`。
- **决策**：在本仓库创建 `../../../../services/yjs-merge-service` 作为独立 Node/TypeScript 服务，包含自己的 `package.json`、TypeScript 构建配置、测试和 Dockerfile；不修改根 `docker-compose.yml`，因为它描述的是现有 Java 应用部署，且 Merge Service 的目标运行位置是独立的数据中心机器。该服务以独立镜像/容器部署，Java 侧仅通过 `jacolp.yjs-merge-service.base-url` 调用。
- **影响**：服务只暴露 `POST /internal/yjs/merge`，输入/输出使用 Base64，服务不连接 MySQL、Redis 或 MinIO，也不实现权限、房间或 WebSocket。部署时需在数据中心机器为其提供 Node 运行时或容器运行时、端口 3100、健康检查与反向代理/网络访问；Java 侧 Base URL 必须按环境配置。
- **备选方案及未选原因**：不建服务、仅保留 Java client 会使快照合并流程无法运行；将服务加入根 compose 会把当前 Java 应用部署拓扑与数据中心存储服务拓扑耦合，且没有仓库依据支持这种耦合。
- **依据**：用户于 2026-08-23 授权在指定时限内按推荐方案继续，不再因该不确定项阻塞。

## D-006：浏览器 WebSocket 的 JWT 握手传递

- **背景**：仓库没有浏览器 WebSocket 的 JWT 鉴权样例。现有 access token 保存于前端本地存储，浏览器原生 `WebSocket` API 不能设置 `Authorization` 请求头；将 token 写入 URL query 会增加访问日志、代理日志和监控系统泄露凭证的风险。
- **决策**：`/ws/document` 的握手使用现有 RS256 access token，客户端通过标准 `Sec-WebSocket-Protocol` 头传递唯一子协议 `bearer.<JWT>`（浏览器代码形态为 `new WebSocket(url, ['bearer.' + accessToken])`）。服务端 `HandshakeInterceptor` 使用现有 `JwtDecoder` 验签、现有 JWT claims validator 和 `SecurityContextCurrentPrincipalAccessor.fromJwt` 生成 `CurrentPrincipal`，再将该主体写入 WebSocket attributes。它不接受 query token、Cookie token 或另一套 document token。
- **影响**：握手失败必须直接拒绝；每个 JOIN 都从握手主体导出个人范围并校验 `document.team_id == principal.userId`，客户端提交的身份/范围一律忽略。WebSocket endpoint 的允许 Origin 复用 `jacolp.web.cors.allowed-origin-patterns`，部署环境应将该现有配置收紧为实际前端域名；不新增另一套 Origin 配置。
- **兼容性**：这是浏览器侧将“同一份既有 JWT”传入握手的传输约定，而非新增登录或 token 签发协议。后续前端接入时仅需按上述子协议建连；本轮不修改 `../../../../frontend`。
- **依据**：用户于 2026-08-23 授权在指定时限内按推荐方案继续。

## D-007：`UPDATE_ACCEPTED` 的请求关联 ID

- **背景**：v0.3 同时规定“所有控制消息至少携带 `requestId`”，但 `UPDATE_ACCEPTED` 示例仅列出 `clientUpdateId` 和 `redisOpId`。CLIENT_UPDATE 是二进制帧，没有独立的 Text Frame `requestId`。
- **决策**：ACK 仍携带 `requestId`，并将它设置为 CLIENT_UPDATE header 中的同一个 UUID，即 `requestId == clientUpdateId`；同时保留 `clientUpdateId` 字段以表达其幂等语义。JOIN/LEAVE/SYNC 等 Text request 的响应使用其原始 `requestId`。
- **影响**：客户端对单次更新可以只用一个稳定 UUID 完成重发去重与 ACK 关联；服务端 codec 对所有控制帧统一要求 UUID requestId，避免出现两种相关性规则。
- **依据**：用户于 2026-08-23 授权在指定时限内按推荐方案继续。

## D-008：一个 WebSocket Session 的文档归属

- **背景**：v0.3 的 binary frame header 只有协议版本、frame type 和 event/client UUID，不携带 `documentId`。如果同一连接同时 JOIN 多个文档，服务端不能安全地把之后的 CLIENT_UPDATE 映射到正确 Redis Stream。
- **决策**：第一版一个 WebSocket Session 只允许 JOIN 一个 document。首次 `JOIN_DOCUMENT` 成功后，重复 JOIN 同一 document 幂等返回当前状态；尝试 JOIN 不同 document 返回 `ERROR`，客户端需要先 `LEAVE_DOCUMENT` 或新建连接。
- **影响**：每个 session 都有唯一的 `documentId` 上下文；二进制 CLIENT_UPDATE 可以由该上下文安全路由。前端若需要同时编辑多个文档，应使用多个 WebSocket 连接，不能复用一个连接多路复用。
- **依据**：用户于 2026-08-23 授权在指定时限内按推荐方案继续。

## D-009：`LEAVE_DOCUMENT` 的第一版语义

- **背景**：v0.3 定义了 `LEAVE_DOCUMENT` control type，但未定义 `LEAVE_ACCEPTED` 或其他响应帧。
- **决策**：收到合法 `LEAVE_DOCUMENT` 后，服务端立即移除该 session 对 Room 的归属；不额外发送 control ACK。连接保持打开，此后可 JOIN 另一文档；未 JOIN 时发送二进制更新会被拒绝。重复 LEAVE 是幂等 no-op。
- **影响**：不增加未冻结的公开响应类型；客户端以发送成功/本地状态为准，并可直接发送新的 JOIN。最后一个 session 离开后 Room 进入 PRE_CLOSE，实际延迟关闭仍由后续 final flush/compact 流程决定。
- **依据**：用户于 2026-08-24 授权窗口内按推荐方案继续。

## D-010：Document 调度的延迟队列实现

- **背景**：仓库已有可靠消息规范：持久化消息、主队列配套 `.retry` 队列（TTL 后回主队列）及 `.dlq`，消费者复用 `EventRetryPublisher`；但没有 RabbitMQ delayed-message 插件或既有 delayed exchange。`FLUSH_LOG` 需要约 2 秒的去抖延迟，且计划明确禁止在无既有约定时私自引入插件。
- **决策**：Document 模块复用现有 retry/DLQ 命名与消费失败处理，并额外采用 RabbitMQ 原生的固定 TTL + DLX：将仅包含 `DocumentScheduleMessage` 的 `FLUSH_LOG` 调度信号先投递到专用 delay queue；TTL 到期后死信转发到 document 主调度队列。第一版不引入 RabbitMQ 插件，也不让消息携带 Yjs 正文、Snapshot、Update List 或 Redis Entry List。
- **影响**：触发时间是近似值（TTL、outbox/publisher confirm 和 broker 调度会带来轻微延后），但消费者必须重新检查 Redis/MySQL 的真实状态，因此重复、延后及 Recovery Scanner 的补发均保持安全。消息发布失败不会撤销已经 XADD 成功的客户端 ACK；后续 Recovery Scanner 负责重新调度仍存在的 pending Stream。后续 `COMPACT` 与 `CLOSE` 复用同一主队列与 retry/DLQ，并按各自固定延迟增加独立 delay queue。
- **依据**：仓库 `ReliableMessagingConfiguration` 与 `EventRetryPublisher` 的既有实现；用户于 2026-08-24 05:00 前授权对未冻结项记录后按推荐方案继续。

## D-011：COMPACT 的触发与单轮合并边界

- **背景**：现有 `biz_document` 没有 `last_successful_snapshot_at`；`update_time` 会在每次接受编辑及 Snapshot pointer 切换时更新，不能可靠单独表示“距上次成功 Snapshot 的时间”。同时没有跨节点的 per-document 调度去重状态。计划已明确最终并发正确性依赖 immutable MinIO object + MySQL CAS，而非调度消息唯一性。
- **决策**：每次成功 `FLUSH_LOG` 后发送一个延迟 `COMPACT` 信号（使用 `jacolp.document.compact.interval-ms`）；若刚刷入的 batch 已达到 `max-unmerged-ops` 或 `max-unmerged-bytes`，则额外立即发送一个 `COMPACT` 信号。消费者始终重新检查 MySQL 真实日志；重复或并发 COMPACT 通过 Snapshot pointer CAS 收敛。单轮只读取从当前 `persisted_log_id` 起、最多 `flush-log.batch-size` 条有序 op_log，并以该批最后一条日志 ID 为 cutoff；剩余日志由后续调度继续压实。
- **影响**：连续编辑时会出现冗余调度或 loser merge，但不会覆盖 winner Snapshot，也不会提前删除日志；代价是额外 CPU/MinIO orphan object，后续可用 Redis lock 优化。重用已有 batch-size 避免把无限日志一次送入 Merge Service，限制单次 HTTP/内存占用；在默认值下，每轮最多合并 500 条。因没有单独的 Snapshot 时间字段，20 秒语义是“刷盘后延迟尝试”，而非精确的全局 Snapshot 周期。
- **依据**：`biz_document.update_time` 的数据库定义、计划 #13 的 CAS 正确性要求及用户于 2026-08-24 05:00 前的连续实施授权。

## D-012：跨实例 CLOSE 的在线 Session 判定

- **背景**：`DocumentRoomManager` 是 JVM 本地运行时容器，但 v0.3 目标允许 1~2 个 Java 实例；仅凭 CLOSE 消费节点本地 `sessionCount` 不能判断另一节点是否仍有 WebSocket Session。仓库没有既有 WebSocket presence/lease 规范，Redis Room Meta 也未保存全局在线数。
- **决策**：每个成功 JOIN 的 session 在 Redis 创建带 TTL 的 presence key：`document:presence:{documentId}:{instanceToken}:{sessionId}`。本实例定时续租本地仍在 Room 内的 key；LEAVE/连接关闭主动删除。CLOSE 消费时扫描该文档的 presence key，只有全局计数为零、`isClose=true` 且 closeToken 匹配时才能进入 final FLUSH_LOG/COMPACT 和清理。TTL 至少覆盖两个 close-delay 周期，续租周期作为 `jacolp.document.session-presence-refresh-ms`（默认 10 秒）配置。
- **影响**：实例崩溃时遗留 presence 会在 TTL 后自动消失，因此 CLOSE 最多延后一个 lease 周期，不会冒险提前清理。Redis 仍不存正文；presence 仅是可过期运行态。JOIN 会写入新的 closeToken 并将 `isClose=false`，失效旧 CLOSE 消息；final 流程在 flush/compact 后再次检查 token、presence 和本地 Room，避免 reopen race。该方案以保守延迟换取跨实例安全，后续若项目出现统一 session registry，应迁移复用。
- **依据**：计划 #11 要求 CLOSE 同时校验 `roomSessionCount(documentId)==0`、closeToken 和 reopen race；用户于 2026-08-24 05:00 前授权对未冻结项记录后继续实施。

## D-013：第一版 HTTP Meta 与活跃文档删除语义

- **背景**：仓库用户侧资源控制器统一使用 `/user/{resource}` 路径与 `BaseContext.getCurrentId()`；v0.3 明确 Meta 创建/读取/标题修改，但未冻结“有活跃 WebSocket Room 时删除”应强制断链还是拒绝。计划要求在正常规则下对此分支阻塞。
- **决策**：新增 `/user/document` 的 create、list、meta get、title patch、soft delete 接口；所有 scope 从认证用户 ID 推导，Request 不公开 `teamId` 或 Snapshot object key。删除前查询跨实例 Redis presence；只要仍有任一活跃 WebSocket session，则拒绝删除，不强制断开客户端，也不修改 CRDT/Redis/MinIO。presence 为零时按已有 `deleted=1` 逻辑删除。
- **影响**：删除操作的调用方需要先离开协作会话再重试，保证不会出现已删除 Meta 仍接收 Update 的语义分裂。第一版不提供直接正文 HTTP 读取，也不创建未冻结的 ES 搜索索引；正文恢复继续只走 WebSocket bootstrap。后续产品若需要“删除时踢出所有协作者”，需定义 WS 错误帧、跨节点广播和 UI 行为后再扩展。
- **依据**：仓库 `NoteController` / `UserImageController` 的路由与认证风格、v0.3 #8.4 的未冻结删除分支，以及用户于 2026-08-24 05:00 前的连续实施授权。

## D-014：Document Core 的指标注册与默认暴露边界

- **背景**：v0.3 #20 已列出 Document Core 必需指标；仓库 Server 已引入 Spring Boot Actuator / Micrometer，但基础 `application.yaml` 当前只对外暴露 health 端点，没有既有模块的 MeterRegistry 使用范式。
- **决策**：Document Biz 向现有 `MeterRegistry` 注册 `document_*` 与 `yjs_merge_service_duration` 指标，复用 Server 提供的 Actuator registry；不在本轮修改基础 profile 的 management endpoint exposure，不擅自公开 `/actuator/metrics`。生产环境若已有受控管理网关，可通过其现有 profile/部署配置开启 metrics 端点或 exporter。
- **影响**：Document 的计数器、Timer、DistributionSummary 和低基数全局 Gauge 会在应用内正常采集；`document_ws_sessions` / `document_active_rooms` 是单实例值，可由监控系统跨实例聚合。Redis pending / unmerged gauges 为读取当前 Document runtime keyspace 的实时聚合，不以 documentId 作为 metric tag，避免高基数。不会记录或导出任何 Yjs binary 正文。
- **依据**：仓库 `../../../../middleware-server/pom.xml` 的 `spring-boot-starter-actuator` 依赖、基础 profile 的 health-only exposure，及用户于 2026-08-24 05:00 前的连续实施授权。

## D-015：Document HTTP 与 WebSocket 的 OAuth Scope 准入

- **背景**：Server 不以笼统的 `/user/**` 匹配授权，而是由 `BusinessRouteScopeCatalogConfiguration` 枚举每条 Bearer HTTP 路由；新 `/user/document` 若未登记，会落在预期 scope 校验链之外。WebSocket upgrade 又不经过该 HTTP route catalogue。既有 WebSocket 单测已使用 `document:write`，但现有 RBAC 权限和 user client scope 尚未注册 document resource。
- **决策**：新增精确权限 `document:read`、`document:write`；Document Meta 的 GET/list 使用 `document:read`，创建、标题更新、删除使用 `document:write`。`/ws/document` 是可写协作通道，握手统一要求 `document:write`（或既有 `*:write`）；第一版没有只读 WebSocket 子协议。将两项 scope 写入 user client `scopes` / `auto_approve`、初始化库和前向 migration；不赋予 core_agent，也不新增未设计的 admin Document API。
- **影响**：旧 access token 不含 document scope 时会被拒绝，用户需按既有登录/刷新流程获取新 token；`*:read` / `*:write` 的既有角色兼容通配仍能匹配新精确 scope。Server 的可执行目录与受其约束的安全文档需要从 116 Bearer 路由同步扩展到 121 条，保证新增接口不会绕过认证。
- **依据**：`BusinessRouteScopeCatalogConfiguration`、`PermissionScopeMatcher`、`DocumentWebSocketHandshakeInterceptor` 以及用户于 2026-08-24 05:00 前的连续实施授权。

## D-016：Phase 9 使用 Tiptap + Yjs Binding，并复用自定义 WebSocket 协议

- **背景**：前端现有笔记编辑器是 CodeMirror Markdown，仓库没有可直接绑定 `Y.XmlFragment('content')` 的协同编辑器。`y-codemirror` 面向 `Y.Text`，不符合 v0.3 冻结的根节点结构；后端已提供带 JWT 子协议、bootstrap、ACK 与 awareness 语义的 `/ws/document` 自定义协议。
- **决策**：新协作文档页面使用 `Tiptap + @tiptap/extension-collaboration + Y.Doc + Y.XmlFragment('content')`。只使用 Tiptap/Yjs 的 Editor Binding；浏览器端实现轻量协议适配器，以既有 `Sec-WebSocket-Protocol: bearer.<JWT>` 连接 `/ws/document`，处理 JOIN、Snapshot、Bootstrap Update、CLIENT_UPDATE、ACK、Awareness 与断线重连。不得引入 Tiptap Collaboration Cloud、Hocuspocus 或 `y-websocket` 作为第二套网络事实源。
- **影响**：编辑器状态与 Yjs 文档之间由成熟 Tiptap Binding 维护，应用代码不手写 ProseMirror/Tiptap 同步算法；网络层只转发原始 Yjs update。所有权威内容恢复继续依赖现有 MinIO/MySQL/Redis bootstrap 链路，前端不会创建持久化正文副本。新增依赖需在前端 lockfile 中固定，并由前端构建验证。
- **依据**：用户于 2026-08-24 明确确认该选型与网络边界。
