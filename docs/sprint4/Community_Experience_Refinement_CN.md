# Community 体验调整（2026-10-16）

分支：`feature/requester-experience`。本轮不提交、不推送，保留之前所有未提交修改。

## 实现
- 社区列表改为浅鼠尾草绿的紧凑标题区，替换旧紫色大横幅和 CSS 气泡装饰。
- 新聊天 / 回复 / 铅笔 PNG 仅用于列表标题区，不复用首页建筑插图。
- 桌面讨论标题 23px、摘要 17px，手机标题 20px、摘要 16px；元数据独立层次，正文保持可读行距。
- 搜索、列表和侧栏统一网格；手机显示真实 Topic 选择框，桌面保留真实分类链接。
- 移除侧栏报修推广卡、默认条件下的 Reset 以及个人回答页重复 shown 统计。
- 搜索增加清空、`/` 聚焦、提交等待反馈；不自动提交选择，不发送额外查询。
- 保留服务端搜索 / 过滤 / 分页、作者、回答数、CSRF、内容转义和权限判断。
- 提问、编辑回答、详情、我的问题/回答共享新字体、间距、边框、表单宽度与焦点样式。
- 动画仅短暂悬停反馈；尊重 prefers-reduced-motion。

## 验证
- 离线 Maven：`test-compile -Dit.test=CommunityPagesIT,CommunityEmptyBoardIT -Dsmartfix.community.captureDir=target/community-experience-preview failsafe:integration-test failsafe:verify`，59 项通过。
- 测试使用原有隔离 H2 配置；不修改生产 Flyway 策略，也没有数据库迁移。
- 最后少量标签/日期显示调整后，通过真实 localhost 页面确认，并执行资源复制及打包（BUILD SUCCESS）。
- JavaScript 语法检查和 git diff --check 通过（既有文件仅换行警告）。
- 真实已登录浏览器：搜索无结果、Reset、清空输入、字符计数和提问表单标题/主体边缘对齐验证。
- 列表 1440/1024/768/390/320px，提问/详情/我的问题 1440/768/390/320px，共17组无横向溢出。列表还检查了唯一 ID 和单一 h1。
- 这些页面检查没有发布问题、回答、举报或采纳操作，不改变现有社区业务数据。
- 证据位于 `target/community-experience-preview/`；服务端日志 `target/community-experience-tests.log`；构建日志 `target/community-experience-build.log`。

## 素材
内置 image_gen 工具生成；文件：`src/main/resources/static/images/community-conversation.png`。
提示词：Two overlapping tactile clay chat bubbles, deep forest teal with ivory dots and an ivory reply bubble with a muted gold checkmark, sage pencil, transparent background, compact balanced composition, gentle studio shadows, no words/logo/building/camera. Match SmartFix forest green, ivory and muted gold.

## 范围边界
没有新增社区接口、审核规则或身份逻辑；没有将模拟讨论填入真实账号。视觉更新不等于真机或读屏工具的完整验收。

## 第二轮：连续讨论框架与详情阅读流

- 搜索、讨论标题、帖子列表和分页合进 `community-feed-shell`；内部以浅底色与白色帖子区分层级，统一圆角、边距与分隔线。
- 列表回答数成为真实链接，跳转同一问题的回答区域，没有添加无后端能力的点赞、关注或分享按钮。
- 详情以单个 `community-thread-shell` 包含作者、标题、问题内容、回答和回复框，删除重复的左侧 Question summary。
- 分类 / Open 或 Solved / 作者 / 发布时间仍可见，编辑时间仅在实际编辑时出现；返回入口使用上方面包屑。
- 回答采用作者首字母标识、正文气泡和紧凑操作；采纳回答以文字徽标及浅绿样式区分，原有 POST、确认、CSRF 和资源权限条件均保留。
- 写回答入口为原生页内链接，可通过键盘到达回复标题；回复框默认收紧、获得焦点展开，保留字符计数与服务端错误。
- 继续运行相同两组社区集成测试：59 项通过，测试断言同步新的线程容器。随后对面包屑与标签做小幅显示调整，真实浏览器验证。
- 真实浏览器对列表和详情各检查 1440/1024/768/390/320px，共10组无横向溢出；列表搜索和内容无缝连接，所有帖子位于外框内；详情唯一ID、一个h1、一个外框检查通过。
- 实测回复锚点正确转移焦点，页面内现有4个POST表单均保留CSRF字段。未提交任何真实回复、采纳或举报。
- Maven 离线资源复制、jar打包和Spring Boot重新打包成功，git diff --check通过（既有文件换行提示除外）。
- 新一轮日志及布局证据：`target/community-thread-tests.log`、`target/community-thread-build.log`、`target/community-thread-preview/live-checks.json`。

## 第三轮：以简洁阅读为主（当前最终方案）

用户反馈上一轮卡片嵌套过多。本轮取代第二轮的视觉方案：
- 移除列表外层 card 和每条讨论的内框，搜索不套卡片；仅讨论列表保留一层白色阅读面板，帖子之间使用细分隔线。
- 顶部不再使用大面积彩色横幅，改为紧凑标题与小型PNG点缀；个人记录与分类侧栏去掉独立卡片，以留白和细竖线组织。
- 详情保留一个阅读面板，正文、回答和回复区无嵌套气泡/底色块；采纳提示保留文字徽标和轻量侧线。
- 清除上一轮Connected feed样式，重写该段，保留现有原生链接、服务端筛选和所有POST操作条件。
- 针对性的 `CommunityPagesIT#refreshedCommunityPagesRetainRealNavigationAndCanBeCaptured` 测试通过，覆盖8个实际模板页面渲染；本轮没有重新运行全部59项测试，不将上一轮结果算作本轮全量验证。
- 真实浏览器验证列表五种宽度无溢出，外层/侧栏无card、帖子无圆角内框；详情在1440/1024/768/390/320宽度无溢出，正文无气泡背景，ID唯一。1024与390在等待浏览器完成布局后单独复核。
- 本地资源复制和Spring Boot打包成功；git diff --check通过（既有文件换行提示除外）。
- 证据：`target/community-flat-tests.log`、`target/community-flat-build.log`、`target/community-flat-preview/`。
