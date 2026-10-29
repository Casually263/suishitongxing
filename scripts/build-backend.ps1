param(
    [string]$JdkHome = 'E:\corretto-17.0.8',
    [string]$MavenRepository = (Join-Path ([Environment]::GetFolderPath('UserProfile')) '.m2\repository'),
    [switch]$IntegrationTests
)
$ErrorActionPreference = 'Stop'
$projectRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
if (-not (Test-Path -LiteralPath (Join-Path $JdkHome 'bin\java.exe'))) { throw 'Java 17 not found; pass -JdkHome.' }
& (Join-Path $PSScriptRoot 'import-local-env.ps1')
$env:JAVA_HOME = $JdkHome
$env:Path = (Join-Path $JdkHome 'bin') + ';' + $env:Path
$previousIntegration = $env:RUN_INTEGRATION_TESTS
Push-Location $projectRoot
try {
    if ($IntegrationTests) {
        & (Join-Path $PSScriptRoot 'prepare-integration-db.ps1')
        $env:RUN_INTEGRATION_TESTS = 'true'
    } else {
        $env:RUN_INTEGRATION_TESTS = 'false'
    }
    # Explicit settings/cache avoid this machine's malformed global settings and C:\.m2 default.
    $settings = Join-Path $projectRoot 'backend\maven-settings.xml'
    $mavenArguments = @('-B','-gs',$settings,'-s',$settings,('-Dmaven.repo.local=' + $MavenRepository),'-f',(Join-Path $projectRoot 'backend\pom.xml'))
    if ($IntegrationTests) { $mavenArguments += 'verify' } else { $mavenArguments += 'package' }
    & mvn @mavenArguments
    if ($LASTEXITCODE -ne 0) { throw 'Backend build failed. Stop a running project JAR before rebuilding; see Maven output.' }
} finally {
    $env:RUN_INTEGRATION_TESTS = $previousIntegration
    Pop-Location
}
