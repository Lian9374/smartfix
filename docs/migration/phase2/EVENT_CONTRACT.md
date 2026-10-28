# 可靠事件与本地审计契约 v1

状态：设计，未实现。现有 Java 事件注释写 AFTER_COMMIT，但实际 CommunityNotificationListener 使用 BEFORE_COMMIT；本设计以可执行代码为现状依据，不在阶段2修改注释或监听器。

## 统一信封

eventId：持久 UUID；eventType：下述类型；schemaVersion=1；producer=community；aggregateType/aggregateId；aggregateSequence：该问题聚合内单调序号；occurredAt：UTC；traceId；payload。outbox 保存原序列化内容及摘要，重试不改 eventId/内容。Core 验证 schema、必需字段和来源，不能据事件内容执行任意 SQL/跳转。

## 事件类型及字段

CommunityAnswerCreated：answerId/questionId/questionAuthorId/answerAuthorId/occurredAt。对应现有 CommunityAnswerCreatedEvent；自答不通知自己。Core 通知收件人为 questionAuthorId，reference 为 questionId。

CommunityAnswerAccepted：answerId/questionId/questionAuthorId/answerAuthorId/occurredAt。对应现有 CommunityAnswerAcceptedEvent，收件人为 answerAuthorId，自采纳不通知自己（业务本身仍遵守自采纳限制）。取消后再采纳是新动作、新 eventId，不用 answerId 永久去重。

CommunityContentVisibilityChanged：contentId/contentType（QUESTION/ANSWER）/questionId/authorId/actorUserId/resultingStatus（VISIBLE/HIDDEN/WITHDRAWN）/occurredAt。映射现有 CommunityContentHiddenEvent，命名明确覆盖隐藏、恢复和撤回；Core 仅 HIDDEN 触发隐藏通知，不能将普通撤回或恢复误发成审核隐藏。

CommunityAuditRecorded：sourceAuditId/legacySourceId（可空）/actorId/action/targetType/targetId/outcome/occurredAt。社区本地审计与 outbox 同事务；Core 投影去重不影响本地权威审计。targetType 仅 QUESTION/ANSWER/REPORT。

## 投递与原子性

业务数据、请求幂等结果、社区审计和相关 outbox 同一本地事务。事务回滚，均不得对外生效。提交后发布器领取任务；租约有时限，多实例竞争领取，退出后可重新领取。单聚合按序发布，Core 检测重复/乱序并保留最后序号，未来事件先缓冲/重试，不能静默跳过缺失事件。

投递至少一次；Core 同事务保存 inbox、通知任务、审计投影后确认。确认丢失时重发按 eventId 返回原确认。不能声称跨库 exactly-once 或外部邮件绝对不重复。

初始重试计划：1、5、15、60、300秒，随后保持有界退避至人工处理阈值；到阈值标 FAILED 并保留原事件，进入可见积压/告警，不删除。人工重放保持 eventId。认证/未知版本/摘要冲突属于配置或协议故障，停止该事件反复快速重试并告警。

观察 pending/failed/oldestAge/重试数/重复数/端到端延迟；正常可见性初始目标15秒。故障恢复必须验证积压最终处理及正确收件人。此目标不是已经通过的性能结论。

## 切换和兼容

不回放整个历史数据库来生成新通知。阶段7记录旧 delivery_tasks 水位、已发通知及新 outbox 起点，防历史重发。切换后停用同动作旧本地监听路径；不双写两套权威社区数据。

事件版本遵循兼容新增可选字段，破坏性变更提升 schemaVersion 并先升级消费端；不要求同步发布 Core/Community。服务认证密钥轮换和消费者版本恢复单独验证。

## 必需失败验证（阶段2均 NOT_RUN）

回滚不发事件；确认丢失后重发；重复/乱序/缺失序号；发布者持租约退出；Core中断恢复；两个发布实例争抢；恢复再隐藏/取消再采纳；自答不通知；审计写入失败回滚；未知版本隔离；迁移水位不重发历史通知。
