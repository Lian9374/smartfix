# 第九阶段运行与人工检查

唯一目录 C:\Users\zhour\smartfix-microservices，唯一分支 feature/community-microservice。仅管理 .migration-runtime/stage9/resources-private.json 明确登记的进程及15483容器/卷，不停原版、19090、19101或其他项目。密码、账号、密钥、快照、附件和原始结果均留在ignored私有目录。

入口：http://127.0.0.1:19111/login；内部LB19114；Community19112/19113/19115；数据库15483。Cookie SMARTFIX_STAGE9_CORE。合成角色账号见 .migration-runtime/stage9/test-accounts-private.json；合成压测用户perf9_0至perf9_999使用其中同一测试密码，不在聊天或Git暴露。原版恢复入口 http://127.0.0.1:19090/login。19101仍是第8阶段验收环境，阶段9不向它发负载。

首次准备已真实执行，不重复provision或seed。若需从停止状态恢复，只按manifest核对PID/产物/容器身份后启动本阶段服务。脚本只绑定回环，启动前Java守卫检查精确URL/库角色/端口/附件/Cookie/对端，拒绝保护基线；HTTP驱动及重定向只接受固定19111–19115白名单，不接受域名、AWS、localhost别名或原版端口。

```powershell
python tools/migration/stage9.py start core
python tools/migration/stage9.py start a
python tools/migration/stage9.py start b
python tools/migration/stage9.py start c
python tools/migration/stage9.py start lb
python tools/migration/perf9_suite.py --list
```

启动已运行服务会拒绝。变更副本数只停止/启动a、b、c，LB健康摘除；Core不动。正式每轮准备由suite先停止本阶段服务、恢复固定快照到新库、再启动指定副本数。恢复失败时保留旧库、新失败库和日志，诊断后重试；禁止清空旧数据冒充恢复。

```powershell
# 本阶段只允许小量；每个独立低量验证最多20秒、1业务迭代/秒。
python tools/migration/verify_stage9.py
python tools/migration/perf9_observe.py --samples 2 --interval 15
python -m unittest discover -s tools/migration -p test_perf9.py -v
```

verify脚本还真实验证API及页面回答、维修提交、Cookie与指标权限，原始检查记录只写本轮私有目录。不会发邮件。账号配额不会在重试时被删除；必要时只在停本轮应用后执行stage9.py restore恢复新一代库，随后重启本轮服务。快照已建立，不再执行snapshot覆盖它。

人工验收：确认19111地址、独立Cookie与三角色可登录；打开社区、问题、我的报修、通知、审计，核对合成数据；阅读PLAN.json/DESIGN.md的目标、数据量与三类实验；确认19090仍能正常登录。页面验收不需要进行正式负载或点击内部API。确认目标可接受后再授权阶段10。未经阶段10授权，--formal-authorized和suite --execute都拒绝；阶段9不运行正式矩阵。

本轮开始时19090和阶段8数据库已在09:51停止，早于新阶段9容器创建。按V2通用约束3a核对隔离恢复容器ID/15443/独立卷后重启19090，使用既有smartfix-stage1-app.py，旧运行日志先保存到stage9私有目录。三角色9项只读页面复查通过，没有重注册或重置恢复库。仅另启动stage8数据库供读取快照；阶段8应用没有被此阶段修改或发布。原smartfix-db在10:10:07由用户主动启动，已明确确认；本轮未启动/停止/连接它，保持用户当前运行状态。
