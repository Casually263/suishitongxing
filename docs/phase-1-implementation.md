# 阶段1实现范围与设计补充

日期：2026-10-28。用户本轮明确要求开始基础工程开发，环境采用已有JDK17、项目隔离MySQL及本机Redis兼容服务推进。整体需求仍以原设计DOCX、总控Prompt及阶段0基线为准。

## 真实业务链路

网站注册→MySQL用户/档案/角色事务写入→账号或邮箱登录→BCrypt验证→JWT access token+Redis refresh记录→前端内存保存access token→请求过滤器逐次查询用户状态、auth_version和DB权限→本人档案读取/乐观锁保存→刷新页面以HttpOnly cookie恢复→退出时服务端撤销。

管理端独立登录渠道→RBAC及channel校验→数据库真实账号数量、分页用户列表→停用/启用用户→旧token立即失效。不能停用自己或最后一个有效管理员。没有前端固定用户数组或mock API，也没有默认内存管理员。Spring默认UserDetailsService自动配置已排除。

## 代码目录与职责

| 目录 | 本轮内容 |
| --- | --- |
| backend/src/main/java/com/suishi/common | Result、全局异常、traceId、OpenAPI |
| backend/src/main/java/com/suishi/security | JWT过滤与签发、RBAC、邮箱加密、Redis会话/限流、配置校验 |
| backend/src/main/java/com/suishi/system | Controller、DTO/VO、Service、MyBatis-Plus Entity/Mapper、显式开发seed |
| backend/src/main/resources/db/migration | V1身份/RBAC/审计表，V2角色权限/兴趣标签种子，Flyway跟踪执行 |
| backend/src/test | 加密/JWT安全单测和真实MySQL/Redis接口集成测试 |
| web/src | Vue3/TS/Vite、Pinia渠道会话、Axios代理/刷新、Router权限、vue-i18n、Element Plus档案与管理组件 |
| web/tests | 登录重定向安全单测、真实浏览器端到端测试 |
| scripts | 环境检查、隔离本机依赖、环境注入、测试库准备、后端启动 |
| deploy | MySQL8/Redis7开发Compose、应用覆盖配置、Dockerfile和Nginx SPA配置 |

## 本轮接口状态

以下已实现，其他设计接口保持待开发，不提供伪成功响应：

- `POST /api/portal/auth/register`：文化身份仅GUIDE/PARTNER；禁止客户端授予ADMIN；账号/邮箱唯一；协议确认；邮箱登记但未验证。
- `POST /api/portal/auth/login`、`POST /api/admin/auth/login`：真实DB/BCrypt，渠道隔离。
- 两网站渠道下`GET auth/csrf`、`POST auth/refresh`、`POST auth/logout`、`GET auth/me`。
- `GET/PUT /api/portal/profile`和`/api/app/profile`，以及`GET /api/app/auth/me`：同一Service，依JWT识别本人。
- `GET /api/common/meta`：DB兴趣标签、支持语言、真实功能开关和当前体验协议版本。
- `GET /api/admin/overview`：真实注册账号数及生成时间，明确包含开发/测试账号，尚非阶段7业务统计大屏。
- `GET /api/admin/users`、`PUT /api/admin/users/{id}/status`：分页、权限、乐观版本、撤销及操作日志。
- `GET /actuator/health`：实际DataSource/Redis健康检查；Actuator使用其标准响应，不属于业务Result接口。
- 开发环境`/v3/api-docs`与`/swagger-ui/index.html`，生产关闭。

当前没有微信测试/真实授权接口、邮件验证发送、数据导出/注销、剧情/节气/结对/AI/上传接口。客户端未展示这些功能的可点击假菜单。

## 安全与状态约定

JWT固定HS256，校验issuer/audience/exp及签名，密钥为服务端独立随机32字节。令牌不含邮箱、密码或权限快照；每次按DB校验，封禁与退出的auth_version变更即生效。

refresh是48字节随机不透明令牌，Redis仅存其SHA256键与会话元数据；Lua原子消费/轮换，用过的令牌有标记。首次重放只撤销对应auth_version，旧令牌不能在用户重新登录后不断撤销新会话。刷新需要白名单Origin、独立CSRF cookie/header匹配；登录、注册和刷新有Redis限流。Redis不可用时认证变更返回503，不切换假内存会话。

浏览器refresh/CSRF cookie使用HttpOnly、SameSite=Strict和渠道路径；dev本机HTTP关闭Secure，prod开启。access只存在Pinia/模块内存，localStorage只记语言。退出为该用户全渠道撤销，暂不提供单设备会话管理。

邮箱AES-256-GCM随机IV加密，规范化后HMAC-SHA256唯一检索；Base64存VARCHAR(768)，不保存明文。邮箱按本项目登录规则不区分大小写，不去除加号或点；最多254个UTF-8字节。密码至少12个字符且最多72个UTF-8字节，哈希成本12。SQL参数绑定，未知DTO字段拒绝，API ID用字符串。

注册/登录依数据库唯一性与身份验证，不要求业务幂等requestId；档案与管理状态用版本比较防覆盖。剧情推进的requestId幂等及奖励事务留在阶段3实现，不能声称本轮已经有通用业务幂等引擎。

## 数据与配置的最小差异

相对阶段0逻辑设计，role_type/status落地为可读字符串并有CHECK；email_enc使用Base64 VARCHAR；RBAC表仅保留本轮实际使用字段，系统公告/运营配置等在后续迁移增加。user_profile提供显示名，JSON存语言、时段、隐私与提醒偏好。MySQL连接初始化为UTC。

演示账号只有显式APP_BOOTSTRAP_ENABLED/APP_DEMO_ENABLED才创建，密码来自本地环境；重复启动不重置密码，不把已有普通用户名自动升为管理员。prod禁止演示用户，seed不是生产默认口令。初始语种为中/英，其他语种随后扩展。

## 环境验证边界

发现现有`E:\corretto-17.0.8`，本轮实际在Java17编译/运行，不修改全机Java25。该17补丁较旧，仅作为本机开发验证环境，生产镜像需使用维护中的17补丁并另行验收。

本轮启动独立MySQL8.0.43实例，数据在项目`.runtime/mysql/data`，端口3307；不启动或修改原有3306服务。开发库suishi_dev、测试库suishi_it分离，测试清理前强制核对DATABASE()。本机Memurai for Redis8.2兼容实例在6380，使用真实TCP、Lua和持久化；它不是Redis7，本轮结果不能写成“Redis7测试通过”。Redis7.4.7镜像标签已从官方镜像仓库核验，但Docker缺失，Compose实际启动尚未验证。

MinIO与上传不属于本轮身份基础链路，推迟至文件业务阶段准备，仍保留原技术要求与已发现的上游分发风险。生产Compose/HTTPS、备份恢复和跨端微信授权均未验收。

## 验证证据

最终执行命令、通过数量、截图及限制见[testing/phase-1-validation.md](testing/phase-1-validation.md)。依赖由package-lock精确锁定；Axios审计告警已修复。后端升级到已从[官方Maven仓库](https://repo.maven.apache.org/maven2/org/springframework/boot/spring-boot-starter-parent/maven-metadata.xml)核验的Boot3.5.16；这不等于完成全部后端依赖CVE审计。
