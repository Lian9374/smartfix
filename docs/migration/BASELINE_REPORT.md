# 第 1 阶段验证报告（续办后）

状态：自动验证通过，等待用户浏览器验收。尚不进入第 2 阶段。

## 原版保护
原项目：C:\Users\zhour\smartfix。最终逐文件指纹校验无差异，原 Git 状态此前已校验不变。
原容器 smartfix-db 仍为 exited；未启动/停止原容器。未改原数据库卷、源码、配置、附件或 AWS。
用户明确选择本目录版本；18080 的 smartfix-swe5006-cd 演示版不属于本任务，未使用其数据。

## 备份与恢复
完整文件备份：C:\Users\zhour\SmartFix-Migration-Backups\20261028-105244，4421 个文件。
当前数据库备份：C:\Users\zhour\SmartFix-Migration-Backups\20261028-105244\database-20261028-105721。
original-stopped-pgdata.tar 为已停止原卷的只读冷备份。源因异常退出可能需崩溃恢复；恢复已在新卷中实际完成。
recovered-current.dump 为从已恢复克隆导出的 PostgreSQL 一致性逻辑备份。
新数据库：sf-stage1-20261028-105721-db，独立卷、127.0.0.1:15443；原卷只读挂载用于导出。
恢复基线数据：11 账号、7 报修、1 问题、1 回答、11 通知，详见 table-counts.json（创建测试数据前）。
原附件记录 0 条；默认原 uploads 目录不存在，无已有附件可供历史读取检查，本项不适用。没有声称读取过历史附件。
源码恢复目录：C:\Users\zhour\smartfix-baseline-verify-20261028-105244；副本已从完整文件备份恢复并逐文件校验。
构建成功，未运行完整单元/集成测试套件；mvn -DskipTests package 不能当作测试通过。

## 实际运行与验证
恢复原版运行地址：http://127.0.0.1:19090/login
独立 Cookie：SMARTFIX_BASELINE_VERIFY；应用仅监听回环；独立附件目录；外发邮件关闭，通知调度/急切投递关闭。
16 项 HTTP 冒烟检查通过：真实注册，三角色表单登录，社区、通知、角色列表页面，提交及取消报修。
完整维修链路 7 项通过：提交→管理员审核派工→技术员接单→维修记录→完成→请求人确认→管理员关闭。
所有写操作仅作用于恢复测试库；合成账号凭据私有文件：C:\Users\zhour\SmartFix-Migration-Backups\20261028-105244\database-20261028-105721\test-accounts-private.txt。
证据：C:\Users\zhour\smartfix-baseline-verify-20261028-105244\stage1-smoke-results.json；C:\Users\zhour\smartfix-baseline-verify-20261028-105244\stage1-workflow-results.json；stage1-build.log；stage1-application.log。
自动检查是 HTTP/数据库验收，不等于已完成浏览器视觉/键盘检查；历史账号密码未改动。

## 用户手动验收
在本机浏览器打开上述 19090 地址，凭私有账号文件分别登录三角色，核对社区列表、通知页与角色工作区。
确认可见历史社区内容，核对完整维修链路测试数据，检查页面/导航是否正常。
有真实历史账号可自行登录副本验证历史通知；不要把密码发到聊天。
确认原目录/原启动方式保留。原容器原先停止，因此本任务没有声称原 8080 正在运行。

## 原版继续使用与停止试验
原目录原启动脚本保留，但运行 start-smartfix.ps1 会启动原 DB 并可能执行 Flyway；本阶段没有执行。
当前可用的隔离原版恢复实例为 19090；不是微服务改造版，源代码未拆分。
停止试验时只管理 Workspace.json 中 baselineAppPid（先核对对应 java 命令是本恢复 jar）与 cloneDatabaseContainer。
不要关闭 18080 演示版、smartfix-db、Docker 引擎或其他项目，也不要删卷和备份。
新增试验数据只在恢复库，不会自动同步回原库。
用户确认手动验收前，nextStageAllowed 保持 false。
