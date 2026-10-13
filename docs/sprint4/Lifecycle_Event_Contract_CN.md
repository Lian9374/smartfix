# C → E 状态事件与真实记录标识契约

日期：2026-10-13；实现 `367cc4f`。新增字段是内部 Java 契约的兼容扩展，不是新的公网 API 或新状态机。

## RequestStatusChangedEvent

现有字段保留：requestId、ticketNumber、requesterId、fromStatus、toStatus、actorUserId、occurredAt。

新增 `historyId`：由正常 RequestLifecycleService 发布时，指向同事务已经保存的 request_status_history 主键。

- 初始提交：fromStatus **必须允许 null**，toStatus=SUBMITTED，historyId 指向真实初始历史。null 表示没有上一状态，不是异常，也不能伪造 SUBMITTED→SUBMITTED。
- 正常转换：from/to 来自既有状态机，historyId 指向该次转换的真实历史。
- occurredAt 是状态转换发生时刻的 Instant；数据库使用其实际支持的时间精度。可靠去重应优先使用记录 ID，不仅依赖格式化时间戳或回读时刻的完全相等。
- 无效角色、非法转换、重复初始记录及事务回滚不产生成功提交的历史事件。

原七参数构造器保留，旧调用方可继续编译，historyId 默认为 null。该兼容路径不具备新标识保证；E 必须明确旧事件的兼容策略，而不能给缺失 ID 编造一个数据库主键。

内部去重命名建议：`REQUEST_STATUS_CHANGED:HISTORY:<historyId>`；最终由 E 按其通知／outbox 模型定义，不能把不同事件类型混用一个无前缀键。

## RequestTransitionResponse 与参与者

响应增加 historyId，保留旧六参数构造器。beforeTransition 在历史落库前执行，此时标识为空；afterTransition 和返回的正常响应包含实际历史 ID。

参与者继续依赖原有 requestId、状态、操作人和时刻。C 不为 SLA 增加猜测的状态、暂停点或生产期限。

## WorkOrderCompletedEvent

原字段保留：workOrderId、requestId、ticketNumber、technicianId、requesterId、occurredAt。

新增 `resolutionHistoryId`，由 WorkOrderService.complete 从 RESOLVED 转换返回的 historyId 提供。保留旧六参数构造器，新字段默认 null。

该标识针对一次解决转换，重新打开后再次解决会有新的历史 ID；工单本身继续复用同一 workOrderId。事件时刻沿用既有解决转换时刻，不声称总与工单实体另一字段的精度完全相同。

建议去重命名：`WORK_ORDER_COMPLETED:RESOLUTION_HISTORY:<resolutionHistoryId>`。它与状态事件是不同事件类型；是否发两种通知仍按既有业务契约，由 E 实现，不由 C 擅自合并。

## 事务与可靠投递

事件在业务事务内发布；历史与工单保存失败会回滚。E 如需可靠恢复，应在同一业务事务持久化待投递记录，提交后再处理，不能把单纯 AFTER_COMMIT 监听称为 outbox。

当前旧通知监听器对初始 fromStatus=null 仍可能出现 .name() 空指针，已登记 E 的消费缺口。本轮没有通过改写初始状态或取消初始事件来绕过它。已有消费者的旧构造器测试通过，不等于故障重试已完成。

这些事件没有追加描述、照片或密码；仅用于内部消费者，不能把其中操作人 ID 当成外部请求的授权凭据。
