# 阶段4：可信身份与社区权限/发帖控制基础

Core仍使用原Session、ActiveAccountFilter、CSRF及全部原社区页面。本阶段没有切换发布/回复/通知业务路径；阶段5再贯通业务链路。

## 访问与密钥

在迁移Core登录后访问 http://127.0.0.1:19091/community/connection，可看到Community确认的当前userId/role和Core批量补齐的本人显示名。不会返回JWT或私钥；这是只读连接验收入口，不是给浏览器签发通用Bearer token的接口。

Community http://127.0.0.1:19092/actuator/health 仍匿名可用；/internal/community/v1/** 使用无Session的Bearer认证，不接受客户端userId/role Header，不接受Core Cookie。端口仅回环开放，浏览器拿不到签发凭证，未配置跨域放行。本地同一操作系统用户是信任边界；云端TLS、独立服务账号/仅Core挂载私钥须在云阶段落地。

私钥、公钥仅在忽略且私有的 .migration-runtime/identity 下保存。Core加载私钥和公钥，Community仅配置公钥路径。启动脚本检测已有密钥后启用身份功能，不自动覆盖密钥。当前kid固定stage4-core-1，未知kid拒绝；多kid重叠轮换尚未实现，不能声称已验证轮换。

采用Spring Security/Nimbus RS256，检查固定issuer/audience、正整数sub、三种角色、安全版本、非强制改密、iat/nbf/exp、最长30秒、5秒时钟容差；拒绝伪造签名。Core签发前再次读取账号当前状态、角色、安全版本和强制改密。已签发凭证存在短期在途撤销窗口，不声称即时全局撤销。

## 权限与本地并发

Community新增只读identity、管理员access-check、作者edit-context用于基础验收。管理员不因此拥有修改别人内容的权限；非作者404，作者保留读取自己的隐藏内容上下文，editable=false。ERROR调度允许返回正确错误码，不把404再次变成认证错误。

Community V4新增community_posting_guards(user_id唯一)。CommunityPostingGuard在同一本地READ_COMMITTED事务中执行首次建行、行锁、重复检查、配额和业务保存回调；无Core账号表依赖。默认问题两分钟大小写不敏感重复检测；问题/回答分别24小时20条，不按隐藏/撤回状态豁免；不新增最小发布间隔。配置属性沿用原CommunityProperties的开关、窗口和上限，可按原属性名覆盖；时间在获得用户行锁后捕获，页面尚未切换，不影响当前Core已有配置。

Core的CommunityIdentityClient.displayNames对一页ID去重后一次调用原公开显示名接口；Community不反向访问Core账号库。原页面批量显示名实现保留，后续迁移使用该适配层。

## 验证命令

根目录运行Core：mvn -B -ntp "-Dtest=CommunityIdentityClientTest,MigrationIsolationGuardTest" test。
Community：mvn -B -ntp -f community-service/pom.xml "-Dtest=CommunityIdentitySecurityTest,MigrationIsolationGuardTest" package。
PostgreSQL测试需STAGE4_POSTGRES=true，运行 -Dtest=PostingPostgresTest -Dsmartfix.test.runtime=本工作区/.migration-runtime。仅固定15453迁移社区库，测试会清理本次生成的测试内容，不能指向原版/保护库。
实际HTTP：python tools/migration/verify_stage4.py，仅操作19091/19092及15453迁移库；临时改变合成测试账号状态后恢复，不改原账号/保护库。测试凭证工具MintTestToken.java只供本机验收，不部署为HTTP端点，不打印令牌到聊天。

## 人工检查

使用 .migration-runtime/test-account-private.txt 账号登录19091，打开 /community/connection，核对Community身份与本人一致。再核对原社区、通知、报修、地图及19090恢复实例仍可用。不要提交密码或截图中的私有数据。

权限基础不等于完整社区业务已迁移；写API、可靠outbox、数据切换、双JVM竞争与正式压测仍在后续阶段。第三阶段验证工具已适配V4表和匿名401，无需重跑provision。完成后停止，不自动进入阶段5。
