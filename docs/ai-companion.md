# 小满 AI 故事伙伴

本轮在既有剧情工程上新增AI模块。剧情推进、判定、默契与印章逻辑留在story模块，AI模块不调用choose、奖励或答案查询接口。

## 体验与无密钥演示

按[原运行指南](run-guide.md)启动，登录并打开立春故事。页面下方“和小满聊一聊”支持中文/英文，刷新读取本人对话记录。

1. 不填写AI_API_KEY，发送问候，看到“脚本伙伴”和“未配置模型密钥”提示。
2. 切换English再提问，获得英文脚本回复。
3. 在dual节点问当前线索：真人模式只转述自己的线索；脚本模式可转述服务端明确授权的虚拟伙伴线索。
4. 刷新，对话与剧情分别恢复；聊天不会提交选择、增加变量或发奖励。
5. 管理员在`http://127.0.0.1:5173/admin/ai`查看真实调用、延迟和原因；脚本不产生模型请求。

## 接入 OpenAI 兼容服务

密钥仅从服务端AI_API_KEY读取，不入数据库，管理页面无密钥输入或回显。在本机忽略文件`.env.local`中配置：

```dotenv
AI_API_KEY=填入自己的服务端密钥
AI_BASE_URL=https://你的兼容服务域名/v1
AI_MODEL=服务商提供的模型ID
AI_ALLOWED_ORIGINS=https://你的兼容服务域名
AI_ALLOW_LOCAL_ENDPOINTS=false
```

修改后重启后端。地址自动追加`/chat/completions`。Base URL必须在服务端origin白名单内，不得带凭据、查询参数或fragment；禁用重定向，拒绝私网地址。仅dev/test同时显式允许字面回环地址时可连接本地兼容服务，prod不接受例外。

管理端可覆盖模型名/Base URL，留空回退环境变量。可调整开关、温度、超时、重试、每日调用、篇章轮数、分钟限流、输出token及流末尾usage请求。密钥存在仅代表已配置，不表示服务商账号、模型或额度一定有效。

## 知识审校

SOURCE_CHECKED只表示核验出处，不自动进入模型。管理员必须核对文化事实、双语和出处并写说明，才授予APPROVED。授权绑定正文、出处标题和URL摘要；修改任意一项或撤销授权都会移出检索。没有审校卡片时不向模型请求文化解释，使用承认资料不足的本地脚本。

管理员审校不等于专业学者认证；自动测试的审批只在隔离测试库发生。实际演示卡片不会自动审批。

## 信息边界

AiContextMapper仅选择当前节点标题、类型和获准线索，不选择config、选项、答案、历史反馈、变量、奖励、邀请码、姓名、档案、伙伴消息或私有回礼。知识仅来自正文/出处摘要匹配的审校卡片。模型上下文不含用户ID、JWT或管理密钥。

仅携带同一节点/会话/语言/知识摘要下最近两轮脱敏对话。常见邮箱、电话、显式姓名/密码/令牌在模型请求及日志前脱敏；规则不能识别所有自然语言个人信息，页面提示不填写敏感信息。

固定人设和边界在system层。用户及检索内容标记为数据。模型无tools和业务写回调，不采纳模型flags判定剧情。输出为短纯文本，事实句须带[K卡片ID]。这与原文JSON flags建议的差异，是为了避免把模型标志误用于状态变更，并支持分句检查。

## 流式与容错

- 上游为真实Chat Completions SSE，增量解码UTF-8与delta.content，支持最后usage-only块；AiProvider也提供非流式chat。
- 出站按完整句子缓冲，检查凭据、隐私、越权命令、未授权引用、已知节气/习俗及无依据数字。中断/拦截后发送reset，前端清除半条模型回复，明确切换脚本。脚本分段不冒充模型token。
- 默认每次8秒，最多重试一次；仅在尚未显示内容且故障可重试时重试，已显示后故障直接降级。头部/正文有超时，客户端取消会关闭上游读取。
- 每用户一分钟请求数、每天实际模型尝试数、每篇章模型轮数、同用户一条流和全局有界执行池限制。每日额度按UTC入库；重试也计次数，脚本不占模型预算。
- requestId绑定用户和请求摘要；完成重试只重播保存回复，不重复调用。同编号改内容、过期节点、注入actor等拒绝。
- 日志记录脱敏问题/回复、模式、状态、原因、尝试、耗时和可获得的token；未知usage不作零费用。管理端仅查看元数据，不能通过统计接口查看私人全文。

词法/出处检查不能证明所有自然语言事实正确，不能替代语义评测、人工文化审查和上线前抽检。

## 接口

用户接口同时支持/api/app和/api/portal，需要Bearer JWT：

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| POST | /stories/{chapterId}/ai/chat | nodeId、message、language(zh/en)、requestId；SSE |
| GET | /stories/{chapterId}/ai/history | 本人最近20轮 |
| GET / PUT | /api/admin/ai/config | ai:read / ai:write |
| GET | /api/admin/ai/usage | ai:read；UTC统计和最近20条元数据 |
| GET | /api/admin/ai/knowledge | knowledge:review；正文、出处与摘要 |
| PUT | /api/admin/ai/knowledge/{id}/review | knowledge:review；摘要、决策、双核对及说明 |

SSE顺序为meta → delta若干 → done。降级时reset → script delta → done。持久化失败/中断可发送error，不宣称成功保存；开始前错误仍是标准JSON与真实HTTP状态。Nginx关闭代理缓冲。

## 协议依据与验证范围

依据[官方Chat Completions创建协议](https://developers.openai.com/api/reference/resources/chat/subresources/completions/methods/create)、[流式事件](https://developers.openai.com/api/reference/resources/chat/subresources/completions/streaming-events)及[服务端认证说明](https://developers.openai.com/api/reference/overview)。为满足第三方兼容要求使用Chat Completions，不宣称实现了Responses API。

自动测试使用本地真实HTTP兼容协议夹具，不消耗付费模型额度；没有第三方密钥时，不能声称已验证某个商业服务商。Docker/Redis7仍沿用未验收状态。测试报告为`docs/testing/ai-validation.md`。
