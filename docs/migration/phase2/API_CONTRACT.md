# Community 内部 API 契约 v1

状态：设计协议，未实现。机器可读路由映射见 contracts.json；不是已经部署的接口文档。

## 通道与身份

Core→Community 前缀 /internal/community/v1。健康端点只提供必要健康信息；业务端点只在受保护内部路径开放。云端使用 TLS，内部服务身份限制调用者；本地只绑定回环或隔离网络。

采用非对称签名短期凭证，固定允许的签名算法，验证 issuer=smartfix-core、audience=smartfix-community、sub=账号ID、role、securityVersion、passwordChangeRequired=false、iat/nbf/exp，初始有效期 30 秒、最大时钟容差 5 秒。凭证每次 Core 请求在现有 ActiveAccountFilter 验证后签发，不把浏览器 Cookie 共享给 Community。私钥仅 Core 持有，Community 只读公钥；kid 轮换采用重叠公钥窗口，拒绝未知 kid/算法，不按凭证提供的 URL 下载密钥。

Core 的普通用户入口继续 CSRF；内部 API 只接受上述 Bearer 凭证，不凭浏览器 Cookie 授权。角色为 REQUESTER/TECHNICIAN/ADMINISTRATOR。可信身份只决定主体，Community 继续判断作者、管理员、可见性和状态。无凭证401，越权管理员操作403，隐藏或无权访问他人内容404。Core 将浏览器未登录保持原登录跳转，而不把 API401 原样渲染成页面。

## 路由与 DTO

contracts.json 中每个现有社区 HTML 路由都有目标 operation。表单 new 页面不访问数据库；edit-context 返回当前用户可编辑内容，不能用公开详情接口泄露被隐藏的正文。

问题写入字段 title（trim 后3–150）、body（10–4000）、category（HARDWARE/SOFTWARE/NETWORK/PERIPHERAL/OTHER）。回答 body 10–4000。举报 reason 为 SPAM/ABUSIVE/OFF_TOPIC/DUPLICATE/OTHER，detail 最大500。处理举报 decision 仅 ACTIONED/DISMISSED，note 最大500，hideContent 独立布尔值，不能由 decision 推导。忽略或拒绝输入中的 authorId/actorId/handledByUserId/状态和服务器时间，身份只取凭证。

读模型由既有 CommunityQuestionSummaryResponse、CommunityQuestionDetailResponse、CommunityAnswerResponse、MyAnswerResponse、CommunityReportResponse、OwnAnswerResponse 的显示字段映射为独立 JSON DTO；不直接序列化 JPA/Page 实现。

QuestionSummary 包含 id/title/category/authorId/status/solved/acceptedAnswerId/createdAt/updatedAt/edited/answerCount；QuestionDetail 另含 body 与 answers，Answer 包含 id/questionId/authorId/status/body/createdAt/updatedAt/edited/accepted。不得把占位隐藏回答的 null body 变为真实正文。MyAnswer 包含原所属问题与可访问/可编辑标志；Report 包含目标、reporterId、reason/detail/status、处理人和时间/note、原视图必需的内容摘要与恢复条件。edit-context 返回可编辑字段与服务器判定能力；调用者不能用能力标志绕过服务端校验。具体字段与现有模板的逐项同义映射在实现时由消费端契约测试固定；本阶段未认证模板渲染兼容。

所有 ID 为正整数，时间为 UTC ISO8601，空值明确使用 null，集合为空数组。正文输出仍由模板转义，不把用户内容作为 HTML。

## 分页与可见性

页码从0开始，负值归零；size<=0使用各页面现有默认值（社区10、审计20），最大50。返回 items/page/size/totalElements/totalPages/hasNext。枚举大小写与现有枚举一致，未知值400。q 使用现有 trim/大小写和字面搜索语义，不扩展为 SQL 通配符查询。

列表 category/filter/q 保留；filter 默认 LATEST，UNANSWERED=未采纳，SOLVED=已采纳。mine 的 tab 默认 QUESTIONS，可选 ANSWERS。举报 view=OPEN/HANDLED/ALL，默认 OPEN；OPEN 最旧优先，历史最新优先。所有排序都有 id 次级键，问题最新优先、详情回答最旧优先；保留越界分页回到有效页的 HTML 适配行为。

## 写入、错误与超时

写入需 Idempotency-Key（Core 为一次用户提交生成并保留随机唯一键），作用域 actorId+operation+key；同键同摘要返回原结果，同键不同摘要409 KEY_REUSED。在途同键返回409 COMMAND_IN_PROGRESS；只在原事务已回滚或租约安全过期后重试，不能覆盖已成功结果。保留记录至少24小时，超过窗口不盲目自动重放；创建类返回资源ID与Location。

POST /commands/lookup 接收 operation+key，只返回该主体的执行状态和结果；通过 INTERNAL_UNKNOWN 表示未确认结果，不能展示“肯定失败”。重复投递有界重试，禁用未经幂等保护的 POST 自动重试。成功创建201、更新/动作200，无状态改变的合法幂等动作200且不重复发事件。

统一错误体 code/message/fieldErrors/traceId/retryable。400 VALIDATION_FAILED/INVALID_ENUM；401 UNAUTHENTICATED；403 FORBIDDEN；404 CONTENT_NOT_FOUND；409 STATE_CONFLICT/DUPLICATE_CONTENT/POSTING_LIMIT/REPORT_ALREADY_EXISTS/KEY_REUSED/COMMAND_IN_PROGRESS；503 DEPENDENCY_UNAVAILABLE。保持原业务拒绝所用页面行为，发帖业务限额按既有冲突语义，不擅自改成HTTP429。基础设施过载的429须独立标注，不与业务限额混淆。

Core 适配字段错误回原表单、冲突回原提示，保留成功后的重定向及筛选参数。初始连接超时1秒、响应超时3秒、最多一次有条件读取重试；写入按幂等查结果。具体连接池上限由阶段3资源预算配置，必须有界并隔离社区调用，不能占满维修线程。

## 审计和事件接收

GET /audit（Community）返回权限控制的社区权威审计，供社区审核结果核对。原 /admin/community/audit 页面查询 Core 本地统一审计视图，按 occurredAt DESC/sourceService/sourceAuditId 稳定分页，保留非社区类别。

Community→Core 使用独立服务身份和 Core 受众凭证：POST /internal/community-events/v1/events。不能使用用户凭证冒充发布器。Core 持久提交 inbox+通知任务/审计投影后返回200 accepted（duplicate=true 可表示已处理）；未知 schemaVersion422，暂时故障503并重试。同 eventId 不同摘要409并隔离告警，不覆盖。
