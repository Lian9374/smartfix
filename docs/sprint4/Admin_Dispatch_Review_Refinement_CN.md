# 管理员工作台与派单验收流程整改

日期：2026-10-16。分支：`feature/admin-dispatch-review-flow`，基于 `feature/requester-experience` 的 `e1df7c8` 创建；开始时工作区干净。本次不提交、不推送。后续按用户明确确认，在本机创建三个工程师账号；没有改动既有请求或分配任务。

## 核心入口

管理员侧栏保留五项：Overview、Requests、Accounts & engineers、Facilities、Reported Content。

- 请求工作台 `/admin/requests` 合并日常查单、搜索、类别/优先级筛选、排序、分页、派单和最终审核入口。旧 `/admin/requests/lookup` 保留以兼容书签，页面正常操作不再依赖它。
- `/admin/users` 和 `/admin/technicians` 归入同一账号区域，分别提供账号权限与工程师派单准备情况；通过区域页签切换，复用现有创建、强制设置密码、启停和角色保护。
- 管理员社区入口仅保留举报处理、已处理记录、隐藏/恢复及审计。一般社区、地图、通知投递不再占据侧栏。既有后端接口及其他角色页面不删除。
- 概览集中展示待派单和待最终审核的真实请求。审计和 SLA 移至折叠的 Operational records；请求页仍可进入维护时限页。每项阶段统计连接同一组状态的查询，修复过去“统计多个状态、点进去只筛一个状态”的差异。

## 正常流程

1. 用户提交请求，进入 **To assign**。
2. 管理员打开 `/admin/requests/{ticket}/dispatch`，查看问题、选定审核优先级和有效的工程师账号；手动指派不受技能、服务范围、个人可用性或档案是否建立的限制。一个提交事务完成优先级审核、活动分配、工单和历史；任一保存失败全部回滚。首次接单无需先跳往独立审核页。
3. 工程师只处理当前有效分配，开始维修、记录诊断/工作/耗时，提交解决说明。进入 **User review**，此时管理员不能关闭。
4. 该请求的用户查看解决说明，接受后进入 **Final review**。用户不接受时可沿用重新维修入口。
5. 管理员查看解决说明和历史后，点击 Approve & close request 完成最终审核，进入 **Finished**。批准动作有确认和成功反馈，历史记录真实操作人及批准说明。

最终审核不通过时，管理员填写 1–500 字符原因，通过新增 POST `/requests/{ticket}/return-for-repair` 退回维修。服务与路由均检查管理员身份；仅 CONFIRMED 可以退回，原因进入原有状态历史。原工单和当前分配保留，工程师可继续维修，管理员也可按原规则改派。旧确认、解决和关闭时间清空；再次维修完成后必须重新经过用户接受，不能直接关闭。

改派/撤回继续使用当前分配标识、乐观锁、唯一约束及即时撤权；有效 TECHNICIAN 账号均可供手动选择；停用、缺失或其他角色的账号仍不可被分配。工程师提交完成不等于整张请求关闭。

## 五类工作阶段

RequestStage 为管理页面的五类业务队列，查询在数据库分页前按状态集合过滤：

- **To assign**：SUBMITTED、UNDER_REVIEW。
- **Repair**：ASSIGNED、IN_PROGRESS、REOPENED。重新维修沿用当前分配，所以归入维修阶段。
- **User review**：RESOLVED。
- **Final review**：CONFIRMED。
- **Finished**：CLOSED、REJECTED、CANCELLED，徽章保留不同结局，避免把取消说成维修成功。

保留已有数据库状态及细粒度历史，不改写历史数据，不新增或修改 Flyway 迁移。共享请求徽章使用清楚的动作名称，工程师列表和详情也显示请求所处验收阶段。新建状态分组不增加第二套数据库状态机。

## 依据与最小设计选择

依据 README、架构文档、管理员/工程师工作台说明、Sprint 4 UserB 派单与 UserC 生命周期交接，以及当前 RequestTransition、AssignmentService、WorkOrderService 实现；既有规则已规定工程师解决、用户确认和管理员关闭的顺序。本次主要补齐直达操作、原子审核派单、阶段查询和界面表达。

用户本次明确要求精简管理员入口及顺序验收。为让“最终审核”能够表达不通过，采用填写原因后退回现有 REOPENED 的最小扩展；管理员审核退回不使用针对用户的重开时限。没有增加自动验收、自动关闭、管理员代替用户确认、任意工程师处理或新的角色。一般社区的既有后端角色规则未调整，移除导航入口不等同于新增访问禁令。

## 验证与限制

相关测试覆盖原子派单及失败回滚、真实工单生成、改派/撤回、当前工程师权限、用户确认前拒绝关闭、管理员不能代用户确认、最终退回必填原因/CSRF/角色保护、再次维修后的重新验收、五组筛选与分页、精简导航及真实模板渲染。

本轮使用隔离 H2，未配置 TEST_DB_URL / TEST_DB_USERNAME / TEST_DB_PASSWORD，因此未宣称重新完成 PostgreSQL 专项验收。没有变更 SQL、关闭 Flyway 或运行任何生产库修复。

浏览器使用隔离测试实际渲染的 HTML 做只读预览，检查概览、队列、首次派单、最终审核的 1440、1000、390 像素布局：每页一个 h1、没有重复 ID、没有整页横向溢出；键盘展开退回表单并验证实时计数。提交和权限链路由真实控制器/服务/隔离数据库测试验证，不将静态预览称为正式账号浏览器全流程验收。

证据在 [页面检查记录](evidence/admin-dispatch-review/layout-checks.json)、[管理员首页](evidence/admin-dispatch-review/overview-desktop.png)、[手机首页](evidence/admin-dispatch-review/overview-mobile.png)、[最终审核](evidence/admin-dispatch-review/final-review-desktop.png)。测试与构建结果收于本地 `target/admin-workflow-final-tests.log`、`target/admin-workflow-build.log`；中间失败记录未抹除。

过程中修复了阶段统计 EnumMap 在模板求值时的类型转换错误；更新了已被统一工作台替代的旧查单导航断言；空列表测试改用专用空账号，消除与另一测试给 Bob 建立请求之间的顺序依赖。

运行包更新后，已启动的旧 Java 进程仍需重启才会加载新后端。后续为使最新派单逻辑生效，将按当前问题修复范围重启本机开发服务；登录会话需要重新建立。

## Overview 视觉细化（同分支）

根据最新截图进一步调整：标题、正文和数字放大；顶部加入新生成的透明 PNG `admin-operations.png`，主色延续深绿、鼠尾草绿与暖金。插画仅作装饰，提供空 alt，并具有一次性入场与轻微悬停反馈；减少动态偏好下停止动画。

五个真实阶段使用独立圆角卡片、含义匹配的图标与明确数字，保留原有查询链接。待派单/最终审核采用宽窄两列，上下边缘对齐；手机单列。每个列表显示最早三条，完整列表由 View all 进入。缩短说明、增大行距，长标题最多显示两行；空状态改为简洁的完成标记和提示，不新增演示数据、额外统计或无后端功能。

实际验证：`target/admin-overview-visual-build.log` 中两项真实集成测试通过（阶段查询/概览渲染、角色工作台空状态），离线编译、打包及 Spring Boot repackage 成功；`git diff --check` 通过。只读浏览器预览检查 1440、1000、390、320 宽度，PNG 正常加载、每个阶段恰有一个图标、单个 h1、没有重复 ID 或横向溢出；桌面两个内容卡片上下边缘一致。

证据：[桌面预览](evidence/admin-overview-visual/desktop.png)、[手机预览](evidence/admin-overview-visual/mobile.png)、[布局检查](evidence/admin-overview-visual/layout-checks.json)。截图为隔离测试数据，未改动业务请求。本轮本地运行包已更新，未重启用户已有服务，未提交或推送。


## 手动派单、工程师账号与详情页收尾

用户明确要求管理员直接派单，因此手动派单名单由有效 TECHNICIAN 账号生成，初始分配、改派、提交时复验采用相同账号规则。技能匹配推荐接口仍独立保留，不作为手动派单门槛。权限、状态机、并发分配保护、首次改密要求保持有效。

请求队列和派单页采用统一浅绿标题区、新的透明 PNG、问题简报及工程师选择卡片。选择卡片支持键盘焦点、选中反馈；选择后才启用提交按钮，较大名单可按姓名或账号筛选。工作量仅作信息展示，不阻止选择。

本机已通过正式 AccountAdministrationService 创建并验证 engineer01、engineer02、engineer03：均为 ACTIVE/TECHNICIAN，使用 BCrypt 且首次登录必须改密。临时密码仅保存在仓库外的当前用户私有文件，并按用户要求单独交付；文档和源码不记录密码。

本轮排查确认旧 Java 服务启动于 20:39，连接 5433 端口的本机 PostgreSQL，早于新派单逻辑的构建；数据库实际已有 eng1 和上述三个有效工程师。旧进程仍加载先前业务类，尽管开发模板可随文件更新，故可出现新页面配旧名单逻辑。需要更新运行中的服务才能生效，不需要添加技能或修改数据库迁移。

View request 管理员详情页统一标题容器和卡片边缘，放大标题、正文及行距；移除摘要中重复的编号与状态、管理员空照片模块，整理维修信息和主要派单按钮。用户及工程师的既有操作和权限保留。当前阶段、最终审核和退回流程继续使用真实接口。

验证：target/admin-request-detail-tests.log 中 DispatchPageIT 28 项、RequestPagesRenderingIT 13 项全部通过，构建及 repackage 成功；早前 AssignmentIT 28 项、TechnicianAccessIT 11 项和 RoleWorkspacesIT 10 项已通过。浏览器对实际测试模板的只读预览在 1280、390 宽度检查无整页横向溢出、单个 h1；选择反馈和禁用态已检查。截图使用隔离测试数据：[桌面详情](evidence/admin-request-detail/desktop.png)、[手机详情](evidence/admin-request-detail/mobile.png)。未将只读预览声明为正式业务账号完整验收。


本机只读业务服务核验：DispatchPageService.describe("SF-2026-000003", 当前有效管理员 ID) 实际返回 [eng1, engineer01, engineer02, engineer03]，三个新账号均 selectable，记录在 target/live-dispatch-check.log，LIVE_DATABASE_DISPATCH_CHECK=PASS。辅助检查退出时正常关闭，不提交分配。已停止经命令行核实的旧本机开发进程，并以新打包文件、dev 配置及同一 5433 数据库重启本机服务；启动日志为 target/smartfix-local-20261016.out.log / .err.log，进程号保存在 target/smartfix-local.pid。此项解决运行中的业务类与开发模板不同步导致的空名单。
