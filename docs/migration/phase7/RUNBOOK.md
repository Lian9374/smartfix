# 阶段7：隔离迁移与增量恢复

固定工作目录：`C:/Users/zhour/smartfix-microservices`；分支：`feature/community-microservice`。
本阶段接续已验收并提交的阶段6，不操作原项目、原库、AWS或19090/15443保护资源。

## 当前入口与权威数据

- 19090：原版保护恢复实例，继续按原方式使用，未被本轮演练切换。
- 19091/19092/15453：保留的阶段6环境，与本阶段数据分离。
- 19093：从备份另建的单体源副本；本阶段结束时社区写入关闭，数据停留在切换水位。
- 19094/19095：本阶段Community与Core；本阶段结束时Community业务写入关闭，19095可读取切换后数据，但不作为当前写入口。
- **19096：恢复了新增数据的旧单体，当前阶段7唯一权威社区写入口。**
- 15463：新建Docker PostgreSQL，容器/卷/角色/库名见EVIDENCE.json及私有resources-private.json。

这里“唯一”限于本阶段7数据集；19090保护版和19091阶段6版各用自己的独立数据。
请先按下面步骤查看19096的数据。本轮不要在其他阶段7入口新增内容；若在19096产生新写入，后续必须以恢复库为源再次同步，不能直接启用停留在旧水位的19095/Community。

## 本次人工验收

打开 http://127.0.0.1:19096/login 。合成账号在私有文件：
`C:/Users/zhour/smartfix-microservices/.migration-runtime/stage7/test-accounts-private.json`。
其中author/responder是普通用户，admin是管理员；文件没有数据库密码，不要提交或公开测试密码。

1. 用author登录，打开 `/community/questions/2`，检查迁移前的历史夹具、原采纳答案，以及初次拷贝后追加的回答。
2. 打开 `/community/questions/3`，检查切换后新增的问题、回答与已采纳状态；这条记录在旧源库中不存在，已恢复到19096新库。
3. 打开 `/notifications`，点击回答通知，确认能跳转到同一问题。退出换responder，确认采纳通知及跳转。
4. 用admin登录，打开 `/admin/community/reports?view=ALL` 和 `/admin/community/audit`，检查历史及新增的举报、决定、隐藏/恢复审计；其他账号审计仍保留。
5. 检查角色工作区和维修页面，再确认 http://127.0.0.1:19090/login 仍可进入。

同一浏览器普通标签页共享同一入口的Session，请退出后换账号，或使用隔离浏览器会话。
源备份附件记录为0：历史附件读取为N/A；附件目录独立拷贝和摘要核对已经完成，不能把空数据集当附件读取成功。

## 脚本与私有证据

`python tools/migration/stage7.py run` 首次创建新资源并执行演练；已有资源时只续跑未完成检查点，不清库、不覆盖档案、不从零重做。
`python tools/migration/stage7.py status` 只输出安全检查摘要。
构建使用 `mvn -Pstage7-artifact '-Dmaven.test.skip=true' package`；Community追加 `-f community-service/pom.xml`。
构建产物写入本阶段私有目录，不替换运行中的阶段6 JAR。

全部转储、行快照、密钥、配置和原始日志位于被Git忽略的 `.migration-runtime/stage7/`。
`archives/02-initial-monolith.dump` 为另建源库的完整逻辑备份；`05-community-after-new-writes.dump`、`06-core-after-new-writes.dump` 为新增写入后的双库备份；01/03/04 JSON为逐字段比较快照。
安全摘要、文件大小和SHA-256见EVIDENCE.json。转储使用独占新建方式写入；不替换已有文件。

如需停止某个本阶段应用，可分别执行 `python tools/migration/stage7.py stop source`、`stop community`、`stop core`、`stop restore_delta`。脚本核对容器和PID的JAR完整路径及端口，仅停止指定的本阶段进程；不停止19090/19091/19092，不删除数据库、容器或卷。本次为了人工验收保留全部入口运行，stop命令未实际执行。

## 切换与回退规则

单体源和Community的业务表都有数据库写入闸门，控制表由stage7_admin拥有，应用角色不能修改。业务DML获取事务锁；暂停操作获取同一把锁，等待在途事务排空后关闸。脚本通过真实PostgreSQL事务和数据库拒绝写入验证这一过程。

流程：初次拷贝 → 源库继续产生回答 → 暂停并排空源写入 → 最终逐字段同步 → 处理旧站内投递任务并记录水位 → 改用19095 → 无新写入时关Community、回到19093 → 再切回Community → 产生新业务数据 → 拒绝直接回到过期源库 → 双库归档 → 恢复到全新的restore_delta库 → 导入Community增量与最终状态 → 核对全部字段、通知、审计和序列 → 启用19096。

历史导入不生成历史业务事件。只在可丢弃源库中按原去重键完成PENDING的IN_APP任务，通知和任务状态同事务提交；EMAIL任务不变，邮件和全局扫描器保持关闭。最终通知/任务一起同步，避免遗漏初次快照后的旧任务。

恢复时保留Core全量账号、维修、统一审计、通知、inbox和任务，再导入最新社区表。新社区审计已可靠投影至Core，按source/source_id保留；不能拿Community本地审计ID覆盖统一审计中的USER等业务ID。

恢复程序仍使用第1阶段已验收的旧JAR。恢复库保留追加后的schema 29；旧程序按其原有Flyway规则验证已知的27项迁移，并提示存在未来版本。没有clean、repair、改历史迁移或删除新schema，实际启动、登录与数据页面均已验证。

本轮最终停留在增量恢复后的单体。19096人工验收未确认前，不标阶段7最终通过，不进入阶段8。
