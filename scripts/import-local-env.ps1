param([string]$Path = (Join-Path $PSScriptRoot '..\.env.local'))
$ErrorActionPreference = 'Stop'
foreach ($line in (Get-Content -LiteralPath $Path)) {
    if ($line -match '^([A-Z][A-Z0-9_]*)=(.*)$') {
        [Environment]::SetEnvironmentVariable($Matches[1], $Matches[2], 'Process')
    }
}
# Deliberately do not print environment values.
