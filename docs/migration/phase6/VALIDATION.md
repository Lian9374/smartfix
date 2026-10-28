# 阶段6实现与验证

日期：2026-10-28。起始提交 f6c9cf2f5496285569e3c9c0cd878d1fc2cfc8c2。
用户明确确认：“19091 第 6 阶段功能已验证”。

## 实现

Community 独立编译本服务拥有的领域、查询、写服务和 Repository。账号访问改为检查可信 JWT；发帖锁使用 Community 的用户守卫行，不访问 Core users。查询不回调 Core，Core 按最多50个ID一批补显示名。

Core 保留原 MVC 控制器、DTO、页面 URL、表单和 CSRF，通过四个 RemoteCommunity 服务适配器调用有限操作白名单 API。HTTP 契约为 POST `/internal/community/v1/operations/{operation}`：读操作使用显式分页信封 content/page/size/total；写操作携带 Idempotency-Key，actor只取JWT，正文只有内容参数。操作名、参数和返回类型见 CommunityOperations、四个适配器及 verify_stage6.py；不传实体、Repository或客户端角色。业务拒绝保留400/403/404/409及安全业务提示，传输故障为503及不确定结果提示。

新增表单键绑定 Session/用户，错误重显保留键；同actor/key/operation的业务、结果和outbox同事务。结果不确定时用户重试原表单，不自动生成第二次写入。Core同时最多8个社区调用，连接500ms、读取2000ms，无等待队列；社区停机不让维修导航同步等待社区。

旧Core社区服务和旧通知监听器在 community-remote profile 禁用；该profile排除社区 Repository 扫描和实体映射。启动断言检查三个 Repository、旧监听器、全部社区实体均不存在，四个服务实际为远程适配器。旧源码仅保留作兼容与基线规则测试，不是运行时备用写入口。Community 使用 community-full profile。

V6使用独立事件序列表，与实体version分离，并从已有outbox序号接续。审核和community_audit/outbox同事务。每次合法重复业务动作生成新eventId，取消后再次采纳、恢复后再次隐藏不会被内容ID永久去重。

Core V29增加审计来源标识，社区事件可靠投影至现有audit_entries，source/source_id唯一约束去重。统一审计保留USER、DELIVERY、SLA_POLICY及原记录，在同一查询内按occurred_at DESC/id DESC全局稳定分页，不拼接两个分页结果。页面显示来源和原始ID；本地投影存在事件投递延迟，Community本地审计是权威记录。

## 实际通过

- Core 122项定向测试、独立Community 32项领域/身份/隔离测试：零失败、零错误、零跳过。测试报告位于两侧target/surefire-reports；没有把旧日期报告计入本次。
- 两个独立产物构建成功。运行中JAR锁通过仅停止已登记迁移进程后重建解决；原版恢复进程未停止。
- verify_stage6.py 的12组真实HTTP/PostgreSQL检查全部通过：原页面发帖/回答和幂等；搜索/分页/我的内容；编辑和所有权；采纳竞争/取消再采纳/撤回答案清除采纳；举报去重及双管理员审核竞争；隐藏恢复、历史；已解决/未回答过滤和回答举报；审计触发器失败的整体回滚；事件通知及审计投影；统一审计其他来源和分页；CSRF/撤回可见性；Community停机时503且维修/地图可用；三入口健康。
- stage3.py verify 在V6 schema上通过：数据库角色相互隔离、内部外键、独立Flyway、注册登录与原页面导航、保护容器身份一致。临时故障触发器已删除。
- 原项目全文件指纹与保护备份一致；原数据库及15443恢复容器ID/State/Mounts未变，main及基线标签保持。邮件和旧投递扫描保持关闭。

命令（PowerShell参数需引用）：`mvn -q '-Dtest=CommunityQuestionTest,CommunityReportTest,CommunityQuestionServiceTest,CommunityAnswerServiceTest,CommunityModerationServiceTest,CommunityQueryServiceTest,CommunityGatewayResponseTest,MigrationIsolationGuardTest,CommunityIdentityClientTest' test`；Community使用 `mvn -q -f community-service/pom.xml '-Dtest=CommunityQuestionTest,CommunityReportTest,MigrationIsolationGuardTest,CommunityIdentitySecurityTest' test`；构建使用 `mvn -q '-Dmaven.test.skip=true' package`，Community追加 `-f community-service/pom.xml`。实际环境没有mvnw.cmd，阶段5报告中的wrapper写法不可直接复用，应使用已安装Maven。

首次沙箱测试卡在Mockito代理初始化，已仅终止核验属于工作区的Maven进程树并在允许本机测试工具运行的环境重跑。社区故障503被旧全局异常处理覆盖成500的问题已通过新增advice优先级修复，并重跑真实故障检查。

## 范围与未运行项

尚未执行阶段7历史数据搬迁/切换或增量恢复，旧Core库历史社区行保留但不作为远程模式回退。历史通知链接须在阶段7导入对应历史社区数据后验收；当前不能把新合成数据验证冒充历史迁移成功。

双JVM竞争、完整系统自动回归、正式压测、AWS部署与生产密钥轮换未运行。跨JVM及完整系统回归属于阶段8；本阶段原生PostgreSQL竞争通过不等于跨实例验证。终止重试阈值和管理员人工重放的完整故障演练未运行。

## 人工路径

登录 http://127.0.0.1:19091/login 。测试账号位于私有 `.migration-runtime/stage6-accounts-private.json`，不在Git。author/responder用于发帖、回答、编辑撤回、采纳/取消；admin1/admin2用于审核。

页面：`/community`、`/community/mine`、`/admin/community/reports?view=ALL`、`/admin/community/reports?view=HANDLED`、`/admin/community/audit`、`/notifications`。再检查维修流程及 http://127.0.0.1:19090/login 。本阶段用户已确认人工验收，不重复索取确认。
