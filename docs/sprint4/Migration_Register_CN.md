# Sprint 4 数据库迁移登记

更新：2026-10-13。协调责任：C；当前使用者按模块自行实现。核查当前分支，不将旧计划中的版本分配当成已发布 SQL。

## 当前有效文件清单

当前有 **23 个 SQL 版本，最高 V25**。V13/V15 没有文件；Flyway 版本允许间隔，不为凑连续编号添加空迁移。

- V1 `V1__baseline.sql`：公共架构基线，文件刻意不建业务表。
- V2 `V2__create_users.sql`：A/B，账号与角色。
- V3 `V3__create_locations.sql`：D，地点。
- V4 `V4__create_maintenance_requests.sql`：C，报修与工单号序列。
- V5 `V5__create_request_attachments.sql`：E，附件元数据。
- V6 `V6__create_request_status_history.sql`：C，状态历史；初始历史部分唯一索引。
- V7 `V7__extend_request_lifecycle.sql`：C，生命周期、版本与审核／解决时刻。
- V8 `V8__create_work_orders.sql`：C，工单和维修记录。
- V9 `V9__create_request_feedback.sql`：C，用户评价。
- V10 `V10__create_technician_profiles.sql`：B，技能、覆盖和可用性。
- V11 `V11__create_assignments.sql`：B，有效指派及历史。
- V12 `V12__create_facilities.sql`：D，设施。
- V14 `V14__create_notifications.sql`：E，已发布通知基础。
- V16 `V16__create_announcements.sql`：D，公告。
- V17 `V17__create_community_questions_and_answers.sql`：A，社区问答。
- V18 `V18__add_community_accepted_answer_constraint.sql`：A，采纳归属约束。
- V19 `V19__create_community_reports.sql`：A，举报。
- V20 `V20__create_notifications.sql`：A/E，另一已发布通知基础；与 V14 的重叠由兼容回调和 V25 协调。
- V21 `V21__create_audit_entries.sql`：E，正式审计。
- V22 `V22__add_verified_nus_building_locations.sql`：D，NUS 建筑地点。
- V23 `V23__add_dispatch_and_technician_support.sql`：B/C，历史分支技师／派单支持的追加整合。
- V24 `V24__secure_account_initialization_and_passwords.sql`：A/B，初始化防重复和受管账号密码设置。
- V25 `V25__reconcile_notification_schema.sql`：C/E，通知结构追加协调。

名称、版本和工作树文件 SHA-256 见 [机器清单](evidence/userc/migration-manifest.json)。SHA-256 是文件字节快照，不是 Flyway 使用的 CRC32 校验和；实际兼容性以 Flyway validate 为准。

## 本轮变化与验证

本次 C **无新增 SQL、无历史 SQL 改动**。初始历史并发通过请求行锁和已有 V6 唯一约束解决；事件记录标识来自已有历史主键，不增加表或列。

已经存在的 LegacyMigrationCompatibility／LegacyMigrationRecoveryConfiguration 和 V25 在本轮真实 PostgreSQL 上复验：新库全迁移链、旧分支数据保留、通知重复结构、非法数据回滚、拒绝未知较早版本及额外外键依赖。命令和结果见 [C 交接](UserC_Migration_Workflow_Handoff_CN.md)。

正常模式严格验证；恢复模式仅受控处理历史遗漏版本 10/11/14/16。不得长期设置恢复开关、删除历史、关闭校验或无条件忽略版本。

## 新版本预留规则

1. 实现前 fetch 并检查当前集成分支、未合入的迁移和共享环境历史；本文不能证明共享环境里没有其他版本。
2. 当前最高版本是 25，后续模块需要 SQL 时从最新最高版本之后申请。**V26 起尚未在本文件预留给任何模块**，避免凭空占用多个版本。
3. E 的 outbox／SLA／邮件、D 的性能索引如确需迁移，由实现者写 SQL，C 登记编号、依赖、负责人、用途和空库／升级／回滚验证。
4. 旧计划 V13 SLA 未发布，不表示现在可以把 V13 插入已有 V25 的发布链；新实现须使用正确的新版本。
5. 已发布版本内容不可改写。结构修复使用追加迁移；非 PostgreSQL 可回滚 DDL 的操作须单独说明失败处置，不承诺自动降级数据库。

## 实际恢复流程边界

以 [既有历史修复说明](../sprint3/Legacy_Flyway_Recovery_20261009_CN.md)为适用条件依据：停止应用、备份并确认可恢复、核查确实属于允许的旧分支场景，再一次性执行恢复模式；之后关掉恢复配置，正常启动并严格验证。

本轮仅在新建测试集群运行，没有对用户业务库再次执行恢复、repair、clean 或升级操作。
