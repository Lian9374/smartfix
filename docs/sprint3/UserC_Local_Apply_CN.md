# 在 Mac 的 VS Code 中接入 User C 代码

本次代码基于 main `0359d99`。当前 GitHub 连接拒绝写入（403），因此尚未建立远程 C3 分支。交付包包含完整源码、Git bundle、补丁和协作说明；任选一种接入方式，不要重复应用。

## 推荐：导入已整理的分支

把 `SmartFix_Sprint3_UserC.bundle` 下载到 Downloads。打开原来的 smartfix 文件夹，在 VS Code 的 Terminal 中执行：

```bash
cd '/Users/haoyangwang/Documents/01_学习(经历)与研究/硕士学习/NUS/Sem 1/SWE5006/Project/smartfix'
git status --short
```

确认工作区干净后继续（若有修改，先提交或另行保存）：

```bash
git fetch origin
git switch main
git pull --ff-only origin main
git fetch "$HOME/Downloads/SmartFix_Sprint3_UserC.bundle" feature/SCRUM-UserC3-WANGHAOYANG:feature/SCRUM-UserC3-WANGHAOYANG
git switch feature/SCRUM-UserC3-WANGHAOYANG
git status
mvn test
mvn clean verify
```

这个方式保留两个有意义的提交：独立的 V5 文件头修复，以及 C 的流程/工单代码、测试和说明。如果本地已存在同名分支，先检查它的内容；不要用 force 覆盖。可以导入到另一个本地名字后比较。

如果 origin/main 后续有新提交，需要把最新 main 合入 C3，再解决冲突和运行测试。B 的指派实现是否已经合并，需要进一步核对；此开发版本仍以 ActiveAssignmentLookup 适配接口连接 B。

本地检查和团队接口核对完成后，可由你的 GitHub 账号推送：

```bash
git push -u origin feature/SCRUM-UserC3-WANGHAOYANG
```

正式 PR 按开发计划拆分；V5 修复优先单独提 PR。当前完整开发分支用于联调，不应把“代码已写出”直接当作全员验收完成。

## 替代：应用补丁

如果更希望自己创建分支和提交，可以下载 `SmartFix_Sprint3_UserC.patch`，在最新 main 的干净工作区执行：

```bash
git switch -c feature/SCRUM-UserC3-WANGHAOYANG
git apply --check "$HOME/Downloads/SmartFix_Sprint3_UserC.patch"
git apply "$HOME/Downloads/SmartFix_Sprint3_UserC.patch"
git status --short
mvn test
mvn clean verify
```

check 若失败，应按最新 main 合并对应文件，不直接覆盖整个项目。补丁的基础版本和团队当前 main 不同，是可能出现冲突的正常原因。

V5 可以单独暂存和提交，再提交其余代码：

```bash
git add src/main/resources/db/migration/V5__create_request_attachments.sql
git commit -m "fix(request): comment invalid attachment migration header"
git add pom.xml src/main src/test docs/sprint3/UserC_Implementation_Handoff_CN.md docs/sprint3/UserC_Verification_CN.md docs/sprint3/UserC_Local_Apply_CN.md
git commit -m "feat(request): implement Sprint 3 lifecycle and work orders"
```

完整源码 ZIP 适合查看和对照。常规协作优先使用 bundle 或补丁，保持原来的 Git 历史。

## 数据库与后续联调

生产配置仍由 Flyway 管理 schema，不使用 create-drop 启动应用。

原生 PostgreSQL 检查：使用独立 `_test` 数据库，按 `UserC_Implementation_Handoff_CN.md` 中的 TEST_DB_* 命令运行 `mvn -Ppostgres-it clean verify`。首次启动旧库前，先确认 V5 修正版的校验和一致，以及全员迁移编号登记。

先阅读协作说明中的 B、E 接入点。重新打开窗口可以用 SMARTFIX_REQUEST_REOPEN_WINDOW 调整，0s 为当前待团队确认的默认值。类、DTO、函数和适配接口可以继续随着同学的实际代码调整，修改后同步测试与说明。
