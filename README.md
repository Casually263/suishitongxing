# 岁时同行

日常打开项目请看[本机运行指南](docs/run-guide.md)：两个终端启动、访问地址、演示账号、关闭步骤及常见问题。

团队协作请看[私有仓库与团队开发指南](docs/team-collaboration.md)：克隆、独立环境配置、分支提交与密钥排除规则。

基于二十四节气与双视角互动叙事的中外文化交流平台。当前已落地**阶段1基础工程、立春剧情引擎、小满AI伙伴及Vue Flow剧情编辑器**：真实账号、JWT/Redis会话、个人档案、管理权限、数据库剧情图、双人异步解谜与持久化结局奖励。小满支持可配置OpenAI兼容对话、SSE和无密钥脚本降级。年度节气日期、通用结对、上传和小程序仍待开发；Redis7及Docker目标环境尚未完成运行验收。

后端：Java17、Spring Boot3.5.16、Spring Security、MyBatis-Plus3.5.16、MySQL8、Redis、Flyway、Springdoc。网站：Vue3/TypeScript、Vite7.3.7、Pinia、Vue Router、Element Plus、vue-i18n11.4.16、Vue Flow1.48.2、Axios1.21.0。

## 已实现

- 账号/邮箱注册登录，BCrypt哈希、邮箱AES-GCM加密/HMAC检索，禁止注册提权。
- JWT、DB权限、Redis限流/刷新轮换/重放检测、HttpOnly cookie、CSRF、退出服务端撤销。
- 本人档案：显示名、语言水平、兴趣、时区、可协作时段、隐私/提醒偏好与乐观锁。
- 管理员独立登录、真实账号数量/分页用户列表、停用/启用及旧token立即失效。
- 中英响应式界面、权限路由、统一Result/异常/traceId、OpenAPI与依赖健康检查。
- 立春篇章14节点/22连线/3知识卡/3结局，支持六种节点、服务端条件分支/变量、逐步存档、幂等与并发校验。
- 真人双视角邀请/接受、线索隔离、48小时窗口/迟交/延长/脚本替代/跳过；私有文字回礼与打卡、服务端唯一印章记录。

剧情入口：[立春篇章](http://127.0.0.1:5173/stories/1)。实现与边界见[剧情引擎说明](docs/story-engine.md)、[文化来源](docs/content/lichun-sources.md)、[剧情测试报告](docs/testing/story-validation.md)。按用户最新任务优先开发引擎，全年日历等阶段2工作仍保留在任务清单中。

没有前端mock API或固定假用户列表。演示和测试账号实际入库，管理数量注明包含开发/测试数据，不代表真实运营成效。邮箱验证邮件、实际提醒、导出/注销、微信授权尚未接入；成年确认不等于实名核验。

## 现代国风用户端

用户端已在原工程上改版：宣纸米白、墨黑与四季主题，原创SVG山水插画、响应式布局、中英适配、加载/空状态/错误重试；保留现有注册登录、存档、双视角提交与小满SSE对话，没有替换业务为假接口。

| 页面 | 地址 | 实际功能 |
| --- | --- | --- |
| 节气首页 | [首页](http://127.0.0.1:5173/) | 24节气环形导览、键盘/下拉选择、四季主题、真实开放篇章和继续入口 |
| 沉浸阅读器 | [立春故事](http://127.0.0.1:5173/stories/1) | 知识出处、双语/大字号、剧情存档、六类交互与AI对话 |
| 双笺同行 | [同行空间](http://127.0.0.1:5173/collaboration) | 立春真人/脚本状态、独立线索、实际提交与持久化等待 |
| 文化回礼 | [文化手记](http://127.0.0.1:5173/gifts) | 自己已保存的回礼/打卡，中英原文对照与筛选 |
| 数字岁时册 | [岁时册](http://127.0.0.1:5173/passport) | 24格真实印章收藏、获得年份筛选、结局详情、浏览器打印 |

年度日期与倒计时尚未开放；未发布章节标为筹备。伙伴间的已审核习俗对照、上传、应时特殊印记、徽章和分享海报仍待开发。当前回礼仅本人可见，岁时册只依据服务端奖励记录显示印章，未获奖励时展示空收藏。

用户端视觉改版当轮验证：前端生产构建和类型检查通过，14项单测、10条真实后端浏览器流程全部通过，检查桌面/手机关键页面。该轮未修改后端或重跑Java测试；本轮编辑器结果见下文。详见[设计实现](docs/ui-design.md)、[测试报告与截图](docs/testing/ui-validation.md)。本机Vite运行时直接刷新即可查看；使用构建产物部署时需重新构建网站。

## Vue Flow真实剧情编辑器

入口：[管理端剧情编织室](http://127.0.0.1:5173/admin/stories)。管理员登录后刷新即可读取新增权限。支持六种节点拖拽、画布平移/缩放/连线/选中、节点与边属性编辑、双语/线索/条件/变量/分数参数、数据库草稿、图结构校验、A/B隔离预览、审校声明发布、版本记录和历史复制。

发布直接写入现有story/story_node/story_edge，新开存档读取最新版本，旧存档继续原版本。预览复用服务端规则，使用独立快照，不写入真实用户进度或发印章。编辑者可保存/预览，审核者可读取/预览/发布，普通用户不能访问完整图和答案。

可创建独立测试篇章验证发布链，结束后归档；复制知识卡不继承AI批准状态。本轮测试没有覆盖正式立春内容，测试篇章保留为已归档开发数据。版本恢复只生成新草稿，不回退用户存档。

最新验证：**72项后端测试（38单元+34集成）、18项前端单测、12条浏览器流程全部通过**；前端生产构建通过，npm审计0条已报告漏洞。Vue Flow锁定1.48.2，修复本轮发现的既有vue-i18n公告并升级11.4.16。见[编辑器使用与接口](docs/story-editor.md)、[验证报告和截图](docs/testing/editor-validation.md)。

## 小满AI故事伙伴

用户入口在[立春故事页面](http://127.0.0.1:5173/stories/1)下方；[管理入口](http://127.0.0.1:5173/admin/ai)提供模型设置、知识审校与调用统计。无密钥可直接演示脚本回复；真实模型仅在配置服务端密钥及审校知识后调用。

配置、无密钥演示和安全边界见[AI伙伴说明](docs/ai-companion.md)，验证见[AI测试报告](docs/testing/ai-validation.md)。

## 当前Windows本机运行

本轮找到并使用已有`E:\corretto-17.0.8`，不修改系统Java25。其他机器可修改脚本参数。首次启动隔离依赖会生成随机密钥与密码到被忽略的`.env.local`，数据写`.runtime/`，不修改原有MySQL服务或数据库。

```powershell
Set-Location 'E:\suishitongxing_project'
& '.\scripts\start-local-dependencies.ps1'
& '.\scripts\build-backend.ps1' -IntegrationTests
```

本机隔离MySQL8.0.43在3307，Memurai for Redis8.2兼容服务在6380；**不是Redis7验收结果**。Memurai开发许可证有运行限制，不能用于生产；JDK17.0.8也仅为本轮开发验证补丁，生产需使用维护中的17补丁并另行验收。

集成测试只清理独立`suishi_it`，先校验数据库名，不清空`suishi_dev`。未设置RUN_INTEGRATION_TESTS时集成测试明确skip，不得称为真实数据库测试通过。构建前退出正在运行同一JAR的后端（Ctrl+C），避免Windows文件锁。

```powershell
# 终端一
& '.\scripts\start-backend.ps1'
```

```powershell
# 终端二
npm.cmd --prefix web ci
npm.cmd --prefix web run dev
```

用户：[登录](http://127.0.0.1:5173/login)、[注册](http://127.0.0.1:5173/register)；[管理员登录](http://127.0.0.1:5173/admin/login)。Vite代理`/api`到8080。开发文档：[Swagger UI](http://127.0.0.1:8080/swagger-ui/index.html)、[OpenAPI](http://127.0.0.1:8080/v3/api-docs)、[健康检查](http://127.0.0.1:8080/actuator/health)。

## 演示账号和数据库

| 账号 | 入口 | 密码来源 |
| --- | --- | --- |
| demo_guide | 用户登录 | 本机.env.local的DEMO_PASSWORD |
| demo_partner | 用户登录 | 同上 |
| demo_admin | 管理员登录 | 本机.env.local的ADMIN_PASSWORD |

账号仅在显式bootstrap后创建，重复启动不重置密码或提升已有普通账号。无通用固定口令；`.env.local`含真实本机凭据，在本机查看，勿提交或分享。

初始化SQL位于`backend/src/main/resources/db/migration/`，由Flyway迁移并记录版本：V1身份表、V2角色/权限/兴趣种子、V3剧情引擎表、V4立春发布内容、V5小满AI配置/审校/调用日志与额度、V6剧情编辑器草稿/发布快照/隔离预览及权限。没有固定管理员密码；后续新增迁移，不修改已执行文件。开发账号只授权对应schema；生产迁移与业务权限分离待阶段9。

## 测试与网站构建

```powershell
npm.cmd --prefix web run typecheck
npm.cmd --prefix web run test:unit
npm.cmd --prefix web run build
# 后端和网站均运行后
& '.\scripts\import-local-env.ps1'
npm.cmd --prefix web run test:e2e
npm.cmd --prefix web audit
```

浏览器测试使用本机Edge；其他环境可安装Playwright Chromium或设置PLAYWRIGHT_BROWSER_EXECUTABLE。缺ADMIN_PASSWORD时管理测试会明确skip，完整验收须加载本机环境。本轮无跳过。E2E会向开发库新增明确命名`e2e_*`的测试账号，不删除其他用户。

## Docker与Redis7

已提供MySQL8.0.43/Redis7.4.7开发Compose，Redis标签已核验存在；本机Docker/WSL缺失，**未运行Compose或Redis7实际验收**。复制`.env.example`为`.env.compose`并填写独立随机32字节Base64密钥、数据库/Redis/管理员密码和COMPOSE_MYSQL_ROOT_PASSWORD，勿提交该文件。

```powershell
docker compose --env-file .env.compose -f deploy/compose.dev.yml config
docker compose --env-file .env.compose -f deploy/compose.dev.yml up -d
# 可选应用覆盖，入口127.0.0.1:8088
docker compose --env-file .env.compose -f deploy/compose.dev.yml -f deploy/compose.app.yml up -d --build
```

Compose默认3306/6379；本机隔离方式使用3307/6380，配置不能混用。该Compose仅供开发。生产HTTPS、备份恢复、MinIO上传和小程序发布仍待验收；prod关闭文档/演示seed、使用Secure cookie。需要关闭本机依赖时可运行`stop-local-dependencies.ps1`，脚本核验项目数据目录后关闭，保留数据、不改系统服务。

## 验证与文档

上一轮AI模块的Java17构建及53项后端测试（28单元+25集成）、当轮前端类型/生产构建、14单测、7浏览器流程通过；其中AI新增13单元、10集成、6前端分片测试和2条浏览器流程。协议与故障测试使用本地HTTP兼容服务，无密钥网页演示使用真实后端；未调用付费模型，不宣称某个商业模型已验收。见[AI测试报告](docs/testing/ai-validation.md)。前阶段已修复Axios/Vite/Vitest公告并完成npm审计；本轮未新增依赖，安全检查范围见AI报告。后端完整依赖CVE扫描尚未执行。

见[测试报告](docs/testing/phase-1-validation.md)、[实现边界](docs/phase-1-implementation.md)、[任务清单](TASKS.md)、[环境报告](docs/environment.md)。

原始规划：[需求](docs/requirements.md)、[架构与目录](docs/architecture.md)、[数据库/ER](docs/database-design.md)、[API](docs/api-design.md)、[开发计划](docs/development-plan.md)、[演示计划](docs/demo-plan.md)、[素材记录](docs/content/assets.md)。
