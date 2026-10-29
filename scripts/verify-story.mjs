import { readFileSync, writeFileSync, readdirSync, existsSync, statSync } from 'node:fs'
import { resolve, relative, dirname, extname } from 'node:path'
import { createHash } from 'node:crypto'
import { fileURLToPath } from 'node:url'

const root = fileURLToPath(new URL('../', import.meta.url))
const file = path => resolve(root, path)
const read = path => readFileSync(file(path), 'utf8')
const sha = value => createHash('sha256').update(value).digest('hex')
const assert = (condition, message) => { if (!condition) throw new Error(message) }
function junit(path) {
  const text = read(path), suite = text.match(/<testsuite\b[^>]+>/)?.[0]
  assert(suite, `No test suite: ${path}`)
  const number = name => Number(suite.match(new RegExp(`\\b${name}="([0-9]+)"`))?.[1] || 0)
  const result = { file: path, tests: number('tests'), failures: number('failures'), errors: number('errors'), skipped: number('skipped') }
  assert(result.tests > 0 && result.failures === 0 && result.errors === 0 && result.skipped === 0, `Tests not fully passing: ${path}`)
  return result
}
function walk(path) {
  return readdirSync(file(path), { withFileTypes: true }).flatMap(entry => {
    const child = `${path}/${entry.name}`
    return entry.isDirectory() ? walk(child) : [child]
  })
}
const backend = [
  'backend/target/surefire-reports/TEST-com.suishi.security.CryptoSecurityTest.xml',
  'backend/target/surefire-reports/TEST-com.suishi.story.StoryGraphTest.xml',
  'backend/target/failsafe-reports/TEST-com.suishi.system.IdentityIT.xml',
  'backend/target/failsafe-reports/TEST-com.suishi.story.StoryIT.xml',
].map(junit)
const unit = JSON.parse(read('docs/testing/story-web-unit.json'))
assert(unit.success && unit.numPassedTests === 8 && unit.numPendingTests === 0, 'Web unit tests failed or skipped')
const browser = JSON.parse(read('web/test-results/results.json'))
assert(browser.stats.expected === 5 && browser.stats.unexpected === 0 && browser.stats.skipped === 0 && browser.stats.flaky === 0, 'Browser tests failed, flaky or skipped')
assert(read('.runtime/story-web-build.log').includes('built in'), 'Missing Vite build evidence')
assert(read('.runtime/story-package.log').includes('BUILD SUCCESS') && read('.runtime/story-tests.log').includes('BUILD SUCCESS'), 'Missing Maven build evidence')
const originalSha = sha(readFileSync(file('岁时同行_系统设计文档.docx')))
assert(originalSha === '5e2d775e58b58d9f7f1a1c799b79daaf2ad3cf4681b1e939feb9c82d8fde3aa6', 'Source document changed')
const source = ['backend/src', 'web/src', 'web/tests', 'scripts'].flatMap(walk).filter(path => /\.(java|sql|ya?ml|ts|vue|css|ps1|mjs)$/.test(path)).sort()
const sourceHash = sha(source.map(path => path + '\0' + sha(readFileSync(file(path)))).join('\n'))
const jar = 'backend/target/suishi-backend-0.1.0.jar'
assert(source.filter(path => path.startsWith('backend/src/main/')).every(path => statSync(file(path)).mtimeMs <= statSync(file(jar)).mtimeMs), 'JAR is older than backend source')
const markdown = ['README.md', 'TASKS.md', ...walk('docs').filter(path => extname(path) === '.md')]
let linkCount = 0
for (const path of markdown) {
  for (const match of read(path).matchAll(/\]\(([^)]+)\)/g)) {
    const target = match[1].replace(/^<|>$/g, '').split('#')[0]
    if (!target || /^[a-z]+:\/\//i.test(target) || target.startsWith('mailto:')) continue
    assert(existsSync(resolve(dirname(file(path)), target)), `Missing link in ${path}: ${target}`)
    linkCount++
  }
}
const secrets = read('.env.local').split(/\r?\n/).filter(line => /^(DB_PASSWORD|REDIS_PASSWORD|JWT_SECRET_BASE64|EMAIL_ENCRYPTION_KEY_BASE64|EMAIL_HMAC_KEY_BASE64|ADMIN_PASSWORD|DEMO_PASSWORD|LOCAL_MYSQL_ROOT_PASSWORD)=/.test(line)).map(line => line.slice(line.indexOf('=') + 1)).filter(value => value.length >= 12)
for (const path of [...source, ...markdown, 'README.md', '.env.example']) assert(secrets.every(secret => !read(path).includes(secret)), `Local credential was copied into ${path}`)
const health = await (await fetch('http://127.0.0.1:8080/actuator/health')).json()
const meta = await (await fetch('http://127.0.0.1:8080/api/common/meta')).json()
assert(health.status === 'UP' && meta.data.features.stories, 'Runtime health/features not ready')
const report = {
  generatedAt: new Date().toISOString(), sourceHash, originalDocumentSha256: originalSha,
  backend: { suites: backend, total: backend.reduce((sum, suite) => sum + suite.tests, 0), failed: 0, skipped: 0, database: 'MySQL 8.0.43 / isolated suishi_it', redis: 'Memurai Redis-compatible 8.2; not Redis 7 certification' },
  web: { unitPassed: unit.numPassedTests, build: 'passed', browser: browser.stats },
  static: { filesHashed: source.length, markdownLinksChecked: linkCount, credentialLeakFound: false },
  runtime: { health: health.status, storiesEnabled: true, frontend: 'http://127.0.0.1:5173/stories/1' },
  screenshots: ['story-lichun-desktop.png', 'story-lichun-mobile.png', 'story-lichun-ending.png'],
  limitations: ['Docker/Redis7 not exercised', 'Text-only private gifts/check-ins', 'Professional cultural/bilingual sign-off pending', 'General matching, AI chat, editor and full passport not implemented'],
}
writeFileSync(file('docs/testing/story-results.json'), JSON.stringify(report, null, 2) + '\n')
console.log(JSON.stringify({ backendPassed: report.backend.total, webUnitPassed: unit.numPassedTests, browserPassed: browser.stats.expected, linksChecked: linkCount, health: health.status }))
