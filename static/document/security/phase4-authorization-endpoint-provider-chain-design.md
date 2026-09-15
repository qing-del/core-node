# Phase 4：SAS 授权端点 Provider 链兼容设计

## 结论

首发继续使用 Spring Authorization Server（SAS）7.0.4 的标准
`OAuth2AuthorizationEndpointFilter`，不另建协议 Controller 或自定义 Filter 来替代它。
该 Filter 保留 OAuth 2.0 授权请求的标准参数解析、异常与重定向响应形状；CORE AGENT
业务语义则由项目自己的 Provider 完成。

授权端点的 Provider 顺序固定为：

1. 项目 `CoreAgentAuthorizationCodeRequestAuthenticationProvider`；
2. 项目 `CoreAgentAuthorizationConsentAuthenticationProvider`；
3. SAS 官方 `OAuth2AuthorizationCodeRequestAuthenticationProvider`（仅兼容 bootstrap）。

前两个项目 Provider 必须覆盖各自支持的官方 token 类型，并且每次调用都只能**返回成功
Authentication**或**抛出异常**，不得返回 `null`。因此正常 CORE AGENT 请求不会流入第三项。

## 为什么保留 SAS 官方 AuthorizationEndpointFilter

保留标准 Filter 的价值是维持 SAS 7.0.4 对 `/oauth2/authorize` 的标准协议边界：授权请求与
consent 请求的官方 Converter、标准 OAuth 错误、以及通过已可信 `redirect_uri` 返回授权结果的
响应形状。项目不复制这些协议层细节，也不让前端或业务 Controller 重建授权端点。

这不意味着采用 SAS 的授权码持久化。Phase 4 的授权码与待确认事务仍是项目 Redis 状态，SAS
`OAuth2AuthorizationService` 被 `FailClosedOAuth2AuthorizationService` 明确拒绝，不能存储或
读取 OAuth2Authorization 记录。

## SAS 7.0.4 的兼容点

SAS 7.0.4 的 `OAuth2AuthorizationEndpointConfigurer` 在初始化时，从官方
`OAuth2AuthorizationCodeRequestAuthenticationProvider` 取得一个内部授权请求校验器，再把它传给
内建 `OAuth2AuthorizationEndpointFilter`。当 Provider 列表完全清空官方 Provider 时，该内部校验器
为 `null`，Filter 无法构建。

因此第三项官方 Provider 仅保留为 **validator/init bootstrap**：

- 必须位于两个项目 Provider 之后；
- 不作为实际 CORE AGENT 授权码签发路径；
- 其构造依赖仍使用 `ActiveRegisteredClientRepository` 与
  `FailClosedOAuth2AuthorizationService`；
- 若错误地收到本应由项目 Provider 处理的请求，不能产生 SAS 授权码或 SAS
  OAuth2Authorization；其持久化调用会 fail-closed。

这是 SAS 7.0.4 首发兼容措施。升级 SAS 时必须重新审查该内部初始化约束；若公开 API 已允许单独
设置校验器，应移除这个 bootstrap Provider。

## 实际 CORE AGENT 授权链

正常请求不是由官方 Provider 发码，而是经过下列项目链：

```text
/oauth2/authorize
  -> 项目 request / consent Provider
  -> Redis pending authorization
  -> Java SecureRandom 生成 auth_code
  -> 短 Lua 原子 pending -> auth-code 转换
  -> /oauth/token（项目 PKCE ExchangeService）
```

项目 Provider 在授权前后重新约束 client、redirect URI、用户安全快照、scope 与 consent；授权码兑换
再由项目 ExchangeService 验证 PKCE S256。SAS 官方 bootstrap Provider 不读取或验证项目授权码，
更不承担 PKCE 校验。

## 不采用的方案

不选择自定义授权 Filter 或 Controller：这会绕过 SAS 标准授权端点的 Converter/错误/重定向机制，
并形成第二套协议实现。

不通过反射访问或复制 SAS private 实现：这会把首发行为绑定到私有类、私有方法及其内部字段，升级
风险不可接受。保留最后一个官方 Provider 是使用公开配置 DSL 所能实现的最小兼容边界。

## 必须锁定的测试约束

- Provider 链顺序严格为“项目 request、项目 consent、官方 bootstrap”；
- 项目两个 Provider 对其支持 token 全覆盖，成功返回或失败抛错，绝不返回 `null`；
- 官方 bootstrap Provider 处于最后，且其意外执行会触发
  `FailClosedOAuth2AuthorizationService`，不能签发或持久化授权码；
- 真实授权码只能走 Redis pending、Java 随机码与短 Lua 原子转换；
- `/oauth/token` 的 PKCE 只由项目 ExchangeService 校验，不依赖 SAS
  OAuth2Authorization。

相关总体规则见[授权升级草案](授权升级-草案.md)和
[分阶段 Prompts](core-node-auth-upgrade-codex-phase-prompts.md)。
