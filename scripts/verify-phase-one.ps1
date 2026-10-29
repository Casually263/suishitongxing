$ErrorActionPreference = 'Stop'
$projectRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
Push-Location $projectRoot
try {
    $issues = [Collections.Generic.List[string]]::new()
    $backend = @()
    $reports = @(Get-ChildItem 'backend/target/surefire-reports/TEST-*.xml') + @(Get-ChildItem 'backend/target/failsafe-reports/TEST-*.xml')
    foreach ($file in $reports) {
        [xml]$xml = Get-Content -LiteralPath $file.FullName -Raw
        $backend += [pscustomobject]@{
            Name = $xml.testsuite.name
            Tests = [int]$xml.testsuite.tests
            Failures = [int]$xml.testsuite.failures
            Errors = [int]$xml.testsuite.errors
            Skipped = [int]$xml.testsuite.skipped
        }
    }
    $unit = Get-Content 'docs/testing/phase-1-web-unit.json' -Raw | ConvertFrom-Json
    $browser = Get-Content 'web/test-results/results.json' -Raw | ConvertFrom-Json
    $audit = Get-Content '.runtime/npm-audit-all.json' -Raw | ConvertFrom-Json
    if (($backend | Measure-Object Tests -Sum).Sum -ne 10 -or @($backend | Where-Object { $_.Failures -or $_.Errors -or $_.Skipped }).Count) { $issues.Add('Backend tests incomplete or failed') }
    if ($unit.numPassedTests -ne 8 -or $unit.numFailedTests) { $issues.Add('Web unit tests incomplete or failed') }
    if ($browser.stats.expected -ne 3 -or $browser.stats.unexpected -or $browser.stats.skipped) { $issues.Add('Browser tests incomplete or failed') }
    if ($audit.metadata.vulnerabilities.total -ne 0) { $issues.Add('npm audit has reported vulnerabilities') }

    $scriptErrors = 0
    foreach ($file in Get-ChildItem 'scripts' -Filter '*.ps1') {
        $parseErrors = $null
        $tokens = $null
        [Management.Automation.Language.Parser]::ParseFile($file.FullName, [ref]$tokens, [ref]$parseErrors) | Out-Null
        $scriptErrors += $parseErrors.Count
    }
    if ($scriptErrors) { $issues.Add('PowerShell syntax errors') }
    $hash = (Get-FileHash -LiteralPath '岁时同行_系统设计文档.docx' -Algorithm SHA256).Hash
    $unchanged = $hash -eq '5E2D775E58B58D9F7F1A1C799B79DAAF2AD3CF4681B1E939FEB9C82D8FDE3AA6'
    if (-not $unchanged) { $issues.Add('Original DOCX changed') }

    & (Join-Path $PSScriptRoot 'import-local-env.ps1')
    $secretNames = @('DB_PASSWORD','REDIS_PASSWORD','JWT_SECRET_BASE64','EMAIL_ENCRYPTION_KEY_BASE64','EMAIL_HMAC_KEY_BASE64','LOCAL_MYSQL_ROOT_PASSWORD','ADMIN_PASSWORD','DEMO_PASSWORD')
    $files = rg --files --hidden -g '!.runtime/**' -g '!.tools/**' -g '!web/node_modules/**' -g '!backend/target/**' -g '!web/dist/**' -g '!web/test-results/**' -g '!**/.tools/**' -g '!.env*'
    $leaks = 0
    foreach ($file in $files) {
        if ([IO.Path]::GetExtension($file) -notin @('.md','.json','.java','.vue','.ts','.ps1','.yml','.xml','.sql','.css','.svg','.txt')) { continue }
        $content = [IO.File]::ReadAllText((Join-Path $projectRoot $file))
        foreach ($name in $secretNames) {
            $value = [Environment]::GetEnvironmentVariable($name)
            if ($value -and $content.Contains($value)) { $leaks++ }
        }
    }
    if ($leaks) { $issues.Add('Project secret value found outside ignored configuration/runtime') }

    $summary = [ordered]@{
        Date = '2026-10-28'
        Timezone = 'Asia/Shanghai'
        BackendRuntime = 'Java17.0.8 / Spring Boot3.5.16'
        Database = 'MySQL8.0.43 isolated port3307'
        RedisTested = 'Memurai for Redis8.2 compatible TCP service6380, NOT Redis7'
        BackendTests = $backend
        WebUnit = @{ Passed = $unit.numPassedTests; Failed = $unit.numFailedTests }
        Browser = @{ Passed = $browser.stats.expected; Failed = $browser.stats.unexpected; Skipped = $browser.stats.skipped }
        NpmAudit = $audit.metadata.vulnerabilities
        Static = @{ ScriptSyntaxErrors = $scriptErrors; SecretsFound = $leaks; SourceUnchanged = $unchanged }
        NotVerified = @('Docker/Compose runtime','Redis7 original','MinIO','Production HTTPS/backups','WeChat','Full backend CVE scan')
    }
    # Create the referenced report before checking document links to it.
    $summary | ConvertTo-Json -Depth 7 | Set-Content 'docs/testing/phase-1-results.json' -Encoding utf8
    $markdown = @(Get-Item 'README.md','TASKS.md') + @(Get-ChildItem 'docs' -Filter '*.md' -Recurse)
    $links = 0
    foreach ($file in $markdown) {
        $content = Get-Content -LiteralPath $file.FullName -Raw
        if ([regex]::Matches($content, '(?m)^```').Count % 2) { $issues.Add('Unclosed Markdown fence: ' + $file.Name) }
        if ($content.Contains([char]0xFFFD)) { $issues.Add('Unicode replacement character: ' + $file.Name) }
        foreach ($match in [regex]::Matches($content, '\[[^\]]+\]\(([^)]+)\)')) {
            $target = $match.Groups[1].Value
            if ($target -match '^(https?://|#)') { continue }
            $links++
            $resolved = [IO.Path]::GetFullPath((Join-Path $file.DirectoryName (($target -split '#')[0])))
            if (-not (Test-Path -LiteralPath $resolved)) { $issues.Add('Broken link: ' + $file.Name + ' => ' + $target) }
        }
    }
    $summary.Static.MarkdownFiles = $markdown.Count
    $summary.Static.LocalLinks = $links
    $summary.Static.Issues = @($issues.ToArray())
    $summary | ConvertTo-Json -Depth 7 | Set-Content 'docs/testing/phase-1-results.json' -Encoding utf8
    $summary | ConvertTo-Json -Depth 7
    if ($issues.Count) { throw 'Phase1 evidence/static verification failed; see report.' }
} finally { Pop-Location }
