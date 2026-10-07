# S3-B-02 技师工作量与推荐排序：实现与交接

2026-10-07。开发分支：`feature/SCRUM-UserB3-WANGPENGRUI`。
基于 B-01 分支 `feature/SCRUM-UserB2-WANGPENGRUI` 的 `5873ceb`，
合入团队 `upstream/main` 的 `690aefb`（本地合并提交 `8da2a6f`）。
这是本地实现与验证记录，不代表已经提交 PR、通过评审、合并或部署。

## 本次实现

对应工作计划 §14.3、§17.2 的 T1/T2 以及 S3-B-02：

- `TechnicianWorkloadService`：通过 C 的公开 `WorkOrderService` 获取真实在办工单数量。
- `TechnicianRecommendationService`：复用 B-01 的资格过滤，对候选技师按冻结规则排序。
- `TechnicianRecommendationResponse`：不可变结果，包含姓名、账号/档案 ID、技能、区域、可用性和工作量。
- 单元测试与真实服务/JPA 集成测试：覆盖资格、排序、状态口径、撤回/改派及依赖缺失。

本次没有新增 HTTP 路由或数据库迁移。V10 沿用 B-01；V11、派单事务及派单页面属于后续工作。
合并基线时保留了技师资料路由与 main 新增路由的授权规则及测试。

## 公开接口

| 接口 | 输入 / 返回 |
|---|---|
| `TechnicianWorkloadService.countOpenWorkOrders(technicianUserId)` | 正整数 **users.id**；返回 `long` |
| `TechnicianRecommendationService.recommend(category, locationId)` | `MaintenanceCategory` + 有效地点 ID；返回有序 `List<TechnicianRecommendationResponse>` |

结果字段：`profileId`、`userId`、`displayName`、`skills`、`serviceAreaIds`、
`availabilityStatus`、`openWorkOrders`。技能与区域集合是防御性拷贝。

这两个接口用于应用内协作。未来管理员派单 Controller 仍须验证角色与请求访问权限，
不应作为匿名接口直接暴露。推荐是当前数据的只读结果，不预留技师，也不保证稍后仍满足资格。

### 硬性过滤与排序

`TechnicianDirectoryService.findCandidates` 先过滤账号 ACTIVE 且当前角色为 TECHNICIAN、
档案 active、技能匹配、服务区域匹配、未请假（F1–F5）。BUSY 仍可成为候选。

排序依次为：

1. `AVAILABLE` 优先于 `BUSY`。
2. 同一可用性下，`openWorkOrders` 从少到多。
3. 同样工作量下，**technician_profiles.id** 从小到大，保证确定性。

统计工单传 **userId**，最后的排序键用 **profileId**；两个 ID 不能混用。
每个候选只查询一次工作量，再在内存中排序，Comparator 不访问数据库。
当前只有一个排序规则，因此没有引入 Strategy 接口。

### 工作量口径与依赖

沿用当前 C 的 `WorkOrderService.countOpenWorkOrders`：

- 计入 `CREATED`、`IN_PROGRESS`、`ON_HOLD`、`REOPENED`。
- 排除 `COMPLETED`、`CLOSED`。
- 还必须存在该请求当前有效指派，且指向同一个技师账号。
- 撤回或已改派给其他人的旧工单行不计入；改派后以 C 同步后的工单与有效指派为准。

**生产 `ActiveAssignmentLookup` 适配器尚未实现，属于 S3-B-03。**
`TechnicianWorkloadService` 先通过 `RequestAssignmentAccessService.isAvailable()` 检查该依赖；
未接入时抛 `BusinessConflictException`，消息为
`Technician workload is unavailable until assignment lookup is connected.`
这样不会把未知工作量误报为零。查询异常同样向上传播，推荐不会返回部分或补零结果。
没有合格候选时直接返回空列表，无须查询工作量。

生产代码只调用其他模块的公开 Service，不访问其他模块 Repository，不自行修改工单或请求。
当前实现逐个候选调用 C 的计数接口；如未来数据量需要优化，应与 C 协商批量公开 API。

## 验证

定向验证：

```powershell
mvn -B '-Dtest=TechnicianWorkloadServiceTest,TechnicianRecommendationServiceTest' '-Dit.test=TechnicianRecommendationIT,TechnicianProfileIT' verify
```

2026-10-07：**40 通过、0 失败、0 错误、0 跳过，BUILD SUCCESS**
（14 个单元用例 + 26 个集成用例，包含现有技师资料回归）。

全量回归：

```powershell
mvn -B clean verify
```

2026-10-07：**321 通过、0 失败、0 错误、0 跳过，BUILD SUCCESS**
（271 个单元/Web/仓储用例 + 50 个集成用例）。合并后的开发前基线为 297 项通过，
本次新增 24 项用例。本次没有修改测试排除项或跳过测试。
环境为本机 Maven 3.9.14 / JDK 25.0.4，编译目标 Java 21；不是 JDK 21 CI 的执行证据。

集成测试使用隔离 H2 数据库，真实技师目录、工作量服务和 C 的工单服务。
`TechnicianRecommendationIT` 仅用测试内的 `ActiveAssignmentLookup` 保存当前指派，
直接准备数据库状态，不替代 C 的生命周期测试或 B-03 的真实派单持久化/并发测试。
`TechnicianProfileIT` 使用没有适配器的真实 Spring 上下文，验证依赖缺失会明确报错。
本次未执行 `postgres-it`，没有验证真实 PostgreSQL 的全量迁移或升级。

## 下一步：S3-B-03

1. 按团队迁移登记确认 V11 与已合并 V12 的应用顺序，新增 assignments 及当前有效指派的唯一约束。
2. 实现独立的指派只读 Service，以及 C 的 `ActiveAssignmentLookup` 适配器。
   适配器只依赖只读 Service，避免回调指派写入编排器形成循环依赖。
3. 实现指派、改派、撤回的事务编排：管理员身份与资格重新检查 → 有效指派变更 →
   C 的生命周期/工单同步；整笔失败一起回滚。
4. 增加并发派单、回滚、停用/资格变更和当前技师访问边界测试。

接口接入顺序以 [C 的交接文档](UserC_Implementation_Handoff_CN.md) 为准：
当前 `WorkOrderLifecycleParticipant` 会在生命周期事务内同步工单，
不要另造一个重复创建工单的路径。推荐接口可直接用于后续派单页面，页面由 S3-B-04 接入。
