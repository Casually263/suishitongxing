param([string]$MysqlBase = 'D:\mysql-8.0.43-winx64')
$ErrorActionPreference='Stop'
& (Join-Path $PSScriptRoot 'import-local-env.ps1')
$oldPassword=$env:MYSQL_PWD
try {
    $env:MYSQL_PWD=$env:LOCAL_MYSQL_ROOT_PASSWORD
    "CREATE DATABASE IF NOT EXISTS suishi_it CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci; GRANT ALL PRIVILEGES ON suishi_it.* TO 'suishi'@'127.0.0.1';" | & (Join-Path $MysqlBase 'bin\mysql.exe') --protocol=TCP -h 127.0.0.1 -P 3307 -u root
    if($LASTEXITCODE -ne 0){throw 'Integration database preparation failed.'}
} finally { $env:MYSQL_PWD=$oldPassword }
Write-Output 'Isolated suishi_it schema is ready; suishi_dev was not cleared.'
