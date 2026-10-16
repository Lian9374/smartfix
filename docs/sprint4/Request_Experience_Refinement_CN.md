# My Requests 与新建报修页面完善

日期：2026-10-15。分支：`feature/requester-experience`，保留此前登录页、首页、导航修改。

## 页面规划与实现

- 本轮覆盖 `/requests/new` 与 `/requests/mine`。报修详情、管理员审核、工程师工作台与权限模型未改动。
- 新建页删除右侧大型建筑插图和纯说明卡片，采用统一1120px以内的内容容器。页头、三个表单卡片和操作区边缘对齐；桌面位置与标题并排，手机单列。标题27–36px，分组标题20–22px，字段文字16–17px。
- 使用 imagegen 技能生成全新的透明相机／照片／定位标记 PNG，保存为项目内 `static/images/request-camera.png`，仅作为页头小型点缀，不复用校园建筑图片。手机窄屏优先展示字段，不放大装饰图。
- 三段分别是 Problem details、Category & urgency、Add photos。紧急程度帮助放入对应分组，使用原有 Low／Medium／High 说明与原生 details 展开，不增加业务等级。
- 开启 JavaScript 时提供分段锚点和真实的必填完成进度。只统计当前原有五个必填字段，空白文本不算完成，照片仍然可选；关闭 JavaScript 时直接显示完整原生表单。
- 照片区域保留原生选择器，增加拖入体验，沿用既有格式、单文件大小、总量与数量限制；预览可删除，修改选择后重算进度。全部预览在本地进行，未提交前不上传。
- 有效提交时显示 Submitting 并防重复点击；浏览器返回时恢复按钮、填写内容、计数和图片预览。CSRF、multipart字段名、服务端校验与真实工单提交入口保持不变。
- 我的报修页将状态筛选合并到记录卡片，保留原有分页和当前筛选，增加清除筛选入口。分类和筛选选项采用可读文字；原始筛选值与状态标签不变。日期显示新加坡时间。列表、空记录及无匹配状态沿用真实查询结果，没有添加假统计。

## 验证

运行 `RequestPagesRenderingIT`，使用真实控制器、认证、BCrypt、服务、H2测试库和Thymeleaf；验证新建表单、错误回显、工单归属、详情读取、首页、列表和真实状态筛选。
旧测试中固定检查的大写分类及旧首页文案已经同步为当前显示文案，权限和数据隔离断言继续保留。

实际结果：`RequestPagesRenderingIT` **13 项全部通过**；独立浏览器检查 **78 项全部通过**。`request-form.js`、浏览器检查脚本语法检查以及 `git diff --check` 通过。
最终资源已通过 `mvn -o resources:resources jar:jar spring-boot:repackage` 打入本地应用包；确认包含新 CSS 与相机 PNG。照片删除后的进度状态也已补充回归检查。

独立 Edge 使用真实测试渲染的五个页面（新建、有记录列表、空列表、无匹配筛选、服务端校验错误），覆盖1440/1000/768/390/320px、模块边缘、无页面横向溢出、唯一ID、单h1、进度、字符计数、锚点、紧急说明展开、真实File/DataTransfer本地预览与删除、格式/数量/单文件大小限制、提交状态及返回恢复、无JS降级。截图位于忽略目录 `target/request-experience-preview/`，已经人工复核桌面和手机页面。

浏览器检查不提交到真实服务器，不修改业务库，不代表正式 PostgreSQL 迁移或真机触屏、读屏验收。原服务端上传校验仍为最终保障。

复现：

```powershell
mvn -o test-compile -Dit.test=RequestPagesRenderingIT -Dsmartfix.request.captureDir=target/request-experience-preview failsafe:integration-test failsafe:verify
node tools/verify-request-experience-browser.mjs
```

新增专用样式按页面标题与普通用户角色加载。表单脚本使用新的资源版本，避免旧缓存。本轮没有数据库迁移、角色授权变更、自动提交或远程推送。
