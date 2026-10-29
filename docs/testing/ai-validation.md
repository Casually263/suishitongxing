# 小满 AI 模块验证与安全检查

日期：2026-10-28。直接扩展现有模块化单体，新增V5迁移，没有重建登录或剧情系统，没有新增第三方库。

## 结果

| 检查 | 结果 |
| --- | --- |
| Java17 / Maven verify | 成功，生成可运行JAR |
| AI安全单元测试 | AiSafetyTest：6通过 |
| OpenAI兼容协议单元测试 | OpenAiCompatibleProviderTest：7通过 |
| 原安全/剧情规则单元测试 | 15通过 |
| AI真实数据库集成测试 | AiIT：10通过 |
| 原身份/剧情集成测试 | 15通过 |
| 后端合计 | 53通过，0失败、0错误、0跳过 |
| 前端类型检查与Vite构建 | 通过 |
| 前端单测 | 14通过：8原路由安全+6新增SSE解析 |
| Edge浏览器流程 | 7通过，0失败、0跳过、0 flaky；其中2条AI流程 |
| 运行与文档 | 数据库迁移到V5，health=UP，OpenAPI提供8个AI路径 |

机器摘要由`node scripts/verify-ai.mjs`读取真实JUnit/Vitest/Playwright结果、构建日志及HTTP状态后生成到`docs/testing/ai-results.json`，含代码和原始DOCX摘要。之前的阶段1/剧情报告保留为历史记录。

## 协议与故障测试

- JDK HttpServer作为**明确标注的本地兼容协议夹具**，使用真实TCP/HTTP/SSE，不使用模拟AiProvider来替代协议解析。
- 捕获真实出站JSON与Authorization，验证密钥只出现在服务端请求头，不在请求正文。
- UTF-8字节拆分、CRLF、多事件、最后usage-only、缺失usage为null、普通chat与增量stream分别验证。
- 用闩锁阻止上游结束，先断言客户端已经收到第一段，证明实际增量读取，并非等待全部内容后伪装流式。
- 验证已收到HTTP头/部分正文后的超时也有效；客户端取消关闭连接；缺失DONE、错误JSON、401和302重定向均按安全类型处理，凭据不随重定向转发。
- 暂时503在未显示文本时重试一次；连续失败降级；已显示后流中断不重试，发送reset后脚本接替。

## 安全与业务验证

1. 三种角色上下文参与测试（管理员/向导/伙伴）；普通用户不能读取配置、审批或调用统计，管理员须通过admin认证通道。
2. 在判定配置、伙伴B线索、档案和私有回礼中植入不同canary，捕获A的真实模型请求并断言全部不存在；只含自己的获准线索。API密钥、用户ID、邀请码和判定字段也不进入上下文。
3. 脚本模式可转述明确获准的虚拟伙伴线索；真人模式不因此扩大访问范围。
4. SOURCE_CHECKED不能作为模型依据。通过真实审批API在隔离库批准卡片后才调用模型；修改正文后摘要失配，立即回到NO_REVIEWED_KNOWLEDGE且不调用模型。审批要求双语/文化检查与版本摘要。
5. 邮箱、电话、显式姓名和常见凭据脱敏；提示词/答案/奖励注入由本地边界回复；模型输出秘密、未知数字/习俗、错误引用及业务写入宣称被拦截。
6. 无密钥中英文SSE回复、历史读取、重复requestId重播、不同内容重用编号冲突均验证；对话前后剧情状态相同。
7. 每日和篇章限额在MySQL持久化，重试按尝试占用额度；耗尽返回脚本。每用户在途锁拒绝并发第二流，失败/结束后释放，有TTL恢复。
8. 统计只含元数据。缺失usage或发生重试时不把未知用量当成零或完整账单，也没有未核实的价格估算。
9. 服务地址只允许服务器配置的origin，无重定向、URL凭据/查询或默认私网访问；本地HTTP例外必须dev/test显式允许，prod不接受。
10. 验证脚本扫描本机密钥是否进入源码、文档或前端构建物，检查上下文SQL投影、源码/JAR时序、本地文档链接和原DOCX未变。

日志中的attempts是进入模型调用流程的尝试计数，限额在尝试前预留；配置/连接检查失败也可能占用一次尝试，不能把它直接等同于供应商计费请求。

## 浏览器实测

通过真实注册/登录创建ai_前缀开发账号，进入立春，发送中文脚本问候，确认SSE内容类型及完成事件在界面生效；刷新后恢复消息且剧情存档不变；切换英文，再验证回复与390px无横向溢出；答案/奖励越权请求得到明确拒绝。

管理员页面读取真实配置与统计，保存开关后恢复原值，查看3张待审卡片；没有自动审批开发库的文化内容。原身份及双人剧情5条浏览器流程同时通过。

已实际打开检查的截图：

- [桌面无密钥对话](ai-no-key-desktop.png)
- [手机英文对话](ai-no-key-mobile.png)
- [AI配置、审校和真实统计](ai-admin.png)

## 重现

```powershell
cd E:\suishitongxing_project
.\scripts\start-local-dependencies.ps1
# 停止运行同一JAR的后端后执行
.\scripts\build-backend.ps1 -IntegrationTests
npm.cmd --prefix web run build
npm.cmd --prefix web run test:unit -- --reporter=json --outputFile=../docs/testing/ai-web-unit.json
```

在两个终端按运行指南启动后端、前端，再用第三个终端执行：

```powershell
.\scripts\import-local-env.ps1
npm.cmd --prefix web run test:e2e
node scripts/verify-ai.mjs
```

集成测试确认数据库名后仅清理suishi_it；浏览器在suishi_dev新增明确开发测试账号，未清空原用户数据。当前兼容服务是Memurai，不冒称Redis7原版验证。日志在忽略目录.runtime，原始JUnit位于backend/target，浏览器JSON在web/test-results。

## 验证限制

没有第三方真实密钥，本轮未调用付费/商业模型。成功验证的是兼容HTTP协议、故障恢复、业务边界和无密钥真实网页闭环。真实模型账号可用性、自然语言质量和语义文化准确性需配置后进一步评测。词法/出处检查不能证明任意自然语言陈述都正确，也不替代专业审校。

Docker/Redis7、完整后端依赖CVE扫描、通用真人/AI结对即时切换、小程序分块传输和其他人设编辑仍待后续工作。
