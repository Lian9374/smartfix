# 管理员设施与举报页面整改

日期：2026-10-16；继续使用 feature/admin-dispatch-review-flow。保留之前全部未提交修改，不提交、不推送，不修改迁移，不创建真实示例设备或举报。

## Facilities 保留原因及空列表原因

实际检查本机 PostgreSQL（开发启动脚本使用的 5433/smartfix）：facilities 表为 0 条。地图中的建筑/地点不是具体设备；既有 V12 facilities、FacilityService、FacilityController 和地图状态聚合均已存在，所以不删除模块。此前管理端只能列出设施和修改状态，没有录入入口，空状态无法通过正常页面解决。

新增管理员 POST /admin/facilities，复用 V12 现有字段、LocationService 和 FacilityService。CreateFacilityCommand 校验地点 ID、名称（必填、最多 120 字符）、描述（可选、最多 500 字符）及既有状态；服务再次校验并拒绝不存在或停用地点，实体沿用既有时间戳和字符串标准化。页面直接在 Add facility 展开表单，不新增一套地点或建筑管理。状态仍为 OPERATIONAL / UNDER_MAINTENANCE / OUT_OF_SERVICE，不假定工单结束自动恢复设备状态。

新增接口位于既有 /admin/** 管理员规则下，保留 ActiveAccountFilter、首次改密检查及 CSRF。没有因美化改变角色或认证设计。无新 SQL：现有表已包含全部所需字段。

设施页改为与账号/派单页一致的浅绿标题区、真实新增入口及设备目录。目录突出设备名称、地点、描述和状态，状态更新按需展开并保留确认。状态徽章使用易读名称及语义色。空页面明确引导登记设备，不虚构记录或把建筑目录当成设施。

## Reported content

统一宽度、标题、字体与卡片边距；保留 Waiting / Handled / All reports 和 Audit history。移除重复面包屑和独立的零条目底栏，当前视图总数仅在队列标题显示，真正多页时才提供分页。

每条举报以标题、元信息、原文引用、可见性操作和 Review report 分区。决定表单按需展开，减少每条都堆积单选框和说明；原生 details/summary 支持键盘且无需 JavaScript。隐藏、恢复、决定、分页位置与唯一输入说明 ID 保留，用户内容仍用 th:text 输出。没有把“处理举报”和“隐藏内容”合并成同一业务含义。

新 PNG：src/main/resources/static/images/community-moderation.png。使用内置 imagegen，提示词为：Premium small 3D matte ceramic clay illustration for a campus community moderation dashboard: a deep forest-green shield with an ivory check mark, an ivory speech bubble behind it and a restrained warm golden small flag beside it. Compact centered three-quarter composition. Muted sage, forest green, warm ivory and soft gold, gentle studio illumination, subtle contact shadow. Genuine transparent background, isolated objects, no text no letters no logos. Polished minimal high-quality product UI asset, readable at 170 pixels wide, generous transparent margin. 图片已保存到项目，空 alt、纯装饰，悬停效果尊重减少动态设置。设施页使用已有维修装备插画，与举报图不同。

## 实际验证

离线编译及相关测试：FacilityControllerTest 2 项、FacilityServiceTest 3 项、CommunityModerationPagesIT 22 项、CommunityModerationQueuePagingIT 4 项通过；RoleWorkspacesIT 首次仅新增设施测试的 JDBC 状态断言失败，原因是测试外层事务尚未 flush，加入 entityManager.flush() 模拟正常提交前的刷新后 11 项全部通过。最终设施模板再单独复验 1 项通过。日志：target/admin-stewardship-tests.log、target/admin-stewardship-final.log、target/admin-facility-render-final.log；没有隐藏中间失败。

新流程测试实际创建并验证记录、状态变更，拒绝空名称、无效地点、普通用户/工程师创建及无 CSRF 写操作。举报测试继续覆盖权限、文本转义、隐藏恢复、处理决定、空状态及分页。测试数据均在隔离 H2，不写入用户 PostgreSQL。

浏览器使用测试真实渲染 HTML 的只读预览检查设施目录、新增表单、举报内容、空队列及键盘展开，390/320 窄屏无整页横向溢出，单个 h1、无重复 ID。只读预览不声明为正式业务浏览器全流程验收。

本机资源打包及 Spring Boot repackage 成功（target/admin-stewardship-package.log），已重启本轮本机应用，8080 登录页及新样式均返回 200。仍使用同一 PostgreSQL/dev 配置；未关闭 Flyway。新页面生效需重新登录。

最终截图：[设施目录](evidence/admin-stewardship/facilities-desktop.png)、[设施录入](evidence/admin-stewardship/facility-form-desktop.png)、[手机录入](evidence/admin-stewardship/facilities-form-mobile.png)、[举报队列](evidence/admin-stewardship/reports-desktop.png)、[空举报队列](evidence/admin-stewardship/reports-empty-mobile.png)。截图均为隔离测试数据。
