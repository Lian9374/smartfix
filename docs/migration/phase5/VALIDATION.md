# 阶段5实际验证

日期：2026-10-28；起始提交5ed233b。用户已明确确认阶段4人工验收通过。

## 可复核命令与产物

工作目录为 C:/Users/zhour/smartfix-microservices。构建命令分别为 `./mvnw.cmd -DskipTests package` 和 `./mvnw.cmd -f community-service/pom.xml -DskipTests package`。定向测试分别选择 `MigrationIsolationGuardTest,CommunityIdentityClientTest`（Core）和 `MigrationIsolationGuardTest,CommunityIdentitySecurityTest`（Community）；对应两侧 target/surefire-reports 的 TEST XML 均为零失败、零错误、零跳过。

真实链路验收命令为 `python tools/migration/verify_stage5.py`；随后执行 `python tools/migration/stage3.py verify` 检查运行隔离。新增五种无效服务凭证检查单独实际执行后已并入同一验收脚本及13组结果。备份manifest逐文件复核、保护容器inspect比对及健康检查在报告生成前另行执行。私有原始结果不进入Git。

## 通过项目

- 两个产物构建成功；Core 9项、Community 9项已有身份/签名/隔离单元测试通过。全量Core回归未运行；不要混用阶段4的并发测试数作本阶段新测试数。
- 13组真实HTTP/数据库验收通过，安全摘要见下；私有原始stage5-results.json留在runtime。
- Core真实页面→Community问题读取→回复保存→outbox→独立服务凭证→Core去重收据/站内任务/通知→原通知页→同一讨论URL跳转，已完成。
- 测试问题仅在Community数据库；Core相同ID不存在，选定路由没有旧Repository/SQL回退。其他社区功能保持旧路径，未双写同一测试问题。
- 重复浏览器提交不增加回答；同键改正文409；超时结果查询仅返回当前用户自己的命令结果。
- 普通用户凭证/匿名不能调用事件入口；服务凭证过期、错误issuer/audience/subject、过长寿命均提前401。
- 重复事件返回duplicate确认，同ID内容不同409；模拟发送端丢失已提交确认后重新投递，仍只有一条通知。
- 在迁移Community库插入outbox的触发器故障使回答、幂等记录和聚合序号一起回滚，之后投递无缺号。
- 在迁移Core库插入通知的触发器故障使inbox/投递任务一起回滚，恢复后重试成功；不调用REQUIRES_NEW的通知创建方法破坏原子性。
- 自答保存回答与收据，但不通知自己。
- 迁移Core停止期间Community仍提交回答并保留PENDING及尝试状态；Core恢复后投递完成。
- 迁移Community重启后恢复持久化过期租约，再投递已提交事件不重复产生通知。
- 故障注入触发器最终均已清除；Core、Community及19090保护实例均健康。两服务双数据库权限与Core原页面公共检查通过。
- 原项目全文件指纹与备份一致，原数据库/15443保护容器ID/State/Mounts未变，main及基线标签未动；未操作AWS。

## 修复与限制

原Core全局异常处理将新接收器ResponseStatusException包装为500，已仅为新增控制器添加局部状态映射，实际同ID内容冲突返回409。没有修改旧全局行为。

链路只针对合成讨论900000000001，仍未全量迁移社区数据和功能，也未执行历史切换/完整状态机回归/双JVM竞争/正式压测/生产TLS或密钥轮换。页面使用已有应用布局的专用测试视图，不声称所有原社区模板均已API化。

当前回答事件序号在测试讨论的version上递增。该讨论本阶段仅只读及回答；阶段6扩展编辑/采纳/审核前必须改为独立事件序号或确保所有版本变更都产生有序事件，不得把实体乐观锁版本直接用于完整业务事件序号。

丢确认与租约故障使用持久化状态注入模拟，真实迁移服务进程确实执行了停止/启动；不声称做了TCP丢包实验或网络exactly-once。

FAILED阈值/人工重放接口和多发布器争抢未完整验收，当前只验证正常持久重试及单进程重启恢复。站内投递在Core同一事务完成、任务状态DELIVERED；邮件与旧全局投递扫描仍关闭。

## 人工操作

按RUNBOOK，使用runtime/stage5-accounts-private.json中的responder登录19091，打开 /community/migration-demo，提交不少于10字符的回复。退出后以author登录 /notifications，核对新通知、未读数及Open discussion跳转；普通标签页共享Session，请顺序切换或使用隔离会话。
确认普通社区、维修页面和19090保护实例仍可使用。人工确认后再单独指示阶段6；本阶段已停止。
