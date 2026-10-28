# 阶段3实际验证记录

日期：2026-10-28。范围：本地独立应用/数据/运行骨架，不是完成社区业务拆分。

## PASS

- 根Core与独立Community分别Maven打包成功，两个可执行JAR独立产出，无业务模块互相依赖。
- Core和Community各8项启动隔离单元测试通过（共16），0失败/0错误/0跳过；未运行全量回归套件。
- 最终JAR实际负面启动：错误数据库URL、H2 test配合独立Flyway URL绕过均在数据源初始化前拒绝。测试使用127.0.0.1:1/sentinel，不用原库作负面目标。
- 新容器/新卷/127.0.0.1:15453，两个独立数据库与账号：本库可连接，对方库CONNECT均拒绝。
- Community独立Flyway三条迁移成功，仅含三张社区表及自己的flyway_schema_history，无users表/跨账号库外键。
- PostgreSQL实际事务测试拒绝跨问题采纳、拒绝举报同时指向问题和回答；测试事务回滚，不保留测试内容。
- Core独立新库从私有备份恢复，可真实注册、登录；独立Cookie及社区、通知、我的报修、地图HTTP页面检查通过。
- Community健康UP，未完成身份接入前业务API403，未开放匿名业务接口。
- Community单独停止/启动时Core保持UP；最终Core单独停止/启动时Community保持UP，重启后同一测试账号仍可登录。
- 最终运行JAR指纹与资源记录一致；19090保护实例与19091/19092迁移服务健康UP。
- 原项目全文件指纹复核无变化，原容器和15443保护库容器ID/State/Mounts与开始时一致；未执行原版数据/附件/云端修改。

## 发现并修复的问题

最初产物与最后保护源码更新存在打包时序差异。一次H2/Flyway负面探测未提前拒绝，尝试连接15443后因未提供密码被拒绝，没有执行迁移SQL。已修正检查顺序、增加H2绕过回归测试、顺序重建，并在最终JAR用无效哨兵地址重新验证通过。保护容器及原文件最终复核一致；不将初次失败标为通过。

## NOT_RUN / 明确限制

新社区业务API、跨服务身份、业务迁移、可靠事件、双社区实例并发和正式压测均尚未实现/验证，属于后续阶段。Core暂保留内置社区实现和本地社区表，Community空schema不接收业务写入，不能声称已切换权威数据所有者。阶段3只验证HTTP表单与页面响应，不等于浏览器视觉/键盘验收。Java启动白名单限本地阶段3，云部署须另行设计。

## 命令和私有证据位置

构建：mvn -B -ntp -Dtest=MigrationIsolationGuardTest package；独立Community加 -f community-service/pom.xml。
运行：python tools/migration/stage3.py provision/start/verify/stop；最终产物负面测试：python tools/migration/verify_stage3_artifacts.py。
构建日志：stage3-core-build.log、stage3-community-build.log；测试报告在各自target/surefire-reports。
运行资源、凭据、验证摘要和日志仅存在被忽略且私有的.migration-runtime目录，不提交Git。此文仅安全摘要。

## 人工入口

Core：http://127.0.0.1:19091/login，测试账号在 .migration-runtime/test-account-private.txt，不在聊天输出密码。
Community：http://127.0.0.1:19092/actuator/health，预期UP；业务接口403是阶段3预期行为。
原版恢复保护入口：http://127.0.0.1:19090/login，保持保留。
请确认Core能登录、上述页面和导航正常，再单独指示阶段4；此阶段不自动推进。
