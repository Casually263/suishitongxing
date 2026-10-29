param([string]$MysqlBase='D:\mysql-8.0.43-winx64',[string]$RedisCli='E:\Memurai\memurai-cli.exe')
$ErrorActionPreference='Stop'
$projectRoot=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
& (Join-Path $PSScriptRoot 'import-local-env.ps1')
$oldMysql=$env:MYSQL_PWD
$oldRedis=$env:REDISCLI_AUTH
try {
    $env:MYSQL_PWD=$env:LOCAL_MYSQL_ROOT_PASSWORD
    $mysqlClient=Join-Path $MysqlBase 'bin\mysql.exe'
    $dataPath=(& $mysqlClient --protocol=TCP -h 127.0.0.1 -P 3307 -u root -N -e 'SELECT @@datadir' 2>$null)
    if($LASTEXITCODE -eq 0) {
        $expected=[IO.Path]::GetFullPath((Join-Path $projectRoot '.runtime\mysql\data')).TrimEnd('\','/')
        $actual=[IO.Path]::GetFullPath(($dataPath|Out-String).Trim()).TrimEnd('\','/')
        if($actual -ne $expected){throw 'Refusing to stop MySQL: data directory is not the project runtime.'}
        & (Join-Path $MysqlBase 'bin\mysqladmin.exe') --protocol=TCP -h 127.0.0.1 -P 3307 -u root shutdown
        if($LASTEXITCODE -ne 0){throw 'MySQL shutdown failed.'}
    }
    $env:REDISCLI_AUTH=$env:REDIS_PASSWORD
    $configuration=& $RedisCli -h 127.0.0.1 -p 6380 --raw CONFIG GET dir 2>$null
    if($LASTEXITCODE -eq 0) {
        $dirValue=($configuration|Select-Object -Last 1).Trim()
        if([IO.Path]::GetFullPath($dirValue).TrimEnd('\','/') -ne [IO.Path]::GetFullPath((Join-Path $projectRoot '.runtime\redis')).TrimEnd('\','/')) {throw 'Refusing to stop Redis: directory is not the project runtime.'}
        & $RedisCli -h 127.0.0.1 -p 6380 SHUTDOWN
        if($LASTEXITCODE -ne 0){throw 'Redis shutdown failed.'}
    }
} finally {$env:MYSQL_PWD=$oldMysql;$env:REDISCLI_AUTH=$oldRedis}
Write-Output 'Project dependency shutdown finished. Data was preserved; no system service was changed.'
