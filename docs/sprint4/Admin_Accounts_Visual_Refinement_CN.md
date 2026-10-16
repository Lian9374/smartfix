# 管理员账号区域视觉整理

日期：2026-10-16。继续使用 feature/admin-dispatch-review-flow。保留此前所有未提交修改，本轮不提交或推送，不修改账号数据、密码、角色规则或迁移。

## 调整

- Accounts & access 移除 Create account 导航，创建工程师入口仅保留在 Engineer readiness；既有受保护创建页面及接口继续可用。
- 两个页签共享浅绿标题容器、插画、字号与边距。账号列表由八列密集表格改成资料、角色、访问状态和加入日期对齐的目录，真实筛选、空状态、结果数量保留。
- 每个账号的 Manage access 使用原生 details/summary，默认折叠角色修改、启停和密码重置；支持键盘及无 JavaScript 操作。高影响操作保留原有确认、CSRF、管理员身份检查和最后一个管理员保护。密码表单不回填密码。
- 管理表单按角色、访问状态、密码分区；手机按一列排列。密码首次设置提示保留。减少重复说明，空结果指向调整筛选，而非再次提供创建按钮。
- 新透明图片 src/main/resources/static/images/account-access.png 使用内置 imagegen 生成并复制到仓库，未覆盖已有素材。最终提示词：premium 3D matte clay forest-green blank ID badge with ivory avatar, two gold access keys and an ivory check shield, soft light, compact three-quarter composition, genuinely transparent background, no text or logos。完整生成规格强调 SmartFix 现有绿/象牙白/暖金配色、约 170px 展示和留白。图片只作装饰，空 alt；悬停轻微反馈尊重减少动态设置。

## 验证

实际运行离线 test-compile、RoleWorkspacesIT 和 RequestPagesRenderingIT：23 项通过，无失败或跳过；日志 target/admin-accounts-tests.log。既有测试覆盖未授权角色、账号创建、临时密码强制替换和密码重置校验；列表渲染断言继续确认原写接口、字段、唯一控件 ID 及无密码输出。本轮同时确认列表不包含创建入口，工程师页保留创建入口。

浏览器使用隔离测试真实渲染 HTML 只读检查：1280、390、320 宽度，无整页横向溢出；账号页单个 h1、无重复 ID。键盘 Enter 能展开管理区，展开后窄屏无溢出。没有提交真实账号修改，不将只读预览称为正式账号浏览器完整验收。

证据：[桌面账号页](evidence/admin-accounts/desktop.png)、[手机账号页](evidence/admin-accounts/mobile.png)、[展开操作](evidence/admin-accounts/mobile-expanded.png)。

本地资源打包及 Spring Boot repackage 成功（target/admin-accounts-package.log）；已重启本轮启动的本机 SmartFix 进程，沿用 5433 PostgreSQL / dev 配置，8080 登录页返回 200。更新后需要重新登录。
