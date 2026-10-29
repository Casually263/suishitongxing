# 本机日常运行指南

适用位置：`E:\suishitongxing_project`。以下是在这台 Windows 电脑上已使用的开发运行方式，命令在 PowerShell 中执行，IDEA 下方的 Terminal 也可以。

## 每次启动：两个终端

### 终端一：启动数据库、Redis 和后端

```powershell
cd E:\suishitongxing_project
.\scripts\start-local-dependencies.ps1
.\scripts\start-backend.ps1
```

先等待依赖脚本显示 `Project dependencies ready`，再运行后端脚本。看到 `Started SuishiApplication` 表示后端启动完成。保留此终端运行。

脚本自动读取根目录 `.env.local` 中的账号、密码和密钥，并选择本机 `E:\corretto-17.0.8` 的 Java 17。数据库初始化由 Flyway 自动处理，无需手动执行 SQL，无需每次修改 yml 或创建 IDEA 数据源。

### 终端二：启动前端

在 IDEA 的 Terminal 中点击 `+` 新建终端，执行：

```powershell
cd E:\suishitongxing_project
npm.cmd --prefix web run dev
```

看到 Vite 输出访问地址后，在浏览器打开网站，保留此终端运行。用户端与管理端属于同一个前端工程，不需要分别启动。

## 浏览器访问地址

| 用途 | 地址 |
| --- | --- |
| 用户登录 | http://127.0.0.1:5173/login |
| 用户注册 | http://127.0.0.1:5173/register |
| 管理员登录 | http://127.0.0.1:5173/admin/login |
| 接口文档 | http://127.0.0.1:8080/swagger-ui/index.html |
| 健康检查 | http://127.0.0.1:8080/actuator/health |

健康检查返回 `"status":"UP"` 表示依赖检查正常。建议日常统一使用上面的 `127.0.0.1` 地址。

演示用户是 `demo_guide`、`demo_partner`，密码为 `.env.local` 中的 `DEMO_PASSWORD`；管理员是 `demo_admin`，密码为同文件中的 `ADMIN_PASSWORD`。如果你已经修改了用户密码，使用修改后的密码。

## 用完后如何关闭

1. 到前端终端按 `Ctrl+C`，如果询问是否终止批处理，输入 `Y` 回车。
2. 到后端终端按 `Ctrl+C`，等待后端退出。
3. 在可以输入命令的 PowerShell 终端执行：

```powershell
cd E:\suishitongxing_project
.\scripts\stop-local-dependencies.ps1
```

关闭依赖会保留数据库数据。下次按启动步骤重新运行即可。数据库实际数据在根目录 `.runtime` 中，不要删除该目录；`.env.local` 保存这些数据使用的密码和密钥，也应保留。

## 常见情况

### PowerShell 提示禁止执行脚本

在出现提示的终端先执行下面一行，再重试原来的脚本。它只影响当前 PowerShell 进程，新终端如遇相同提示，需要分别执行。

```powershell
Set-ExecutionPolicy -Scope Process -ExecutionPolicy Bypass
```

### 已经启动过后端或前端

同一服务只运行一份。后端已经在运行就无需再次启动；如果端口 `8080` 或 `5173` 被已有项目进程占用，先到原终端按 `Ctrl+C`，再启动。不要同时通过脚本和 IDEA 绿色运行按钮启动两份后端。

### 想在 IDEA 的 Database 面板查看表

启动依赖后，添加 MySQL 数据源：Host 为 `127.0.0.1`，Port 为 `3307`，User 为 `suishi`，Database 为 `suishi_dev`，Password 使用 `.env.local` 中 `DB_PASSWORD=` 后面的值。IDEA 数据源只用于查看和操作数据库，不是后端运行的前提。

建表脚本是 [V1__identity.sql](../backend/src/main/resources/db/migration/V1__identity.sql) 和 [V2__roles_and_tags.sql](../backend/src/main/resources/db/migration/V2__roles_and_tags.sql)，由后端自动执行。项目使用的 `3307` 数据库与个人原有的 `3306` 数据库分开，账号不能混用。

### 前端缺少依赖

当前电脑已经安装过，不必每次安装。如果重新下载了项目或删除了 `web/node_modules`，先执行一次：

```powershell
cd E:\suishitongxing_project
npm.cmd --prefix web ci
```

然后再执行前端启动命令。

### 修改了后端代码

`start-backend.ps1` 运行的是已打包的 JAR，修改 Java 代码后需先停止后端并重新打包。依赖可以保持运行，在 PowerShell 中执行：

```powershell
cd E:\suishitongxing_project
.\scripts\build-backend.ps1
```

确认 `BUILD SUCCESS` 后再执行 `.\scripts\start-backend.ps1`。完整验证命令与测试边界见 [README](../README.md)。前端日常开发由 Vite 自动更新页面，通常无需重启。

该构建脚本显式选择Java17、项目Maven配置和用户依赖缓存，避免本机全局Maven配置冲突。完整后端测试可用 `.\scripts\build-backend.ps1 -IntegrationTests`，需先启动本地依赖，测试只清理独立的`suishi_it`。

## 小满AI伙伴

启动步骤与以前一致，进入立春后可在页面下方对话；不配置模型密钥时明确使用本地脚本。模型环境变量、知识审校和演示说明见[AI伙伴指南](ai-companion.md)。管理入口为 http://127.0.0.1:5173/admin/ai 。本轮验证命令是 `node scripts/verify-ai.mjs`，原阶段报告保留为历史记录。
