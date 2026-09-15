# Phase 5 业务路由 Scope 目录

## 状态与适用范围

这是 Phase 5 首发的、可执行的 route-to-scope 目录。Phase 6 删除旧四个登录/登出
路由后，当前存在 120 个 `@RestController` 下的 `/user/**` 与 `/admin/**` 最终 HTTP
路由：75 个 user 路由、45 个 admin 路由；其中 116 个是 bearer 业务路由、4 个是下文明确排除的
legacy/public/activation 例外。认证完成后，每个业务路由都必须同时满足本表的全部
required scope；scope 的 wildcard 匹配由统一 matcher 完成，签发 JWT 时不展开。

本目录的权限名空间固定为：

| resource | actions |
| --- | --- |
| `account` | `read`、`write`、`manage` |
| `note` | `read`、`write`、`manage` |
| `media` | `read`、`write`、`manage` |
| `audio` | `read`、`write`、`manage` |
| `audit` | `read`、`write`、`manage` |

`*:super` 不是任何路由的默认 required scope。它仍是 creator 显式请求时才能
签发的特殊 scope；creator-only username 修改、ownership 与 role rank 都是独立的
业务规则，不能由本目录或 role 替代。

## 入口边界与例外

- `user` client 只能进入 `/user/**`；`admin` client（JWT 角色为 `ADMIN` 或
  `CREATOR`）只能进入 `/admin/**`。不会新增 `/admin/creator/**`。
- 路径 client 边界不授予业务权限；表内 scope 才是准入条件。role 只用于身份、
  ownership 和管理等级判断。
- `core_agent` 首版拒绝全部本表旧业务路由，即使它的 scope 能匹配；它不具有临时
  user/admin 旁路。
- 以下 4 条路由不是本目录的 bearer 业务路由：`POST /user/user/register`、
  `POST /user/user/resend-activation`、`GET /user/user/active/{token}` 与
  `POST /user/user/active-code`。它们属于既有 public activation 协议，首版不改造。

所有标记为“own”的操作还必须在 service 层做 ownership 校验；标记为“any”的
admin 操作还会保留相应 rank/creator 业务约束。

## HTTP 拒绝语义

认证缺失、无效或过期时返回 HTTP `401`。JWT route scope、client 边界不满足时，及
通过路由准入后 service 层拒绝 ownership、rank 或 creator-only 业务规则时，均返回
HTTP `403`；响应体保持既有 `Result.error` 契约。此行为在 RS256 与 legacy 模式下一致。
OAuth 协议端点不属于本目录，继续使用 RFC OAuth 错误格式，不走业务 `Result` 错误体。

## `/user/**`：user client（71 bearer routes）

| # | method + path | required scopes（all-of） | 业务语义 |
| ---: | --- | --- | --- |
| 1 | `POST /user/audio/generate` | `audio:write` | 创建 own AI 音频任务 |
| 2 | `POST /user/audio/retry/{taskId}` | `audio:write` | 重试 own 任务 |
| 3 | `GET /user/audio/status/{taskId}` | `audio:read` | 读取 own 任务 |
| 4 | `POST /user/audio/list` | `audio:read` | 列表 own 任务 |
| 5 | `POST /user/audio/cancel/{taskId}` | `audio:write` | 取消 own 任务 |
| 6 | `DELETE /user/audio/{taskId}` | `audio:write` | 删除 own 任务 |
| 7 | `POST /user/audit/image/submitAudit` | `audit:write` | 提交 own 图片审核 |
| 8 | `POST /user/audit/image/cancelAudit` | `audit:write` | 取消 own 图片审核 |
| 9 | `POST /user/image/list` | `media:read` | 列表 own/public 图片 |
| 10 | `GET /user/image/overview` | `media:read` | own 图片统计 |
| 11 | `POST /user/image/upload` | `media:write` | 上传 own 图片 |
| 12 | `PUT /user/image/modify-file` | `media:write` | 更新 own 图片文件 |
| 13 | `PUT /user/image/modify-info` | `media:write` | 更新 own 图片信息 |
| 14 | `GET /user/image/{id}` | `media:read` | 读取 own/public 图片 |
| 15 | `DELETE /user/image/{id}` | `media:write` | 删除 own 图片 |
| 16 | `POST /user/note/list` | `note:read` | 列表 own 笔记 |
| 17 | `GET /user/note/overview` | `note:read` | own 笔记统计 |
| 18 | `POST /user/note/upload` | `note:write` | 创建 own 笔记 |
| 19 | `PUT /user/note/upload/{noteId}` | `note:write` | 更新 own 源内容 |
| 20 | `POST /user/note/upload/{noteId}/confirm` | `note:write` | 确认 own 内容变更 |
| 21 | `GET /user/note/upload/{noteId}/diff` | `note:read` | 读取 own 变更 diff |
| 22 | `PUT /user/note/publish/{noteId}/{status}` | `note:write` | 发布/取消发布 own 笔记 |
| 23 | `GET /user/note` | `note:read` | 列表 own 笔记 |
| 24 | `GET /user/note/{noteId}` | `note:read` | 读取 own 笔记详情 |
| 25 | `GET /user/note/source/{id}` | `note:read` | 读取 own Markdown 源内容 |
| 26 | `GET /user/note/converted/{noteId}` | `note:read` | 读取 own 转换结果 |
| 27 | `POST /user/note/convert` | `note:write` | 转换 own 笔记 |
| 28 | `DELETE /user/note/convert` | `note:write` | 删除 own 转换结果 |
| 29 | `PUT /user/note/{id}/info` | `note:write` | 更新 own 笔记信息 |
| 30 | `DELETE /user/note/{id}` | `note:write` | 删除 own 笔记 |
| 31 | `GET /user/note/search` | `note:read` | 搜索 own 笔记 |
| 32 | `POST /user/note/relation/check/{noteId}` | `note:read` | 校验 own 关联完成度 |
| 33 | `GET /user/note/relation/{noteId}` | `note:read` | 读取 own 关联信息 |
| 34 | `GET /user/note/relation/images/{noteId}` | `note:read` + `media:read` | 读取 own 笔记关联图片 |
| 35 | `GET /user/note/relation/backlinks/{noteId}` | `note:read` | 读取 own 笔记反链 |
| 36 | `GET /user/note/relation/backlinks/tag/{tagId}` | `note:read` | 读取 own 标签反链 |
| 37 | `GET /user/note/relation/backlinks/image/{imageId}` | `note:read` + `media:read` | 读取图片反链 |
| 38 | `PUT /user/note/relation/tag/bind` | `note:write` | 绑定 own 笔记标签 |
| 39 | `DELETE /user/note/relation/tag/unbind/{mappingId}` | `note:write` | 解绑 own 笔记标签 |
| 40 | `PUT /user/note/relation/image/bind` | `note:write` + `media:read` | 绑定 own 笔记图片 |
| 41 | `DELETE /user/note/relation/image/unbind/{mappingId}` | `note:write` | 解绑 own 笔记图片 |
| 42 | `PUT /user/note/relation/each/bind` | `note:write` | 绑定 own 笔记 |
| 43 | `DELETE /user/note/relation/each/unbind/{mappingId}` | `note:write` | 解绑 own 笔记 |
| 44 | `GET /user/public-note` | `note:read` | 列表公开笔记 |
| 45 | `GET /user/public-note/{noteId}` | `note:read` | 读取公开笔记 |
| 46 | `POST /user/tag/list` | `note:read` | 查询 own 标签 |
| 47 | `GET /user/tag/stats` | `note:read` | own 标签统计 |
| 48 | `GET /user/tag` | `note:read` | 列表 own 标签 |
| 49 | `POST /user/tag/add` | `note:write` | 创建 own 标签 |
| 50 | `POST /user/tag/batch-add` | `note:write` | 批量创建 own 标签 |
| 51 | `DELETE /user/tag/delete` | `note:write` | 删除 own 标签 |
| 52 | `POST /user/tag/assign` | `note:write` | 分配 own 标签 |
| 53 | `POST /user/tag/remove` | `note:write` | 移除 own 标签 |
| 54 | `POST /user/audit/note/submitAudit` | `audit:write` | 提交 own 笔记审核 |
| 55 | `POST /user/audit/note/cancelAudit` | `audit:write` | 取消 own 笔记审核 |
| 56 | `POST /user/audit/tag/submitAudit` | `audit:write` | 提交 own 标签审核 |
| 57 | `POST /user/audit/tag/cancelAudit` | `audit:write` | 取消 own 标签审核 |
| 58 | `POST /user/topic/list` | `note:read` | 列表 own 主题 |
| 59 | `GET /user/topic/children` | `note:read` | 读取 own 主题树 |
| 60 | `GET /user/topic/stats` | `note:read` | own 主题统计 |
| 61 | `POST /user/topic/add` | `note:write` | 创建 own 主题 |
| 62 | `PUT /user/topic/modify` | `note:write` | 修改 own 主题 |
| 63 | `DELETE /user/topic/delete` | `note:write` | 删除 own 主题 |
| 64 | `POST /user/email/resend-activation` | `account:write` | 重发 own 激活邮件 |
| 65 | `GET /user/email/status` | `account:read` | 读取 own 邮箱状态 |
| 66 | `POST /user/email/change-code` | `account:write` | 发起 own 换邮箱 |
| 67 | `POST /user/email/verify-change` | `account:write` | 确认 own 换邮箱 |
| 68 | `GET /user/user/me` | `account:read` | 读取 own 资料 |
| 69 | `GET /user/user/overview` | `account:read` | 读取 own 概览 |
| 70 | `PUT /user/user/me` | `account:write` | 更新 own 资料/密码 |
| 71 | `DELETE /user/user/me` | `account:write` | 删除 own 账户 |

下列 4 条 `/user/**` 注册/activation 路由属于本文件开头的例外，计入源码路由
总数但不分配 bearer required scope：`POST /user/user/register`、
`POST /user/user/resend-activation`、`GET /user/user/active/{token}` 和
`POST /user/user/active-code`。它们保持既有 activation 协议；因此表内 bearer
业务条目为 71 条，`/user/**` 源码 endpoint 总数为 75。

## `/admin/**`：admin client（45 bearer routes）

| # | method + path | required scopes（all-of） | 业务语义 |
| ---: | --- | --- | --- |
| 1 | `POST /admin/audio/list` | `audio:read` | 查询 any 音频任务 |
| 2 | `GET /admin/audio/statistics` | `audio:read` | any 音频任务统计 |
| 3 | `GET /admin/audio/{taskId}` | `audio:read` | 读取 any 音频任务 |
| 4 | `POST /admin/audio/cancel/{taskId}` | `audio:manage` | 取消 any 音频任务 |
| 5 | `DELETE /admin/audio/{taskId}` | `audio:manage` | 删除 any 音频任务 |
| 6 | `POST /admin/audit/meta/list` | `audit:read` | 查询标签审核记录 |
| 7 | `POST /admin/audit/image/list` | `audit:read` | 查询图片审核记录 |
| 8 | `POST /admin/audit/note/list` | `audit:read` | 查询笔记审核记录 |
| 9 | `PUT /admin/audit/meta/review/batch` | `audit:manage` | 批量审核标签 |
| 10 | `PUT /admin/audit/image/review/batch` | `audit:manage` | 批量审核图片 |
| 11 | `PUT /admin/audit/note/review/batch` | `audit:manage` | 批量审核笔记 |
| 12 | `PUT /admin/image/audit/review` | `audit:manage` | 兼容图片审核 |
| 13 | `PUT /admin/image/modify-info` | `media:manage` | 修改 any 图片信息 |
| 14 | `PUT /admin/image/transfer-to-cloud` | `media:manage` | 迁移 any 图片存储 |
| 15 | `DELETE /admin/image/delete` | `media:manage` | 批量删除 any 图片 |
| 16 | `POST /admin/image/list` | `media:read` | 查询图片 |
| 17 | `GET /admin/image/notes/{imageId}` | `media:read` + `note:read` | 查询图片关联笔记 |
| 18 | `POST /admin/image/public/{isPublic}` | `media:manage` | 变更图片公开状态 |
| 19 | `GET /admin/note/source/{noteId}` | `note:read` | 读取 any 笔记源内容 |
| 20 | `POST /admin/note/convert/{noteId}` | `note:manage` | 转换 any 笔记 |
| 21 | `DELETE /admin/note/convert/{noteId}` | `note:manage` | 删除 any 转换结果 |
| 22 | `PUT /admin/note/force/{status}/{noteId}` | `note:manage` | 强制变更笔记状态 |
| 23 | `DELETE /admin/note/delete` | `note:manage` | 删除 any 笔记 |
| 24 | `PUT /admin/note/info` | `note:manage` | 修改 any 笔记信息 |
| 25 | `POST /admin/note/list` | `note:read` | 查询笔记 |
| 26 | `GET /admin/note/info/{noteId}` | `note:read` | 读取笔记信息 |
| 27 | `GET /admin/note/open/{noteId}` | `note:read` | 打开笔记 |
| 28 | `GET /admin/note/relation/backlinks/{noteId}` | `note:read` | 笔记反链 |
| 29 | `GET /admin/note/relation/backlinks/tag/{tagId}` | `note:read` | 标签反链 |
| 30 | `GET /admin/note/relation/backlinks/image/{imageId}` | `note:read` + `media:read` | 图片反链 |
| 31 | `PUT /admin/tag/modify` | `note:manage` | 修改 any 标签 |
| 32 | `DELETE /admin/tag/delete` | `note:manage` | 删除 any 标签 |
| 33 | `POST /admin/tag/list` | `note:read` | 查询标签 |
| 34 | `GET /admin/topic/{id}` | `note:read` | 读取主题 |
| 35 | `POST /admin/topic/list` | `note:read` | 查询主题 |
| 36 | `GET /admin/topic/children` | `note:read` | 读取主题树 |
| 37 | `DELETE /admin/topic/delete` | `note:manage` | 删除 any 主题 |
| 38 | `POST /admin/email/send` | `account:manage` | 发送平台邮件 |
| 39 | `POST /admin/user/list` | `account:read` | 查询用户 |
| 40 | `PUT /admin/user/user` | `account:manage` | 修改用户；rank/creator 另行校验 |
| 41 | `POST /admin/user/user` | `account:manage` | 创建用户；rank 另行校验 |
| 42 | `DELETE /admin/user/user` | `account:manage` | 删除用户；rank 另行校验 |
| 43 | `POST /admin/user/status/{status}` | `account:manage` | 改用户状态；rank 另行校验 |
| 44 | `GET /admin/user/user` | `account:read` | 读取用户 |
| 45 | `GET /admin/user/me` | `account:read` | 读取当前管理员资料 |

admin bearer 业务条目为 45 条，且 `/admin/**` 源码 endpoint 总数同为 45。

## 数据与签发约束

本目录要求 `sys_permission.code` 具备上述 15 个精确 code。保留原有 4 个 wildcard
code（`*:read`、`*:write`、`*:manage`、`*:super`）作为 RBAC 兼容数据，matcher 可令
其匹配精确路由 required scope；但第一方 client 的 scopes 和 auto-approve 应改为本
目录精确 code，避免默认签发跨资源 wildcard。USER 角色可继续拥有 `*:read,*:write`，
ADMIN 通过 rank 继承 USER 并拥有 `*:manage`，CREATOR 继承并拥有 `*:super`。

`user` client 的 scope/auto-approve 是五资源的 `read,write`；`admin` client 的
scope/auto-approve 是五资源的 `read,manage`，`*:super` 不得 auto-approve。`core_agent`
保持其既有 `note:read,note:write,sys:read,media:read` 范围，但按上文继续拒绝旧业务
路由。所有变更仍通过“角色有效权限 ∩ client scopes ∩ request scopes（未传则
auto-approve）”计算。
