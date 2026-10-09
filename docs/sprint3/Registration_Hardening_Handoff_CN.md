# 自助注册限流交接（2026-10-09）

关联：UserA 收口项 14、19；自助注册沿用 S3-A 账号模块和 B 的认证链。

## 行为与范围

`POST /register` 在字段校验结果处理、数据库访问和 BCrypt 编码之前，原子预留一次尝试。
默认同一连接地址在滚动一小时内最多 5 次；全实例一分钟最多 100 次。
无效输入、已占用用户名、新会话都计入配额。缺少 CSRF 的请求在安全过滤器中拒绝。
超额返回 HTTP 429 和向上取整的 `Retry-After` 秒数，保留非密码输入、清除两项密码。
GET 注册页面及登录流程不受该计数器限制。

计数器通过同步临界区保证单实例并发上限，最多保存 4,096 个未过期地址。
容量用尽时拒绝新地址，不能通过挤掉旧地址桶重置配额；窗口到期会回收空桶。
它是应用内存状态，应用重启会清空，不能声称跨实例或跨重启限额。
多实例公开部署应在可信入口加共享限流；当前完成的是单实例注册防刷。

只读取 `request.getRemoteAddr()`；基础配置明确设置 `server.forward-headers-strategy: none`，
应用不自行解析 `X-Forwarded-For`。部署在反向代理后时，必须先明确可信代理范围或在入口限流，
否则来自同一代理的访客会共用地址配额。不能为了区分访客而直接信任客户端传入的转发头。

配置键（可通过环境变量按 Spring Boot 规则覆盖）：

- `smartfix.registration.rate-limit.max-per-address`：5。
- `smartfix.registration.rate-limit.window`：`PT1H`。
- `smartfix.registration.rate-limit.max-global-per-minute`：100。
- `smartfix.registration.rate-limit.max-addresses`：4096。

没有新增迁移、账号状态或角色；注册仍创建 ACTIVE REQUESTER，成功后需另行登录。
邮箱验证、验证码、分布式入口限流是独立部署范围，不宣称本次已实现。

## B 复核清单

本次应复核 `RegistrationController`、`RegistrationRateLimiter`、转发头设置，以及
429 响应、CSRF、密码不回显、账号类型选择与权限矩阵回归。
既有 PR #23 的 `SelectedAccountTypeAuthenticationProvider` 与注册绑定白名单也列为历史认证复核范围。
新 PR 会向 B 的 GitHub 账号 `Bogang233`（S3-B-06 PR #24 作者）请求评审；
请求评审与评审通过是不同状态，不把作者自测标记为 B 已批准。

## 验证入口

`RegistrationRateLimiterTest` 验证 12 次并发恰好放行 5 次、过期、全局上限及容量耗尽。
`RegistrationRateLimitIT` 验证无 CSRF、新会话、伪造转发头、字段错误及 429 密码清理。
`RegistrationFlowIT` 保留既有注册、角色固定、密码与唯一性场景；测试专用配额为 200，
以便它验证注册规则而非重复验证限流，新限流测试使用真实 2 次配额。
真实 PostgreSQL 注册唯一性仍由 `RegistrationConcurrencyPostgresIT` 验证。
本轮命令、结果和浏览器材料集中于 `UserA_Quality_Completion_CN.md`。
