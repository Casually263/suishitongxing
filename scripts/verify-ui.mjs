import fs from 'node:fs'
import path from 'node:path'
import { createHash } from 'node:crypto'
import assert from 'node:assert/strict'

const root = process.cwd()
function walk(dir) {
  return fs.readdirSync(dir, { withFileTypes: true }).flatMap(item => item.isDirectory() ? walk(path.join(dir, item.name)) : [path.join(dir, item.name)])
}
const sources = walk('web/src').filter(file => /\.(vue|ts|css)$/.test(file))
const unit = JSON.parse(fs.readFileSync('docs/testing/ui-web-unit.json', 'utf8'))
const browser = JSON.parse(fs.readFileSync('web/test-results/results.json', 'utf8'))
assert(unit.success && unit.numFailedTests === 0 && unit.numPendingTests === 0 && unit.numPassedTests >= 14, 'Frontend unit suite must pass without skips')
assert(browser.stats.expected >= 10 && browser.stats.unexpected === 0 && browser.stats.skipped === 0 && browser.stats.flaky === 0, 'All browser flows must pass without skips or retries')
const build = fs.statSync('web/dist/index.html')
assert(sources.every(file => fs.statSync(file).mtimeMs <= build.mtimeMs), 'Rebuild after source edits before recording evidence')
const clientFiles = [...sources, ...walk('web/dist').filter(file => /\.(js|html|css)$/.test(file))]
assert(clientFiles.every(file => !/\bsk-[A-Za-z0-9_-]{20,}|-----BEGIN (?:RSA |EC )?PRIVATE KEY-----/.test(fs.readFileSync(file, 'utf8'))), 'Credential-like material found in client artifacts')
const documentFiles = ['README.md', 'TASKS.md', 'docs/ui-design.md', 'docs/testing/ui-validation.md']
let linksChecked = 0
for (const file of documentFiles) {
  const document = fs.readFileSync(file, 'utf8')
  for (const [, target] of document.matchAll(/\]\(([^)]+)\)/g)) {
    if (/^(https?:|#)/.test(target)) continue
    const resolved = path.resolve(path.dirname(file), target.split('#')[0])
    // This report is created below, after the independent checks succeed.
    assert(resolved === path.resolve('docs/testing/ui-results.json') || fs.existsSync(resolved), `Missing local link in ${file}: ${target}`)
    linksChecked++
  }
}
const healthResponse = await fetch('http://127.0.0.1:8080/actuator/health', { signal: AbortSignal.timeout(5000) })
const health = await healthResponse.json()
assert(healthResponse.ok && health.status === 'UP', 'Existing backend must be healthy')
const webResponse = await fetch('http://127.0.0.1:5173', { signal: AbortSignal.timeout(5000) })
assert(webResponse.ok, 'Local website must be available')
const report = {
  recordedAt: new Date().toISOString(), workspace: root,
  frontend: { productionBuildAt: build.mtime.toISOString(), unitPassed: unit.numPassedTests, browserPassed: browser.stats.expected, skipped: browser.stats.skipped, failed: browser.stats.unexpected, browserDurationMs: browser.stats.duration },
  runtime: { backendHealth: health.status, websiteHttpStatus: webResponse.status },
  review: { localDocumentationLinksChecked: linksChecked, clientCredentialPatternScan: 'passed (limited pattern scan, not a full security audit)', backendTestsRerun: false, npmDependenciesAdded: false, realMobileDeviceTested: false },
  sourceSha256: Object.fromEntries(sources.sort().map(file => [file.replaceAll('\\', '/'), createHash('sha256').update(fs.readFileSync(file)).digest('hex')])),
}
fs.writeFileSync('docs/testing/ui-results.json', JSON.stringify(report, null, 2) + '\n')
console.log(JSON.stringify({ unitPassed: report.frontend.unitPassed, browserPassed: report.frontend.browserPassed, health: health.status, documentLinks: linksChecked }))
