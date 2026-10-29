# 剧情引擎与立春篇章验证

日期：2026-10-28。本轮直接修改现有工程，未重建身份系统。需求范围为系统设计文档第5.1、5.2、5.4、6、7章和用户本轮指令。

## 验证结果

| 检查 | 真实结果 |
| --- | --- |
| Java17 / Maven构建 | 成功，生成可运行Spring Boot JAR |
| 剧情规则单元测试 | StoryGraphTest：12通过，0失败、0跳过 |
| 原安全单元测试 | CryptoSecurityTest：3通过 |
| 剧情集成测试 | StoryIT：8通过，真实MySQL，未用H2/模拟Mapper |
| 原身份集成测试 | IdentityIT：7通过，真实MySQL及Redis兼容服务 |
| 后端合计 | 30通过，0失败、0错误、0跳过 |
| Vue TypeScript / Vite生产构建 | 成功，包含StoryPage独立路由包 |
| 前端单元测试 | 8通过（原路由安全回归，不冒称新增剧情单测） |
| Edge / Playwright | 5通过，0失败、0跳过、0 flaky；其中2条新增剧情流程 |
| 数据迁移 | 开发库和测试库均成功应用V3/V4，Flyway校验通过 |
| 运行健康检查 | UP，功能元数据stories=true，OpenAPI包含14个剧情/篇章路径（双前缀） |

机器可读摘要由`node scripts/verify-story.mjs`从JUnit、Vitest、Playwright报告和真实HTTP健康状态生成，文件为`docs/testing/story-results.json`，包含源代码摘要、源DOCX摘要及检查时间。

## 业务覆盖

1. 读取数据库发布图，验证六类型、14节点、22连线及双语知识卡；三条路径分别到达三个不同结局。
2. narrative、choice、dual、gift、checkin、ending全部实际执行，变量与历史真实入库，完成后再次读取和开始都不重置。
3. 同编号同内容重试无重复分数，同编号改内容冲突；过期版本、伪造节点、未知选项、注入variables、短记录均拒绝。
4. 两个独立真实账号分别取得A/B线索与选项；第一人提交后刷新仍等待，响应不含另一侧答案/解释，第二人提交统一推进两份存档。
5. 并发双人提交最终只判定一次；并发同人竞争选择只有一次推进，另一请求409。
6. 判定错误仍可继续，10基础默契/5命中奖励由服务端计算；结束不能再次结算，不给尚未完成的伙伴发奖励。
7. 测试库把截止时间设置到过去，验证TIMEOUT、延长一次、迟交判定、脚本替代/跳过，以及不代推进缺席人。没有修改开发库时间或声称实际等待48小时。
8. 新发布第2版后，旧账号仍在第1版保存/推进，新用户获得第2版；测试结束删除仅测试库中的临时版本。
9. 私有回礼/打卡在GET中恢复，伙伴和第三方不获得其内容；所有新业务接口沿用JWT认证。
10. 图规则测试覆盖六个比较运算、优先级/fallback、变量范围、未知变量/表达式、跨图边、断边、环、不可达节点和缺失翻译。

## 浏览器实测

新增脚本流程通过真实注册接口建立测试账号，再从网页登录，依次走选择、刷新、双视角、私有回礼、再次刷新、打卡、结局、印章和完成后刷新。另一条使用两个独立浏览器上下文，确认不同线索、等待刷新和伙伴提交后的4秒轮询恢复。没有拦截/伪造业务接口。

新增截图已实际打开检查：

- [桌面双语剧情](story-lichun-desktop.png)
- [390px手机剧情](story-lichun-mobile.png)
- [移动端完成与印章](story-lichun-ending.png)

390px页面没有水平溢出，正文、双语选项和提交按钮可读；原登录/个人档案/管理端浏览器流程也回归通过。

## 重现命令

```powershell
cd E:\suishitongxing_project
.\scripts\start-local-dependencies.ps1
# 先停止占用同一JAR的后端；测试不要求开发后端在运行
.\scripts\build-backend.ps1 -IntegrationTests
npm.cmd --prefix web run build
npm.cmd --prefix web run test:unit -- --reporter=json --outputFile=../docs/testing/story-web-unit.json
```

前后端分别按运行指南启动后，在另一个终端执行：

```powershell
.\scripts\import-local-env.ps1
npm.cmd --prefix web run test:e2e
node scripts/verify-story.mjs
```

验证脚本还检查报告、Markdown本地链接、开发密钥未进入源码/文档、原始DOCX未变及JAR不早于后端源码。构建/测试日志保存在本机忽略目录`.runtime`；完整JUnit在backend/target中，Playwright报告在web/test-results中。

## 环境与限制

- 本机Java17.0.8、MySQL8.0.43及Memurai Redis兼容8.2服务；Docker和Redis7原版仍未验证，不能据此宣称生产部署通过。
- 集成测试核验数据库名后，只清理独立`suishi_it`；没有清空`suishi_dev`。浏览器向开发库新增明确前缀的story_、e2e_测试账号和进度，不代表真实运营用户。
- 首次构建遇到本机全局Maven配置/缓存默认路径冲突，以及旧后端占用JAR，已通过项目构建脚本和停止旧实例解决；未修改全局Maven配置。
- 本轮未新增第三方依赖，未重复宣称已完成全后端依赖漏洞扫描。
- 图片上传、公开审核、AI对话、通用匹配、图编辑发布、完整岁时册和另外三章尚未实现；文本内容已核验出处，专业文化及母语签审尚未完成。
