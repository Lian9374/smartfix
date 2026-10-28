# SmartFix 当前代码文件架构与阅读指南

基于 2026-10-18 本地工作区 `feature/requester-detail-refresh`，HEAD 为 `735eadc`。说明按当前本地文件读取，不是对 GitHub 默认分支的说明。此次只新增架构文档，不改动业务代码。

## 1. 整体是什么架构

这是一个**按业务模块划分的 Spring Boot 单体应用**。后端使用 Java 21 / Spring Boot 3.5.4，页面使用 Thymeleaf 服务端渲染，浏览器交互使用原生 JavaScript 和 CSS。数据通过 Spring Data JPA 读写 PostgreSQL，结构变更由 Flyway SQL 迁移维护。登录使用 Spring Security 的 Session Cookie。

不是前后端分离的 React 项目，也不是多个独立部署的微服务。Java、HTML 模板、CSS、JS 和图片一起打进同一个应用 JAR。

```text
浏览器：HTML 表单 / 页面交互
       ↓ HTTP GET / POST（写入含 CSRF）
Spring Security：登录、角色、账号状态
       ↓
Controller：接收参数 → 校验表单 → 调用服务
       ↓
Service：业务规则、资源权限、事务、事件
       ↓
Domain + Repository：实体/状态规则 + 数据查询写入
       ↓
PostgreSQL

Controller → Model → Thymeleaf templates → 返回 HTML
业务事件 → Listener → 数据库投递任务 → 后台消息投递
```

## 2. 根目录

```text
smartfix/
├─ pom.xml                       Maven 依赖、Java 版本、测试和打包配置
├─ src/
│  ├─ main/
│  │  ├─ java/com/smartfix/        后端源代码（按业务模块组织）
│  │  └─ resources/
│  │     ├─ application.yml       通用运行配置
│  │     ├─ application-dev.yml   开发环境补充配置
│  │     ├─ templates/            Thymeleaf HTML 页面
│  │     ├─ static/css/           基础样式与各页面样式
│  │     ├─ static/js/            浏览器交互脚本及 Leaflet
│  │     ├─ static/images/        PNG 插图、SVG 网站图标
│  │     ├─ data/                 校园建筑静态目录 JSON
│  │     └─ db/migration/         Flyway 数据库版本迁移
│  └─ test/
│     ├─ java/com/smartfix/       单元、页面渲染、业务与集成测试
│     └─ resources/              测试配置
├─ docs/                         需求、设计、迭代说明、截图证据
├─ tools/                        浏览器验证、性能及发布证据辅助脚本
├─ Dockerfile                    应用容器构建
├─ docker-compose.yml            数据库和可选应用容器
├─ Jenkinsfile                   CI 构建、测试、打包流程
├─ start-smartfix.ps1            Windows 本地启动脚本
├─ repair-smartfix-local.ps1     本地修复辅助脚本（不是正常业务入口）
├─ README.md / README.zh-CN.md    使用说明
├─ .env.example                  环境变量示例
├─ target/                       编译产物、测试报告和临时预览；不在这里改源码
└─ local-db-backups/              本地备份；不是程序源码
```

`.idea/` 是 IDE 配置；`.github/` 当前包含 PR/Issue 模板，不能把它理解成已经配置了 GitHub Actions。部署配置和脚本是否存在，不代表对应服务已经在远端部署。

## 3. 每个业务模块内部如何读

模块通常使用以下分层，少量模块不需要全部子目录：

- `controller/`：URL 入口，绑定表单/查询参数，构造 Model，选择页面或重定向。
- `service/`：实际业务逻辑、事务边界、权限及状态前提。
- `domain/`：JPA 实体、枚举及对象自身的规则。`@Version` 用于并发更新检测。
- `repository/`：数据库读写接口或针对数据库的插入实现。
- `dto/`：跨层传递的数据。`*Command` 多为写入输入，`*Response` 多为读取结果。
- `event/`、`listener/`：业务事件与订阅处理，如状态变化产生消息。
- `config/`、`validation/`：可配置规则和输入校验。
- `spi/`：模块协作接口，让 request 不必直接依赖工单和指派的具体实现。

不要把“页面上看不见按钮”当成权限控制。Controller 路由、Service 资源检查和状态机分别承担不同保护。

## 4. 业务模块导览

### `auth/` — 登录与访问控制

验证身份、登录账号类型、会话有效性、强制改密和 URL 权限。

- [SecurityConfig.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/auth/config/SecurityConfig.java)
- [ActiveAccountFilter.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/auth/security/ActiveAccountFilter.java)
- [SmartFixUserDetailsService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/auth/service/SmartFixUserDetailsService.java)
- [SelectedAccountTypeAuthenticationProvider.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/auth/security/SelectedAccountTypeAuthenticationProvider.java)

### `user/` — 账号与角色

用户资料、账号启停、角色修改、创建工程师、密码策略和初始化管理员。

- [User.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/user/domain/User.java)
- [Role.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/user/domain/Role.java)
- [UserService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/user/service/UserService.java)
- [AccountAdministrationService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/user/service/AccountAdministrationService.java)
- [UserManagementController.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/user/controller/UserManagementController.java)

### `request/` — 报修主流程

提交报修、查询、状态机、用户验收、管理员终审、取消、反馈与附件。

- [MaintenanceRequest.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/domain/MaintenanceRequest.java)
- [RequestStatus.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/domain/RequestStatus.java)
- [RequestStage.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/domain/RequestStage.java)
- [RequestTransition.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/domain/RequestTransition.java)
- [RequestLifecycleService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/service/RequestLifecycleService.java)
- [RequestSubmissionService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/service/RequestSubmissionService.java)
- [RequestPresentationService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/service/RequestPresentationService.java)
- [RequestConfirmationService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/service/RequestConfirmationService.java)

### `dispatch/` — 管理员指派

分配、转派、撤销指派及指派记录；手工指派采用启用的工程师账号，不依赖技能匹配。

- [Assignment.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/dispatch/domain/Assignment.java)
- [DispatchController.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/dispatch/controller/DispatchController.java)
- [AssignmentService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/dispatch/service/AssignmentService.java)
- [DispatchPageService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/dispatch/service/DispatchPageService.java)
- [RequestAssignmentLookupAdapter.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/dispatch/service/RequestAssignmentLookupAdapter.java)

### `workorder/` — 工程师维修执行

工作台、维修历史、接单、维修记录、完成提交，以及报修与工单状态同步。

- [WorkOrder.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/workorder/domain/WorkOrder.java)
- [RepairRecord.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/workorder/domain/RepairRecord.java)
- [WorkOrderStatus.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/workorder/domain/WorkOrderStatus.java)
- [WorkOrderController.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/workorder/controller/WorkOrderController.java)
- [WorkOrderService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/workorder/service/WorkOrderService.java)
- [WorkOrderLifecycleParticipant.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/workorder/service/WorkOrderLifecycleParticipant.java)
- [WorkOrderDetailContributor.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/workorder/service/WorkOrderDetailContributor.java)

### `technician/` — 工程师资料

技能、服务区域、可用状态和工作量；可选资料保留，手工指派不以资料完整性为前提。

- [TechnicianDirectoryService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/technician/service/TechnicianDirectoryService.java)
- [TechnicianWorkloadService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/technician/service/TechnicianWorkloadService.java)
- [AdminTechnicianController.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/technician/controller/AdminTechnicianController.java)
- [TechnicianProfileController.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/technician/controller/TechnicianProfileController.java)

### `facility/` — 地点、设施与地图目录

维护 Location / Facility，读取校内建筑目录、坐标和别名。

- [Location.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/facility/domain/Location.java)
- [Facility.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/facility/domain/Facility.java)
- [FacilityService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/facility/service/FacilityService.java)
- [CampusGeographyService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/facility/service/CampusGeographyService.java)
- [CampusMapService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/facility/service/CampusMapService.java)

### `community/` — 社区与举报管理

提问、回答、采纳、撤回、举报和管理员隐藏/处理举报。

- [CommunityQueryService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/community/service/CommunityQueryService.java)
- [CommunityQuestionService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/community/service/CommunityQuestionService.java)
- [CommunityAnswerService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/community/service/CommunityAnswerService.java)
- [CommunityModerationService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/community/service/CommunityModerationService.java)
- [CommunityAccessGuard.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/community/service/CommunityAccessGuard.java)

### `notification/` — 站内消息与投递

消息列表、未读数、已读操作、事务发件箱、重试投递及可选邮件。

- [NotificationController.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/notification/controller/NotificationController.java)
- [NotificationService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/notification/service/NotificationService.java)
- [DeliveryOutboxService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/notification/service/DeliveryOutboxService.java)
- [DeliveryDispatcher.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/notification/service/DeliveryDispatcher.java)
- [DeliveryWorker.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/notification/service/DeliveryWorker.java)
- [NotificationEventListener.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/notification/listener/NotificationEventListener.java)

### `reporting/` — 概览、统计与地图状态

管理员概览、维修统计、报表导出和地图状态聚合。

- [AdminWorkspaceController.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/reporting/controller/AdminWorkspaceController.java)
- [CampusMapController.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/reporting/controller/CampusMapController.java)
- [CampusMapStatusService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/reporting/service/CampusMapStatusService.java)
- [DashboardService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/reporting/service/DashboardService.java)
- [OperationalReportService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/reporting/service/OperationalReportService.java)
- [ReportExportService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/reporting/service/ReportExportService.java)

### `announcement/` — 校园公告

公告发布与展示，供首页轮播与公告页读取。

- [AnnouncementController.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/announcement/controller/AnnouncementController.java)
- [AnnouncementService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/announcement/service/AnnouncementService.java)
- [Announcement.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/announcement/domain/Announcement.java)

### `audit/` — 审计记录

账号、社区等管理操作的审计查询和记录。

- [AuditController.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/audit/controller/AuditController.java)
- [AuditService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/audit/service/AuditService.java)
- [AuditEntry.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/audit/domain/AuditEntry.java)

### `sla/` — 维修时限

时限策略、截止时间计算、提醒/超时扫描。实际行为取决于已启用的策略。

- [SlaPolicyService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/sla/service/SlaPolicyService.java)
- [SlaCalculationService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/sla/service/SlaCalculationService.java)
- [SlaScanner.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/sla/service/SlaScanner.java)
- [SlaScanWorker.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/sla/service/SlaScanWorker.java)

### `common/` — 共享基础设施

首页分流、导航数据、未读角标、统一异常、时间及旧数据库迁移兼容。

- [HomeController.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/common/web/HomeController.java)
- [NavigationAdvice.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/common/web/NavigationAdvice.java)
- [NotificationNavigationAdvice.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/common/web/NotificationNavigationAdvice.java)
- [GlobalExceptionHandler.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/common/exception/GlobalExceptionHandler.java)
- [TimeConfig.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/common/configuration/TimeConfig.java)

## 5. 报修、指派、工单是三个不同概念

- `MaintenanceRequest`：用户报的故障，贯穿提交、维修、两级审核和结束。
- `Assignment`：谁被指派、由谁指派、何时指派、是否仍有效。转派保留旧记录。
- `WorkOrder`：工程师执行维修的对象，关联报修和工程师，拥有维修状态及解决方案。
- `RepairRecord`：一张工单的多次诊断、处理、材料和工时记录。
- `RequestStatusHistory`：报修的状态变化审计。
- `RequestFeedback`：请求者验收后的评分与评论。

不能只改数据库中的一个状态字段就认为整个流程完成：关联工单、状态历史、消息任务等都需要由服务一起处理。

## 6. 一次完整维修实际经过哪些代码

### 用户提交

`RequestSubmissionController` → `RequestSubmissionService.submit()` → 校验请求者、地点和字段 → `AttachmentService` 校验并存图 → `RequestTicketNumberGenerator` 生成编号 → 保存 `MaintenanceRequest` → `RequestLifecycleService.recordInitialSubmission()` → 保存附件元数据。

提交服务使用事务模板处理数据库提交，并在数据库回滚时补偿已经存储的附件文件；文件系统本身不是数据库事务的一部分。

### 管理员指派

`DispatchController` → `DispatchPageService` 提供页面信息 → `AssignmentService.assign()` 审核优先级并创建指派 → 报修进入 ASSIGNED → `WorkOrderLifecycleParticipant` 创建/同步工单。

当前手动指派读 `TechnicianDirectoryService.listManualAssignees()`。管理员可选择启用的工程师，不要求技能、区域、可用性或资料完整性匹配。`TechnicianRecommendationService` 仍保留可选推荐逻辑，不能把它误认为当前指派的硬门槛。

### 工程师接单、记录、完成

`WorkOrderController` → `WorkOrderService.accept()` 进入维修中 → `recordRepair()` 写多条维修记录 → `complete()` 要求已有记录并提供解决方案 → 工单 COMPLETED、报修 RESOLVED，等待请求者验收。

Service 检查当前有效指派；旧工程师不能依赖之前已登录的 Session 绕过转派权限。

### 用户验收和管理员终审

`RequestConfirmationController` → `RequestConfirmationService.confirm()` → 报修 CONFIRMED，等待管理员；随后管理员 `close()` → CLOSED。管理员也可 `returnForRepair()`，用户在允许状态可 `reopen()`，进入 REOPENED，再次维修和验收。

### 评分与取消

- `feedback()`：请求者在 CONFIRMED / CLOSED 状态给出 1–5 分及评论，防止重复反馈。HTML 是星级单选，但后端仍接收整数 `rating`。
- `cancel()`：自己的 SUBMITTED / UNDER_REVIEW / ASSIGNED 请求可取消；已开工的 IN_PROGRESS 不允许直接取消。关联工单同步 CLOSED，历史保留，停止后续维修写入。

### 状态机

```text
SUBMITTED → UNDER_REVIEW → ASSIGNED → IN_PROGRESS → RESOLVED → CONFIRMED → CLOSED
   待指派       待指派          维修           维修          用户验收       最终审核      结束

SUBMITTED / UNDER_REVIEW / ASSIGNED → CANCELLED
UNDER_REVIEW → REJECTED
RESOLVED / CONFIRMED → REOPENED → 再次指派或重新开工
```

初次管理员直接指派会在服务事务内完成审核与分配，不能据此认为 UNDER_REVIEW 完全不存在。

`RequestStage` 是界面队列分组：TO_ASSIGN、REPAIR、USER_REVIEW、ADMIN_REVIEW、FINISHED；`RequestStatus` 是实际业务状态。两者不要混用。

`WorkOrderStatus` 则是 CREATED、IN_PROGRESS、ON_HOLD、COMPLETED、REOPENED、CLOSED。报修 RESOLVED 不等于最终 CLOSED；工单 COMPLETED 也不等于管理员已经终审。

## 7. 模块如何避免互相缠绕

request 的 `spi/ActiveAssignmentLookup` 由 dispatch 的 `RequestAssignmentLookupAdapter` 接入。

request 的 `spi/RequestDetailContributor` 由 workorder 的 `WorkOrderDetailContributor` 补充工单编号、工单状态和解决方案。

request 的 `spi/RequestTransitionParticipant` 由 workorder 的 `WorkOrderLifecycleParticipant` 在状态变化前检查工单条件、变化后同步工单。

`RequestLifecycleService` 集中处理允许的角色/状态、事务、乐观锁冲突、状态历史和事件发布。它是最值得先读的服务，但不是所有业务都放在这里；指派、存图和维修记录仍由各自模块负责。

## 8. 主要 URL 与页面文件

- `/`：`HomeController`；用户首页 `templates/home.html`，管理员跳 `/admin`，工程师跳 `/technician`。
- `/login`、`/register`、`/account/password`：auth 模块及 login/register/account 模板。
- `/requests/new`：`RequestSubmissionController` → `request/new.html`。
- `/requests/mine`：`RequestQueryController` → `request/mine.html`。
- `/requests/{ticket}`：`RequestQueryController` → `request/detail.html`；多角色共享，按权限显示信息/操作。
- `/admin`：`AdminWorkspaceController` → `admin/overview.html`。
- `/admin/requests`：`RequestQueryController` → `admin/request-queue.html`。
- `/admin/requests/{ticket}/dispatch`：`DispatchController` → `dispatch/assign.html`。
- `/admin/users`：`UserManagementController` → `admin/users.html`。
- `/admin/technicians`：`AdminTechnicianController` → `admin/technicians.html`。
- `/technician`、`/workorders/mine`：`WorkOrderController` → `workorder/mine.html`；HISTORY 视图复用页面并筛选 COMPLETED/CLOSED。
- `/workorders/{id}`：`WorkOrderController` → `workorder/detail.html`。
- `/community` 及问答详情：community 模块 → `community/`。
- `/admin/community/reports`：community 管理控制器 → `admin/community-reports.html`。
- `/campus-map`：`CampusMapController` → `facility/map.html`。
- `/admin/facilities`：`FacilityController` → `facility/list.html`。
- `/notifications`：`NotificationController` → `notification/list.html`。

精简导航不等于删除旧功能。`/admin/requests/lookup` 仍对应 `admin/requests.html`；`/dashboard`、`/technician/dashboard` 和通知投递、SLA 等控制器仍在，是否可访问由 SecurityConfig 及对应 Service 决定。

## 9. 前端文件怎么配合

`templates/fragments/layout.html` 是公共页面骨架和资源加载中心，按角色/页面标题决定附加样式。`components.html` 提供状态徽章、时间线等；`ui-icons.html` 管理通用图标；`illustrations.html` 保留模板插图组件。

样式分三层：

1. `site.css`：基础变量、布局、表单、卡片、按钮、状态等。
2. `visual-refresh.css`：公共视觉调整。
3. 页面样式：如 requester-home / requester-detail、admin-requests、engineer-detail。

最新功能对应：

- `request/detail.html` + `requester-detail.css`：用户报修详情。
- `workorder/detail.html` + `engineer-detail.css`：工程师工单详情。
- `request-feedback.css`：星级评分、反馈框、红色未读角标、取消入口和管理员详情分组。
- `common/web/NotificationNavigationAdvice.java`：把当前账号真实未读数加入 Model；在页面渲染时查询，不是 WebSocket 实时推送。
- `requester-navigation.css`：顶部导航的角色布局和消息入口。
- `workspace.js`：提交确认、通用工作区表单行为。
- `request-form.js`：报修表单交互；`admin-dispatch.js`：工程师选择和指派按钮交互。
- `campus-map.js` + 本地 Leaflet：地图交互；`community-*.js`：社区交互。

CSS 和 JS 仅负责显示/交互，不能代替后端权限和状态校验。页面样式有覆盖顺序，修改基础选择器会影响多个角色，局部调整优先限定页面容器。

## 10. 数据与存储关系

```text
users                         账号、角色、状态、密码及通知联系信息
 ├─ maintenance_requests      请求者提交的报修
 │   ├─ request_status_history
 │   ├─ request_attachments   附件元数据，文件内容另存本地 uploads 目录
 │   ├─ assignments           多次指派历史，active 标识有效记录
 │   ├─ work_orders           关联维修工单
 │   │   └─ repair_records
 │   ├─ request_feedback
 │   └─ sla_states
 ├─ technician_profiles / technician_skills / technician_service_areas
 ├─ notifications / delivery_tasks
 └─ community_questions / community_answers / community_reports

locations → facilities
announcements、audit_entries、sla_policies、request_ticket_sequences、account_initialization
```

这是逻辑关系导览；实体间有些使用 ID 字段和服务查询协作，不是所有箭头都对应 Java 的 `@OneToMany`。

Flyway 负责建表与后续结构修改，应用配置 `ddl-auto: none`。当前 SQL 文件有历史版本跳号、早期定义与后续兼容修复；不要重命名或覆盖已经执行过的迁移。新增结构应追加新版本。具体约束以迁移 SQL 为准，实体注解不能代替数据库定义。

## 11. 消息、邮件、SLA 和审计

状态变化发布 `RequestStatusChangedEvent`，维修完成发布 `WorkOrderCompletedEvent`。监听器通过 `DeliveryOutboxService` 在业务事务中写 `delivery_tasks`，提交成功后由 Dispatcher/Worker 投递；定时扫描负责待处理和重试任务，去重键避免重复任务。

站内消息存入 `notifications`。邮件功能是可选通道，默认关闭，需要显式配置 SMTP 和收件联系信息，不能仅凭代码存在就认为邮件已配置完成。

SLA 由策略、计算服务、扫描器和状态表组成。默认 SQL 不预置生产时限，也不能把“有扫描器”理解为所有请求已经启用 SLA。

审计模块记录相关管理行为；报修状态历史则由 request 自己保存。这两种记录用途不同。

## 12. 安全规则在哪看

- `SecurityConfig`：URL、HTTP 方法和角色；CSRF；Session 登录退出；默认拒绝未匹配路由。
- `ActiveAccountFilter`：每次请求重新核对账号停用、角色/安全版本及强制改密，不只在登录时检查。
- `RequestAccessService`：本人报修、管理员及当前指派工程师的资源可读性。
- `WorkOrderService`：有效指派及维修前提，限制工程师写入。
- `CommunityAccessGuard`：社区内容权限。
- `NotificationService`：按当前收件人读取/标记消息。
- `AttachmentValidator` / `AttachmentService` / `AttachmentController`：图像校验、元数据、私有文件读取。

`templates` 的 `th:if` 用于按钮显示，不是业务授权。

## 13. 测试与运行

测试代码按业务包分组。重点先读：

- `request/RequestWorkflowTest.java`：维修生命周期、审核、反馈与规则。
- `request/domain/RequestTransitionTest.java`：角色/状态允许矩阵。
- `dispatch/TechnicianAccessIT.java`：真实服务和页面下的指派权限、撤销/转派、取消和维修写入限制。
- `request/web/RequestPagesRenderingIT.java`：真实 Thymeleaf 渲染、星级反馈及消息角标。
- `auth/RoleWorkspacesIT.java`：角色入口和工作区。
- `*PostgresIT.java`：真实 PostgreSQL 的迁移、约束和并发场景，需专用测试库。

`mvn test`、`mvn verify`、`mvn -Ppostgres-it verify` 不是同一个范围；Failsafe 的 PostgreSQL 集成测试默认排除，按 pom.xml 的 profile 显式启用。不要用业务数据库运行数据库测试。

`Jenkinsfile` 定义 compile/test/package/verify 和可选 PostgreSQL 检查，外部 Security 扫描阶段目前明确禁用，不能把流水线绿色视为已跑过外部安全扫描。

Windows 本地正常启动看 `start-smartfix.ps1`；环境变量覆盖数据库地址等配置。脚本把宿主机数据库端口设为 5433，容器内 PostgreSQL 使用 5432；不要把两个地址混用。生产应使用相应部署环境配置，开发日志和默认 cookie 选项不能直接视为生产设置。

## 14. 推荐阅读顺序

1. `pom.xml`、`SmartFixApplication.java`、`application.yml`：认识运行基础。
2. `SecurityConfig.java`、`HomeController.java`、`layout.html`：理解登录后怎么到达各角色页面。
3. request 的 `MaintenanceRequest`、`RequestStatus`、`RequestStage`、`RequestTransition`：理解业务对象和状态。
4. `RequestLifecycleService`：理解状态、历史和事件集中管理。
5. `RequestSubmissionController` → `RequestSubmissionService`：完整读一次用户提交。
6. `DispatchController` → `AssignmentService` → `WorkOrderLifecycleParticipant`：完整读一次指派。
7. `WorkOrderController` → `WorkOrderService`：读接单、记录、完成。
8. `RequestConfirmationController` → `RequestConfirmationService`：读验收、终审、取消、反馈。
9. `RequestQueryService`、`RequestPresentationService` → `request/detail.html`：理解页面数据与可用按钮如何产生。
10. 阅读对应测试，再按需求选择地图、社区、消息和后台任务。

想改页面：先找模板，再看 layout 里加载的 CSS/JS。想改业务：先读状态规则和 Service，再改 Controller/模板。想改数据结构：先看迁移和 Repository，再加迁移。

## 15. 当前实现与旧注释的区别

`SmartFixApplication.java` 等文件仍可能保留早期“仅脚手架/尚无用户存储”注释，这些描述已落后于当前实现。当前已有数据库账号、完整业务模块和测试。此次只做架构说明，没有顺手修改这些业务源码或注释。

下方清单是本次读取的实际文件，不应凭旧注释判断功能是否完成；功能是否对主导航展示，与它是否仍有受保护路由，也需要分开判断。

## 附录 A：完整后端源码索引

### announcement

- [controller/AnnouncementController.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/announcement/controller/AnnouncementController.java)
- [domain/Announcement.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/announcement/domain/Announcement.java)
- [domain/AnnouncementStatus.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/announcement/domain/AnnouncementStatus.java)
- [dto/AnnouncementResponse.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/announcement/dto/AnnouncementResponse.java)
- [dto/CreateAnnouncementCommand.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/announcement/dto/CreateAnnouncementCommand.java)
- [repository/AnnouncementRepository.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/announcement/repository/AnnouncementRepository.java)
- [service/AnnouncementService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/announcement/service/AnnouncementService.java)

### audit

- [controller/AuditController.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/audit/controller/AuditController.java)
- [domain/AuditEntry.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/audit/domain/AuditEntry.java)
- [dto/AuditEntryResponse.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/audit/dto/AuditEntryResponse.java)
- [listener/NotificationContactAuditListener.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/audit/listener/NotificationContactAuditListener.java)
- [repository/AuditEntryRepository.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/audit/repository/AuditEntryRepository.java)
- [service/AuditService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/audit/service/AuditService.java)

### auth

- [config/SecurityConfig.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/auth/config/SecurityConfig.java)
- [controller/CsrfTokens.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/auth/controller/CsrfTokens.java)
- [controller/LoginController.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/auth/controller/LoginController.java)
- [controller/PasswordController.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/auth/controller/PasswordController.java)
- [controller/RegistrationController.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/auth/controller/RegistrationController.java)
- [security/ActiveAccountFilter.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/auth/security/ActiveAccountFilter.java)
- [security/SelectedAccountType.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/auth/security/SelectedAccountType.java)
- [security/SelectedAccountTypeAuthenticationProvider.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/auth/security/SelectedAccountTypeAuthenticationProvider.java)
- [security/SelectedAccountTypeFailureHandler.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/auth/security/SelectedAccountTypeFailureHandler.java)
- [security/SelectedAccountTypeMismatchException.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/auth/security/SelectedAccountTypeMismatchException.java)
- [security/SmartFixUserDetails.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/auth/security/SmartFixUserDetails.java)
- [service/RegistrationRateLimiter.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/auth/service/RegistrationRateLimiter.java)
- [service/SmartFixUserDetailsService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/auth/service/SmartFixUserDetailsService.java)

### common

- [configuration/LegacyMigrationCompatibility.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/common/configuration/LegacyMigrationCompatibility.java)
- [configuration/LegacyMigrationRecoveryConfiguration.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/common/configuration/LegacyMigrationRecoveryConfiguration.java)
- [configuration/TimeConfig.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/common/configuration/TimeConfig.java)
- [exception/BusinessConflictException.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/common/exception/BusinessConflictException.java)
- [exception/GlobalExceptionHandler.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/common/exception/GlobalExceptionHandler.java)
- [exception/InputValidationException.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/common/exception/InputValidationException.java)
- [exception/ResourceNotFoundException.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/common/exception/ResourceNotFoundException.java)
- [web/HomeController.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/common/web/HomeController.java)
- [web/NavigationAdvice.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/common/web/NavigationAdvice.java)
- [web/NotificationNavigationAdvice.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/common/web/NotificationNavigationAdvice.java)

### community

- [config/CommunityProperties.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/community/config/CommunityProperties.java)
- [controller/AdminCommunityController.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/community/controller/AdminCommunityController.java)
- [controller/CommunityAnswerController.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/community/controller/CommunityAnswerController.java)
- [controller/CommunityDetailPageModel.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/community/controller/CommunityDetailPageModel.java)
- [controller/CommunityQuestionController.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/community/controller/CommunityQuestionController.java)
- [controller/CommunityReportController.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/community/controller/CommunityReportController.java)
- [domain/CommunityAnswer.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/community/domain/CommunityAnswer.java)
- [domain/CommunityCategory.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/community/domain/CommunityCategory.java)
- [domain/CommunityContentStatus.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/community/domain/CommunityContentStatus.java)
- [domain/CommunityContentType.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/community/domain/CommunityContentType.java)
- [domain/CommunityQuestion.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/community/domain/CommunityQuestion.java)
- [domain/CommunityReport.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/community/domain/CommunityReport.java)
- [domain/CommunityReportReason.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/community/domain/CommunityReportReason.java)
- [domain/CommunityReportStatus.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/community/domain/CommunityReportStatus.java)
- [dto/AnswerFormCommand.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/community/dto/AnswerFormCommand.java)
- [dto/CommunityAnswerResponse.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/community/dto/CommunityAnswerResponse.java)
- [dto/CommunityQuestionDetailResponse.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/community/dto/CommunityQuestionDetailResponse.java)
- [dto/CommunityQuestionSummaryResponse.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/community/dto/CommunityQuestionSummaryResponse.java)
- [dto/CommunityReportResponse.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/community/dto/CommunityReportResponse.java)
- [dto/MineTab.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/community/dto/MineTab.java)
- [dto/MyAnswerResponse.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/community/dto/MyAnswerResponse.java)
- [dto/OwnAnswerResponse.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/community/dto/OwnAnswerResponse.java)
- [dto/QuestionFilter.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/community/dto/QuestionFilter.java)
- [dto/QuestionFormCommand.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/community/dto/QuestionFormCommand.java)
- [dto/ReportContentCommand.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/community/dto/ReportContentCommand.java)
- [dto/ReportView.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/community/dto/ReportView.java)
- [dto/ResolveReportCommand.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/community/dto/ResolveReportCommand.java)
- [event/CommunityAnswerAcceptedEvent.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/community/event/CommunityAnswerAcceptedEvent.java)
- [event/CommunityAnswerCreatedEvent.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/community/event/CommunityAnswerCreatedEvent.java)
- [event/CommunityContentHiddenEvent.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/community/event/CommunityContentHiddenEvent.java)
- [repository/CommunityAnswerRepository.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/community/repository/CommunityAnswerRepository.java)
- [repository/CommunityQuestionRepository.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/community/repository/CommunityQuestionRepository.java)
- [repository/CommunityReportRepository.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/community/repository/CommunityReportRepository.java)
- [service/CommunityAccessGuard.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/community/service/CommunityAccessGuard.java)
- [service/CommunityAnswerService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/community/service/CommunityAnswerService.java)
- [service/CommunityModerationService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/community/service/CommunityModerationService.java)
- [service/CommunityQueryService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/community/service/CommunityQueryService.java)
- [service/CommunityQuestionService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/community/service/CommunityQuestionService.java)

### dispatch

- [controller/DispatchController.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/dispatch/controller/DispatchController.java)
- [domain/Assignment.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/dispatch/domain/Assignment.java)
- [dto/AssignmentResponse.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/dispatch/dto/AssignmentResponse.java)
- [dto/AssignTechnicianCommand.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/dispatch/dto/AssignTechnicianCommand.java)
- [dto/DispatchPageResponse.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/dispatch/dto/DispatchPageResponse.java)
- [dto/ReassignTechnicianCommand.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/dispatch/dto/ReassignTechnicianCommand.java)
- [dto/TechnicianRecommendationResponse.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/dispatch/dto/TechnicianRecommendationResponse.java)
- [dto/WithdrawAssignmentCommand.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/dispatch/dto/WithdrawAssignmentCommand.java)
- [event/AssignmentCreatedEvent.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/dispatch/event/AssignmentCreatedEvent.java)
- [event/AssignmentWithdrawnEvent.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/dispatch/event/AssignmentWithdrawnEvent.java)
- [repository/AssignmentRepository.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/dispatch/repository/AssignmentRepository.java)
- [service/AssignmentReadService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/dispatch/service/AssignmentReadService.java)
- [service/AssignmentService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/dispatch/service/AssignmentService.java)
- [service/DispatchPageService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/dispatch/service/DispatchPageService.java)
- [service/RequestAssignmentLookupAdapter.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/dispatch/service/RequestAssignmentLookupAdapter.java)
- [service/TechnicianRecommendationService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/dispatch/service/TechnicianRecommendationService.java)

### facility

- [controller/FacilityController.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/facility/controller/FacilityController.java)
- [domain/Facility.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/facility/domain/Facility.java)
- [domain/FacilityStatus.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/facility/domain/FacilityStatus.java)
- [domain/Location.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/facility/domain/Location.java)
- [dto/CampusBuilding.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/facility/dto/CampusBuilding.java)
- [dto/CampusGeography.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/facility/dto/CampusGeography.java)
- [dto/CampusMapBuildingResponse.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/facility/dto/CampusMapBuildingResponse.java)
- [dto/CampusMapFacilityResponse.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/facility/dto/CampusMapFacilityResponse.java)
- [dto/CampusMapResponse.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/facility/dto/CampusMapResponse.java)
- [dto/CreateFacilityCommand.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/facility/dto/CreateFacilityCommand.java)
- [dto/FacilityResponse.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/facility/dto/FacilityResponse.java)
- [dto/LocationFacilityCount.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/facility/dto/LocationFacilityCount.java)
- [dto/LocationResponse.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/facility/dto/LocationResponse.java)
- [repository/FacilityRepository.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/facility/repository/FacilityRepository.java)
- [repository/LocationRepository.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/facility/repository/LocationRepository.java)
- [service/CampusGeographyService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/facility/service/CampusGeographyService.java)
- [service/CampusMapService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/facility/service/CampusMapService.java)
- [service/FacilityService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/facility/service/FacilityService.java)
- [service/LocationService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/facility/service/LocationService.java)

### notification

- [config/DeliveryProperties.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/notification/config/DeliveryProperties.java)
- [config/DeliveryScheduling.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/notification/config/DeliveryScheduling.java)
- [controller/DeliveryAdministrationController.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/notification/controller/DeliveryAdministrationController.java)
- [controller/NotificationController.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/notification/controller/NotificationController.java)
- [domain/DeliveryTask.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/notification/domain/DeliveryTask.java)
- [domain/Notification.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/notification/domain/Notification.java)
- [event/DeliveryQueuedEvent.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/notification/event/DeliveryQueuedEvent.java)
- [listener/CommunityNotificationListener.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/notification/listener/CommunityNotificationListener.java)
- [listener/NotificationEventListener.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/notification/listener/NotificationEventListener.java)
- [listener/SlaNotificationListener.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/notification/listener/SlaNotificationListener.java)
- [repository/DeliveryTaskInsertRepository.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/notification/repository/DeliveryTaskInsertRepository.java)
- [repository/DeliveryTaskRepository.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/notification/repository/DeliveryTaskRepository.java)
- [repository/NotificationInsertRepository.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/notification/repository/NotificationInsertRepository.java)
- [repository/NotificationRepository.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/notification/repository/NotificationRepository.java)
- [repository/PostgresNotificationInsertRepository.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/notification/repository/PostgresNotificationInsertRepository.java)
- [service/DeliveryAdministrationService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/notification/service/DeliveryAdministrationService.java)
- [service/DeliveryDispatcher.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/notification/service/DeliveryDispatcher.java)
- [service/DeliveryOutboxService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/notification/service/DeliveryOutboxService.java)
- [service/DeliveryWorker.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/notification/service/DeliveryWorker.java)
- [service/EmailDeliveryService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/notification/service/EmailDeliveryService.java)
- [service/NotificationService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/notification/service/NotificationService.java)

### reporting

- [controller/AdminWorkspaceController.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/reporting/controller/AdminWorkspaceController.java)
- [controller/CampusMapController.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/reporting/controller/CampusMapController.java)
- [controller/DashboardController.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/reporting/controller/DashboardController.java)
- [controller/ReportController.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/reporting/controller/ReportController.java)
- [dto/AdminDashboardResponse.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/reporting/dto/AdminDashboardResponse.java)
- [dto/CampusMapSnapshot.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/reporting/dto/CampusMapSnapshot.java)
- [dto/OperationalReportResponse.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/reporting/dto/OperationalReportResponse.java)
- [dto/TechnicianDashboardResponse.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/reporting/dto/TechnicianDashboardResponse.java)
- [service/CampusMapStatusService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/reporting/service/CampusMapStatusService.java)
- [service/DashboardService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/reporting/service/DashboardService.java)
- [service/OperationalReportService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/reporting/service/OperationalReportService.java)
- [service/ReportExportService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/reporting/service/ReportExportService.java)
- [service/ReportingPeriodService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/reporting/service/ReportingPeriodService.java)

### request

- [config/AttachmentProperties.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/config/AttachmentProperties.java)
- [config/RequestWorkflowProperties.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/config/RequestWorkflowProperties.java)
- [controller/AttachmentController.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/controller/AttachmentController.java)
- [controller/AttachmentInspectionController.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/controller/AttachmentInspectionController.java)
- [controller/RequestConfirmationController.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/controller/RequestConfirmationController.java)
- [controller/RequestQueryController.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/controller/RequestQueryController.java)
- [controller/RequestReviewController.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/controller/RequestReviewController.java)
- [controller/RequestSubmissionController.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/controller/RequestSubmissionController.java)
- [domain/Attachment.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/domain/Attachment.java)
- [domain/MaintenanceCategory.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/domain/MaintenanceCategory.java)
- [domain/MaintenanceRequest.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/domain/MaintenanceRequest.java)
- [domain/RequestFeedback.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/domain/RequestFeedback.java)
- [domain/RequestStage.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/domain/RequestStage.java)
- [domain/RequestStatus.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/domain/RequestStatus.java)
- [domain/RequestStatusHistory.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/domain/RequestStatusHistory.java)
- [domain/RequestTicketSequence.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/domain/RequestTicketSequence.java)
- [domain/RequestTransition.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/domain/RequestTransition.java)
- [domain/UrgencyLevel.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/domain/UrgencyLevel.java)
- [dto/AdminRequestRow.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/dto/AdminRequestRow.java)
- [dto/AttachmentResponse.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/dto/AttachmentResponse.java)
- [dto/FinalReviewCommand.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/dto/FinalReviewCommand.java)
- [dto/LocationRequestCount.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/dto/LocationRequestCount.java)
- [dto/MaintenanceRequestDetailsResponse.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/dto/MaintenanceRequestDetailsResponse.java)
- [dto/MaintenanceRequestSubmissionResponse.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/dto/MaintenanceRequestSubmissionResponse.java)
- [dto/MaintenanceRequestSummaryResponse.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/dto/MaintenanceRequestSummaryResponse.java)
- [dto/ReadableAttachment.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/dto/ReadableAttachment.java)
- [dto/RequestFeedbackCommand.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/dto/RequestFeedbackCommand.java)
- [dto/RequestLifecycleActionCommand.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/dto/RequestLifecycleActionCommand.java)
- [dto/RequestPresentationResponse.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/dto/RequestPresentationResponse.java)
- [dto/RequestReviewCommand.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/dto/RequestReviewCommand.java)
- [dto/RequestSnapshotResponse.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/dto/RequestSnapshotResponse.java)
- [dto/RequestStatusHistoryResponse.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/dto/RequestStatusHistoryResponse.java)
- [dto/RequestTransitionResponse.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/dto/RequestTransitionResponse.java)
- [dto/RequestWorkSummary.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/dto/RequestWorkSummary.java)
- [dto/StoredAttachment.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/dto/StoredAttachment.java)
- [dto/SubmitMaintenanceRequestCommand.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/dto/SubmitMaintenanceRequestCommand.java)
- [dto/UploadAttachmentCommand.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/dto/UploadAttachmentCommand.java)
- [event/RequestStatusChangedEvent.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/event/RequestStatusChangedEvent.java)
- [repository/AttachmentRepository.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/repository/AttachmentRepository.java)
- [repository/MaintenanceRequestRepository.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/repository/MaintenanceRequestRepository.java)
- [repository/RequestFeedbackRepository.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/repository/RequestFeedbackRepository.java)
- [repository/RequestStatusHistoryRepository.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/repository/RequestStatusHistoryRepository.java)
- [repository/RequestTicketSequenceRepository.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/repository/RequestTicketSequenceRepository.java)
- [service/AttachmentInspectionService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/service/AttachmentInspectionService.java)
- [service/AttachmentService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/service/AttachmentService.java)
- [service/AttachmentStorageService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/service/AttachmentStorageService.java)
- [service/RequestAccessService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/service/RequestAccessService.java)
- [service/RequestAssignmentAccessService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/service/RequestAssignmentAccessService.java)
- [service/RequestConfirmationService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/service/RequestConfirmationService.java)
- [service/RequestEvidenceService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/service/RequestEvidenceService.java)
- [service/RequestLifecycleService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/service/RequestLifecycleService.java)
- [service/RequestPresentationService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/service/RequestPresentationService.java)
- [service/RequestQueryService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/service/RequestQueryService.java)
- [service/RequestReadService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/service/RequestReadService.java)
- [service/RequestReviewService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/service/RequestReviewService.java)
- [service/RequestSubmissionService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/service/RequestSubmissionService.java)
- [service/RequestTicketNumberGenerator.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/service/RequestTicketNumberGenerator.java)
- [spi/ActiveAssignmentLookup.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/spi/ActiveAssignmentLookup.java)
- [spi/RequestDetailContributor.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/spi/RequestDetailContributor.java)
- [spi/RequestTransitionParticipant.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/spi/RequestTransitionParticipant.java)
- [storage/LocalAttachmentStorageService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/storage/LocalAttachmentStorageService.java)
- [validation/AttachmentValidator.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/validation/AttachmentValidator.java)

### sla

- [controller/SlaController.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/sla/controller/SlaController.java)
- [domain/SlaPhase.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/sla/domain/SlaPhase.java)
- [domain/SlaPolicy.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/sla/domain/SlaPolicy.java)
- [domain/SlaState.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/sla/domain/SlaState.java)
- [event/SlaBreachedEvent.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/sla/event/SlaBreachedEvent.java)
- [event/SlaDueSoonEvent.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/sla/event/SlaDueSoonEvent.java)
- [repository/SlaPolicyRepository.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/sla/repository/SlaPolicyRepository.java)
- [repository/SlaStateRepository.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/sla/repository/SlaStateRepository.java)
- [service/SlaCalculationService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/sla/service/SlaCalculationService.java)
- [service/SlaPolicyService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/sla/service/SlaPolicyService.java)
- [service/SlaScanner.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/sla/service/SlaScanner.java)
- [service/SlaScanWorker.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/sla/service/SlaScanWorker.java)

### technician

- [controller/AdminTechnicianController.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/technician/controller/AdminTechnicianController.java)
- [controller/TechnicianProfileController.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/technician/controller/TechnicianProfileController.java)
- [domain/AvailabilityStatus.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/technician/domain/AvailabilityStatus.java)
- [domain/TechnicianProfile.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/technician/domain/TechnicianProfile.java)
- [dto/TechnicianCandidateResponse.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/technician/dto/TechnicianCandidateResponse.java)
- [dto/TechnicianProfileResponse.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/technician/dto/TechnicianProfileResponse.java)
- [dto/UpdateTechnicianProfileCommand.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/technician/dto/UpdateTechnicianProfileCommand.java)
- [repository/TechnicianProfileRepository.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/technician/repository/TechnicianProfileRepository.java)
- [service/TechnicianDirectoryService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/technician/service/TechnicianDirectoryService.java)
- [service/TechnicianWorkloadService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/technician/service/TechnicianWorkloadService.java)

### user

- [config/BootstrapAdminInitializer.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/user/config/BootstrapAdminInitializer.java)
- [config/BootstrapAdminProperties.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/user/config/BootstrapAdminProperties.java)
- [config/PasswordConfig.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/user/config/PasswordConfig.java)
- [controller/UserManagementController.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/user/controller/UserManagementController.java)
- [domain/AccountStatus.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/user/domain/AccountStatus.java)
- [domain/Role.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/user/domain/Role.java)
- [domain/User.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/user/domain/User.java)
- [dto/ChangeAccountStatusCommand.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/user/dto/ChangeAccountStatusCommand.java)
- [dto/ChangeUserRoleCommand.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/user/dto/ChangeUserRoleCommand.java)
- [dto/CreateUserCommand.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/user/dto/CreateUserCommand.java)
- [dto/RegistrationCommand.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/user/dto/RegistrationCommand.java)
- [dto/UserAccessResponse.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/user/dto/UserAccessResponse.java)
- [dto/UserAuthenticationData.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/user/dto/UserAuthenticationData.java)
- [dto/UserSummaryResponse.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/user/dto/UserSummaryResponse.java)
- [event/NotificationContactChangedEvent.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/user/event/NotificationContactChangedEvent.java)
- [repository/UserRepository.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/user/repository/UserRepository.java)
- [service/AccountAdministrationService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/user/service/AccountAdministrationService.java)
- [service/NotificationContactService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/user/service/NotificationContactService.java)
- [service/UserBootstrapService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/user/service/UserBootstrapService.java)
- [service/UserService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/user/service/UserService.java)
- [validation/PasswordConstraintValidator.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/user/validation/PasswordConstraintValidator.java)
- [validation/PasswordPolicy.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/user/validation/PasswordPolicy.java)
- [validation/ValidPassword.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/user/validation/ValidPassword.java)

### workorder

- [controller/WorkOrderController.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/workorder/controller/WorkOrderController.java)
- [domain/RepairRecord.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/workorder/domain/RepairRecord.java)
- [domain/WorkOrder.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/workorder/domain/WorkOrder.java)
- [domain/WorkOrderStatus.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/workorder/domain/WorkOrderStatus.java)
- [dto/CompleteWorkOrderCommand.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/workorder/dto/CompleteWorkOrderCommand.java)
- [dto/RepairRecordCommand.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/workorder/dto/RepairRecordCommand.java)
- [dto/RepairRecordResponse.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/workorder/dto/RepairRecordResponse.java)
- [dto/WorkOrderQueueRow.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/workorder/dto/WorkOrderQueueRow.java)
- [dto/WorkOrderQueueView.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/workorder/dto/WorkOrderQueueView.java)
- [dto/WorkOrderResponse.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/workorder/dto/WorkOrderResponse.java)
- [event/WorkOrderCompletedEvent.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/workorder/event/WorkOrderCompletedEvent.java)
- [repository/RepairRecordRepository.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/workorder/repository/RepairRecordRepository.java)
- [repository/WorkOrderRepository.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/workorder/repository/WorkOrderRepository.java)
- [service/WorkOrderDetailContributor.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/workorder/service/WorkOrderDetailContributor.java)
- [service/WorkOrderLifecycleParticipant.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/workorder/service/WorkOrderLifecycleParticipant.java)
- [service/WorkOrderService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/workorder/service/WorkOrderService.java)

入口：[SmartFixApplication.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/SmartFixApplication.java)

## 附录 B：页面、样式、脚本、迁移与测试索引

### HTML 模板

- [account/password.html](C:/Users/zhour/smartfix/src/main/resources/templates/account/password.html)
- [admin/community-audit.html](C:/Users/zhour/smartfix/src/main/resources/templates/admin/community-audit.html)
- [admin/community-reports.html](C:/Users/zhour/smartfix/src/main/resources/templates/admin/community-reports.html)
- [admin/overview.html](C:/Users/zhour/smartfix/src/main/resources/templates/admin/overview.html)
- [admin/request-queue.html](C:/Users/zhour/smartfix/src/main/resources/templates/admin/request-queue.html)
- [admin/requests.html](C:/Users/zhour/smartfix/src/main/resources/templates/admin/requests.html)
- [admin/technicians.html](C:/Users/zhour/smartfix/src/main/resources/templates/admin/technicians.html)
- [admin/users.html](C:/Users/zhour/smartfix/src/main/resources/templates/admin/users.html)
- [announcement/admin.html](C:/Users/zhour/smartfix/src/main/resources/templates/announcement/admin.html)
- [announcement/list.html](C:/Users/zhour/smartfix/src/main/resources/templates/announcement/list.html)
- [community/answer-form.html](C:/Users/zhour/smartfix/src/main/resources/templates/community/answer-form.html)
- [community/index.html](C:/Users/zhour/smartfix/src/main/resources/templates/community/index.html)
- [community/mine.html](C:/Users/zhour/smartfix/src/main/resources/templates/community/mine.html)
- [community/question-form.html](C:/Users/zhour/smartfix/src/main/resources/templates/community/question-form.html)
- [community/question.html](C:/Users/zhour/smartfix/src/main/resources/templates/community/question.html)
- [dispatch/assign.html](C:/Users/zhour/smartfix/src/main/resources/templates/dispatch/assign.html)
- [error.html](C:/Users/zhour/smartfix/src/main/resources/templates/error.html)
- [facility/list.html](C:/Users/zhour/smartfix/src/main/resources/templates/facility/list.html)
- [facility/map.html](C:/Users/zhour/smartfix/src/main/resources/templates/facility/map.html)
- [fragments/components.html](C:/Users/zhour/smartfix/src/main/resources/templates/fragments/components.html)
- [fragments/illustrations.html](C:/Users/zhour/smartfix/src/main/resources/templates/fragments/illustrations.html)
- [fragments/layout.html](C:/Users/zhour/smartfix/src/main/resources/templates/fragments/layout.html)
- [fragments/ui-icons.html](C:/Users/zhour/smartfix/src/main/resources/templates/fragments/ui-icons.html)
- [home.html](C:/Users/zhour/smartfix/src/main/resources/templates/home.html)
- [login.html](C:/Users/zhour/smartfix/src/main/resources/templates/login.html)
- [notification/delivery-admin.html](C:/Users/zhour/smartfix/src/main/resources/templates/notification/delivery-admin.html)
- [notification/list.html](C:/Users/zhour/smartfix/src/main/resources/templates/notification/list.html)
- [register.html](C:/Users/zhour/smartfix/src/main/resources/templates/register.html)
- [reporting/dashboard.html](C:/Users/zhour/smartfix/src/main/resources/templates/reporting/dashboard.html)
- [reporting/report.html](C:/Users/zhour/smartfix/src/main/resources/templates/reporting/report.html)
- [reporting/technician-dashboard.html](C:/Users/zhour/smartfix/src/main/resources/templates/reporting/technician-dashboard.html)
- [request/attachment-inspection.html](C:/Users/zhour/smartfix/src/main/resources/templates/request/attachment-inspection.html)
- [request/detail.html](C:/Users/zhour/smartfix/src/main/resources/templates/request/detail.html)
- [request/mine.html](C:/Users/zhour/smartfix/src/main/resources/templates/request/mine.html)
- [request/new.html](C:/Users/zhour/smartfix/src/main/resources/templates/request/new.html)
- [request/review.html](C:/Users/zhour/smartfix/src/main/resources/templates/request/review.html)
- [sla/admin.html](C:/Users/zhour/smartfix/src/main/resources/templates/sla/admin.html)
- [technician/profile.html](C:/Users/zhour/smartfix/src/main/resources/templates/technician/profile.html)
- [workorder/detail.html](C:/Users/zhour/smartfix/src/main/resources/templates/workorder/detail.html)
- [workorder/mine.html](C:/Users/zhour/smartfix/src/main/resources/templates/workorder/mine.html)

### 样式（含第三方）

- [admin-accounts.css](C:/Users/zhour/smartfix/src/main/resources/static/css/admin-accounts.css)
- [admin-requests.css](C:/Users/zhour/smartfix/src/main/resources/static/css/admin-requests.css)
- [admin-stewardship.css](C:/Users/zhour/smartfix/src/main/resources/static/css/admin-stewardship.css)
- [admin-workspace.css](C:/Users/zhour/smartfix/src/main/resources/static/css/admin-workspace.css)
- [campus-map-experience.css](C:/Users/zhour/smartfix/src/main/resources/static/css/campus-map-experience.css)
- [community-experience.css](C:/Users/zhour/smartfix/src/main/resources/static/css/community-experience.css)
- [engineer-detail.css](C:/Users/zhour/smartfix/src/main/resources/static/css/engineer-detail.css)
- [engineer-workspace.css](C:/Users/zhour/smartfix/src/main/resources/static/css/engineer-workspace.css)
- [login.css](C:/Users/zhour/smartfix/src/main/resources/static/css/login.css)
- [request-experience.css](C:/Users/zhour/smartfix/src/main/resources/static/css/request-experience.css)
- [request-feedback.css](C:/Users/zhour/smartfix/src/main/resources/static/css/request-feedback.css)
- [requester-detail.css](C:/Users/zhour/smartfix/src/main/resources/static/css/requester-detail.css)
- [requester-home.css](C:/Users/zhour/smartfix/src/main/resources/static/css/requester-home.css)
- [requester-navigation.css](C:/Users/zhour/smartfix/src/main/resources/static/css/requester-navigation.css)
- [requester-sections.css](C:/Users/zhour/smartfix/src/main/resources/static/css/requester-sections.css)
- [site.css](C:/Users/zhour/smartfix/src/main/resources/static/css/site.css)
- [vendor/leaflet/leaflet.css](C:/Users/zhour/smartfix/src/main/resources/static/css/vendor/leaflet/leaflet.css)
- [visual-refresh.css](C:/Users/zhour/smartfix/src/main/resources/static/css/visual-refresh.css)

### 脚本（含第三方）

- [accessibility.js](C:/Users/zhour/smartfix/src/main/resources/static/js/accessibility.js)
- [admin-dispatch.js](C:/Users/zhour/smartfix/src/main/resources/static/js/admin-dispatch.js)
- [announcement-carousel.js](C:/Users/zhour/smartfix/src/main/resources/static/js/announcement-carousel.js)
- [campus-map.js](C:/Users/zhour/smartfix/src/main/resources/static/js/campus-map.js)
- [community-experience.js](C:/Users/zhour/smartfix/src/main/resources/static/js/community-experience.js)
- [community-form.js](C:/Users/zhour/smartfix/src/main/resources/static/js/community-form.js)
- [login.js](C:/Users/zhour/smartfix/src/main/resources/static/js/login.js)
- [request-form.js](C:/Users/zhour/smartfix/src/main/resources/static/js/request-form.js)
- [requester-home.js](C:/Users/zhour/smartfix/src/main/resources/static/js/requester-home.js)
- [requester-navigation.js](C:/Users/zhour/smartfix/src/main/resources/static/js/requester-navigation.js)
- [vendor/leaflet/leaflet.js](C:/Users/zhour/smartfix/src/main/resources/static/js/vendor/leaflet/leaflet.js)
- [workspace.js](C:/Users/zhour/smartfix/src/main/resources/static/js/workspace.js)

### 数据库迁移

- [V10__create_technician_profiles.sql](C:/Users/zhour/smartfix/src/main/resources/db/migration/V10__create_technician_profiles.sql)
- [V11__create_assignments.sql](C:/Users/zhour/smartfix/src/main/resources/db/migration/V11__create_assignments.sql)
- [V12__create_facilities.sql](C:/Users/zhour/smartfix/src/main/resources/db/migration/V12__create_facilities.sql)
- [V14__create_notifications.sql](C:/Users/zhour/smartfix/src/main/resources/db/migration/V14__create_notifications.sql)
- [V16__create_announcements.sql](C:/Users/zhour/smartfix/src/main/resources/db/migration/V16__create_announcements.sql)
- [V17__create_community_questions_and_answers.sql](C:/Users/zhour/smartfix/src/main/resources/db/migration/V17__create_community_questions_and_answers.sql)
- [V18__add_community_accepted_answer_constraint.sql](C:/Users/zhour/smartfix/src/main/resources/db/migration/V18__add_community_accepted_answer_constraint.sql)
- [V19__create_community_reports.sql](C:/Users/zhour/smartfix/src/main/resources/db/migration/V19__create_community_reports.sql)
- [V1__baseline.sql](C:/Users/zhour/smartfix/src/main/resources/db/migration/V1__baseline.sql)
- [V20__create_notifications.sql](C:/Users/zhour/smartfix/src/main/resources/db/migration/V20__create_notifications.sql)
- [V21__create_audit_entries.sql](C:/Users/zhour/smartfix/src/main/resources/db/migration/V21__create_audit_entries.sql)
- [V22__add_verified_nus_building_locations.sql](C:/Users/zhour/smartfix/src/main/resources/db/migration/V22__add_verified_nus_building_locations.sql)
- [V23__add_dispatch_and_technician_support.sql](C:/Users/zhour/smartfix/src/main/resources/db/migration/V23__add_dispatch_and_technician_support.sql)
- [V24__secure_account_initialization_and_passwords.sql](C:/Users/zhour/smartfix/src/main/resources/db/migration/V24__secure_account_initialization_and_passwords.sql)
- [V25__reconcile_notification_schema.sql](C:/Users/zhour/smartfix/src/main/resources/db/migration/V25__reconcile_notification_schema.sql)
- [V26__create_delivery_tasks.sql](C:/Users/zhour/smartfix/src/main/resources/db/migration/V26__create_delivery_tasks.sql)
- [V27__add_notification_contacts_and_sla.sql](C:/Users/zhour/smartfix/src/main/resources/db/migration/V27__add_notification_contacts_and_sla.sql)
- [V2__create_users.sql](C:/Users/zhour/smartfix/src/main/resources/db/migration/V2__create_users.sql)
- [V3__create_locations.sql](C:/Users/zhour/smartfix/src/main/resources/db/migration/V3__create_locations.sql)
- [V4__create_maintenance_requests.sql](C:/Users/zhour/smartfix/src/main/resources/db/migration/V4__create_maintenance_requests.sql)
- [V5__create_request_attachments.sql](C:/Users/zhour/smartfix/src/main/resources/db/migration/V5__create_request_attachments.sql)
- [V6__create_request_status_history.sql](C:/Users/zhour/smartfix/src/main/resources/db/migration/V6__create_request_status_history.sql)
- [V7__extend_request_lifecycle.sql](C:/Users/zhour/smartfix/src/main/resources/db/migration/V7__extend_request_lifecycle.sql)
- [V8__create_work_orders.sql](C:/Users/zhour/smartfix/src/main/resources/db/migration/V8__create_work_orders.sql)
- [V9__create_request_feedback.sql](C:/Users/zhour/smartfix/src/main/resources/db/migration/V9__create_request_feedback.sql)

### 测试代码

- [com/smartfix/announcement/controller/AnnouncementControllerTest.java](C:/Users/zhour/smartfix/src/test/java/com/smartfix/announcement/controller/AnnouncementControllerTest.java)
- [com/smartfix/announcement/service/AnnouncementServiceTest.java](C:/Users/zhour/smartfix/src/test/java/com/smartfix/announcement/service/AnnouncementServiceTest.java)
- [com/smartfix/auth/AccountInitializationPostgresIT.java](C:/Users/zhour/smartfix/src/test/java/com/smartfix/auth/AccountInitializationPostgresIT.java)
- [com/smartfix/auth/AuthenticationFlowIT.java](C:/Users/zhour/smartfix/src/test/java/com/smartfix/auth/AuthenticationFlowIT.java)
- [com/smartfix/auth/config/SecurityConfigTest.java](C:/Users/zhour/smartfix/src/test/java/com/smartfix/auth/config/SecurityConfigTest.java)
- [com/smartfix/auth/RegistrationConcurrencyPostgresIT.java](C:/Users/zhour/smartfix/src/test/java/com/smartfix/auth/RegistrationConcurrencyPostgresIT.java)
- [com/smartfix/auth/RegistrationFlowIT.java](C:/Users/zhour/smartfix/src/test/java/com/smartfix/auth/RegistrationFlowIT.java)
- [com/smartfix/auth/RegistrationRateLimitIT.java](C:/Users/zhour/smartfix/src/test/java/com/smartfix/auth/RegistrationRateLimitIT.java)
- [com/smartfix/auth/RoleWorkspacesIT.java](C:/Users/zhour/smartfix/src/test/java/com/smartfix/auth/RoleWorkspacesIT.java)
- [com/smartfix/auth/security/ActiveAccountFilterTest.java](C:/Users/zhour/smartfix/src/test/java/com/smartfix/auth/security/ActiveAccountFilterTest.java)
- [com/smartfix/auth/security/SelectedAccountTypeTest.java](C:/Users/zhour/smartfix/src/test/java/com/smartfix/auth/security/SelectedAccountTypeTest.java)
- [com/smartfix/auth/service/RegistrationRateLimiterTest.java](C:/Users/zhour/smartfix/src/test/java/com/smartfix/auth/service/RegistrationRateLimiterTest.java)
- [com/smartfix/auth/service/SmartFixUserDetailsServiceTest.java](C:/Users/zhour/smartfix/src/test/java/com/smartfix/auth/service/SmartFixUserDetailsServiceTest.java)
- [com/smartfix/common/exception/GlobalExceptionHandlerTest.java](C:/Users/zhour/smartfix/src/test/java/com/smartfix/common/exception/GlobalExceptionHandlerTest.java)
- [com/smartfix/common/LegacyMigrationRecoveryPostgresIT.java](C:/Users/zhour/smartfix/src/test/java/com/smartfix/common/LegacyMigrationRecoveryPostgresIT.java)
- [com/smartfix/common/MigrationIT.java](C:/Users/zhour/smartfix/src/test/java/com/smartfix/common/MigrationIT.java)
- [com/smartfix/common/web/HomeControllerTests.java](C:/Users/zhour/smartfix/src/test/java/com/smartfix/common/web/HomeControllerTests.java)
- [com/smartfix/community/CommunityAnswerConcurrencyPostgresIT.java](C:/Users/zhour/smartfix/src/test/java/com/smartfix/community/CommunityAnswerConcurrencyPostgresIT.java)
- [com/smartfix/community/CommunityMigrationPostgresIT.java](C:/Users/zhour/smartfix/src/test/java/com/smartfix/community/CommunityMigrationPostgresIT.java)
- [com/smartfix/community/CommunityModerationPostgresIT.java](C:/Users/zhour/smartfix/src/test/java/com/smartfix/community/CommunityModerationPostgresIT.java)
- [com/smartfix/community/domain/CommunityQuestionTest.java](C:/Users/zhour/smartfix/src/test/java/com/smartfix/community/domain/CommunityQuestionTest.java)
- [com/smartfix/community/domain/CommunityReportTest.java](C:/Users/zhour/smartfix/src/test/java/com/smartfix/community/domain/CommunityReportTest.java)
- [com/smartfix/community/repository/CommunityQuestionRepositoryTest.java](C:/Users/zhour/smartfix/src/test/java/com/smartfix/community/repository/CommunityQuestionRepositoryTest.java)
- [com/smartfix/community/service/CommunityAccessGuardTest.java](C:/Users/zhour/smartfix/src/test/java/com/smartfix/community/service/CommunityAccessGuardTest.java)
- [com/smartfix/community/service/CommunityAnswerServiceTest.java](C:/Users/zhour/smartfix/src/test/java/com/smartfix/community/service/CommunityAnswerServiceTest.java)
- [com/smartfix/community/service/CommunityModerationServiceTest.java](C:/Users/zhour/smartfix/src/test/java/com/smartfix/community/service/CommunityModerationServiceTest.java)
- [com/smartfix/community/service/CommunityQueryServiceTest.java](C:/Users/zhour/smartfix/src/test/java/com/smartfix/community/service/CommunityQueryServiceTest.java)
- [com/smartfix/community/service/CommunityQuestionServiceTest.java](C:/Users/zhour/smartfix/src/test/java/com/smartfix/community/service/CommunityQuestionServiceTest.java)
- [com/smartfix/community/web/CommunityDeliveryIT.java](C:/Users/zhour/smartfix/src/test/java/com/smartfix/community/web/CommunityDeliveryIT.java)
- [com/smartfix/community/web/CommunityEmptyBoardIT.java](C:/Users/zhour/smartfix/src/test/java/com/smartfix/community/web/CommunityEmptyBoardIT.java)
- [com/smartfix/community/web/CommunityModerationPagesIT.java](C:/Users/zhour/smartfix/src/test/java/com/smartfix/community/web/CommunityModerationPagesIT.java)
- [com/smartfix/community/web/CommunityModerationQueuePagingIT.java](C:/Users/zhour/smartfix/src/test/java/com/smartfix/community/web/CommunityModerationQueuePagingIT.java)
- [com/smartfix/community/web/CommunityPagesIT.java](C:/Users/zhour/smartfix/src/test/java/com/smartfix/community/web/CommunityPagesIT.java)
- [com/smartfix/dispatch/AssignmentIT.java](C:/Users/zhour/smartfix/src/test/java/com/smartfix/dispatch/AssignmentIT.java)
- [com/smartfix/dispatch/AssignmentMigrationIT.java](C:/Users/zhour/smartfix/src/test/java/com/smartfix/dispatch/AssignmentMigrationIT.java)
- [com/smartfix/dispatch/AssignmentPostgresIT.java](C:/Users/zhour/smartfix/src/test/java/com/smartfix/dispatch/AssignmentPostgresIT.java)
- [com/smartfix/dispatch/DispatchPageIT.java](C:/Users/zhour/smartfix/src/test/java/com/smartfix/dispatch/DispatchPageIT.java)
- [com/smartfix/dispatch/DispatchPagePostgresIT.java](C:/Users/zhour/smartfix/src/test/java/com/smartfix/dispatch/DispatchPagePostgresIT.java)
- [com/smartfix/dispatch/service/RequestAssignmentLookupAdapterTest.java](C:/Users/zhour/smartfix/src/test/java/com/smartfix/dispatch/service/RequestAssignmentLookupAdapterTest.java)
- [com/smartfix/dispatch/service/TechnicianRecommendationServiceTest.java](C:/Users/zhour/smartfix/src/test/java/com/smartfix/dispatch/service/TechnicianRecommendationServiceTest.java)
- [com/smartfix/dispatch/TechnicianAccessIT.java](C:/Users/zhour/smartfix/src/test/java/com/smartfix/dispatch/TechnicianAccessIT.java)
- [com/smartfix/dispatch/TechnicianAccessPostgresIT.java](C:/Users/zhour/smartfix/src/test/java/com/smartfix/dispatch/TechnicianAccessPostgresIT.java)
- [com/smartfix/dispatch/TechnicianRecommendationIT.java](C:/Users/zhour/smartfix/src/test/java/com/smartfix/dispatch/TechnicianRecommendationIT.java)
- [com/smartfix/facility/CampusCatalogueMigrationPostgresIT.java](C:/Users/zhour/smartfix/src/test/java/com/smartfix/facility/CampusCatalogueMigrationPostgresIT.java)
- [com/smartfix/facility/CampusMapIntegrationIT.java](C:/Users/zhour/smartfix/src/test/java/com/smartfix/facility/CampusMapIntegrationIT.java)
- [com/smartfix/facility/CampusMapPostgresIT.java](C:/Users/zhour/smartfix/src/test/java/com/smartfix/facility/CampusMapPostgresIT.java)
- [com/smartfix/facility/controller/FacilityControllerTest.java](C:/Users/zhour/smartfix/src/test/java/com/smartfix/facility/controller/FacilityControllerTest.java)
- [com/smartfix/facility/service/FacilityServiceTest.java](C:/Users/zhour/smartfix/src/test/java/com/smartfix/facility/service/FacilityServiceTest.java)
- [com/smartfix/facility/service/LocationServiceTest.java](C:/Users/zhour/smartfix/src/test/java/com/smartfix/facility/service/LocationServiceTest.java)
- [com/smartfix/notification/controller/NotificationControllerTest.java](C:/Users/zhour/smartfix/src/test/java/com/smartfix/notification/controller/NotificationControllerTest.java)
- [com/smartfix/notification/DeliveryReliabilityPostgresIT.java](C:/Users/zhour/smartfix/src/test/java/com/smartfix/notification/DeliveryReliabilityPostgresIT.java)
- [com/smartfix/notification/listener/NotificationEventListenerTest.java](C:/Users/zhour/smartfix/src/test/java/com/smartfix/notification/listener/NotificationEventListenerTest.java)
- [com/smartfix/notification/listener/NotificationTransactionTest.java](C:/Users/zhour/smartfix/src/test/java/com/smartfix/notification/listener/NotificationTransactionTest.java)
- [com/smartfix/notification/repository/NotificationPersistenceIT.java](C:/Users/zhour/smartfix/src/test/java/com/smartfix/notification/repository/NotificationPersistenceIT.java)
- [com/smartfix/notification/service/DeliveryWorkerTest.java](C:/Users/zhour/smartfix/src/test/java/com/smartfix/notification/service/DeliveryWorkerTest.java)
- [com/smartfix/notification/service/EmailDeliveryServiceTest.java](C:/Users/zhour/smartfix/src/test/java/com/smartfix/notification/service/EmailDeliveryServiceTest.java)
- [com/smartfix/notification/service/NotificationServiceTest.java](C:/Users/zhour/smartfix/src/test/java/com/smartfix/notification/service/NotificationServiceTest.java)
- [com/smartfix/notification/UserEDeliveryMigrationPostgresIT.java](C:/Users/zhour/smartfix/src/test/java/com/smartfix/notification/UserEDeliveryMigrationPostgresIT.java)
- [com/smartfix/reporting/controller/DashboardControllerTest.java](C:/Users/zhour/smartfix/src/test/java/com/smartfix/reporting/controller/DashboardControllerTest.java)
- [com/smartfix/reporting/controller/ReportControllerTest.java](C:/Users/zhour/smartfix/src/test/java/com/smartfix/reporting/controller/ReportControllerTest.java)
- [com/smartfix/reporting/service/DashboardServiceTest.java](C:/Users/zhour/smartfix/src/test/java/com/smartfix/reporting/service/DashboardServiceTest.java)
- [com/smartfix/reporting/service/OperationalReportServiceTest.java](C:/Users/zhour/smartfix/src/test/java/com/smartfix/reporting/service/OperationalReportServiceTest.java)
- [com/smartfix/reporting/service/ReportExportServiceTest.java](C:/Users/zhour/smartfix/src/test/java/com/smartfix/reporting/service/ReportExportServiceTest.java)
- [com/smartfix/reporting/service/ReportingPeriodServiceTest.java](C:/Users/zhour/smartfix/src/test/java/com/smartfix/reporting/service/ReportingPeriodServiceTest.java)
- [com/smartfix/request/controller/AttachmentControllerTest.java](C:/Users/zhour/smartfix/src/test/java/com/smartfix/request/controller/AttachmentControllerTest.java)
- [com/smartfix/request/controller/RequestQueryControllerTest.java](C:/Users/zhour/smartfix/src/test/java/com/smartfix/request/controller/RequestQueryControllerTest.java)
- [com/smartfix/request/domain/MaintenanceRequestTest.java](C:/Users/zhour/smartfix/src/test/java/com/smartfix/request/domain/MaintenanceRequestTest.java)
- [com/smartfix/request/domain/RequestTransitionTest.java](C:/Users/zhour/smartfix/src/test/java/com/smartfix/request/domain/RequestTransitionTest.java)
- [com/smartfix/request/repository/AttachmentPersistenceIT.java](C:/Users/zhour/smartfix/src/test/java/com/smartfix/request/repository/AttachmentPersistenceIT.java)
- [com/smartfix/request/repository/RequestRepositoryTest.java](C:/Users/zhour/smartfix/src/test/java/com/smartfix/request/repository/RequestRepositoryTest.java)
- [com/smartfix/request/RequestMigrationIT.java](C:/Users/zhour/smartfix/src/test/java/com/smartfix/request/RequestMigrationIT.java)
- [com/smartfix/request/RequestWorkflowPostgresIT.java](C:/Users/zhour/smartfix/src/test/java/com/smartfix/request/RequestWorkflowPostgresIT.java)
- [com/smartfix/request/RequestWorkflowTest.java](C:/Users/zhour/smartfix/src/test/java/com/smartfix/request/RequestWorkflowTest.java)
- [com/smartfix/request/service/AttachmentInspectionServiceTest.java](C:/Users/zhour/smartfix/src/test/java/com/smartfix/request/service/AttachmentInspectionServiceTest.java)
- [com/smartfix/request/service/AttachmentServiceTest.java](C:/Users/zhour/smartfix/src/test/java/com/smartfix/request/service/AttachmentServiceTest.java)
- [com/smartfix/request/service/RequestAccessServiceTest.java](C:/Users/zhour/smartfix/src/test/java/com/smartfix/request/service/RequestAccessServiceTest.java)
- [com/smartfix/request/service/RequestAssignmentAccessServiceTest.java](C:/Users/zhour/smartfix/src/test/java/com/smartfix/request/service/RequestAssignmentAccessServiceTest.java)
- [com/smartfix/request/service/RequestQueryServiceTest.java](C:/Users/zhour/smartfix/src/test/java/com/smartfix/request/service/RequestQueryServiceTest.java)
- [com/smartfix/request/service/RequestSubmissionServiceTest.java](C:/Users/zhour/smartfix/src/test/java/com/smartfix/request/service/RequestSubmissionServiceTest.java)
- [com/smartfix/request/service/RequestTicketNumberGeneratorTest.java](C:/Users/zhour/smartfix/src/test/java/com/smartfix/request/service/RequestTicketNumberGeneratorTest.java)
- [com/smartfix/request/storage/LocalAttachmentStorageServiceTest.java](C:/Users/zhour/smartfix/src/test/java/com/smartfix/request/storage/LocalAttachmentStorageServiceTest.java)
- [com/smartfix/request/validation/AttachmentValidatorTest.java](C:/Users/zhour/smartfix/src/test/java/com/smartfix/request/validation/AttachmentValidatorTest.java)
- [com/smartfix/request/web/RequestPagesRenderingIT.java](C:/Users/zhour/smartfix/src/test/java/com/smartfix/request/web/RequestPagesRenderingIT.java)
- [com/smartfix/sla/domain/SlaStateTest.java](C:/Users/zhour/smartfix/src/test/java/com/smartfix/sla/domain/SlaStateTest.java)
- [com/smartfix/SmartFixApplicationTests.java](C:/Users/zhour/smartfix/src/test/java/com/smartfix/SmartFixApplicationTests.java)
- [com/smartfix/technician/repository/TechnicianProfileRepositoryTest.java](C:/Users/zhour/smartfix/src/test/java/com/smartfix/technician/repository/TechnicianProfileRepositoryTest.java)
- [com/smartfix/technician/service/TechnicianDirectoryServiceTest.java](C:/Users/zhour/smartfix/src/test/java/com/smartfix/technician/service/TechnicianDirectoryServiceTest.java)
- [com/smartfix/technician/service/TechnicianWorkloadServiceTest.java](C:/Users/zhour/smartfix/src/test/java/com/smartfix/technician/service/TechnicianWorkloadServiceTest.java)
- [com/smartfix/technician/TechnicianProfileIT.java](C:/Users/zhour/smartfix/src/test/java/com/smartfix/technician/TechnicianProfileIT.java)
- [com/smartfix/user/controller/UserManagementControllerTest.java](C:/Users/zhour/smartfix/src/test/java/com/smartfix/user/controller/UserManagementControllerTest.java)
- [com/smartfix/user/service/UserBootstrapServiceTest.java](C:/Users/zhour/smartfix/src/test/java/com/smartfix/user/service/UserBootstrapServiceTest.java)
- [com/smartfix/user/service/UserServiceTest.java](C:/Users/zhour/smartfix/src/test/java/com/smartfix/user/service/UserServiceTest.java)

## 规模快照

本次读取：267 个主 Java 文件、40 个 HTML 模板、92 个 Java 测试文件、25 个 SQL 迁移文件。这是文件数量，不是测试用例数，也不表示每个保留入口都在主导航展示。
