# User C 开发版本验证记录

基于 main `0359d99`，2026-10-01。

- `mvn test` / `mvn clean package`：通过。
- 最终 `mvn clean verify`：通过；212 项常规/H2/MockMvc 测试，以及 14 项 H2 认证集成测试；失败、错误、跳过均为 0。
- 可运行 Spring Boot JAR 已检查 ZIP 结构及 BOOT-INF 依赖。
- V1–V9 SQL：在 PGlite（PostgreSQL WASM）空库执行通过。
- V5 → V9：在带 Sprint 2 请求数据的 PGlite 库执行通过；原有数据保留，version 为 0；状态、初始历史唯一性、评价约束检查通过。
- 模块检查：没有生产代码跨模块导入其他模块 Repository；git diff --check 通过。

## 验证边界

H2 的 C 流程测试复用真实 Service、持久化、文件存储和模板。未完成的 B 指派只读接口，以及 H2 不支持的 PostgreSQL ticket SQL，使用仅在测试目录中的替身。

原生 PostgreSQL 集成验收尚未通过本次环境执行：本环境未运行原生服务。PGlite 的 SQL 结果不替代原生 Flyway/JDBC、多连接、ticket counter 或队友模块联调；尝试 WASM wire bridge 的 Flyway/JDBC 检查未通过，不计入通过项。

已提供 `RequestMigrationIT` 与 `RequestWorkflowPostgresIT`，可用 `TEST_DB_*` 和 `mvn -Ppostgres-it clean verify` 在专用 `_test` 数据库上执行；B 的真实派单、E 的 SLA/通知/审计和 A 的最终 UI 也需要联合验证。

迁移编号和 D-08 重新打开窗口仍是团队确认事项。请先处理独立的 V5 修复，再按迁移顺序拆分正式 PR；本分支用于 C 整体代码联调，不表示所有团队验收项已完成。

## 后续补充验证（2026-10-01）

- 修改提交页，使附件限制提示读取 AttachmentProperties；新增一个 MockMvc 工作流回归用例。
- 新 Controller 使用既有可运行 JAR 中的依赖通过 javac 编译检查。
- 使用真实 SpringTemplateEngine 对修改的提示模板独立渲染；默认及修改后的数量/大小配置检查通过。
- 尝试运行 RequestWorkflowTest、RequestTransitionTest、RequestSubmissionServiceTest、RequestQueryControllerTest、SecurityConfigTest。Maven 在解析父 POM 时失败：先前依赖缓存已不存在，且当前无法解析 repo.maven.apache.org；本轮测试未进入编译/执行阶段。
- 原生 PostgreSQL 可执行文件存在，但原安装包截断、缺少 postgres.bki 等初始化资源，不能建立测试集群；未运行 PostgreSQL IT。
- 上面的 226 项通过结果属于补充修改之前的版本，不代表本轮新回归用例已经通过。

恢复依赖后先执行：

```bash
mvn -Dtest=RequestWorkflowTest,RequestTransitionTest,RequestSubmissionServiceTest,RequestQueryControllerTest,SecurityConfigTest test
```

随后在独立原生测试库运行 postgres-it，并与 B/E 进行真实模块联调。
