param(
    [string]$MysqlBase = 'D:\mysql-8.0.43-winx64',
    [string]$RedisExecutable = 'E:\Memurai\memurai.exe'
)
$ErrorActionPreference = 'Stop'
$projectRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$runtimeDirectory = Join-Path $projectRoot '.runtime'
$mysqlData = Join-Path $runtimeDirectory 'mysql\data'
$redisDirectory = Join-Path $runtimeDirectory 'redis'
New-Item -ItemType Directory -Force -Path $runtimeDirectory,$redisDirectory | Out-Null
$envFile = Join-Path $projectRoot '.env.local'
function New-LocalSecret {
    $bytes = New-Object byte[] 32
    $generator=[Security.Cryptography.RandomNumberGenerator]::Create()
    try {$generator.GetBytes($bytes)} finally {$generator.Dispose()}
    return [Convert]::ToBase64String($bytes)
}
if (-not (Test-Path -LiteralPath $envFile)) {
    @(
        'SPRING_PROFILES_ACTIVE=dev'
        'DB_URL=jdbc:mysql://127.0.0.1:3307/suishi_dev?useUnicode=true&characterEncoding=UTF-8&connectionTimeZone=UTC'
        'DB_USERNAME=suishi'
        ('DB_PASSWORD=' + (New-LocalSecret))
        'REDIS_HOST=127.0.0.1'
        'REDIS_PORT=6380'
        ('REDIS_PASSWORD=' + (New-LocalSecret))
        ('JWT_SECRET_BASE64=' + (New-LocalSecret))
        ('EMAIL_ENCRYPTION_KEY_BASE64=' + (New-LocalSecret))
        ('EMAIL_HMAC_KEY_BASE64=' + (New-LocalSecret))
        ('LOCAL_MYSQL_ROOT_PASSWORD=' + (New-LocalSecret))
        'APP_ALLOWED_ORIGINS=http://localhost:5173,http://127.0.0.1:5173'
        'APP_BOOTSTRAP_ENABLED=true'
        'ADMIN_USERNAME=demo_admin'
        ('ADMIN_PASSWORD=' + (New-LocalSecret))
        'APP_DEMO_ENABLED=true'
        ('DEMO_PASSWORD=' + (New-LocalSecret))
    ) | Set-Content -LiteralPath $envFile -Encoding utf8
}
& (Join-Path $PSScriptRoot 'import-local-env.ps1') -Path $envFile
foreach($value in @($env:DB_PASSWORD,$env:LOCAL_MYSQL_ROOT_PASSWORD,$env:REDIS_PASSWORD)) {
    if($value -notmatch '^[A-Za-z0-9+/=]{32,128}$'){throw 'Local helper expects its generated base64 passwords. Configure an existing custom server separately.'}
}
if($env:DB_USERNAME -ne 'suishi' -or $env:DB_URL -notmatch '^jdbc:mysql://127\.0\.0\.1:3307/suishi_dev\?'){throw 'Local helper only provisions the project schema on loopback port3307.'}
function Test-LocalPort([int]$Port) {
    $client = New-Object Net.Sockets.TcpClient
    try { return $client.ConnectAsync('127.0.0.1',$Port).Wait(200) -and $client.Connected }
    catch { return $false }
    finally { $client.Dispose() }
}
$mysqlExecutable = Join-Path $MysqlBase 'bin\mysqld.exe'
$mysqlClient = Join-Path $MysqlBase 'bin\mysql.exe'
if (-not (Test-Path -LiteralPath $mysqlExecutable)) { throw 'MySQL binary not found; pass -MysqlBase.' }
$newDatabase = -not (Test-Path -LiteralPath (Join-Path $mysqlData 'mysql'))
if ($newDatabase) {
    if ((Test-Path -LiteralPath $mysqlData) -and (Get-ChildItem -LiteralPath $mysqlData -Force | Measure-Object).Count -gt 0) { throw 'Non-empty uninitialized data directory: inspect manually. No files were deleted.' }
    New-Item -ItemType Directory -Path $mysqlData -Force | Out-Null
    & $mysqlExecutable --no-defaults --initialize-insecure "--basedir=$MysqlBase" "--datadir=$mysqlData" --console 2>&1 | Set-Content -LiteralPath (Join-Path $runtimeDirectory 'mysql-initialize.log')
    if ($LASTEXITCODE -ne 0) { throw 'MySQL initialization failed; inspect .runtime/mysql-initialize.log.' }
}
if (-not (Test-LocalPort 3307)) {
    $mysqlArgs = @('--no-defaults', ('--basedir="'+$MysqlBase+'"'), ('--datadir="'+$mysqlData+'"'), '--port=3307','--bind-address=127.0.0.1','--mysqlx=0','--skip-log-bin','--console')
    $mysqlProcess = Start-Process -FilePath $mysqlExecutable -ArgumentList $mysqlArgs -WindowStyle Hidden -PassThru -RedirectStandardOutput (Join-Path $runtimeDirectory 'mysql.out.log') -RedirectStandardError (Join-Path $runtimeDirectory 'mysql.err.log')
    $mysqlProcess.Id | Set-Content -LiteralPath (Join-Path $runtimeDirectory 'mysql.pid')
}
$ready = $false
for ($attempt=0; $attempt -lt 100; $attempt++) { if (Test-LocalPort 3307) { $ready=$true; break }; Start-Sleep -Milliseconds 200 }
if (-not $ready) { throw 'Project MySQL did not become ready; inspect .runtime/mysql.err.log.' }
# Credentials are random base64 without quotes; SQL is sent via stdin, never shell arguments.
$oldMysqlPassword = $env:MYSQL_PWD
try {
    if ($newDatabase) { $env:MYSQL_PWD = '' } else { $env:MYSQL_PWD = $env:LOCAL_MYSQL_ROOT_PASSWORD }
    $actualData=& $mysqlClient --protocol=TCP -h 127.0.0.1 -P 3307 -u root -N -e 'SELECT @@datadir'
    if($LASTEXITCODE -ne 0 -or [IO.Path]::GetFullPath(($actualData|Out-String).Trim()).TrimEnd('\','/') -ne [IO.Path]::GetFullPath($mysqlData).TrimEnd('\','/')) {throw 'Refusing to provision a MySQL instance outside the project runtime.'}
    $sql = "CREATE DATABASE IF NOT EXISTS suishi_dev CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci; CREATE USER IF NOT EXISTS 'suishi'@'127.0.0.1' IDENTIFIED BY '$($env:DB_PASSWORD)'; GRANT ALL PRIVILEGES ON suishi_dev.* TO 'suishi'@'127.0.0.1'; ALTER USER 'root'@'localhost' IDENTIFIED BY '$($env:LOCAL_MYSQL_ROOT_PASSWORD)';"
    $sql | & $mysqlClient --protocol=TCP -h 127.0.0.1 -P 3307 -u root
    if ($LASTEXITCODE -ne 0) { throw 'Project MySQL provisioning failed. Existing machine service was not touched.' }
} finally { $env:MYSQL_PWD = $oldMysqlPassword }
if (-not (Test-LocalPort 6380)) {
    if (-not (Test-Path -LiteralPath $RedisExecutable)) { throw 'Redis-compatible executable not found; use Docker Redis7 or pass -RedisExecutable.' }
    $redisConfig = Join-Path $redisDirectory 'server.conf'
    @('bind 127.0.0.1','port 6380','appendonly yes','save ""',('dir "'+$redisDirectory.Replace('\','/')+'"'),('logfile "'+(Join-Path $redisDirectory 'server.log').Replace('\','/')+'"'),('requirepass '+$env:REDIS_PASSWORD)) | Set-Content -LiteralPath $redisConfig -Encoding ascii
    $redisProcess = Start-Process -FilePath $RedisExecutable -ArgumentList ('"'+$redisConfig+'"') -WorkingDirectory (Split-Path -Parent $RedisExecutable) -WindowStyle Hidden -PassThru -RedirectStandardOutput (Join-Path $runtimeDirectory 'redis.out.log') -RedirectStandardError (Join-Path $runtimeDirectory 'redis.err.log')
    $redisProcess.Id | Set-Content -LiteralPath (Join-Path $runtimeDirectory 'redis.pid')
}
$redisReady = $false
for ($attempt=0; $attempt -lt 50; $attempt++) { if (Test-LocalPort 6380) { $redisReady=$true; break }; Start-Sleep -Milliseconds 200 }
if (-not $redisReady) { throw 'Redis-compatible process did not become ready.' }
Write-Output 'Project dependencies ready: MySQL 127.0.0.1:3307, Redis-compatible server 127.0.0.1:6380. Secrets are only in ignored .env.local.'
