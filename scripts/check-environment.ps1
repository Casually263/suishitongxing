param(
    [string]$OutputPath = (Join-Path $PSScriptRoot '..\docs\environment-check.json'),
    [string]$JdkHome = '',
    [switch]$ProjectLocal
)

# Read-only machine inspection. Only the requested JSON report is written.
# No installation/service changes. ProjectLocal imports only this project's env file without printing secrets.
$ErrorActionPreference = 'Continue'
$mysqlPort = 3306
$redisPort = 6379
if ($ProjectLocal) {
    & (Join-Path $PSScriptRoot 'import-local-env.ps1')
    if ($env:DB_URL -match ':(\d+)/') { $mysqlPort = [int]$Matches[1] }
    if ($env:REDIS_PORT) { $redisPort = [int]$env:REDIS_PORT }
}
if ($JdkHome) {
    $env:JAVA_HOME = $JdkHome
    $env:Path = (Join-Path $JdkHome 'bin') + ';' + $env:Path
}

function Invoke-BoundedProcess {
    param([string]$File, [string]$Arguments, [bool]$UnicodeOutput = $false)
    $startInfo = New-Object System.Diagnostics.ProcessStartInfo
    $startInfo.FileName = $File
    $startInfo.Arguments = $Arguments
    $startInfo.UseShellExecute = $false
    $startInfo.CreateNoWindow = $true
    $startInfo.RedirectStandardOutput = $true
    $startInfo.RedirectStandardError = $true
    if ($UnicodeOutput) {
        $startInfo.StandardOutputEncoding = [System.Text.Encoding]::Unicode
        $startInfo.StandardErrorEncoding = [System.Text.Encoding]::Unicode
    }
    $process = $null
    try {
        $process = [System.Diagnostics.Process]::Start($startInfo)
        $stdout = $process.StandardOutput.ReadToEndAsync()
        $stderr = $process.StandardError.ReadToEndAsync()
        if (-not $process.WaitForExit(8000)) {
            $process.Kill()
            $process.WaitForExit()
            return [pscustomobject]@{ ExitCode = $null; Output = 'TIMEOUT after 8 seconds'; Status = 'TIMEOUT' }
        }
        $combined = ($stdout.Result + "`n" + $stderr.Result).Trim()
        $status = if ($process.ExitCode -eq 0) { 'AVAILABLE' } else { 'FAILED' }
        return [pscustomobject]@{ ExitCode = $process.ExitCode; Output = $combined; Status = $status }
    }
    catch {
        return [pscustomobject]@{ ExitCode = $null; Output = $_.Exception.Message; Status = 'ERROR' }
    }
    finally {
        if ($null -ne $process) { $process.Dispose() }
    }
}

function Get-ToolProbe {
    param([string]$Name, [string]$Arguments, [string[]]$FallbackPaths = @(), [bool]$UnicodeOutput = $false)
    $command = Get-Command -Name $Name -CommandType Application -ErrorAction SilentlyContinue | Select-Object -First 1
    $executablePath = if ($command) { $command.Source } else { $null }
    if (-not $executablePath) {
        $executablePath = $FallbackPaths | Where-Object { Test-Path -LiteralPath $_ -PathType Leaf } | Select-Object -First 1
    }
    if (-not $executablePath) {
        return [pscustomobject]@{ Name = $Name; Path = $null; Status = 'NOT_DETECTED'; ExitCode = $null; Output = '' }
    }
    if ([System.IO.Path]::GetExtension($executablePath) -eq '.cmd') {
        # Arguments are fixed version probes in this script, never untrusted input.
        $result = Invoke-BoundedProcess -File $env:ComSpec -Arguments ('/d /s /c ""' + $executablePath + '" ' + $Arguments + '"')
    }
    else {
        $result = Invoke-BoundedProcess -File $executablePath -Arguments $Arguments -UnicodeOutput $UnicodeOutput
    }
    return [pscustomobject]@{ Name = $Name; Path = $executablePath; Status = $result.Status; ExitCode = $result.ExitCode; Output = $result.Output }
}

function Get-PortProbe {
    param([int]$Port)
    $client = New-Object System.Net.Sockets.TcpClient
    try {
        $connect = $client.ConnectAsync('127.0.0.1', $Port)
        $open = $connect.Wait(500) -and $client.Connected
        $state = if ($open) { 'OPEN' } else { 'CLOSED_OR_UNREACHABLE' }
    }
    catch { $state = 'CLOSED_OR_UNREACHABLE' }
    finally { $client.Dispose() }
    return [pscustomobject]@{ Host = '127.0.0.1'; Port = $Port; State = $state }
}

$toolsFound = @(
    Get-ToolProbe -Name 'java.exe' -Arguments '-version'
    Get-ToolProbe -Name 'javac.exe' -Arguments '-version'
    Get-ToolProbe -Name 'mvn.cmd' -Arguments '-version'
    Get-ToolProbe -Name 'node.exe' -Arguments '--version'
    Get-ToolProbe -Name 'npm.cmd' -Arguments '--version'
    Get-ToolProbe -Name 'git.exe' -Arguments '--version'
    Get-ToolProbe -Name 'mysql.exe' -Arguments '--version' -FallbackPaths @('D:\mysql-8.0.43-winx64\bin\mysql.exe')
    Get-ToolProbe -Name 'mysqld.exe' -Arguments '--version' -FallbackPaths @('D:\mysql-8.0.43-winx64\bin\mysqld.exe')
    Get-ToolProbe -Name 'redis-server.exe' -Arguments '--version'
    Get-ToolProbe -Name 'redis-cli.exe' -Arguments '--version'
    Get-ToolProbe -Name 'memurai.exe' -Arguments '--version' -FallbackPaths @('E:\Memurai\memurai.exe')
    Get-ToolProbe -Name 'docker.exe' -Arguments '--version' -FallbackPaths @(
        (Join-Path $env:ProgramFiles 'Docker\Docker\resources\bin\docker.exe'),
        (Join-Path $env:LOCALAPPDATA 'Programs\DockerDesktop\resources\bin\docker.exe')
    )
    Get-ToolProbe -Name 'wsl.exe' -Arguments '--status' -FallbackPaths @((Join-Path $env:WINDIR 'System32\wsl.exe')) -UnicodeOutput $true
    Get-ToolProbe -Name 'winget.exe' -Arguments '--version'
)

$dockerProbe = $toolsFound | Where-Object { $_.Name -eq 'docker.exe' }
$dockerEngine = [pscustomobject]@{ Status = 'NOT_CHECKED'; ExitCode = $null; Output = 'Docker CLI not available' }
$compose = $dockerEngine
$containerRedis = $dockerEngine
if ($dockerProbe.Status -eq 'AVAILABLE') {
    $dockerEngine = Invoke-BoundedProcess -File $dockerProbe.Path -Arguments 'info --format "{{.ServerVersion}}"'
    $compose = Invoke-BoundedProcess -File $dockerProbe.Path -Arguments 'compose version'
    if ($dockerEngine.Status -eq 'AVAILABLE') {
        $containerRedis = Invoke-BoundedProcess -File $dockerProbe.Path -Arguments 'exec suishi-dev-redis redis-server --version'
    }
}

$mysqlService = Get-Service -Name 'mysql' -ErrorAction SilentlyContinue
$serviceStatus = if ($mysqlService) { $mysqlService.Status.ToString() } else { 'NOT_DETECTED' }
$ports = @(3306, 6379, 3307, 6380, 8080, 5173, 9000, 9001 | ForEach-Object { Get-PortProbe -Port $_ })
$issues = New-Object 'System.Collections.Generic.List[string]'
$javaProbe = $toolsFound | Where-Object { $_.Name -eq 'java.exe' }
$javacProbe = $toolsFound | Where-Object { $_.Name -eq 'javac.exe' }
$mavenProbe = $toolsFound | Where-Object { $_.Name -eq 'mvn.cmd' }
$nodeProbe = $toolsFound | Where-Object { $_.Name -eq 'node.exe' }
$npmProbe = $toolsFound | Where-Object { $_.Name -eq 'npm.cmd' }
if ($javaProbe.Output -notmatch 'version "17\.' -or $javacProbe.Output -notmatch 'javac 17\.') { $issues.Add('JDK_17_NOT_ACTIVE') }
if ($mavenProbe.Status -ne 'AVAILABLE') { $issues.Add('MAVEN_NOT_AVAILABLE') }
elseif ($mavenProbe.Output -notmatch 'Java version: 17\.') { $issues.Add('MAVEN_NOT_USING_JAVA_17') }
if ($mavenProbe.Output -match 'Apache Maven (\d+\.\d+\.\d+)') {
    if ([version]$Matches[1] -lt [version]'3.6.3') { $issues.Add('MAVEN_TOO_OLD') }
}
if ($nodeProbe.Status -ne 'AVAILABLE' -or $npmProbe.Status -ne 'AVAILABLE') { $issues.Add('NODE_OR_NPM_NOT_AVAILABLE') }
elseif ($nodeProbe.Output -match '^v(\d+\.\d+\.\d+)') {
    if ([version]$Matches[1] -lt [version]'22.12.0') { $issues.Add('NODE_BELOW_PROJECT_BASELINE_22_12') }
}
if (($ports | Where-Object { $_.Port -eq $mysqlPort }).State -ne 'OPEN') { $issues.Add('MYSQL_TCP_NOT_AVAILABLE') }
if (($ports | Where-Object { $_.Port -eq $redisPort }).State -ne 'OPEN') { $issues.Add('REDIS_TCP_NOT_AVAILABLE') }
if ($dockerEngine.Status -ne 'AVAILABLE') { $issues.Add('DOCKER_ENGINE_NOT_AVAILABLE') }
if ($compose.Status -ne 'AVAILABLE') { $issues.Add('DOCKER_COMPOSE_NOT_AVAILABLE') }

$report = [ordered]@{
    CheckedAtClientTime = [TimeZoneInfo]::ConvertTimeBySystemTimeZoneId([DateTimeOffset]::UtcNow, 'China Standard Time').ToString('o')
    ClientTimezone = 'Asia/Shanghai'
    Workspace = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
    InspectionOnly = $true
    ProjectLocal = [bool]$ProjectLocal
    ProjectMySqlPort = $mysqlPort
    ProjectRedisPort = $redisPort
    Tools = $toolsFound
    MySqlService = $serviceStatus
    DockerEngine = $dockerEngine
    DockerCompose = $compose
    ContainerRedisVersion = $containerRedis
    Ports = $ports
    Stage1PrecheckReady = ($issues.Count -eq 0)
    ProjectIdentityPrecheckReady = (@($issues | Where-Object { $_ -notmatch '^DOCKER_' }).Count -eq 0)
    Redis7Verified = ($containerRedis.Output -match 'v=7\.')
    GateIssues = @($issues.ToArray())
    NotVerifiedByThisScript = @('MySQL authenticated query and clean migration', 'Redis 7 authenticated PING/version', 'Dependency resolution and actual builds', 'MinIO verified source/image and connectivity')
    ScopeLimit = 'Not detected means absent from PATH and known locations, not proof of absence on all disks. ProjectLocal imports project-owned configuration; output excludes secret values and no machine credential store is queried.'
}
$absoluteOutput = [IO.Path]::GetFullPath($OutputPath)
$parentDirectory = Split-Path -Parent $absoluteOutput
if (-not (Test-Path -LiteralPath $parentDirectory)) { New-Item -ItemType Directory -Path $parentDirectory -Force | Out-Null }
$report | ConvertTo-Json -Depth 8 | Set-Content -LiteralPath $absoluteOutput -Encoding utf8
$report | ConvertTo-Json -Depth 8
