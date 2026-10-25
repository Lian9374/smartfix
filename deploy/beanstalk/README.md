# SmartFix：Beanstalk 部署包

适用于 Java SE / Corretto 21 / Amazon Linux 2023。上传的是 JAR ZIP，不使用 Docker/Tomcat WAR。

GitHub Actions 产物传递、手动部署入口及配置见 [CI.md](CI.md)。CI 直接封装已验证的同一份 JAR，不调用下面的本地 Maven 打包流程。

## 本地打包

在项目根目录的 CMD 运行：

```cmd
powershell -NoProfile -ExecutionPolicy Bypass -File deploy\beanstalk\build.ps1
```

脚本先执行 `mvn clean verify`，失败时不生成部署 ZIP。若刚完成验证，且源码未变化，可以复用已验证的 JAR：

```cmd
powershell -NoProfile -ExecutionPolicy Bypass -File deploy\beanstalk\build.ps1 -UseVerifiedJar
```

输出位于 `target/beanstalk/<时间>/`，不提交生成文件。ZIP 根目录含 `app.jar`、`Procfile`、`config/`、`.platform/`、`certs/`。生产配置在 JAR 外，只有部署包启用它；开发环境不受影响。CA 是从 AWS 官方地址下载的公开证书，不含私钥。

## 创建收费资源前可以完成的工作

1. 本地构建与 ZIP 检查。
2. 配置 Beanstalk service role 和 EC2 instance profile。
3. 有自有域名时，在新加坡 ACM 申请证书并完成 DNS 验证。

随后建立独立 EFS、Beanstalk 示例环境；最后创建 RDS、迁移数据、设置以下变量，再上传 SmartFix ZIP。没有 RDS/EFS 时先使用 AWS 示例程序，不能用本包冒充健康应用。

## 环境属性（无密码）

```text
DB_URL=jdbc:postgresql://实际RDS域名:5432/smartfix?sslmode=verify-full&sslrootcert=/var/app/current/certs/rds-global-bundle.pem
SMARTFIX_EFS_ID=fs-实际ID
SMARTFIX_EFS_ACCESS_POINT=fsap-实际ID
SMARTFIX_NOTIFICATION_EMAIL_ENABLED=false
SMARTFIX_BOOTSTRAP_ADMIN_ENABLED=false
```

`DB_USERNAME`、`DB_PASSWORD` 通过 Beanstalk 环境 Secret 注入。不要提交密码，不要把整个 JSON 当作密码。若启用 JSON 字段提取，选支持该功能的当前平台版本，并给 instance profile 指定 Secret 的读取权限。

从空库上线才临时启用管理员初始化，密码使用单独 Secret。恢复已有数据库时不重新初始化管理员。初始化成功后移除密码引用并关闭该功能。

## EFS 和网络前提

- EFS 独立于环境创建，在应用使用的两个可用区建立挂载目标。
- Access Point 根目录 `/smartfix/uploads`；POSIX UID/GID `10001:10001`；创建目录 owner `10001:10001`，permissions `0770`。
- EFS 安全组允许来源 `smartfix-app-sg` 的 TCP 2049。
- EC2 instance profile 允许指定 EFS/AP 的 `ClientMount`、`ClientWrite`，EFS policy 也允许该角色使用 AP；启用 TLS。
- Hook 自动挂载到 `/mnt/smartfix-uploads` 并测试 webapp 写入。挂载失败中止发布，禁止回退到临时磁盘。
- 应用实例需有软件包下载出口。当前方案：公网子网、公网 IPv4、IGW；入站 HTTP 只允许 ALB，5000 不公开。
- RDS 安全组仅允许应用组访问 5432，公开访问设为否。
- 可信代理范围匹配现有 VPC 公网子网 `10.20.0.0/24`、`10.20.1.0/24`。变更网络时必须调整配置；线上验证客户端地址和 HTTPS 转发头。

## 环境和 HTTPS

- 负载均衡型环境，ALB，Min=Max=1，暂用 All at once 发布；会短暂停机并丢失内存登录会话。
- 先用 AWS 示例程序建立环境，再部署本包。运行时需数据库与 EFS 均已就绪。
- ALB 默认进程健康检查 `/actuator/health`，状态码 200。
- ACM 新加坡证书绑定 ALB 443，HTTP 80 重定向到 HTTPS，自有子域名指向环境/ALB。
- Secure Cookie 已开启，通过最终 HTTPS 域名验收登录；不要用关闭 Secure Cookie 来掩盖代理问题。
- 未接入共享 Session/统一限流前不扩为多实例。

## 验收与限制

本地检查不代表已经成功部署 AWS。必须在线验证：Flyway、登录、权限、提交/取消请求、指派、维修、用户验收、管理员关闭、评分、通知、照片上传下载；替换实例后附件仍保留。

备份数据库和 EFS，二者不是同一份备份。演示后停止/删除数据库会导致业务不可用；单独停止 RDS 也不会停止 ALB、EC2、EFS 等费用。清理前验证可恢复的数据备份。
