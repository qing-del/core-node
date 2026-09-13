# 文档模块重构 v0.6｜单例 LINK 绑定方案

> 本文只冻结单例 Java 服务、单例 Redis 下的 WebSocket `LINK`、Binding Stream 消费、Rebind 边界，以及 Yjs 与 DB 的双写边界。不修改业务代码、不新增数据库迁移。

## 1. WebSocket `LINK` 与双队列

### 1.1 `LINK` 协议

v0.6 中，绑定协作文档资源不再使用 Bind/DeleteBind HTTP 接口，而是使用统一的 WebSocket `LINK` 二进制帧。绑定和解绑动作由 `BindingEnvelope.commandType` 区分，外层不再拆分为 `LINK_BIND`、`LINK_UNBIND`。

```text
18 字节外层头
+ BindingEnvelope
+ raw Yjs update
```

18 字节外层头固定为：

```text
1 byte  protocolVersion
1 byte  LINK = 0x06
16 byte eventId / clientUpdateId(UUID)
```

v0.6 的最小 `BindingEnvelope` 为：

```text
4 byte  envelopeLength       // big-endian，表示后续 Envelope body 长度；当前为 27
1 byte  schemaVersion        // v0.6 使用 2
1 byte  commandType          // BIND = 0x01，UNBIND = 0x02
16 byte refId                // UUID 原始字节
1 byte  targetType           // DOCUMENT = 0x01
8 byte  targetId             // MySQL BIGINT
```

因此，当前固定开销为：

```text
BindingEnvelope：31 字节
LINK 固定开销：18 + 31 = 49 字节
```

协议约束：

- 不将 `sourceDocumentId` 放入 Envelope；服务端从 WebSocket Room 和 `document:bindings:{documentId}` Stream Key 推导。
- `refId` 是正文中具体 `resourceReference` 节点的稳定身份；节点生命周期内不得更换。
- `BIND`、`UNBIND` 都必须携带有效的 `refId`、`targetType` 和 `targetId`。
- `UNBIND` 携带删除前 `resourceReference` 的目标字段快照，以便直接定位关系边。
- v0.6 只允许 `targetType = DOCUMENT`，只处理协作文档资源；图片、音频等媒体资源暂不纳入。
- `envelopeLength` 必须让服务端明确区分 Envelope 与后续 raw Yjs；后续可扩展字段仍受长度边界保护。
- `schemaVersion` 只描述 BindingEnvelope 的字段布局和解释方式；它不同于外层 `protocolVersion`、Redis Stream ID、Yjs 版本或业务版本。
- 新增必填 `refId` 后，v0.6 使用 `schemaVersion = 2`；未知或不兼容的版本必须拒绝，不得当作 raw Yjs 继续解析。
- `operationId`、`refVersion` 不参与当前 v0.6 的 Binding 处理：`operationId` 暂不用于 Binding 幂等，`refVersion` 作为后续版本控制预留。
- 不将数据库内部的 `resourceNodeId` 放入协议；它由 Binding Consumer 在数据库中生成和维护。
- 具体 TLV、CBOR 或其他完整二进制字节布局暂不冻结。
- `BindingEnvelope + raw Yjs update` 总大小不超过现有 `256 KiB` payload 限制；18 字节外层头不计入该限制，因此 raw Yjs 可用空间会相应减少。

### 1.2 前端绑定入口

前端只在完整的 `[[...]]` 资源引用节点完成解析并选择目标后创建绑定：

```text
输入 [[关键词
  → 按 title 使用 MySQL 模糊查询协作文档
  → 返回有限数量候选
  → 用户选择目标文档
  → 创建 resourceReference
  → resourceType = DOCUMENT
  → resourceId = targetId
  → refId = 稳定 UUID
  → 在同一个 Yjs 事务中生成 LINK BIND
```

搜索约束：

- 只查询协作文档；
- 只返回当前用户有权限访问且未删除的文档；
- 使用现有资源搜索中的 MySQL `title LIKE` 模糊匹配模式返回候选列表；
- v0.6 不包含图片、音频等媒体资源；
- 未选择目标文档时不发送 `LINK`，也不写入 Binding Stream。

删除资源引用节点时，前端必须先读取删除前的 `refId`、`resourceType`、`resourceId`，再在同一个 Yjs 事务中删除 `resourceReference` 并生成 `LINK UNBIND`。前端清理 `[[]` 残片、创建完整 `resourceReference` 或删除节点所产生的 raw Yjs update，与对应 BindingEnvelope 组成同一个逻辑 Link 更新。

前端 pending 队列必须保留原始发送信息：

```text
frameType
BindingEnvelope
rawYjsUpdate
clientUpdateId / eventId
```

重连时直接重发原始 `LINK`，不能根据 Yjs 内容重新猜测更新类型。

### 1.3 服务端拆包与两条 Stream

普通文档更新保持原有职责：

```text
CLIENT_UPDATE
  → updates Stream
  → Yjs Op Log / Compact / Snapshot
```

`LINK` 更新拆分为两条独立记录：

```text
LINK
 ├─ raw Yjs update     → updates Stream
 └─ BindingEnvelope    → binding Stream
```

队列边界固定为：

- `updates` 只保存 raw Yjs update，继续负责 `document_op_log`、Compact 和 Snapshot；
- `binding` 只保存 BindingEnvelope 二进制内容，不保存需要再次解析的 Yjs；
- 服务端只解码外层头和 BindingEnvelope，不调用 Yjs 解码或解析逻辑；
- 广播给其他客户端时只发送 raw Yjs `CRDT_UPDATE`；
- 不得将包含 Envelope 的完整 `LINK` payload 当作 Yjs 广播。

对于 `LINK`，服务端使用一次 Redis Lua 脚本分别向两条 Stream 执行 `XADD`。两条 Stream 默认位于同一个 Redis 实例；Redis Cluster 暂不属于 v0.6 运行模型。

### 1.4 单例 Binding Consumer

v0.6 只有一个活跃的 Binding Consumer，不使用 Consumer Group。每个协作文档使用同一条 Binding Stream：

```text
document:bindings:{documentId}
```

消费者按如下流程处理：

```text
XRANGE document:bindings:{documentId}
  → 按 Stream ID 升序批量读取
  → BindingCodec 解码 BindingEnvelope
  → 按关系键有序折叠
  → 生成 CSet / DSet
  → 在一个数据库事务中批量 Upsert
  → 事务提交成功后 XDEL
```

单例顺序约束：

- Redis Stream ID 表示 Redis 接收顺序；同一批次严格按 Stream ID 升序处理。
- `sourceDocumentId` 由 Stream Key 推导，不从 Envelope 读取。
- `XRANGE` 返回的 `ByteRecord.getId()` 是消费者处理和 `XDEL` 使用的消息 ID。
- 服务重启后继续读取尚未 `XDEL` 的消息；不同文档的 Stream ID 不比较，也不假设跨 Stream 存在全局顺序。
- 同一个关系键不并行处理。
- Stream ID 只表示 Redis 接收顺序，不表示客户端真实操作意图，也不承担业务版本号职责。

本模式不使用 Consumer Group，因此不使用 `XACK`。`XACK` 只用于确认 Consumer Group 的 Pending Entries，不会删除 Stream 记录；v0.6 在数据库事务提交后直接使用 `XDEL` 删除已处理记录。

按关系键进行有序折叠：

```text
for message in Binding Stream order:
    key = (sourceDocumentId, refId)
    latest[key] = {
        commandType,
        targetType,
        targetId
    }

CSet = latest 中 commandType = BIND 的 refId
DSet = latest 中 commandType = UNBIND 的 refId
```

同一批次内：

```text
BIND → UNBIND  => DSet
UNBIND → BIND  => CSet
```

跨批次消息依赖同一 Binding Stream 的连续顺序继续处理。当前不引入 `refVersion`、CAS 或其他复杂版本冲突规则。

### 1.5 Binding Consumer 的关系写入

关系表按正文引用节点粒度建模，逻辑唯一键为：

```text
(source_id, ref_id)
```

如果关系表只服务协作文档，物理字段采用 `source_document_id`；`source_id` 仅表示该逻辑字段。

资源节点表表示某个引用实例当前指向的目标：

```text
biz_resource_node
-----------------
id
resource_type
target_id
```

`biz_resource_node` 的约束：

- 不建立 `UNIQUE(resource_type, target_id)`；
- 本轮不新增资源节点的额外索引；
- 同一目标被不同文章或不同 `refId` 引用时，可以生成不同的 `resource_node_id`；
- 资源节点是引用投影实例，不是全局唯一的目标资源身份；
- 同一个 `(sourceDocumentId, refId)` 在生命周期内复用原有 `resource_node_id`；
- 资源节点和关系表都不是正文事实源。

关系表至少包含：

```text
biz_document_relation
---------------------
source_document_id
ref_id
resource_node_id
is_delete

UNIQUE(source_document_id, ref_id)
```

绑定操作的数据库语义：

```sql
创建关系：
  创建一个属于当前 sourceDocumentId + refId 的 resource_node
  创建关系并设置 resource_node_id、is_delete = 0

已有关系：
  复用原 resource_node_id
  原地更新 biz_resource_node.resource_type / target_id
  更新关系 is_delete = 0
```

同一 `refId` 的新 BIND 指向新目标时，v0.6 允许直接覆盖原目标；不创建新的 Rebind 二进制帧，也不为资源节点按 `(resource_type, target_id)` 执行 `ON DUPLICATE KEY`。

解绑操作的数据库语义：

```sql
已有关系：
  按 (sourceDocumentId, refId) 设置 is_delete = 1
  保留原 resource_node_id

不存在关系：
  根据 UNBIND 的目标快照最多创建一次 resource_node
  创建 is_delete = 1 的关系墓碑
```

重试时必须先按 `(sourceDocumentId, refId)` 判断关系是否存在，再决定是否创建资源节点，避免重复消息产生孤立节点。上述资源节点和关系更新都必须在同一个数据库事务内完成，事务成功后才 `XDEL` 对应消息；事务失败则保留消息等待重试。

Binding Consumer 只翻译 BindingEnvelope：

```text
BindingEnvelope bytes
  → BindingCodec
  → BIND / UNBIND 领域动作
  → biz_resource_node / biz_document_relation
```

消费者不调用 `Y.applyUpdate`，不解析 Yjs。`biz_document_relation` 是可重建的查询投影，`resourceReference` 是正文事实源。

`refId` 是当前关系的定位依据，不再只是展示字段。因此同一来源可以通过不同 `refId` 多次引用同一目标，删除其中一个 `resourceReference` 只会软删除对应的关系行。

### 1.6 `LINK_ACCEPTED` 与 Redis Stream ID

Lua 成功执行两个 `XADD` 后，服务端返回文本控制帧 `LINK_ACCEPTED`：

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

两个 Stream ID 的含义：

- `updatesRedisOpId` 是 updates Stream 中该 raw Yjs 消息的 ID；
- `bindingRedisOpId` 是 binding Stream 中该 BindingEnvelope 消息的 ID；
- ID 由 `XADD` 立即返回，也会在后续 `XRANGE` 中通过 `ByteRecord.getId()` 再次取得；
- 返回值主要用于即时 ACK、日志关联、排查和后续消费删除，不是 Binding Consumer 必须依赖的额外数据；
- 两个 ID 只在各自 Stream 内有序，不作为跨 Stream 全局版本，也不表示关系表已经写入。

`LINK_ACCEPTED` 的 `QUEUED` 语义仅表示两条 Redis Stream 已入队，不表示 `biz_document_relation` 已完成写入。

Lua 边界：

- 一个脚本执行两个 `XADD`，只能保证脚本在同一 Redis 内执行期间不可交错；
- 不保证 Redis 与 MySQL 的跨系统事务；
- 不保证脚本异常时两个写入全部回滚；
- Lua 不负责生成业务顺序版本，也不替代单例 Binding Consumer 的 Stream 顺序。

### 1.7 旧绑定接口

以下接口保留但标记为废弃，不删除：

```text
POST   /document/{sourceDocumentId}/relations/bind
DELETE /document/{sourceDocumentId}/relations/{refId}
```

约束如下：

- v0.6 之后新客户端不得调用；
- 新建和删除绑定统一使用 WebSocket `LINK`；
- 旧接口仅用于兼容历史客户端或后续迁移；
- 不新增新的 Bind/DeleteBind HTTP 接口。

## 2. Rebind 接口

Rebind 本轮暂不改造，继续保留现有独立 HTTP/API 能力，不新增 Rebind 二进制帧，也不冻结 Rebind 如何进入 Binding Stream。

同一 `refId` 的目标更新由 `LINK BIND` 直接覆盖对应 `biz_resource_node` 的 `resource_type/target_id`；该行为不新增 HTTP Rebind API，也不新增独立的 Rebind 二进制帧。

Rebind API 与 `resourceReference` 正文事实、关系投影之间的一致性处理仍标记为后续工作。现阶段不允许据此新增一套独立于 `LINK` 的绑定事实源。

## 3. Yjs 与 DB 双写边界

### 3.1 Yjs 链路不翻译

正文更新链路保持原有职责：

```text
raw Yjs update
  → updates Stream
  → document_op_log.update_data
  → Compact / Snapshot
```

Redis 到 MySQL Op Log 只保存和转移原始 Yjs 二进制，不翻译 Yjs 内容。Java 层的对象映射或 Base64 只属于存储/传输编码，不是 Yjs 语义翻译。只有 Compact / Merge 阶段才按 Yjs 语义应用和生成 update。

### 3.2 Binding 与正文的边界

```text
raw Yjs update
  → resourceReference 正文事实

BindingEnvelope
  → binding Stream
  → Binding Consumer
  → biz_resource_node / biz_document_relation 查询投影
```

两条 Stream 可以通过 Lua 原子入队，但后续 Yjs Op Log 与关系表写入仍是两个异步、最终一致的处理过程：

- `updates` 只服务 Yjs 持久化、Compact 和 Snapshot；
- `binding` 只服务关系投影；
- Binding Consumer 只翻译 BindingEnvelope，不翻译 Yjs；
- `biz_resource_node` 按引用实例保存目标类型和目标 ID，不承担全局资源去重；
- `resourceReference` 是事实源，`biz_document_relation` 是可重建投影；
- `LINK_ACCEPTED` 只表示两条 Redis Stream 已入队，不表示 MySQL 已完成。

### 3.3 v0.6 已知缺陷与生命周期约束

- LINK 当前不使用 `operationId` 做 Binding 幂等，重试可能产生重复 Binding 消息；
- `biz_resource_node` 不对 `(resource_type, target_id)` 做全局唯一约束；v0.6 接受资源节点重复，后续根据实际查询性能再评估索引和归并策略；
- Rebind 与 Yjs 正文事实、关系投影之间的一致性暂不处理；
- 当前单例方案使用 Stream 顺序，不提前冻结多实例版本冲突语义；后续可再引入 `refVersion + CAS`；
- `CLOSE` 流程在删除 Redis 运行时状态前，必须确认对应 Binding Stream 已消费并 `XDEL`，避免未处理绑定随运行时清理而丢失。
