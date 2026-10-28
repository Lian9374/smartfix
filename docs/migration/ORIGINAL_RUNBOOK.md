
原目录原启动脚本保留，但运行 start-smartfix.ps1 会启动原 DB 并可能执行 Flyway；本阶段没有执行。
当前可用的隔离原版恢复实例为 19090；不是微服务改造版，源代码未拆分。
停止试验时只管理 Workspace.json 中 baselineAppPid（先核对对应 java 命令是本恢复 jar）与 cloneDatabaseContainer。
不要关闭 18080 演示版、smartfix-db、Docker 引擎或其他项目，也不要删卷和备份。
新增试验数据只在恢复库，不会自动同步回原库。
用户确认手动验收前，nextStageAllowed 保持 false。
