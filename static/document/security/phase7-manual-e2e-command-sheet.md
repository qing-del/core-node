# Phase 7：隔离环境手工 E2E 命令表

本命令表配合[Phase 7 后端授权升级验收与发布 Runbook](phase7-backend-authorization-acceptance-and-release-runbook.md)
使用。它只适用于**隔离的、单节点 Redis、MySQL 和 SMTP stub/测试收件箱环境**，不为生产环境
提供可直接复制的写入命令。

它不保存密码、验证码、private PEM、raw authorization code、refresh token 或 bearer token。所有
凭据必须由验收负责人从会话级 secret manager 注入；不要放进 `.env`、PowerShell 历史、命令行、
evidence 文件或 Git。以下 `${...}` 和 `$env:...` 均为调用方提供的占位符，没有默认 host、port、
用户或密码。

## 0. 强制安全门禁

只读预检和静态验证可在目标环境之外执行；所有 `/auth/login`、`/oauth/email-code`、
`/oauth2/authorize`、`/oauth/token`、`/auth/logout`、`/oauth/logout`、MySQL migration 或 Redis
状态检查流转，均视为 mutation 测试，必须先通过以下明确门禁：

```powershell
if ($env:PHASE7_ALLOW_MUTATION -ne 'I_UNDERSTAND_ISOLATED_TEST_ONLY') {
    throw 'Refusing mutation: set the exact isolated-environment acknowledgement in this process only.'
}
if ([string]::IsNullOrWhiteSpace($env:PHASE7_BASE_URI) -or
    [string]::IsNullOrWhiteSpace($env:PHASE7_ENVIRONMENT_NAME)) {
    throw 'Refusing mutation: explicit isolated base URI and environment name are required.'
}
Write-Host ('Running only against isolated environment: ' + $env:PHASE7_ENVIRONMENT_NAME)
```

禁止以下操作：

- 对生产或拓扑不明的 Redis/MySQL 运行本表 mutation；
- `FLUSHALL`、`FLUSHDB`、`KEYS`，或按宽泛模式删除 Redis key；
- 输出、保存或截图 token/code/password/PEM；
- 使用 Docker/compose 启动临时服务代替已批准的隔离环境；
- 把 Cluster 作为首发 Redis 验收目标。

若目标不是单节点 Redis、SMTP 不是测试收件箱/stub、或环境名无法由部署负责人确认，停止并将
Phase 7 标记为阻塞。

## 1. 只读预检与静态验证

先在仓库根目录运行。脚本只创建本地 evidence 文件；不会启动服务、Docker、Redis 或 MySQL。

```powershell
$evidence = Join-Path $PWD 'phase7-evidence'
.\scripts\phase7\Test-AuthorizationEnvironmentPreflight.ps1 `
  -BaseUri $env:PHASE7_BASE_URI `
  -RedisHost $env:PHASE7_REDIS_HOST `
  -MySqlHost $env:PHASE7_MYSQL_HOST `
  -EvidenceDirectory $evidence

.\scripts\phase7\Invoke-AuthorizationStaticVerification.ps1 `
  -EvidenceDirectory $evidence
```

`Test-AuthorizationEnvironmentPreflight.ps1` 固定检查 liveness、readiness、Redis TCP 和 MySQL TCP。
它不默认检查 JWK：当前首发 security chain 有意不暴露 metadata/JWK 路径；只有后续单独启用
公开 JWK Set 时才可显式传入 `-JwkSetUri`。

静态验证使用 JDK 21、Maven `-o` 离线模式和下列模块：`middleware-common-security`、
`middleware-common-web`、`middleware-module-system-biz`、`middleware-server`。依赖不在本地
Maven cache 时应失败，不得取消 `-o` 来下载依赖。

## 2. 证据与脱敏约定

每个 mutation case 仅记录：case ID、UTC 时间、环境名、HTTP status、响应字段名、Redis key
**名称**、TTL、MySQL 行数/不变量和结论。不要记录响应 body 或 Redis value。示例：

```powershell
function Add-Phase7Evidence {
    param([string]$CaseId, [int]$HttpStatus, [string]$Conclusion)
    [PSCustomObject]@{
        caseId = $CaseId
        atUtc = [DateTime]::UtcNow.ToString('o')
        environment = $env:PHASE7_ENVIRONMENT_NAME
        httpStatus = $HttpStatus
        conclusion = $Conclusion
    } | ConvertTo-Json | Add-Content -Encoding utf8 (Join-Path $evidence 'manual-e2e-sanitized.jsonl')
}
```

Token/code 仅可在当前 PowerShell 进程的变量中暂存，并在 case 结束时执行
`Remove-Variable -Name accessToken,refreshToken,authorizationCode -ErrorAction SilentlyContinue`。不得
使用 `Write-Host`、`Write-Debug`、`ConvertTo-Json` 或 transcript 输出这些变量。

## 3. HTTP E2E 顺序

以下为操作顺序与必须观察的结果；请求 body 由验收负责人按当前 DTO 使用隔离测试账号构造。
先执行第 0 节门禁。

| Case | 请求/动作 | 必须验证的结果 |
| --- | --- | --- |
| H1 | `POST /auth/login`：user/password；再 admin/password。 | 仅 `user/admin` 接受，无 client secret；返回 RS256 access/refresh，但证据只记字段名与 HTTP status。 |
| H2 | 使用 H1 的 refresh 调用 `POST /auth/login`，并携带 `grant_type=refresh_token`；`/oauth/token` 必须拒绝 user/admin。 | 只发新 access/refresh；旧 refresh 立即失败；两个并发 refresh 仅一个成功。 |
| H3 | 使用 H1 access 调用 `POST /auth/logout`。 | 当前 access 后续为 401，当前 refresh/session 删除，另一 client 同用户会话不受影响。 |
| H4 | `POST /oauth/email-code`，从 SMTP stub/test inbox 取得 code 后以 `grant_type=email-code` 调用 `/auth/login`。 | 防枚举响应、60 秒冷却、每小时 5 次、10 分钟 TTL、一次兑换、第五次错误失效；SMTP 故障时 state 删除而限流保留。 |
| H5 | 浏览器按 `/oauth2/authorize` → `/oauth/login` → `/oauth/consent` → callback 流程完成 `core_agent` PKCE S256。 | 仅 `core_agent`、精确 redirect URI、original state、auto_approve 不可取消；deny 返回 RFC `access_denied`。 |
| H6 | 将 callback code 与 verifier 交换到 `POST /oauth/token`。 | auth-code 仅兑换一次；错误 verifier/client/redirect 均失败；IP 改变只产生告警。 |
| H7 | `core_agent` refresh 与 `POST /oauth/logout`。 | 1h access、24h refresh、强制 rotation；仅当前 `(client,user)` session 被注销。 |
| H8 | 使用缺失、伪造、过期、blacklisted、scope 不足、client boundary 不符的 JWT 访问业务路由。 | 前四类为 401；scope/client/ownership/rank/creator-only 为 403；OAuth 协议错误仍是 RFC 格式。 |
| H9 | 分别修改 `status,role_id,password,email,username,extra_grant_types`。 | 业务 MySQL 操作完成后、commit 前精确吊销当前 `core_agent` auth-code pointer；Redis 故障使 MySQL 回滚。 |
| H10 | activation 与 email-change 原有凭据流程。 | 仍独立运行，且不接受或恢复 user/admin HS256 bearer 链。 |

对 H1–H8 的 HTTP 调用使用 session 内变量，不要将变量值传入命令行。例如，可只读取 response
字段名并记录状态：

```powershell
$response = Invoke-WebRequest -Method Post -Uri ($env:PHASE7_BASE_URI + '/auth/login') `
  -ContentType 'application/json' -Body $isolatedLoginBody -UseBasicParsing
Add-Phase7Evidence -CaseId 'H1-user-password' -HttpStatus $response.StatusCode `
  -Conclusion 'Inspect response fields in memory only; do not persist token values.'
```

`$isolatedLoginBody` 必须由隔离测试凭据在内存中生成；本文件刻意不提供用户名、密码、邮箱、
验证码、token 或默认 host。

## 4. Redis 真实 Lua、TTL 与并发

使用与应用相同的隔离单节点 Redis，配备 `redis-cli`，并通过会话级安全注入提供认证；不要在
命令行中添加 `-a password`。仅在第 0 节门禁通过后，以实际应用流转产生的随机测试 key 做只读
核验：

```powershell
if (-not (Get-Command redis-cli -ErrorAction SilentlyContinue)) {
    throw 'redis-cli is required for manual Redis evidence; do not substitute KEYS or an unauthenticated client.'
}
# The caller supplies REDISCLI_AUTH in this process from the approved secret manager.
redis-cli -h $env:PHASE7_REDIS_HOST -p $env:PHASE7_REDIS_PORT PTTL 'oauth2:authorize:pending:{<random-handle>}'
redis-cli -h $env:PHASE7_REDIS_HOST -p $env:PHASE7_REDIS_PORT PTTL 'oauth2:auth_code:{<random-code>}'
redis-cli -h $env:PHASE7_REDIS_HOST -p $env:PHASE7_REDIS_PORT PTTL 'user:auth_code:{<test-user-id>}:{core_agent}'
```

记录 key 名和 `PTTL`，不记录 `HGETALL` 的 value。按下表利用 H2/H4/H5/H6/H7 的应用流转完成
全部脚本的真实验证；对需要并发的行，从两个独立 PowerShell 进程在同一屏障后同时提交同一
HTTP 请求，再只记录两个 HTTP status 和最终 key 是否存在。

| 脚本 | 通过应用流转产生 | TTL/并发证据 |
| --- | --- | --- |
| `core_agent_pending_authorization_save.lua` | H5 授权请求。 | pending TTL 约 10 分钟；失效后不可继续。 |
| `core_agent_pending_authorization_to_code.lua` | H5 approve。 | pending 消失；code+pointer 同 TTL；双 approve 仅一成功。 |
| `core_agent_authorization_code_consume.lua` | H6 exchange。 | code+pointer 同时删除；双 exchange 仅一成功。 |
| `core_agent_authorization_code_replace.lua` | 仅隔离环境中连续授权两次。 | pointer 切换到新 code，旧 code 不再可 CAS 兑换。 |
| `core_agent_authorization_code_invalidate.lua` | H9 安全字段变更。 | 当前 pointer/code 删除；不存在 pointer 时幂等。 |
| `replace-current-session.lua` | H1 成功登录。 | refresh/session TTL 与 client settings 一致。 |
| `rotate-current-session.lua` | H2 refresh。 | old refresh 删除；新 refresh/session 正确；双请求仅一成功。 |
| `revoke-current-session.lua` | H3/H7 logout。 | blacklist TTL 不超过 access 剩余寿命；只删除当前 client session。 |
| `email_login_code_issue_rate_limit.lua` | H4 重复发送。 | email/IP 冷却与小时计数不可并发突破。 |
| `email_login_code_state_replace.lua` | H4 重发。 | `(client,user)` 单 state、TTL 约 10 分钟。 |
| `email_login_code_state_consume.lua` | H4 正确兑换。 | state 一次删除；双兑换仅一成功。 |
| `email_login_code_state_record_failure.lua` | H4 连续错误五次。 | 失败计数不续 TTL，第五次删除。 |

不要手工 `EVAL` 项目 Lua 来伪造输入或绕过 Java 的 SecureRandom/BCrypt/DTO 校验；真实证据应覆盖
Java 到 Redis 的实际调用链。脚本参数非法且“不写状态”的情况由现有单元测试覆盖；若需要真实
Redis 非法参数验证，应作为单独批准的测试工具工作，不在本表临时构造。

## 5. MySQL fresh、forward migration 与只读后置检查

Fresh database 与 existing database upgrade 均必须在隔离 MySQL 8 实例完成，且先备份。现有授权
migration 是 forward-only 静态 SQL，不由应用自动追踪。执行顺序、preflight/postflight 和回滚边界
以 Runbook 为准。

通过安全注入提供 `MYSQL_PWD` 后，以下只读查询可记录不变量计数；不要把查询结果中的敏感字段
全文存入 evidence：

```powershell
if (-not (Get-Command mysql -ErrorAction SilentlyContinue)) {
    throw 'mysql CLI is required for manual MySQL evidence.'
}
mysql --protocol=tcp --host=$env:PHASE7_MYSQL_HOST --port=$env:PHASE7_MYSQL_PORT `
  --user=$env:PHASE7_MYSQL_USER --database=$env:PHASE7_MYSQL_DATABASE `
  --skip-column-names --execute "SELECT COUNT(*) FROM oauth2_registered_client;"
mysql --protocol=tcp --host=$env:PHASE7_MYSQL_HOST --port=$env:PHASE7_MYSQL_PORT `
  --user=$env:PHASE7_MYSQL_USER --database=$env:PHASE7_MYSQL_DATABASE `
  --skip-column-names --execute "SELECT COUNT(*) FROM sys_permission WHERE status = 'active';"
```

验收负责人必须复核：三 client（无 `authorization_client`/`agent_client` grant）、三角色及 rank、
19 active permission、role-perm、`extra_grant_types` 语义、consent 主键，以及 Phase 1/3/4/5 的每个
preflight/postflight 输出。不得把 `createDatabase.sql` 再导入已有库，也不得为“重跑”迁移而手工
修改数据跨过 `SIGNAL SQLSTATE`。

## 6. 旧 Redis key 的运维清理

`adminId:*`、`userId:*` 清理不是 E2E 脚本步骤。只有旧版本和外部消费者已经书面下线确认后，
运维负责人才能在维护窗口按 Runbook 用 cursor 分页 `SCAN MATCH` 与批量 `UNLINK` 执行，并记录
每批 cursor/数量/操作者。此命令表故意不提供自动清理循环；禁止 `KEYS` 和应用启动自动清理。

## 7. 结束与结论

完成后清除会话内敏感变量，保存脱敏 evidence，并在 Runbook 模板中记录通过、失败或阻塞。若任一
真实 Redis/MySQL/SMTP/HTTP/PEM 门禁缺少证据，结论只能是“正式切换阻塞”，不能用 Mockito、
脚本源码审阅或静态 Maven 通过替代。
