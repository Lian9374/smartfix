# 数据所有权与跨边界依赖

状态：设计，尚未迁移。代码依据均为仓库相对路径。

## 权威写入者

Community：community_questions、community_answers、community_reports，以及新增 community_audit_entries、community_posting_guards、community_command_dedup、community_outbox。

Core：users、账号初始化、technician_*、locations、facilities、maintenance_requests、request_*、assignments、work_orders、repair_records、SLA、announcements、notifications、delivery_tasks，以及非社区 audit_entries。新增 core_community_inbox 和只读 community_audit_projection。

说明：既有 audit_entries 同时包含 QUESTION/ANSWER/REPORT 和 USER/DELIVERY/SLA_POLICY，不能整表搬走。切换时复制社区类别至 Community 本地审计，保留 legacySourceId 和来源水位；Core 的历史显示必须去重。正式迁移脚本在阶段 7 编写验证。

## 逐项替代

1. community/service/CommunityAccessGuard.java → users.getUserAccess：Core 当前入口查账号，签名传递有效账号/角色/安全版本/强制改密结论。Community 服务拒绝无可信上下文调用并再做内容权限检查。短期凭证内的在途撤销窗口必须记录，不宣称即时全局撤销。
2. 同文件 serializePosting → users.lockActivePostingAccount：改锁 community_posting_guards 中 userId 对应行；唯一键解决首次创建竞争。锁、窗口内计数、重复检测与写入同事务。多 JVM 验证在阶段 8。
3. community/service/CommunityQueryService.java → users.findDisplayNames：API 返回 authorId/handlerId 等账号 ID，Core 收集去重后一次批量补名。展示名变化以 Core 最新值为准；未知引用使用原页面可识别占位，不泄露其他用户资料。
4. db/migration/V17、V19 → users 外键：新库仅存外部账号 ID，通过可信入口确认合法操作主体；保留历史引用，不复制账号认证表，不作跨库 join。账号物理删除策略保持现状，不增加级联删除。
5. V17/V18/V19 社区内部约束：保留问题/回答外键、答案所属问题的复合采纳约束、举报唯一性、状态检查、索引和稳定分页。隐藏/撤回均为逻辑操作，不删除历史。
6. community/service/CommunityModerationService.java → AuditService.record：Community 本地审计接口同事务持久化；记录 actor、action、targetType/Id、outcome、occurredAt。统一审计由 Core 投影展示，不同步回写 Core 审计表来模拟跨库事务。
7. community/event → notification/listener/CommunityNotificationListener.java：进程内 BEFORE_COMMIT 监听改为 Community 同事务 outbox；Core 接收端按 eventId 去重并写通知任务。切换后同一动作的旧监听路径停用，防双发。
8. controller → SmartFixUserDetails/common exceptions：Core HTML 控制器继续取 Session 身份；Community API 使用独立通信 DTO/错误码。异常映射在 Core 客户端处理，不共享 Spring/JPA 内部对象。
9. audit/controller/AuditController.java：/admin/community/audit 当前是跨业务统一视图。保留 URL 和所有来源；Core 对本地原审计与社区投影做全局排序分页，不能各取一页后拼接。
10. common/web/NotificationNavigationAdvice.java：原未读数仍由 Core 通知模块提供；维修页面菜单不得为渲染导航额外同步查询 Community。

## 新数据的事务约束

- posting_guards：userId 唯一，不靠 JVM 内存锁；问题/回答窗口限制分别计数。
- command_dedup：唯一 (actorId, operation, key)，保存规范化请求摘要、结果和业务引用；与业务写入原子提交。
- outbox：eventId 全局唯一，记录聚合及序列、投递尝试、租约和下次重试；重发不重新生成 eventId。
- inbox：Core 以 eventId 唯一并校验事件摘要，去重与通知任务/审计投影在本地同事务提交。
- audit_projection：唯一 (sourceService, sourceAuditId)，保留 legacySourceId 防迁移重影；仅查询投影，不反向改 Community。

## 隔离和迁移规则

数据库凭据分别不能访问对方数据库。Community 应用不装配 Core UserService/AuditService/Repository；Core 不装配社区数据访问实现。共同契约可以共享 schema，不共享实现实体。

阶段 3 创建独立迁移开发库；不得复用保护用 15443 恢复库。阶段 7 从备份恢复出新的迁移演练源库，暂停该测试源社区写入后同步水位，只启用一个权威写入口。已有备份、原系统和恢复保护实例不执行这些操作。
