# S3-B-04 派单页面与授权交接

日期：2026-10-08。基线：团队 B3 的 `2cec5b8`（S3-B-03 派单事务）。
本阶段在本地 `feature/SCRUM-UserB4-WANGPENGRUI` 开发，按用户要求交付到团队 `feature/SCRUM-UserB3-WANGPENGRUI`；不新增迁移或运行时依赖。

## 管理员操作

- 从 `/admin/requests` 请求队列或请求详情的 **Manage assignment** 进入派单页。
- 页面显示请求、地点、类别、有效优先级、当前技师及推荐列表。推荐保持 B02 的可用性 → 在办工单数 → 档案 ID 排序；候选使用账号 ID 提交。
- 审核完成且为 `UNDER_REVIEW` 时可指派。已指派、处理中、重新打开时可改派；改派原因必填，最多 500 字符。仅 `REOPENED` 允许再次指派当前技师。
- 仅 `ASSIGNED` 可撤回，原因必填。业务状态、工单、历史和事件仍由 B03/C 的同一事务处理。
- 成功后重定向回派单页并显示提示；输入不合法返回 400 并保留原因。400 沿用仓库现有全局异常处理与技师资料页约定，区别于计划 §13.4 的早期 200 表述。
- 资格变化、状态变化、旧指派 ID 或并发冲突返回 409，显示刷新链接并禁用页面写操作。不会自动用新的指派 ID 重交旧表单。
- 无候选、地点停用、审核未完成、不可派单状态均有文字说明，不提供可执行的指派按钮。
- 技师资料页新增 **My work orders** 链接，接入 C 已实现的工作台。

## 路由和安全边界

| 方法 | 路径 | 权限 |
| --- | --- | --- |
| GET | `/admin/requests/{ticketNumber}/dispatch` | 有效管理员 |
| POST | `/admin/requests/{ticketNumber}/assign` | 有效管理员 + CSRF |
| POST | `/admin/requests/{ticketNumber}/reassign` | 有效管理员 + CSRF |
| POST | `/admin/requests/{ticketNumber}/withdraw` | 有效管理员 + CSRF |

以上路径的其他方法被明确拒绝，避免被既有 `/admin/**` 规则放行。
操作者只从登录会话取得；表单只能提交技师、预期指派 ID、原因。所有写操作再次调用 B03 服务检查资格与状态。

`SecurityConfig` 同时接入已确认的计划 §13.2 社区、§13.3 通知/公告及管理员/技师仪表盘路由。
测试覆盖三角色、匿名访问、CSRF、社区 `/questions/new` 与 ID 路由顺序及未列出方法。
管理员的其他模块路由沿用计划规定的 `/admin/**` 覆盖。
尚未实现的 A/D/E 页面仅验证**路由授权契约**，没有添加虚假页面或导航；这些模块仍需各自完成作者、收件人、数据范围检查。
设施状态沿用实际控制器 `POST /admin/facilities/{id}/status`，而非计划 N-24 表格中含混的 GET 表述。

## 实现和共享文件

- `DispatchPageService` 通过请求、地点、用户模块的公开服务组合只读数据，不访问其他模块仓储。
- `DispatchController` 负责表单、错误、会话身份和重定向；事务仍在 `AssignmentService`。
- `dispatch/assign.html` 复用实际仓库的 `head/sidebar/appbar/footer/scripts` 及现有组件样式。`ui-guide.md` 的旧 `topbar` 示例与实际布局不同，本次以已运行布局为准。
- 原生 HTML 表单工作不依赖 JavaScript；使用标签、表格列/行标题、可读状态文字和自动转义。
- 共享改动：B 负责的 `SecurityConfig.java`；`pom.xml` 仅将 PostgreSQL 页面测试加入现有 profile 的默认排除列表，不增加依赖。
- C 的请求详情/队列模板仅增加派单入口；合并时与 C 核对。共享布局、公共 CSS、首页均无改动。

## 验证与复现

`DispatchPageIT` 使用真实服务、Thymeleaf、安全过滤器和数据库事务；`DispatchPagePostgresIT` 在独立随机 PostgreSQL schema 中继承同一组验收用例，完成后清理自己的 schema。

覆盖：指派 → 改派 → 撤回、工单与权限变化、伪造操作者、资格变化、过期表单、输入绑定失败、原因回显与 XSS 转义、空列表、状态按钮、匿名/非管理员/停用会话及 CSRF。

```powershell
mvn -B clean verify
# 在专用 *_test 数据库设置 TEST_DB_URL、TEST_DB_USERNAME、TEST_DB_PASSWORD 后：
mvn -B -Ppostgres-it clean verify
```

Docker 测试使用现有 `smartfix-b03-postgres-test`（PostgreSQL 16.15）；映射端口会随启动变化，应通过 `docker port smartfix-b03-postgres-test 5432/tcp` 查询。
测试口令仅在本机临时目录和进程环境中使用，不入库。
页面测试在 `target/dispatch-preview/` 输出合成数据的 HTML，供浏览器走查。

本轮结果：

| 检查 | 结果 |
| --- | --- |
| `mvn -B clean verify` | 281 单元/Web/仓储 + 95 集成，共 376 项通过 |
| `mvn -B -Ppostgres-it clean verify` | 281 + 173，共 454 项通过；零失败、错误、跳过 |
| 最后一次表格可读性调整后 | H2 / Docker PostgreSQL 页面验收各 21 项，共 42 项再次通过 |
| Edge 渲染 | 指派、改派、冲突、空列表 × 390 / 1280 / 1440 / 1920，共 16 个布局通过；整页无横向溢出 |
| 无 JavaScript 键盘路径 | 改派原因、技师按钮、撤回原因及按钮均可通过 Tab 到达 |
| 表单结构 | 控件标签、唯一 ID、描述引用和 POST CSRF 均通过；冲突页写按钮禁用 |
| PostgreSQL schema 清理 | 测试结束后残留测试 schema 为 0；容器保持运行 |

本机使用 Maven 3.9.14 / JDK 25.0.4，编译目标 Java 21；未宣称 JDK 21 CI 已运行。
普通回归期间本机响应变慢并出现 JVM 退出超时日志，但所有测试通过；随后的 Docker 全量回归约 67 秒正常结束。
日志位于本机临时目录 `smartfix-b04-verify.log`、`smartfix-b04-docker-verify.log`、`smartfix-b04-page-final.log`。
截图、浏览器检查脚本及 JSON 结果保存在本机 Codex 可视化目录的 `sprint3-b04-20261008` 文件夹；浏览器仅验证测试生成的页面，业务提交由真实服务集成测试验证。

## 后续协作

2026-10-08 更新：本阶段已推送团队 B3（`b3baaf8`）；后续 S3-B-06 的本地权限验证和撤回列表修复见 [技师权限交接](B_Technician_Access_Handoff_CN.md)，C 联合评审仍待完成。

下一项为 S3-B-06：与 C 联合复核技师读取请求、附件和工单的权限，以及改派/撤回后旧技师立即失权。
B03 与本阶段已经验证真实指派链路；联合评审和 E 的通知/审计监听器接线仍需团队完成，不将事件发布等同于已发送通知。
