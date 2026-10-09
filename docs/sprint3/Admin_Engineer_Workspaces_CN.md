# SmartFix 管理员与工程师工作台

本次以当前功能分支实际代码为准：Spring Boot 3.5.4、Java 21、Thymeleaf 服务端页面、会话认证、BCrypt、CSRF、PostgreSQL 和 Flyway。未发现项目或父目录 AGENTS.md。开始时工作区干净；本次不提交、不推送。

## 现状与设计依据

- 依据 README、架构/模块/UI/数据库指南，以及 Sprint 3 的 B 派单与工程师、C 报修生命周期、D 页面、E 通知审计契约实现。
- 角色沿用 REQUESTER、TECHNICIAN、ADMINISTRATOR；界面称 Engineer，数据库仍为 TECHNICIAN。使用同一登录系统，不创建第二套身份。
- 原分支已有用户、设施、工单、维修记录和请求确认能力，但缺少主分支已实现的 technician/dispatch 模块。复用仓库 origin/main 的既有模块和公共接口，补接当前分支；没有合并整个主分支或覆盖其余功能。
- 工单关联 locationId，设施设施表独立；没有捏造 facilityId 关联。类别沿用枚举，没有假分类管理按钮。
- 文档的早期占位页面与实际功能不一致；新增工作台说明更新当前入口。原主分支的旧迁移不直接搬入当前已发布迁移序列。

## 页面和真实操作

管理员登录后，服务器依据持久化角色将 `/`、`/home` 重定向至 `/admin`。概览展示全站、审核/派单、维修管道、待关闭数量及最早提交的工单。导航连接真实用户、工程师、设施、报修、社区审核、审计和地图页面。

- `/admin/requests`：按编号、标题、详情搜索，状态/类别/有效优先级筛选；最新、最早、最近更新排序；保留分页筛选。有效优先级使用管理员审核结果，尚未审核则使用上报值。
- `/requests/{ticket}/review`：审核最终优先级或拒绝，拒绝必须填写原因。
- `/admin/requests/{ticket}/dispatch`：真实候选人、技能、服务范围、可用性及工作量；派单、改派原因、撤回原因和历史记录。使用版本/期望 assignmentId 防止覆盖并发改动。改派与撤回后原工程师立即失去工单及私有图片权限。
- `/admin/users`、`/admin/users/new`：搜索、角色/状态筛选，创建账号，变更角色、启停、重置临时密码。保留最后一个有效管理员保护和旧会话失效机制；影响性操作有确认。
- `/admin/technicians`：工程师账号和真实配置情况，连接创建和账号管理。无配置时明确提示需要工程师填写技能与覆盖范围。
- `/admin/facilities`：复用已有设施列表及状态变更能力；不新增没有模型依据的删除/设施编辑。

工程师登录后进入 `/technician`，旧 `/workorders/mine` 仍可用。只显示当前有效分配，按工单状态、请求有效优先级、创建日期和排序筛选；权限过滤在分页之前，已撤销工单不计入列表总数。

- `/technician/profile`：配置已有模型中的维修技能、服务范围、可用性；无地点、无效选择、过期版本均有反馈。
- `/workorders/{id}`：原报修描述、地点、私有图片、维修记录、耗时与材料；开始维修、添加记录、提交解决说明。
- 不新增随意暂停/关闭接口。遵循已有生命周期：提交 → 审核 → 派单 → 维修 → 已解决 → 用户确认 → 管理员关闭；原有拒绝、取消、重开和改派规则保持。

页面复用共享布局、CSS、已有 PNG、状态徽章和服务端错误页。新增渐进增强包含等待反馈、防重复提交、确认、文本计数和错误定位；无 JavaScript 仍能提交服务端表单。空列表、验证失败、操作冲突、权限不足均由实际服务/页面反馈。

## 账号创建与密码

普通注册继续由后端固定 REQUESTER/ACTIVE，绑定白名单忽略 role、isAdmin、authority 等额外字段；不存在管理员或工程师公开注册入口。

管理员在用户页创建工程师，或者将普通账号转换为 TECHNICIAN。新建的受管账号与管理员重置密码后的账号标记必须更换密码。临时密码只在请求期间存在，数据库仅存 BCrypt；页面不回填、日志不打印密码。重置必须验证当前管理员密码，不能借此重置自己的密码。

账号使用临时密码登录后，仅允许密码设置、退出和必要静态资源：业务 GET 重定向 `/account/password`，业务写请求直接 403。改密码需当前密码、新密码和确认；沿用至少 12 字符、字母数字、最多 72 UTF-8 字节的密码策略，并禁止沿用当前密码。完成后退出登录并使已有会话失效，需用新密码登录。

首次管理员沿用受控部署初始化开关，不设置默认密码：

- `SMARTFIX_BOOTSTRAP_ADMIN_ENABLED=true`
- `SMARTFIX_BOOTSTRAP_ADMIN_USERNAME`：部署者选定账号名。
- `SMARTFIX_BOOTSTRAP_ADMIN_PASSWORD`：通过部署秘密配置提供，满足密码策略；不可提交到仓库。
- `SMARTFIX_BOOTSTRAP_ADMIN_DISPLAY_NAME`：可选。

数据库唯一初始化标记在事务中原子认领；并发启动、重启或改变配置账号名都不能再次初始化。已有管理员的数据库升级时关闭初始化；失败创建会回滚认领。首次成功后关闭初始化开关并移除启动环境中的初始秘密。之后通过管理员页面维护账号。

## 接口、权限和数据库

新增 GET `/admin`、`/admin/technicians`、`/technician`、GET/POST `/account/password` 和 POST `/admin/users/{id}/password`；接入原主分支的工程师配置及管理员 assign/reassign/withdraw 路由。其余接口保持现有请求和工单控制器风格，未增加平行 JSON API。

SecurityConfig 检查后端角色；账号管理服务再次验证数据库中 ACTIVE 管理员及密码设置状态；请求、附件、工单服务检查资源权限和当前有效分配。前端隐藏按钮不承担授权。账号创建、角色变更、启停及管理员密码重置使用同事务 USER 审计；不记录凭据。

- 新 V23：将主分支既有 technician/dispatch 表、索引和约束接入当前分支。保留已预置的同模型工程师数据，验证 active assignment 唯一性和历史记录。
- 新 V24：password_change_required、一次性初始化标记、USER 审计目标。
- 未修改已发布迁移、未关闭迁移校验、未删除历史、未设置迁移忽略。生产仍 Flyway 建表、Hibernate validate。

当前分支缺少历史 V10/V11；此交付通过后续 V23 接入，不往已有部署插入低版本迁移。未来整合其他分支时仍须核对所有历史版本、表结构和通知迁移的重复定义，不应未经核对整体复制迁移目录。

## 最小假设和范围

没有邮箱邀请/密码找回基础设施，因此采用管理员经安全渠道交付临时密码、首次强制设置的最小方案，不虚构邮件发送能力。普通账号转为工程师仍使用其原密码；角色变更会撤销旧会话。

不自动推断技能和服务范围，工程师先配置后才进入派单候选。列表日期边界使用明确标注的 UTC，展示时间用 Asia/Singapore。新后端字段仅为安全密码流程和初始化防重所必需。

## 验证记录

2026-10-09 验证完成：

- `mvn -o -B verify`：BUILD SUCCESS；432 个单元/服务测试和 259 个默认集成测试，总计 691，失败/错误/跳过均为 0。日志：`target/role-workspaces-verify-success.log`。
- 最终 PostgreSQL 回归 `mvn -o -B -Ppostgres-it "-Dtest=UserBootstrapServiceTest,RequestWorkflowTest" "-Dit.test=AccountInitializationPostgresIT,AssignmentMigrationIT,AssignmentPostgresIT,DispatchPagePostgresIT,TechnicianAccessPostgresIT,MigrationIT,RequestWorkflowPostgresIT" verify`：BUILD SUCCESS，41 个相关基础回归及 89 个原生数据库测试全部通过，无跳过。日志：`target/role-workspaces-postgres-final.log`。
- 原生测试验证 V22 → V23/V24 升级保留已有工程师配置、活动派单唯一约束、改派/撤回即时撤权、私有照片权限、完整工单状态规则、并发初始化只能产生一个管理员、初始化失败回滚、改配置不能再次初始化。数据库为本机专用 `_test` 库，各测试使用随机独立 schema，结束删除自己的 schema。
- 新增 `RoleWorkspacesIT` 检查管理入口拒绝普通用户/工程师、管理服务拒绝伪造操作人、按服务器角色进入工作台、受管账号强制改密码及业务写请求禁止、重置验证管理员密码、搜索转义 `%` 和实际审核优先级。既有注册测试继续验证客户端提权字段不会改变 REQUESTER。
- `node tools/verify-role-workspaces-browser.mjs`：真实 Edge + 独立 `smartfix_role_acceptance_test` 库，98 项检查全部通过。使用 Tab、Enter、Space、ArrowDown 走完创建工程师、临时密码登录、改密码重新登录、选择技能/覆盖/可用性、用户提交、管理员审核/派单、工程师记录维修/提交解决、用户确认、管理员关闭。临时测试凭据为脚本的合成数据，不是应用默认密码。
- `ROLE_ACCEPTANCE_LAYOUT_ONLY=true` 下同一脚本再次复核最后的导航图标/卡片对齐调整，32 项检查全部通过。宽度 1440、1000、768、390、320；检查无整页横向溢出、无重复 HTML ID、单个 h1，并人工查看桌面/手机截图。
- `node --check` 检查 `workspace.js` 和浏览器脚本；`git diff --check` 通过。无前端框架或额外 npm 构建步骤。

浏览器脚本需要设置 `ROLE_ACCEPTANCE_DB_URL` 指向专用本地测试库，以及测试数据库凭据；拒绝其他数据库名。应先执行 Maven 构建。可选 `ROLE_ACCEPTANCE_JAR` 指定生成的应用 jar。页面验收读取当前源码模板/静态资源，后端使用构建出的真实应用；每次运行使用唯一测试用户。浏览器和应用进程在脚本 finally 中清理。

证据：[完整流程结果](evidence/role-workspaces/results.json)、[最终布局结果](evidence/role-workspaces/results-layout.json)、[管理员桌面](evidence/role-workspaces/admin-overview-1440.png)、[工程师手机](evidence/role-workspaces/engineer-assigned-390.png)、[派单](evidence/role-workspaces/dispatch-1440.png)、[维修记录](evidence/role-workspaces/work-order-recorded-1440.png)。

过程中的失败已定位并修正：旧测试将首页固定为 200 而新角色首页会重定向；H2 用户夹具需密码标记默认值；旧 PostgreSQL 夹具直接传 Instant、没有清理已真实落库的通知；共享脚本使原先“页面不得有任何 script”的断言失效，改为检查用户注入的 script 被转义。没有通过关闭校验、跳过业务测试或修改历史迁移规避错误。

H2 夹具仅用于隔离测试；生产迁移和并发规则用真实 PostgreSQL 验证。尚未进行物理手机、读屏器或完整 WCAG 审核，不将浏览器宽度检查等同于这些验收。本次没有提交或推送；原有用户数据和运行实例未修改。
