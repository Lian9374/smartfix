# 原版保护约束
原项目 C:/Users/zhour/smartfix 只读。后续仅在 Workspace.json 的 workRoot 工作。
不启动原 start-smartfix.ps1 或 repair-smartfix-local.ps1，不修改原库、卷、附件、进程及 AWS。
原版恢复副本已在独立数据库完成恢复验证；19090/15443及其目录、卷和容器作为保护基线保留，后续迁移另建环境。禁止直接运行复制来的原版启动/修复脚本。
必须使用独立数据库、凭据、附件副本、端口和不同 Session Cookie；邮件不得发给真实收件人。
备份私有保存；源码恢复副本不含 .git，避免指回原仓库。阶段1浏览器验收已由用户明确确认通过。
唯一代码工作区为 smartfix-microservices，唯一迁移分支为 feature/community-microservice；不得修改 main、移动已有基线标签、自动合并或推送。
阶段3和阶段4浏览器验收已由用户明确确认；当前仅授权阶段5最小回答/outbox/通知链路。仅合成讨论900000000001通过Community API读写，其他社区路径保持原实现，不进入阶段6。阶段5两用户浏览器验收仍待确认。代码回退不能替代数据恢复。
