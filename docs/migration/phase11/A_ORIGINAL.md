# A：继续使用原版

本卡只说明入口，不授权启动、修复或替换原系统。

原目录：`C:\Users\zhour\smartfix`。原配置本地网页端口8080、PostgreSQL端口5433；这些配置不是当前正在运行的证明。现有AWS入口本次未查询、未修改，继续使用用户原有书签，不提供猜测地址。

已保留的原版恢复副本：[19090登录](http://127.0.0.1:19090/login)。目录为`C:\Users\zhour\smartfix-baseline-verify-20261028-105244`，数据库15443、容器`sf-stage1-20261028-105721-db`，Cookie为`SMARTFIX_BASELINE_VERIFY`。它运行原版代码，不是微服务版。合成验收账号在备份目录的`database-20261028-105721/test-accounts-private.txt`，不要公开密码。

本阶段又从原备份新建独立恢复：[19121登录](http://127.0.0.1:19121/login)，数据库15493；资源、账号及日志仅位于迁移仓库`.migration-runtime/stage11/`。账号文件为`test-accounts-private.json`。它证明备份可再次恢复，不替换19090。

完整备份：`C:\Users\zhour\SmartFix-Migration-Backups\20261028-105244`，包含`project-snapshot`、4421文件指纹、Git状态/未提交差异、冷备`database-20261028-105721/original-stopped-pgdata.tar`、逻辑备份`recovered-current.dump`和必要私有配置。既有备份不覆盖。

先登录19090，分别检查社区、通知和所属角色工作区。新恢复19121的三角色账号是本次新库内合成账号；恢复前全部原表行数已与原备份核对。原备份附件记录为0，历史附件读取为N/A。

原版与实验版的数据互不自动同步。实验版新问题、维修或附件不会自动出现在19090或原库。原系统恢复若需要启动，只按已核对的隔离runbook另行操作；不要运行原目录的repair脚本，也不要把实验库连接参数写回原项目。
