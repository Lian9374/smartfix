# 用户端主页面宽度与首页风格统一（2026-10-16）

分支 `feature/requester-experience`，本轮未提交/推送，既有本地改动保留。

用户明确要求保留气泡，以已认可的首页为基准。本轮取代社区第三轮的无气泡/无卡片视觉方向。

## 实现
- 新增 `requester-sections.css`，仅在服务器导航角色为 REQUESTER 时加载，位于各局部样式之后。
- Community、My Requests、Campus Map三个主页面统一采用首页的 `--content-max`、`--content-pad` 和同一个最大宽度公式，不再使用社区1400px/报修1120px的独立上限。
- 为长短页面预留相同滚动条空间，修复短报修列表相对首页偏移约8px的问题。首页原有内容/轮播不改动。
- 三页采用首页同色深绿 #204f47、奶油金 #efd18b、柔和弧面与24px圆角。主页面标题36～44px自适应，手机缩小。
- 社区搜索和侧栏恢复独立白色卡片，列表保持一层白色面板，不在帖子外重复套框。回答恢复浅绿底、细边框和柔和圆角气泡，已采纳状态仍有文本提示。
- My Requests维持真实表格、筛选、状态和新建操作；手机标题及按钮按首页方向左对齐。
- Campus Map保留真实地图、搜索/区域切换/状态刷新，只调整外观。
- 细节表单/长文仍采用较窄阅读宽度，统一的是四个导航主页面，避免表单与回答文本过长。

## 实际验证
- 离线 Maven：`test-compile -Dit.test=CommunityPagesIT#refreshedCommunityPagesRetainRealNavigationAndCanBeCaptured,RequestPagesRenderingIT,CampusMapIntegrationIT failsafe:integration-test failsafe:verify`：37项通过。
- 浏览器实测四页在1920/1440/390px下的标题区左右坐标完全一致（12组页面宽度检查），无横向溢出。
- 1920px下四页左右坐标232.333/1672.333；1440px下112.333/1312.333；390px下16/358.667（当前浏览器滚动条与像素缩放配置）。
- 实测回答气泡背景rgb(238,244,233)、圆角4px/17px，无横向溢出。
- 证据 `target/requester-sections-preview/width-checks.json`、`answer-bubble.png`；测试日志`target/requester-sections-tests.log`。
- 本轮无数据库、认证或接口改动，没有创建/修改问答或工单业务记录。

## 讨论行间距微调
根据截图减少视觉拥挤：作者与日期两端对齐（移除多余圆点），标题/正文/底部信息增加留白；回答数在左，分类与状态在右，分类去色块、状态改轻量圆点文字。保留回答气泡和首页统一主题。
实测1440/768/390px无横向溢出，底部留白24/24/20px；未改业务行为。本轮仅执行资源复制、浏览器布局检查与diff检查，没有重复全量业务测试。

## 讨论内容的柔和分区
标题、正文和底部操作加入单个 `discussion__content` 浅绿阅读气泡，使用细侧线与内部横向分隔线，回答入口以小白底突出、分类使用淡金底。作者和日期位于气泡外，减少大面积留白同时保持信息层次。
本轮真实浏览器1440/768/390/320px无横向溢出；每条帖子只有一个内容气泡，底部操作位于其中。资源复制成功，未重复运行全量业务测试。预览位于 `target/requester-sections-preview/discussion-content-bubble.png`。

## My Requests细化（移除标题装饰）
删除标题上方的装饰图标；标题区由245px收紧为200px最小高度（手机自适应），保持与首页一致的宽度和深绿配色。筛选改为44px高的紧凑横排控件，手机标签和选择框同行；表头、行距、标题/地点/日期的字重及颜色重新分层，增加悬停/键盘焦点反馈。日期使用统一数字格式以避免英文页面混入中文月份。
本轮现有RequestPagesRenderingIT共13项通过；1920/1440/768/390/320px浏览器检查无横向溢出、装饰已移除、筛选控件高44px。离线打包成功、diff检查通过；没有修改工单数据、接口与筛选规则。
证据：`target/request-list-refinement-tests.log`、`target/request-list-refinement-build.log`、`target/requester-sections-preview/my-requests-refined.png`。

## My Requests横幅构图统一
New request操作放入标题说明下方，右侧接入已有透明PNG `request-camera.png`（相机、照片与定位主题），不复用首页建筑或社区聊天图片。横幅使用左右网格布局，手机保留小型右侧插图；首页同款绿色/金色按钮、24px圆角及内容宽度保持一致。移除的是之前标题上方的小装饰图标。
本轮针对性模板渲染测试1项通过（同时渲染新建、列表、筛选、空态和错误页）；资源复制成功。当前浏览器账号会话回到登录页，隔离预览服务器未能被浏览器连接，因此未将本轮布局描述为浏览器实测通过。
