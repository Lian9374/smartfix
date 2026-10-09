# Sprint 3 · 社区模块实施契约（A）

对应计划：`SmartFix_Sprint3_Development_Plan_CN.md` §4.3.1、§6.6–6.8、§9.5、§10.1–10.3、
§11.3–11.6、§12.1–12.6、§13.2、§15.2、§17.1、§29。
配套文档：`docs/ui-guide.md`（共享 UI 契约，A 维护）、`UserC_Implementation_Handoff_CN.md`（C 的协作格式范例）。

---

## 当前状态索引（2026-10-09）

当前交付与验收以 [UserA 收口记录](UserA_Quality_Completion_CN.md)、
[注册限流交接](Registration_Hardening_Handoff_CN.md) 和 ADR-003 为准。
§11–17 是按阶段追加的历史记录，其中“举报未做”“无事件消费者”“没有限流”等旧句子
不能用于描述当前代码。当前举报、处理历史、恢复、社区通知和正式审计均已实现。
回答数、批量昵称、社区页面/输入反馈、空分页回退及作者并发发布保护见收口记录。
本轮不修改已发布的迁移；V17 为问题/回答，V18 为采纳复合外键，V19 为举报。
最新 GitHub main 的通知 V14/V20 冲突另列为集成边界，不能把本分支 PostgreSQL 验收
当作该冲突已经解决的证据。

## 0. 本文档的性质（先读这一段）

**当前实施基线已于 2026-10-08 按使用者授权确定，见 [ADR-003](../decisions/ADR-003-community-completion.md)。这是本次项目决策，不是团队会议批准记录。**

- 本文档把计划里**分散在 8 个章节**的社区契约（路由、状态、输入限制、返回值、事件、权限、
  迁移）收敛到一处，让 B/C/E 不必翻 3883 行计划就能对齐接口。
- 凡计划里已经写死的内容，本文档**照抄并标注出处**，不擅自改写。
- 原留待决定的 D-05、D-09、D-10、D-13、D-15 已在 §1 与 ADR-003 记录结论；后续章节中的旧「未定」描述仅为历史记录。
- 本文档同时记录了**实现者在核对计划时发现的 4 处契约自相矛盾**（§10）。这些不是
  建议，是计划本身需要出结论的地方。

**引用规则：** 当前实现引用 ADR-003 与本节结论，明确为使用者授权确定的项目基线；不得描述成未经发生的团队会议批准。

**历史证据基线：** 初稿核对于 main 的 `V1`–`V9` 迁移与当时的 `SecurityConfig`。
2026-10-08 的补全依据、迁移及验证见 §17 和 ADR-003。

---

## 1. 五项已确定决策（2026-10-08）

下表直接对应 §29 的 D-05 / D-09 / D-10 / D-13 / D-15。
**结论已按本次使用者授权确定**，历史建议与可选方案保留以说明原始权衡；当前验收以结论及 ADR-003 为准。

### D-05 提问者能否采纳自己的回答？

| 项 | 内容 |
|---|---|
| 问题 | 提问者可否把自己的回答标记为采纳答案 |
| 选项 | ① 允许 ② 禁止 |
| 规划者建议（§29） | **②禁止**，理由：避免自我刷「已解决」 |
| **当前项目结论** | **禁止自我采纳（ADR-003，2026-10-08）** |
| 若按建议基线执行 | `CommunityAnswerService.accept`（阶段三已落地，方法名不是草案里的 `acceptAnswer`）第 5 步：`answer.authorId.equals(actorUserId)` → `BusinessConflictException("You cannot accept your own answer.")` |
| 若改为① | 删掉该分支即可，其余规则不变；但 `Unanswered` 筛选会失去意义的一部分——作者可以瞬间把自己的问题变成「已解决」 |
| **实现状态：禁止自我采纳，现已确定** | 服务端分支、页面按钮条件及测试保留；以 ADR-003 为当前依据。旧阶段记录的暂定说明不再代表当前状态。 |
| 谁需要知道 | A（实现）、E（是否需要一条「自我采纳」的审计记录） |

### D-09 是否引入「关闭回答」概念？

| 项 | 内容 |
|---|---|
| 问题 | 是否需要「作者可停止接收新回答」这个独立开关 |
| 选项 | ① 引入 ② 不引入 |
| 规划者建议（§29） | **②不引入**，理由：v1 没有真实场景，造出来就是一个没有用途的状态 |
| **当前项目结论** | **不引入独立关闭回答开关（ADR-003，2026-10-08）** |
| 若按建议基线执行 | 领域模型只有 `CommunityContentStatus`（`VISIBLE`/`HIDDEN`/`WITHDRAWN`），再无第二个状态维度 |
| 若改为① | 需要新增列、新增迁移内容、新增作者路由、新增权限行，**并单独估算工作量**；§15.2 的 V17 内容随之变化 |
| 谁需要知道 | A、C（迁移内容）、B（若新增路由则要授权） |

### D-10 社区图片做不做？

| 项 | 内容 |
|---|---|
| 问题 | 社区问题/回答是否支持贴图 |
| 选项 | ① 本 Sprint 不做（v1 纯文本） ② 新建 `community_attachments` 表 ③ 复用 `request_attachments` |
| 规划者建议（§29） | **①不做 或 ②新建独立表**；**③绝对不行** |
| **当前项目结论** | **本 Sprint 社区纯文本，不提供社区附件（ADR-003，2026-10-08）** |
| 为什么③被明确禁止（§4.5） | `request_attachments.request_id` 是 `NOT NULL` 且外键指向 `maintenance_requests`，复用会让每条社区帖子被强制挂到一条报修上 |
| 若按①执行 | 正文一律 `th:text` 纯文本（§11.4），无上传路由、无存储、无独立授权策略 |
| 若改为② | 需要：独立元数据表 + 独立迁移号（V17/V18 之后）+ 独立授权策略（谁能看谁上传的图）+ 只在**存储层**复用 `AttachmentStorageService` 的能力 + 单独工作量估算 |
| 谁需要知道 | A、E（若复用存储能力，E 是存储的所有者）、C（再多一个迁移号） |

### D-13 「采纳的回答必须属于该问题」用数据库约束还是服务层？

| 项 | 内容 |
|---|---|
| 问题 | R3 的落点 |
| 选项 | ① 复合外键 `(accepted_answer_id, id) → (id, question_id)` ② 只在服务层校验 |
| 规划者建议（§10.1 / §29） | **① + 服务层友好校验**（双保险） |
| **当前项目结论** | **复合外键 + 服务端归属校验 + 条件更新（ADR-003，2026-10-08）** |
| 若按建议基线执行 | V17 建表时 `community_answers` 带 `UNIQUE (id, question_id)`；`community_questions` 带 `UNIQUE (id, accepted_answer_id)`；V18 用 `ALTER TABLE ... ADD CONSTRAINT` 补复合外键（环状外键必须后加） |
| 若改为② | 少一个环状外键，迁移更简单；但**必须**在 PR 里写明放弃的完整性保证（§10.1 的原文要求）。放弃后，并发路径下的「回答属于别的问题」只能靠条件更新挡住，`acceptAnswer` 的第 3 步从「双保险」降级为**唯一**防线 |
| 谁需要知道 | A、C（V17/V18 的写法直接不同） |

### D-15 社区反重复怎么实现？

| 项 | 内容 |
|---|---|
| 问题 | 同一用户短时间内重复发相同内容怎么办 |
| 选项 | ① 时间窗内相同标题+正文拒绝 ② 不限制 |
| 规划者建议（§29） | **①**，时间窗可配，**默认 2 分钟** |
| **当前项目结论** | **重复问题窗口默认 2 分钟；问题/回答分别默认每滚动 24 小时 20 条；按作者事务锁串行检查，不加 30 秒间隔（ADR-003，更新于 2026-10-09）** |
| 原计划差异的结论 | ADR-003 已统一：重复问题窗口默认 2 分钟，问题/回答分别默认每滚动 24 小时 20 条，不叠加 30 秒最小间隔。§10 保留原始差异记录。 |
| 若按①执行 | `CommunityProperties` 提供时间窗与每日上限；超限 → `InputValidationException`（§12.1）；**不**引入幂等 token（§11.5 已说明理由） |
| DB 兜底 | 举报的防重复**不靠**这个窗口，靠 §10.3 的两个部分唯一索引——它是硬约束，无论 D-15 怎么定都要做 |
| 谁需要知道 | A、E（若通知也要限流则共用配置） |

> **2026-10-08：** 五项结论现已写入计划 §29 与 ADR-003，原 Day 1 未定门槛已解除。

---

## 2. 路由契约（A-R1 … A-R22）

出处：§13.2。**路径在 Day 1 冻结后不得再改**，因为 `SecurityConfig` 的授权、
页面链接和渲染测试三处都要跟着改。

| # | 方法 | 路径 | 用途 | 授权 |
|---|---|---|---|---|
| A-R1 | GET | `/community` | 问题列表（搜索/分类/筛选/分页） | authenticated |
| A-R2 | GET | `/community/questions/new` | 提问表单 | authenticated |
| A-R3 | POST | `/community/questions` | 提问 | authenticated |
| A-R4 | GET | `/community/questions/{questionId}` | 问题详情 + 回答 | authenticated |
| A-R5 | GET | `/community/questions/{questionId}/edit` | 编辑表单 | authenticated（Service 校验作者） |
| A-R6 | POST | `/community/questions/{questionId}` | 保存编辑 | authenticated（Service 校验作者） |
| A-R7 | POST | `/community/questions/{questionId}/withdraw` | 撤回自己的问题 | authenticated（Service 校验作者） |
| A-R8 | POST | `/community/questions/{questionId}/answers` | 发布回答 | authenticated |
| A-R9 | GET | `/community/answers/{answerId}/edit` | 编辑回答表单 | authenticated（Service 校验作者） |
| A-R10 | POST | `/community/answers/{answerId}` | 保存回答编辑 | authenticated（Service 校验作者） |
| A-R11 | POST | `/community/answers/{answerId}/withdraw` | 撤回自己的回答 | authenticated（Service 校验作者） |
| A-R12 | POST | `/community/questions/{questionId}/answers/{answerId}/accept` | 采纳 | authenticated（Service 校验问题作者） |
| A-R13 | POST | `/community/questions/{questionId}/acceptance/remove` | 取消采纳 | 同上 |
| A-R14 | POST | `/community/questions/{questionId}/reports` | 举报问题 | authenticated |
| A-R15 | POST | `/community/answers/{answerId}/reports` | 举报回答 | authenticated |
| A-R16 | GET | `/community/mine` | 我的提问 / 我的回答（`?tab=questions\|answers`） | authenticated |
| A-R17 | GET | `/admin/community/reports` | 举报队列 | ADMINISTRATOR |
| A-R18 | POST | `/admin/community/reports/{reportId}/resolve` | 处理举报 | ADMINISTRATOR |
| A-R19 | POST | `/admin/community/questions/{questionId}/hide` | 隐藏问题 | ADMINISTRATOR |
| A-R20 | POST | `/admin/community/questions/{questionId}/restore` | 恢复问题 | ADMINISTRATOR |
| A-R21 | POST | `/admin/community/answers/{answerId}/hide` | 隐藏回答 | ADMINISTRATOR |
| A-R22 | POST | `/admin/community/answers/{answerId}/restore` | 恢复回答 | ADMINISTRATOR |

**路径匹配顺序（§13.2.1，必须验证而不是想当然）：**

| 潜在冲突 | 结论 | 需要的证据 |
|---|---|---|
| `/community/questions/new` vs `/community/questions/{questionId}` | Spring Boot 3 用 `PathPatternParser`，**字面量段优先于变量段**，`/new` 命中表单 | **必须**有一条测试断言 `GET /community/questions/new` 返回表单，而不是去查 `id = "new"` |
| `/community/mine` vs `/community/questions/...` | 层级不同，不冲突 | 无需 |
| `/community/answers/{answerId}` vs `/community/questions/{questionId}` | 第二段字面量不同 | 无需 |
| `/admin/community/**` vs 既有 `/admin/**` | **已被 `/admin/**` 覆盖**，不需要新规则 | 保持 `/admin/**` 在 `anyRequest()` 之前 |

**路由纪律（§13.2 末）：** 新增路由**必须先在 `SecurityConfig` 里显式授权**，
**再做**实现与页面。`docs/ui-guide.md` §13 的原文原则是**链接绝不能先于路由存在**。
在授权与路由落地之前，**首页和共享导航里不得出现指向 `/community` 的链接**——
一个 403 的链接比一个不存在的链接更糟，它看起来像功能坏了。

**错误语义（§13.4，沿用 Sprint 2，不新增）：**

| 情形 | 状态码 |
|---|---|
| 未登录访问受保护页面 | 302 → `/login` |
| 角色不符 | 403 |
| 资源不存在**或**无权读 | **404（故意不区分）** |
| 表单校验失败 | 200 + 页面内错误 |
| 非法状态流转 / 并发冲突 | 409 语义（页面级 `alert('error', ...)`） |
| 未知路径 | **403**（`anyRequest().denyAll()`，**不**改成 404） |
| 未处理异常 | 500 + `error.html` |

---

## 3. 领域状态契约

### 3.1 只有两个状态维度，且刻意不合并（§6.6）

| 维度 | 取值 | 谁改 |
|---|---|---|
| `CommunityContentStatus`（问题与回答**共用**） | `VISIBLE` / `HIDDEN` / `WITHDRAWN` | 作者可 `WITHDRAWN`；ADMIN 可 `HIDDEN` ↔ `VISIBLE` |
| **是否已解决** | **不存列**，由 `community_questions.accepted_answer_id IS NOT NULL` **推导** | 采纳 / 取消采纳改变它 |

### 3.2 三条必须遵守的推导规则

1. **「已解决」的唯一事实来源是 `accepted_answer_id`。**
   不存在 `solved` 布尔列。若同时存在两者，它们**必然**会在某次并发或异常路径后互相矛盾。
2. **隐藏/撤回一条已采纳的回答时，在同一个 `@Transactional` 里**
   `UPDATE community_questions SET accepted_answer_id = NULL WHERE id = ?`，
   解决状态自动跟随，**不存在需要同步的第二个字段**。
3. **筛选口径必须写全：**
   - `Unanswered` = `status = 'VISIBLE' AND accepted_answer_id IS NULL`
   - `Solved` = `status = 'VISIBLE' AND accepted_answer_id IS NOT NULL`
   - `LATEST`（默认）= `status = 'VISIBLE'`，按 `created_at DESC`

   > ⚠️ §6.6 把 `Solved` 简写成 `accepted_answer_id IS NOT NULL`，
   > **漏了 `status = 'VISIBLE'`**。列表永远只列 `VISIBLE` 的问题（R13），
   > 所以正确的口径是上面这一行。这是一处需要按本文档修正的简写，不是新增设计。

### 3.3 三个不得混用的概念（§6.6）

| 概念 | 是什么 | v1 |
|---|---|---|
| 内容隐藏 / 撤回 | 内容是否还能被公开看到 | ✅ 做 |
| 是否已解决 | 提问者是否采纳了一条回答 | ✅ 做（推导） |
| 关闭回答 | 一个独立的生命周期开关 | ❌ **不做**（D-09） |

### 3.4 可见性一致性铁律（§6.8 / R13）

一条 SQL 过滤条件（`status = 'VISIBLE'`）必须在
**列表、详情、搜索、计数四处都出现**。
推荐收敛到 `CommunityQueryService` 的一个私有谓词方法里，而不是在四个地方各写一遍。

| 场景 | 列表 | 详情 | 搜索 | 管理员 |
|---|---|---|---|---|
| 问题 `VISIBLE` | 可见 | 可见 | 命中 | 可见 |
| 问题 `WITHDRAWN`（作者撤回） | 不出现 | 作者本人可见（带提示），他人 404 | 不命中 | 可见 |
| 问题 `HIDDEN`（管理隐藏） | 不出现 | 作者本人可见（带提示），他人 404 | 不命中 | 可见，可恢复 |
| 回答 `WITHDRAWN` | 不显示正文，占位「This answer was withdrawn.」 | 同上 | 不参与 | 可见 |
| 回答 `HIDDEN` | 不显示正文，占位「This answer is hidden.」 | 同上 | 不参与 | 可见，可恢复 |

---

## 4. 输入限制契约（§11.3 / §11.6）

**规则：HTML 的 `maxlength`、DTO 的 `@Size`、实体校验、DB 列宽 —— 四处必须相同。**
任何一处不同都会变成「页面能填、保存报错」或「保存成功但被截断」。

| 字段 | 限制 | DB 列宽 | 校验位置 |
|---|---|---|---|
| 问题 `title` | 必填；trim 后 **3–150** | `VARCHAR(150)` | DTO + 实体 + DB CHECK（`= btrim()`） |
| 问题 / 回答 `body` | 必填；trim 后 **10–4000** | `VARCHAR(4000)` | 同上 |
| `category` | 必填；5 值枚举 | `VARCHAR(30)` | DTO + DB CHECK |
| 举报 `reason` | 必填；5 值枚举 | `VARCHAR(30)` | DTO + DB CHECK |
| 举报 `detail` | 选填；**≤500** | `VARCHAR(500)` | DTO |
| 搜索 `q` | trim 后 **≤100**；0 字符视为无搜索 | — | **Service**（`CommunityQueryService.likePattern`，见下） |
| 分页 `page` | **≥0，负数归 0** | — | Service（沿用 `RequestQueryService` 的做法） |
| 分页 `size` | 默认 **10**，上限 **50**，非法值取默认 | — | Service |

**搜索上限为什么落在 Service 而不是 Controller（本行已修正）：**
Controller 的职责是把 HTTP 输入交给 Service，长度上限是「查询本身是否可接受」的规则，
和分页上限同类。放在 Service 的另一个理由是**唯一性**：
`likePattern` 同时负责 trim、长度校验、大小写归一和通配符转义，
这四件事必须一起发生，否则「长度校验通过但模式没转义」这种组合会出现。
Controller 里再写一次 `@Size` 会变成第二个定义。

**通配符语义（§11.4 要求「显式说明」，此处固定下来）：**
搜索词里的 `%` 和 `_` 是**字面字符，不是通配符**。
用户输入 `50%` 是找「50%」这个字符串，不是「以 50 开头的任何内容」。
实现：Java 侧把 `%`、`_` 和转义符本身用 `!` 前缀，
SQL 侧写 `LOWER(col) LIKE :pattern ESCAPE '!'`。
转义符选 `!` 而不是反斜杠，是为了避开 JPQL 字符串字面量里反斜杠需要双写的问题。
不使用 PostgreSQL 的 `ILIKE`：H2 不支持它，而本模块除原生 PG 用例外的所有测试都跑在 H2 上，
用 `ILIKE` 等于把大小写行为放在一个没有任何测试覆盖的地方。

**内容安全（§11.4 / R9）：**

| 规则 | 做法 |
|---|---|
| 纯文本渲染 | Thymeleaf `th:text`；**全仓库禁用 `th:utext`** |
| 换行保留 | CSS `white-space: pre-wrap`（`.prose`，**已存在**），**不**用 `th:utext` 换 `<br>` |
| 链接 | **不**自动识别 URL（避免把 `javascript:` 变成可点链接） |
| 验收 | 渲染测试断言：含 `<script>alert(1)</script>` 的正文在 HTML 中**以转义形式**出现 |

**可编辑字段的白名单（§11.3 末行，R1、R8）：**
编辑**只能**改 `title` / `body` / `category`；
**不能**改 `author_id` / `status` / `accepted_answer_id`。
命令对象里**没有** `authorId` 字段 ——
作者身份只来自 `SmartFixUserDetails.getUserId()`（§5.3 铁律 2）。

**DTO 名称（本节原写作 `EditQuestionCommand` / `EditAnswerCommand`，实现时合并为
一个 `QuestionFormCommand`，此处更正）：**
提问和编辑的字段与规则完全相同，只有文案、目标 URL 和按钮文字不同，
所以两者共用一个命令对象，由 `editing` 一个布尔量决定那三处差异。
分成两个类的代价是同一份 `@Size` 注解要写两遍，而两遍注解迟早会不一致 ——
这正是本节开头「四处必须相同」那句话要防的事。
`EditAnswerCommand` **目前不存在**，也不需要存在：回答交互是下一阶段的范围内的事，
现在写一个没有调用方的命令对象只会是死代码。
本阶段真正落地的 DTO 是
`QuestionFormCommand`（表单绑定与校验）、
`QuestionFilter`（列表筛选标签）、
`CommunityQuestionSummaryResponse` / `CommunityQuestionDetailResponse` / `CommunityAnswerResponse`
（视图模型，均只读）。

---

## 5. 权限契约（§5.3 / §5.4）

**三条铁律：**

1. **`navRole` 只决定「显示什么」，永远不决定「允许什么」。**
   授权只在 `SecurityConfig` + Service 层所有权/状态检查里。
2. **作者身份只来自认证上下文**，**绝不**从表单字段读 `authorId`。
3. **越权读取一律返回 404，不是 403**，避免暴露「该资源存在」。

| 能力 | REQUESTER | TECHNICIAN | ADMIN |
|---|---|---|---|
| 浏览列表 / 详情 / 搜索 / 筛选 | ✅ | ✅ | ✅ |
| 发布问题 / 回答问题 | ✅ | ✅ | ✅ |
| 编辑问题 / 回答 | 🔒 本人 | 🔒 本人 | 🔒 本人 |
| 撤回自己的问题 / 回答 | 🔒 本人 | 🔒 本人 | 🔒 本人 |
| 采纳 / 取消采纳 | 🔒 **本人的问题**（+D-05 未定） | 🔒 本人的问题 | ⛔ **不代采纳** |
| 举报问题 / 回答 | ✅ | ✅ | ✅ |
| 处理举报 / 隐藏 / 恢复 | ⛔ | ⛔ | ✅ |

**管理员不代采纳（§5.4 注）：** 管理员可以**隐藏**一条被采纳的回答，
但由此触发的「清除采纳」是**系统副作用**，不是管理员在替提问者做决定。

**所有权检查的位置：** 全部在 **Service 层**（§4.3.1 第 16 条：
「服务端权限检查，不依赖页面隐藏」）。页面隐藏只是体验，不是防线。

---

## 6. 分页与查询返回结构（§12.4）

### 6.1 计划原文

| 方法 | 输入 | 返回 | 权限 |
|---|---|---|---|
| `browse(category, filter, q, page, size)` | 筛选条件 | `List<CommunityQuestionSummaryResponse>` | 任何 ACTIVE 角色 |
| `myQuestions(actorUserId, page, size)` | — | 同上 | 本人，**含**自己的隐藏/撤回内容 |
| `myAnswers(actorUserId, page, size)` | — | `List<CommunityAnswerResponse>` | 同上 |

### 6.2 ⚠️ 计划内部冲突（见 §10 缺陷 1）

§11.3 要求 `page`/`size` 分页（默认 10、上限 50），但 §12.4 的返回类型是**裸 `List`**。
**裸 `List` 没有总数，页面就无法渲染翻页器，也无法显示「共 N 条」。**

本仓库已有的先例是 `request` 模块：

```java
// RequestQueryService（已存在，main 上可查）
public Page<MaintenanceRequestSummaryResponse> listMyRequestsPage(...)
```

`docs/ui-guide.md` §7 记录的 `${pagination}` 契约
（`.hasPrevious()` / `.hasNext()` / `.totalElements` / `.number` / `.size`）
就是按 Spring Data `Page` 写的。

### 6.3 A 的建议基线（**未定，不得写成已批准**）

**建议：三个查询方法返回 `Page<...>`，与 `request` 模块保持一致。**

| 方案 | 优点 | 缺点 |
|---|---|---|
| ① `Page<CommunityQuestionSummaryResponse>`（**建议**） | 与 `RequestQueryService.listMyRequestsPage` 一致；`${pagination}` 契约直接可用；UI 指南已文档化 | Service 接口泄漏 Spring Data 类型；换持久化框架要改签名 |
| ② 项目自有 `PageResponse<T>` record | 不泄漏框架类型；返回结构由本项目定义 | **偏离现有先例**，两个模块的分页写法不同，UI 片段要写两套；引入新类型需要团队同意 |
| ③ 返回 `List` + 另给 `count(...)` 方法 | 不改计划原文签名 | 两次查询之间有竞态（计数与列表可能来自不同快照）；调用方必须记得调第二个方法 |

**若团队选②或③，`docs/ui-guide.md` §7 的 `${pagination}` 说明必须同步更新** ——
它现在描述的是 `Page` 的属性名。

**统一的分页对象属性（无论选哪种，页面依赖这三个）：** `hasPrevious()`、`hasNext()`、
`totalElements`。页码从 0 开始（沿用 `RequestQueryService` 的做法）。

**`myQuestions` / `myAnswers` 的特例（§6.8 / R14）：** 本人**能看到**自己已隐藏/撤回的
内容，所以这两个方法的过滤条件**不是** `status = 'VISIBLE'`，而是 `status IN (...)` 或
不加状态条件 + 页面上标注占位文案。这与 `browse` 是**不同**的谓词，不要复用同一个。

---

## 7. 事件契约（给 E）

出处：§9.5.4、§12.6、§29.1 的 F-5/F-6/F-7。

### 7.1 依赖方向（单向，不得反转）

> **`community` 只发布，不订阅。`community` 不依赖 `notification` 或 `audit`。**

`community` 的 `event` 包里定义事件类型；E 的 `notification` / `audit` 监听器
`import` 社区的事件类型。这是计划允许的**唯一**一种「依赖方向反转」形式（§12.6），
方向单一，不构成循环。

### 7.2 事件字段（**已在代码中落地并冻结**，F-5 / F-6 / F-7）

载荷约束（§12.6）：事件是 `record`、**不可变**；只携带 **ID 与必要值**；
**禁止**携带 JPA 实体、`User`、`HttpServletRequest`。

> **本节由「建议」升为「事实」（阶段三，2026-10-07）。** 下面三段代码是**仓库里
> 现有记录的原文**，不再是草案。E 的监听器按这三段写。
> 与本节早先草案的**三处差异**列在代码之后，其中第 1 条会影响 E
> 能否在通知正文里写标题，**必须先对接，不能默认标题会到位**。

```java
// community/event/CommunityAnswerCreatedEvent.java        （F-5，已落地）
public record CommunityAnswerCreatedEvent(
        Long    answerId,           // 新回答的 id
        Long    questionId,         // 它回答的问题
        Long    questionAuthorId,   // 收件人候选（提问者）
        Long    answerAuthorId,     // 触发者（回答者）
        Instant occurredAt) {}
```

```java
// community/event/CommunityAnswerAcceptedEvent.java       （F-6，已落地）
public record CommunityAnswerAcceptedEvent(
        Long    answerId,           // 被采纳的回答
        Long    questionId,
        Long    questionAuthorId,   // 触发者（问题作者采纳了别人）
        Long    answerAuthorId,     // 收件人候选
        Instant occurredAt) {}
```

```java
// community/event/CommunityContentHiddenEvent.java        （F-7，已落地）
public record CommunityContentHiddenEvent(
        Long    contentId,                      // 问题或回答的 id，由 contentType 指明
        CommunityContentType contentType,       // QUESTION | ANSWER
        Long    questionId,                     // 所属问题，供通知链接
        Long    authorId,                       // 收件人候选
        Long    actorUserId,                    // 撤回的作者本人，或执行隐藏的 ADMIN
        CommunityContentStatus resultingStatus, // WITHDRAWN | HIDDEN | VISIBLE（恢复）
        Instant occurredAt) {}
```

**差异 1（必须与 E 对接）：三个事件都**不带** `questionTitle`。**
早先草案带它的理由是「通知正文需要标题，而 §12.5 不允许 notification 回查 community」。
落地版本没有它。**后果是真实的：** E 若要在通知正文里写出标题，
只有两条路 ——（a）按 §28.3 提文档先行变更，给 F-5/F-6 加字段；
（b）在 §12.5 表里显式新增一行 `community → CommunityQueryService.findQuestionForNotification(...)`，
并接受这条反向依赖。**本阶段没有替 E 选。** 事件目前只保证「谁、对哪个 id 做了什么」。

**差异 2：没有 `CommunityHiddenCause` 枚举。** F-7 用一个 `resultingStatus`
表示**方向**（撤回 / 隐藏 / 恢复），而不是用一个「原因」枚举。区分撤回与隐藏靠
`authorId.equals(actorUserId)`；恢复由 `resultingStatus = VISIBLE` 表示。
一个事件类型承担三种方向，是计划 §12.2/§12.3 本身就把三种动作指到同一个事件上的结果，
不是实现时的合并 —— 见 §10 缺陷 2（恢复语义）在 F-7 里留下的位置。

**差异 3：字段名与顺序。** `answerId` 在 `questionId` 之前；F-7 的作者字段叫 `authorId`
而不是草案里的 `contentAuthorId`。事务内发布的时机不变（§7.3）。

**F-7 里 `resultingStatus = VISIBLE`（恢复）目前没有发布方**：审核模块
（`hideQuestion` / `restoreQuestion` / `hideAnswer` / `restoreAnswer`）尚未实现，
本阶段只实现了作者撤回回答这一条发布路径。字段为它留了位置，但**不要**据此认为
恢复流程已经存在。

**收件人规则（A 的建议基线，E 可调整）：**
- `CommunityAnswerCreatedEvent`：通知 `questionAuthorId`；**若 `questionAuthorId.equals(answerAuthorId)` 则不通知**（自问自答不打扰自己）。
- `CommunityAnswerAcceptedEvent`：通知 `answerAuthorId`；同理跳过自己。
- `CommunityContentHiddenEvent`：`authorId.equals(actorUserId)` 时**不通知**（作者自己做的动作）；不相等时通知 `authorId` 且**排除** `actorUserId`（管理员隐藏了别人的内容）。
  > 早先草案这里写的是 `cause = HIDDEN_BY_MODERATOR` / `WITHDRAWN_BY_AUTHOR`，那个枚举不存在（§7.2 差异 2），判据改为两个 id 是否相同。
  > **本阶段唯一会发出这个事件的路径是作者撤回自己的回答**，按上表它不通知任何人 —— 所以这条规则现在只是给审核模块留下的位置。

### 7.3 AFTER_COMMIT 消费契约（§12.6 硬规定）

> **所有订阅方使用 `@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)`。**

**理由：** 业务事务回滚时**绝不能**发出「成功」通知。
`AFTER_COMMIT` 保证通知只在业务事实**已提交**之后才被触发。

E 的监听器必须知道这条注解带来的**六个后果**：

| # | 后果 | 对 E 的要求 |
|---|---|---|
| 1 | 若发布时**没有活动事务**，监听器**默认不会执行**（`fallbackExecution` 默认 `false`） | 社区所有发布事件的方法在 §12.1–12.3 都标了 `@Transactional`，且 §12.7 规定事务编排**只在 Service、不在 Controller**，所以正常情况下总有活动事务。E 若依赖「没有事务也能收到」，必须显式 `fallbackExecution = true` 并说明理由 |
| 2 | 监听器在**提交之后**运行，它抛出的异常**不能**回滚业务事实 | 监听器内部必须捕获所有异常，交给 `notification` 自己的重试机制（§14.7）。**不得**让异常冒泡到用户请求 |
| 3 | 监听器运行时持久化上下文**可能已经关闭**，实体是游离的 | 这正是「事件只带 ID 与必要值、禁止带实体」这条规则存在的原因 —— 拿着游离实体做懒加载会炸 |
| 4 | 默认是**同步**的，在提交它的那个线程上运行 | 长耗时的投递会拖慢用户响应。若要异步，用 `@Async` **叠加** `@TransactionalEventListener`；异步不改变「只在提交后触发」的保证，但会改变线程与异常处理方式 |
| 5 | 同一事件的多个监听器**没有定义顺序** | `notification` 与 `audit` 不得互相依赖（例如审计不得假设通知已经发出） |
| 6 | 这是**至多一次**投递：提交与投递之间进程崩溃，通知会**丢失** | v1 接受这个限制，**不**声称「恰好一次」。要消除它需要发件箱表（outbox），属于 Sprint 4 的稳定性范围（§4.4 S4-2） |

### 7.4 发布点与计划的不一致（需 Day 1 裁定，见 §10 缺陷 2/4）

| 发布点 | 计划 §12.1–12.3 的「事件」栏 | 本文档的建议 |
|---|---|---|
| `CommunityQuestionService.askQuestion` | `—` | 无事件 ✅ 一致 |
| `CommunityQuestionService.withdrawQuestion` | `—` | ⚠️ **建议确认**：撤回问题会让其下所有回答不再公开可见，但计划不发布任何事件。若 E 需要通知回答者「你回答的问题已被作者撤回」，这里必须补一个事件 |
| `CommunityAnswerService.postAnswer` | `CommunityAnswerCreatedEvent` | ✅ 一致 |
| `CommunityAnswerService.withdrawAnswer` | `CommunityContentHiddenEvent`（**若曾被采纳**） | ✅ **已落地的口径见下方 §7.4.1**：不是「无条件」，也不是「若曾被采纳」，而是**当且仅当本次调用真的改变了状态** |
| `CommunityAnswerService.acceptAnswer` | `CommunityAnswerAcceptedEvent` | ✅ 一致 |
| `CommunityAnswerService.removeAcceptance` | `—` | ✅ 与计划一致（**已落地**：取消采纳不发任何事件，见 §7.4.2） |
| `CommunityModerationService.resolveReport` | `CommunityContentHiddenEvent`（若隐藏） | ✅ 一致 |
| `CommunityModerationService.hideQuestion/hideAnswer` | `CommunityContentHiddenEvent` | ✅ 一致 |
| `CommunityModerationService.restoreQuestion/restoreAnswer` | `CommunityContentHiddenEvent` | ❌ **语义错误**：恢复内容却发布「内容被隐藏」事件，通知会发出错误的消息。建议：恢复**不发布**该事件，或另定义 `CommunityContentRestoredEvent` |
| `CommunityModerationService.reportQuestion/reportAnswer` | `—` | ✅ 一致（举报不通知被举报人） |

> **「若曾被采纳」为什么要去掉：** 通知的收件人是**作者本人**，
> 而撤回正是作者自己的动作，所以按 §7.2 的收件人规则（两个 id 相同则不通知）
> 本来就不会发通知。既然如此，条件发布只会让「事件是否发出」取决于一个与通知无关的
> 事实，给测试增加一条无意义的分支。真正的业务副作用（清除 `accepted_answer_id`）
> 在**事务内**完成，与事件无关。

#### 7.4.1 撤回事件的实际口径（**计划未裁定，本阶段选定并标注为暂定**）

计划 §12.2 给 `withdrawAnswer` 的事件栏只有一句话：
「`CommunityContentHiddenEvent`（若曾被采纳）」。
它没有说**重复撤回**要不要再发一次，也没有说 `cause` 从哪来（§7.2 差异 2 已经说明那个枚举不存在）。

本阶段落地的口径是：

> **当且仅当本次调用把回答的状态从非 `WITHDRAWN` 改成了 `WITHDRAWN`，发布
> `CommunityContentHiddenEvent`，`resultingStatus = WITHDRAWN`。**

三条理由，按重要性排列：

1. **幂等重复不发第二次。** 撤回是幂等的（服务层的明确要求：双击表单不能变成错误页）。
   若每次调用都发事件，那么一次重复提交就会让下游收到两条「内容已隐藏」，
   而业务事实只发生了一次。事件描述的是**变化**，不是**调用**。
2. **去掉「若曾被采纳」这个条件。** 见上面那段引注：它影响的不是通知，
   而只是「事件是否发出」；把它留下会让一个与通知无关的事实决定下游是否收到事件。
3. **`resultingStatus` 而不是 `cause`。** 见 §7.2 差异 2。

**这仍是暂定口径。** 「重复撤回是否要发第二条件事件」是计划没有回答的问题，
本阶段按「事件描述变化」这一条通用原则选了一边。若团队裁定相反（例如为了审计要记录
每一次撤回动作），改动点是 `CommunityAnswerService.withdraw` 里的一个 `if`，
**不影响 F-7 的字段**。

#### 7.4.2 取消采纳不发事件（与计划一致，此处只是明确）

`removeAcceptance` 在计划里的事件栏是 `—`，本阶段按 `—` 实现：
**取消采纳不发布任何事件，也不通知原回答者。**
「你的回答不再被采纳了」这条通知因此**不存在**，如果 E 认为它必要，
那是一次文档先行变更，不是本实现遗漏。
与它对称的另一条：取消采纳**不会**恢复任何内容，恢复内容也**不会**恢复旧的采纳
（两种状态各自只有一个写入口，见 §3.2 与下方的锁顺序约定）。

---

## 8. 迁移契约（给 C）

### 8.1 初稿历史基线（当时核对于 main；不是当前迁移目录）

初稿时 `src/main/resources/db/migration/` 为 **V1 – V9**；当前新增迁移见本节实际登记与收口记录：

```
V1__baseline.sql                    V6__create_request_status_history.sql
V2__create_users.sql                V7__extend_request_lifecycle.sql
V3__create_locations.sql            V8__create_work_orders.sql
V4__create_maintenance_requests.sql V9__create_request_feedback.sql
V5__create_request_attachments.sql
```

`V6`–`V9` **已在 main 上**，对应 §15.2 表里的 V6–V9（C 的内容）。
**V10–V16 已被 §15.2 预留给 B/D/E**，登记表是唯一登记处，由 C 维护。

### 8.2 社区实际编号：V17 / V18 / V19（原计划 §15.2）

**原计划：**

| 版本 | 内容 | 负责人 | 表结构依赖 |
|---|---|---|---|
| **V17** | `community_questions` + `community_answers` | A | `V2`（`users`） |
| **V18** | `community_reports` | A | `V17` |

**实际合并（本阶段已交付，见 §13）：**

| 版本 | 文件 | 实际内容 |
|---|---|---|
| **V17** | `V17__create_community_questions_and_answers.sql` | **只有两张表**：`community_questions` + `community_answers`（含各自的 CHECK、索引、`UNIQUE (id, question_id)`） |
| **V18** | `V18__add_community_accepted_answer_constraint.sql` | **复合外键**：`ALTER TABLE community_questions ADD CONSTRAINT fk_community_questions_accepted_answer FOREIGN KEY (accepted_answer_id, id) REFERENCES community_answers (id, question_id)`，外加 `idx_community_questions_accepted_answer`（部分索引，PostgreSQL 不会为引用侧自动建） |

| **V19** | `V19__create_community_reports.sql` | 举报、处理记录与防重复唯一索引；依赖 V17/V18 |

> **编号更正（原表最后一行是错的，此处以实际为准）：**
> `V18` **不是** `community_reports`，而是 D-13 建议基线里的「补复合外键」这一步。
> §8.4 与 D-13 都已经写明「环状外键必须后加」，也就是说
> **V18 这个号在写 V18 之前就已经被复合外键占用了**，
> 原表把 `community_reports` 放在 V18 是编号冲突，实现时不能照抄。
>
> **因此：`community_reports` 目前还没有迁移号，仍需要一个号（V19 或更晚）。**
> 这是需要 C 登记的事项，A 不自行决定。
> 举报功能不在本阶段范围内，所以这个缺失**不影响本次交付**，
> 但 §8.5 提到的「D-13 选② 时 V18 会缩小到只有 `community_reports`」这条推论同样作废：
> 无论 D-13 怎么选，V18 都已经是复合外键，
> 选②只会让 V18 变成**空文件**（或干脆不建），不会变成举报表。

**为什么拆成两个文件而不是一个（这三条都是环状外键的直接结果）：**

1. **先建表、后加约束。** `community_questions.accepted_answer_id` 指向
   `community_answers`，而 `community_answers.question_id` 又指向 `community_questions`。
   两张表在同一条语句里互相引用是建不出来的，必须先都建好，再补其中一条边。
2. **补的那条边是复合的。** 单列外键只能保证「这个 id 是某个回答」，
   保证不了「这个回答是**这个问题的**回答」。复合外键 `(accepted_answer_id, id)
   → community_answers (id, question_id)` 才能表达后者，这也是 D-13 建议基线的全部意义。
3. **`MATCH SIMPLE` 让 NULL 跳过检查。** 未采纳的问题 `accepted_answer_id IS NULL`，
   复合键里只要有一列为 NULL，`MATCH SIMPLE`（PostgreSQL 默认）就不做检查 ——
   否则「没有已采纳回答」这个再普通不过的状态会被外键拒掉。

> **登记纪律（§15.2）：** 只有 C 在登记表里写入一行，编号才算被占用。
> 本文档只是**请求**编号，不构成占用。

### 8.3 合并顺序建议（这是本节的重点）

按 §15.2，V10–V16 属于 B/D/E，V17/V18 属于 A。**A 不能假设 V10–V16 一定会先合并。**

**规则：Flyway 按版本号顺序应用；A 的迁移只依赖 V2 与 V17 自身，
不依赖 V10–V16 的任何一张表。**

由此得到两条操作建议：

1. **A 可以在 V10–V16 尚未合并时先合并 V17/V18**，因为 `community_questions.author_id`
   只外键到 `users(id)`（V2），不指向任何 B/D/E 的表。
   代价：`V17` 的版本号高于尚未存在的 V10–V16，**后合并的 V10–V16 会变成乱序迁移**，
   已有本地库必须 `flyway repair` 或重建。**是否接受这个代价由 C 和团队决定**，
   A 不单方面决定。
2. **更保守的做法（A 的建议）：** 等 V10–V16 全部合并后再提交 V17/V18，
   保证「版本号顺序 = 合并顺序」。代价是 A 的数据库工作被 B/D/E 的进度阻塞。

> **A 的实际处境：** 领域模型与 `V17` 是 PR-1 的内容（§17.1），
> 但它被 D-09（是否新增状态）和 D-13（是否用复合外键）阻塞。
> 所以**真正的前置条件是 Day 1 的两项决策，而不是迁移号**。

> **⚠️ 实际发生的是「建议 1」，且需要 C 明确追认（本次交付的事实）：**
> 写这份契约时，V10–V16 **一个都还没有**：`src/main/resources/db/migration/`
> 当前是 `V1, V2, V3, V4, V5, V6, V7, V8, V9, V17, V18`。
> 也就是说 A **已经在 V10–V16 缺位的情况下提交了 V17/V18**，
> 上面那两条操作建议里的第 1 条成了既成事实。
>
> **后果，必须由 C 处理，不能默认没发生：**
> B/D/E 之后合并的 V10–V16，其版本号将**低于已应用的 V17/V18**。
> Flyway 默认 `outOfOrder=false`，在**已经跑过 V17/V18 的库**上，
> 这些补进来的 V10–V16 会被**忽略**（或按配置报错），
> 而不是被应用——比 §8.3 原文写的「变成乱序」更严重：
> 不是顺序难看，是**新迁移不会执行**。
>
> **A 明确没有做的事：** 没有开启 `spring.flyway.out-of-order`，
> 没有 `repair`，没有 `clean`，没有删库（这是任务约束）。
> 所以这个问题的处理方式是 C 的决定，A 只报告事实。
> 三个可选处理：让 B/D/E 改用 V19 起的号；或由 C 统一在合并时开启
> `out-of-order` 并 `repair`；或在 V10–V16 合并前**不要**在任何共享库上应用 V17/V18。
> **本机开发库不受影响**（H2 内存库 + `ddl-auto=create-drop` 走实体，不走 Flyway）。

### 8.4 V17/V18 内部的写法要点（§10.1–10.3、§15.2）

- **环状外键必须分两步**：`community_questions.accepted_answer_id` 指向
  `community_answers`，而 `community_answers.question_id` 指向 `community_questions`。
  处理方式：**V17 建两张表**（回答表的 FK 建表时即带；问题表的 `accepted_answer_id`
  **不带**外键），**V18 用 `ALTER TABLE ... ADD CONSTRAINT` 补上复合外键**。
  顺序：**先建表、后加环状外键**。
- **V18 不只为举报表存在**：它同时承担「补复合外键」这一步。
  这也正是 `community_questions` 与 `community_answers` **不能**合并成一个迁移的理由之一
  （§15.2：将来只回滚举报表时无法拆分）。
- **不加 `ON DELETE CASCADE`**（R7）：隐藏/撤回是**逻辑状态**，
  隐藏问题**不**删其回答。
- **举报的防重复用两个部分唯一索引**，不是复合唯一（§10.3）。
  PostgreSQL 中 `NULL` 互不相等，单个
  `UNIQUE (reporter_id, question_id, answer_id)` 拦不住重复举报。
- **`maintenance_requests` 与社区无关**：§15.2 明确禁止把社区图片塞进
  `request_attachments`（D-10）。

### 8.5 若 D-13 选②（只在服务层校验）

则 V17 不需要 `UNIQUE (id, question_id)` 与 `UNIQUE (id, accepted_answer_id)`，
V18 也不需要复合外键。

> **原文此处写「V18 的内容会缩小到只有 `community_reports`」，该推论已作废（见 §8.2）：**
> `community_reports` 从来不是 V18 的内容，把 D-13 选② 说成「V18 剩下举报表」
> 是建立在原 §8.2 那张错误编号表上的。
> 实际情况是：**D-13 选② 只会让 V18 变成一个空文件**（没有约束可加），
> 举报表仍然需要一个独立的新号。
>
> **这条更正对本阶段没有实际影响**：本阶段已按①（数据库约束）实现并交付，
> 见 §13 的验证结果。上面这段只在「团队事后改选②」时才需要回头看。

**这正是 D-13 必须在写迁移前出结论的原因。**（本阶段按① 执行，见 §13。）

---

## 9. 授权变更建议（给 B）

**背景：** `SecurityConfig` 由 **B 协调**（§17.1 明确写在 A 的「不负责」栏里）。
本分支**没有**已授权的协作安排，因此本节只给出**可审阅的最小补丁**，
**不擅自应用**，也**不擅自放宽任何权限**。

### 9.1 当前状态（**已更新：补丁已应用**）

原状态是 `SecurityConfig.securityFilterChain` 的最后两条为：

```java
.requestMatchers("/admin/**").hasRole("ADMINISTRATOR")
.requestMatchers(HttpMethod.GET, "/actuator/info").hasRole("ADMINISTRATOR")
.anyRequest().denyAll()
```

因此**当时**访问 `/community` 会命中 `anyRequest().denyAll()` → 403，
与 `docs/ui-guide.md` §13 的原则一致：路由先授权，再链接。

**现在该补丁已应用**（见下），`/community/**` 由 `.authenticated()` 放行，
Community 也已接入三种角色的导航和首页 —— 即 §13.3 原先列为「未做」的那两项，
在授权到位之后已完成。

### 9.2 最小补丁（一行，**已应用**）

```diff
                         .requestMatchers("/admin/**").hasRole("ADMINISTRATOR")
                         .requestMatchers(HttpMethod.GET, "/actuator/info").hasRole("ADMINISTRATOR")
+                        // Sprint 3 community: every route is for signed-in users of any role.
+                        // The six moderation routes live under /admin/community/ and are already
+                        // covered by /admin/** above. Deliberately .authenticated() rather than
+                        // permitAll: a signed-out visitor must not reach the board at all, and
+                        // ownership is checked again in the service layer.
+                        .requestMatchers("/community/**").authenticated()
                         .anyRequest().denyAll())
```

**为什么是一行就够：**
A-R1…A-R16 全部是「任意已登录角色」；A-R17…A-R22 全部以 `/admin/community/` 开头，
**已被上面那条 `/admin/**` 规则覆盖**，不需要新增规则（§13.2.1 第 4 行）。

> **补丁状态：已应用。** 仓库所有者在知悉下文全部后果后明确要求应用，
> 因此这不再算 A 的单方面改动。
>
> **测试专用授权链 `CommunityTestSecurityConfig` 已删除。**
> 它存在的唯一理由是「补丁未应用时页面根本渲染不出来」。补丁落地后它不但多余，
> 而且**有害**：它会遮住生产链，让 IT 在一条假链上全绿，
> 却证明不了真正的授权规则 —— 而那恰恰是这行 diff 要保证的东西。
> 两个 IT 的 `@Import` 已移除，现在跑在**生产 `SecurityFilterChain`** 上。
>
> **新增的授权回归（「接入导航」之前必须先有的东西）：**
> - **社区 GET 的角色矩阵不在 `SecurityConfigTest`，在
>   `CommunityPagesIT.everyRoleReachesTheBoardThroughTheSameRoutes`**：
>   三种角色 × 3 个 GET（`/community`、`/community/mine`、`/community/questions/new`）
>   全部期望 200。放在那里更值 —— 它跑在**生产链 + 真实控制器 + 真实模板**上，
>   而 `WebMvcTest` 切片用的是探针桩，只能证明「规则没把它挡掉」。
>   **本节早先写的「`SecurityConfigTest` 新增 3 角色 × 5 个社区 GET、用例数 42 → 63」
>   与仓库现状不符**：实际是 42 → **51**（`/community` 进匿名重定向表 1 条 +
>   写入用例 1 条 + 此前的用例），GET 矩阵如上所述在 `CommunityPagesIT`。
>   这是一处文档错误，在此更正；`SecurityConfigTest` 的实测总数见 §14.2。
> - `SecurityConfigTest` 为社区真正新增的两条：
>   `anonymousPageAccessRedirectsToLogin` 的 `@ValueSource` 增加 `/community`，
>   以及一条 `communityWritesAreForEverySignedInRoleAndStillRequireCsrf`
>   （5 个社区写路由 × 3 角色 × 有/无 CSRF）。
> - **实测证据（阶段三重新测量，2026-10-07）**：把 `/community/**` 那一行临时删掉后
>   —— `SecurityConfigTest` **1 条失败**（那条写入用例；匿名重定向那几条**不会**失败，
>   因为未认证请求命中 `denyAll()` 时 `ExceptionTranslationFilter` 走的是登录入口，
>   仍然是 302，见下方说明）；
>   `CommunityPagesIT` **55 条里 52 条失败**。合计 **53 条**。
>   恢复该行后两者全绿。**本节早先写的「16 个用例失败」是错的**，实际敏感面比它大得多。
> - `HomeControllerTests` 3 → **6**：新增参数化用例，断言**三种角色的首页**
>   都同时给出导航链接与社区卡片。
>   导航由两个不同片段渲染（管理员的 rail、其余角色的 bar），
>   只测一个角色会漏掉另一个 —— 我第一次写这条断言时就确实漏了 rail
>   （rail 把链接文字包在 `<span>` 里，bar 是纯文本），失败后改为按角色区分断言。

### 9.3 必须保持不动的部分

| 项 | 要求 |
|---|---|
| `.csrf(Customizer.withDefaults())` | **保留**。社区的 **15 个 POST 路由**（A-R3/6/7/8/10/11/12/13/14/15/18/19/20/21/22）全部依赖它。去掉 CSRF 等于让这些写操作可以被任意跨站表单触发 |
| `.anyRequest().denyAll()` | **保留**，且必须**排在 `/community/**` 之后**。删掉它等于把所有未声明的路径开放 |
| `.exceptionHandling(...accessDeniedHandler...)` | **保留**：`sendError(403)` 与 §13.4 的「未知路径返回 403」一致 |
| `.addFilterAfter(new ActiveAccountFilter(userService), ...)` | **保留**：`/community/**` 的 `authenticated()` 只保证「有身份」，禁用账号的拦截由这个过滤器负责 |

### 9.4 两个需要 B 判断的点

1. **`.authenticated()` 还是 `.hasAnyRole("REQUESTER","TECHNICIAN","ADMINISTRATOR")`？**
   计划 §13.2 的原文是「authenticated（任意角色）」。
   当前 `Role` 恰好只有三个值，两者等价；`.authenticated()` 在未来新增角色时**不必改**，
   但也**不会**自动排除新角色。B 决定。
2. **顺序位置。** 补丁把 `/community/**` 放在 `/admin/**` 之后、`anyRequest()` 之前。
   `PathPatternParser` 下 `/community/**` 与既有规则无交集，位置不影响正确性；
   放在最后只是为了「越具体的规则越靠前、兜底规则永远最后」这个可读性约定。

### 9.5 与社区无关但相邻的一项（不由 A 提出）

计划 §13.3 的 **N-3**（`GET /requests/{ticketNumber}/attachments/{attachmentId}`，
E 负责）标注为「需扩规则」，因为现有规则是
`hasAnyRole("REQUESTER","ADMINISTRATOR","TECHNICIAN")`，而 TECH 分支需要「被指派给自己」
这一层。**那属于 C/E 与 B 的接口点，本文档不涉及，仅在此登记以免被误认为遗漏。**

---

## 10. 核对计划时发现的 4 处契约缺陷

这些**不是**建议，是计划内部自相矛盾、必须在 Day 1 或写代码前出结论的地方。
A 不单方面裁定，只负责把它们摆出来。

| # | 位置 | 矛盾 | 影响 | 建议处置 |
|---|---|---|---|---|
| 1 | §12.4 vs §11.3 | `browse` 返回**裸 `List`**，但 §11.3 要求分页并给了 `page`/`size` 上限。裸 List 没有总数，翻页器无法渲染 | 列表页做不出分页；与 `request` 模块的 `Page` 先例不一致；`docs/ui-guide.md` §7 的 `${pagination}` 契约对不上 | 见 §6.3 的三个方案，团队选一个。**选②或③时 UI 指南必须同步改** |
| 2 | §12.3 | `restoreQuestion` / `restoreAnswer` 的「事件」栏写的是 `CommunityContentHiddenEvent`。**恢复内容却发布「内容被隐藏」事件** | E 会发出语义相反的通知 | 恢复不发布该事件，或另定义 `CommunityContentRestoredEvent`。**必须 Day 1 定**，因为 F-7 是冻结接口 |
| 3 | §11.5 vs §29 D-15 | 反重复窗口：§11.5 说「最小间隔**30 秒** + 每日上限 **20 条**」，§29 说「时间窗可配，**默认 2 分钟**」 | `CommunityProperties` 的默认值无法确定；测试写不出确定断言 | 统一成一个数值口径（建议：时间窗取 §29 的 2 分钟，每日上限取 §11.5 的 20 条，二者并不冲突——**但需要团队确认这不是我替他们做的解释**） |
| 4 | §6.6 vs §6.8 / §12.1 | §6.6 把 `Solved` 简写为 `accepted_answer_id IS NOT NULL`（漏 `status='VISIBLE'`）；§12.1 的 `withdrawQuestion` 事件栏为 `—`，但撤回问题会让其下回答不再公开可见 | ①筛选口径不一致会让隐藏但已解决的问题出现在「已解决」列表里；②E 无法通知「你回答的问题被撤回」 | ①按 §3.2 第 3 条统一口径；②若需要该通知，补一个事件并在 §12.5/§12.6 登记 |

---

## 11. 依赖与阻塞（A 的视角）

| 依赖 | 提供方 | 计划冻结时间 | 若缺失的后果 |
|---|---|---|---|
| D-05 / D-09 / D-13 的结论 | 团队（Day 1） | Day 1 | **领域模型与 V17 无法开工**（D-09 改状态、D-13 改表结构） |
| `/community/**` 的授权 | B | Day 1 | 所有社区页面 403；**在授权落地前不得加任何可点击的社区链接** |
| 迁移号 V17 / V18 登记 | C | Day 1 | 不能写迁移文件 |
| `NotificationService` 投递能力 | E | Day 3（事件先发，投递可后到） | 事件可以照发，E 的监听器后补；**不影响 A 的发布点** |
| `AuditService` 写入 | E | Day 3 | 同上 |
| D-10 的结论 | 团队 | Day 1 | 若选②，PR 范围与迁移号都会变 |

> **本次交付对这张表的实际处置（诚实记账，见 §13.3）：**
> - **D-05 / D-09 / D-13 没有拿到团队结论。** A 按本契约的
>   **建议基线**（§1 各条的「若按建议基线执行」）写了领域模型与 V17/V18。
>   这不是「依赖已满足」，而是**在依赖未满足时选了一条可回退的路**：
>   三者都落在「不加状态、不加列、用数据库约束」这一侧，
>   与该侧相比，改选另一侧要动的是**表结构本身**，不是调用方。
>   因此**交付物仍应按「未定决策上的暂定实现」看待**，
>   团队一旦改选，V17/V18 需要新迁移而非修改（历史迁移不可改）。
> - **B 的授权：已落地**（由仓库所有者要求应用，见 §9.2）。
>   这条依赖**已解除**，「接入导航与首页」这半步据此完成。
>   仍需 B 事后审阅：`/community/**` 是否就用 `.authenticated()`，
>   以及 `/admin/community/**` 的六个审核路由是否确认由 `/admin/**` 覆盖即可（§9.4）。
> - **C 的编号登记**：V17/V18 已按本契约落地；`community_reports` 仍缺号（§8.2）。
> - **E 的投递能力**：本阶段不涉及事件发布，见 §13.3 的「未做」清单。

**A 自己先要做的（§17.1 的第一优先级）：** 修好 `docs/ui-guide.md`。
计划原文：「在它修复之前，任何成员照文档写页面都会失败。这是**阻塞四人**的事，必须先做。」
本文档不重复该工作，只声明它已完成的状态由 `docs/ui-guide.md` 自身与测试证据说明。

---

## 12. 本文档的维护

- 改动本文档需要说明：改了哪一条、依据是计划的哪一节或哪一次会议结论。
- 团队给出 D-05/D-09/D-10/D-13/D-15 的结论后，**§1 的「团队结论」栏必须被填上**，
  并把对应的「若按建议基线执行」段落改成「已确定」的陈述。
- 本文档**不**代替计划：冲突时以计划和 Day 1 会议记录为准，并回头修正本文档。
- **§13 是交付记录，不是契约。** 计划更新时先改 §1–§12（契约），
  §13 只在重新交付时更新，并注明「上一次验证」与「本次验证」的时间与结果。
- 注意区分引用对象：本文档里 `§12.4`、`§12.6` 一类指的是**计划**的 §12；
  本节（本文档 §12）只讲怎么维护**本文档**。

---

## 13. 本阶段交付与验证（§17.1 PR-1）

> 本节记录**实际做出来的东西**，与前文「契约」「建议」区分开。
> 前文是「打算怎么做」，本节是「做成了什么、验证到什么程度」。

### 13.1 实际文件

**主代码（`src/main`）**

| 文件 | 作用 |
|---|---|
| `community/domain/CommunityQuestion.java` | 聚合根。`ask` 静态工厂、`edit`、`withdraw`、`requireText` 归一 |
| `community/domain/CommunityAnswer.java` | 回答实体（本阶段只读：详情页展示，写入留给下一阶段） |
| `community/domain/CommunityCategory.java` | 5 值枚举：`HARDWARE` / `SOFTWARE` / `NETWORK` / `PERIPHERAL` / `OTHER` |
| `community/domain/CommunityContentStatus.java` | `VISIBLE` / `HIDDEN` / `WITHDRAWN`（§3.1：**不与 solved 合并**） |
| `community/repository/CommunityQuestionRepository.java` | 可见性过滤 + 搜索 + 计数，`Page` 返回 |
| `community/repository/CommunityAnswerRepository.java` | 按问题 id 取回答，稳定排序 |
| `community/service/CommunityQueryService.java` | 列表 / 详情 / 搜索 / 分页 / 过滤，`likePattern` 做转义 |
| `community/service/CommunityQuestionService.java` | 提问 / 编辑 / 撤回，含反重复与限流 |
| `community/service/CommunityAccessGuard.java` | ACTIVE 身份校验，越权一律 404 |
| `community/controller/CommunityQuestionController.java` | **5 个 GET + 3 个 POST**（列表、我的、提问表单、详情、编辑表单 ｜ 提问、保存编辑、撤回），全部 PRG |
| `community/dto/QuestionFormCommand.java` | 表单命令（提问与编辑共用） |
| `community/dto/QuestionFilter.java` | 列表筛选标签：`LATEST` / `UNANSWERED` / `SOLVED` |
| `community/dto/CommunityQuestionSummaryResponse.java` 等 3 个 | 视图模型（只读） |
| `community/config/CommunityProperties.java` | 反重复窗口与限流阈值，**默认值标注为暂定基线** |
| `resources/db/migration/V17__create_community_questions_and_answers.sql` | 两张表 + CHECK + 索引 + 两个 `UNIQUE` |
| `resources/db/migration/V18__add_community_accepted_answer_constraint.sql` | 复合外键（+ 引用侧的部分索引） |
| `resources/templates/community/{index,question,question-form,mine}.html` | 四个模板，复用 `appbar`/`sidebar`/`pageHeading` |

**测试（`src/test`）**

| 文件 | 用例数 | 验证什么 |
|---|---|---|
| `community/domain/CommunityQuestionTest.java` | 14 | 长度边界、trim、编辑白名单、撤回幂等 |
| `community/repository/CommunityQuestionRepositoryTest.java` | 8 | **JPQL 本身**（`@DataJpaTest`）：可见性过滤、标题+正文搜索、`UNANSWERED` 语义、分页与排序 |
| `community/service/CommunityQueryServiceTest.java` | 13 | 过滤标签、分页钳制、排序、搜索转义、可见性 |
| `community/service/CommunityQuestionServiceTest.java` | 14 | 作者来源、重复/限流、越权 404 |
| `community/service/CommunityAccessGuardTest.java` | 5 | ACTIVE 校验与统一 404 文案 |
| `community/web/CommunityPagesIT.java` | 36 | 真实渲染、伪字段、CSRF、PRG、XSS、分页链接保持过滤（**跑在生产授权链上**） |
| `community/web/CommunityEmptyBoardIT.java` | 3 | 「完全空」与「筛选无结果」两种空态（**跑在生产授权链上**） |
| `community/CommunityMigrationPostgresIT.java` | 1 | 原生 PostgreSQL 约束（**本次未运行**，见 §13.2） |
| `auth/config/SecurityConfigTest.java` | 42 → **63** | **授权回归**：社区路由矩阵（3 角色 × 5 GET，全部 200）、匿名重定向、三个 POST 的 CSRF（见 §9.2） |
| `common/web/HomeControllerTests.java` | 3 → **6** | **首页接入**：三种角色都能看到社区导航链接与首页卡片 |

> **为什么仓库层要单独测：** Service 层用 Mockito 打桩，
> **查不出 JPQL 写错**——方法名拼对、桩返回对，测试就绿了。
> `CommunityQuestionRepositoryTest` 跑在真实 H2 上执行真实查询，
> 它才是「隐藏/撤回的问题确实不出现在公开列表里」「搜索确实同时命中标题和正文」
> 这两条断言的依据。两者都保留：一个测决策，一个测查询。

### 13.2 验证结果（H2 与原生 PostgreSQL 必须分开看）

> **这一节记的是阶段一/二的**上一次验证**（2026-10-06）。阶段三之后已过期：
> 本次为 327 + 82 全绿、两个原生 PG 用例也已跑通，见 §14.5。**
> 下面保留原文，是为了让「哪些结论是当时下的、后来变了」有据可查 ——
> 特别是本节末尾「原生 PostgreSQL 未运行」那一段，它**已经不再成立**。

**H2 —— 已运行，全绿（本次验证：2026-10-06，本机 Windows + Java 21.0.9，`mvn -o verify`）：**

```
mvn -o verify
  surefire   Tests run: 294, Failures: 0, Errors: 0, Skipped: 0
  failsafe   Tests run:  63, Failures: 0, Errors: 0, Skipped: 0
  BUILD SUCCESS
```

其中**社区模块自身**贡献 **54 个单元用例 + 39 个 IT 用例**（另有 1 个原生 PG 用例未运行）；
授权与导航接入另加 **21 个**（`SecurityConfigTest` +21）与 **3 个**（`HomeControllerTests` +3）用例。

**原生 PostgreSQL —— 未运行：**

`CommunityMigrationPostgresIT` **能编译，但没有在本机执行**：
本环境没有可用的 PostgreSQL 凭据（先用 `postgres-it` profile + `TEST_DB_URL` 提供）。
**因此以下三项在真实 PostgreSQL 上属于「未验证」，不能算作通过：**

1. 复合外键 `(accepted_answer_id, id) → community_answers (id, question_id)` 真的拒绝「采纳别人的回答」；
2. `MATCH SIMPLE` + `UNIQUE` 让 27 行 `accepted_answer_id IS NULL` 并存；
3. 三个 `CHECK`（标题长度、`title = btrim(title)`、枚举取值）。

**为什么这三项在 H2 上测不了（这是设计使然，不是遗漏）：**
H2 的表结构由 **JPA 实体**生成（`ddl-auto=create-drop`），**不走 Flyway**。
实体故意不为 `accepted_answer_id` 建关联，所以 H2 上**根本没有那条外键**，
CHECK 约束也只存在于迁移文件里。
在 H2 上断言它们，等于断言「Hibernate 生成了它并不生成的约束」——
会全绿，且毫无意义。
这就是 `CommunityMigrationPostgresIT` 存在的唯一理由，
也是它被列进 failsafe `excludes`、只在 `postgres-it` profile 下运行的原因。

> **待办：** 在具备 PostgreSQL 的环境执行
> `mvn -Ppostgres-it verify`（需 `TEST_DB_URL` / `TEST_DB_USERNAME` / `TEST_DB_PASSWORD`，
> 库名必须以 `_test` 结尾 —— 用例会随机建 schema 并在 finally 里 drop，
> 但仍会先断言这一点）。
> **不要**为了让本机跑通而 `flyway repair` / `clean` / 删库。

### 13.3 未做的部分（明确不做，不是遗漏）

> **本节是阶段一/二的边界记录，已被阶段三的部分交付取代。**
> 阶段三完成了「回答的写入」并把原生 PostgreSQL 用例跑通，
> **本次（2026-10-07）的数字与结论以 §14 为准**；下面的表保留原样以便对照。

| 未做 | 原因 |
|---|---|
| 回答的写入（回答、编辑回答、采纳、撤回回答） | ~~**下一阶段**~~ **已在阶段三完成**，见 §14。本节以下记录的是阶段一/二当时的边界 |
| `community_reports` 表与举报流程 | 不在本阶段范围；编号仍待 C 登记（§8.2）。**阶段三仍未做**，见 §14.6 |
| 事件发布（F-5/F-6/F-7）与通知投递 | 本阶段不涉及；`NotificationService` 等由 E 提供 |
| 报修 / 派单 / 通知投递模块 | 明确不在范围内 |

> **原列在此表的另两项已完成**（授权补丁、接入导航与首页），
> 记录在 §9.1/§9.2 与 §13.4：补丁由仓库所有者要求后应用，
> 随后接入三种角色的导航和首页，并补上了对应的授权与渲染回归测试。

### 13.4 本阶段改动的既有文件（非新增）

| 文件 | 改动 |
|---|---|
| `auth/config/SecurityConfig.java` | **应用了一行授权规则**：`.requestMatchers("/community/**").authenticated()`，附注释（§9.2）。**不是 `permitAll`** |
| `templates/fragments/layout.html` | 共享导航加 Community 入口：管理员的 sidebar rail 一条，其余角色的 appbar 一条（无角色条件） |
| `templates/home.html` | 首页加 Community 卡片，**三种角色都渲染**；只列现存可用的三个目的地，不提供回答/采纳 |
| `pom.xml` | failsafe 的 `excludes` **新增一行** `CommunityMigrationPostgresIT.java`（原生 PG 用例按既有约定 opt-in） |
| `auth/config/SecurityConfigTest.java` | 路由矩阵、匿名重定向、CSRF 三处新增社区用例（42 → 63） |
| `common/web/HomeControllerTests.java` | 新增三角色首页参数化用例（3 → 6） |
| `docs/ui-guide.md` | 第一阶段成果（首页与共享导航只写真实授权的路由） |
| `docs/sprint3/UserA_Community_Implementation_Contract_CN.md` | 本文件：§4、§8.2/§8.5、§9.1/§9.2（补丁**已应用**）、§11、§13 |

**删除的文件：** `src/test/java/com/smartfix/community/web/CommunityTestSecurityConfig.java`
（测试专用授权链，补丁落地后改为直测生产链，见 §9.2）。

**没有改动的：** 任何既有迁移（`V1`–`V9` 一字未动；`V10`–`V16` 本来就不存在，见 §8.3）、
任何 B/D/E 拥有的文件。也**没有**开启 `spring.flyway.out-of-order`（已核对配置文件无此键）。

### 13.5 本阶段由测试抓出的一个真实缺陷

`community/question-form.html` 的**全局错误提示块原本在 `<form>` 之外**，
并且用了 `#fields`。Thymeleaf 的 `#fields` 只在 `th:object` 作用域内可用，
所以提问页（有全局错误时）和编辑页**会直接 `TemplateInputException` 渲染失败**，
而不是仅仅不显示那条提示。

**没有 IT 就不会发现这个：** 纯单元测试不渲染模板，手工点页面时
「无全局错误的路径」一切正常，只有反重复/限流触发时才 500。
修复是把该块移进 `<form th:object>` 内，并加了注释说明
「wrapper 承担 `th:if`、内层承担 `th:replace`，不能放在同一个元素上 ——
`th:replace` 先于 `th:if` 处理」。

### 13.6 反重复与限流的并发保证（§11.5 / D-15，**不夸大**）

`CommunityQuestionService.rejectRepetition` 是**先查后插**，两步之间没有加锁，
所以它**不是**「恰好一次」或「至多 N 条」的保证，
而是通常意义上的**限流**：并发提交**可以**同时通过检查。

- 阈值来自 `CommunityProperties`，**标注为暂定基线** ——
  §11.5 与 §29 D-15 对时间窗的说法不一致（见 §10 缺陷 3），
  A 没有替团队做这个决定。
- 需要严格保证时应改为数据库唯一约束或原子计数，那是团队定下 D-15 之后的事。

---

## 14. 阶段三交付与验证：回答、采纳与「我的回答」

> **与 §13 的关系：** §13 是阶段一/二的交付记录（上一次验证：**2026-10-06**）。
> 本节是阶段三的记录（本次验证：**2026-10-07**），只写「这次多做了什么、验证到什么程度」。
> 契约部分（§1–§12）在阶段三被修正了三处，都是**文档与代码不一致的更正**，不是新设计：
> §7.2（事件字段从建议升为事实）、§7.4（撤回事件的实际口径）、§9.2（授权回归的实测数字）。

### 14.1 本阶段落地的路由（§2 的 A-R8–A-R13，以及 A-R16 的 answers 标签）

| # | 方法 + 路径 | 服务入口 | 越权/非法时的回答 |
|---|---|---|---|
| A-R8 | `POST /community/questions/{questionId}/answers` | `post` | 问题不存在或不可见 → **404**；限流 → 200，重渲染详情页并保留已输入正文 |
| A-R9 | `GET /community/answers/{answerId}/edit` | `editForm` | 非作者 → **404** |
| A-R10 | `POST /community/answers/{answerId}` | `saveEdit` | 非作者 → **404**；已撤回 → 409 语义 |
| A-R11 | `POST /community/answers/{answerId}/withdraw` | `withdraw` | 非作者 → **404**；重复撤回 **幂等**（不改状态、不发事件） |
| A-R12 | `POST /community/questions/{questionId}/answers/{answerId}/accept` | `accept` | 非问题作者、或答案不属于该问题 → **404**；已采纳/问题或回答不可见/自我采纳 → 冲突，以 flash 提示重定向回详情页 |
| A-R13 | `POST /community/questions/{questionId}/acceptance/remove` | `removeAcceptance` | 非作者 → **404**；重复取消 **幂等** |
| A-R16 | `GET /community/mine?tab=answers` | `mine` | 未知 `tab` → **400**（不是静默显示提问） |

三个角色都可以回答、编辑、撤回、采纳（采纳按**问题作者**判定，与角色无关）；
管理员没有「替别人采纳」或「改写别人正文」的能力。授权回归见 §9.2。

### 14.2 事务与锁顺序（**本阶段新增的核心规则**）

- **只有一把锁：问题行。** `CommunityQuestionRepository.findByIdForUpdate`（`SELECT … FOR UPDATE`）。
- **三条需要跨行一致性的路径都在第一步取它**：采纳、取消采纳、撤回回答。
- **撤回回答的顺序值得单独记下来**，因为它的路由只给了 `answerId`：
  1. `findQuestionIdOfOwnAnswer` —— **标量投影**，只查 `question_id`，**不把回答实体加载进持久化上下文**；
  2. `findByIdForUpdate(questionId)` 取锁；
  3. `findById(answerId)` **在锁内**重新加载回答。
  第 1 步必须是标量查询，否则第 3 步会被一级缓存服务，读到的是锁**前**的快照，
  「锁后读」退化成「锁前读」—— 那样锁就白加了。
- **为什么不会死锁：** 整个模块**只有这一把行锁**，且每条路径都先取它。
  一个事务最多持有一把，锁的等待图里不可能出现环 —— 所以「统一锁顺序」在这里
  不需要真的作为一条顺序规则存在（这是设计选择，不是疏忽）。
- **锁不是不变量。**「同一问题只有一个采纳答案」由 `acceptAnswerIfOpen` 的
  **条件 UPDATE 的 affected-rows** 保证：
  `WHERE q.acceptedAnswerId IS NULL AND q.status = VISIBLE AND EXISTS(该问题的、VISIBLE 的回答)`。
  锁只负责另一件事：防止「读到回答 VISIBLE」之后、写入之前被别的事务撤回。
  两者不能互相替代，§14.5 分别验证了它们。
- **取消采纳与撤回不恢复任何东西**：两个状态各自只有一个写入口
  （`acceptAnswerIfOpen` 写、`clearAcceptanceIfPresent` 清）。取消采纳不会恢复内容，
  恢复内容也不会恢复旧的采纳。

### 14.3 D-05（自我采纳）的暂定解读

**按计划 §29 的建议基线实现：禁止自我采纳。团队结论仍未定。**
标注位置：§1 的 D-05 行、`CommunityAnswerService.accept` 的方法注释与常量注释、
详情页 accept 按钮上的条件、以及用例名 `theQuestionAuthorCannotAcceptTheirOwnAnswer`。
改选①时的改动点是**一个服务分支 + 页面上的一个条件**，不动表结构、迁移与事件字段。

### 14.4 详情页与「我的回答」的渲染规则

- **已采纳的回答置顶，且只出现一次。**「置顶」与「其余按时间正序」两条规则在
  被采纳的那条回答上互相冲突，解法是**移动而不是复制** —— 复制会让同一条回答渲染两次，
  而两处渲染可能不一致。
- 其余回答按 `created_at ASC, id ASC` **稳定**正序（同一秒内也有确定顺序）。
- 按钮与端权限一致，且**页面隐藏不是防线**：
  - Edit / Withdraw：`answer.authorId == viewerId && answer.status == VISIBLE`；
  - Accept：`本人问题 && 问题 VISIBLE && 未解决 && 回答 VISIBLE && 回答不是自己写的`；
  - Remove acceptance：`本人问题`。
  每一项在服务层都有对应的重查，页面只是不显示不可用的按钮。
- **回答不可公开时正文根本不进入模型**（不是被 CSS 藏起来），读者看到占位；
  「我的回答」里作者仍能看到**自己的**正文 —— 那是他自己写的东西。
- **「我的回答」里父问题不可读时 `questionTitle` 为 `null`**，页面渲染「Not available」。
  既不给标题，也**不给原因**：说「已撤回」还是「已被管理员隐藏」会让这个列表变成
  一个探测他人内容状态的入口。列表指向问题的链接也只在标题非空时出现。

### 14.5 验证结果（真实数字，H2 与原生 PostgreSQL 分开看）

**H2（`mvn -o verify`，2026-10-07，本机 Windows 11 + Java 21）：**

| 阶段 | 结果 |
|---|---|
| surefire | **Tests run: 327, Failures: 0, Errors: 0, Skipped: 0** |
| failsafe | **Tests run: 82, Failures: 0, Errors: 0, Skipped: 0** |
| 合计 | BUILD SUCCESS |

> 这两行数字是在**修完 §14.9 之后**的最终树上重新跑出来的（`mvn -o -B verify`，02:19），
> 不是修改前的旧数：327 / 82 与修复前一致，说明那个缺陷的修复没有影响其它用例。

社区模块自身的用例：**单元 87**（`CommunityQuestionTest` 14、`CommunityQuestionRepositoryTest` 8、
`CommunityAccessGuardTest` 5、`CommunityQuestionServiceTest` 14、`CommunityQueryServiceTest` **20**、
`CommunityAnswerServiceTest` **26**）+ **IT 58**（`CommunityPagesIT` **55**、`CommunityEmptyBoardIT` 3）。
（对照 §13.2 的上一次：294 + 63 / 社区 54 + 39。）

**原生 PostgreSQL 16.14（`smartfix-db` 容器，`localhost:5433/smartfix_test`，
在随机 schema 里跑 Flyway V1–V18 后 drop）：**

| 用例 | 结果 |
|---|---|
| `CommunityMigrationPostgresIT`（约束） | **1/1 通过** |
| `CommunityAnswerConcurrencyPostgresIT`（并发） | **2/2 通过** |

命令（凭据来自容器，库名以 `_test` 结尾是硬要求）：

```
TEST_DB_URL=jdbc:postgresql://localhost:5433/smartfix_test \
TEST_DB_USERNAME=smartfix TEST_DB_PASSWORD=smartfix \
mvn -o -B -Ppostgres-it verify -Dtest=NoSuchUnitTest \
    -Dsurefire.failIfNoSpecifiedTests=false \
    -Dit.test=CommunityMigrationPostgresIT,CommunityAnswerConcurrencyPostgresIT
```

**并发用例做了什么**（每个线程走真实事务代理，各自从连接池取连接、各自开事务，
用 `CyclicBarrier` 同时放行 —— 不是串行调用，也不是 Mockito）：

1. `fourSimultaneousAcceptancesLeaveExactlyOneAcceptedAnswer`：4 条不同回答被同时采纳 →
   **恰好 1 个成功**、3 个 `BusinessConflictException`；数据库里 `accepted_answer_id`
   等于胜者的 id；`AFTER_COMMIT` 监听器只记录到 **1** 条 `CommunityAnswerAcceptedEvent`
   （败者一条也没发）。
2. `aWithdrawalRacingAnAcceptanceNeverLeavesAWithdrawnAnswerAccepted`：采纳与撤回同时发生 →
   撤回**总是**成功；采纳要么先拿到锁而成功（随后在同一事务里被清除），
   要么以可见性冲突被拒。**两种顺序留下的状态完全相同**：回答 `WITHDRAWN`、
   问题 `accepted_answer_id IS NULL`、且全库不存在「已采纳但不可见」的回答。

**这两个用例是能被证伪的（已实测，不是自证）：** 把 `acceptAnswerIfOpen` 里的
`AND q.acceptedAnswerId IS NULL` 临时删掉后，用例 1 立即失败并给出
`[exactly one of the four acceptances may be recorded] Expected size: 1 but was: 4`；
恢复后重新全绿。也就是说「只有一个成功」这条断言确实由**条件更新**提供，
而不是由测试的串行性提供 —— 这正是任务要求不能省略的那一步。

**本阶段没有做的验证（必须写清楚，不能算通过）：**

- **没有多进程/多实例验证。** 行锁与条件更新是在**一个 JVM、多连接**下验证的；
  跨实例在这个层面与单实例等价（锁在数据库上），但本阶段**没有真的起第二个进程**去证明它。
- **没有压力测试。** 4 个并发者足以证伪「不加条件」，不足以说明高并发下的吞吐与锁等待。
- 报修、派单、审核模块的并发不在本阶段范围内。

### 14.6 本阶段明确未做的部分

| 未做 | 原因 |
|---|---|
| 举报与审核（A-R14/15、A-R17–A-R22） | 不在本阶段范围；`community_reports` 仍缺迁移号（§8.2） |
| 通知投递 | E 的模块尚不存在，见 §14.7 |
| 「管理员可查看被隐藏回答」的查看例外 | 计划要求，但审核模块未实现，**当前没有任何内容会处于 `HIDDEN`**，该例外随审核一起做 |
| 对 `HIDDEN` 内容的端到端用例 | 同上：无法制造一个真实到达 `HIDDEN` 的记录（只有审核能改），用例只能直接改库造状态，那验证的是测试自己。服务层对 `HIDDEN` 的规则有单元用例覆盖 |

### 14.7 E 的接线状态（**诚实记账**）

- 三个事件（F-5/F-6/F-7）在**业务事务内**发布，消费端使用 `AFTER_COMMIT`（§7.3）。
  字段以 §7.2 的落地版本为准。
- **本阶段没有实现任何通知中心，也没有声称通知已送达。** 仓库里**不存在**
  `NotificationService`、通知表、通知页面或投递逻辑；E 的模块未就绪时保留的是
  「真实事件发布 + 交接契约」，不是替代品。
- **仓库里唯一的监听器是测试用的**：`CommunityAnswerConcurrencyPostgresIT` 里的
  `AcceptanceRecorder`（`@TransactionalEventListener(AFTER_COMMIT)`）。
  它只证明「事件在提交后按预期出现」和「被拒的采纳一条都不发」，不是通知功能。
- **E 必须先解决的一件事：** F-5/F-6 **不带 `questionTitle`**（§7.2 差异 1）。
  要给通知正文加标题，只能改事件字段（文档先行变更）或新增反向依赖，二者都需要团队决定。
- 发布点覆盖：`postAnswer`（F-5）、`acceptAnswer`（F-6）、`withdrawAnswer`（F-7，口径见 §7.4.1）。
  `removeAcceptance` 与 `withdrawQuestion` 按计划**不发布事件**。

### 14.8 双账号手工检查步骤（网页）

**前置：** 开发库 `smartfix` 已由 Flyway 迁到 V18（本机 `smartfix-db` 容器已完成，
`flyway_schema_history` 到 18）。测试库 `smartfix_test` 的 schema 由集成测试自建自删，不用来手工验证。

启动（8080/8081 在本机已被占用，用 8082；`DB_URL` 指向容器的 5433）：

```
cd C:\Users\zhour\smartfix
set DB_URL=jdbc:postgresql://localhost:5433/smartfix
set DB_USERNAME=smartfix
set DB_PASSWORD=smartfix
mvn -o -B spring-boot:run -Dspring-boot.run.arguments=--server.port=8082
```

两个账号：用你已知密码的账号登录（库里现有 `root.admin`/`admin`/`uxreview`（管理员）、
`user1`/`casey`/`dana`（REQUESTER））。若都不记得密码，用其中一个管理员登录后到
**/admin/users** 新建两个账号：`check.asker`（REQUESTER）与 `check.answerer`（TECHNICIAN），
或只用一个浏览器 + 一个隐私窗口开两个会话。

建议顺序（每一步都写明**应该看到什么**）：

1. **提问**：以 `check.asker` 登录 → 社区 → Ask a question → 填标题/正文/主题 → 提交。
   期望：跳回详情页，问题正文按纯文本显示。
2. **回答**：**隐私窗口**以 `check.answerer` 登录 → 打开同一问题 URL →
   期望：能看到正文，底部有「Post answer」输入框（因为问题是 VISIBLE）。
   提交一条回答。期望：跳回详情页，回答出现在提问下方（时间正序）。
3. **越权（关键）**：仍是 `check.answerer`，期望**页面上没有** Edit/Withdraw 按钮（回答不是他的？
   不 —— 这条是他的，所以应**有** Edit/Withdraw），并且**没有**「Accept this answer」按钮。
   手工访问 `GET /community/questions/{qid}/answers/{aid}/edit` 用自己的 id 应能打开；
   用**别人的** aid 应得到 **404 页面**（不是 403）。
4. **采纳**：切回 `check.asker` → 详情页 → 对第二个人那条回答点「Accept this answer」。
   期望：跳回详情页；该回答**置顶**（其余按时间正序）；出现「Remove acceptance」；
   其他回答上的 Accept 按钮消失。
5. **幂等与冲突**：用浏览器后退 + 再次提交采纳（或直接对同一问题第二个人另一条回答点 Accept）。
   期望：回到详情页并显示「That answer was not accepted …」，**不是**错误页。
6. **自我采纳（D-05 暂定）**：以 `check.asker` 在自己的问题上发一条回答，
   期望：那条回答上**没有** Accept 按钮；即便手工构造 URL 提交，也得到冲突提示而不是成功。
7. **撤回已采纳的回答**：以 `check.answerer` 撤回自己那条**已被采纳**的回答。
   期望：跳回「我的回答」标签页；该回答状态显示 Withdrawn；
   再以 `check.asker` 打开问题，期望问题**已恢复未解决**（没有置顶回答、Accept 按钮回来）。
8. **我的回答与父问题可见性**：仍以 `check.answerer` 打开 `/community/mine?tab=ANSWERS`，
   （**大写**：`tab` 绑定到枚举，按常量名匹配；`?tab=answers` 会 400 —— 这正是 §14.9 那个缺陷）
   期望看到回答列表；若父问题被 `check.asker` 撤回，该行的问题列应显示 **Not available**，
   且**不显示**问题标题。
9. **XSS**：发一条正文为 `<script>alert(1)</script>` 的回答。
   期望：页面上**原样显示这段文字**，不弹窗、不执行（`th:text`，禁用 `th:utext`）。
10. **CSRF**：任何一个写操作去掉 `_csrf`（例如用 curl 直接 POST）应得到 403。

> 上面这些步骤里，第 3、6、7、8 条是**服务端规则**，页面只是不显示不可用的按钮 ——
> 所以第 3、6 条特意要求手工构造 URL，验证的是服务端而不是按钮。

### 14.9 本阶段抓出的一个真实缺陷（阶段一/二遗留，**已修**）

`CommunityAnswerController.withdraw` 的 PRG 跳转写的是字面量
`redirect:/community/mine?tab=answers`，而 `tab` 参数绑定到枚举 `MineTab`，
**枚举按常量名匹配、区分大小写**，所以 `answers` 匹配不到 `ANSWERS`：

> **撤回自己的回答后，作者被送到的是一个 400 错误页。** 不是「少了个高亮」，是功能坏了。

**为什么之前全绿还是漏了：** 页面上的每一个链接都由 Thymeleaf 从枚举生成
（`@{/community/mine(tab='ANSWERS')}`），永远是对的；只有**手写的**那一条 query string 会错。
而当时的用例只断言了 `redirectedUrl("...tab=answers")` —— **检查跳转指向哪里，
不等于检查它能不能用**；MockMvc 默认不跟随跳转，所以没有任何一条用例真的打开过那个地址。

**修复：** 跳转改写成 `tab=ANSWERS`（统一为常量名，与页面链接、与 `category`/`filter`
参数的写法一致），并修正 `MineTab` 的文档注释（它原文写的是 `?tab=answers`，是错的）。
**保留的回归：** 该用例现在在断言跳转地址之后**再打开一次**
`GET /community/mine?tab=ANSWERS` 并期望 200 —— 有这一行，同一个错法不可能再漏过去。
实测：修之前该断言 `Status expected:<200> but was:<400>`，修之后该用例通过，`CommunityPagesIT` 55/55
（最终树上的完整 `verify` 与原生 PG 的 3 个用例已重跑，见 §14.5）。

### 14.10 本阶段改动的文件

**新增（主代码）**

| 文件 | 作用 |
|---|---|
| `community/controller/CommunityAnswerController.java` | A-R8–A-R13 六个路由 |
| `community/controller/CommunityDetailPageModel.java` | 详情页的公共装配（问题页与回答写回失败后的重渲染共用一份，避免两处渲染漂移） |
| `community/service/CommunityAnswerService.java` | 回答的写路径 + 采纳/取消采纳，含锁顺序与条件更新 |
| `community/domain/CommunityContentType.java` | `QUESTION` / `ANSWER`，给 F-7 用 |
| `community/dto/AnswerFormCommand.java`、`OwnAnswerResponse.java`、`MyAnswerResponse.java`、`MineTab.java` | 回答表单命令与只读视图模型 |
| `community/event/{CommunityAnswerCreatedEvent,CommunityAnswerAcceptedEvent,CommunityContentHiddenEvent}.java` | F-5 / F-6 / F-7 |
| `resources/templates/community/answer-form.html` | 回答编辑页 |

**修改（主代码）**

| 文件 | 改动 |
|---|---|
| `community/controller/CommunityQuestionController.java` | `DETAIL_VIEW` 降为包可见并与回答控制器共用；`mine()` 增加 `tab` 参数 |
| `community/domain/CommunityAnswer.java` | `post` / `edit` / `withdraw` 与状态查询 |
| `community/repository/CommunityQuestionRepository.java` | 新增 `findByIdForUpdate`、`acceptAnswerIfOpen`、`clearAcceptanceIfPresent` |
| `community/repository/CommunityAnswerRepository.java` | 新增 `findByAuthorId`、`findQuestionIdOfOwnAnswer`（标量投影） |
| `community/service/CommunityQueryService.java` | 新增 `findOwnAnswer`、`listMyAnswers`；详情页回答改为**采纳置顶 + 时间正序** |
| `resources/templates/community/question.html` | 回答列表 + 作者/采纳按钮 + 回答输入框 + 采纳被拒提示 |
| `resources/templates/community/mine.html` | 新增「我的回答」标签页（两个 URL，不是前端状态） |
| `resources/templates/home.html`、`community/index.html` | 「My answers」入口与注释更新 |

**缺陷修复（§14.9）**

| 文件 | 改动 |
|---|---|
| `community/controller/CommunityAnswerController.java` | `withdraw` 的跳转由 `?tab=answers` 改为 `?tab=ANSWERS`（枚举按常量名绑定，小写是 400） |
| `community/dto/MineTab.java` | 文档注释原文写的是 `?tab=answers`（错的），改为常量名并写明原因 |
| `community/web/CommunityPagesIT.java` | `withdrawingTheAcceptedAnswerOpensTheQuestionAgainInTheSameRequest` 增加一次**跟随跳转**的 `GET`，把「跳转指向哪里」升级为「跳转能不能打开」 |

**测试**

| 文件 | 用例数 | 验证什么 |
|---|---|---|
| `community/service/CommunityAnswerServiceTest.java` | 26（新） | 作者来源、越权 404、限流、幂等、**锁顺序**（`InOrder`）、`affected == 0` → 冲突且**不发事件**、自我采纳 |
| `community/service/CommunityQueryServiceTest.java` | 13 → 20 | 采纳置顶且只出现一次、无采纳时保持正序、我的回答的父问题可见性与一次查询 |
| `community/web/CommunityPagesIT.java` | 36 → 55 | 回答渲染与 XSS、采纳置顶、越权 404、取消采纳幂等与拒绝、撤回已采纳回答、我的回答标签页 |
| `community/CommunityAnswerConcurrencyPostgresIT.java` | 2（新，原生 PG） | §14.5 的两个并发不变量 |
| `auth/config/SecurityConfigTest.java` | 42 → 51 | 社区写入的角色与 CSRF（见 §9.2 的更正） |
| `pom.xml` | — | failsafe `excludes` 增加一行 `CommunityAnswerConcurrencyPostgresIT.java` |

**没有改动的：** `V1`–`V9`、`V17`/`V18` 一字未动（本阶段不需要新迁移：
所有变更都落在已有两表与其约束内）；没有开启 `spring.flyway.out-of-order`；
没有 `repair` / `clean` / 删库；没有提交、没有推送、没有部署。

---

## 15. 范围调整：Requester 自助注册 + 三种角色的登录入口

本节是一次**用户明确批准的范围调整**（原文案：Sprint 3 排除公开注册）。它记录的是
变更本身，不回写 §1–§14 的历史计划：§14 描述的社区功能与本次改动共存于同一棵代码树上，
本次是增量，没有回退也没有重建任何已完成的部分。

### 15.1 改了什么，以及对什么说了不

**新增：** 匿名可访问的 `GET /register` + `POST /register`；登录页把「以什么身份登录」
做成三个明确入口（Requester / Technician / Administrator），只有 Requester 一处给出注册链接。

**明确不做：** 验证码、邮件服务、找回密码、新的身份体系、第三方登录；**不新增
`ENGINEER` 角色** —— 界面上写「Technician」，后端枚举仍然是 `TECHNICIAN`，
`Role` 三个常量一字未改。

### 15.2 新增路由与权限

| 路由 | 权限 | CSRF |
|---|---|---|
| `GET /register` | `permitAll`（`HttpMethod.GET`，路径精确匹配） | 不适用 |
| `POST /register` | `permitAll`（`HttpMethod.POST`，路径精确匹配） | **必需**，页面带 token |

- 写的是**具名路由**而不是前缀：`/register/`、`/register/anything` 都不在这次开放之内
  （`SecurityConfigTest.nothingIsOpenedAlongWithTheSignUpRoute` 断言它们对匿名者仍是跳登录）。
- `/admin/**`、`/community/**` 与其余业务路由**没有**因为本次改动而开放匿名；
  `anyRequest().denyAll()` 保持原样。
- 未动的：CSRF（所有写入照旧）、session fixation（`changeSessionId`）、
  禁用账号检查（`ActiveAccountFilter`）、logout。`requestCache` 沿用项目现有配置
  （当前是 `disable()`），本次没有改动它。

### 15.3 账号初始状态

| 项 | 值 | 由谁决定 |
|---|---|---|
| 角色 | `REQUESTER` | `UserService.registerRequester`（**服务端固定**，表单里没有这个字段） |
| 状态 | `ACTIVE` | `User.create`（注册路径无从提交状态） |
| `securityVersion` | `0` | `User.create` |

**无邮件验证，因此注册成功后账号立即可用。** 这一点必须写在文档里而不是只写在代码里：
它意味着任何能访问网站的人都能得到一个立刻生效的账号。上线前的补强项见 §15.11。

### 15.4 身份选择规则（登录页）

一个登录页、一套 Spring Security 流程，三个入口只切换说明文字、选中态与「期望的身份」，
**不复制三套认证逻辑**。默认选中 Requester。

| 请求里的 `accountType` | 结果 |
|---|---|
| 缺失或全空白 | **不检查**，与改动前完全一样地登录 |
| 等于账号的真实角色（大小写不敏感） | 正常登录 |
| 不等于账号真实角色 | 拒绝 |
| 不是一个角色名（例如 `SUPERUSER`） | **拒绝**（不能当作「没提交」） |

两条硬规则：

1. **身份选择只表达登录意图，权限只来自数据库。** `accountType` 不参与任何授权：
   会话里的 `ROLE_*` 来自 `SmartFixUserDetails`，也就是持久化的 `Role`。
   表单里再塞 `role=ADMINISTRATOR` 也只是被忽略的未知参数
   （`AuthenticationFlowIT.aRoleParameterOnTheSignInFormGrantsNothing`）。
2. **不匹配 = 登录失败，不是「登录后用前端提示假装拒绝」。** 检查放在
   `SelectedAccountTypeAuthenticationProvider`，**在密码校验成功之后**：
   - 放在密码之前会把登录表单变成角色探测器（谁都能问出「这个账号是技师吗」）；
   - 放在成功之后（例如成功处理器里）会先留下一个已认证会话再反悔。
   - 现在的效果：不建立认证、不留下可用会话、不自动改角色，跳转
     `/login?error=type`，文案为
     "Unable to sign in with the selected account type. Check your details or select another type." ——
     **不透露账号的真实角色**（`SelectedAccountTypeTest.theRefusalMessageNamesNoRole` 断言消息里
     不出现任何 `Role` 常量名）。
   - 失败原因被区分开只是为了文案：其余失败仍然落在原来的 `/login?error`。
   - **（第二轮修订，见 §16.2）** 这两个地址现在会带回访客选中的那一段，形如
     `/login?error=type&accountType=TECHNICIAN`、`/login?error&accountType=REQUESTER`；
     追加的值只可能是三个 `Role` 常量名之一（提交的原文被解析后丢弃），用途只有一个：
     让重定向后的页面把访客选过的那一段重新勾上。

**为什么异常类型是 `AccountStatusException` 而不是 `BadCredentialsException`。**
这是本次实测抓出来的问题，值得写下来：`ProviderManager` 对普通
`AuthenticationException` 只记为「最后一个失败」然后继续问下一个 provider，
而表单登录用的局部认证管理器的父级正是 Spring Boot 用 `SmartFixUserDetailsService`
建出来的全局管理器 —— 父级不知道有「身份选择」这回事，会把同一份凭据**认证成功**。
用 `BadCredentialsException` 时，拒绝被吞掉、用户反而登录成功（实测跳转是 `/`）。
`AccountStatusException` 是框架里「这个账号不许登录」的信号，会被立即重抛，
不再询问其它 provider —— 禁用账号走的也是同一条路（`DisabledException` 就是它的子类）。

### 15.5 受保护字段

`RegistrationCommand` 只有四个字段：`username`、`displayName`、`password`、`confirmPassword`。
**没有** `role`、`accountStatus`、`securityVersion`、`id`、`passwordHash`、时间戳。
所以「构造 `role=ADMINISTRATOR` 也不能提权」不是靠过滤实现的，而是**根本没有绑定目标**。
`RegistrationFlowIT.protectedFieldsOnTheFormCannotBeSubmitted` 断言落库结果是
`REQUESTER` / `ACTIVE` / `securityVersion = 0`，并且用 Administrator 入口登录会被拒。

注册路径与管理员创建路径共用 `UserService` 的私有 `createAccount`：同一套用户名规范化、
同一条显示名规则、同一个 `PasswordEncoder`、同一份 `PasswordPolicy`、同一套唯一冲突翻译。

### 15.6 用户名与密码规则

- 用户名沿用项目**既有**规则 `^[a-z0-9._-]{3,50}$`，入库前 trim + 小写；
  大小写不敏感（`Alice` 存成 `alice`，之后用任意大小写都能登录）。
- 密码复用 `PasswordEncoder`（BCrypt）与 `PasswordPolicy`（≥12 字符、含字母与数字、
  ≤72 UTF-8 字节）。页面上的提示数字从 `PasswordPolicy` 取，不是抄一遍。
- **密码不 trim、不进日志、不回显**：模板不对两个密码用 `th:field`，
  且 `RegistrationController.renderForm` 在重新渲染之前调用
  `RegistrationCommand.clearSecrets()` —— 让「不回显」是数据层的性质，而不是标记的巧合。
- 确认密码不一致 → 字段级 "Passwords do not match."；该规则在服务层再查一次。

### 15.7 重复用户名与并发兜底

- `users` 表的唯一约束**已经存在**（`V2__create_users.sql` 的 `uk_users_username`），
  所以本次**不需要新迁移**：`V1`–`V18` 一字未动，没有 `repair` / `clean` / 删库，
  也没有开启 `spring.flyway.out-of-order`。
- `existsByUsername` 只是**友好预检**（给出一句人话），并发下不可信；
  真正的兜底是唯一约束：`saveAndFlush` 抛 `DataIntegrityViolationException`
  → 翻译成 `BusinessConflictException` → 409 + 字段级提示，
  **不返回原始 SQL、约束名或 500**。
- 并发本身有原生 PostgreSQL 用例证明（§15.10 第 2 条）。

### 15.8 需要 B 复核的变更

| 位置 | 变更 | 复核要点 |
|---|---|---|
| `auth/config/SecurityConfig.java` | provider 换成 `SelectedAccountTypeAuthenticationProvider` | 仍只做原本的密码/状态校验，附加的一步在密码成功之后 |
| 同上 | 新增 `GET /register`、`POST /register` 的 `permitAll` | 具名路由；`/admin/**`、`/community/**`、`anyRequest().denyAll()` 未动 |
| 同上 | `formLogin` 增加 `authenticationDetailsSource(SelectedAccountType::new)` | 选择以 `WebAuthenticationDetails` 承载，不是凭据 |
| 同上 | `formLogin` 增加 `failureHandler(new SelectedAccountTypeFailureHandler())`，并**删掉 `.failureUrl("/login?error")`** | `failureUrl(...)` 会**覆盖** handler（它内部就是 `failureHandler(new SimpleUrlAuthenticationFailureHandler(url))`）；handler 自己的默认地址就是 `/login?error`，其余失败行为不变 |
| `auth/security/SelectedAccountType.java` | 新增 | 三态判定；字段名 `accountType`（故意不叫 `role`） |
| `auth/security/SelectedAccountTypeMismatchException.java` | 新增 | 继承 `AccountStatusException`，理由见 §15.4 |
| `auth/security/SelectedAccountTypeFailureHandler.java` | 新增 | 只在 `instanceof` 时改跳转地址，其余交回父类 |
| `auth/controller/RegistrationController.java` | 新增 | 匿名写入路由；不自动登录；失败重新渲染不吐栈 |
| `auth/controller/CsrfTokens.java` | 新增（从 `LoginController` 抽出） | 纯搬运，行为不变；登录页与注册页共用 |
| `user/dto/RegistrationCommand.java` | 新增 | 无受保护字段（§15.5） |
| `user/service/UserService.java` | 新增 `registerRequester` | 角色在服务端固定；与管理员路径共用 `createAccount` |
| `templates/login.html`、`templates/register.html`、`static/css/site.css` | 三入口 + 注册页 + `.account-type` 组件 | 原生 `fieldset` + radio，键盘可用 |

**没有只留补丁不接通：** 上述每一项都有端到端用例覆盖，`/register` 真的能被匿名打开并被提交
（§15.9 的 `RegistrationFlowIT`），身份选择真的能拒绝不匹配的登录（§15.9 的
`AuthenticationFlowIT`），不是只有单元测试。

### 15.9 验证结果（真实数字，本轮实测）

| 用例集 | 数量 | 说明 |
|---|---|---|
| `auth/RegistrationFlowIT.java` | **11（新）** | H2 + 真实 MockMvc：匿名 GET 成功；无 CSRF 的 POST 403 且不落库；正常注册后跟随跳转能打开登录页并显示成功文案；落库角色恒为 REQUESTER、状态 ACTIVE、`securityVersion=0`、哈希 `$2` 开头；`role`/`accountStatus`/`securityVersion`/`id` 注入全部无效；重复用户名 409 且页面无 SQL/约束名/异常类名；非法用户名、弱密码、确认密码不一致各自 400 且文案正确；被拒后保留非敏感输入、两个密码都不回显；大写用户名被规范化 |
| `auth/AuthenticationFlowIT.java` | 14 → **23** | 三种角色各自用对应入口登录成功；身份不匹配 → `/login?error=type`、`unauthenticated()`、会话不可用、页面显示专用文案且不显示通用文案；无法识别的取值被拒；不带字段仍可登录；禁用账号即使入口正确也被拒；表单里加 `role=` 不授予任何权限；登录页恰好三个 radio、只 `checked` 一次、只有 Requester 处有注册链接 |
| `auth/config/SecurityConfigTest.java` | 51 → **53** | `GET /register` 匿名 200 且带 `_csrf`；`POST /register` 无 CSRF 403；`/register/`、`/register/anything`、`/admin/users` 对匿名者仍跳登录 |
| `auth/security/SelectedAccountTypeTest.java` | 8（上一轮新增） | 三态判定、拼写容忍、字段名、拒绝消息不含角色名 |
| `auth/RegistrationConcurrencyPostgresIT.java` | **2（新，原生 PG，opt-in）** | 见 §15.10 |

**完整 `verify`（H2，离线）：** `mvn -o -B verify` → **BUILD SUCCESS**，用时 2 分 35 秒，
surefire **337/337**、failsafe **102/102**，Failures 0、Errors 0、Skipped 0（合计 439）。
failsafe 中与本次相关的四个：`AuthenticationFlowIT` 23、`RegistrationFlowIT` 11、
`CommunityPagesIT` 55（未回归）、`RequestPagesRenderingIT` 10（未回归）。
**原生 PostgreSQL：** `RegistrationConcurrencyPostgresIT` 2/2 通过
（`TEST_DB_URL=jdbc:postgresql://localhost:5433/smartfix_test`，`smartfix-db` 容器 5433 端口）。
原生 PG 用例在默认 `verify` 里被 `pom.xml` 的 failsafe `excludes` 排除（需 `postgres-it` profile +
环境变量），所以上面那个 439 是 **H2 树上的数字**，PG 的 2 个单独列出，不混算。

### 15.10 本轮抓出的三个真实缺陷（都是「新功能一上线就是坏的」）

1. **`.failureUrl("/login?error")` 覆盖了自定义失败处理器。**
   `AbstractAuthenticationFilterConfigurer.failureUrl()` 内部就是
   `failureHandler(new SimpleUrlAuthenticationFailureHandler(url))`，写在 `failureHandler(...)`
   之后会把前者整个换掉。症状很有欺骗性：身份不匹配**确实被拒绝**了（没有会话，
   不匹配时不再是登录成功），但跳转退化成 `/login?error`，页面显示通用文案 ——
   专用文案成了死代码。修复：删掉 `failureUrl(...)`，由 handler 自己的默认值承担。
   `AuthenticationFlowIT.choosingAnAccountTypeThatIsNotTheAccountsOwnRefusesTheSignIn`
   现在同时断言「跳转是 `/login?error=type`」与「页面不出现通用文案」，两半都覆盖。

2. **拒绝类型选错，导致拒绝被父认证管理器吞掉。** 详见 §15.4 末尾。
   用 `BadCredentialsException` 时 `AuthenticationFlowIT` 的失败断言实测为
   `Redirected URL expected:</login?error=type> but was:</>` —— 也就是说行为是**登录成功**。

3. **注册页的模板本身在 GET 时就解析失败（500）。**
   `#fields.hasGlobalErrors()` / `#fields.globalErrors()` 必须写在建立了表单上下文的
   `<form th:object="...">` **内部**；写在 form 之前，Thymeleaf 抛
   "Could not bind form errors using expression \"global\""，整个模板渲染失败 ——
   连一次正常的 GET 都打不开。已把那段提示移进 form 内作为第一个子元素。
   同一次运行还暴露了登录页的一处：`th:if="${param.error and param.error[0] != 'type'}"`，
   `param.error` 缺省是 `null` 而不是空数组，SpEL 的 `and` 不接受把 `null` 当布尔
   （`EL1001E: Type conversion problem, cannot convert from null to boolean`），
   于是**正常打开登录页**就会 500。改成 `param.error != null and ...`。

**另外记录一个既有行为（不是本次引入，故未改字符串）：** `@Pattern` 的用户名提示消息里
含单引号（`'.', '_' or '-'`），该消息在到达页面之前会经过 `java.text.MessageFormat`，
而 MessageFormat 把单引号当引用标记吃掉，于是页面上显示成
`digits, ., _ or -`。同一条消息也被管理员创建账号的表单使用，所以本次**没有改这条
共享字符串**，只在测试注释与本文件中记录；字段下方的提示（模板文本，不过 MessageFormat）
是完整的。

### 15.11 上线前尚未具备的防滥用措施（诚实记账）

- **没有验证码、没有速率限制、没有邮箱验证。** 因此任何匿名者可无限次创建立即可用的
  `ACTIVE` 账号，这是本次范围里最大的缺口。
- `RegistrationCommand` 的字段长度上限只能挡住超大请求体，不构成限流。
- 未实现、也未声称实现的补强方向：按 IP 的速率限制 / 验证码 / 邮箱验证 + `PENDING`
  初始状态（后者需要新状态与新迁移，本次按「优先沿用既有 schema」的要求未做）。

### 15.12 明确**没有**执行的检查

- **没有浏览器能力**：1440 / 1280 / 390 宽度的视觉检查**未执行**，因此移动端布局与
  键盘操作的**实机**表现未经确认。已做的是模板渲染断言（HTML 字符串层面）与语义选择
  （原生 `fieldset` + radio，天然支持方向键、`legend` 会被读屏播报）—— 这不等于视觉检查，
  不能把 MockMvc 的结果当成浏览器验收。
- 未做屏幕阅读器 / 真机键盘验证。
- 未做部署、推送、提交。

### 15.13 网站验收步骤（人工，约 3 分钟）

> **第二轮已修订本节第 1、6 步**：身份选择的交互与注册入口的位置都变了，按下面这套步骤
> 打开现在的页面会失败。当前有效的步骤见 **§16.6**，本节保留为第一轮交付时的记录。

1. 打开 `/login`：应看到 **"Sign in as"** 三个选项，Requester 默认选中，只有它下面有
   "Create a requester account" 链接，另外两个写 "Accounts are created by an administrator."
2. 点注册链接 → `/register`，四个字段都有标签与提示，页脚有 "Already have an account? Sign in"。
3. 故意两次密码不一致提交 → 字段级 "Passwords do not match."，密码框清空、用户名与显示名保留。
4. 改成一致后提交 → 回到 `/login` 并显示 **"Account created. Sign in to continue."**，
   且 Requester 仍为默认选中（**没有自动登录**）。
5. 用 Requester 入口登录 → 能开 `/home`、`/requests/new`、`/community`；
   手动访问 `/admin/users` 得到 403 页面。
6. 退出；用同一账号但选 **Technician** 入口登录 → 显示
   "Unable to sign in with the selected account type. Check your details or select another type."，
   且**没有被登录**（再次访问 `/home` 会跳回登录页）。
7. 再注册一次同名用户 → 友好提示 "An account with this username already exists."，
   页面上看不到 SQL 或约束名。
8. 用管理员登录 `/admin/users/new` → 仍然可以创建 Technician / Administrator 账号
   （公开注册只覆盖 Requester）。

### 15.14 本阶段改动的文件

**新增（主代码）**

| 文件 | 作用 |
|---|---|
| `auth/security/SelectedAccountType.java` | 登录页选择以请求 details 承载；三态判定 |
| `auth/security/SelectedAccountTypeAuthenticationProvider.java` | 密码成功之后追加的一步检查 |
| `auth/security/SelectedAccountTypeMismatchException.java` | 拒绝信号（`AccountStatusException`） |
| `auth/security/SelectedAccountTypeFailureHandler.java` | 把「类型不符」与其它失败区分到两个地址 |
| `auth/controller/RegistrationController.java` | `GET/POST /register`，POST-Redirect-GET |
| `auth/controller/CsrfTokens.java` | 从 `LoginController` 抽出的 token 预解析（两个匿名页面共用） |
| `user/dto/RegistrationCommand.java` | 自助注册表单，只有四个字段 |
| `resources/templates/register.html` | 注册页，与登录页同构 |

**修改（主代码）**

| 文件 | 改动 |
|---|---|
| `auth/config/SecurityConfig.java` | provider、两条 permitAll、details source、failure handler（并删除 `.failureUrl(...)`） |
| `auth/controller/LoginController.java` | 改用 `CsrfTokens.resolve`；向模型加 `accountTypeField` |
| `user/service/UserService.java` | 新增 `registerRequester`（角色服务端固定） |
| `resources/templates/login.html` | 三个身份入口 + 成功/类型不符两条反馈分支 + 页脚文案 |
| `resources/static/css/site.css` | `.account-type` 组件（`:has()` 增强并带降级说明） |

**测试**

| 文件 | 用例数 | 验证什么 |
|---|---|---|
| `auth/RegistrationFlowIT.java` | 11（新） | §15.9 |
| `auth/RegistrationConcurrencyPostgresIT.java` | 2（新，原生 PG） | 4 个线程同时注册同一用户名 → 恰好 1 个成功、3 个 `BusinessConflictException`（**不是** `DataIntegrityViolationException`）、表中恰好 1 行；以及绕过服务层直接用 JDBC 插重复行被 `23505` 拒绝、约束 `uk_users_username` 确实存在于 Flyway 建出的 schema 上 |
| `auth/AuthenticationFlowIT.java` | 14 → 23 | §15.9 |
| `auth/config/SecurityConfigTest.java` | 51 → 53 | §15.9 |
| `test/resources/db/auth-test-schema.sql` | — | 注释更新为「两个 IT 各自一个库共用此夹具」 |
| `pom.xml` | — | failsafe `excludes` 增加 `RegistrationConcurrencyPostgresIT.java` |



---

## 16. 第二轮：登录页身份选择交互、注册入口与布局修复

本轮范围：**只修登录/注册入口的交互、入口位置与布局**。§1–§15 已交付的社区功能、
注册后端与认证安全规则一律保留，未重写、未覆盖未提交修改。范围外未动一行。

### 16.1 真实根因（浏览器实测得出，不是读代码推断）

先说**不是**病因的部分，因为这一条决定了修法：**radio 分组本身是正确的**。真实渲染出的
登录页里，三个 `input[type=radio]` 在同一个 `<form>` 里、共用同一个非空 `name="accountType"`、
`id` 三个唯一、`for` 与 `id` 一一对应、渲染结果里 `checked` 恰好出现一次、没有遮罩层也没有
拦截点击的脚本。所以「看起来三个都能同时选中」不是分组失效，而是：

1. **视觉状态与真实状态脱节。** 三个选项是三个 100px 高、同样的白底、同样的描边的卡片，
   唯一区别是选中卡片上一层很淡的染色。三个同样"实心"的方框并排，读起来就是三个都被选中；
   而真正表达状态的 radio 圆点在卡片内部，小到不构成对比。
2. **点击目标只有中间的文字列。** label 只包住文字，卡片 100px 高度里的内边距不属于任何
   可点区域。**实测**：在选项卡片的内边距处派发一次真实鼠标点击（`Input.dispatchMouseEvent`，
   落在元素中心偏上的空白处），`checked` 仍然是 `REQUESTER` —— 这就是「无法切换」的字面原因：
   在卡片上按下去没有反应，人只会认为控件坏了，而不是认为自己点偏了。
3. **注册入口藏得太深。** 链接当时在 Requester 卡片内部，实测 y≈307，而 Sign in 按钮在
   y≈767：入口存在，但在 460px 之外，用户找不到。

（这三点都是先在浏览器里量出来、再回到模板确认的；没有先假设「可能是 checkbox 写错了」。）

### 16.2 改了什么

**`resources/templates/login.html`** —— 三个身份入口改成一个分段条：

```html
<fieldset class="account-type">
  <legend class="account-type__legend">Sign in as</legend>
  <div class="account-type__track">
    <label class="account-type__option" for="accountType-requester">
      <input class="account-type__input" type="radio" id="accountType-requester"
             th:name="${accountTypeField}" value="REQUESTER"
             th:checked="${selectedAccountType == 'REQUESTER'}">
      <span class="account-type__face">Requester</span>
    </label>
    <!-- 另外两段同构，值分别为 TECHNICIAN / ADMINISTRATOR -->
  </div>
</fieldset>
```

- 字段名仍由服务端注入（`${accountTypeField}`），与后端契约不可能漂移；值就是 `Role` 常量。
- **label 包住 input**，并且额外写了 `for`/`id`：整段都是点击目标，关联在标记里也是显式的。
- 注册入口移到表单之外、Sign in 按钮正下方：`New to SmartFix? Create a requester account`
  → 真实 `GET /register`，**在所有三种身份状态下都在同一个位置**；下面一行 muted 文案
  "Technician and administrator accounts are created by an administrator."
- 页面里**没有任何脚本**参与身份切换（本轮也确认过：`static/` 下只有 `css/site.css` 与
  favicon，没有任何 js 文件；页面内联脚本只有 layout 的 `.js` 标记与导航抽屉）。

**`resources/static/css/site.css`** —— 旧的卡片样式（`__body`/`__name`/`__hint`/`__action`，
含 `:has()` 增强）整块删除，换成：

- `.account-type__track`：`repeat(3, minmax(0, 1fr))` 三等分，2px 间隙/内边距，浅色圆角容器。
- `.account-type__input`：`position:absolute; inline-size:1px; block-size:1px; opacity:0` ——
  **刻意不用 `display:none` / `hidden`**：那会把控件移出 Tab 顺序、方向键和表单，整组 radio
  会散掉。视觉上的那一段是它的相邻兄弟 `.account-type__face`（`min-block-size:40px`，
  实测条高 46px）。
- 选中态只有一条来源：`.account-type__input:checked + .account-type__face`（底色/文字色/
  内描边）。**没有 active 类**，三个段落的标记除 id/for/value/文案/checked 外完全相同 ——
  眼睛与 radio 不可能对不上。`.account-type__input:focus-visible + .account-type__face`
  给 3px 品牌色 outline，键盘用户能看见"下一个选中的是谁"。
- `.auth__register` / `.auth__register-note` 两条新样式。
- 断点：≤520px 收窄面板内边距与段内边距；≤340px 再收一档。
  **这里改过一次，理由是可测的**：320px 下 "Administrator" 在 13px 下量得 85.4px，
  而可用宽度只有 89px —— 让掉的是内边距，不是字号（降到 12px 反而更糟）。
  所有规则都限定在 `.auth*` / `.account-type*` 选择器内，社区表单和其它页面的
  radio/label/button 不受影响（`verify` 全绿即为证）。

**`auth/controller/LoginController.java`** —— 模型里加
`selectedAccountType = SelectedAccountType.defaultedName(request)`：**由服务端决定哪一段是
勾上的**，因此"至多一个选中"是响应的性质，而不是浏览器猜出来的。

**`auth/security/SelectedAccountType.java`** —— 新增两个静态读取器 `nameOf(request)`
（不可用即为 `null`）与 `defaultedName(request)`（不可用时回落到 `REQUESTER`）。两者都先
解析再丢弃原文，返回的只可能是 `Role` 常量名：一个客户端的字符串永远进不了 `Location`
响应头或渲染属性。

**`auth/security/SelectedAccountTypeFailureHandler.java`** —— 失败跳转带上访客选过的那一段
（`/login?error=type&accountType=TECHNICIAN`、`/login?error&accountType=REQUESTER`）。
**选择的保留只是显示**：它不参与授权，§15.4 的两条硬规则一字未改 —— 账号是不是技师由
数据库决定，选错入口不建立认证、不留会话、不改角色。

**没有改动**：`SecurityConfig` 的授权规则、CSRF、密码策略、用户名/显示名校验、禁用账号判定、
管理员建号入口、注册服务端固定 `REQUESTER`、社区功能与其它页面。

### 16.3 测试结果（最终代码树，真实数字）

```
mvn -o -B verify   →   BUILD SUCCESS，用时 2 分 32 秒
surefire  380/380   failures 0  errors 0  skipped 0
failsafe  113/113   failures 0  errors 0  skipped 0    合计 493
```

failsafe 里含 3 个原生 PostgreSQL 的 IT（`RegistrationConcurrencyPostgresIT`、
`CommunityAnswerConcurrencyPostgresIT`、`CommunityMigrationPostgresIT`），本轮全部通过。

按影响范围改动的测试：

| 文件 | 用例数 | 本轮改动 |
|---|---|---|
| `auth/AuthenticationFlowIT.java` | 23 → **29** | 类型不符断言改为 `redirectedUrl("/login?error=type&accountType=TECHNICIAN")`；禁用账号走自己入口时改为 `/login?error&accountType=REQUESTER` 并断言页面仍是通用文案、选中段被保留；身份条断言整体重写（见下）；新增参数化 `exactlyOneSegmentIsCheckedWhateverTheAddressSays`（3 例）与 `theRegisterEntryIsOfferedWhateverSegmentIsSelected`（3 例） |
| `auth/security/SelectedAccountTypeTest.java` | 7 → **11** | `nameOf` / `defaultedName` 的三态与回落；「回传的值永远来自常量」用一组敌意输入（含 `"><script>`、`%00`、`_csrf`）断言 |
| `auth/RegistrationFlowIT.java` | 11 | 「注册后拿管理员入口登录被拒」的跳转期望同步为带参数的地址 |
| `auth/config/SecurityConfigTest.java` | 53 | 无需改动，全绿 |

**静态模板断言与真实浏览器行为的区分**（本轮特意分开写）：

- 静态断言能证明的：三个 radio 同组同 name、各自的值与文案、每个 input 在自己的 label 内部
  且 id/for 对应、渲染结果里恰好一段带 `checked` 且服务端对任意地址都给出恰好一个选中、
  三段标记除 id/for/value/文案/checked 外**逐字相同**（有第二套"选中"来源就会在此失败）、
  注册链接在按钮之下且在 `<fieldset>` 之外、且在每个身份状态下都恰好一次。
- 静态断言**不能**证明的：点击段落任意位置是否真的落在 input 上（这是布局事实），以及
  方向键/空格的真实行为。这两项只由下面的浏览器实测覆盖，没有用 MockMvc 的通过代替。

### 16.4 浏览器实测（真实 Edge + CDP，非 MockMvc）

无依赖地驱动本机 Edge（`--headless=new --remote-debugging-port`，CDP over WebSocket）：
真实鼠标点击用 `Input.dispatchMouseEvent` 落在元素中心，按键用 `Input.dispatchKeyEvent`，
提交体从 `Network.requestWillBeSent.postData` 读取。

- `verify-login.js` —— **59/59 通过**：结构断言；Requester → Technician → Administrator →
  Requester 四次**真实点击**，每次都比对绘制前后状态；方向键/空格/`:focus-visible`；提交体
  里恰好一个 `accountType`；拒绝后选择被保留且没有会话；1440/1280/390/320 四个宽度无横向
  溢出、无裁切、无重叠；关闭 JavaScript 后点击仍能切换。
- `flow.js` —— **22/22 通过**，完整走通：注册 → 回登录页看到 "Account created. Sign in to
  continue." 且 Requester 仍为默认选中 → 新 Requester 登录 → 同一账号走 Technician 入口被拒
  （跳转 `/login?error=type&accountType=TECHNICIAN`，无会话，`/home` 仍要求登录）→ 管理员登录
  → 经 `/admin/users/new` 建 Technician → 该 Technician 从 Technician 入口登录成功 → 走
  Administrator 入口被拒（保留 Administrator 选中）→ 管理员密码错显示通用文案。
- `nojs.js` —— **6/6 通过**：浏览器层面关闭脚本（`Emulation.setScriptExecutionDisabled`）后，
  只用真实鼠标点击与真实输入（`Input.insertText`）走完：默认仍恰好一段勾选 → 点第三段即选中，
  且绘制出来的填充/描边/文字色跟着变（选中段 `rgb(231,242,237)` 不透明，未选中段是同一色
  但 `alpha=0`）→ 输入用户名口令 → 点 Sign in **真的登录成功**（落在 `/`，页面有退出表单）。
  这条覆盖的是「关掉 JS 仍能完成身份选择和基本登录」，不是静态断言。
- 实测取到的证据：真实提交体为
  `_csrf=…&accountType=TECHNICIAN&username=…&password=…`（每次恰好一个身份值）；
  320px 下条高 46px、最窄段 95px，Administrator 完整显示。
- 截图（本轮生成在仓库外的临时目录里，未提交；文件名不含任何账号或口令，符合 §22.4）：
  `after-1440.png`、`after-390.png`、`after-320.png`（登录页三种宽度）、
  `flow-registered.png`（注册后回到登录页）、`flow-admin-users.png`、`flow-technician-home.png`。
- 测试账号：三个一次性账号（`ui.check.*`、`ui.tech.*`、`uicheck.admin`）建在临时 schema
  `ui_check_01` 上，用户名/口令由命令行环境变量传入，**没有写进代码、日志、测试或本文档**；
  验证结束后该 schema 已 drop，本机数据库与工作区恢复到进入时的状态。

### 16.5 本轮仍然没有做的

- 没有屏幕阅读器（NVDA/VoiceOver）与真机（iOS/Android）验证。
- 没有做限流/验证码/邮箱验证 —— §15.11 记录的缺口本轮未变，也不在本轮范围内。
- 没有部署、推送、提交。

### 16.6 网站验收步骤（当前有效，约 3 分钟）

1. 打开 `/login`：`Sign in as` 是一条约 46px 高的横向三段条，**只有 Requester 那一段有底色
   和描边**，另外两段是容器底色 —— 一眼就能看出只有一个被选中。
2. 依次点 Technician、Administrator、Requester：**点在段内的任意位置**（包括文字两侧的
   空白）都能切换，且**页面不刷新、用户名与密码框的内容不丢**。
3. 只用键盘：Tab 走到身份条（能看到 3px 品牌色轮廓），← → 切换，空格选中；确认选中态跟着
   焦点走。
4. Sign in 按钮正下方永远有 **"New to SmartFix? Create a requester account"**，三种身份状态下
   位置一致；点它进入真实的 `/register`（不是占位页），页脚有回登录页的链接。
5. 用 Requester 账号故意选 Technician 入口登录：跳回登录页，显示
   "Unable to sign in with the selected account type. Check your details or select another type."，
   **且 Technician 那一段仍是选中的**，同时**没有被登录**（再开 `/home` 会跳回登录页）。
6. 用该账号选 Requester 入口登录成功；退出后选 Administrator 入口再试一次，同样被拒。
7. 把窗口缩到 390px 与 320px：身份条仍是**一行三段**，"Administrator" 完整可见，无横向滚动条，
   注册入口不需要滚动就能看到；错误文案与注册入口在任何宽度下都不被隐藏。
8. 关闭 JavaScript 再打开 `/login`：仍能点选身份段并提交登录（导航会变成展开的列表，这是
   有意的降级）。

## 17. 2026-10-08：社区恢复、通知与正式审计

使用者授权由本次实现直接完成原 A/E 衔接。当前规则见 ADR-003，§11–16 中“无消费者”“E 尚未实现”“未定”等句子保留为历史，不再描述当前代码。

- `/admin/community/reports?view=HANDLED` 保留已处理举报，`view=ALL` 查看全部；隐藏内容可在历史中恢复。表单及分页保留视图上下文。
- `/notifications` 展示本人通知；共享导航提供铃铛入口。回答通知提问者，采纳通知回答者；管理员隐藏通知作者，自我回答和作者撤回不通知。
- `/admin/community/audit` 查看正式 `audit_entries`，记录处理人、目标、动作、结果、时间。业务修改与审计在同一事务。
- V20 创建通知表、外键及查询索引；V21 创建正式审计表。未修改 V1–V19。
- 新增 CommunityDeliveryIT，使用真实监听与持久化验证通知、回滚、历史恢复、权限和 CSRF。原生 PostgreSQL 测试同步验证正式审计与新迁移。
- 通知沿用提交后同步监听，写入失败记录日志；持久化重试/outbox 不在本次范围，见 ADR-003 边界。

### 17.1 本轮验证

- `mvn verify`：386 项单元测试 + 143 项集成测试，共 529 项全部通过，无失败、错误或跳过。
- `postgres-it` 专项：CommunityMigrationPostgresIT、CommunityAnswerConcurrencyPostgresIT、CommunityModerationPostgresIT，共 11 项全部通过。使用专用 smartfix_test 数据库的随机 schema，Flyway 到 V21、Hibernate validate 通过；测试后只删除各自随机 schema。
- 无界面 Edge：34 项检查全部通过，管理历史/审计/通知页面在 1440、1000、768、390、320px 均无横向溢出；已检查标题卡片、恢复表单上下文及 CSRF、未读样式、通知链接与唯一 DOM ID，并查看桌面与手机截图。
- 测试日志与截图位于忽略的 target/community-completion-* 路径。既有 UI 修改、用户的备份目录和修复脚本保留；本轮未推送、未部署、未对应用数据库执行迁移。
