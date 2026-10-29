# 团队协作与代码共享

项目按所有者确认于 2026-10-29 改为公开仓库：[Casually263/suishitongxing](https://github.com/Casually263/suishitongxing)。可以直接把链接发给队友，任何人都能查看和下载代码。

公开可读不等于可直接修改本仓库。当前没有邀请协作者；后续由所有者按 GitHub 用户名逐一邀请，伙伴接受邀请后才能直接推送自己的分支。团队开发通常授予写入权限，无需授予管理员权限；未获写权限的开发者也可以通过自己的 Fork 提交 Pull Request。

## 首次获取项目

在 Git Bash、PowerShell 或 IDEA 的 Git 克隆入口使用仓库地址，无需先获得邀请：

```powershell
git clone https://github.com/Casually263/suishitongxing.git
cd suishitongxing
git switch -c feature/your-task
```

这是代码仓库，不包含原开发者的数据库、账号密码或已安装的依赖。每位成员独立配置自己的 MySQL、Redis、Java 17、Maven 和 Node.js。初始化 SQL 在 `backend/src/main/resources/db/migration/`，后端启动后由 Flyway 执行。

从 `.env.example` 创建自己的 `.env.local`，配置本机数据库、Redis、管理员口令及三个独立随机的 32 字节 Base64 密钥。AI 密钥可留空，使用脚本伙伴。不要索取或复制其他成员的 `.env.local`。

Windows 本地启动脚本中的 JDK、MySQL、Redis 可执行文件默认路径来自原开发电脑；请使用脚本的 `-JdkHome`、`-MysqlBase`、`-RedisExecutable` 参数指定自己的安装目录。详细命令见 [README](../README.md) 和 [运行指南](run-guide.md)。使用自己已有的数据库时，应自行创建 `suishi_dev` 并配置环境变量，不要运行专用于隔离实例的依赖初始化脚本。

前端首次安装依赖：

```powershell
npm.cmd --prefix web ci
npm.cmd --prefix web run dev
```

用户端和管理端共用 `web` 工程与后端。Docker 配置已提供，但原开发电脑尚未完成 Docker 运行验收。

## 日常提交

以下向原仓库推送的流程适用于已经接受写权限邀请的团队成员：

```powershell
git pull --ff-only origin main
git switch -c feature/another-task
# 修改、构建与验证完成后
node scripts/verify-git-share.mjs
git add .
git diff --cached --stat
git commit -m "Describe the change"
git push -u origin HEAD
```

随后在 GitHub 发起 Pull Request，由伙伴检查后合并。已有工作分支时直接切换到自己的分支继续，不必重复创建。

`.gitignore` 排除 `.env.local`、运行数据、依赖目录、构建产物和私钥文件。分享检查会检查 Git 候选文件、体积、当前本机秘密的副本与常见令牌格式；它不能识别所有可能的秘密，也不替代人工检查。不要使用 `git add -f` 强行上传这些文件，不要把含密码的截图、数据库备份或真实用户数据放进仓库。
