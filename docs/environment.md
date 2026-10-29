# 本机开发环境检查与准备步骤

更新日期：2026-10-28，Asia/Shanghai。阶段1已发现并选用E盘JDK17，项目隔离MySQL3307和Memurai6380已实测联调，源码和构建已完成。Redis7与Docker仍未实际验收。最新检查见[environment-check.json](environment-check.json)，阶段0快照见[历史报告](testing/phase-0-environment-check.json)。

当前运行方式以根README为准；以下第1节为阶段0首次检查历史，第2–7节保留目标环境准备步骤。重跑项目环境可用`check-environment.ps1 -ProjectLocal -JdkHome E:\corretto-17.0.8`，不修改机器全局环境。

阶段0当时只执行只读检查与写报告；阶段1已启动独立开发实例并使用项目自行生成的口令，未修改原有服务、启用WSL或拉取镜像。未检测到指检查范围内未找到，不证明整台机器均未安装。

## 1. 阶段0首次实测结果（历史）

| 组件 | 目标 | 本机结果 | 处理 |
| --- | --- | --- | --- |
| Java/Javac | JDK17 | Java25.0.2、javac25.0.2；Java runtime为 `C:\Program Files\Java\jdk-25.0.2` | 并存安装17并为项目选择；不卸载25 |
| Maven | ≥3.6.3，项目使用3.9系列 | 3.9.12，当前使用Java25 | Maven无需重装，切换JAVA_HOME后复查 |
| Node.js | 项目基线≥22.12，当前24可用 | v24.14.0 | 满足初步版本门槛，依赖实际构建仍待测 |
| npm | 与Node兼容 | 11.11.0 | 使用npm.cmd避免PowerShell执行策略问题 |
| Git | 可用 | 2.47.0.windows.2 | 可用；当前目录还不是Git仓库 |
| MySQL | 8.0且可登录 | `D:\mysql-8.0.43-winx64\bin`存在8.0.43；mysql服务Stopped；3306不可连 | 启动已有服务并用本机已知口令验证；不要重新初始化数据目录 |
| Redis | 7.x且PING成功 | redis-server/redis-cli未检测到；6379不可连 | Docker准备好后运行Redis7开发容器 |
| Docker/Compose | CLI、engine、Compose均可用 | CLI与常见安装路径未检测到，无法检查engine和Compose | 按官方Windows安装流程准备 |
| WSL2 | Docker选用WSL后端时可用 | 系统wsl.exe存在，但--status退出50并明确返回“未安装适用于Linux的Windows子系统” | 安装/启用WSL并重启验证 |
| winget | 可选 | 未检测到 | 以下采用官方安装包，不依赖winget |
| MinIO | 后续真实对象存储 | 9000/9001不可连，未确认有可用安装 | 阶段1验证源码/镜像方案，见第6节 |
| 业务端口 | 8080、5173 | 本轮均不可连 | 正常，目前无业务应用 |

`Get-CimInstance Win32_Service`在当前沙箱返回拒绝访问，随后通过`Get-Service`和`sc.exe query/qc mysql`取得服务停止状态及已安装路径；没有把受限查询当作未安装。TCP探测不等同于数据库认证；当前未验证账号密码、schema、Redis认证、镜像下载和依赖仓库访问。

[Spring Boot3.5官方系统要求](https://docs.spring.io/spring-boot/3.5/system-requirements.html)以Java17为最低基线，较新补丁也可能支持Java25；本项目按用户明确指定统一到17，不能以“25更高”代替JDK17运行验证。[Vite官方指南](https://vite.dev/guide/)列出了Node最低要求；现有24仅代表版本预检通过，不代表前端已构建。

## 2. 安装并选择 JDK17

1. 从 [Eclipse Temurin 官方下载](https://adoptium.net/temurin/releases/?version=17)选择Windows x64、JDK17、HotSpot。可用ZIP便携解压到项目内`.tools/jdk-17`，也可使用MSI安装；遵循[官方Windows安装说明](https://adoptium.net/installation/windows/)。不要覆盖已有JDK25。
2. 为本项目终端临时设置，路径替换成实际JDK17目录（下例假设ZIP内容已放到项目内`.tools/jdk-17`，目录应直接含bin/java.exe）：

```powershell
Set-Location 'E:\suishitongxing_project'
$projectJdk17 = (Resolve-Path -LiteralPath '.\.tools\jdk-17').Path
$env:JAVA_HOME = $projectJdk17
$env:Path = (Join-Path $projectJdk17 'bin') + ';' + $env:Path
java -version
javac -version
mvn -version
```

3. 三个命令都应显示17；Maven仍为现有3.9.12。IDE Project SDK、Maven runner、后续Docker runtime都选17。终端设置仅在当前进程生效；不需要删除Oracle javapath或修改全机环境变量。`.tools`需在阶段1加入.gitignore。

## 3. Docker Desktop 与 WSL

参考 [Docker官方Windows安装](https://docs.docker.com/desktop/setup/install/windows-install/)与[微软WSL安装说明](https://learn.microsoft.com/en-us/windows/wsl/install)。这一步涉及Windows功能、安装和可能的重启，请在本机自行操作；当前任务没有自动修改系统。

1. 按Docker文档核对Windows版本、内存和硬件虚拟化；在有权限的PowerShell执行WSL安装，依提示重启：

```powershell
& "$env:WINDIR\System32\wsl.exe" --install
# 若提示重启，重启完成后再执行后续命令
& "$env:WINDIR\System32\wsl.exe" --update
& "$env:WINDIR\System32\wsl.exe" --version
& "$env:WINDIR\System32\wsl.exe" --status
& "$env:WINDIR\System32\wsl.exe" --list --verbose
```

2. 从Docker官方页面下载Desktop安装包，按所选安装方式操作，选择WSL2后端，启动Docker Desktop，等待engine就绪。需要的系统权限取决于安装模式；不是所有安装步骤都必须全局管理员。
3. 新开终端验证：

```powershell
docker --version
docker compose version
docker info
docker run --rm hello-world
```

CLI有版本不代表engine运行；必须`docker info`成功、hello-world实际拉取运行。镜像不可访问时记录真实错误并配置可用网络/受信任镜像源，不关闭TLS校验。

## 4. 启用已安装的 MySQL8.0

优先使用已有MySQL8.0.43，避免重复安装及3306冲突。确认该服务属于本机开发环境后，在有启动服务权限的终端执行：

```powershell
Get-Service -Name mysql
Start-Service -Name mysql
Get-Service -Name mysql
& 'D:\mysql-8.0.43-winx64\bin\mysql.exe' -h 127.0.0.1 -P 3306 -u root -p
```

`-p`后不带口令，在本机交互提示输入，勿把真实密码发到聊天或写进命令历史。进入MySQL后验证：

```sql
SELECT VERSION(), @@character_set_server, @@collation_server, @@global.time_zone;
SELECT 1;
```

预期版本8.0.x、查询成功；项目schema将显式使用utf8mb4，连接session按UTC配置，不擅自修改现有全局数据库设置。阶段1再用受限的独立业务账号和迁移创建`suishi_dev`，应用不使用root。

若服务启动失败，先查看MySQL错误日志和原配置，记录原因；不运行`mysqld --initialize`、不删除data目录、不重置root密码。若改用Docker新开发库，映射3307并同步应用配置，别与原服务争用3306；检查脚本目前按默认3306探测，改端口后需同步。

## 5. 运行 Redis7 开发依赖

Docker可用后，按[Redis官方Docker运行说明](https://redis.io/docs/latest/operate/oss_and_stack/install/install-stack/docker/)使用Redis7镜像。以下是本机隔离开发示例，命名卷持久化，只绑定127.0.0.1；生产配置使用独立认证与内部网络，不能直接复制无认证设置到公网。

```powershell
docker run -d --name suishi-dev-redis --publish 127.0.0.1:6379:6379 --mount source=suishi-dev-redis-data,target=/data redis:7.4 redis-server --appendonly yes
docker exec suishi-dev-redis redis-cli PING
docker exec suishi-dev-redis redis-server --version
docker inspect suishi-dev-redis --format '{{.State.Status}}'
```

预期PONG、版本7.x、running。若同名容器已存在，先检查其镜像/挂载/用途后复用或`docker start suishi-dev-redis`，不要反复创建或删除数据卷。`redis:7.4`用于首轮开发启动，阶段1验收后记录实际镜像摘要并锁定；不能用会升级主版本的latest替代7。

## 6. MinIO准备与当前上游变化

本轮核验的[MinIO官方仓库](https://github.com/minio/minio)显示仓库已归档，社区版采用source-only分发；不能假定旧教程中的`minio/minio:latest`仍是受维护、可直接取得的部署方案。这是实际发现的依赖风险，保留用户指定MinIO，不自动换成其他存储。

阶段1的具体准备顺序：

1. 从官方仓库选择可复核的tag/commit，记录源码SHA、许可证和`go.mod`要求；将源码放在独立的项目工具目录，不混入业务源包，不执行未经检查的脚本。
2. 按仓库的“Install from Source/Build Docker Image”步骤，使用匹配Go工具链构建Linux版minio二进制，再用仓库Dockerfile构建本地镜像。Go可放Docker构建阶段，避免强制本机长期安装。源码commit、基础镜像摘要与构建命令应随`deploy/minio/`归档；当前未执行或验证该构建。
3. Docker镜像构建成功后，设置独立的MINIO_ROOT_USER/MINIO_ROOT_PASSWORD，命名卷挂载`/data`，只在开发绑定9000/9001，运行`server /data --console-address :9001`。应用使用受限S3账号，不使用root。
4. 验证`http://127.0.0.1:9000/minio/health/live`成功、私有bucket创建、上传/下载及越权拒绝；记录镜像摘要后纳入Compose。若无法完成，阶段1报告具体构建失败原因，不以假上传接口蒙混通过。

这部分是待落实的安装路线，尚无已验证的本地镜像或构建配方。官网归档/分发变化应在部署设计记录中保留；需更换存储时再给出具体方案及需求差异。

## 7. 重跑检查与阶段1门槛

在已选择JDK17的项目终端执行：

```powershell
Set-Location 'E:\suishitongxing_project'
& '.\scripts\check-environment.ps1'
```

脚本会更新docs/environment-check.json，并输出版本、服务和端口，不安装/启动任何东西。脚本单个进程探测最多8秒，WSL输出用UTF-16读取；当前实测可运行。其`Stage1PrecheckReady`只是版本与连通预检：即使为true，也仍需MySQL认证、Redis7确认、MinIO实际连接与依赖下载构建才能完成阶段1验收。

门槛：Java/javac/Maven实际使用17；Maven可解析正式依赖；Node/npm可安装构建；MySQL8登录成功；Redis7/PONG；Docker engine/Compose可运行；MinIO源码/镜像方案经验证。模型API Key不属于启动门槛，无key必须脚本模式可用。微信AppID也不阻止网站阶段，但小程序真实授权须到阶段8验证。

上面的步骤为目标环境准备说明。阶段1现在已有真实工程、mvn/npm构建和MySQL迁移结果，见阶段1测试报告；Docker和生产部署仍未验证。
