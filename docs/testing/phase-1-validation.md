# 阶段1测试与构建报告

日期：2026-10-28（Asia/Shanghai）。范围：真实身份基础工程；完整文化业务和生产部署不在本轮完成范围。

## 执行环境

JDK17.0.8（E盘已有Corretto，Maven实际使用同一JDK）、Maven3.9.12、Node24.14.0、npm11.11.0、Spring Boot3.5.16、MySQL8.0.43项目隔离实例3307、Memurai for Redis8.2兼容实例6380、Windows本机Edge无头浏览器。Docker/WSL缺失，未把Memurai结果标为Redis7通过。

MySQL开发库suishi_dev、集成库suishi_it分离，Flyway V1/V2在空库真实执行成功；重启应用验证迁移校验及开发seed不重复、不重置密码。系统原有MySQL服务/数据目录未修改。

## 实际命令与结果

| 命令或检查 | 结果 |
| --- | --- |
| JDK17环境下`mvn -B -s backend/maven-settings.xml -f backend/pom.xml verify`，RUN_INTEGRATION_TESTS=true | BUILD SUCCESS；3单元+7真实集成，共10通过，0失败/错误/跳过 |
| `npm.cmd --prefix web run typecheck` | 退出0，Vue/TS类型检查通过 |
| `npm.cmd --prefix web run build` | 退出0，Vite7.3.7生产构建成功，生成web/dist |
| `npm.cmd --prefix web run test:unit` | 8项通过，0失败；最终Vitest4.1.12，JSON另存 |
| `npm.cmd --prefix web run test:e2e` | 3条真实浏览器流程通过，无接口mock，无跳过；最终后端Boot3.5.16 |
| `npm.cmd --prefix web audit` | 更新Axios1.21.0/Vite7.3.7/Vitest4.1.12后，最终已报告漏洞0 |
| GET /actuator/health | UP，实际MySQL/Redis连接 |
| GET /v3/api-docs | 200，生成18个路径，标题“岁时同行 API” |
| GET 网站/login | 200，Vite代理业务联调成功 |
| PowerShell解析所有项目脚本 | 0语法错误；环境脚本实际执行，项目身份预检true |

## 覆盖的关键行为

- 加密使用随机IV、同邮箱HMAC一致、AES-GCM篡改拒绝；密钥缺失/过弱/重复拒绝。
- JWT签名、issuer/audience、auth_version与渠道校验；令牌不包含密码/邮箱/角色权限快照。
- 真实注册持久化、账号/邮箱大小写唯一；拒绝ADMIN身份/未知roles字段及超长UTF-8密码。
- 个人档案中文内容/JSON/兴趣关联真实写入、app/portal读取一致、旧版本保存409。
- 401/403、普通用户不能登录管理端、管理员portal令牌不能代替管理登录、列表不泄露哈希或邮箱密文。
- 管理员停用后旧JWT失效，恢复不会恢复旧JWT；不能停用自己或最后一个管理员。
- refresh需要Origin+CSRF，轮换后重放401且撤销原会话；旧重放不会不断撤销用户后续重新登录的新会话。
- Redis Lua并发消费恰好一正常一重放；真实限流触发；退出后的JWT与refresh均不可用。
- 错误方法405、错误Content-Type415、坏JSON400和统一错误code/traceId。
- 浏览器真实注册→登录→档案保存→刷新恢复及双标签并发恢复→退出→路由拦截；管理登录/真实分页/刷新恢复；中英切换及390px登录、360px注册无水平溢出。

## 可复核产物

- [前端单测JSON](phase-1-web-unit.json)、[汇总JSON](phase-1-results.json)。
- 后端原始JUnit报告位于backend/target/surefire-reports和failsafe-reports；浏览器原始JSON位于web/test-results/results.json（构建/运行产物被Git忽略）。
- 页面截图：[桌面登录](phase-1-login-desktop.png)、[移动登录](phase-1-login-mobile.png)、[用户首页](phase-1-home.png)、[文化档案](phase-1-profile.png)、[用户管理](phase-1-admin-users.png)。已实际查看这些图像；移动装饰已减淡，Element Plus组件随语言切换，无主要重叠或裁切。

## 限制及尚未通过的验收

未运行Docker/Compose、Redis7原版、MinIO源码/镜像、生产HTTPS和备份恢复、微信授权、模型调用、性能压测或完整后端CVE扫描。npm审计只反映查询时的已知公告，不证明绝无漏洞。JDK17.0.8是已有旧补丁，生产需另行使用维护版本验证。

本轮未完成节气、剧情/双视角、真人结对、AI、上传/审核/岁时册、小程序或16步总演示，不提前勾选。MinIO安装准备移到文件阶段，保留需求。当前只完成阶段1核心编码与本机兼容服务验证，Redis7/Docker目标环境验证仍是阶段1待办。

所有开发口令仅在被忽略的本地配置中，报告不复制密钥、令牌或口令。浏览器录制trace关闭，截图使用开发测试账号。Windows构建需先停止占用同一JAR的Java进程，该问题修复后最终打包与测试均成功。

验证后已停止本任务启动的Vite/后端及项目隔离MySQL/Redis，保留持久化数据；启动脚本重复运行和带数据目录核验的停止脚本均已实际执行。环境JSON为测试期间快照，不是持续在线状态。重新体验请按README先启动依赖与两个应用。
