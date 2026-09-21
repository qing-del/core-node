# 文档模块重构 v0.6｜单例 LINK 绑定方案

> 本文只冻结单例 Java 服务、单例 Redis 下的 WebSocket `LINK`、Binding Stream、Rebind 边界，以及 Yjs 与 DB 的双写边界。不修改业务代码、不新增数据库迁移。

## 1. WebSocket `LINK` 与双队列

### 1.1 `LINK` 协议

v0.6 使用统一的 WebSocket `LINK` 二进制帧：

```text
18 字节外层头
+ 31 字节 BindingEnvelope
+ raw Yjs update
```

固定布局：

```text
0       1 byte   protocolVersion
1       1 byte   LINK = 0x06
2-17    16 byte  eventId / clientUpdateId(UUID)

18-21   4 byte   envelopeLength = 27
22      1 byte   schemaVersion = 1
23      1 byte   commandType          // BIND = 0x01，UNBIND = 0x02
24-39   16 byte  refId                // UUID 原始字节
40      1 byte   targetType           // DOCUMENT = 0x01
41-48   8 byte   targetId             // MySQL BIGINT，大端序

49...   raw Yjs update
```

```text
BindingEnvelope：31 字节
LINK 固定开销：49 字节
```

协议约束：

- `schemaVersion = 1` 是 v0.6 唯一格式。
- `envelopeLength` 表示 Envelope body 长度，不包含自身的 4 字节。
- `BIND`、`UNBIND` 都必须携带 `refId`、`targetType`、`targetId`。
- `refId` 对应正文中具体 `resourceReference` 节点的稳定身份。
- 不携带 `sourceDocumentId` 和数据库内部 `resourceNodeId`；`sourceDocumentId` 由 Room 和 Stream Key 推导。
- 前端候选目标只包含协作文档，`targetType` 使用 `DOCUMENT = 0x01`；LINK 不查询或校验目标资源是否存在、删除、有权限或实际 `ResourceType`。
- `BindingEnvelope + raw Yjs update` 不超过现有 `256 KiB` payload 限制，18 字节外层头不计入该限制。

### 1.2 前端绑定入口

```text
输入 [[关键词]
→ 查询协作文档标题
→ 返回候选列表
→ 用户选择目标文档
→ 创建 resourceReference
→ 生成或保留 refId
→ 一个 Yjs 事务生成一个 LINK
```

一次 LINK 操作使用一个显式事务意图：

```text
LinkIntent:
  commandType
  refId
  targetType
  targetId
```

前端约束：

- 补全列表只返回当前用户可访问且未删除的协作文档。
- v0.6 不处理图片、音频等媒体资源。
- 一个 Yjs 事务最多处理一个资源引用动作，并只产生一个 LINK。
- BIND 在同一 Yjs 事务中清理 `[[]` 残片并创建或更新 `resourceReference`。
- UNBIND 在同一 Yjs 事务中先读取 `refId/resourceType/resourceId`，再删除 `resourceReference` 并生成 LINK。
- 使用 Yjs transaction origin 或一次性事务上下文绑定 `LinkIntent`，不扫描 raw Yjs 或按时间窗口判断帧类型。
- 未选择目标文档时不发送 LINK，也不写入 Binding Stream。
- 新建引用使用 `crypto.randomUUID()`；修改已有引用时保留原 `refId`。
- 复制、粘贴或导入引用节点时生成新的 `refId`；生成后检查当前文档已有引用 ID，冲突时重新生成。
- 发出 LINK 前不得存在空的 `refId`。
- pending 队列保存原始帧类型、`BindingEnvelope`、raw Yjs update 和 `clientUpdateId/eventId`。
- 重连时重发原始 LINK，不重新判断更新类型。

### 1.3 服务端拆包与双队列

普通更新：

```text
CLIENT_UPDATE
→ updates Stream
→ Yjs Op Log / Compact / Snapshot
```

LINK 更新：

```text
LINK
├─ raw Yjs update  → updates Stream
└─ BindingEnvelope → binding Stream
```

队列约束：

- `updates` 只保存 raw Yjs update。
- `binding` 只保存 BindingEnvelope 二进制内容。
- 服务端只解码外层头和 BindingEnvelope，不解析 Yjs。
- Binding Consumer 不做目标资源及 `ResourceType` 业务校验，只维护当前文档的资源边。
- 广播给其他客户端时只发送 raw Yjs `CRDT_UPDATE`。
- 不得将完整 LINK payload 当作 Yjs 广播。
- LINK 使用一次 Redis Lua 脚本向两条 Stream 执行 `XADD`。

### 1.4 单例 Binding Consumer

每个协作文档使用一条 Binding Stream：

```text
document:bindings:{documentId}
```

消费流程：

```text
XRANGE Binding Stream
→ 按 Stream ID 升序读取
→ BindingCodec 解码
→ 按 (sourceDocumentId, refId) 归并
→ 生成 CSet / DSet
→ DB 事务写入
→ XDEL
```

单例约束：

- 只有一个活跃 Binding Consumer。
- 不使用 Consumer Group 和 `XACK`。
- `XRANGE` 返回的 `ByteRecord.getId()` 用于消息定位和 `XDEL`。
- 服务重启后继续读取尚未 `XDEL` 的消息。
- 不比较不同文档 Stream 之间的 ID。
- 每次 `XRANGE` 的数量上限与 CRDT Update Consumer 相同，当前默认值为 `500`；未读完时继续按 Stream ID 顺序读取。
- 数据库事务提交后才允许 `XDEL`；失败时保留消息重试。

批次归并：

```text
latest[(sourceDocumentId, refId)] = {
    commandType,
    targetType,
    targetId
}

CSet = latest 中 commandType = BIND 的 refId
DSet = latest 中 commandType = UNBIND 的 refId
```

- 同一批次内按 Stream 顺序处理，最后一条命令生效。
- 不存在的 UNBIND 只记录日志，按幂等成功处理，不创建墓碑。
- 非法 BindingEnvelope 进入失败处理。

CLOSE 时使用同一个单例 Binding Consumer 排空当前文档的 Binding Stream，不并行启动第二个消费者：

```text
CLOSE
→ Updates Stream 持续落库
→ 当前文档 Binding Stream 持续落库
→ Yjs Compact / Snapshot
→ 再次确认没有新会话
→ 清理 Room Meta、Updates Stream、Binding Stream
```

- Binding Stream 未清空或数据库事务失败时，不清理 Redis 运行态，保留消息重试。
- 只有 Updates 和 Binding 都处理完成后，才允许删除对应 Redis Stream。

### 1.5 资源节点和关系表

资源节点表：

```text
biz_resource_node
-----------------
id
resource_type
target_id
```

资源节点约束：

- 不建立 `UNIQUE(resource_type, target_id)`。
- 本轮不增加资源节点额外索引。
- `biz_resource_node` 按引用实例生成，不作为全局资源身份表。
- 不同文章或不同 `refId` 可以拥有不同的 `resource_node_id`。
- 同一 `(sourceDocumentId, refId)` 生命周期内复用原 `resource_node_id`。

关系表：

```text
biz_document_relation
---------------------
source_document_id
ref_id
resource_node_id
is_delete

UNIQUE(source_document_id, ref_id)
```

写入规则：

- 新 `refId`：创建资源节点和关系，设置 `is_delete = 0`。
- 已有 `refId`：复用资源节点，原地更新 `resource_type/target_id`，设置 `is_delete = 0`。
- UNBIND：按 `(sourceDocumentId, refId)` 设置 `is_delete = 1`，保留原 `resource_node_id`。
- 不存在的 UNBIND：记录日志并成功结束，不创建关系墓碑。
- 重试前先检查关系，避免重复创建资源节点。
- 不按 `(resource_type, target_id)` 对资源节点执行 Upsert。

`resourceReference` 是正文事实源；`biz_resource_node` 和 `biz_document_relation` 是查询投影。

### 1.6 `LINK_ACCEPTED`

两个 Stream 的 `XADD` 都成功后返回：

```json
{
  "type": "LINK_ACCEPTED",
  "documentId": 123,
  "clientUpdateId": "uuid",
  "updatesRedisOpId": "stream-id",
  "bindingRedisOpId": "stream-id",
  "status": "QUEUED"
}
```

```text
Lua 只保证同一 Redis 内两条 Stream 的原子入队，不保证 Redis 与 MySQL 的跨系统事务。
```

不存在的 UNBIND 不发送同步 `NOT_FOUND` 响应，Binding Consumer 记录日志并按幂等成功处理。

### 1.7 旧绑定接口

以下接口保留但标记为废弃，不删除：

```text
POST   /document/{sourceDocumentId}/relations/bind
DELETE /document/{sourceDocumentId}/relations/{refId}
```

v0.6 新客户端不得调用；新建和删除绑定统一使用 WebSocket `LINK`。

## 2. Rebind 接口

- 本轮不新增 Rebind 二进制帧。
- Rebind API 不改造。
- 同一 `refId` 的目标更新由 `LINK BIND` 覆盖资源节点。
- Rebind 与 Yjs 正文事实、关系投影之间的一致性后续处理。

## 3. Yjs 与 DB 双写边界

```text
raw Yjs
→ updates Stream
→ document_op_log
→ Compact / Snapshot
```

```text
BindingEnvelope
→ binding Stream
→ Binding Consumer
→ biz_resource_node / biz_document_relation
```

- Redis 到 MySQL Op Log 只保存 raw Yjs，不翻译 Yjs 内容。
- Binding Consumer 只翻译 BindingEnvelope，不解析 Yjs。
- `resourceReference` 是正文事实源，关系表和资源节点是可重建查询投影。
- `LINK_ACCEPTED` 只表示两条 Redis Stream 已入队。
- CLOSE 必须确认当前文档的 Binding Stream 已排空并完成 `XDEL`，再清理 Redis 运行态。

暂不处理：

```text
operationId 幂等
refVersion / 多实例
Rebind 一致性
媒体资源
```
