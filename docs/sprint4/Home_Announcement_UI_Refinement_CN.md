# 首页公告与视觉细节完善（2026-10-14）

## 实现范围

- 保留已认可的深绿、薄荷、奶油色和校园 PNG。首页插图增加轻微投影；共享字体优先使用本机 Segoe UI Variable，仍保留系统回退，不加载外部字体。
- 标题使用平衡换行；日期、状态和工单号使用等宽数字。社区快捷入口统一最小高度、标题行高和说明间距。
- 首页增加独立公告卡片，复用 `AnnouncementService.listVisible()`，遵循现有发布状态与有效时间规则，最多预览六条。全部内容从真实服务获取，不添加示例公告或数据库种子。
- 原生横向滚动与 CSS scroll-snap，支持触屏滑动、触控板、左右按钮。聚焦公告区域时可用左右键、Home、End；显示当前位置，边界按钮禁用。相邻公告平滑切换，跨页定位和减少动态效果设置即时切换。
- 不自动播放，避免阅读被打断。无 JavaScript 仍可横向滚动并打开详情；零条有空状态，单条不显示多余按钮。
- 公告正文摘要限制两行、标题限制两行，完整内容可通过对应锚点打开。详情保留换行、以新加坡时间显示有效期。标题与正文继续使用 Thymeleaf 转义，禁止把公告当作 HTML 执行。
- 管理员、工程师仍按现有服务器角色规则进入各自工作台；此次首页公告接入面向普通用户 `/`、`/home`，所有角色仍可访问原有 `/announcements`。

## 验证

实际运行的相关 Maven 测试：`HomeControllerTests,AnnouncementControllerTest,AnnouncementServiceTest`，24 项通过。
覆盖真实模板渲染、单条/多条公告、六条预览上限、详情锚点、正文转义、新加坡时间，以及原公告服务与控制器规则。

浏览器使用真实 MockMvc 渲染出的模板、测试服务数据和本地静态资源，在独立 Edge 中运行。
30 项检查通过：按钮、键盘首尾定位、原生横向滚动、减少动态效果、无 JS 降级、唯一 ID、单个 h1，以及 1440/1000/768/390/320px 下首页和公告详情无页面横向溢出。
桌面及手机截图已人工查看。结果与截图输出到忽略目录 `target/ui-polish-preview/`。
最终 `mvn -o -DskipTests package` 构建成功；两个 JavaScript 文件通过 Node 语法检查，`git diff --check` 通过。
这是模板和浏览器交互验收，不代表真实数据库公告发布全链路、真机触屏或读屏验收。未操作正在运行的业务数据库。

复现：

```powershell
mvn -o -Dtest=HomeControllerTests,AnnouncementControllerTest,AnnouncementServiceTest -Dsmartfix.ui.captureDir=target/ui-polish-preview test
node tools/verify-home-announcements-browser.mjs
node --check src/main/resources/static/js/announcement-carousel.js
```

原有数据库结构、迁移、认证和权限设计无需变化。管理员从 `/admin/announcements` 发布有效公告后，普通用户首页按既有可见性规则展示。
