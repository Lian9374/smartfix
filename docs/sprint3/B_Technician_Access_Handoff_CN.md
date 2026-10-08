# S3-B-06：技师读取权限与撤回后的列表修复

日期：2026-10-08。初始代码基线：团队 B3 的 `b3baaf8`（S3-B-04）。
独立交付分支：`feature/SCRUM-UserB6-WANGPENGRUI`，接入主干 `c658b0a`。
本机验证与 C 联合评审分开记录；不将合并授权或自动化测试记作队友评审。

## 已复现的问题

C 的 `RequestAccessService` TECH 分支已经通过 `RequestAssignmentAccessService` 查询 B 的有效指派。
请求详情、附件下载、工单详情和工单写操作会拒绝已失去指派的技师。
但 `WorkOrderService.findMine` 原先只按 `work_orders.technician_id` 分页。
撤回指派会保留工单的历史技师 ID，因此旧技师仍能从“我的工单”列表看到该请求及总数。

在已有 `AssignmentIT.withdrawsRevokesAccessAndAllowsASeparateNewAssignment` 中加入列表断言后，
修复前稳定失败：预期撤回后总数 0，实际仍为 1。复现日志：本机临时目录 `smartfix-b06-repro.log`。

## 修复方式

1. B 的 `AssignmentReadService` 增加 `findActiveRequestIdsForTechnician(Long technicianId)`，只返回该账号当前有效指派的请求 ID，返回集合不可变。
2. C 的主干 PR #22 已合入 `findActiveRequestIds(technicianId, candidateRequestIds)`。B 的适配器实现此接口，用一次批量查询取代逐个请求查询，再与候选工单的请求 ID 求交集。
3. C 的 `RequestAssignmentAccessService` 转交不可变结果；无适配器时返回空集合，不用历史工单归属作为授权依据。
4. 保留 C 主干的 `WorkOrderService.findMine`：先确认有效技师，再取得候选工单 ID 并通过公开接口校验有效指派，最后按“技师账号 + 有效请求 ID”筛选并分页。
5. 内容查询和分页总数使用相同筛选条件，避免分页后再过滤造成空洞页、错误总数或历史记录泄露。

没有跨模块读取仓储，没有更改请求状态、附件规则、指派历史或数据库迁移。
已完成的工单只要仍有当前有效指派，仍能在本人列表中查看；“在办数量”依旧排除已完成工单。

## 给 C 的接口评审说明

原有 Day 1 的 `ActiveAssignmentLookup.findActiveAssignment(Long)`、
`RequestAccessService.requireReadableRequest(...)`、派单写服务签名保持不变。
本次沿用 C 已合入的 `ActiveAssignmentLookup.findActiveRequestIds(Long, Collection<Long>)`，
不另起并行 SPI，也不覆盖 C 的工单服务和测试夹具。

旧适配器保留 C 的默认实现：逐条核对当前指派的请求 ID 和技师账号；B 的生产适配器覆盖为批量读取。
缺失适配器仍返回空集合。新增测试验证默认兼容行为、批量结果不越出候选范围，以及返回值不可变。
本地验证不代替接口评审。

## 访问矩阵

| 身份 / 状态 | 请求详情 | 该请求附件 | 工单详情 / 写操作 | 我的工单 |
| --- | --- | --- | --- | --- |
| 当前有效技师 | 200 | 200，私有、禁止缓存 | 可读；写操作继续受状态机限制 | 仅当前有效指派 |
| 未指派、旧技师 | 404 | 404，存储层未读取文件 | 404 | 不出现失权记录，总数同步减少 |
| 请求提交人 | 200 | 200 | 403 | 403 |
| 其他请求人 | 404 | 404 | 403 | 403 |
| 管理员 | 200 | 200 | 403（技师路由） | 403 |
| 停用 / 改角色 / 安全版本过期的既有会话 | 302 到登录失效提示 | 同左，存储层未读取文件 | 同一全局会话过滤器 | 同一全局会话过滤器 |
| 未登录 | 302 到登录页 | 302 | 302；缺 CSRF 的 POST 403 | 302 |

ID 猜测和票号/附件 ID 混配均返回 404，不用不同错误泄露资源是否存在。
改派与撤回测试复用同一个 `MockHttpSession`，不会通过重新登录掩盖缓存授权问题。
附件使用实际私有临时目录、有效 PNG、真实元数据和真实下载响应；存储 spy 仅验证拒绝时没有打开文件。

## 验证

- 新增 `TechnicianAccessIT`：11 个场景，覆盖当前/旧技师、不同请求人的请求和附件、真实会话失权、分页、已完成工单、匿名访问及 CSRF。
- `TechnicianAccessPostgresIT` 在 Docker PostgreSQL 的独立随机 schema 上继承同一组测试。
- 扩充请求授权与适配器单测，覆盖缺失适配器、错误请求 ID、技师账号不符、旧适配器的逐条授权、批量读取边界及读取失败传播。
- 扩充 B03 撤回验收，同时保留 C 的独立工作流测试夹具；未以 mock 替代 B/C 真实链路的权限判断。
- 每个原生测试 schema 完成后单独清理；上传测试文件只在本轮临时目录中创建和清理。

复现命令：

```powershell
mvn -B clean verify
# 为专用 *_test 数据库设置 TEST_DB_URL、TEST_DB_USERNAME、TEST_DB_PASSWORD 后：
mvn -B -Ppostgres-it clean verify
```

2026-10-08 初始基线 `b3baaf8` 的实测结果（不代表最新主干结果）：

| 检查 | 结果 |
| --- | --- |
| 修复前的撤回列表回归 | 失败，预期 0 条、实际 1 条，确认缺陷可复现 |
| 权限单测 + B03 / B06 定向验收 | 12 单测 + 36 集成通过 |
| `mvn -B clean verify` | 288 单元/Web/仓储 + 106 集成，共 394 项通过 |
| `mvn -B -Ppostgres-it clean verify` | 288 + 195，共 483 项通过；零失败、错误、跳过 |
| 新增跨模块权限场景 | H2 与 Docker PostgreSQL 各 11 项通过 |
| 隔离清理 | 测试 schema 残留为 0，Docker 测试容器保持健康 |

Docker 使用 `smartfix-b03-postgres-test`，PostgreSQL 16.15；映射端口从 `docker port` 动态读取。
口令仅保存在本机临时文件和进程环境中，不写入仓库或日志。
Maven 3.9.14 / JDK 25.0.4，编译目标 Java 21；JDK 21 CI 结果仍需团队流水线确认。
本机临时目录保留 `smartfix-b06-focused.log`、`smartfix-b06-verify.log`、`smartfix-b06-docker-verify.log`。

## 交付边界

- S3-B-01..04 已通过团队 PR #19 / #20 合并。本 PR 单独交付 S3-B-06。
- C 主干已修复列表授权，本次保留该实现并提供 B 批量适配器及完整请求/附件/工单验收。
- 默认测试仍使用 H2，全部 PostgreSQL 测试由 `postgres-it` 启用；恢复主干合并时遗漏的既有 B 原生测试配置。
- 指派、页面与本次权限测试均在清理用户之前清理通知，适配最新主干的外键和提交后通知。
- 在主干 `c658b0a` 上，`mvn -B clean verify` 被通知模块测试编译错误阻塞：`NotificationTransactionTest` 调用六参数 `createNotification`，服务只剩五参数方法；独立主干目录的 `mvn -B test-compile` 复现同一错误。
- 主干 V14 与 V20 同时创建 `notifications`，还需在确认 V20 执行状态后修复。不会通过跳过测试、删除已执行迁移或关闭校验来宣称验证通过。
- 最新主干的完整验证结果将在上述集成问题处理后补录。
