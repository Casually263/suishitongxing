# 用户端 UI 验证记录

日期：2026-10-28。环境：Windows、本机 Edge、Vite `127.0.0.1:5173`、已有真实后端 `127.0.0.1:8080`、MySQL 3307、Redis兼容服务6380。

## 最终结果

| 检查 | 结果 |
| --- | --- |
| `npm.cmd --prefix web run build` | TypeScript检查与Vite生产构建通过，无新增依赖 |
| `npm.cmd --prefix web run test:unit` | 14项通过，0失败/跳过；导航安全与SSE分片测试保持通过 |
| `npm.cmd --prefix web run test:e2e` | 10条浏览器流程通过，0失败/跳过；包含原有7条和新增3条 |
| 本机服务 | 健康检查UP，网站HTTP 200 |
| 图像复核 | 查看桌面/手机首页、四季主题、阅读器、同行空间、回礼和岁时册截图；调整窄屏字号与截图过渡残影后重新构建/回归 |

构建日志位于被忽略的 `.runtime/ui-build.log`，浏览器日志 `.runtime/ui-regression.log`，完整Playwright JSON `web/test-results/results.json`。可保留证据见 [单测JSON](ui-web-unit.json) 和 [验证摘要](ui-results.json)。

后端源码与协议未修改，本轮未重跑Java测试。上一轮53项后端测试的历史结果见 [AI验证](ai-validation.md)，不能视为本轮重新执行。未安装新依赖，本轮未重复npm审计。

## 真正执行的流程

1. 真实注册、登录、档案修改/刷新、多标签恢复、退出及权限守卫；管理员真实用户列表与AI设置/统计/知识审校页面继续通过。
2. 小满真实SSE响应、中英对话、聊天刷新恢复、不改变剧情存档。无模型密钥时使用后端脚本回复，不调用付费模型。
3. 24个环形按钮、方向键焦点、下拉选择、四季切换与真实已发布篇章入口；没有把未开发篇章显示成可玩。
4. 360px英文首页/同行/回礼/岁时册/阅读器和768px平板无横向溢出；字号切换和取消对照真实生效。另检查390px中文与桌面布局。
5. 实际脚本通关立春，等待服务端确认存档后刷新，填写中英回礼及中文打卡、领取唯一印章；回礼页读取2份真实提交、筛选为1份，岁时册读取1枚印章，刷新仍保留，未获冬至印章无伪造奖励。
6. 两个独立真实账号分别看到A/B线索。在同行空间提交A侧后刷新仍等待，再回阅读器；B侧提交后两个阅读器都收到统一结果，另一侧答案不提前出现。
7. 延迟真实奖励请求验证加载指示；中断该请求验证错误/重试，不提供伪响应。恢复网络后重新读取真实0印章记录。
8. 使用浏览器print媒体验证导航隐藏、印章墙保留。未打开操作系统打印对话框，也未进行实体打印。

首轮旧剧情用例在点击后未等待POST确认便立即重载，产生一次竞态失败；已改为等待服务器返回的新节点再刷新。最终全套通过。截图关闭有限过渡并回到页顶，避免把未结束的主题过渡或sticky滚动位置保存成页面缺陷。

## 截图

| 页面 | 桌面 | 手机 |
| --- | --- | --- |
| 春季首页 | [青绿首页](ui-home-spring.png) | [中文](ui-home-mobile.png)、[英文](ui-en-home-mobile.png) |
| 其他首页主题 | [夏至朱红](ui-home-summer.png)、[立秋金黄](ui-home-autumn.png)、[冬至靛蓝](ui-home-winter.png) | 使用同一响应式结构 |
| 阅读器 | [知识与开始](ui-reader-start-desktop.png)、[进行中的双视角](story-lichun-desktop.png) | [双视角阅读](story-lichun-mobile.png)、[英文开始](ui-en--stories-1-mobile.png) |
| 同行空间 | [真实等待状态](ui-collaboration-desktop.png) | [独立线索](ui-collaboration-mobile.png) |
| 文化手记 | [双语原文](ui-gifts-desktop.png) | [语言分段](ui-gifts-mobile.png) |
| 数字岁时册 | [真实1印章](ui-passport-desktop.png) | [移动收藏册](ui-passport-mobile.png) |
| 失败状态 | [网络中断与重试](ui-network-error.png) | 共用响应式错误组件 |

截图全部使用实际入库的开发/测试账号及测试文字，不代表真实运营用户或调研。现有回归测试会重新生成部分旧文件名的截图，图片展示当前UI。

## 复现

按 [运行指南](../run-guide.md) 启动后端和网站，项目根目录执行：

```powershell
npm.cmd --prefix web run build
npm.cmd --prefix web run test:unit -- --reporter=json --outputFile=../docs/testing/ui-web-unit.json
& '.\scripts\import-local-env.ps1'
npm.cmd --prefix web run test:e2e
node scripts/verify-ui.mjs
```

测试会新增 `ui_*`、`story_*`、`ai_*`、`e2e_*`账号，不删除现有业务数据。管理员密码从本机环境读取，无硬编码口令。手机检查是Edge视口模拟，未冒充真实手机/Safari验收；屏幕阅读器完整审计、生产部署和后端性能测试不在本轮范围。
