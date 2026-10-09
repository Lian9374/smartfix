# 2026-10-09 本地 Java 编译冲突修复

启动日志中的 39 个编译错误来自未完成的 rebase，三个 Java 文件中仍有 Git 冲突标记。未重置、丢弃分支，也未修改数据库迁移或认证设计来绕过编译。

## 修复内容

- SecurityConfig.java：合并社区、通知、公告访问规则与账号密码、管理员和工程师工作台入口；保留服务器端角色限制、CSRF 和资源级授权边界。
- TechnicianDirectoryService.java：保留工程师资料与候选人逻辑，以及有管理员权限检查的资料目录查询。
- WorkOrderController.java：合并地点和原始报修信息依赖，保留筛选、分页、操作反馈与校验；移除工单详情重复查询。
- 页面保留新版工作台设计和有效的派单、维修记录、照片、完成说明等功能。
- 测试保留上游新增用例及修复后的 DTO 类型、审计清理、文本转义检查；将旧标题断言同步为新版页面的 Reported issue。
- AssignmentMigrationIT 保留从实际已有 V10 迁移升级及历史记录约束测试，避免在已存在资料表的 V22 数据库中重复执行建表夹具；未修改任何迁移 SQL。本次未运行 PostgreSQL 迁移测试。

## 已处理的 15 个冲突文件

路径均相对于仓库根目录。

1. src/main/java/com/smartfix/auth/config/SecurityConfig.java
2. src/main/java/com/smartfix/technician/service/TechnicianDirectoryService.java
3. src/main/java/com/smartfix/workorder/controller/WorkOrderController.java
4. src/main/resources/templates/admin/request-queue.html
5. src/main/resources/templates/dispatch/assign.html
6. src/main/resources/templates/request/detail.html
7. src/main/resources/templates/request/review.html
8. src/main/resources/templates/technician/profile.html
9. src/main/resources/templates/workorder/detail.html
10. src/main/resources/templates/workorder/mine.html
11. src/test/java/com/smartfix/dispatch/AssignmentMigrationIT.java
12. src/test/java/com/smartfix/dispatch/TechnicianAccessIT.java
13. src/test/java/com/smartfix/request/RequestWorkflowTest.java
14. src/test/java/com/smartfix/technician/TechnicianProfileIT.java
15. src/test/java/com/smartfix/technician/repository/TechnicianProfileRepositoryTest.java

原 rebase 完成后另将远程分支的新合并提交 d8c0425 合入修复分支，保留双方提交历史及远程文档更新。相对于该远程提交，最终代码差异集中在 SecurityConfig、WorkOrderController、AssignmentMigrationIT 和 RequestWorkflowTest；其余冲突文件的有效修改已包含在远程提交中。

## 实际验证

- `mvn -o -DskipTests compile`：BUILD SUCCESS，229 个主代码源文件编译通过。
- `mvn -o -DskipTests test-compile`：BUILD SUCCESS，84 个测试源文件编译通过。
- `mvn -o "-Dtest=SecurityConfigTest,TechnicianDirectoryServiceTest,TechnicianProfileRepositoryTest,RequestWorkflowTest" "-Dit.test=RoleWorkspacesIT,TechnicianAccessIT,TechnicianProfileIT,DispatchPageIT" verify`：BUILD SUCCESS，126 个单元测试和 58 个集成测试通过，失败、错误、跳过均为 0。
- 首轮测试发现旧页面标题断言不匹配；修复后上述测试全部重新运行通过。
- `git diff --check` 通过；源代码、模板和测试中无遗留冲突标记。
- 本地测试日志位于被 Git 忽略的 target/compile-conflict-verification-final.log。

交付分支：`fix/local-java-compile-20261009`。不强制推送原已共享分支，不直接修改 main。
