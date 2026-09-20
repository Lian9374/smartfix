# Sprint 2 B 部分开发与交接

基于 A 的 `92e661b`，工作分支为 `codex/user-b-auth`。
原 Sprint 规划稿保留原有状态；本文记录此分支的实际实现与验证边界。

## 已实现的代码

| 文件/类 | 职责 |
|---|---|
| auth/security/SmartFixUserDetails | 登录主体，携带 userId、role、securityVersion；认证后擦除哈希 |
| auth/service/SmartFixUserDetailsService | 通过 A 的 UserService 加载用户；不存在用户转换成认证失败 |
| auth/security/ActiveAccountFilter | 每次认证请求检查账户状态、安全版本和角色；失效时清理会话与 Cookie |
| auth/config/SecurityConfig | Form Login、POST 登出、CSRF、会话固定攻击防护、角色路由规则、默认拒绝未登记路由 |
| auth/controller/LoginController | GET /login；POST 登录与登出由 Spring Security 处理 |
| common/exception/GlobalExceptionHandler | 统一安全错误页，映射 400/403/404/405/406/409/413/415/500 |
| common/configuration/TimeConfig | 注入 UTC Clock，供需要可测时间的服务使用 |
| common/web/HomeController | 登录后按角色显示入口，技术员受控占位页 |
| templates/login.html、home.html、error.html | 登录、角色首页、通用错误页 |

复用 A 已有的三个公共异常、PasswordConfig、UserAuthenticationData、
UserAccessResponse 和 UserService，未新增用户迁移或重复账户实现。
管理员页面继续使用 A 的 Controller；增加登出入口，时间以 Asia/Singapore 显示。

## 与其他成员对齐的接口

- 登录：`UserService.findAuthenticationByUsername(String)`。
- 会话检查：`UserService.getUserAccess(Long)`。
- 可信操作人：`@AuthenticationPrincipal SmartFixUserDetails principal`，
  使用 `principal.getUserId()`，不要从表单接收 requesterId。
- 角色/状态变化由 A 自增 securityVersion；B 在旧会话下一次请求时将其注销。
- 所有 POST 表单使用 Thymeleaf `th:action`，由框架生成 CSRF hidden input。
- C/D/E 抛出已有公共异常即可使用统一错误页；表单字段错误仍可就地回显。
- D 实现资源所有权（非所有者 404），E 的附件下载复用 D 的访问检查。
- SecurityConfig 已登记 C/D/E 规划路由；本分支尚无这些业务 Controller，
  因此首页的报修/代查链接需要合入对应模块后才能使用。
- A 的页面测试保持其原本的无过滤器单元测试范围；新增 B 的测试启用真实过滤器，
  并用集成测试验证管理员实际写操作。

## 本地验证

```powershell
mvn -B clean verify
```

Surefire 运行单元/Web 测试；Failsafe 运行 AuthenticationFlowIT。
报告分别位于 `target/surefire-reports` 与 `target/failsafe-reports`。
2026-09-20 本机执行结果：128 个单元/Web 测试 + 14 个认证集成测试，
共 142 个用例，失败 0、错误 0、跳过 0；`BUILD SUCCESS`。
认证集成测试使用独立 H2 表结构，不会操作开发 PostgreSQL。
`mvn test` 只运行单元/Web 测试，不能代替完整 verify。

测试覆盖：三种角色登录、用户名规范化、错误/停用凭据、CSRF、
角色路由矩阵、管理员禁止代提交、创建账户、多个旧会话失效、
角色变更后重新登录、登出、Session ID 轮换、凭据擦除、安全错误页。
另有真实 Tomcat HTTP 测试，使用页面生成的 CSRF token 和 Cookie 完成登录与 403 页面验证。

本机 Maven 当前使用 JDK 25，编译目标为 Java 21。团队/CI/容器基线仍是 JDK 21；
本机测试不冒充 JDK 21 或 Jenkins 的执行证据。

## 使用真实 PostgreSQL 验证迁移

准备专用测试数据库，例如 `smartfix_test`，在终端设置以下环境变量：
`TEST_DB_URL`（例如 `jdbc:postgresql://localhost:5432/smartfix_test`）、
`TEST_DB_USERNAME`、`TEST_DB_PASSWORD`。不要把真实密码写入代码或文档。

```powershell
mvn -B -Ppostgres-it clean verify
```

该 profile 额外运行 MigrationIT，验证空 schema 迁移、重复迁移、唯一约束与角色约束。
它创建并清理自己的随机 schema，拒绝名称不以 `_test` 结尾的数据库。
缺少环境变量或数据库不可用会失败，不会静默跳过。
默认 verify 明确不执行 MigrationIT，因此默认构建成功不代表真实迁移已验证。

## 启动和首次管理员登录

需要可用的 PostgreSQL。正式应用由 Flyway 建表，保持 `ddl-auto: none`。

1. 参考 `.env.example` 配置数据库。
2. 首次创建管理员时设置：
   - `SMARTFIX_BOOTSTRAP_ADMIN_ENABLED=true`
   - `SMARTFIX_BOOTSTRAP_ADMIN_USERNAME`：自选管理员用户名
   - `SMARTFIX_BOOTSTRAP_ADMIN_PASSWORD`：自选强密码（满足 A 的密码规则）
   - `SMARTFIX_BOOTSTRAP_ADMIN_DISPLAY_NAME`：可选显示名
3. 使用 `mvn spring-boot:run` 启动，打开 `http://localhost:8080/login`。
4. 登录管理员后进入用户管理，创建 Requester/Technician 验证登录。
5. 完成首次引导后可关闭 bootstrap 开关。A 的引导逻辑本身是幂等的。

Compose 会读取 .env 并将引导配置传给 app；在宿主机通过 Maven 启动时，
必须在当前终端设置环境变量，Spring Boot 不会自动读取 .env。

会话默认空闲 30 分钟；通过 SMARTFIX_SESSION_TIMEOUT 修改。
本地 HTTP 使用非 Secure Cookie；HTTPS 环境设置 SMARTFIX_SESSION_COOKIE_SECURE=true。

## CI 与容器改动

- Jenkins 保留 Checkout/Build/Unit Test/Package，并新增 Verify。
- 单独归档 Surefire/Failsafe 报告，缺失报告不能算通过；成功时归档 JAR。
- 可选参数 RUN_POSTGRES_IT 运行真实迁移测试，凭据通过 Jenkins 环境注入。
- Security 阶段显式跳过，构建描述注明扫描工具未启用。
- Docker 构建执行完整默认 verify，移除忽略失败和跳过测试的命令。
- Compose 传入首次管理员及会话配置，未改动已有数据库卷。
- 按工作文档，CI/构建文件改动应单独评审、拆成独立 PR；当前尚未创建 PR。

## 尚不能声称完成的验收

| 项目 | 实际状态 / 前置 |
|---|---|
| 真实 PostgreSQL MigrationIT | 测试已编写；本机未配置专用 PostgreSQL，未执行 |
| Docker 构建、容器重启和附件持久化 | 本机无 Docker 命令；附件还依赖 E |
| Jenkins 实际构建 | 流水线已准备；未连接 Jenkins 执行 |
| MaintenanceRequestFlowIT / 全报修闭环 | 等待 C/D/E 合入；路由探针测试不代表这些业务已完成 |
| 非所有者请求/附件 404 | D/E 所有权实现的联合验收，B 不冒充已测 |
| Testcontainers / JaCoCo / Checkstyle / Dependency-Check / Gitleaks / Trivy | 未启用 |
| 团队 ADR 确认、跨成员 PR Review | 待团队评审；ADR-002 为 Proposed |

建议先评审认证主链路，再接入 C/D/E，并在有 PostgreSQL/Docker 的环境补齐外部验收。
