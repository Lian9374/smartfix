# 2026-10-09 Flyway 历史分支兼容升级

## 原因

Java 编译通过，但本地 PostgreSQL 已执行到 V24，之后才合入 V10、V11、V14、V16。Flyway 正常启动会拒绝这些较早却尚未执行的迁移。V23 已建工程师/派单表，V20 已建通知表；直接打开乱序执行仍会撞上同名表。另外，新库正常执行 V14 后再执行 V20 也会重复建通知表。

## 实现及边界

- V1–V24 原始 SQL 保持不变，不删除历史、不修补已应用的校验值，不关闭迁移校验。
- LegacyMigrationCompatibility 使用 Flyway 的 beforeEachMigrate / afterEachMigrate 事务回调，只处理 V10、V11、V14、V20 的已知同名表。暂存表与原始迁移、数据恢复均在同一 PostgreSQL 迁移事务内执行。
- 恢复原主键、全部原列、行数、读通知时间、工程师技能和服务区域、派单历史，以及身份序列；用双向 EXCEPT ALL 核对原始数据。无 id 的关联表不做序列操作。
- 数据验证成功后仅删除本次创建的临时归档，无 CASCADE。存在额外外键、缺失关联表、无法恢复的列或不符合约束的数据时拒绝升级并回滚当前迁移，交由人工检查。
- V14 给历史通知生成稳定的 LEGACY_NOTIFICATION:<id> 去重键；V20 执行时保留已有去重键。新增 V25 补齐通知去重、默认时间和数据约束。
- 恢复模式必须显式开启，只允许补执行 V10、V11、V14、V16。其他未知较早迁移被拒绝。每条迁移仍真实执行并由 Flyway 记录；完成后使用普通配置再次严格 validate。
- 普通 application.yml 未开启 outOfOrder，未设置忽略迁移或关闭校验。重启无需恢复开关。
- 回滚以单条迁移为单位，先前已成功的迁移可能已提交；修正导致失败的数据后可以安全重试。升级必须在停止业务服务、备份后执行，不用自动清库解决问题。

Flyway 回调机制参考：[官方回调事件](https://documentation.red-gate.com/flyway/reference/callback-events)、[事务内迁移回调示例](https://www.red-gate.com/hub/product-learning/flyway/try-before-you-commit-in-flyway/)。

## 使用

普通启动仍使用：

```powershell
& .\start-smartfix.ps1
```

仅对上述历史分支数据库，在停止服务并备份后，一次性使用：

```powershell
& .\start-smartfix.ps1 -ReconcileLegacyMigrations
```

等价的应用启动参数为 `--smartfix.database.reconcile-legacy-migrations=true`。不要把它长期加到部署配置，也不要同时启动多个升级进程。

## 本机实际结果

- 修复前本地备份：`local-db-backups/smartfix-before-legacy-recovery-20261009.dump`，78,854 字节，已确认 pg_restore 可读取备份目录。该目录被 Git 忽略，备份不上传。
- 本地 V10、V11、V14、V16、V25 共五条迁移执行成功。数据库共有 23 条成功的版本记录，最新版本为 V25。
- 升级前后 20 张既有业务表的完整行数据指纹一致，包括原有 7 个账号、3 个报修请求。
- 关闭恢复开关后，实际运行 `start-smartfix.ps1`，普通 Flyway 严格验证全部 23 条迁移成功；没有待执行迁移。
- 实际 HTTP 检查：`/actuator/health` 返回 `UP`，`/login` 返回 200。
- 编译通过。以下定向验证 BUILD SUCCESS：13 个单元测试、13 个真实 PostgreSQL 集成测试，失败、错误、跳过均为 0。未声称已运行整个测试套件。

```powershell
mvn -o -Ppostgres-it "-Dtest=TechnicianDirectoryServiceTest" "-Dit.test=LegacyMigrationRecoveryPostgresIT,MigrationIT,AccountInitializationPostgresIT,AssignmentMigrationIT,CommunityMigrationPostgresIT,RequestMigrationIT,AttachmentPersistenceIT,CampusCatalogueMigrationPostgresIT" verify
```

测试使用专用 `smartfix_migration_recovery_test` 数据库中的随机隔离 schema；没有用业务库做测试。五个新增回归覆盖旧库数据保留、新库 V14/V20 去重保留、非法数据回滚、拒绝未知较早迁移及拒绝额外外键依赖。现有手动初始化 Flyway 的迁移测试已注册相同回调；旧 RequestMigrationIT 自建 Spring 上下文还补齐了 Boot 的 Duration 转换器。

本机日志位于 `target/legacy-migration-verification-success.log`、`target/legacy-recovery-local-startup.log` 和 `target/legacy-recovery-normal-startup.log`，均被 Git 忽略。
