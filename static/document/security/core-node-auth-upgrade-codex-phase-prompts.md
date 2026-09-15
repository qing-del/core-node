# CORE NODE 授权升级：阶段 Codex Prompt

> 使用方式：每次只把一个阶段 Prompt 交给 Codex。  
> 所有阶段都只要求 **先规划，不直接实现**。  
> Codex 必须优先读取仓库内的授权升级方案文档，并结合当前代码实际情况输出实施计划。  
> **如果出现关键不确定项、方案与现状冲突、需要新增未约定设计时，立即停止并向我提问，不自行拍板。**
>
> 本轮范围：**仅后端**。  
> `middleware-open-api-agent` 可以提前创建，但只允许做**空模块/占位模块**，本轮不实现 Agent 业务接口。  
> **禁止修改 frontend/**，前端适配放到后续独立计划。

---

## Phase 0：现状核对与实施基线

```text
你现在工作在当前 middleware 仓库。

先在仓库内找到并完整阅读我放入项目的“授权升级方案”文档，然后结合当前代码实际情况，规划 Phase 0：授权升级实施基线。

本阶段只做分析和规划，不修改代码。

重点检查：
- 当前 Maven 多模块结构
- middleware-common-security
- middleware-common-web
- middleware-module-system
- middleware-server
- 当前 SecurityFilterChain
- LegacyJwtAuthenticationFilter
- JwtTokenSupport / JwtProperties
- TokenSessionService / RedisJwtTokenSessionService
- SecurityContextBridge / BaseContext / PermissionContext
- user/admin 登录、logout
- sys_user / sys_role
- static/database/createDatabase.sql
- static/database/migrations
- 现有鉴权相关测试

目标：
1. 对照授权升级方案，列出当前实现与目标之间的差异。
2. 明确哪些现有行为需要 characterization test 冻结。
3. 明确后续各阶段应该修改哪些模块、哪些模块不应该承担授权逻辑。
4. 给出后续实施顺序和依赖关系。
5. 不修改生产代码，不修改数据库，不修改前端。

输出：
- 当前鉴权调用链
- 当前关键类/文件清单
- 与授权升级方案的差异
- 建议补充的基线测试
- 后续阶段依赖关系
- 风险点

如果发现授权升级方案与当前代码存在关键冲突，或者无法确定某项设计，应立即停止，列出问题等我回复，不要自行决定。
```

---

## Phase 1：RBAC / OAuth Client 数据基础 + Agent 空模块占位

```text
请先读取仓库内的授权升级方案文档和 Phase 0 已确认的结论，然后规划 Phase 1。

本阶段只做规划，不修改代码。

目标：
1. 建立新授权体系所需的数据模型和持久化基础。
2. sys_role 增加 rank，后续角色等级不再依赖 role id。
3. sys_user 增加 extra_grant_types；默认账号授权模式由配置提供，不能与该字段形成两套真相。
4. 新增授权方案中约定的：
   - oauth2_registered_client
   - oauth2_authorization_consent
   - sys_permission
   - sys_role_perm
5. 规划对应 Entity / Mapper / XML / Repository / Service 边界。
6. 规划 Client、Role、Permission 的初始化数据。
7. 规划 module:operation 权限规则，以及 Resource Server 对 wildcard 权限的匹配方式；JWT scope 不在签发时展开。
8. 新增 middleware-open-api-agent 模块，但只能作为空模块占位：
   - 加入 Maven modules
   - 提供最小 pom
   - 可以有空 package / package-info / 占位说明
   - 不允许新增 Agent Controller、业务 API、权限实现
9. 不修改现有 user/admin 登录和 JWT 行为。
10. 不修改 frontend/。

数据库要求：
- 使用 static/database/migrations 新增向前迁移脚本
- 同步 static/database/createDatabase.sql
- 尽量保持 Spring Authorization Server RegisteredClient 模型兼容
- 避免同一配置存在两套真相

请输出：
- 精确文件改动计划
- DDL 设计
- 数据迁移和 seed 方案
- Java 分层设计
- middleware-open-api-agent 空模块结构
- 测试计划
- 实施顺序
- 回滚方案

如果授权升级方案中的字段、Client 配置、权限规则与现有代码无法直接兼容，先停止并向我提问，不要自行新增设计。
```

---

## Phase 2：RS256 / Token / Security Core

```text
请先读取仓库内授权升级方案文档，以及前面阶段已经确认的代码结构，然后规划 Phase 2：新 Security Core。

本阶段只做规划，不修改代码。

目标：
1. 在现有 middleware-common-security 内演进，不新建平行 security 模块。
2. 引入并规划 RS256 Access Token 能力。
3. 规划 RSA private/public key 加载。
4. 规划 JwtEncoder / JwtDecoder。
5. 按授权升级方案生成 Access Token claims：
   - iss
   - sub
   - aud
   - iat
   - exp
   - jti
   - client_id
   - grant_type
   - username
   - roles
   - scope
6. 规划 jti blacklist。
7. 规划 256-bit opaque refresh token 生成与安全存储/索引方式。
8. 规划 CurrentPrincipalAccessor，使现有 BaseContext / PermissionContext 可以逐步兼容新 JwtAuthenticationToken。
9. 保留 legacy JWT 作为过渡，不在本阶段删除。
10. 规划 JWK / public key 暴露所需基础设施。
11. 不修改 frontend/。
12. 不实现 Agent 业务接口。

重点检查：
- middleware-common-security
- middleware-common-web
- JwtTokenSupport
- JwtProperties
- LegacyJwtAuthenticationFilter
- SecurityContextBridge
- SecurityPrincipal / SecurityIdentity
- BaseContext / PermissionContext
- 当前 Redis Token Session 实现
- 当前 SecurityFilterChain

输出：
- 包结构/类设计
- 新旧 Security 共存方式
- RS256 key 配置设计
- JwtEncoder/JwtDecoder 设计
- blacklist / refresh token 设计
- SecurityContext 兼容方案
- 测试矩阵
- 实施顺序
- cutover 前置条件

如果 Spring Boot / Spring Security 当前版本的 API 与方案存在差异，必须先核对当前依赖和官方 API；仍存在关键不确定时立即停止并问我。
```

---

## Phase 3：USER / ADMIN 统一 Token、Refresh、Logout

```text
请先读取仓库内授权升级方案文档，以及 Phase 1、Phase 2 已确认的设计，然后规划 Phase 3。

本阶段只做规划，不修改代码。

目标：
1. 后端建立 Spring Authorization Server，并将 USER / ADMIN 统一为 internal 登录体系，签发 RS256 access token。
2. fixed registered client 仅为 `user`、`admin`、`core_agent`；本阶段只处理前两个。`user`、`admin`使用数据库认证方式 `internal`，不传 `client_secret`，且不等同于 public client。
3. 仅保留 `POST /auth/login`、`POST /auth/logout` 作为 USER / ADMIN internal endpoint；目标架构删除 `/user/user/login`、`/admin/user/login`、`/user/user/logout`、`/admin/user/logout`，不保留 compatibility adapter。
4. `/auth/login`按 `(client_id,grant_type)` 路由：client_id 仅 `user|admin`；grant_type 仅 `password|email-code`。校验 client status、client grant、账号默认/extra grant、账号状态和 socket remote address 的 `allowed_ips`。
5. `sys_user.extra_grant_types`仅保存附加模式；账号配置默认模式为 `password,email-code,authorization_code`，不得将默认模式写入该字段；refresh_token 是 client 技术 grant。
6. 新增独立 `POST /oauth/email-code`：`client_id,email`、6 位、10 分钟、同一 `(client,user)`单有效；每邮箱/每 IP 60 秒冷却且每小时 5 次，5 次兑换失败失效；Redis 仅存 BCrypt verifier 与绑定信息，成功原子消费，且绝不复用 activation/email-change 逻辑或泄漏账号存在性。
7. 权限计算：有请求 scope 时为 client scopes ∩ rank-role permissions ∩ 请求 scopes；未请求时再∩ auto_approve。scope 必须按 `resource:action` wildcard 模式逐分量求交（相同保留、一方`*`取另一方、不同具体分量无交集），不是精确字符串相等，也不展开 permission catalogue；显式空请求不等同于未请求。`*:super`及其他`*:super`/`resource:super`结果只在 role code 为`CREATOR`、client 允许、且本次明确请求存在可相交 super 模式时签发；auto_approve 永不签发 super。
8. 实现 access token + refresh token；USER/ADMIN TTL 与 rotation 以 client `token_settings`为唯一来源。旧 refresh 成功后必须立即失效并返回新 access + refresh。
9. `/auth/logout`只注销当前 client + user session：access jti blacklist、refresh 删除、session 删除；不得影响另一 client 的会话。
10. 本阶段只改后端，禁止修改 frontend/，不实现 Agent 业务接口。

重点检查：
- system 模块现有 user/admin 登录 Controller / Service
- PasswordEncoder / BCrypt
- TokenSessionService
- Redis session
- SecurityFilterChain
- 现有登录返回结构
- 现有 admin/user 权限判定

输出：
- Authorization Server SecurityFilterChain 设计
- Resource Server SecurityFilterChain 演进方式
- internal client authentication、`/auth/login` password/email-code 路由，以及 AuthenticationConverter / AuthenticationProvider 设计
- user/admin 登录调用链
- email-code 发送、Redis 原子消费、限流与防枚举调用链
- refresh rotation 数据结构
- logout/revoke 调用链
- 旧四条 user/admin login/logout route 删除顺序及无 adapter 的迁移边界
- 精确文件计划
- 测试矩阵
- 上线切换和回滚策略

如果授权升级文档对 grant_type、client authentication 或旧接口兼容方式存在歧义，立即停止向我提问，不要自行决定。
```

---

## Phase 4：Authorization Code + PKCE + Consent

已确认的 Phase 4 实施口径：

- Authorization Endpoint 使用 Spring Authorization Server 默认`/oauth2/authorize`，Token Endpoint 固定为`POST /oauth/token`；`POST /oauth/logout`注销当前 core_agent + user session。
- 允许仅用于 Authorization Server 浏览器流程的后端 Thymeleaf 页面：`GET/POST /oauth/login`和`GET /oauth/consent`。它们不属于 USER/ADMIN internal JSON endpoint，且不修改 frontend/。
- SAS 7.0.4 保留`OAuth2AuthorizationEndpointFilter`、官方 GET/POST converter 与标准 redirect 响应形状；在`authorizationEndpoint.authenticationProviders(...)`中移除默认 code-request/consent provider，改接项目 provider。不得写 SAS `OAuth2Authorization`或 SAS authorization code；确认后唯一的 raw code 只写`CoreAgentAuthorizationCodeStore`，避免双写。
- 等待 consent 的完整授权 transaction 存 Redis、TTL 10 分钟；HttpSession 只保留 Java `SecureRandom`生成的 256-bit Base64URL `pendingHandle`，不得存 transaction、raw code、access token 或 refresh token。pending key 固定为`oauth2:authorize:pending:{pendingHandle}`。自定义 consent 表单仍`POST /oauth2/authorize`，带标准`client_id`、`state`、重复`scope`、CSRF token及`consent_action=approve|deny`；wrapper converter/details 把 action 交项目 provider。approve 必须服务端重算并强制 auto_approve，先幂等保存 MySQL consent，再用 Redis Lua 原子消费 pending、写 auth-code state/TTL和`user:auth_code:{user_id}:{client_id}`指针/同 TTL；Lua只做 keys/args/TTL校验与 Redis 变迁，不做密码学或版本判断，脚本失败不得写状态。deny 必须按标准 redirect 返回`access_denied`，不得由 hidden mandatory scope 决定。
- 浏览器授权首版仅 username/password；`email-code`仍只服务`user`、`admin` internal client。
- 浏览器 session 为 10 分钟，Cookie 为 HttpOnly、SameSite=Lax，生产 Secure；`POST /oauth/login` 的 CSRF 校验由`jacolp.oauth2.browser-login.csrf-enabled`控制，默认值为`false`，为`true`时必须校验 token，为`false`时仅豁免登录 POST。SAS 7.0.4 会忽略完整 endpoint matcher 的 CSRF，普通 DSL 无法精确撤销，因此增加独立 browser CSRF filter，始终精确保护`POST /oauth2/authorize`；`/oauth/token`与 Bearer`/oauth/logout`保持精确豁免。业务资源链继续关闭 CSRF。
- scope 缺省时使用 core_agent `auto_approve`作为 consent 候选；auto_approve 为强制项，用户不可取消，只可增加其余可选 scope。保存 consent 后，相同范围可复用，新增范围必须再次确认。
- Phase 4 migration 将 core_agent 改为 active；迁移期保留既有签名/校验链，新授权 token 使用 RS256，Phase 5 再完成全量 Resource Server 切换。该开关是临时迁移措施，Phase 6 已删除，不能作为最终运行模式。

```text
请先读取仓库内授权升级方案文档，以及前面阶段已经实现/确认的授权基础，然后规划 Phase 4。

本阶段只做规划，不修改代码。

目标：
1. 只为固定 client `core_agent`建立标准 OAuth Authorization Code + PKCE 后端能力；不得创建第三方 client、别名或恢复 `authorization_client`。
2. `core_agent`是 public client，以 PKCE S256 为核心保证；DB grants 为 `authorization_code,refresh_token`。首次授权唯一使用 `response_type=code`，不得使用 `agent_client`自定义 grant 或依赖 client_secret。
3. Authorization Endpoint 必须校验 active client、精确 registered `redirect_uri`、原始 `state`、唯一 S256、账号默认/extra grant；具体 Authorization Endpoint path 由 Authorization Server 设置确定，不能自行发明。
4. 使用 `oauth2_authorization_consent`保存 consent；effective scope 为 client scopes ∩ rank-role permissions ∩ 本次用户 consent，使用与 Phase 3 相同的逐分量 wildcard 模式求交，不做完整权限目录展开。
5. 本阶段明确允许在 Spring Authorization Server 标准流程的必要扩展中使用 Redis auth-code cache：256-bit code TTL 10 分钟、成功兑换原子删除；不得另起协议级 auth_code Controller。
6. cache 必须保存 client、精确 redirect URI、consent scopes、challenge/method、原始 socket remote address 与用户安全字段快照；pending transaction使用`oauth2:authorize:pending:{pendingHandle}`，`pendingHandle`与 raw auth code 均由 Java `SecureRandom`生成 256-bit Base64URL 值；`user:auth_code:{user_id}:{client_id}`原子指向当前 code。首版不删除旧 code key，兑换时必须校验指针仍指向该 code，旧 code 自然 TTL 失效。安全字段`status,role_id,password,email,username,extra_grant_types`变更时，仅精确吊销固定`core_agent`指针；不得用`SCAN`或`KEYS`，其他 client 的按用户前缀清理留后续扩展。事务内的 MySQL 写入及其他业务操作先完成，Redis 吊销最后执行但在 commit 前；Redis 失败抛出并回滚 MySQL，不做 after-commit、重试或补偿；若 Redis 先成功而 DB 最终 commit 失败，接受授权码提前失效这一 fail-safe 误伤，后续调整策略必须重新评估。
7. `POST /oauth/token`完成 authorization_code 兑换与 refresh 技术 grant；core_agent access token 1h、refresh token 24h，强制 rotation；`POST /oauth/logout`注销当前 client + user session。兑换 IP 变化只告警，不拒绝。
8. middleware-open-api-agent 仍然保持空模块，不实现任何 Agent API；不修改 frontend/。
9. 如果需要一个最小授权/consent 页面能力，只规划后端所必需的最小实现，不进行前端 Vue 改造。

重点：
- 优先使用 Spring Authorization Server 自带 Authorization Code / OAuth2AuthorizationService 能力
- 不新增协议级 auth_code Controller；仅按方案在 Spring Authorization Server 标准流程中实现受控 Redis auth-code cache 扩展

输出：
- 完整授权时序
- Authorization Server 相关扩展点
- consent 持久化
- PKCE 校验
- Redis auth-code 生命周期、user 指针和安全字段失效机制
- socket remote address 记录与 IP 变化告警（首版不拒绝，且不信任 X-Forwarded-For）
- `/oauth/token` code/refresh 与 `/oauth/logout`调用链
- 精确文件计划
- 测试矩阵
- 回滚方案

如果当前 Spring Authorization Server 能力、项目文档要求和现有代码之间存在关键冲突，立即停止并向我提问。
```

---

## Phase 5：统一 Resource Server 权限模型与后端鉴权收口

```text
请先读取仓库内授权升级方案文档和前面阶段实现结果，然后规划 Phase 5。

本阶段只做规划，不修改代码。

目标：
1. 将新 RS256 Access Token 正式作为 USER / ADMIN 请求的统一认证来源。
2. 将 roles / scope 正确映射到 Spring Security authorities。
3. 统一 401 / 403 行为。
4. 逐步用 Spring Resource Server 替换 LegacyJwtAuthenticationFilter 对 user/admin 请求的认证职责。
5. 保持 BaseContext / PermissionContext 对现有业务代码兼容。
6. 所有角色高低判断切换到 sys_role.rank。
7. 按授权升级方案落实 username 修改权限约束。
8. scope 权限和业务数据 ownership 必须分离。
9. middleware-open-api-agent 保持空模块，不增加业务接口。
10. 不修改 frontend/。
11. JWT scope 可原样保留 `*:read`等通配 scope；签发时以逐分量 wildcard 模式求交后保留结果，Resource Server 鉴权工具负责后续通配匹配，不在签发时展开为具体权限。
12. 仅接受固定 client `user`、`admin`、`core_agent`签发的目标 access token，不为其他 client 或已删除的 legacy route 保留认证旁路。
13. `/user/**`与`/admin/**`保留为第一方 client 入口边界：user client 仅进入`/user/**`，admin client（ADMIN 或 CREATOR 角色）仅进入`/admin/**`，不新增`/admin/creator/**`。路径边界不代替业务授权；路径内准入唯一由 JWT scope 与 route-to-scope 目录决定，不得用 ROLE_ADMIN、ROLE_CREATOR 或 client_id 代替 scope 授权。role 只用于身份、ownership 和 rank 管理等级。首版若某旧业务路由尚无明确 scope 映射，core_agent 必须拒绝进入，不能因其 role/client 获得临时旁路。
14. Phase 6 删除旧四个登录/登出路由后，路由映射以`static/document/security/phase5-business-route-scope-catalog.md`为唯一目录：120 条最终`/user/**`、`/admin/**`映射中，116 条 bearer 业务路由进入目录，4 条 public/activation 路由精确排除；五类资源`account/note/media/audio/audit`均有`read/write/manage`精确 scope；image-note 复合查询须同时满足两项 scope；user audit submit/cancel=`audit:write`，admin audit review=`audit:manage`。`*:super`不是默认路由 scope，ownership/rank/creator-only 仍为独立业务检查；core_agent 继续拒绝目录内旧业务路由。
15. 统一 HTTP 语义：认证缺失、无效或过期为 `401`；route scope、client 边界，及 service 层 ownership/rank/creator-only 业务权限拒绝均为 `403`。业务错误体保持 `Result.error` 契约，RS256 与 legacy 模式一视同仁；OAuth 协议端点继续使用 RFC OAuth 错误格式，不走业务 `Result` handler。

重点检查：
- 当前 /user/** 和 /admin/** 路由保护方式
- RequireSuperiorRole 等现有角色等级逻辑
- BaseContext / PermissionContext 使用范围
- Spring Method Security 是否需要在本阶段启用
- 当前异常返回格式和测试

输出：
- Resource Server 最终认证链路
- authority 映射规则
- route-to-scope 目录及其客户端无关的 scope 准入规则
- legacy filter 退出顺序
- role.rank 替换清单
- username 权限约束落点
- 兼容方案
- 测试矩阵
- 分批实施顺序
- 回滚方式

如果发现某些业务接口依赖旧 SecurityIdentity 的隐式语义，无法安全替换时先停止并列出具体位置等我确认。
```

---

## Phase 6：Legacy Security Cleanup（后端）

```text
请先读取仓库内授权升级方案文档和 Phase 1-5 的实际实现结果，然后规划 Phase 6：后端 Legacy Security Cleanup。

本阶段只做规划，不修改代码。

前置条件：
- 新 RS256 Authorization Server 已稳定
- USER / ADMIN 登录、refresh、logout 已稳定
- Authorization Code + PKCE 已稳定
- Resource Server 已正式接管 user/admin access token 验证

目标：
1. 删除不再使用的 LegacyJwtAuthenticationFilter。
2. 删除 user/admin HS256 登录 JWT。
3. 删除旧 user/admin secret 配置。
4. 删除旧 Redis “完整 JWT 单 Token Session” key 模型。
5. 清理旧 SecurityIdentity 中不再需要的登录身份语义。
6. 拆分旧 TokenSessionService 中 OAuth Session 与账号激活/验证码职责。
7. 判断 activation token 是否继续保留旧 JWT；如果授权升级方案没有明确要求，不自行重构，先列问题问我。
8. 如果 jjwt 已无用途，再规划移除依赖。
9. middleware-open-api-agent 仍为空模块。
10. 不修改 frontend/。
11. 授权目标已明确删除旧 `/user/user/login`、`/admin/user/login`、`/user/user/logout`、`/admin/user/logout`；本阶段核验无后端引用后完成路由、测试和配置清理，不保留 compatibility adapter。
12. activation token 保持 legacy，除非后续单独授权重构；不得因本清理阶段一并改变其协议或语义。
13. 删除迁移期开关及所有依赖它的条件分支；最终后端只能运行 RS256 授权链，不能以缺失配置或备用链退回 HS256。
14. RSA private/public PEM 为最终部署前置条件：应用必须在 key 缺失、不可读、格式错误或不匹配时 fail-closed。部署先配置并验证外部 PEM，再升级删除旧链路的版本。
15. 旧`adminId:*`、`userId:*` Redis 单 token key 只能在确认旧应用版本及外部消费者均已下线后，由运维通过分页`SCAN`和批量`UNLINK`清理；禁止`KEYS`，不在应用启动时自动扫描或删除。

输出：
- legacy reference inventory
- 可删除项 / 暂时保留项
- commit-by-commit 清理顺序
- 配置清理
- Redis key 清理
- 测试矩阵
- 部署注意事项
- rollback 方案

任何无法确认“是否还有外部调用方依赖”的 endpoint、配置或 Redis key，都不要直接规划删除，先停下来问我。
```

---

## Phase 7：后端授权升级最终验收

```text
请读取仓库内授权升级方案文档，以及 Phase 1-6 的实际实现结果，规划一次“后端授权升级最终验收”。

本阶段只做验证规划，不修改功能代码。

本轮不包含：
- frontend/ 改造
- middleware-open-api-agent 业务接口
- Agent 实际数据读取 API
- OAuth 管理后台，以及仅 creator 管理 registered OAuth client 的 endpoint/DTO、client secret 生命周期、审计和启停安全规则
- OIDC
- 社交登录
- 多设备 Session
- Key Rotation 等下一轮增强能力

请核对以下后端目标是否全部完成：
- RS256 access token
- refresh token rotation
- logout/revoke
- jti blacklist
- 固定 Client 目录仅 `user`、`admin`、`core_agent`；`authorization_client`不存在，`agent_client`不是 grant
- RBAC permission
- role.rank
- USER/ADMIN internal `POST /auth/login`、`POST /auth/logout`；无 `client_secret`、`password|email-code`、旧四条 login/logout route 已删除且无 adapter
- 独立 `POST /oauth/email-code`的防枚举、限流、BCrypt Redis verifier、失败失效与原子消费
- `sys_user.extra_grant_types`与配置默认账号 grant 集合；refresh 仅作为 client 技术 grant
- core_agent 标准 Authorization Code + PKCE S256、严格 redirect URI 和 original state
- consent
- auto_approve
- allowed_ips
- core_agent pending transaction/auth-code 均为10分钟、一次性兑换、`user:auth_code:{user_id}:{client_id}`原子指针、Lua pending→code 变迁及安全字段失效计划（后续非阻塞 SCAN，禁止 KEYS）
- core_agent `/oauth/token` authorization_code + refresh、1h/24h TTL、强制 rotation，以及 `/oauth/logout`
- SecurityContext / BaseContext / PermissionContext 兼容
- legacy user/admin JWT 已按计划收口
- activation token 仍按既有协议独立运行
- middleware-open-api-agent 仅为空模块占位

输出：
1. 验收 checklist
2. 自动化测试清单
3. 手工 E2E 测试清单
4. 数据库验证项
5. Redis 验证项
6. 安全边界验证项
7. 仍未完成但明确属于“前端后续计划 / Agent API 后续计划 / OAuth client 管理独立阶段”的事项
8. 是否满足“后端授权升级第一阶段完成”的结论

如果存在任何会影响后端正式切换的未决问题，立即停止并列出问题，不要用假设填补。
```

Phase 7 的可执行验收矩阵、真实环境门禁、证据模板与发布边界见
[Phase 7 后端授权升级验收与发布 Runbook](phase7-backend-authorization-acceptance-and-release-runbook.md)。首发部署固定为单节点 Redis；Redis Cluster 的多 key Lua `CROSSSLOT` 风险属于后续专项，不得在首发启用。Testcontainers 暂不引入，真实单节点 Redis/MySQL/SMTP/HTTP E2E 证据均不得由 Mockito 测试替代，缺失时会阻塞正式切换结论。
