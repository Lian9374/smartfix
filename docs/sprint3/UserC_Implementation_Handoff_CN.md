# Sprint 3 · User C 实现与协作说明（开发版本）

对应计划：`SmartFix_Sprint3_Development_Plan_CN.md` §17.3，以及 §6、§9.3–9.4、§12、§15。
基于 main `0359d99`；本次开发分支为 `feature/SCRUM-UserC3-WANGHAOYANG`。

这份实现用于先补齐 C 的代码，并提供可以继续联调的基础。方法和 DTO 是本次实现的协作建议，不表示团队已经冻结所有接口。若 B/A/D/E 的实际实现不同，可调整适配器、DTO 或服务签名，并同步调用方和测试。状态、权限、数据库约束和事务一致性需要随修改一起验证。

## 已实现的 C 代码

| 范围 | 主要落点 |
|---|---|
| 提交报修及附件一致性 | RequestSubmissionService / Controller；request/new.html |
| 状态与历史 | RequestTransition；RequestLifecycleService；RequestStatusHistory；RequestStatusChangedEvent |
| 管理员审核及最终优先级 | RequestReviewService / Controller；request/review.html |
| 工单与维修记录 | workorder 包；WorkOrderService；workorder/mine.html、detail.html |
| 用户确认、评价、重新打开、取消；管理员关闭 | RequestConfirmationService / Controller；RequestFeedback |
| 分页、状态筛选、详情操作区、附件下载 | RequestQueryService、RequestPresentationService、AttachmentController |
| 协作与报表只读 API | RequestReadService；RequestSnapshotResponse；workorder 的公开 DTO/API |
| 对象权限与并发冲突 | RequestAccessService；工作单当前指派校验；@Version；409 错误映射 |

保留 Sprint 2 的 `listMyRequests(...)` 和 Repository 查询方法；新增 `listMyRequestsPage(...)`，避免直接改变同学已有调用的返回类型。用户报告的 urgency 与管理员 final priority 分开保存；跨模块读取时提供 effective priority。

## B：指派接口的接入方式

当前 main 没有 AssignmentService。本分支没有编写另一套指派表、匹配算法或假指派服务。

C 提供 `request/spi/ActiveAssignmentLookup`：

```java
Optional<ActiveAssignment> findActiveAssignment(Long requestId);
// ActiveAssignment(Long assignmentId, Long requestId, Long technicianId)
```

建议 B 提供一个 Spring Bean 实现这个接口，调用 B 的公开、只读指派 Service，把实际 AssignmentResponse 转成上述 DTO。C 的服务只依赖这个小接口。若 B 已有不同 DTO，调整这一个适配器即可。

适配器建议依赖独立的 assignment read service，不注入同时调用 C 生命周期的 assignment write orchestrator。这样能避免相互注入以及读操作意外再次发起派单。

未接入时，指派相关转换返回业务冲突；未指派技师读取/写入返回 404。页面不会把缺失依赖当成已经完成的指派。

### 同一事务的建议调用顺序

B 的 assign/reassign/withdraw 服务应是事务发起方（REQUIRED）：

1. 检查管理员身份、技能/服务区/账户状态等 B 所有的匹配规则。
2. 保存/撤销有效指派，确保同一 request 只有一条有效指派。
3. 调用 C 的 `RequestLifecycleService.transition(...)`。
4. 若已有代码调用 `WorkOrderService.createFor(requestId, technicianId)`，可以保留；该方法会返回已同步建立的工单。
5. 任一步失败，回滚整笔指派、请求状态、历史与工单变化。

本次 `WorkOrderLifecycleParticipant` 在生命周期事务内同步创建/改派工单，因此 createFor 不需要重复创建。这个编排比计划中的示意顺序多了同步钩子；联调时可与 B 协商由谁创建，但应只有一个实际创建入口，且始终保持同一事务与唯一 request_id 约束。

撤回需先让有效指派失效，再调用 ASSIGNED → UNDER_REVIEW。原工单暂置 ON_HOLD，旧技师因无有效指派失去操作权限；再次派单会沿用原工单和历史维修记录。技师工作量 API 排除没有当前有效指派的工单。

如果后续 B 在请求关闭后撤销指派，技师的历史工单阅读权限也应一起讨论。当前实现以“仍是当前有效指派人”为准；请求本人及管理员的请求阅读权限不受影响。

## E：附件、SLA、通知与审计

附件复用已有 `validateAndStore / saveMetadata / deleteStoredFiles / readAttachment`，没有替换存储实现。

提交时先校验并写文件，再由 TransactionTemplate 完成请求、ticket、初始历史、附件元数据的数据库事务。失败时清理文件；清理范围包含数据库 commit 失败。提交成功后发生的回调异常不会误删已提交附件。

为接通 C 页面，补了真实下载 Controller；同时把 AttachmentProperties 的前缀对齐到现有配置 `smartfix.uploads`，把配置字段对齐为 `max-file-size`。这两个 E 接口相关改动建议由 E 联合检查。新增 DTO 不公开内部路径或 storedFilename。

C 发布：

- `RequestStatusChangedEvent(requestId, ticketNumber, requesterId, fromStatus, toStatus, actorUserId, occurredAt)`。
- `WorkOrderCompletedEvent(workOrderId, requestId, ticketNumber, technicianId, requesterId, occurredAt)`。

事件在业务事务内部发布；通知、SLA、审计使用 `@TransactionalEventListener(AFTER_COMMIT)` 消费，并使用自己模块的新事务/重试机制持久化。不要把 AFTER_COMMIT 写入当成仍然参加原业务事务。

RESOLVED 的状态事件和工单完成事件描述同一次完成事实。E 应选择一个通知入口或按 requestId/状态/occurredAt 去重，避免发两条“待确认”。C 不计算 SLA、不伪造 dueAt、不投递通知。

详情扩展点 `RequestDetailContributor.describe(requestId)` 返回只读 `DetailItem(label,value)` 列表；E 可以通过自己的 SlaCalculationService 接入真实截止时间/超时信息。接口不适用时可以协商换成类型更明确的 SLA DTO。

## A 与 D

A：新增页面沿用现有 site.css；未修改首页。A 的 UI PR 合并后，应把新页面接入最终公共片段/样式令牌，再检查现有请求页面的合并差异。目前没有复制未合并的 UI 分支。首页需增加管理员请求队列、技师工单入口。当前可直接访问：

- `/requests/new`、`/requests/mine`
- `/admin/requests`、`/admin/requests/lookup`
- `/workorders/mine`

D：可用 `RequestReadService.findById / findByTicketNumber / countByStatus / findByStatus / findCreatedBetween` 读取不可变快照；时间区间为 [start,end)。`WorkOrderService.countOpenWorkOrders` 提供技师工作量。以上是应用内只读 API，不是匿名 HTTP API；报表 Controller 仍须验证角色与数据可见范围。新增统计字段可以继续扩展 DTO，避免跨模块读取 Repository。

## 当前可调整的业务决定

| 内容 | 当前实现 | 后续调整位置 |
|---|---|---|
| 重新打开窗口（D-08 未冻结） | `0s` 表示关闭前不限时间；非零从本次 resolved/confirmed 时刻计算 | `SMARTFIX_REQUEST_REOPEN_WINDOW`，例如 `48h`；RequestWorkflowProperties |
| 评价 | 确认后可选；1–5 分；每个请求一次；重新打开保留原评价 | RequestConfirmationService / V9；如果要多轮评价，应另加迁移 |
| 维修完成 | 需要解决说明与至少一条维修记录 | WorkOrderService.complete |
| 技师接单 | 接单同时开始维修 | WorkOrderService.accept；如团队需要单独接受状态，需一起调整状态模型 |
| 状态规则 | 按计划 T01–T13 集中定义 | RequestTransition + LifecycleService 的业务前置检查 |
| 页面额外信息 | 可选 contributor；没有 SLA 时不显示虚构值 | RequestDetailContributor / RequestPresentationService |

保留这些变化空间不意味着绕过权限。所有浏览器 actorId 仍从 principal 获取；非本人请求/非当前指派工单统一 404；跳步、终态变更、重复完成返回冲突。

## 数据库与合并建议

| 文件 | 用途 |
|---|---|
| V5（仅文件头注释修复） | 修复原非法 SQL |
| V6 | 状态历史、时间线索引、每个请求唯一初始历史 |
| V7 | 状态 CHECK、version DEFAULT 0、审核/最终优先级/解决确认关闭时刻 |
| V8 | 工单与维修记录 |
| V9 | 评价 |

这是当前 main 最大版本为 V5 时的候选编号。全员合并前再核对登记表；尚未应用的新迁移可协调编号，已应用后不要改号或改内容。

V5 修复应单独提 PR、优先合并；已使用本地修正版的同学按团队统一方式处理校验和，不自动 repair/clean，不删除已有数据。

这个开发分支集中保存 C 的整体实现，供联调。正式 PR 按计划拆分审核，迁移 PR 遵守单模块约束，并按 V6 → V7 → V8 → V9 应用顺序安排。不要先应用 V9 再补 V8，也不要仅合并新枚举/版本字段而缺少 V7 数据库约束。

联调涉及 B 的 SecurityConfig/409 handler/POM，以及 E 的下载 Controller/附件配置，需要相应负责人联合检查。当前保留 CSRF 和 denyAll，不扩大为任意路径放行。

POM 将新增 PostgreSQL IT 与已有 PostgreSQL 测试一起放入现有 `postgres-it` 选择机制；默认 verify 仍运行 H2 认证集成测试，显式 `-Ppostgres-it` 才要求专用原生数据库。这是测试环境选择，不把失败用例删除或标成跳过。建议 B 联合检查这几条 excludes。

## 验证与继续工作

常规构建：

```bash
mvn test
mvn clean package
```

原生 PostgreSQL 验收（使用独立名字以 `_test` 结尾的数据库）：

```bash
TEST_DB_URL='jdbc:postgresql://localhost:55432/smartfix_test' \
TEST_DB_USERNAME=smartfix \
TEST_DB_PASSWORD='<本地测试库密码>' \
mvn -Ppostgres-it clean verify
```

已有容器若只有 smartfix 库，需要另外创建 smartfix_test；不要把测试 URL 指向团队共享应用数据库。这些测试只建立/清理自己创建的随机 schema。

RequestWorkflowTest 用真实 C Service、H2/JPA、文件存储和模板，B 的指派只读接口与 PostgreSQL ticket SQL 使用测试替身；替身仅位于测试目录。RequestWorkflowPostgresIT 复用同样用例，要求真实 Flyway 迁移与 Hibernate validate，另外检查真实 ticket counter；RequestMigrationIT 验证带旧数据的 V5 → V9 升级。

本次环境中的 H2/MockMvc 用例和打包结果见交付记录。迁移 SQL 另在 PGlite（PostgreSQL WASM 引擎）验证空库/旧数据升级；这不等同于原生 PostgreSQL、多连接或全员服务联调验收。原生 PostgreSQL、B 的实际派单、E 的实际 SLA/通知，以及 A 最终 UI 仍需联合验证。
