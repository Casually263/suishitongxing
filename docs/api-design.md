# 岁时同行 API 契约设计

这是阶段0完整契约基线。阶段1身份接口已实现；本轮新增篇章/知识卡/剧情state/start/join/choose/奖励接口，具体见[剧情说明](story-engine.md)与本文末尾。其他规划接口仍未实现，不能把下表全部当作可用接口。

## 1. 协议与安全约定

- HTTPS REST+JSON；业务标准前缀 `/api/app`，同一业务同时支持 `/api/portal` 别名，不能复制 Service；表中 `{B}` 指这两个前缀。微信/网站/管理员认证分别适配；`/api/common` 不意味着所有接口匿名。
- 非公开接口使用 `Authorization: Bearer <accessToken>`；从 token 得出 userId，不接受客户端指定 actor/role/userId 来访问他人数据。所有 ID 均为字符串；时间戳 ISO8601 UTC，如 `2026-10-28T03:00:00.000Z`，DATE 用 `YYYY-MM-DD`。
- `Accept-Language: zh-CN/en-US`，也可 `lang=zh/en/parallel`；界面文案按语言返回，内容保留 `{zh,en}`。一般内容缺翻译可回退并给 `fallbackLanguage`，正式发布四章要求中英齐全。
- 普通响应：`{"code":"OK","msg":"成功","data":{},"traceId":"..."}`，保留原文 code/msg/data，补 traceId 便于排错。业务字段只进 data；HTTP 状态必须真实使用，不能一律200。分页 `items,page,size,total`；留言用 `items,nextCursor,hasMore`。
- POST/PUT/PATCH 业务变更带 `X-Request-Id` 或请求体 requestId（同请求两处出现必须一致）；推进操作还带 expectedVersion。requestId 是 UUID 等≤64字符，不能代替状态校验。
- 用户可读的节点只含 public 内容和自己的 clue；不返回 rules、正确集、变量 effects、隐藏边条件、其他人未提交/未揭晓答案。后台私有 DTO 与用户 VO 必须是不同类型。

| HTTP / code | 含义 |
| --- | --- |
| 400 VALIDATION_ERROR | 参数、未知字段或枚举非法；details 只含字段与错误，不回显密码 |
| 401 UNAUTHENTICATED / TOKEN_EXPIRED | 登录失效或令牌无效 |
| 403 FORBIDDEN / ACCOUNT_DISABLED / AGE_RESTRICTED | 权限不足、封禁、真人功能不适用 |
| 404 RESOURCE_NOT_FOUND | 对不可见私密资源也返回404，避免泄露其存在 |
| 409 VERSION_CONFLICT / IDEMPOTENCY_CONFLICT | 存档过期或同一请求 ID 内容不同 |
| 409 INVALID_NODE / ALREADY_SUBMITTED / PAIR_LIMIT | 非当前节点、已提交换答案、结对配额冲突 |
| 409 DUAL_TIMEOUT / SESSION_CLOSED / REVIEW_REQUIRED | 过期提交、会话封存或任务尚未通过审核 |
| 413 FILE_TOO_LARGE / 415 FILE_TYPE_NOT_ALLOWED | 上传超限或格式不允许 |
| 422 GRAPH_INVALID / CONTENT_NOT_APPROVED | 图/校对/来源不能通过发布校验 |
| 429 RATE_LIMITED | 调用限制，附 Retry-After；AI 可在200响应脚本降级并注明原因 |
| 503 TERM_DATES_UNAVAILABLE / DEPENDENCY_UNAVAILABLE | 关键配置或服务缺失，不伪造业务数据 |

生产错误不暴露堆栈、SQL、答案与内部密钥。所有角色/归属拒绝记录脱敏审计。

## 2. 认证 档案 公共元数据

| 方法 路径 | 请求要点 | 返回 / 权限 |
| --- | --- | --- |
| POST /api/portal/auth/register | username、email、password、cultureRole、成年资格确认、同意版本 | userId；公开但限流；不得指定ADMIN |
| POST /api/portal/auth/login | identifier、password | accessToken、expiresIn、user、permissions；refresh 走安全 cookie |
| POST /api/admin/auth/login | identifier、password | 同上，需后台登录权限；普通用户不得获取后台会话 |
| GET /api/portal/auth/csrf | 无 | 浏览器 refresh/logout 的 CSRF token，同源且 no-store |
| GET /api/admin/auth/csrf | 无 | 管理员独立作用域 |
| POST /api/portal/auth/refresh | cookie + X-CSRF-Token | 新 accessToken，轮换refresh并检测重用 |
| POST /api/admin/auth/refresh | 同上 | 限后台会话 |
| POST /api/portal/auth/logout | cookie/当前token + CSRF | 清 cookie，更新 auth_version 撤销该用户已有会话 |
| POST /api/admin/auth/logout | 同上 | 同一撤销逻辑，不能只删浏览器 token |
| POST /api/app/auth/wx-login | code、appId配置引用 | 真实微信适配层结果；不接受客户端 openid/unionid 当登录凭证 |
| POST /api/app/auth/test-login | 测试账号凭据，显式 TEST 标记 | 仅dev启用，prod路由不存在；不是微信授权 |
| POST /api/app/auth/refresh | 小程序安全存储中的refreshToken | 轮换令牌；服务端校验渠道与 auth_version |
| POST /api/app/auth/logout | accessToken与refreshToken | 服务端撤销 |
| GET /api/portal/auth/me 与 GET /api/app/auth/me | 无 | 当前账号、档案、权限；不返完整第三方标识 |
| POST {B}/auth/email/verify-request | email或当前账号，按场景校验 | 泛化提示、不暴露账号是否注册；真实邮件服务未接入时显式禁用 |
| POST {B}/auth/email/verify | token | 验证邮箱状态；比赛阶段邮箱注册不等于已验证邮箱 |
| POST {B}/auth/wechat/bind | 当前账号重新验证、真实微信code | 绑定同一账号；不能按相同显示名自动合并 |
| DELETE {B}/auth/wechat/bind | 重新认证 | 有其他登录凭证才可解绑 |
| GET/PUT {B}/profile | langLevel、timezone、availSlots、tagIds、privacy等 | 本人档案；PUT带expectedVersion，禁止修改角色权限 |
| GET /api/common/meta | 无 | 支持语言、兴趣标签、可用模式及功能开关，无敏感配置 |
| POST {B}/account/export | scope | 异步任务ID；仅本人；安全下载、短有效期 |
| GET {B}/account/exports/{jobId} | 无 | 本人的任务状态/下载入口 |
| POST {B}/account/deactivate | 重新认证、确认 | 停用、解除/封存关系、清理计划，不删除他人的记录 |

浏览器 accessToken 只放内存，刷新页面通过 HttpOnly+Secure+SameSite cookie 恢复；refresh/logout 校验 Origin、CSRF及渠道。小程序使用独立令牌存储与刷新协议，日志不输出令牌。默认 access15分钟/refresh7天，阶段1验证后锁定。密码长度12–72 UTF-8字节（BCrypt限制），用户名3–64字符，邮箱按统一规则规范化；不对邮箱 local part 做擅自去点/去加号。

## 3. 节气 篇章与知识

| 方法 路径 | 参数 | 返回 |
| --- | --- | --- |
| GET {B}/terms?year=2026 | 年份，可匿名 | 24个定义、已审日期、当前/下一项、chapterStatus；日期缺失单独标记 |
| GET {B}/terms/current | 可匿名 | current、next、serverNow、calendarTimezone、chapter；缺年度数据503 |
| GET {B}/chapters | page/size/status | 已发布/筹备篇章；登录时加本人进度，不混入他人进度 |
| GET {B}/chapters/{chapterId} | lang | 双语详情、可用模式、发布时间、完成状态 |
| GET {B}/chapters/{chapterId}/cards | lang | 仅已审核卡片、来源、地域说明、revision；未审不可公开 |
| GET {B}/terms/{termId}/mascot | 无 | 有授权记录的已上线形象，不存在时返回明确空状态 |

每年的日期从数据库读取；后台录入和审核后才参与开放计算。日历不可硬编码真实当年日期，无数据时必须保留可理解错误/空状态。

## 4. 剧情引擎

| 方法 路径 | 请求/返回 | 权限/规则 |
| --- | --- | --- |
| POST {B}/stories/{chapterId}/start | mode、purpose=PRIMARY/REPLAY、pairId?、seasonYear、requestId → sessionId和当前state | 本人，HUMAN需有效双方确认；新建/恢复/重玩不能重置已得奖励 |
| GET {B}/stories/{chapterId}/state | seasonYear? → 当前用户存档与当前节点投影 | 禁止客户端随意nodeId读取未来节点 |
| POST {B}/stories/{chapterId}/choose | sessionId、nodeId、optionKey?、expectedVersion、requestId | narrative继续、choice选择、任务/结局确认；dual另用submit |
| POST {B}/stories/{chapterId}/partner-switch | targetMode、pairId?、expectedVersion、requestId | 安全边界切换或建立待确认切换；原进度保留，返回switchStatus和原因 |
| GET {B}/stories/{chapterId}/history | cursor | 本人的已发生公开历史；未揭晓私密信息不返回 |

用户状态示例：

```json
{
  "code": "OK",
  "msg": "成功",
  "data": {
    "sessionId": "1001001",
    "storyVersion": "1",
    "progressVersion": 4,
    "status": "IN_PROGRESS",
    "mode": "HUMAN",
    "node": {
      "id": "2002002",
      "key": "spring-dual-1",
      "type": "dual",
      "content": {"zh": "请与伙伴交流线索", "en": "Exchange clues with your partner"},
      "clue": {"zh": "仅属于当前视角的线索", "en": "A clue for your assigned perspective"},
      "options": [{"key": "option-1", "label": {"zh": "选项一", "en": "Option one"}}]
    },
    "variables": {"knowledge": 2},
    "allowedActions": ["DUAL_SUBMIT"],
    "serverNow": "2026-10-28T03:00:00.000Z"
  },
  "traceId": "example-trace"
}
```

此处为契约占位文本，不是立春文化内容或运行种子。variables 只暴露用于用户展示且已发生的值；不返可据以推断正确答案的中间计算。`choose` 传入 `variables/rewards/nextNodeId/userId` 等越权字段应拒绝，不可默默采用。

## 5. 结对与双视角

| 方法 路径 | 请求/返回 | 规则 |
| --- | --- | --- |
| GET {B}/pairs/recommend | limit≤20 → 脱敏候选、共同兴趣、J/L/Z/A/M、ruleVersion | 成年，硬过滤与隐私设置；不返邮箱/完整活动轨迹 |
| GET {B}/pairs | 状态 → 本人邀请及有效关系 | 仅本人 |
| POST {B}/pairs/invite | targetUserId、requestId | 不同用户，未拉黑、配额和时效检查 |
| POST {B}/pairs/accept | invitationId、requestId、expectedVersion | 受邀者，锁双方配额；默认邀请7天过期（新增默认） |
| POST {B}/pairs/reject 或 cancel | invitationId、requestId | 受邀者拒绝/邀请者取消 |
| POST {B}/pairs/dissolve | pairId、expectedVersion、requestId | 任一有效成员；撤销私密访问并封存协作 |
| GET {B}/pairs/{pairId} | 无 | 本人结对空间、脱敏伙伴、协作摘要 |
| POST {B}/pairs/ai-partner | chapterId、personaCode、expectedVersion?、requestId | 返回 AI/script session，无假真人pair；等价引擎安全切换编排 |
| POST {B}/users/{userId}/block | requestId | 拉黑并处理关系；不能返回被拉黑方私密数据 |
| DELETE {B}/users/{userId}/block | requestId | 本人解除拉黑 |
| POST {B}/pairs/{pairId}/dual/submit | sessionId、nodeId、visitNo、answer、expectedVersion、requestId、note? | 真人文档兼容路径；member+session一致 |
| GET {B}/pairs/{pairId}/dual/state | sessionId、nodeId? | 当前协作投影，不能任意读其他会话 |
| POST {B}/sessions/{sessionId}/dual/submit | 同上，去掉sessionId | AI/script及真人统一核心路径；后台服务相同 |
| GET {B}/sessions/{sessionId}/dual/state | 当前节点 | 会话成员；仅本人视角 |
| POST {B}/sessions/{sessionId}/dual/extend | nodeId、visitNo、expectedVersion、requestId | TIMEOUT，有限次数，返回新的deadline |
| POST {B}/sessions/{sessionId}/dual/replace | nodeId、visitNo、expectedVersion、requestId、confirm=true | TIMEOUT且自己已提交；封存缺席方该轮写入口，受控AI答案 |
| POST {B}/sessions/{sessionId}/dual/skip | 同上 | 记录本人跳过确认；双方确认后SKIPPED；只确认自己 |
| POST {B}/sessions/{sessionId}/dual/accept-resolution | expectedVersion、requestId | 缺席方回归确认替代结果，才能推进自己 |

双视角提交请求示例：

```json
{
  "sessionId": "1001001",
  "nodeId": "2002002",
  "visitNo": 1,
  "answer": "option-1",
  "expectedVersion": 2,
  "requestId": "d0921112-2c5e-49e0-b3ac-65e72013fd63"
}
```

这里 expectedVersion 指 pair_progress.version；个人 choose 的 expectedVersion 指 story_progress.version。GET state 必须将两个版本分开命名：progressVersion/dualVersion；前端不能混用。

SOLO在dual提交接口采用专用单人练习规则，返回SOLO_PRACTICE结果，不制造另一真人的提交记录；组合练习线索仅在SOLO且无未封存真人轮次时可读。AI/SCRIPTED的缺失slot由服务端受控策略提交，模型本身无submit权限。

第一人响应的 data 仅为：

```json
{
  "state": "WAITING_PARTNER",
  "dualVersion": 3,
  "selfSubmitted": true,
  "partnerSubmitted": false,
  "ownAnswer": "option-1",
  "deadline": "2026-10-30T03:00:00.000Z",
  "allowedActions": ["WAIT"]
}
```

第二人有效提交后为 `JUDGED`，包含 result、explanation_i18n、harmonyDelta、resolutionType 和双方新的 progressVersion；此时可按发布规则揭晓双方答案。过期返回409 DUAL_TIMEOUT并附TIMEOUT状态和允许的恢复动作。`now==deadline` 算过期。相同 requestId 重试返回原快照（可能仍是WAITING），客户端随后 GET 当前 state 获取最新状态；不能把幂等响应当轮询接口。

## 6. AI与实时消息

| 方法 路径 | 请求/返回 | 权限 |
| --- | --- | --- |
| POST {B}/sessions/{sessionId}/ai/chat | nodeId、message、lang、requestId、stream=true/false | 当前成员，本人上下文；stream=false 返回完整审核后reply |
| POST {B}/pairs/{pairId}/ai/chat | sessionId及上述参数 | 原文兼容入口；不因pair路径允许读取真人对侧线索 |
| GET {B}/sessions/{sessionId}/ai/history | cursor | 本人的脱敏/允许恢复对话；不公开管理员prompt |
| GET {B}/pairs/{pairId}/messages | cursor、limit | 当前有效成员，审核通过历史；本人可见自己待审标记 |
| POST {B}/pairs/{pairId}/messages | clientMessageId、type、text?、assetIds? | 先存审后推，不允许未审核私密附件直接发送 |
| POST /api/common/ws-ticket | 当前accessToken | 一次性短期票据（30秒默认），禁止长效token放URL |
| WS /ws | STOMP CONNECT鉴权票据 | 后台校验用户、关系与订阅权限；不是公开topic |

保留原文 `WS /ws/pair/{id}` 作为兼容握手路径，pairId 仍需校验，不能把路径当授权。推荐使用统一 `/ws`，订阅 `/user/queue/pair-events`，事件只带 pairId/sessionId/eventId/type/version，客户端收到后重新拉状态；心跳和断线重连不重发业务动作。SEND 留言必须走同一审核服务。

SSE响应 `Content-Type:text/event-stream`，不使用普通 Result 包裹。事件协议：`meta`（mode=MODEL/SCRIPTED、aiIdentity、requestId）、`status`（GENERATING/REVIEWING）、`delta`（审核通过后的文本片段）、`done`（latencyMs、usage可空）、`error`（可恢复错误）。注释心跳保持连接；Nginx禁用缓冲。开始输出后不重新播放已发文本；断线通过同一requestId查询已完成结果或非流式恢复，不触发游戏推进/重复计费。

无模型密钥必须返回 SCRIPTED 和可解释 fallbackReason，不能声称模型已接入。流式输出前先审核短回复，允许以小片段呈现，UI不误导为“未审核模型实时直出”。

## 7. 上传 打卡 回礼 岁时册 展厅

| 方法 路径 | 请求/返回 | 规则 |
| --- | --- | --- |
| POST /api/common/files | multipart file、purpose、requestId → assetId,status | 登录；图片默认5MB、语音10MB、PDF10MB；首版白名单JPEG/PNG/WebP、MP3/M4A、PDF；服务器验证真实格式；PDF仅必要文件用途 |
| GET /api/common/files/{assetId}/access | purpose | 权限校验后短期下载URL；附件未审只可本人/审核者访问 |
| POST {B}/chapters/{chapterId}/checkin | sessionId、nodeId、note、assetIds、publicRequested、requestId | 当前合法checkin节点；返回PENDING_MANUAL等真实状态 |
| GET {B}/chapters/{chapterId}/checkins | 本人有效版本 | 自己提交和审核原因 |
| POST {B}/chapters/{chapterId}/gift | sessionId、nodeId、content、language、assetIds、tags、publicRequested、requestId | 当前合法gift节点；不能指定别的作者 |
| GET {B}/chapters/{chapterId}/gifts | sessionId | 已审核双方回礼并排、作者类型与翻译状态；无权限不给内容 |
| POST {B}/gifts/{giftId}/interactions | type、message?、requestId | 有权限的首次有效互动，唯一奖励 |
| PUT {B}/checkins/{id} 或 gifts/{id} | expectedVersion、修改内容 | 本人被驳回/草稿重提生成修订，不能改已发奖来源而保留不合法状态 |
| GET {B}/passport | year | 24印章位、真实印章、徽章、年度分母24、真人/AI默契分列 |
| GET {B}/passport/recap | year | 本人真实回顾指标；未知国别不当新国家统计 |
| POST {B}/share/poster | chapterId或year、format=png/pdf、privacy选项、requestId | 返回任务id；只取已获得的奖励与获准公开内容 |
| GET {B}/share/poster/{jobId} | 无 | 任务状态/短期下载，未完成明确pending |
| GET /api/portal/showcase | cursor、termId、lang | 仅公开精选真人内容 |
| POST/DELETE /api/portal/showcase/{id}/like | requestId | 登录且幂等 |
| POST {B}/share/events | contentId、eventType、requestId | 可验证的分享动作/点击，不把按钮点击声称为真实触达人数 |
| POST {B}/reports | targetType、targetId、reason、description、assetIds | 登录、限流、可访问目标；不得反向暴露举报人 |

两种方式都要能查看审核状态；不能提交后前端直接标为已完成。海报公开字段由用户选择，不默认包含头像、国别、伙伴姓名或留言。

## 8. 管理端

下表均在 `/api/admin` 下，必须满足权限 code+方法权限+对象范围；菜单隐藏不代替后端校验。

| 方法 相对路径 | 用途 | 权限 |
| --- | --- | --- |
| GET /users；GET /users/{id} | 脱敏列表及管理详情 | user:read |
| PUT /users/{id}/status；PUT /users/{id}/roles | 封禁/解封、分配已有角色 | user:write / role:grant；不可移除最后可用管理员 |
| GET/POST/PUT /roles；PUT /roles/{id}/permissions | RBAC配置 | role:grant |
| GET /terms/dates?year=；POST /terms/dates | 按年度读/导入日期+source | term:write；批量校验24条、无重复且顺序正确 |
| POST /terms/dates/{year}/review | 审核年度日期版本 | content:review |
| GET/POST /chapters；GET/PUT /chapters/{id} | 篇章、开放规则、封面、上下线 | chapter:write |
| GET/POST /cards；GET/PUT /cards/{id} | 知识卡草稿与修订 | card:write |
| GET/POST/PUT /terminology | 术语及来源维护 | card:write |
| GET/POST /stories；GET /stories/{id} | 逻辑剧情管理 | story:read / story:write |
| POST /stories/{id}/versions | 从当前发布版克隆新草稿 | story:write |
| GET/PUT /stories/{id}/versions/{versionId}/graph | 编辑器整图与位置保存 | story:read / story:write；带revision和schemaVersion |
| POST /stories/{id}/versions/{versionId}/validate | 结构、汇合、变量、翻译、来源检查 | story:write；返回路径定位错误数组 |
| POST /stories/{id}/versions/{versionId}/preview | 创建不发奖预览会话 | story:preview |
| POST /previews/{sessionId}/actions | 使用同一引擎试读测试 | story:preview；强制is_preview |
| PUT /i18n/{resourceType}/{id} | 原文接口细化：双语草稿/校对修订 | content:translate |
| POST /stories/{id}/versions/{versionId}/review | 指定contentHash的文化/双语审校 | content:review |
| POST /stories/{id}/versions/{versionId}/publish | expectedRevision、contentHash、requestId | story:publish；需审核与hash一致，原子切指针 |
| POST /stories/{id}/rollback | targetPublishedVersionId、requestId | story:publish；不修改存档 |
| GET /audits；PUT /audits/{id} | 审核队列、通过/驳回及原因 | audit:review；带expectedVersion |
| GET/PUT /reports/{id} | 举报处理与下线 | report:handle |
| GET/PUT /ai/config；GET/POST/PUT /ai/personas | 参数、secret引用、人设、开关 | ai:config；响应永不含密钥/环境变量值 |
| GET /ai/usage | 调用、延迟、失败、降级、token、费用估计 | ai:usage；费用未知为null |
| GET/PUT /pairs/rules；GET /pairs | 默认匹配权重/上限、异常处理 | pair:manage；不能绕过成员消息隐私 |
| GET/POST/PUT /activities；GET/POST/PUT /badges | 活动及规则版本配置 | operation:write |
| GET /stat/overview | scope=real/demo/all、from、to | stat:read；返回来源与generatedAt，all分开显示 |
| GET /logs/operations | 脱敏操作日志 | audit:read |

后台图保存结构包含 `schemaVersion,revision,entryNodeKey,variables,nodes,edges`。node 的 publicContent、cluesBySlot、privateRules 与 UI position 分开；edge 包含 from/to、optionKey、priority、condition。服务端输出校验错误如 `nodes.spring-dual-1.privateRules.correctPairs`，仅在后台可见；发布后原位PUT返回409 IMMUTABLE_VERSION。

统计返回真实数量、分母口径、unknown分类、isDemo、generatedAt；完成率=区间内开始篇章的用户中已完成数/开始数（按同一 cohort 定义），不能把不同时间分母混合。预览和脚本演示事件分开统计。

## 9. 契约测试清单

阶段1：401/403、注册不能提权、禁用/退出撤销、错误体、ID字符串和脱敏。阶段3：非法节点/选项/变量、幂等同键不同体、旧版恢复和奖励唯一。阶段4：三账号线索隔离、第一人等待无泄露、并发第二提交/超时、SSE无密钥和超限降级。阶段5：邀请上限并发与私密留言越权。阶段6：附件归属、审核门槛、印章无客户端发放。阶段7：未经审校发布失败、修改后hash失效、新旧版本分别读取。阶段8：同用户跨端进度一致。阶段9：全新部署执行完整16步并保存证据。

## 10. 本轮已实现的剧情接口

以下路径同时支持/api/app和/api/portal，均需用户JWT。id为chapterId，当前立春为1。通用pairs接口尚未实现；本轮通过篇章内协作邀请提供双人闭环。

| 方法 | 相对路径 | 输入/行为 |
| --- | --- | --- |
| GET | /chapters | 已发布篇章及本人完成状态 |
| GET | /chapters/{id}/cards | 双语知识卡、出处及SOURCE_CHECKED状态 |
| GET | /stories/{id}/state | 未开始返回started=false；已开始返回玩家投影与持久化存档 |
| POST | /stories/{id}/start | mode为SCRIPT或HUMAN；重复开始返回原存档 |
| POST | /stories/{id}/join | inviteCode；PARTNER接受GUIDE邀请，不覆盖已有存档 |
| POST | /stories/{id}/choose | nodeId、expectedVersion、requestId、action，可选answer/textZh/textEn |
| GET | /stories/rewards | 本人真实结算的印章记录 |

选择示例：

```json
{"nodeId":"1002","expectedVersion":1,"requestId":"a-long-unique-request-id","action":"learn"}
```

dual使用action=submit和自己的answer选项id。超时操作为extend/script/skip。ending使用finish。错误和并发契约见[状态机说明](story-engine.md)。请求体不接受userId、目标节点、变量或奖励字段。

## 11. 本轮AI接口与差异

已实现章节级 /stories/{chapterId}/ai/chat（SSE）、/ai/history，以及管理员 /api/admin/ai/config、usage、knowledge、knowledge/{id}/review。请求与权限见[AI说明](ai-companion.md)。尚无通用pairs服务，因此不伪造原规划pairs路径。

SSE实际事件为meta/delta/reset/done/error；新增reset清除未完成模型文本后显示脚本来源，不重复播放模型文本。未实现独立status事件/心跳及小程序分块适配。正确答案、模型flags与奖励没有任何写回接口。

## 用户端UI整合（2026-10-28）

新增的首页、同行空间、文化手记和岁时册直接复用上述已实现的chapters/profile/state/choose/rewards接口。没有新增或模拟日历日期、伙伴回礼、成就或海报API。客户端新增的chapters()/rewards()仅封装现有GET路径，双视角提交仍带版本和请求UUID。视图映射见[UI设计](ui-design.md)。

## 真实剧情编辑器接口（2026-10-29）

已实现/api/admin/story下11个路径、13个HTTP操作：篇章列表/独立创建、草稿GET/PUT、图校验、隔离预览创建/恢复/推进、发布、版本列表/读取、历史复制与归档。完整表和JSON格式见[编辑器接口](story-editor.md)。

路径以chapter作为稳定编辑入口，发布后生成不可变story_id，与最初规划中的通用stories草稿路径有所区别；避免用户把编辑中的图当成游戏版本。保存/发布/恢复要求expectedRevision，预览也带独立revision；401/403/404/409/400和Result复用现有规范。用户端不新增读取完整图或答案的接口。
