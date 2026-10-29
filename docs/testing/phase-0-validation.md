# 阶段0验证记录

日期：2026-10-28（Asia/Shanghai）。工作目录：`E:\suishitongxing_project`。范围：需求文档分析、七个指定文件与补充环境资料；未涉及业务实现。

## 实际执行及结果

| 检查 | 方法 | 结果与边界 |
| --- | --- | --- |
| 源文件完整性 | Get-FileHash SHA256 | 与读取前摘要相同，原DOCX未修改；摘要见source/README.md |
| 读取范围 | .NET ZipFile+XML按body顺序提取 | 251非空段落/表格块，最后P288文档结束；读取16章及全部表格，另查页眉页脚；非分页视觉审查 |
| 指定输出文件 | Test-Path与非空检查 | 六个指定docs Markdown和TASKS共7文件存在且非空 |
| 文档结构 | Markdown围栏配对、UTF-8替换字符检查 | 检查通过；不代表Markdown渲染器或Mermaid渲染已实际验证 |
| 本地链接 | 正则提取Markdown目标，按文件所在目录解析并Test-Path | 检查通过，无失效本地文件链接；外部链接依据浏览工具查阅主要官方文档，不宣称全部URL健康检查 |
| 需求编号 | 对requirements表格行提取R编号 | 37个唯一功能需求，覆盖来源与阶段；语义覆盖另作人工交叉检查 |
| 检查脚本语法 | PowerShell Language.Parser.ParseFile | 无语法错误 |
| 检查脚本运行 | 执行scripts/check-environment.ps1 | 退出0并生成可解析JSON；这表示检查脚本成功，不代表环境达标 |
| 版本与服务 | java/javac/mvn/node/npm/git/MySQL版本，Get-Service与sc | 真实结果见environment-check.json；MySQL8.0.43存在但停止 |
| WSL状态 | 显式系统路径、UTF-16读取--status | 退出50，明确未安装WSL；未把乱码或PATH缺失直接当作安装结论 |
| 端口 | 127.0.0.1 TCP限时连接 | 3306/6379/8080/5173/9000/9001均未连通；无认证成功声明 |
| 设计交叉核验 | 人工对照需求、ER、API、阶段、演示 | 补齐AI替代任一缺失slot、单人dual语义、重玩奖励唯一、关系解除和协作锁序、旧版本会话及阶段依赖说明 |

## 环境检查结论

Java/Javac25.0.2、Maven3.9.12使用Java25；Node24.14.0、npm11.11.0、Git2.47.0可用。JDK17未检测到；MySQL服务停止；Redis/Docker未检测到，WSL未安装。Stage1PrecheckReady=false，与任务清单及开发计划一致。

检查未安装或启动服务、未修改系统环境变量、未读取数据库/模型口令。`Get-CimInstance`权限受限，使用可用的服务查询补充证据。MinIO上游归档与源码分发状态已通过官方仓库核验，实际构建尚未执行。

## 尚未执行的验证

当前无backend/pom.xml、web/package.json及数据库迁移，因此没有运行Maven编译、npm依赖安装/构建、业务单元/集成测试、Playwright、数据库登录、Docker部署、MinIO构建或微信授权验证。也未渲染原Word的分页图像或进行UI截图核验。

本轮已完成阶段0的设计与环境诊断；阶段1及后续业务均待实现。不能据此记录“项目可运行”“接口通过”或“演示成功”。环境安装与验证步骤见[../environment.md](../environment.md)。
