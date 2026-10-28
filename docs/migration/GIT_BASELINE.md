# 迁移分支准备
独立工作区：C:\Users\zhour\smartfix-microservices
当前分支：feature/community-microservice
基线提交：cfff844a8ecf0575617426ec80b3b59450ac88a2
基线分支：baseline/original-system
基线标签：baseline/pre-community-microservice
本地 main 保持原 main：6cb329f4fe9b1f7e2e708b9ee00d81c456740fc0
历史从私有备份导入，无远端推送配置，不共享原 .git。
保留了源分支 735eadc933afaf7cb91c5e0e47ef67ecdaef1d21 的历史，并将快照中未提交的安全源码变更保存为基线提交。
完整原始备份仍在 C:\Users\zhour\SmartFix-Migration-Backups\20261028-105244；未把运行数据、数据库转储、密钥、临时制图目录和新生成二进制文档加入新基线。
原项目 refs、Git 状态及完整文件指纹复核无变化。
本次只准备 Git 基线与分支，没有开始阶段 2；原阶段 1 浏览器人工验收仍待确认。
未来回退先核查未提交修改/数据状态；不得对原项目执行 reset/clean，不把 Git 回退当数据库恢复。

补充：无扩展名的部署 Procfile 已补入版本控制。最终完整准备基线以 baseline/ready-for-migration 标签为准。未跟踪的 docx 与演示材料保留在磁盘及完整文件备份，不作为代码提交。
