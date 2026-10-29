# SmartFix 微服务实验版：使用说明

状态：Core + Community 两个业务服务已拆分并可运行。用户要求停止性能实验，按可用微服务收尾；不能据此宣称扩容性能、最大持续容量或自动扩缩容已验证。

## 实际入口

- 微服务页面：http://127.0.0.1:19111/login 。原页面、三角色、社区、维修、通知/审计由Core承载。
- 原版恢复副本：http://127.0.0.1:19090/login 。它是单体保护基线，15443数据库不用于迁移测试。
- Community A/B：19112/19113；内部负载均衡19114；实验PostgreSQL15483。
- 合成账号文件：C:\Users\zhour\smartfix-microservices\.migration-runtime\stage9\test-accounts-private.json。不要提交或公开密码。

## 架构

浏览器→Core（Session、CSRF、账号、维修、通知）→短期JWT/HTTP→19114→Community A/B。
Community拥有问答、采纳、举报审核、社区审计、发帖控制、幂等和outbox；Core通过API访问，作者显示名由Core补齐。
两服务有独立可执行JAR、启动入口、逻辑数据库、数据库角色及Flyway历史。它们共用本机PG服务器，不能描述为独立物理数据库服务器。不是两个相同单体副本。
回答和outbox同事务；事件至少一次可靠重试，Core收件箱幂等处理后生成通知。新发布器采用有界批量领取，保留每事件独立租约、顺序及确认CAS。

## 已验证与保留

三角色页面、社区/通知/维修工作区、原表单和通知链接、数据库事务回滚、重复事件、接收端恢复、真实领取进程退出恢复均有实际证据。退出实验Core时只清理其配置的Session Cookie，已用私有测试Cookie哨兵验证不会清掉原JSESSIONID。
原项目C:\Users\zhour\smartfix只读，main和既有基线标签保持；代码/配置完整备份及原DB/附件保留，原AWS部署未改。电脑12:01重启后原smartfix-db保持停止，未由迁移启动；19090已按保护runbook恢复并验证。实验新增内容不会自动进入原版。

## 日常启动/停止

先核对.migration-runtime/stage9/resources-private.json，已运行服务不要重复启动。只在迁移工作目录使用tools/migration/stage9.py的start/stop，并明确指定core、a、b或lb；脚本核验PID、产物、库、端口和挂载，不删库/卷，不操作原系统。当前使用coreArtifactVersion=stage10-cookie-v2和communityArtifactVersion=stage10-v2。
Core新产物目录build-core-v2，Community新产物目录build-community-v2，均在私有运行目录。需要重新构建时先仅停止对应实验服务，再分别用根pom的stage10-core-artifact或community-service/pom的stage10-artifact；不要覆盖正在运行的JAR。

234轮和12轮性能脚本已停用，不再自动运行。完整容量、长稳、混合负载和云端性能未验证。后续压测或生产切换需要单独任务；本阶段不自动进入第11阶段。

人工只需打开19111，用三角色确认页面和一次回答/通知正常，再确认19090可登录。详细自动证据见EVIDENCE.json。
