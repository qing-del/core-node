# Agent 聊天消息持久化设计 v1

## 1. 会话存储

使用独立会话表 `biz_agent_chat_session`，一行对应一个聊天会话。

| 字段 | 类型 | 说明 |
|---|---|---|
| `id` | BIGINT | 自增主键 |
| `session_uuid` | CHAR(36) | 前端传入的会话 UUID，唯一 |
| `user_id` | BIGINT | 所属用户，由后端登录态确定 |
| `context` | JSON | 当前聊天的上下文 |
| `history` | JSON | 用于恢复历史聊天记录 |
| `create_time` | DATETIME(3) | 创建时间 |
| `update_time` | DATETIME(3) | 更新时间 |

上下文与历史由后端维护。会话读取和写入均校验所属用户。

## 2. JSON 记录格式

`context` 与 `history` 均使用有序记录数组，以 `type` 区分以下五类内容：

| `type` | 含义 |
|---|---|
| `system_prompt` | 系统提示词 |
| `user_request` | 用户发送的请求 |
| `agent_response` | Agent 最终交付给用户的回复 |
| `llm_response` | 内部 LLM 输出，包括最终回复对应的模型输出 |
| `tool_calling` | 工具调用，记录工具名、调用标识、参数及结果或错误 |

普通消息的 `content` 为字符串；工具调用的 `content` 为 JSON 参数，`call_id` 为调用标识，`tool_name` 为工具名。正常返回时加入 `result` 字段，内容为 JSON；调用失败时加入 `error` 字段，内容为错误说明字符串。每条已完成调用记录仅包含 `result` 或 `error` 之一，不同时包含两者。暂不加入其他记录类型、时间戳或状态字段。

以下示例展示一个成功轮次。外层对象仅用于说明，数据库中仍分别存储 `context` 和 `history`。

```json
{
  "context": [
    {
      "type": "system_prompt",
      "content": "你是协助用户编辑文档的 AI Agent。"
    },
    {
      "type": "user_request",
      "content": "读取当前文档，告诉我标题。"
    },
    {
      "type": "llm_response",
      "content": "需要读取当前文档。"
    },
    {
      "type": "tool_calling",
      "call_id": "call_001",
      "tool_name": "read_document",
      "content": {
        "document_id": "doc_001"
      },
      "result": {
        "title": "项目说明"
      }
    },
    {
      "type": "llm_response",
      "content": "当前文档的标题是《项目说明》。"
    }
  ]
}
```

```json
{
  "history": [
    {
      "type": "user_request",
      "content": "读取当前文档，告诉我标题。"
    },
    {
      "type": "agent_response",
      "content": "当前文档的标题是《项目说明》。"
    }
  ]
}
```

失败调用示例：

```json
{
  "type": "tool_calling",
  "call_id": "call_001",
  "tool_name": "read_document",
  "content": {
    "document_id": "doc_001"
  },
  "error": "文档不存在"
}
```

保存规则：

- `context` 保存当前对话使用的系统提示词，以及成功轮次的用户请求、LLM 输出和工具调用记录，按实际发生顺序记录；每次工具调用用一条记录保存参数及结果或错误。它不是展示记录，也不记录模型隐藏推理。
- 单次工具调用失败不等同于整轮对话失败；若 Agent 后续成功完成本轮，`context` 可以保留包含 `error` 的工具记录。
- `history` 只保存用户请求和 Agent 最终回复，用于恢复聊天展示；不保存系统提示词、内部 LLM 输出或工具调用过程。
- Agent 最终回复可能与最后一次 LLM 输出相同，二者职责不同；`context` 不额外重复保存 `agent_response`。
- 整轮对话失败或中断时，`history` 仅追加本轮 `user_request`，不记录任何模型回复；`context` 保持不变。

## 3. 创建与持久化

- 前端生成并传递会话 UUID。
- 首次发送消息时创建会话，不在打开聊天窗口时创建。
- 成功轮次保留用户消息与最终回复；失败或中断时，`history` 保留本轮用户消息，不记录模型回复（包括部分输出），也不持久化本轮上下文变化。
- 首轮失败或中断时保留会话及本轮用户消息。
- `history` 不记录 tool calling 过程。
- `context` 保存当前聊天上下文，不受 `history` 排除工具调用过程这一展示规则限制。
- 成功轮次的 `context` 与 `history` 在同一数据库事务中写回，避免部分更新。

## 4. 会话互斥

v1 不支持 steer。同一会话 UUID 同时只能执行一个聊天请求，不同会话可以并行。

请求流程：

1. 校验用户身份与会话归属，尝试锁定会话 UUID。
2. 已被锁定时拒绝新请求，不排队、不打断当前请求。
3. 首次请求创建会话，加载已保存的上下文。
4. 在本轮临时状态中执行 LLM 调用。
5. 成功后统一写回 `context` 与 `history`。
6. 失败或中断时，仅将本轮用户消息写入 `history`，保持 `context` 不变。
7. 成功、失败或中断后均释放会话锁；解锁前完成写回或本轮终止。

首发沿用单实例定位，使用实例内会话锁，不引入分布式锁。

## 5. 验证要点

- JSON 示例格式合法，五类记录含义明确，每次工具调用仅用一条记录保存参数及结果或错误，`result` 与 `error` 互斥。
- 工具调用失败但整轮对话成功时，`context` 可保留包含 `error` 的工具记录；整轮失败或中断时，`context` 保持不变。
- `context` 与 `history` 按各自保存规则记录，不重复保存 Agent 最终回复。
- 首次发送才创建会话，后续请求复用前端 UUID。
- 成功轮次更新上下文，并可恢复用户消息和最终回复。
- 失败或中断不改变已有上下文及历史消息，仅向 `history` 追加本轮用户消息，不记录模型回复。
- 首轮失败或中断后，可恢复该会话的用户消息。
- 历史中不包含工具调用过程。
- 同 UUID 并发请求被拒绝，不同 UUID 互不阻塞。
- 写回失败时事务回滚；所有终止路径均释放锁。
- 用户不能读取或修改其他用户的会话。
