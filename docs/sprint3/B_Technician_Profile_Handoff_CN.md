# S3-B-01 技师资料模块：实现与交接

基线：`upstream/main` 的 `0359d99`。开发分支：`feature/SCRUM-UserB2-WANGPENGRUI`。
提交目标：团队仓库 `Lian9374/smartfix`（`upstream`）。
2026-09-30，用户确认团队已按 Sprint 3 文档完成 Day 1 对齐，技师模块使用 **V10**。
本文件记录实际改动与验证边界，不代表已经评审、合并或部署。

## 本次范围

- `technician` 模块：技师档案、技能集合、服务区域集合、可用性、资料查询与保存。
- `GET/POST /technician/profile`：技师维护自己的资料；首次 GET 不创建数据，首次有效 POST 创建档案。
- `TechnicianDirectoryService.findCandidates(category, locationId)`：提供满足 F1–F5 的候选目录。
- 两条资料路由的显式授权、CSRF、表单校验、服务端身份检查及测试。
- `V10__create_technician_profiles.sql`：三张表、外键、唯一约束、枚举约束和查询索引。

S3-B-02 的工作量统计及推荐排序、S3-B-03 的派单事务、S3-B-04 的派单页面与全量授权、
S3-B-06 的请求访问检查不在本次实现内。没有用零工单数或虚构派单模拟这些能力。

## 接口与数据约定

| 接口 | 返回 / 行为 |
|---|---|
| `getProfile(actorUserId)` | `Optional<TechnicianProfileResponse>`；仅启用的 TECHNICIAN；尚无档案时为空 |
| `updateProfile(actorUserId, command)` | 返回保存后的档案；首次创建，后续更新；身份来自登录主体 |
| `findCandidates(category, locationId)` | `List<TechnicianCandidateResponse>`；过滤账号状态、当前角色、档案状态、技能、区域及请假状态；按档案 ID 稳定返回 |

- `profileId` 是技师档案 ID，`userId` 是账号 ID，两者不可互换。
- 候选 DTO 包含姓名、技能、区域与可用性；推荐优先级和工单数量留给 S3-B-02。
- 使用已有 `UserService.listUsers()` 读取账号状态和姓名，只向调用方返回合格候选。
  数据量增长时可与 A 协商增加批量摘要接口；当前没有访问其他模块的 Repository。
- 技能和区域是档案内的值集合，使用 `@ElementCollection` 存入独立关联表；
  没有为无独立生命周期的每个技能或区域创建额外实体类。
- 表单至少选择一个技能、一个有效服务区域和一种可用性。选择 `ON_LEAVE` 后不会成为候选。
- 表单没有可编辑的 `userId`、`profileId` 或 `active`；控制器只绑定允许的偏好字段。
- `version` 用于识别过期表单，数据库乐观锁防止并发覆盖；重复建档或过期修改返回 409。
- 停用账号 / 非技师由服务层拒绝；无效档案无法通过自行保存重新启用。
- 地点只通过 `LocationService` 校验。不存在或停用的地点拒绝保存，页面保留输入并显示错误。

## 数据库合并依赖

V10 的结构依赖为 **V2（users）与 V3（locations）**。
生产库仍由 Flyway 管理，`ddl-auto` 保持 `none`。

当前基线存在两个前置问题：

1. **V5 的文件头不是合法 SQL**，团队文档已分配 C 修复。本次未改动已合并迁移。
2. **V6–V9 尚未出现在当前 main**。与 C 协调顺序，在较小编号的迁移合入后再应用 V10，
   避免后续较小编号被 Flyway 的默认顺序规则阻挡。不要靠启用 out-of-order 或修改历史版本绕过。

本次不会对现有开发库执行迁移或删库。H2 测试显式执行 V10 验证映射和约束，
这不等于 PostgreSQL 的全量迁移或 Sprint 2 数据库升级已通过。

## 页面与团队交接

页面使用当前 main 已有的 `site.css`，保持现有 Home / Sign out 导航、表单和焦点样式。
当前 main 没有规划中的共享片段，待 A 的界面分支合并后接入其布局。

- **给 A**：技师导航入口 `/technician/profile`，建议 active key 为 `technician-profile`。
  本次未修改 A 负责的首页或共享布局，开发时可直接打开该地址。
- **给 C**：确认 V10 的合并顺序；后续以 `userId` 对接当前技师，`profileId` 仅用于档案。
- **给后续 B 任务**：候选查询已提供资格过滤；派单时仍须重新校验资格，不能信任页面上的旧候选列表。

## 验证

针对本次模块：

```powershell
mvn -B '-Dtest=TechnicianDirectoryServiceTest,TechnicianProfileRepositoryTest,SecurityConfigTest' '-Dit.test=TechnicianProfileIT' verify
```

覆盖资料创建和更新、集合替换、外键与唯一约束、资格过滤、身份与角色限制、
CSRF、非法输入、停用账号、表单伪造 ID、过期修改和页面渲染。
页面测试输出在 `target/technician-preview/`，仅含隔离测试数据。

全量默认验证：

```powershell
mvn -B clean verify
```

基线已复现：`AttachmentPersistenceIT` 要求 `TEST_DB_URL` 等 PostgreSQL 参数，
却被默认 Failsafe 执行，导致无数据库配置时失败。原 `MigrationIT` 才被排除到 `postgres-it`。
本次不跳过或删除附件测试来制造全量通过。

真实库验证应在 V5 修复、迁移顺序对齐且专用 `_test` 数据库就绪后运行：

```powershell
mvn -B -Ppostgres-it clean verify
```

2026-09-30 实际结果（本机 Maven 3.9.14 / JDK 25.0.4，编译目标 Java 21，非 JDK 21 或 CI 证据）：

| 检查 | 结果 |
|---|---|
| 定向验证命令 | **70 通过、0 失败、0 跳过，BUILD SUCCESS**：54 个单元/仓储/权限用例 + 16 个技师页面集成用例 |
| 完整 `mvn -B clean verify` | 192 个单元/Web/仓储用例通过；31 个集成用例中 30 通过、1 失败。唯一失败为基线已有的 `AttachmentPersistenceIT` 缺少 `TEST_DB_URL` |
| 页面标签修复后的验证 | 重跑上述 70 个定向用例通过；修复技能和可用性标签的模板映射，并增加非空标签断言 |
| Edge 无头浏览器 | 初次填写、已保存、校验错误三种真实渲染结果，在 390 / 1280 / 1440 宽度均无横向溢出；表单控件均有非空关联标签；Tab 能到达保存按钮，焦点可见 |
| PostgreSQL 全量 / 升级迁移 | **未执行**：未配置专用测试数据库，且 V5 / 迁移顺序前置尚未解决 |

浏览器检查基于真实 Thymeleaf 测试响应与当前 `site.css`，不冒充已部署应用的验收。
截图与检查记录：`target/technician-preview/profile-390.png`、`profile-1440.png`、`browser-checks.json`。
实际保存及服务端权限由 `TechnicianProfileIT` 覆盖。

## 待完成的团队验收

- V5 修复及迁移顺序合入，PostgreSQL 干净库 / 升级库验证。
- A 的共享布局与技师导航集成。
- PR 交叉评审、CI、合并及任务追踪更新。

以上完成前，本模块是可评审的实现，不声明 Sprint 3 的 Definition of Done 已全部满足。
