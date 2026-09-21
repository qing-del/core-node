# Codex Goal Prompt｜Document v0.3

<aside>
🚦

**两条最高优先级执行规则**

1. **每次完成一个小而独立、可以验证的改动，就立即做一次 Git Commit。** 不要把多个阶段、多个不相关修改或整套重构堆成一个大 Commit。
2. **遇到任何会影响架构、兼容性、一致性、安全、表结构、基础设施或公开协议的不确定事项时，将当前目标标记为 `BLOCKED`，停止该目标继续实现并等待我的确认。** 不允许自行猜测关键决策；其他不依赖该 blocker 的目标可以继续。
</aside>

# Goal

在当前 `middleware` 项目中，按照父页面 [文档模块重构 v0.3｜Codex Implementation Plan](%E6%96%87%E6%A1%A3%E6%A8%A1%E5%9D%97%E9%87%8D%E6%9E%84%20v0%203%EF%BD%9CCodex%20Implementation%20Plan%203c2d548d4cf581c2834dc37f805c5bd3.md) 实施新的 `document` 模块。

该 v0.3 页面是当前实现的**唯一架构与一致性基线**。如果更早的草案、旧架构图、旧代码注释与 v0.3 冲突，以 v0.3 为准；但**不要删除旧模块、旧接口、旧表或大规模重写旧代码**。

目标是采用并行演进方式，在旧模块继续可运行的情况下，新建并逐步实现新的 Document Core。

# 开始前必须执行 Phase 0

先完整阅读 v0.3 Implementation Plan，然后只做仓库勘察，确认：

- Maven / Spring Boot 模块和 package 结构
- 旧 note / document 模块边界
- 数据库表、MyBatis / Repository、migration 规范
- Redis 现有封装
- RabbitMQ 队列、延迟消息、retry / DLQ 现有实现
- MinIO / OSS 现有封装
- WebSocket 现有实现
- Spring Security / JWT / Gateway 鉴权方式
- Result DTO / ErrorCode 规范
- Elasticsearch 当前 index、client、mapping 与查询封装
- 前端编辑器技术栈及 Yjs Binding 可用性
- 前后端仓库关系
- Node.js / TypeScript 服务是否已有部署惯例

Phase 0 的目标不是重新设计架构，而是确认 v0.3 方案应该如何适配当前代码库。

若 Phase 0 没有阻塞 Phase 1 的关键问题，可以直接开始 Phase 1，无需再次询问是否继续。

# 强制工程规则

## 1. 不删除旧模块

不得为了 v0.3：

- 删除旧 note / document 模块
- 删除旧 Controller / Service / Mapper
- 删除旧数据库表
- 大范围重命名旧 package
- 修改旧接口语义以迁就新模块

新 `document` 模块先独立演进。旧数据第一版不迁移，旧文档继续走旧链路，新文档走新模块。

## 2. 小改动即 Commit

**这是强制规则，不是建议。**

每完成一个小而独立、可以单独验证和回滚的修改，就执行：

1. 查看 `git diff`
2. 运行与本次修改有关的测试 / 编译检查
3. 确认没有误改旧模块
4. `git commit`
5. 再继续下一项修改

不要等待整个 Phase 完成才一定提交；如果一个 Phase 内自然拆成多个独立改动，应拆成多个 Commit。

Commit message 应简洁表达修改目的，例如：

```
feat(document): scaffold document module
feat(document): add document op log model
feat(document): add redis pending update repository
feat(document): add websocket binary codec
feat(document): add durable update flush
feat(document): add snapshot compaction
```

## 3. 不确定即 BLOCKED

**这是强制规则，不是建议。**

只要不确定事项会影响：

- 数据一致性
- 安全或权限边界
- 表结构
- 对外 HTTP / WebSocket 协议
- RabbitMQ / Redis / MinIO / ES 基础设施接法
- 模块结构
- 与旧系统兼容性
- Node 服务部署方式
- 前端编辑器 Binding

就停止当前目标，标记：

```
BLOCKED: <当前目标>

已确认：
- ...

无法从仓库确认：
- ...

为什么会影响实现：
- ...

可选方案：
A. ...
B. ...

推荐：
- ...

等待用户确认后继续当前目标。
```

不要自行选择关键方案。

但不要因为普通变量命名、局部代码风格、可以直接遵循仓库现有规范的问题频繁阻塞。

# 已冻结的核心架构，不得重新设计

以下内容已经在 v0.3 冻结：

- CRDT 使用 **Yjs**
- 前端根结构使用 `Y.Doc + Y.XmlFragment('content')`
- Java 不解析 `Y.XmlFragment`
- Java 不实现 Yjs Binary Protocol
- Java 不手写 CRDT merge
- 实时通信只使用 WebSocket，不使用 SSE 参与协同
- WebSocket Text Frame + JSON 用于控制协议
- WebSocket Binary Frame 用于 Yjs State / Update / Awareness
- Client Update 必须 Redis `XADD` 成功后才能 ACK 和 broadcast
- Redis 不保存完整正文，只保存 Room Meta + pending Yjs Updates
- Redis pending Update 先通过 **FLUSH_LOG** 转存 MySQL `document_op_log`
- **FLUSH_LOG 与 COMPACT 分离**
- COMPACT 使用 Yjs Merge Service 合并 `MinIO base snapshot + MySQL op_log`
- MinIO Snapshot 使用 immutable object，不覆盖旧对象
- MySQL CAS 决定当前有效 Snapshot Pointer
- 长期 Snapshot Watermark 使用 `document_op_log.id / persisted_log_id`
- Redis Stream ID 只承担 Redis -> MySQL cutoff / 幂等，不作为跨 Room 生命周期版本号
- Yjs Merge Service 是独立、无状态的 TypeScript + Yjs 服务
- RabbitMQ 只负责调度，不携带正文、Snapshot 或 Update List
- JOIN / reconnect 使用 `MinIO Snapshot + MySQL op_log + Redis pending update` bootstrap
- Java 不 merge bootstrap，客户端通过 `Y.applyUpdate()` 恢复
- CLOSED 必须完成 final FLUSH_LOG + final COMPACT 后才允许清理 Room runtime
- Awareness / Cursor / 在线状态不持久化
- 正确性依赖 Immutable MinIO + MySQL CAS；Redis Lock 只能作为减少重复 Merge 的优化

# 已确认的仓库适配：第一版个人文档域

仓库目前没有 Team 实体、成员关系、当前 Team 上下文或 Team 权限校验能力。v0.3 第一版
保留 `team_id` / `teamId` 字段，但其值必须由服务端的 `CurrentPrincipal.userId` 派生，等同于
`owner_user_id`：

- 客户端不得传入、选择或覆盖 `teamId`。
- 所有 HTTP、WebSocket、Redis Room Meta 与 Elasticsearch 查询均以当前用户 ID 作为 scope，
  仅允许访问 `document.team_id == currentPrincipal.userId` 的文档。
- 文档中的“Team / 当前 Team / teamId”在没有特别声明时均指这一个人 scope。
- 后续接入正式 Team 模式前，必须先实现成员校验与当前 Team 解析，并按 Blocker 规则确认
  个人文档的迁移方案；不得直接放松过滤条件。

# Relation 已冻结规则

双链节点使用正式 `resource-ref`，至少包含：

```tsx
interface ResourceRefAttrs {
  refId: string
  resourceId?: string
  resourceType?: string
  displayText: string
  alias?: string
}
```

规则：

- `refId` 标识正文中这个具体引用节点
- `resourceId` 标识目标资源
- CRDT `resource-ref` 是关系事实源
- `document_relation` 只是 Projection
- Relation 状态：`ACTIVE / UNRESOLVED / BROKEN / DELETED`
- ES 补全命中并选择目标后，bind 携带明确 targetId / resourceType
- ES 无匹配时允许建立 `UNRESOLVED` relation：targetId/resourceType 为空
- 用户删除 `resource-ref` 节点后调用 deleteBind，采用软删除，状态变为 `DELETED`
- 目标资源被删除时不是 `DELETED`，而是 `BROKEN`
- Rebind 第一版主要由后续 Document 关联管理界面触发
- Rebind 最终必须修改 CRDT 中的 resource-ref，而不是只修改 Relation Table
- COMPACT / Materialize 阶段允许通过 `resourceRefs` 对 Relation Projection 做 reconcile

# Elasticsearch 已冻结规则

ES 必须支持统一的 Document SearchEntity，至少具有：

```
documentId
teamId
title
tag[]
context
deleted
metaVersion
contextVersion
lastModifyTime
```

综合搜索要求：

```
title   boost ≈ 4
tag     boost ≈ 2
context boost ≈ 1
```

搜索必须包含当前 team / 权限范围过滤，不能跨团队泄漏数据。

同时需要双链补全接口，补全排序优先：

```
title exact
> title prefix
> title fuzzy
> tag 辅助
```

不要因为正文 context 中普通出现关键词，就优先于标题高度相关的文档。

Meta Projection 和 Context Projection 对 ES 只能做各自字段的 partial update，不允许拿旧的完整 SearchEntity 相互覆盖。

`context` 由 Yjs Merge Service 在 COMPACT / materialize 时从 Yjs 文档物化生成，Java 不解析 Yjs 正文。

# 配置规则

沿用 middleware 当前配置风格，不为新 document 模块主动增加环境变量依赖。

Spring 配置可以继续引用：

```yaml
spring:
  datasource:
    url: jdbc:mysql://${jacolp.datasource.host}:${jacolp.datasource.port}/${jacolp.datasource.database}
    username: ${jacolp.datasource.username}
    password: ${jacolp.datasource.password}

  data:
    redis:
      host: ${jacolp.redis.host}
      port: ${jacolp.redis.port}
      password: ${jacolp.redis.password}

  rabbitmq:
    host: ${jacolp.rabbitmq.host}
    port: ${jacolp.rabbitmq.port}
    username: ${jacolp.rabbitmq.username}
    password: ${jacolp.rabbitmq.password}
```

实际环境值通过：

```
application-home.yml
application-dev.yml
application-prod.yml
```

或项目已有 Profile / 配置机制调整。

新配置优先使用：

```
jacolp.elasticsearch.*
jacolp.minio.*
jacolp.yjs-merge-service.*
jacolp.document.*
```

已确认 Elasticsearch 与 MinIO 必须以框架层通用自动配置提供，遵循现有 Aliyun OSS 的
`autoconfigure + starter` 模式。自动配置只负责创建通用 Client，不能知道 document
业务；Document 模块只能注入这些 Client，不得自行重复创建连接。`jacolp.minio.bucket.*`
与 `jacolp.elasticsearch.index.*` 是通用的逻辑资源名映射，Document 分别使用
`bucket.document` 与 `index.document`。

配置层级固定为：`jacolp.minio.*` 仅包含 MinIO 连接与 bucket 映射，
`jacolp.elasticsearch.*` 仅包含 Elasticsearch 连接与 index 映射，
`jacolp.yjs-merge-service.*` 与 `jacolp.document.*` 均为 `jacolp` 的一级子树，
不得错误嵌套在 `jacolp.minio.*` 下。

Redis / RabbitMQ / Datasource 已有配置必须复用，不为 document 重复创建第二套连接配置。

如果项目已有环境变量 fallback，可以保留兼容，但不要主动给新配置增加新的环境变量依赖。

Secret 不允许硬编码，遵循项目当前 Secret 处理方式；无法确认时 BLOCKED。

# 实施顺序

按照父页面 v0.3 的 Phase 顺序实施：

```
Phase 0  仓库勘察
Phase 1  document 模块骨架
Phase 2  MySQL / Redis 数据模型
Phase 3  Yjs Merge Service + Java Client
Phase 4  WebSocket Room + Bootstrap
Phase 5  Update Ingest + ACK
Phase 6  FLUSH_LOG
Phase 7  COMPACT + MinIO CAS
Phase 8  Room PRE_CLOSE / CLOSE
Phase 9  Frontend Yjs minimal integration
Phase 10 Integration / Regression / Observability
```

Relation 与 ES 在其依赖的基础能力完成后按 v0.3 对应章节逐步加入，不允许跳过核心持久化链路直接构建第二套事实源。

# 每次修改后的最低检查

根据改动范围至少执行能够执行的：

- compile / build
- unit test
- repository test
- integration test
- WebSocket protocol test
- duplicate update test
- Redis -> DB crash-window test
- COMPACT CAS race test
- CLOSE reopen race test
- 两客户端 Yjs convergence test
- 旧模块 regression test

测试失败不得以“后续再修”为理由直接提交。

# 工作方式

开始后按以下方式持续执行：

1. 阅读完整 v0.3 Implementation Plan
2. Phase 0 勘察仓库
3. 汇报：可复用能力 / 需要新增能力 / 差异 / blocker
4. 没有关键 blocker 时直接开始实现
5. **每完成一个小而独立的改动立即 Git Commit**
6. **出现关键不确定事项立即将当前目标标记为 BLOCKED，等待我的确认**
7. 其他无依赖目标可以继续
8. 按依赖顺序持续推进，不需要每个普通步骤都询问我是否继续

# 最终输出

每次阶段性汇报或最终完成时列出：

- 已完成目标 / Phase
- Git Commit hash + message
- 新增或修改文件
- 新增/修改表结构
- Redis Key
- RabbitMQ Queue / Message
- MinIO Object
- Yjs Merge Service
- WebSocket Protocol
- Relation
- Elasticsearch
- 测试结果
- 当前 Blocker
- 剩余 TODO

不要重新设计 v0.3 已冻结内容。优先完成正确、可恢复、可验证的第一版实现，而不是提前为未来规模增加复杂架构。
