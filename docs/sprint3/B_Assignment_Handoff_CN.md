# S3-B-03 指派、改派与撤回：实现与交接

日期：2026-10-07。提交分支：`feature/SCRUM-UserB3-WANGPENGRUI`。
基于 B3 提交 `fbbffcd`；按用户确认，将本次派单后端追加到团队仓库的 B3 分支。
下一项 S3-B-04 页面开发在本地 `feature/SCRUM-UserB4-WANGPENGRUI` 继续。

## 本次范围

- V11 `assignments`、有效指派部分唯一索引、历史记录及乐观锁。
- `AssignmentService`：管理员指派、改派、撤回；服务端重新检查 F1–F5 和账号角色。
- `AssignmentReadService` + `RequestAssignmentLookupAdapter`：接入 C 的真实当前指派查询。
- 与 C 的请求生命周期、历史、工单同步组成同一事务，任一步失败一起回滚。
- 发布指派/撤回事实事件，供 E 在提交后订阅；没有直接实现通知投递或审计存储。
- H2 行为回归及 PostgreSQL 迁移、并发和事务验收测试。

本次是内部服务接口，不新增派单 HTTP 路由。管理员派单页面及路由权限由下一项 S3-B-04 接入。

## 服务契约

| 方法 | 参数 | 返回 / 限制 |
|---|---|---|
| `assign(ticketNumber, command, actorUserId)` | `AssignTechnicianCommand(technicianId)` | `AssignmentResponse`；请求须处于 UNDER_REVIEW，且有已审核优先级 |
| `reassign(ticketNumber, command, actorUserId)` | `ReassignTechnicianCommand(technicianId, expectedAssignmentId, reason)` | 新指派 DTO；旧指派保留为失效历史 |
| `withdraw(ticketNumber, command, actorUserId)` | `WithdrawAssignmentCommand(expectedAssignmentId, reason)` | 失效后的指派 DTO；只允许尚未开工的 ASSIGNED 请求 |
| `findActiveAssignment(requestId)` | 请求 ID | `Optional<AssignmentResponse>`；应用内只读 API |

写操作要求 ACTIVE ADMINISTRATOR；调用者身份是独立参数，未来 Controller 应从登录主体取得。
`technicianId` 是 **users.id**。改派/撤回必须提交读到的 `expectedAssignmentId`，
当前指派已经变化时返回 `BusinessConflictException`，避免旧页面覆盖新决定。
原因必填，最长 500 字符，保存时 trim；普通改派须选择不同技师。

DTO 含指派人、指派时间、原因、active，以及撤回/改派操作人的 ID、失效时间和原因。
时间按数据库的微秒精度保存，使写入响应、事件与回读结果一致。

## 状态与事务

| 操作 | C 的状态流转 | 工单结果 |
|---|---|---|
| 首次指派 / 撤回后再次指派 | UNDER_REVIEW → ASSIGNED | 创建或复用该请求唯一工单，状态 CREATED |
| 尚未开工时改派 | ASSIGNED → UNDER_REVIEW → ASSIGNED | 同一事务内两条状态历史，原工单切换技师 |
| 进行中改派 | IN_PROGRESS → ASSIGNED | 保留工单及维修历史，重新置 CREATED |
| 重新打开后派单 | REOPENED → ASSIGNED | 允许重新校验后保留原技师，也可换技师 |
| 撤回 | ASSIGNED → UNDER_REVIEW | 原工单 ON_HOLD，无当前指派后不计入工作量 |

编排顺序：资格检查 → 保存指派变化并 flush → `RequestLifecycleService.transition` → 发布事件。
工单创建/改派由 C 已有的 `WorkOrderLifecycleParticipant` 执行，B 不再额外创建工单。
原技师在改派/撤回后即无法读取或操作该请求的工单；通过生产适配器验证 C 已有权限分支，
没有直接修改 C 的 `RequestAccessService`。

首次派单最终由 `uk_assignments_active_request` 仲裁并发，不能仅依赖“先查再写”。
重复插入转为 `BusinessConflictException("This request already has an active assignment.")`。
对已有指派的并发改派/撤回由 `@Version` 拒绝过期写入；失败事务不会留下半条改派记录。

依赖方向：C → `ActiveAssignmentLookup` 接口 → B 的适配器 → `AssignmentReadService`。
适配器不注入 `AssignmentService`，避免写入编排与 C 生命周期相互注入。

## 事件交接给 E

`AssignmentCreatedEvent` 字段：

`assignmentId, requestId, ticketNumber, requesterId, technicianId, previousTechnicianId, actorUserId, reason, occurredAt`。

首次指派的 `previousTechnicianId` / `reason` 为 null；改派时包含原技师和原因。
`AssignmentWithdrawnEvent` 字段为上述字段去掉 `previousTechnicianId`，其中 `technicianId` 是原技师。
改派只发一个 Created 事件；中间失效步骤不额外发送“已撤回”的通知事件。

事件在业务事务内发布，通知/审计必须使用 `@TransactionalEventListener(AFTER_COMMIT)` 消费。
消费者应协调与 C 的状态事件去重；监听器写入自己的数据时需要自己的事务。
当前测试验证提交后才消费、回滚后不消费；不代表 E 的实际投递功能已完成。

## V11 应用范围

V11 使用计划中的预留编号，`technician_id` / 管理员 ID 外键指向 users，request_id 指向 maintenance_requests。
部分唯一索引为 `UNIQUE (request_id) WHERE active`，保留多条 inactive 历史。
失效记录必须同时包含失效人、时间和非空原因。

新库按 V1–V12 顺序应用；从 V10 升级时验证原有技师/请求数据保留。
**若现有开发库已应用 V12、但缺少 V10/V11，不能直接把本分支启动视为可升级。**
需按团队登记处理缺失版本与应用顺序；本次不修改历史迁移，不开启 out-of-order，不重置现有数据库。

## 验证

```powershell
mvn -B clean verify
```

默认全量回归：**345 项通过、0 失败、0 错误、0 跳过**（271 单元/Web/仓储 + 74 集成），BUILD SUCCESS。
其中 `AssignmentIT` 25 项，用真实 B/C 服务、生产查询适配器和真实事务提交，覆盖：
资格变更、非管理员、失效管理员、旧页面、重复提交、改派/撤回、重新打开、权限转移、
下游失败与外层回滚，以及两名管理员同时指派/改派的一胜一败。

H2 不支持生产部分索引语法；隔离 H2 测试使用生成列模拟有条件唯一性。
`AssignmentPostgresIT` 继承同样 25 项验收用例，通过 Flyway 使用真正的 V11 索引；
`AssignmentMigrationIT` 验证有数据 V10 升级、唯一性、历史共存、外键及失效记录约束。
这两个 PostgreSQL 用例沿用项目的 `postgres-it` profile，默认测试不要求外部数据库。

真实库运行命令：配置专用、库名以 `_test` 结尾的 PostgreSQL 数据库及
`TEST_DB_URL` / `TEST_DB_USERNAME` / `TEST_DB_PASSWORD` 后执行：

```powershell
mvn -B -Ppostgres-it clean verify
```

Docker PostgreSQL 全量验证：**402 项通过、0 失败、0 错误、0 跳过，BUILD SUCCESS**
（271 单元/Web/仓储 + 131 集成，包括 25 个派单 PostgreSQL 用例和 1 个 V11 升级用例）。
数据库运行在容器 `smartfix-b03-postgres-test`，镜像 `postgres:16-alpine`，实际版本 PostgreSQL 16.15；
镜像 digest 为 `sha256:721873c34ceb9f8d8fc265984940dc982404c105f19ad51be9fdc5970a6080ea`。
测试通过本机 Maven 连接容器数据库运行，数据库只绑定本机回环地址；
本轮测试库 `smartfix_b03_test`，端口 `127.0.0.1:64981`（此端口是本轮分配值）。
每个原生数据库测试创建自己的随机 schema，结束时只删除该 schema，不能使用已有业务数据库执行测试。
本机 Maven 3.9.14 / JDK 25.0.4，编译目标 Java 21；不代表 JDK 21 CI 结果。

已有测试的调整：C 和 B-02 的测试指派夹具标记为 `@Primary`，保留各自的隔离测试；
B-02“生产适配器尚未接入”的临时集成断言由本次真实适配器验收替代，缺失依赖的单元测试仍保留。
Docker 全量验证还暴露并修正了两处已有测试兼容性问题：C 的 JDBC 测试数据改用
`Timestamp`（PostgreSQL 驱动不自动推断 `Instant`）；迁移测试继续明确验证 V6–V9，
同时允许后续团队迁移存在，避免把迁移总数固定为 4。没有修改 C 的生产业务逻辑。

## 下一步：S3-B-04

接入管理员派单页面、POST 指派/改派/撤回和显式路由授权/CSRF 测试。
页面必须提交技师账号 ID 与当前指派 ID；收到 409 后重新加载最新状态。
与 E 对齐事件消费，与 C 联合评审当前技师访问边界，并按团队流程提交 PR。
