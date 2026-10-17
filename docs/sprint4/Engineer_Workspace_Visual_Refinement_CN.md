# 工程师 Workspace 精简与视觉更新

日期：2026-10-17。新分支 feature/engineer-workspace-refresh，基于管理员分支提交 9da2d1a 创建；开始时工作区干净。本轮不提交、不推送，不更改账号或真实工单。

## 页面安排

- 工程师主导航保留 Workspace 与 Repair history 两个核心入口。Community、Campus Map、Skills & availability 从该角色主导航移除。密码、通知和退出保留；可选技能资料通过标题区低优先级 Profile settings 进入，不再作为没有工单时的主要操作。
- Workspace 使用统一深绿标题区及新工具包 PNG，放大标题和正文、统一内容宽度与左右边缘。没有添加演示数字或无业务能力的模块。
- 筛选与工单归入同一内容卡片。状态、优先级、排序常驻，创建日期放入可键盘展开的 More filters。沿用原 GET /technician、字段和 UTC 日期语义；选定日期时自动展开，分页保留筛选参数。
- 任务行突出编号、标题、地点、更新时间、真实请求阶段及优先级，整行进入原 /workorders/{id}。CREATED 的筛选文案显示 Ready to start，枚举值和状态机不变。
- 区分无分配任务与筛选无结果；移除重复的 Optional profile 引导和没有翻页意义的分页栏。管理员指派、工程师维修记录/完成、用户验收和管理员最终审核流程保持原实现。
- 动效限于插画一次入场、轻微悬停以及任务行反馈；尊重 reduced-motion。导航移动端开合、原生筛选和 details 无需新增脚本。

角色路由的实际权限仍由后端原有规则负责。本轮精简的是工程师入口，未删除其他角色使用的社区、地图模块或既有资料接口。

## 图片

项目文件 src/main/resources/static/images/engineer-workspace.png，由内置 imagegen 生成并复制到仓库。最终提示词：Premium compact 3D matte ceramic clay illustration for a campus engineer's personal repair workspace. A forest-green open technician tool bag with a silver wrench and warm gold screwdriver, an ivory work-order clipboard with three green lines and a small gold checkmark beside it. No buildings and no map. Sculpted soft rounded objects, calm professional product design, deep forest green, muted sage, ivory and restrained golden accents. Three-quarter front view, soft studio light, subtle contact shadows. Isolated genuinely transparent background, no backdrop, no text, no letters, no logos. Centered compact composition with generous margins, crisp readable at 200px width. 图片空 alt、纯装饰，未替换管理员及普通用户的插画。

## 实际验证

离线运行 test-compile、TechnicianAccessIT 和 RoleWorkspacesIT：22 项通过，0 失败/错误/跳过，日志 target/engineer-workspace-tests.log。扩展既有访问测试：当前工程师真实页面包含自己的工单，另一工程师的空列表不泄露它；工程师导航不再包含社区、地图入口；保留优先级和日期字段，已完成筛选正确呈现无匹配状态。原越权、改派撤权、附件权限及账号验证继续通过。

本地资源复制、jar:jar 及 Spring Boot repackage 成功，日志 target/engineer-workspace-package.log。当前运行的开发服务对新 CSS 与 PNG 均返回 HTTP 200。本轮仅更新页面资源，未停止当前 Java 进程，保留现有登录会话。git diff --check 通过。

浏览器对隔离测试真实渲染的 HTML 做只读检查：1440 宽度标题与任务卡片 left 均为 24px；390/320 宽度无整页横向溢出；单个 h1、无重复 ID；More filters 的 Enter 展开正常；手机 Menu 可打开；后续补充的维修记录与 Workspace 共同构成两个主入口。未向正式数据库添加或分配演示工单，未宣称正式账号浏览器完整验收。

证据：[宽屏](evidence/engineer-workspace/desktop.png)、[手机任务列表](evidence/engineer-workspace/mobile.png)、[无任务](evidence/engineer-workspace/empty-desktop.png)。截图均为隔离测试数据。


## 维修记录入口补充

根据用户后续要求，增加 Repair history，与 Workspace 复用完整布局、字体、插画、筛选和任务卡片。未新增无真实能力的导航模块；个人资料仍为次要入口，通知与密码复用既有账号区域。

GET /technician?view=HISTORY（兼容同一控制器的 /workorders/mine）使用 WorkOrderQueueView 只读视图枚举。服务在数据库分页前增加 COMPLETED/CLOSED 状态集合限制，仍先核查工程师账号并按当前有效分配过滤；既有队列重载默认 WORKSPACE 保持兼容。历史页仅提供完成/关闭状态筛选，并在提交、清空与翻页时保留 view。用户验收前完成的维修也可查看，但徽章明确显示真实请求阶段，不能把它当作已批准关闭。

这不是新的状态机、永久访问授权或数据库存档；改派和撤回仍会撤销旧工程师的访问。打开历史工单沿用原详情权限与状态校验。无 SQL 迁移及认证设计更改。

新增验证扩展在既有 TechnicianAccessIT：已完成维修可在历史页读取、另一工程师的历史为空、已关闭工单纳入查询、视图字段保留；RoleWorkspacesIT 一并回归。target/engineer-history-tests.log 中 22 项全部通过，0 失败/错误/跳过。之前“开发资源更新无需重启”的记录只适用于最初纯页面美化；本次新增了后端查询，因此完成打包后重启本机服务加载新类。

维修记录补充完成后，离线本地打包成功（target/engineer-history-package.log）；当前服务已加载新后端，8080 登录页返回 200，重新登录后查看。浏览器只读检查 1440、390、320 宽度、历史页导航高亮、隐藏 view 字段及控件 ID，未见横向溢出或重复 ID。历史页截图：[桌面](evidence/engineer-workspace/history-desktop.png)、[手机](evidence/engineer-workspace/history-mobile.png)。没有提交或推送。
