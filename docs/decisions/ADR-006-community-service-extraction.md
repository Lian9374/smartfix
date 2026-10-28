# ADR-006：抽离 Community，保留 Core 维修事务边界

日期：2026-10-28。状态：设计已确定，尚未实现。适用分支：feature/community-microservice。

## 背景与依据

当前一个 Spring Boot 应用、一个数据库。CommunityAccessGuard 调用 UserService 并锁账号行；CommunityQueryService 批量读取显示名；CommunityModerationService 同事务调用 AuditService；CommunityNotificationListener 在 BEFORE_COMMIT 中写通知 outbox。V17/V19 社区表有 users 外键，V18 有采纳关系的复合约束。

因此直接复制 package 无法形成独立业务服务。需要独立数据所有权、协议和可恢复的跨服务通信。本决策替代 ADR-001 中“独立后端/分布式消息不在范围内”的范围限制；保留原 ADR 历史以及分层、权限、Flyway 和回归约束。

## 决策

- Core 拥有页面入口、Session/CSRF、账号、安全版本、报修、派工、工单、设施地图、SLA、通知和非社区审计。维修状态转换继续同一本地事务，不在本轮拆分。
- Community 拥有问题、回答、采纳、举报、审核、社区本地审计、发帖控制、请求幂等和 outbox。独立部署、独立数据库身份；禁止跨服务 SQL、JPA 实体或 Repository 调用。
- HTML 控制器/模板留在 Core。Core 使用版本化 JSON API，通过 DTO 适配当前页面；不搬入新的社区业务规则。
- Core 验证账号状态、安全版本与强制改密后，签发短期 Community 受众凭证；Community 独立校验签名/issuer/audience/有效期和权限。服务通道受保护，不能只信任 userId Header。具体身份契约见阶段 2 API 文档。
- 显示名在 Core 按页批量补齐；Community 不反向调用 Core 查询用户，从而不形成同步调用环。
- Community 的审核审计与业务同事务。可靠事件异步传到 Core；使用 outbox + 内部 HTTP + inbox 去重，暂不要求新增消息队列或独立通知服务。
- Core 的统一审计页面使用本地只读社区审计投影，与原非社区审计合并查询。Community 本地审计为权威事实源；投影明确显示更新时间，不能声称和业务跨库原子可见。
- 同一 Git 仓库、两个独立产物、两个数据所有者。Core 固定一个实例，Community 经内部 LB 部署 1/2/3 副本验证独立扩容。

## 功能与失败语义

保留全部浏览器路由、CSRF、所有权和隐藏内容规则。保留 2 分钟重复问题检测、问题/回答各 24 小时最多 20 条的默认规则，不新增最小发帖间隔。UNANSWERED 表示未采纳，不是没有回复。

Community 不可用时社区页面受控失败，不能把失败显示成功；Core 维修不等待 Community。调用超时、连接池上限和并发隔离必须落地。写入超时由幂等键查结果，不无条件重放 POST。

通知和统一审计投影允许短暂延迟；本阶段定义正常情况下事件到 Core 可见的初始目标为 15 秒，故障恢复后积压须在受控恢复实验中全部处理。15 秒为待后续运行验证的工程目标，不是当前测量。通知失败不回滚已提交社区业务；Community 本地审计失败则审核回滚。

## 明确的限制

保留 Core 页面入口意味着端到端仍可能先受 Core 限制；必须同时测试社区 API 与完整页面路径。共用 PostgreSQL 主机仍共享 CPU/I/O。多个服务并不证明性能提高，不保证线性扩容。部署成功、功能正确、性能收益分别验收。

## 保护与实施次序

原项目/main/已有标签/19090恢复实例/15443恢复库保留。本轮阶段 2 只有文档。阶段 3 另建开发环境；先实现登录→读问题→回答→outbox→通知，再迁移其余操作，随后数据迁移和双实例验证。不得将设计状态冒充已实现。

下一阶段以前核对 phase2 下的数据归属、API、事件和回归清单。原历史 Flyway 不重写；新 Community 维护独立迁移历史。数据切换只在新建测试库演练，原库不作为测试源目标。
