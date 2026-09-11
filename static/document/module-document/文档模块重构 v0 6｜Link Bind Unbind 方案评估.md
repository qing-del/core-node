# 文档模块重构 v0.6｜Link_Bind / Link_Unbind 方案评估

> 本文只记录 WebSocket Link 更新与双队列方案、Rebind 接口，以及 Yjs 与 DB 的双写边界。

## 1. WebSocket Link 更新与双队列方案

### 1.1 更新分类

普通文档编辑使用 `CLIENT_UPDATE`，只进入现有 `updates` 队列，继续由原有链路负责 Yjs Op Log、Compact 和 Snapshot。

涉及创建或删除 `resourceReference` 的更新属于特殊 `CLIENT_UPDATE`：

- 线上的外层帧类型使用 `LINK_BIND` / `LINK_UNBIND`，服务端可以从协议头直接识别，不需要解析 Yjs；
- 原始 Yjs update 仍进入 `updates` 队列；
- 对应的结构化绑定命令进入新的 `binding` 队列；
- `Link_Bind` / `Link_Unbind` 控制命令本身不能写入 Yjs Op Log。

这里的“特殊 `CLIENT_UPDATE`”表示业务语义；`LINK_BIND` / `LINK_UNBIND` 表示线上外层帧类型。
不能只把 `BIND` / `UNBIND` 埋在 Yjs payload 中，否则服务端必须翻译 Yjs 才能分流。

### 1.2 两条队列

```text
普通 CLIENT_UPDATE
  → updates Stream
  → document_op_log.update_data
  → Yjs Compact / Snapshot

LINK_BIND / LINK_UNBIND
  → updates Stream：原始 Yjs update
  → binding Stream：结构化绑定命令
  → binding Consumer
  → biz_document_relation
```

绑定命令至少需要能够表达 `sourceDocumentId`、`targetDocumentId`、`refId`、`operationId`、`BIND / UNBIND` 和 `refVersion`。绑定队列不应保存需要再次解析的 Yjs 内容。

### 1.3 Redis Lua 入队

对于 `LINK_BIND` / `LINK_UNBIND`，建议通过一次 Redis Lua 调用执行两个 `XADD`：

```text
XADD document:updates:{documentId}  ...raw Yjs update...
XADD document:bindings:{documentId} ...binding command...
```

这样可以保证两个 `XADD` 在 Redis 内执行期间不会被其他请求插入。脚本成功后，服务端返回 `LINK_ACCEPTED`，状态为 `QUEUED`，表示两条任务都已进入 Redis 队列，不表示关系表已经完成写入。

Lua 不能提供 Redis 与 MySQL 之间的跨系统事务，也不会自动解决脚本中途错误或网络响应丢失后的重复投递。因此仍需：

- 使用 `operationId` / `clientUpdateId` 做幂等；
- 使用 `refVersion` 处理 Bind、Unbind、Rebind 的乱序；
- 两条队列分别消费、分别确认和删除；
- 绑定消费者失败时保留任务并重试。

如果使用 Redis Cluster，两个 Stream Key 必须通过共同 hash tag 放到同一个 hash slot；当前方案默认两条 Stream 使用同一个 Redis 实例。

### 1.4 RabbitMQ 边界

RabbitMQ Topic Exchange 可以一次发布并路由到多个队列，但要让它同时承载 Yjs `updates` 和绑定任务，需要整体改造当前 Redis 更新、Bootstrap、恢复和 Compact 链路。

因此 v0.6 暂不使用 RabbitMQ 替换现有更新队列。RabbitMQ 可以继续承担调度、重试或后续领域事件分发；本次增量方案采用 Redis Lua + 两条 Stream。

## 2. Rebind 接口

保留 Rebind 能力，并继续使用独立的 HTTP/API，方便后续关系管理 UI：

```text
PATCH /document/{sourceDocumentId}/relations/{refId}/rebind
```

Rebind 以 `sourceDocumentId + refId` 定位关系，校验当前用户权限、目标资源可见性、资源类型白名单和版本冲突。

Rebind 继续保持接口形式，不改造成新的 WebSocket 二进制帧。修改关系投影后，仍需最终同步正文中的 `resourceReference`；如果只修改 DB 而没有修改 Yjs，后续校准应以 Yjs 正文为准。

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

真正按 Yjs 语义处理的位置仍是 Merge Service 的 `Y.applyUpdate(...)` 和 `Y.encodeStateAsUpdate(...)`。绑定消费者不参与这条翻译链路。

### 3.2 双写边界

一次绑定包含两个结果：

```text
原始 Yjs update       → resourceReference 正文事实
绑定命令               → biz_document_relation 查询投影
```

`resourceReference` 仍是正文事实源，`biz_document_relation` 只是关系投影。两条 Redis Stream 可以通过 Lua 原子入队，但后续 Yjs Op Log 与关系表写入仍然是异步、最终一致的两个处理过程。

因此：

- `updates` 队列继续只服务 Yjs 持久化和 Compact；
- `binding` 队列只服务关系投影；
- 绑定消费者只解析绑定命令，不解析 Yjs；
- Link 命令重复、丢失或乱序时，分别依靠幂等、重试和后续 Yjs reconcile 修复；
- ACK 只承诺可靠入队，不承诺 `biz_document_relation` 已完成写入。

如果要求正文节点和关系表强一致，需要重新设计统一持久化事件包，超出当前 v0.6 增量方案范围。
