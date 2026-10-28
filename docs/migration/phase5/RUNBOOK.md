# 阶段5：一条真实讨论/回答/通知链路

本阶段仅接管新建的合成验收讨论900000000001，不批量迁移历史讨论、不切换其他社区功能。数据只在Community库；Core的相同问题ID不存在。Core对该字面URL的MVC映射优先于旧变量路由，仅通过Community API取数据/写回答，通过Core账号模块批量补显示名，没有社区数据库连接或旧Repository回退。

## 页面验收

运行环境仍为19091 Core、19092 Community、15453迁移数据库，19090/15443原版恢复资源保留。
访问 http://127.0.0.1:19091/community/migration-demo ，跳转原社区URL家族 /community/questions/900000000001 。页面复用现有应用布局，测试范围只有读讨论/回答/回复；采纳、编辑、举报等仍留给第6阶段，不在本测试页提供无效按钮。

两名合成账号与共用测试密码在被Git忽略且私有的 .migration-runtime/stage5-accounts-private.json：author为提问人，responder为回复者。先以responder登录并提交10–4000字符回复，再退出换author登录 /notifications，核对新通知并点击Open discussion。也可用两个隔离浏览器会话；同一浏览器普通标签页共享登录状态，不能把它们当两个独立用户。

普通 /community、账号、维修等路径保持原实现。没有删除原数据，也没有声称全部社区微服务化。

## 请求幂等与不确定结果

Core每次展示表单生成UUID命令键，保留在当前Session且绑定用户；CSRF和当前账号检查继续执行。失败重显保持同一键和正文，不把超时显示成确定失败。收到网络超时时先按当前用户查询Community命令结果，若已提交则正常跳转，否则保留同一表单安全重试。

Community以actor+UUID键存储请求摘要、回答ID、事件ID。先获取社区用户行锁再查幂等结果，重复成功请求不再次消耗配额；同键不同问题/正文409。幂等结果、回答、问题聚合序号和outbox同事务。键保留未自动清理，满足至少24小时保留；不是无限期生产清理策略。

## 可靠事件

新增Community V5的community_answer_commands、community_outbox；Core追加V28 core_community_inbox。旧Flyway文件未改。
用户身份仍用Core签发的短期凭证。发布者另用Community独立RSA密钥，issuer=smartfix-community、subject=community-publisher、aud=smartfix-core-events、kid=stage5-community-1；普通用户凭证不能调用事件接收端。密钥仅在私有runtime，未提供浏览器签发入口。

发布器同事务领取并提交租约，再发HTTP；15秒租约，SKIP LOCKED，按同问题序号阻止越过未完成事件。失败持久退避，最多30秒间隔、20次后FAILED；协议/认证4xx转FAILED，保留原事件，可由管理员调用 /internal/community/v1/moderation/events/{id}/retry 重排同eventId。终止失败阈值与人工接口的完整演练尚未执行。

Core对事件ID和规范化JSON摘要去重，用聚合事务锁保证顺序；同ID内容变更409，缺失前序503，未知协议版本422。inbox、站内delivery_tasks及实际notification同一本地事务，提交后才确认。这里站内任务已同步完成，状态DELIVERED；不启动旧全局投递扫描器，不回放历史邮件任务，外发邮件仍关闭。自答记录收据但不通知自己。

这是一条“至少一次投递+消费幂等”的链路，不声称网络或外部邮件exactly-once。数据库中原payload不可重写来重放，重试保持eventId；新的业务动作生成新ID。

## 运行和证据

顺序使用 python tools/migration/stage3.py start core / start community；不要重新provision或覆盖密钥。服务密钥生成器只创建不存在的文件；Core只配置Community公钥，Community只配置自己的发布私钥。
python tools/migration/verify_stage5.py 会在15453迁移库创建合成账号和测试讨论，并在测试范围注入触发器故障、模拟丢确认及重启迁移Core/Community。只用于隔离验收，不是只读探针，不能接原版/保护库。各临时触发器在finally中删除，报告确认没有遗留。

原始日志、测试账号、事件内容及stage5-results.json均仅保存在被忽略的私有runtime。安全摘要见VALIDATION.md；确认本阶段浏览器验收后再单独指示第6阶段。
