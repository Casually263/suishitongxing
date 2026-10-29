# 剧情编辑器验证记录

2026-10-29。现有Windows项目、本机Edge、Java17、MySQL8.0.43（3307）、Memurai Redis兼容服务（6380）、Vite5173、后端8080。没有用假接口替代保存、发布或预览。

## 结果

| 检查 | 最终结果 |
| --- | --- |
| Java17后端构建 | package/完整集成构建成功，V6已在独立测试库及开发库执行 |
| 后端单元测试 | 38通过：原28+编辑器/规则10 |
| 真实集成测试 | 34通过：原25+编辑器9；全部操作限定suishi_it |
| 前端构建 | vue-tsc和Vite生产构建成功，Vue Flow按路由延迟加载 |
| 前端单测 | 18通过：原14+图标识/连线引用操作4 |
| 浏览器流程 | 12通过：原10+编辑器2；无失败、跳过或flaky |
| npm audit | 0条已报告漏洞；修复现有vue-i18n公告并升级11.4.16，Vue Flow锁定1.48.2 |

机器摘要：[editor-results.json](editor-results.json)。单测：[前端JSON](editor-web-unit.json)。审计：[npm原始结果](editor-npm-audit.json)。完整Maven XML在`backend/target/surefire-reports`与`failsafe-reports`，浏览器JSON在`web/test-results/results.json`；这些构建目录被忽略。

构建/测试日志：`.runtime/editor-backend-verify.log`、`.runtime/editor-final-backend-tests.log`、`.runtime/editor-web-build.log`、`.runtime/editor-full-e2e.log`。补充B预览测试后执行`mvn test failsafe:integration-test failsafe:verify`，再次运行全部38单元+34集成；没有覆盖正在运行的JAR，新增内容仅是测试。当前运行JAR包含本轮全部后端业务修改。

## 覆盖的业务证据

- 草稿往返保留中英文本与坐标；缺译内容可保存但不能发布；格式错误、非法参数、环路、不可达/悬空、缺省边与优先级冲突阻止发布。
- 两个并发保存相同revision，仅一个成功，另一返回409；界面明确提示冲突并保留本地输入。并发重复发布只产生一个不可变版本与发布记录。
- 发布新版本后，新账号读取真实新标题、新节点、新连线及12小时窗口/自定义11分默契；已有账号继续旧版本的原文和默认15分。用户响应不包含answerA/B、config或另一侧选项。
- 隔离预览实际执行叙述、选择、双视角、回礼、打卡、结局；A/B分别验证自己的线索与判定输入映射。完成预览后真实进度、会话、提交、印章表均未新增。
- 修改草稿不会改变已启动预览的快照；刷新可以按预览URL恢复。其他管理账号访问预览404，过期404，旧预览revision409，伪造variables请求400。
- 普通用户/未登录请求403/401；EDITOR不能发布，REVIEWER不能保存；跨篇章恢复版本404。缺少核对声明不能发布。
- 版本恢复只复制新草稿，不更改既有发布图；恢复后发布产生下一个版本。归档阻止新开存档，已有存档及知识卡仍可读取。

## 浏览器实际操作

1. 管理员创建独立`ED_<时间>_FLOW`篇章，复制立春图和出处；初次发布v1并用旧账号建立存档。
2. 实际拖动节点、从组件库拖入新叙述、编辑中英正文与分数、修改出边目标，并用鼠标把新节点动作端点连到已有节点。保存后刷新，核对15节点、23边和变更坐标。
3. 运行服务端预览，经过新增节点、原有选择、双视角（11分）、回礼、打卡和结局；中途刷新仍可继续，完成不发真实印章。
4. 发布v2，新账号在网站用户端看到新标题并走到新增叙述节点，刷新仍在该节点；旧账号仍看到v1原文。
5. 查看v1/v2真实发布记录和说明，复制v1为r3草稿；发布版本仍只有两份。检查390px英文编辑器无横向溢出，弹窗关闭后焦点回到编辑工具。
6. 另一个独立篇章验证服务端并发更新后的409、本地内容保留、无效草稿校验、断网错误与恢复。
7. 两条测试均在afterEach归档自己的测试篇章。早期超时用例留下的一份独立发布测试篇章也已按明确测试代码/名称归档；没有修改或回退正式立春内容。

保留的测试篇章、草稿、发布快照、预览和测试账号是真实开发数据，不代表正式文化审校或真实运营用户。复制知识卡保持SOURCE_CHECKED，不复制AI批准记录。AI管理回归按真实返回的卡片数量验证，不再假定数据库永久只有3张卡。

## 截图复核

| 截图 | 内容 |
| --- | --- |
| [桌面编辑器](editor-desktop.png) | 六种节点库、实际连线、节点属性与持久化修改 |
| [隔离预览](editor-preview.png) | 服务端结局与不发印章说明 |
| [发布用户端](editor-published-player.png) | 真实新增节点、剧情版本2与保存恢复 |
| [版本记录](editor-history.png) | v1/v2、真实时间、发布说明与复制入口 |
| [保存冲突](editor-conflict.png) | 409后本地文本仍然保留 |
| [手机英文](editor-mobile-english.png) | 节点库、可缩放画布、属性面板及焦点恢复 |

早期浏览器测试因沙箱启动限制退出，最终使用本机Edge。首轮预览还发现提交期间可继续填写导致快速操作时丢失下一段输入；已禁用提交期间的输入，并以服务端新节点作为继续填写的依据。弹窗嵌套关闭的焦点回退也已修复并增加断言。最终完整12条流程通过。

## 复现与范围

按 [运行指南](../run-guide.md) 启动依赖。后端构建前先退出占用同一JAR的后端；本机命令：

```powershell
& '.\scripts\build-backend.ps1' -IntegrationTests
& '.\scripts\start-backend.ps1'
```

另一个终端启动网站，再执行：

```powershell
npm.cmd --prefix web run build
npm.cmd --prefix web run test:unit -- --reporter=json --outputFile=../docs/testing/editor-web-unit.json
& '.\scripts\import-local-env.ps1'
npm.cmd --prefix web run test:e2e
npm.cmd --prefix web audit --json
node scripts/verify-editor.mjs
```

没有第三方付费模型调用。没有虚构专家签审、真实用户试用或线上部署验收。手机为Edge视口模拟，未完成真机/Safari验证。编辑器不代表文件上传、公开内容审核、全年日期、另外三季篇章或小程序已完成。
