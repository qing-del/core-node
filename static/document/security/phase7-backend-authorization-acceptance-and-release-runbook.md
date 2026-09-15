# Phase 7：后端授权升级验收与发布 Runbook

## 目的、边界与结论口径

本文件是 Phase 1–6 后端授权升级的最终验收与正式切换 Runbook。它把“代码和静态
测试已经证明的事实”与“只能由真实部署环境证明的事实”明确分开，防止将 Mockito
测试、脚本源码审阅或本地配置检查误报为生产验收。

本阶段只定义和执行验证；不借验收之名改动授权功能。本期不包含 `frontend/`、
`middleware-open-api-agent` 业务 API、Agent 数据读取 API、OAuth client 管理后台、OIDC、
社交登录、多设备 session 或 key rotation。

最终结论只能使用下列三种状态：

| 状态 | 含义 |
| --- | --- |
| **通过** | 每项代码、真实 Redis、MySQL、SMTP、HTTP E2E 和部署前置条件均有可复核证据。 |
| **阻塞** | 任一正式切换门禁没有证据，或 Redis 拓扑与 Lua 多 key 约束未确认；不得切换。 |
| **失败** | 某项测试、脚本、迁移、启动或安全边界产生与本文件不符的实际结果。 |

静态测试全绿只允许说明“代码静态验收通过”；它不足以得出“后端授权升级第一阶段已完成”
或“可以正式切换”的结论。

相关总体约束见[授权升级草案](授权升级-草案.md)、[分阶段 Prompts](core-node-auth-upgrade-codex-phase-prompts.md)、
[Phase 4 Provider 链设计](phase4-authorization-endpoint-provider-chain-design.md)和
[Phase 5 业务路由 Scope 目录](phase5-business-route-scope-catalog.md)。

## 已有代码与静态验收基线

以下项应由当前源码和对应单元/上下文/契约测试复核。记录实际 Maven 命令、commit、
JDK 版本与 Surefire 汇总，不能只记录“已通过”。

| 验收项 | 主要实现/测试证据 | 静态判定 |
| --- | --- | --- |
| RS256 唯一模式与外部 PEM fail-closed | `OAuth2Rs256CodecConfiguration`、`OAuth2Rs256DeploymentConfigurationTest` | 缺少、不可读、格式错误或不匹配 PEM 时不能建立可接受业务请求的链。 |
| 业务链与 HTTP 401/403 | `BusinessRouteResourceServerSecurityConfigurationTest`、`BusinessRouteAuthorizationManagerTest`、`GlobalExceptionHandlerTest` | RS256 business chain、客户端边界和目录 scope 必须保持既定语义。 |
| JWT claim/blacklist | `CoreNodeAccessTokenClaimsValidatorTest`、`AccessTokenBlacklistJwtValidatorTest`、`RedisAccessTokenBlacklistStoreTest` | `iss/aud/time/jti/client/grant/roles/scope` 严格校验，blacklist 到期前拒绝。 |
| refresh rotation、当前会话注销 | `OAuth2RefreshTokenSessionServiceTest`、`RedisOAuth2TokenStateStoreTest`、`RedisOAuth2SessionRevocationStoreTest` | refresh 指纹不存明文；rotation 和 revoke 使用 Redis 原子脚本。 |
| internal login、email-code、refresh | `InternalLoginServiceTest`、`InternalRefreshTokenServiceTest`、`InternalLogoutServiceTest`、`EmailLoginCode*Test`、`InternalAuthControllerTest` | 仅 `user/admin`、无 client secret；password/email-code 与 scope 交集规则；refresh 必须重新校验 client、IP、账户、角色和当前权限后 CAS rotation。 |
| PKCE、consent 与 code | `CoreAgentAuthorizationCode*Test`、`CoreAgentAuthorizationConsent*Test`、`CoreAgentAuthorizationServerSecurityConfigurationTest` | 固定 `core_agent`、S256、精确 redirect URI、原始 state、项目 Provider 链。 |
| 安全字段吊销和事务顺序 | `AccountAuthorizationStateRevocationService*Test` 及 user/admin write-path tests | MySQL 业务操作完成后、commit 前同步吊销；Redis 失败使事务回滚。 |
| legacy 收口与 activation 例外 | `LegacyUserAdminLoginRoutesRemovalTest`、`ContextCompatibilityTest`、activation tests | 旧 user/admin JWT/路由不复活；activation 协议保持独立。 |
| DB/bootstrap/部署契约 | `MigrationScriptTest`、`ContainerHealthcheckContractTest`、`OAuth2Rs256DeploymentConfigurationTest` | 新库 bootstrap 和 SQL 文本具有预期目录、PEM 挂载、健康检查约束。 |

静态复核还必须确认：注册客户端恰为 `user`、`admin`、`core_agent`，无
`authorization_client`，且 `agent_client` 不是 grant；`middleware-open-api-agent` 保持空模块占位。

## 真实环境总门禁

在运行任何会生成 token、code、验证码或修改生产数据的 E2E 前，验收负责人必须记录：

- 被测环境名称、时间、应用镜像 digest/commit、JDK、Redis 与 MySQL 版本；
- Redis 拓扑、端点、是否 Cluster、主从/哨兵模式与 `cluster-enabled` 实际值；
- 已部署外部 private/public PEM 的文件权限、指纹和 `kid`（不得记录私钥内容）；
- 独立测试账号、测试邮箱、SMTP stub/测试收件箱、允许来源 socket IP；
- 已执行迁移清单及每个 preflight/postflight 的输出；
- 清理计划、数据保留人与回滚决策人。

不得在日志、HTTP 响应、测试报告或证据附件记录 password、BCrypt verifier、验证码、
raw authorization code、refresh token、private PEM 或完整 bearer token。可记录不可逆指纹、
截断后的 `jti`、HTTP 状态和 Redis key 名称。

## Redis 拓扑：首发单节点部署前提

首发部署已确定仅支持**单节点 Redis**；当前仓库的 Docker Compose 同样声明 Redis 7
单节点。以下
CORE AGENT Lua 脚本一次操作多个 key，当前 key 的 hash tag 分别来自 raw handle、raw code
或 user id：

- `core_agent_pending_authorization_to_code.lua`：pending、auth-code、user pointer；
- `core_agent_authorization_code_consume.lua`：auth-code、user pointer；
- `core_agent_authorization_code_replace.lua`：auth-code、user pointer；
- `rotate-current-session.lua`：旧 refresh、新 refresh、session；
- `revoke-current-session.lua`：blacklist、session、可选 refresh。

Redis Cluster 不属于首发支持范围。在 Redis Cluster 中，多 key `EVAL` 要求所有 key 位于同一
hash slot；现有 tag 不保证此条件，会产生 `CROSSSLOT`。不能以“单元测试通过”或“当前代码
没有 Cluster 配置”替代验证。

| 生产拓扑 | 允许的验收动作 | 切换结论 |
| --- | --- | --- |
| 单节点 Redis | 在与生产等价版本 Redis 上完成本文件的真实 Lua/TTL/并发验证。 | 可在其余门禁通过后继续。 |
| Redis Cluster | 首发不支持。必须另起专项设计：为每一原子状态机选择不泄漏凭据、不会扩大影响范围的共同 hash-tag 策略，并完成 Cluster E2E。 | 不得以本次首发版本部署。 |

禁止为绕过问题临时把 EVAL 拆为多次普通 Redis 调用；那会破坏一次性 code、rotation 和
logout/revoke 的原子性。

Testcontainers 也明确不纳入首发验收。真实 Redis、MySQL、SMTP 和 HTTP E2E 必须在受控
单节点环境由本 Runbook 的命令/人工步骤执行并保存证据；后续若批准引入 Testcontainers，应
作为独立测试基础设施变更，不能改变本次单节点部署前提或替代发布环境复验。

## Redis Lua 与并发/TTL 验收矩阵

所有 12 个脚本均须使用真实 `StringRedisTemplate` 到真实 Redis 执行；Mockito 仅验证 Java
组装的 keys/args 和预期返回值，不能替代本表。每个 case 在隔离 namespace/数据库中完成，并在
完成后删除测试数据。

| 脚本/状态 | 成功与 TTL 断言 | 并发/失败断言 |
| --- | --- | --- |
| `core_agent_pending_authorization_save.lua` | pending hash 完整，TTL 接近 10 分钟且不会无 TTL。 | 非法 handle/args 不写任何 key；过期后不可读取。 |
| `core_agent_pending_authorization_to_code.lua` | 原子删除 pending，写 `oauth2:auth_code:{code}` hash 与 `user:auth_code:{user}:{core_agent}` pointer；两者 TTL 同为约 10 分钟。 | 两次同 pending 仅一成功；client/user/session 不绑定、过期或非法 args 时不写 code/pointer。 |
| `core_agent_authorization_code_consume.lua` | 兑换删除 code 和 pointer。 | 两个并发兑换同一 code 恰一成功；pointer 已替换、user/client 不符、过期均为失败且不得产生 token。 |
| `core_agent_authorization_code_replace.lua` | 新 code 与 pointer 原子更新且 TTL 正确；旧 code 可以自然 TTL 保留。 | 非法 args 不写状态；旧 pointer 指向的 code 不可再通过 CAS 兑换。 |
| `core_agent_authorization_code_invalidate.lua` | 精确 `core_agent` 安全字段吊销删除当前 pointer/code。 | 不存在 pointer 时幂等；不得使用 `KEYS` 或用户前缀 `SCAN`。 |
| `replace-current-session.lua` | 新 refresh state 与 `user:session:{client}:{user}` 同步写入，TTL 与 token settings 一致。 | 异常/非法参数不产生半写入状态。 |
| `rotate-current-session.lua` | 旧 refresh 删除，新的 refresh/session 均存在并具正确 TTL。 | 两个并发 refresh 只有一成功；旧 refresh、错 client/user/fingerprint 均不能换出第二个 token。 |
| `revoke-current-session.lua` | 当前 access `jti` blacklist TTL 不超过 access 剩余寿命；当前 session 与 refresh 删除。 | 错 refresh pointer 或已被 rotation 的会话必须 fail closed；不得影响其他 client 的同用户会话。 |
| `email_login_code_issue_rate_limit.lua` | email/IP 各 60 秒冷却、每小时计数最多 5，TTL 正确。 | 并发发送不能突破配额；计数格式/TTL 异常 fail closed。 |
| `email_login_code_state_replace.lua` | `(client,user)` 只保留一条 state，TTL 约 10 分钟，内容只有绑定字段和 BCrypt verifier。 | 重发替换旧 code；不泄露邮箱/账号存在性。 |
| `email_login_code_state_consume.lua` | 正确 code 一次性删除。 | 并发正确兑换仅一成功；错误 verifier、过期/不存在 state 不可成功。 |
| `email_login_code_state_record_failure.lua` | 第 5 次错误立即删除。 | 失败计数不重置 TTL，错误 verifier、过期/不存在 state fail closed。 |

附加 Redis 检查：access blacklist 命中后 access token 必须为 401；所有 raw secret 仅以允许的
哈希/指纹形态存在；不得出现 Phase 6 已废弃的 `adminId:*`、`userId:*` 写入。

## MySQL、迁移与回滚边界

### Fresh database

在空 MySQL 8 数据库中，以当前 `static/database/createDatabase.sql` 初始化，验证：

- 只存在三个注册客户端，状态、grant、client authentication、redirect URI、scope、auto approve、
  allowed IP 与草案一致；
- `sys_user.extra_grant_types` 为空即仍采用配置默认 grant 集合，refresh 不是账号 grant；
- `sys_role.rank`、三角色、19 条 permission 和 role-perm 关系正确；
- consent 表主键与记录语义正确；
- 不存在 `authorization_client` 或 `agent_client` grant；
- 新库应用以外部 PEM、Redis、MySQL 成功启动，并可通过下文 HTTP E2E。

### Existing database forward migration

当前迁移是静态 forward-only SQL 文件，不由运行中的应用自动通过 Flyway/Liquibase 追踪。
对已有库必须由部署负责人在维护窗口、备份完成后，按仓库确定的顺序执行且保存输出：

1. 先确认既有库的 schema/data 版本与已执行脚本，禁止猜测、重复执行或跳过前置版本；
2. 执行 Phase 1 foundation、Phase 3 account grants/registered clients、Phase 4 core-agent activation、
   Phase 5 business route scopes 及该库所缺的先前迁移；
3. 每个具有 preflight/postflight 的脚本都必须看到成功结果；任何 `SIGNAL SQLSTATE` 都是失败，
   不得手工修改数据来强行跨过；
4. 在发布前导出 `oauth2_registered_client`、`sys_role`、`sys_permission`、`sys_role_perm`、
   `sys_user` 相关字段的受控快照，并对敏感值脱敏；
5. 发布后重新查询上述不变量，并运行受控 HTTP E2E。

现有 SQL 没有可安全自动化的授权数据回滚路径。发布前的数据库备份、回滚镜像与停机窗口
是必须前置条件；一旦迁移/新授权数据已被使用，回滚必须由部署负责人基于备份和业务影响决定，
不能通过删除表、清空 consent 或回退 Redis 来模拟。

## HTTP、SMTP 与安全边界 E2E

在隔离环境逐项运行，并保存请求参数的脱敏版本、响应状态、持久化和 Redis 后置状态。

1. **启动与 PEM：** 缺少 private、缺少 public、不可读、错误格式和不匹配 key 各自启动失败；
   正确外部 PEM 才能通过 liveness 并创建 security chains。不得存在 HS256 fallback。
2. **internal login：** `user/admin` 的 password 与 email-code 成功发 RS256 access/refresh；有
   `client_secret`、未知 client/grant、disabled client、越界 IP、disabled user、错误密码/验证码均失败且
   不泄漏账号存在性。验证 requested scope、auto_approve 和 creator-only `*:super` 约束。
3. **email-code：** 使用 SMTP stub/测试邮箱确认邮件不进入 outbox/event；验证 60 秒/IP+email 限流、
   一小时 5 次、10 分钟 TTL、一次消费、5 次失败失效。强制 SMTP 失败后 state 必须删除，而已占用
   rate-limit key 必须保留。
4. **refresh/logout/revoke：** user 和 admin 分别登录；internal refresh 只可通过
   `POST /auth/login` 且 `grant_type=refresh_token`，不得接受 `/oauth/token`。验证 refresh 时重新读取
   client/IP/account active 状态/当前角色与权限，明确 scope 只能维持或收窄；并发兑换同一 refresh
   恰一成功，rotation 后旧 refresh 失败。logout 仅注销当前 `(client,user)`，旧 access 因 blacklist
   返回 401，另一个 client 的 session 仍可用。对 `core_agent` 验证 1h access/24h refresh/rotation 与
   `/oauth/logout` 同样的隔离语义。
5. **authorization code：** 从 `/oauth2/authorize` 到 browser login、consent、registered callback、
   `/oauth/token` 完整跑通；检查 S256、精确 redirect URI、original state、auto_approve 不可撤销、deny
   返回 RFC `access_denied`，并验证 code 一次性和 IP 变化仅告警。
6. **resource server：** 缺失/伪造/过期/blacklisted JWT 均为 401；scope 或 client boundary 不足、
   ownership/rank/creator-only 拒绝均为 403，且业务响应保持 `Result.error`；OAuth endpoint 错误仍是
   RFC OAuth 格式。随机抽样并至少覆盖 Phase 5 目录的每种 resource/action 和 all-of image-note case。
7. **安全字段改变：** 对 `status,role_id,password,email,username,extra_grant_types` 分别进行真实更新。
   事务业务操作成功后、commit 前应同步精确吊销 `core_agent` pointer；Redis 故障时 MySQL 回滚。允许的
   已知边界是 Redis 已成功吊销但最后 MySQL commit 失败造成 fail-safe 提前失效；不得改为 after-commit
   或静默重试而不重新设计。
8. **activation 例外：** `GET /user/user/active/{token}` 与现有 activation/email-change credential
   协议继续按既有行为运行；它不得接受或恢复 user/admin HS256 bearer chain。

## 旧 Redis key 清理（运维门禁，非应用行为）

应用启动、健康检查、E2E 和迁移均不得自动扫描或清理旧 `adminId:*`、`userId:*`。只有部署负责人
书面确认所有旧应用版本及外部消费者已经下线后，才可在维护窗口使用 cursor 分页 `SCAN MATCH`，
分批 `UNLINK`，记录每批 cursor、数量、操作者、时间与最终数量。禁止 `KEYS`，禁止匹配或删除其他
namespace。未完成该外部依赖确认时，保留 key 不阻断新链路运行，但清理状态必须记为“未执行”。

## 证据记录模板

每一轮验收建立一份不可包含 secret 的记录：

| 项目 | 记录内容 |
| --- | --- |
| 构建 | commit/image digest、JDK、Maven 命令、测试数/失败数、开始/结束时间。 |
| Redis | 版本、拓扑及 Cluster 状态、连接方式、Lua case、并发数、TTL 实测、key 后置检查、清理结果。 |
| MySQL | 版本、fresh/upgrade 类型、备份标识、迁移顺序、每个 pre/postflight 输出、最终不变量查询。 |
| SMTP | stub/test inbox 标识、成功与故障注入结果、state/rate-limit 后置检查。 |
| HTTP | case 标识、脱敏请求、HTTP 状态、响应契约、JWT/key 状态，不保存 token/code/password。 |
| PEM | public key fingerprint、私钥路径可读性检查结果、五类 fail-closed 启动结果。 |
| 结论 | 通过/失败/阻塞，未完成项、负责人、解决条件与复验时间。 |

## 当前未决项与最终结论

当前首发 Redis 拓扑已确定为单节点，但不得将缺少真实 Redis/MySQL/SMTP/HTTP E2E 的环境
视为通过。Docker/真实测试环境不可用或上述证据缺失时，本阶段结论为：

> **代码静态验收可继续；后端授权升级第一阶段的正式切换结论为阻塞。**

当且仅当单节点 Redis 与所有真实环境门禁通过后，才能将结论更新为“后端授权升级第一阶段完成”。
