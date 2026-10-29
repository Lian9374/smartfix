# B：使用微服务实验版

固定目录`C:\Users\zhour\smartfix-microservices`；分支`feature/community-microservice`。

入口：[19111登录](http://127.0.0.1:19111/login)。Core承载原网页和登录；Community A19112、B19113通过19114负载均衡。内部API不作为用户浏览器入口。数据库15483，与19090/15443原版保护资源分开。

账号：`.migration-runtime/stage9/test-accounts-private.json`。其中author/responder为提问者/回答者，tech为工程师，admin为管理员；密码仅看本地私有文件。Cookie为`SMARTFIX_STAGE9_CORE`；退出只清理这一配置名称。

## 启动与独立构建发布

先核对`.migration-runtime/stage9/resources-private.json`、真实PID/产物/数据库容器身份；已运行服务不要重复启动。`stage9.py`会拒绝错误分支、基线、挂载、库、端口或运行资源。数据库停止时先核对该清单的确切容器ID、15483绑定、stage9标签和独立卷，再仅启动这个容器；不使用模糊名字批量启动。

在上述固定目录执行（数据库已运行，四个应用均停止时）：

```powershell
python tools/migration/stage9.py start a
python tools/migration/stage9.py start b
python tools/migration/stage9.py start lb
python tools/migration/stage9.py start core
```

当前构建选择为`coreArtifactVersion=stage10-cookie-v2`、`communityArtifactVersion=stage10-v2`，不要更换为旧产物。

Core与Community分别构建，不要求同步发布。以下命令是既有构建方式，本交付阶段不重复构建或覆盖运行JAR：

```powershell
D:\Maven\apache-maven-3.9.16\bin\mvn.cmd -Pstage10-core-artifact -Dtest=MigrationIsolationGuardTest,Stage8InAppDeliveryTest,SecurityConfigTest -Dspring.profiles.active=test -Dspring.datasource.url=jdbc:h2:mem:cookie-isolation-unit package
D:\Maven\apache-maven-3.9.16\bin\mvn.cmd -f community-service/pom.xml -Pstage10-artifact -Dtest=MigrationIsolationGuardTest,CommunityIdentitySecurityTest,CommunityQuestionTest,CommunityReportTest package
```

PowerShell中遇到参数解析时，将完整`-D...`参数各自用单引号包裹。产物分别在`.migration-runtime/stage9/build-core-v2/`和`build-community-v2/`。重建前先停止对应产物的所有使用者；Community为a和b，Core只停core。记录新JAR摘要及兼容验证，再分别启动，不替换原版发布包。阶段8已实际验证仅发布兼容Community、Core不重新发布；不兼容协议升级未验证。

## 人工验收

1. author登录19111：社区搜索、分页、我的内容、问题详情及编辑/撤回；responder回答，author看通知并跳回同一讨论。
2. author采纳/取消；admin检查举报、审核、隐藏/恢复、历史与统一审计；确认其他业务审计仍存在。
3. 三角色各自工作区；报修、分派、接单、维修记录、完成确认到关闭；上传图片并按权限读取。
4. 确认19090仍可登录。内部健康页200仅代表存活，不能替代这些操作。

外发邮件保持关闭；站内通知可靠异步。本地共享宿主机与PG服务器，不是AWS生产部署。全部压测已按用户要求停止，不运行phase10/phase10_quick或snapshot/restore来演示日常启动。
