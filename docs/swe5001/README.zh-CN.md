# SWE5001 SmartFix：实现、运行与演示手册

## 1. 两门课如何区分

同一个仓库，两个版本：`main` 保留 SWE5006；`swe5001-cloud-native` 展示 SWE5001。本次从 `6cb329f` 开始，不修改 main，不新建另一个仓库。演示时打开分支链接，说明业务功能复用，新增成果是架构演进、消息可靠性、独立部署、云基础设施、DevOps 和性能验证。

这是 **Core + Notification Worker 两个独立服务的 MVP**。Request、Dispatch、Work Order 仍是 Core 中的 bounded contexts，不能说已经拆出四个微服务。原四服务 Proposal 的差异要在展示中说明。

## 2. 已实现的代码

- Core 保留原有登录、报修、审核、派工、维修、通知页面与权限规则。
- 通知 outbox → SQS → 独立 Python Worker → Core 通知投影，Worker 使用独立数据库。
- 重复消息去重、事务回滚、错误重试、DLQ；Core/Worker 通过版本化 JSON 契约通信。
- Core 多副本共享 JDBC Session 与附件卷；AWS 采用 EFS，登录不依赖粘性会话。
- 独立 Docker 镜像、本地完整 Compose、nginx 负载均衡、Prometheus 指标。
- AWS CloudFormation：ECS、ECR、RDS、EFS、SQS、Secrets Manager、ALB HTTPS、Cloud Map、CloudWatch、CPU 自动扩容策略。
- GitHub Actions 自动验证；手动触发、需要审批环境的镜像发布与部署；部署后 smoke。
- 真实网页表单 E2E、k6 递增负载、单副本/双副本对比、CPU/内存/队列采样工具。

实际执行状态与数据见 [verification.md](verification.md)。AWS 模板通过静态验证不等于部署成功；只有真实云上运行后才能在 Presentation 里展示 AWS 成果。

## 3. 本地运行

需要 Docker Desktop；本机直接测试需要 JDK 21、Maven 3.9+、Python 3.12+。不需要 Kubernetes。

```bash
git clone --branch swe5001-cloud-native https://github.com/Lian9374/smartfix.git
cd smartfix
docker compose -f compose.swe5001.yml up -d --build --wait --wait-timeout 300
python3 scripts/swe5001/e2e.py
```

打开：应用 http://localhost:8080；Prometheus http://localhost:9090；本地邮件箱 http://localhost:8025。监控 UI、数据库和队列端口只绑定本机回环地址。两个应用数据库分别映射 15432、15433，Worker 没有 Core 数据库凭据。

仅限这个本地教学环境的默认账户：管理员 `demo.admin` / `SmartFixDemo1!`。E2E 会通过管理员网页创建 `swe5001.requester` 和 `swe5001.tech`，完成首次强制修改密码后，使用 `SmartFixUser2!`。这些公开演示凭据不得用于公网。AWS 使用 Secrets Manager 随机密码和内部 token，模板不会使用本地默认密码。

配置本地环境可设置 `DEMO_ADMIN_PASSWORD`、`DEMO_USER_PASSWORD`、`SMARTFIX_INTERNAL_TOKEN`、`LOCAL_DB_PASSWORD` 等环境变量。修改已有账户的环境变量不会自动重置账户密码。测试脚本只用于专用教学/测试环境，会创建真实账户与报修记录。

停止但保留数据：`docker compose -f compose.swe5001.yml down`。只有明确不再需要本地演示数据时再使用 `down -v` 删除卷。

## 4. 验证与实现顺序

1. 审计原仓库并固定基线，写入设计和实施计划。
2. 用测试约束 Core 回写的去重、身份校验和事务边界；新增 QUEUED 状态与共享会话迁移。
3. 构建独立 Worker、独立 inbox、队列契约；验证失败不确认、成功提交后确认。
4. 启动真实 PostgreSQL、SQS 模拟器、Core、Worker；通过真实登录和 CSRF 表单跑完整业务流程。
5. 扩容到两个 Core/Worker，再跑业务链路，检查同一登录会话经过多个副本仍有效。
6. 跑负载测试，保存原始指标，不预先承诺扩容一定更快。
7. 验证 AWS 模板与流水线；获批费用并配置 AWS 账户后再部署。

```bash
mvn -B verify
(cd services/notification-worker && python3 -m unittest discover -s tests -v)
pip install cfn-lint==1.36.0
cfn-lint infra/aws/stack.json
```

可选真实 PostgreSQL 回归：先在专用本机 PostgreSQL 建立 `smartfix_test`，然后设置 `TEST_DB_URL=jdbc:postgresql://localhost:15432/smartfix_test`、`TEST_DB_USERNAME`、`TEST_DB_PASSWORD`，执行 `mvn -B -Ppostgres-it verify`。测试包含清理/迁移操作，必须只用独立 `_test` 数据库。默认 `verify` 不要求外部数据库。

## 5. 性能与扩容实验

```bash
bash scripts/swe5001/benchmark.sh
```

脚本先准备演示账户，然后依次启动 1、2 个 Core 副本；每个副本限制 0.5 CPU、1 GiB，Worker 固定 1 个。每轮 k6 以 2、5、10 VU 递增，使用真实登录/CSRF 提交报修，保存 `results/k6-1.json`、`k6-2.json` 和资源 CSV。`request_submission_ms` 是提交响应耗时；`iterations.rate` 是工作流每秒吞吐；`http_reqs.rate` 包含页面获取等所有 HTTP 请求，不应混淆。默认每阶段 20 秒，可设置 `STAGE_DURATION` 延长。

比较请求提交 p95、工作流吞吐、workflow_errors、CPU/内存、实例数、队列可见/处理中消息。数据库和 Worker 固定，增加 Core 不一定提高性能。短时、本机共享宿主机的实验只能作为本地证据；报告测试时间、机器、资源限制、工作负载和瓶颈。CSV 中空白队列值表示采集失败，不能当零。

## 6. AWS 费用估算与批准边界

当前没有创建 AWS 资源，也没有云端部署证据。按新加坡区域、1 Core + 1 Worker（各 0.5 vCPU/1 GiB）、小型单 AZ RDS、一个 ALB、少量 EFS/日志和消息，建议先预留 **US$5–10/天** 的教学预算，五天 **US$25–50**；这是保守规划区间，不是 AWS 报价，也未扣任何免费额度。扩容到四个实例、负载、日志、快照、数据传出和公网 IPv4 都会改变账单，域名/税费另计。

创建前在 [AWS Pricing Calculator](https://calculator.aws/) 按实际区域和时间重新计算并由账户所有者批准。官方计费来源：[Fargate](https://aws.amazon.com/fargate/pricing/)、[ALB](https://aws.amazon.com/elasticloadbalancing/pricing/)、[RDS PostgreSQL](https://aws.amazon.com/rds/postgresql/pricing/)、[EFS](https://aws.amazon.com/efs/pricing/)、[公网 IPv4/VPC](https://aws.amazon.com/vpc/pricing/)。停止 ECS 任务仍会产生 RDS、ALB、存储等费用。默认零个任务的基础栈也收费。

## 7. AWS 首次部署（费用获批之后）

准备专用 AWS 教学账户/区域、AWS CLI v2、域名 DNS、同区域 ACM 证书、GitHub OIDC 部署角色。模板要求 HTTPS；不通过公网 HTTP 暴露登录。部署角色应限于这个专用栈及对应 ECR/ECS/IAM 资源；OIDC 信任条件限定仓库 `Lian9374/smartfix` 与环境 `swe5001-demo`。GitHub Environment 配置 required reviewers；设置环境变量 `AWS_REGION`、secret `AWS_DEPLOY_ROLE_ARN`。OIDC 角色/信任配置依账户而定，不在模板中替你扩大账户权限。

1. 复制 `infra/aws/parameters.example.json` 为不提交的 `parameters.local.json`，填证书 ARN 与域名。
2. 费用批准后执行下面的首次创建命令。`CoreCount=0` / `WorkerCount=0`，先创建基础设施，暂不启动应用。

```bash
aws cloudformation create-stack --region ap-southeast-1 \
  --stack-name smartfix-swe5001 --template-body file://infra/aws/stack.json \
  --parameters file://infra/aws/parameters.local.json --capabilities CAPABILITY_IAM
aws cloudformation wait stack-create-complete --region ap-southeast-1 --stack-name smartfix-swe5001
```

3. 将域名指向输出 `AlbDns`。检查证书与 DNS 验证完成。
4. GitHub Actions 选择 SWE5001 deploy，选择分支 `swe5001-cloud-native`，填写栈名并确认费用批准。经过环境审批后，流水线测试、构建 amd64 镜像并以 SHA/run id 不可变 tag 发布到 ECR。
5. `deploy.sh` 只更新已存在的栈，不创建栈；更新镜像后运行一次数据库初始化任务，用 RDS master 创建两个数据库/账户并撤回公共 CONNECT；之后激活服务，等待稳定并 smoke。
6. 在 Secrets Manager 读取栈输出 `AdminSecret`，用 `demo.admin` 登录；不要把密码、token、AWS key 写进仓库或截图。
7. 用实际 HTTPS 地址运行 `smoke.sh`；在专用云端教学环境可设置 `BASE_URL`、`DEMO_ADMIN_PASSWORD` 后运行 E2E。实际云部署、日志、CI 成功运行和负载结果要另外保存证据。

获取 AWS 资源指标：`python3 scripts/swe5001/collect-aws-metrics.py --stack smartfix-swe5001 --minutes 15 > results/aws-metrics.json`（需要 boto3 与已配置的账户授权）。CloudWatch 中查看 dashboard、ECS CPU/内存/任务数、SQS backlog、DLQ alarm。告警目前没有 SNS 通知接收人。

## 8. 演示顺序（建议 8–10 分钟技术演示）

先展示 SWE5006 基线与 SWE5001 分支差异、DDD 边界和两张架构图。随后 requester 提交 → admin 审核派工 → technician 开工完成 → requester 异步收到通知。展示 Worker 独立进程/数据库、SQS 接收与去重。短暂停 Worker 后提交一条请求，展示积压；恢复 Worker 后展示清空与通知到达。扩容 Core 后用同一登录会话访问，展示两个 upstream 和共享附件。最后展示真实 CI 运行、云资源与指标、单/双副本压测数据以及局限。

只有已经运行的项目才展示“已完成”。本地 ElasticMQ 不等于 AWS SQS；模板不等于云上成功；短时扩容实验不等于无限扩展。SWE5001 业务范围是通知交付 MVP，未实现四个独立业务服务、Kubernetes、多区域容灾或生产级邮件 exactly-once。

## 9. 故障与清理

Core pending 增长：检查 SQS 可达性、IAM 和 outbox failures；QUEUED 增长：检查 Worker、私有 Core DNS/回写、Worker DB、DLQ。修复根因后，显式运行 `replay-dlq.py --source <DLQ URL> --destination <队列 URL>`，保留原任务身份；不要直接删除 Core outbox。

云环境清理必须先确认不再需要演示数据：降低服务与 autoscaling 最小数量；关闭 RDS deletion protection；按 CloudFormation 删除栈。RDS 设置 Snapshot 保留，ECR 有镜像时需先处理镜像才能删仓库，EFS/快照/日志/Secrets 也要检查账单。不要通过删除项目分支来认为云资源已停止计费。


分布式模式中的 SKIPPED 邮件任务是最终回执，界面和服务会拒绝用相同身份重试。先修复联系地址，后续新事件使用新任务；若需要重发历史已跳过邮件，应先实现新的投递身份/版本。Core 中旧的同步 drain 在分布式模式下关闭，管理员操作不会绕过独立 Worker。

## 10. 实际故障验证与构建备选

E2E 完成后可运行 `docker compose -f compose.swe5001.yml run --rm fault-tests`，它使用单独的本地测试容器访问两个演示数据库，注入回写后的崩溃并发场景。这个测试容器有显式的测试数据库凭据；常驻 Worker 不拥有 Core 数据库凭据。仅在本机专用演示数据上执行。

同一真实消息重发：先安装 Worker requirements，再运行 `python3 scripts/swe5001/reliability.py`。多副本校验：`EXPECTED_CORE_COUNT=2 python3 scripts/swe5001/e2e.py`，它必须实际看到至少两个 upstream，并反复验证同一附件内容。

若已在本机/CI 执行 `mvn -B verify`，可直接用 `docker build -f Dockerfile.runtime -t smartfix-core:swe5001 .` 打包已验证成品，再执行 `docker compose -f compose.swe5001.yml build worker` 和 `docker compose -f compose.swe5001.yml up -d --no-build`。标准 Dockerfile 仍支持完整容器内构建；runtime Dockerfile 避免重复下载 Maven 构建依赖。

## 11. 文件导航与剩余工作量

Core 改造见 `src/main/java/com/smartfix/cloud/`、`application-distributed.yml`、V28/V29；旧同步入口的限制在 `notification/service/DeliveryDispatcher.java` 与 `DeliveryAdministrationService.java`。Worker 位于 `services/notification-worker/`，两个服务分别构建，不共享进程或数据库凭据。

本地部署见 `compose.swe5001.yml` 和 `infra/local/`；AWS 见 `infra/aws/stack.json`；CI/CD 见 `.github/workflows/swe5001-*.yml`；真实表单、故障注入、压测与指标采集见 `scripts/swe5001/`；原始结果见 `results/`。历史设计和实施计划位于 `docs/superpowers/`。

本地代码、回归、E2E、故障与扩容实验已执行。剩余是账户相关的真实 AWS 部署与云上证据，不是已经完成的云端交付：在证书/DNS/OIDC 权限就绪且费用获批后，建议预留半天至一天完成首次部署、排障与演示排练，再预留约两小时跑云端负载并导出指标。证书验证、账户权限和容量限制可能延长时间。


GitHub 干净运行器也已通过整条自动验证链路：[成功运行记录](https://github.com/Lian9374/smartfix/actions/runs/39227770132)。这包含构建、单双副本 E2E 和故障测试，不代表已执行 AWS 发布/部署。
