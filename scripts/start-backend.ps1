param([string]$JdkHome='E:\corretto-17.0.8')
$ErrorActionPreference='Stop'
& (Join-Path $PSScriptRoot 'import-local-env.ps1')
$env:JAVA_HOME=$JdkHome
$env:Path=(Join-Path $JdkHome 'bin')+';'+$env:Path
$jar=Join-Path $PSScriptRoot '..\backend\target\suishi-backend-0.1.0.jar'
if(-not(Test-Path -LiteralPath $jar)){throw 'Build backend with Maven before starting.'}
& (Join-Path $JdkHome 'bin\java.exe') '-Dfile.encoding=UTF-8' -jar $jar
