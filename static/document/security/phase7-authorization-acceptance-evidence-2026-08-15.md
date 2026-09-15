# Phase 7 授权升级验收证据（2026-08-15）

本文记录 2026-08-15 在隔离环境完成的 Phase 7 验收结果。它是
[Phase 7 后端授权升级验收与发布 Runbook](phase7-backend-authorization-acceptance-and-release-runbook.md)
的一次执行记录，不替代 Runbook 中尚未完成的正式发布门禁。

## 结论

- **代码、静态门禁和本轮可执行的隔离 E2E：通过。**
- **正式切换：阻塞。** 尚需专用 RabbitMQ/邮件事件环境、既有数据库 forward migration
  基线与备份窗口，以及可控 Redis 故障注入。Testcontainers 按已确认边界暂缓。
- 首发 Redis 拓扑固定为**单节点**；本轮没有验证 Redis Cluster，也不得据此启用 Cluster。

## 环境与保密边界

- JDK：OpenJDK 21.0.11。
- Redis：隔离逻辑库，单节点；测试结束后仅清理了本轮精确 email-code rate-limit key。
- MySQL：隔离的 fresh schema；未将 fresh-schema 结果冒充既有库 forward migration 证据。
- SMTP：仅回环地址上的临时 implicit-TLS sink，邮件和验证码只存在于进程内存。
- RSA：工作区外临时 2048-bit PKCS#8/X.509 PEM；未提交私钥、密码、token、验证码或邮件内容。
- `target/phase7` 下的 E2E 工具与证据均为 Git ignored 临时产物，不属于发布制品。

## 自动化静态门禁

执行 `scripts/phase7/Invoke-AuthorizationStaticVerification.ps1` 的最终结果：

| 项目 | 结果 |
| --- | --- |
| Maven | 离线 reactor build success（26 个模块） |
| Surefire | 182 份报告，721 tests |
| failures / errors / skipped | 0 / 0 / 0 |
| 工作树 | 仅提交的源码、测试和文档；无 `target/phase7` 产物进入 Git |

本轮还补正了 `InternalLoginServiceContextTest` 对新增 internal refresh 服务的装配门禁，
聚焦回归为 15/15 通过。

## 已通过的真实隔离 E2E

1. **internal password / refresh / logout**：user/admin 登录、scope 收窄、refresh rotation、旧
   refresh replay、同一 refresh 并发唯一成功、错误 client、`/oauth/token` 拒绝 internal client、
   logout 后 access/refresh 失效均符合契约。
2. **CORE AGENT authorization code**：浏览器登录、consent、callback、PKCE S256、redirect
   绑定、一次性 code、refresh rotation、业务路由 client 边界和 `/oauth/logout` 均通过。
3. **Resource Server**：缺失/伪造/blacklisted bearer 为 401；scope 或 client 边界不足为
   403；允许的业务路由成功；注销后的 access replay 被拒绝。
4. **rank / creator-only**：ADMIN 不能修改用户名，不能管理同级角色，可以管理低级角色。
5. **安全字段变更吊销**：username、email、password、role（含 extra-grant 写路径）和 status
   更新后，当前 CORE AGENT code 与 user+client pointer 均失效，旧 code 无法兑换。
6. **旧路由与 activation 边界**：四个退役 user/admin login/logout 路由均为 404；非法
   activation token 为 401。
7. **PEM fail-closed**：缺失、非文件、非法 PEM、不匹配 key pair 和 1024-bit key 均无法启动。
8. **email-code**：同步 SMTP 投递与正确消费、cooldown、五次错误失效、SMTP 失败删除 state
   且保留 rate-limit 均通过；测试账号和精确隔离 key 已清理。
9. **日志红线**：本轮服务器日志未匹配 bearer JWT、access/refresh token、PKCE verifier、
   password 或 verifier hash 形态。

## 验收过程中发现并修复的问题

| Commit | 修复 |
| --- | --- |
| `47d2211` | 为 CORE AGENT 授权码签发服务提供确定的 UTC clock 装配 |
| `f895171` | 允许 pending Redis repository 被 Spring CGLIB 代理 |
| `f4c01df` | 显式选择两个 BCrypt 生产构造器 |
| `4e112b6` | 在真实 SAS filters 中保留项目 request context，并隔离官方 bootstrap provider 的运行时 fallback |
| `62a10d5` | 全局 Jackson mapper 支持 `Instant` 事件时间 |
| `83d92b8` | 修复 email-code 限流 Lua 的 canonical counter 校验 |
| `0127234` | 静态验证脚本保留 Maven stderr/Mockito 警告证据 |
| `6866ed0` | 更新 internal refresh 的 Spring context 装配门禁 |

## 仍阻塞正式切换的外部门禁

1. **RabbitMQ / activation 与 email-change 投递**：必须提供专用测试 vhost、账号和受控消费者，
   再验证 outbox 到真实投递的完整链路；不得在共享 vhost 上启动本轮 listeners。
2. **既有 MySQL forward migration**：部署负责人需确认实际版本与已执行脚本，完成备份和维护
   窗口，按顺序执行所有缺失 migration 的 preflight/postflight。本轮只证明 fresh schema。
3. **Redis 故障事务边界**：需专用 Redis 或受控故障注入，证明 commit 前吊销失败会令 MySQL
   事务回滚；不得为此停止共享 Redis。
4. **发布运维**：旧 `adminId:*`、`userId:*` 仅能在旧版本和外部消费者全部下线后，由部署
   负责人分页 `SCAN`、分批 `UNLINK`；禁止 `KEYS` 和应用启动自动清理。

上述门禁完成并补齐脱敏证据前，Phase 7 保持**阻塞**，不得宣告正式切换完成。
