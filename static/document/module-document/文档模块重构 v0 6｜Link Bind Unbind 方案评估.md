# 文档模块重构 v0.6｜LINK 协议与 Binding Consumer 方案评估

> 本文只记录 WebSocket `LINK` 与双队列方案、Rebind 接口，以及 Yjs 与 DB 的双写边界。

## 1. WebSocket `LINK` 与双队列方案

### 1.1 更新分类

普通文档编辑使用 `CLIENT_UPDATE`，只进入现有 `updates` 队列，继续由原有链路负责 Yjs Op Log、Compact 和 Snapshot。

涉及创建或删除 `resourceReference` 的更新属于逻辑上的特殊 `CLIENT_UPDATE`，线上统一使用外层 `LINK` 帧。绑定还是解绑不再由外层帧类型区分，而由 `BindingEnvelope.commandType` 判断。

`LINK` 帧的 18 字节外层头保持固定：

```text
1 byte  protocolVersion
1 byte  LINK
16 byte eventId(UUID)
```

关联信息保留在 payload 的二进制 `BindingEnvelope` 中，逻辑结构为：

```text
18 字节外层协议头
+ BindingEnvelope（二进制关联信息）
+ raw Yjs update
```

`BindingEnvelope` 至少表达：

- `schemaVersion`
- `commandType`：`BIND` / `UNBIND`
- `operationId`
- `sourceDocumentId`
- `refId`
- `targetType`
- `targetId`
- `refVersion`

约束如下：

- `BIND` 必须携带有效目标资源；
- `UNBIND` 必须携带 `targetType`、`targetId` 快照，以便按关系唯一键直接执行解绑；
- 外层类型固定为 `LINK`，内部 `commandType` 是唯一动作来源；
- Envelope 必须具备长度边界或自描述能力，确保服务端能区分 Envelope 与 raw Yjs；
- 具体 TLV、CBOR 或其他二进制字节布局暂不冻结；
- 不能只把动作埋在 Yjs payload 中，否则服务端必须翻译 Yjs 才能分流。

### 1.2 服务端拆包与两条队列

服务端只拆分外层协议头和 `BindingEnvelope`，不解析 Yjs：

```text
普通 CLIENT_UPDATE
  → updates Stream
  → document_op_log.update_data
  → Yjs Compact / Snapshot

LINK
  → updates Stream：只写 raw Yjs update
  → binding Stream：只写 BindingEnvelope bytes
  → Binding Consumer
  → biz_document_relation
```

`updates` 队列只保存 raw Yjs，`binding` 队列只保存 BindingEnvelope 二进制内容，不保存需要再次解析的 Yjs。服务端向其他客户端广播时，只发送切出的 raw Yjs `CRDT_UPDATE`，不得把包含 Envelope 的完整 payload 当作 Yjs 广播。

前端清理 `[[]` 残片、创建 `resourceReference` 或删除节点，应在同一个 Yjs 事务中形成一个可关联的 Link 更新。前端 pending 队列需要保留帧类型、Envelope 和重试 ID，不能在重连时重新猜测更新类型。

### 1.3 Binding Consumer 批处理与单例顺序

Binding Consumer 的处理流程为：

```text
按 Binding Stream 顺序批量读取
  → BindingCodec 解码
  → 按关系键有序折叠
  → 生成 CSet / DSet
  → 批量执行关系表 Upsert
  → 数据库事务提交
  → XACK / XDEL
```

v0.6 采用单一活跃消费者顺序处理 Binding Stream。所有 BIND 和 UNBIND 都进入同一条 `document:bindings:{documentId}` Stream，Redis Stream ID 作为单例阶段的接收顺序；同一个关系键不并行处理。

集合不再使用两个集合的差集计算，而是按 Stream ID 升序对关系键进行有序折叠：

```text
for message in Binding Stream order:
    key = (source_id, resource_type, target_id)
    latest[key] = message.commandType

CSet = latest 中 commandType = BIND 的关系键
DSet = latest 中 commandType = UNBIND 的关系键
```

其中集合元素是关系键，而不是原始二进制消息。这样同一批次内的 `BIND → UNBIND` 最终进入 `DSet`，`UNBIND → BIND` 最终进入 `CSet`。跨批次消息依赖单例消费者的连续顺序和数据库当前状态继续处理。

消费者约束：

- v0.6 使用单一活跃消费者按 Stream ID 升序读取，并保留每条消息的 Stream ID；
- 按 `commandType` 翻译 Binding 二进制内容，不调用 `Y.applyUpdate`，不解析 Yjs；
- 使用 `operationId` 做重复消息幂等校验；
- 数据库事务提交后再确认和删除 Stream 消息；
- 非法 payload 进入失败重试或死信流程；
- 单例阶段只把 Stream ID 作为接收顺序，不将其解释为客户端真实操作意图；
- 跨不同 Stream 不假定存在全局顺序，关系归并至少按来源文档和关系键隔离。

后续多实例扩展只保留方向：引入 `refVersion` 作为业务顺序版本，关系表保存当前版本，消费者使用 `incoming.refVersion > current.refVersion` 的 CAS 条件更新；低版本消息直接确认，不重复重试。多实例通过 Consumer Group 扩展，同一个关系键不并行更新；如果客户端自增无法保证多写者唯一顺序，再由 Redis 或服务端生成版本。

### 1.4 关系表写入语义

关系表按边粒度建模，唯一键统一写作：

```text
(source_id, resource_type, target_id)
```

绑定使用幂等 Upsert，并将 `is_delete` 设置为 `0`：

```sql
INSERT ... ON DUPLICATE KEY UPDATE
  is_delete = 0
```

解绑同样使用 Upsert，并将 `is_delete` 设置为 `1`：

```sql
INSERT ... ON DUPLICATE KEY UPDATE
  is_delete = 1
```

单例阶段主要依赖 Binding Stream 顺序，不提前引入复杂版本冲突策略；后续启用 `refVersion` 后，两类操作都必须携带版本条件，不能让旧的 Bind 或 Unbind 无条件覆盖新状态。解绑时关系行不存在，也允许写入 `is_delete = 1` 的墓碑记录。

本方案假设同一来源到同一目标只保留一条关系，不区分多个 `resourceReference.refId`。如果未来需要按引用节点区分关系，则需要调整关系粒度或增加引用明细表，超出本次冻结范围。

### 1.5 Redis Lua 入队与 ACK 边界

对于 `LINK`，通过一次 Redis Lua 调用执行两个 `XADD`：

```text
XADD document:updates:{documentId}  ...raw Yjs update...
XADD document:bindings:{documentId} ...BindingEnvelope bytes...
```

Lua 只能保证两个 `XADD` 在 Redis 内执行期间不可交错，不保证 Redis 与 MySQL 的跨系统事务，也不保证脚本异常时两个写入全部回滚。Lua 负责双队列原子入队，不负责生成单例业务顺序版本。脚本成功后服务端返回 `LINK_ACCEPTED`，状态为 `QUEUED`，只表示两条任务都已进入 Redis，不表示关系表已经完成写入。

WebSocket Link 更新使用同一个 UUID 关联 `eventId`、`clientUpdateId` 和 `operationId`，以支持重试和幂等。两条队列分别消费、确认和删除；Binding Consumer 失败时保留任务并重试。

如果使用 Redis Cluster，两个 Stream Key 以及后续版本计数器必须通过共同 hash tag 放到同一个 hash slot；当前方案默认两条 Stream 使用同一个 Redis 实例。RabbitMQ Topic Exchange 暂不替换现有 Redis 更新链路。

## 2. Rebind 接口

保留 Rebind 能力，并继续使用独立的 HTTP/API，方便后续关系管理 UI：

```text
PATCH /document/{sourceDocumentId}/relations/{refId}/rebind
```

Rebind 以 `sourceDocumentId + refId` 定位关系，校验当前用户权限、目标资源可见性、资源类型白名单和版本冲突。

Rebind 不新增 WebSocket 二进制帧。它修改的是 `resourceReference` 对应的正文事实，关系表更新应复用同一套 Binding 投影语义；不允许将 DB 关系表作为独立事实源长期覆盖 Yjs。如果 API 先更新投影而 Yjs 更新失败，后续 reconcile 必须以 Yjs 为准修复。

## 3. Yjs 与 DB 双写问题

### 3.1 Yjs 存储链路不翻译

当前正文链路保持不变：

```text
Yjs CLIENT_UPDATE
  → WebSocket
  → Redis updates Stream
  → MySQL document_op_log.update_data
  → Compact
  → Yjs Merge Service
  → Snapshot
```

Redis 到 MySQL Op Log 只做二进制保存和对象映射，不翻译 Yjs 内容。`update_data` 仍是原始二进制；Java 到 Merge Service 的 Base64 只用于传输编码。

真正按 Yjs 语义处理的位置仍是 Merge Service 的 `Y.applyUpdate(...)` 和 `Y.encodeStateAsUpdate(...)`。Binding Consumer 的翻译仅针对 `BindingEnvelope`，不参与 Yjs 翻译链路。

### 3.2 双写边界

一次绑定包含两个结果：

```text
raw Yjs update       → resourceReference 正文事实
BindingEnvelope      → biz_document_relation 查询投影
```

`resourceReference` 仍是正文事实源，`biz_document_relation` 只是可重建的关系投影。两条 Redis Stream 可以通过 Lua 原子执行入队，但后续 Yjs Op Log 与关系表写入仍然是异步、最终一致的两个处理过程。

因此：

- `updates` 队列继续只服务 Yjs 持久化和 Compact；
- `binding` 队列只服务关系投影；
- Binding Consumer 只解析 Binding 二进制内容，不解析 Yjs；
- Link 命令重复、丢失或乱序时，依靠幂等、重试和后续 Yjs reconcile 修复；
- ACK 只承诺两条队列可靠入队，不承诺 `biz_document_relation` 已完成写入。

如果要求正文节点和关系表强一致，需要重新设计统一持久化事件包，超出当前 v0.6 增量方案范围。
