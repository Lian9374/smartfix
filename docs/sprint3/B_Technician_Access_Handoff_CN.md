# S3-B-06：技师读取权限与撤回后的列表修复

日期：2026-10-08。代码基线：团队 B3 的 `b3baaf8`（S3-B-04）。
本阶段在本地 B4 工作分支实现；代码与自动化验收完成后，仍需 **C 联合评审**，不将本机测试视为团队评审。

当前状态：**本地代码与自动化验证完成；未提交/推送；C 联合评审待完成。**

## 已复现的问题

C 的 `RequestAccessService` TECH 分支已经通过 `RequestAssignmentAccessService` 查询 B 的有效指派。
请求详情、附件下载、工单详情和工单写操作会拒绝已失去指派的技师。
但 `WorkOrderService.findMine` 原先只按 `work_orders.technician_id` 分页。
撤回指派会保留工单的历史技师 ID，因此旧技师仍能从“我的工单”列表看到该请求及总数。

在已有 `AssignmentIT.withdrawsRevokesAccessAndAllowsASeparateNewAssignment` 中加入列表断言后，
修复前稳定失败：预期撤回后总数 0，实际仍为 1。复现日志：本机临时目录 `smartfix-b06-repro.log`。

## 修复方式

1. B 的 `AssignmentReadService` 增加 `findActiveRequestIdsForTechnician(Long technicianId)`，只返回该账号当前有效指派的请求 ID，返回集合不可变。
2. B 的 `RequestAssignmentLookupAdapter` 通过 C 的只读 SPI 暴露这些 ID。
3. C 的 `RequestAssignmentAccessService` 转交不可变结果；无适配器时返回空集合，不用历史工单归属作为授权依据。
4. `WorkOrderService.findMine` 先确认账号是有效技师，再通过公开 API 获取 ID，使用工单自己的仓储按“技师账号 + 有效请求 ID”筛选并分页。
5. 内容查询和分页总数使用相同筛选条件，避免分页后再过滤造成空洞页、错误总数或历史记录泄露。

没有跨模块读取仓储，没有更改请求状态、附件规则、指派历史或数据库迁移。
已完成的工单只要仍有当前有效指派，仍能在本人列表中查看；“在办数量”依旧排除已完成工单。

## 给 C 的接口评审说明

原有 Day 1 的 `ActiveAssignmentLookup.findActiveAssignment(Long)`、
`RequestAccessService.requireReadableRequest(...)`、派单写服务签名保持不变。
本次在 `ActiveAssignmentLookup` **新增默认只读方法** `findActiveRequestIdsForTechnician(Long)`，供分页前授权使用。

默认实现返回空集合，使旧适配器保持源码兼容且不会放行历史工单。
生产 B 适配器及 C 工作流测试夹具都实现了新方法；B/C 这批改动必须一起合并，
否则使用旧适配器时列表会安全地变为空，而不是自动从 `work_orders.technician_id` 放行。
请 C 联合确认这一扩展及其分页语义；本地验证不代替接口评审。

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
- 扩充请求授权与适配器单测，覆盖缺失适配器、错误请求 ID、技师账号不符、旧适配器的安全默认行为及读取失败传播。
- 扩充 B03 撤回验收，同时保留 C 的独立工作流测试夹具；未以 mock 替代 B/C 真实链路的权限判断。
- 每个原生测试 schema 完成后单独清理；上传测试文件只在本轮临时目录中创建和清理。

复现命令：

```powershell
mvn -B clean verify
# 为专用 *_test 数据库设置 TEST_DB_URL、TEST_DB_USERNAME、TEST_DB_PASSWORD 后：
mvn -B -Ppostgres-it clean verify
```

2026-10-08 实测结果：

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

- S3-B-04 已提交并推送到团队 `feature/SCRUM-UserB3-WANGPENGRUI`：`b3baaf8`。
- S3-B-06 本地实现的业务验收与 C 的联合评审分开记录；本阶段尚未推送。
- 本次已检查团队主干 `ee33858` 相对先前基线的增量，仅涉及 E 的通知模块和旧 C 说明文档删除，没有请求/工单权限实现的变化；这些主干增量未混入本阶段。
- E 的通知投递与跨模块事件接线不属于本次权限修复的验收范围，需后续合并主干后联调。
