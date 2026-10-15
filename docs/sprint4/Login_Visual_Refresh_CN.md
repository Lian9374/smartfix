# 登录页美化（2026-10-15）

分支：`feature/login-visual-refresh-20261015`。创建分支时保留了上轮未提交的首页公告修改；此次新增工作集中在 `/login`，未提交或推送。

## 视觉与交互

- 深绿与暖白双栏调整为 48:52，表单最大宽度 480px。欢迎标题 34–46px，品牌侧标题随窗口缩放，输入框文字 18px，高度至少 58px。
- 复用项目现有透明校园 PNG，替换登录页原有底部线稿。加入淡点阵背景、柔和底衬和两张装饰标签，没有加载外部字体或图片服务。
- 左侧 Report / Track / Connect 按钮切换简短功能介绍，支持键盘，选中状态使用 `aria-pressed`；它们是功能介绍，不是未登录即可报修的业务入口。
- 鼠标在插图区域移动时产生最大约 5px 的轻微位移；页面入场动画只播放一次。减少动态效果时关闭动画与位移，触屏设备不执行鼠标位移效果。
- 手机端保留紧凑品牌区域和小插图，优先展示登录表单；移动端不显示桌面功能介绍按钮。
- 删除 “Contact your administrator” 及 “Technician and administrator accounts are created by an administrator” 两段，底部改为简洁品牌标识和短句。保留真实普通账号注册入口。
- 密码显隐继续复用现有组件，补充 Caps Lock 提示、提交时的等待反馈与防重复点击；浏览器返回时重置按钮状态，并重新遮蔽密码。

## 权限边界

未改变登录 Controller、认证 Provider、角色枚举、数据库迁移或账号创建规则。角色选择仍使用服务端字段和原有三个单选值，POST `/login` 仍由 Spring Security 与 CSRF 保护。
脚本不保存账号密码，不记录输入内容。无 JavaScript 时，登录、角色选择、注册入口与 PNG 仍正常可用。

`SecurityConfigTest` 同步补上此前首页新增的公告服务 mock，避免 MVC 切片因缺少依赖无法启动。

## 验证与查看

- `SecurityConfigTest,SelectedAccountTypeTest,HomeControllerTests` 共 94 项测试通过。
- 独立 Edge 对真实 MockMvc 渲染模板完成 40 项检查：PNG 加载、介绍按钮与键盘、角色字段、密码显隐、Caps Lock、原生必填校验、等待状态与返回恢复、减少动态效果、错误提示、无 JS 降级及窄屏标题与插图互不遮挡。
- `AuthenticationFlowIT` 的 29 项真实认证集成测试通过（真实服务、BCrypt、H2 测试库）；其中一项原先断言必须显示被要求删除的旧说明，已按新需求更新断言。三角色选择、注册入口及权限相关断言继续保留。
- 1920、1440、1024、768、390、320px 下无页面横向溢出、ID 无重复、角色标签未溢出。截图在忽略目录 `target/login-visual-preview/`，已人工复核桌面与手机效果。
- 浏览器检查使用隔离测试页面与合成输入，不代表实际账号登录验收或真机触屏验收。
- 本地 `mvn -o -DskipTests package` 成功；最终模板资源更新后执行 `resources:resources jar:jar spring-boot:repackage` 成功。JavaScript 语法与 `git diff --check` 通过。

复现模板与交互检查：

```powershell
mvn -o -Dtest=SecurityConfigTest,SelectedAccountTypeTest,HomeControllerTests -Dsmartfix.login.captureDir=target/login-visual-preview test
node tools/verify-login-visual-browser.mjs
```

此前修改主要位于登录后的首页，不能据此判断登录页变化。检查时 `localhost:8080` 没有应用响应。正常启动或重启项目后访问 `/login`，新页面会请求 `/css/login.css?v=20261015`，不应继续显示被删除的两段说明。
