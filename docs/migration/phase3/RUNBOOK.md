# 阶段3：两个独立应用的本地隔离骨架

本阶段不迁移社区业务、不连接两服务业务API、不实现跨服务身份；这些分别在阶段4–6完成。

## 构建和代码边界

Core 保留根 pom.xml、现有 src 和完整原业务；Community 为 community-service/pom.xml 独立项目，自己的启动入口、安全配置、Flyway和JAR。两个项目没有 Maven 父子业务依赖，可以单独构建发布。

在 C:/Users/zhour/smartfix-microservices 的 feature/community-microservice 分支执行：

    mvn -B -ntp -Dtest=MigrationIsolationGuardTest package
    mvn -B -ntp -f community-service/pom.xml -Dtest=MigrationIsolationGuardTest package

上述只执行隔离保护测试，不代表完整回归套件已通过。完整回归沿阶段计划继续。

Core JAR：target/smartfix-0.0.1-SNAPSHOT.jar。
Community JAR：community-service/target/smartfix-community-0.0.1-SNAPSHOT.jar。

## 已建立的实验环境

原项目、原库、19090/15443恢复保护资源、现有云环境都保留。本阶段另建 stage3 数据卷与数据库容器，不使用 external 卷、不挂载原目录。资源真实身份保存在被Git忽略的 .migration-runtime/resources.json。

Core：http://127.0.0.1:19091/login，账号sf_migration_core，库smartfix_migration_core。
Community：http://127.0.0.1:19092/actuator/health，账号sf_migration_community，库smartfix_migration_community。
新 PostgreSQL 仅绑定127.0.0.1:15453。两个独立数据库共享本次新建PostgreSQL实例，因此共享CPU/I/O的限制仍存在。

Core 从已有私有逻辑备份恢复到新库后启动，保留原功能。为保证阶段3仍可运行，Core 暂时仍有内置社区表及原社区实现；Community 只初始化空的自有schema，不双写、不读取Core库。最终权威归属切换在后续阶段完成，本阶段不能声称已交付社区微服务业务。

Community V1/V2/V3来自原V17/V18/V19的最终社区结构，删除仅指向users的外键，保留社区内部复合采纳约束、唯一性、CHECK和索引。未重写原Flyway文件，也未复制原flyway_schema_history到社区库。

## 启动、核查与停止

以下命令必须从迁移工作区根目录执行，Python3.12及Docker权限须可用。不要执行原 start-smartfix.ps1/repair脚本。

    python tools/migration/stage3.py start core
    python tools/migration/stage3.py start community
    python tools/migration/stage3.py verify

首次 provision 已完成，禁止重复 provision 覆盖数据；该命令在检测到已有runtime时拒绝执行。服务启动命令检测端口占用并停止，不抢占其他进程。操作锁拒绝同一runtime并发启动/验证。verify会在迁移Core库创建一个新的合成测试请求人，勿将其当成只读探针反复运行。

    python tools/migration/stage3.py stop community
    python tools/migration/stage3.py stop core

停止命令先核对记录PID的实际JAR路径，再仅终止该迁移进程；这是本地开发停止工具，采用强制退出，不代表已验证生产优雅下线。没有删卷、清库、改原进程的操作。不要关闭Docker引擎以代替停止本实验。

## 启动前拒绝错误环境

Core和Community均通过EnvironmentPostProcessor在数据源/Flyway初始化前校验隔离开关、精确数据库URL、服务数据库账号、回环地址、独立网页端口、Cookie、附件目录、关闭邮件/管理员初始化/通知投递，以及当前工作目录。拒绝单独Flyway URL/账号、JNDI、自定义DataSource及Hikari JDBC URL覆盖。

启动脚本另检查容器ID、阶段标签、唯一新卷、精确端口映射、运行目录真实路径，拒绝误指原版/保护资源。密钥及密码仅存runtime私有文件，不提交Git，不在工具输出打印。

Java保护只允许阶段3的本地白名单。不要拿此阶段产物直接替换云部署；未来云端部署必须有单独方案、环境白名单及负面验证，不能简单删除保护。

## 安全和人工验证

Core Cookie为SMARTFIX_MIGRATION_sf_migration_core，不会覆盖原版JSESSIONID或SMARTFIX_BASELINE_VERIFY。附件使用runtime/uploads-sf_migration_core；外发邮件关闭，通知自动投递关闭，不会把恢复的历史邮件任务发给真实收件人。

Community仅放行健康端点，其他请求拒绝访问；此时访问社区业务API被拒绝是预期结果，不能临时关闭安全配置凑连通。阶段4接入可信身份。

浏览器打开Core登录页，使用 .migration-runtime/test-account-private.txt 的合成账号检查登录、社区、通知、我的报修和地图。Community健康检查应返回UP。原版恢复地址19090仍保留，独立人工核对。

部署包、日志、运行元数据和密码均被Git忽略。必要的安全验证摘要见 VALIDATION.md；原始私有日志留在runtime和本地构建日志，不提交。
