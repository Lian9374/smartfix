# 阶段8：双进程正确性与故障隔离

本轮仅使用15473的新Docker容器/数据卷、独立角色、附件目录和身份密钥。
从19096当前恢复库取得完整快照，另行克隆到本轮Core；社区历史和最新状态复制到本轮Community。没有重新启用第7阶段的过期源库，没有覆盖原备份或原部署。

## 入口

- Core： http://127.0.0.1:19101/login ，一个JVM，Cookie为SMARTFIX_STAGE8_CORE。
- Community a/b：19102、19103，两个独立Java进程，各-Xmx384m、连接池5；使用同一Community库、发帖守卫、命令表及outbox。
- 内部负载均衡：19104，只转发受身份保护的社区API；健康探测摘除失败后端，POST不跨后端自动重试。
- 数据库：15473，仅本轮角色可访问对应业务库。角色、容器ID及挂载见EVIDENCE.json。
- 19090/15443原版恢复基线、19091/19092阶段6环境及19093–19096阶段7环境均保留。

这些是独立数据集。阶段8新增内容不会自动写回19096或原版。

## 完整人工验收清单

账号及密码只在私有 `.migration-runtime/stage8/test-accounts-private.json`。
author/responder为普通用户，admin/admin2为管理员，tech为工程师；quota是限流测试账号，不用于日常验收。可以新注册普通账号，避免累计测试配额影响人工发帖。

1. 普通用户登录19101，浏览列表、搜索、筛选和分页；在“我的内容”检查问题及回答。新建问题、回答、编辑和撤回；检查他人不能编辑自己的内容。
2. 提问人采纳、取消后再次采纳；回答者检查通知与跳转。检查撤回采纳答案后问题重新打开。
3. 举报内容，管理员在 `/admin/community/reports?view=ALL` 审核、隐藏、恢复，查看HANDLED历史。检查普通用户不能打开审核页，隐藏内容不向无权限用户泄露。
4. 在 `/admin/community/audit` 检查社区记录及其他业务来源，切换分页；在 `/notifications` 检查新通知及历史讨论2/3跳转。
5. 三角色完成一次维修：普通用户提交并附真实图片，管理员审核/派工，tech接受、记录维修、提交完成，普通用户确认，管理员关闭；检查图片和站内通知。工程师预设ELECTRICAL技能和测试区域；可在档案页查看/调整。
6. 已完成的自动维修样例和图片ID见EVIDENCE.json的repair字段。用author查看，tech查看其工单，admin查看审核及关闭结果；陌生人不能读取该图片。
7. 核对 http://127.0.0.1:19090/login 原版恢复入口仍可用。不要为验收停止或修改原版资源。

同一浏览器普通标签页共享同一入口Session；换账号时退出，或使用隔离浏览器会话。
后端身份、摘除和领取者退出的破坏性操作已自动执行；人工验收不要求再随意结束Java进程。

## 构建与自动验收

工作目录固定为C:/Users/zhour/smartfix-microservices，分支feature/community-microservice。
先运行两侧 `mvn -Pstage8-artifact '-Dmaven.test.skip=true' package`，Community追加 `-f community-service/pom.xml`。产物位于本轮私有build-core/build-community，不覆盖其他阶段运行JAR。

首次运行 `python tools/migration/stage8.py provision` 创建本轮专属资源。已存在时不会清库或覆盖备份；初始化未完成时只能诊断后续跑，不用reset/clean。
按顺序执行 `stage8.py start core`、`start a`、`start b`、`start lb`。停止只用该脚本的 `stop core/a/b/lb`，它核对本轮资源及进程完整路径，不删卷、不操作原版。

自动脚本按顺序执行，不能并行操作生命周期：

- `python tools/migration/verify_stage8.py`：真实双JVM、发帖/采纳/审核竞争、幂等、事件及领取者恢复、后端故障与独立发布。
- `python tools/migration/verify_stage8_community.py`：将既有完整社区回归明确绑定到本轮端口、数据库和生命周期；不会停止阶段6服务。
- `python tools/migration/verify_stage8_repair.py`：三角色、图片、完整维修和自动站内通知。
- `python tools/migration/verify_stage8_extra.py`：采纳/隐藏竞争、接收端停机补发、工程师页面及陌生人权限。

测试会创建新合成配额/回答账号，不清除旧数据，也不重置用户限额。重新运行会增加测试记录。
全部私钥、密码、转储、照片和日志留在被忽略的私有runtime；安全摘要提交Git。

## 发布与通知模式

兼容r2产物在实际JAR中新增可选版本诊断资源；其SHA不同、后端返回的release不同，业务契约不变。本轮验证的是这个兼容补丁的独立发布，不是任意未来破坏性schema升级。
发布Community时记录Core的PID和JAR摘要前后相同。Core在另外的“接收端停机恢复”故障测试中会主动重启；这与独立发布是不同实验。

本地验收保持邮件和旧全局/SLA扫描关闭。仅stage8场景启用Core拥有的IN_APP调度，复用原DeliveryWorker自动投递维修站内通知，不扫描EMAIL，不增加第三个业务服务。社区通知仍走事务outbox/inbox链路。

本阶段必须在19101完成新的完整人工验收后才可进入阶段9。此前19096验收不代替本轮验收，不自动开始压测。
