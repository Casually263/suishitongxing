import { readFileSync, writeFileSync, readdirSync, existsSync, statSync } from 'node:fs'
import { resolve, dirname, extname } from 'node:path'
import { createHash } from 'node:crypto'
import { fileURLToPath } from 'node:url'

const root = fileURLToPath(new URL('../', import.meta.url))
const file = path => resolve(root, path), read = path => readFileSync(file(path), 'utf8')
const hash = value => createHash('sha256').update(value).digest('hex')
function assert(ok, message) { if (!ok) throw new Error(message) }
function walk(path) { return readdirSync(file(path), { withFileTypes: true }).flatMap(entry => entry.isDirectory() ? walk(`${path}/${entry.name}`) : [`${path}/${entry.name}`]) }
function suite(path) {
  const header = read(path).match(/<testsuite\b[^>]+>/)?.[0]
  assert(header, `Missing JUnit result: ${path}`)
  const attribute = name => Number(header.match(new RegExp(`\\b${name}="([0-9]+)"`))?.[1] || 0)
  const result = { path, tests: attribute('tests'), failures: attribute('failures'), errors: attribute('errors'), skipped: attribute('skipped') }
  assert(result.tests && !result.failures && !result.errors && !result.skipped, `JUnit did not fully pass: ${path}`)
  return result
}
const unit = ['ai.AiSafetyTest', 'ai.OpenAiCompatibleProviderTest', 'security.CryptoSecurityTest', 'story.StoryGraphTest'].map(name => suite(`backend/target/surefire-reports/TEST-com.suishi.${name}.xml`))
const integration = ['ai.AiIT', 'story.StoryIT', 'system.IdentityIT'].map(name => suite(`backend/target/failsafe-reports/TEST-com.suishi.${name}.xml`))
const backendTotal = [...unit, ...integration].reduce((count, result) => count + result.tests, 0)
assert(backendTotal === 53, 'Expected 53 backend tests')
const webUnit = JSON.parse(read('docs/testing/ai-web-unit.json'))
assert(webUnit.success && webUnit.numPassedTests === 14 && webUnit.numPendingTests === 0, 'Expected 14 passing web tests')
const browser = JSON.parse(read('web/test-results/results.json'))
assert(browser.stats.expected === 7 && !browser.stats.unexpected && !browser.stats.skipped && !browser.stats.flaky, 'Expected 7 passing browser tests without skips/flakes')
assert(read('.runtime/ai-tests.log').includes('BUILD SUCCESS'), 'Missing Maven verification evidence')
assert(read('.runtime/ai-web-build.log').includes('built in'), 'Missing Vite build evidence')
const source = ['backend/src', 'web/src', 'web/tests', 'scripts'].flatMap(walk).filter(path => /\.(java|sql|ya?ml|ts|vue|css|ps1|mjs)$/.test(path)).sort()
const jarTime = statSync(file('backend/target/suishi-backend-0.1.0.jar')).mtimeMs
assert(source.filter(path => path.startsWith('backend/src/main/')).every(path => statSync(file(path)).mtimeMs <= jarTime), 'Backend JAR is older than source')
const sourceHash = hash(source.map(path => path + '\0' + hash(readFileSync(file(path)))).join('\n'))
const originalDocumentSha256 = hash(readFileSync(file('岁时同行_系统设计文档.docx')))
assert(originalDocumentSha256 === '5e2d775e58b58d9f7f1a1c799b79daaf2ad3cf4681b1e939feb9c82d8fde3aa6', 'Original design document changed')
const markdown = ['README.md', 'TASKS.md', ...walk('docs').filter(path => extname(path) === '.md')]
let links = 0
for (const path of markdown) for (const match of read(path).matchAll(/\]\(([^)]+)\)/g)) {
  const target = match[1].replace(/^<|>$/g, '').split('#')[0]
  if (!target || /^[a-z]+:\/\//i.test(target) || target.startsWith('mailto:')) continue
  assert(existsSync(resolve(dirname(file(path)), target)), `Broken local link in ${path}: ${target}`); links++
}
const secrets = read('.env.local').split(/\r?\n/).filter(line => /^(DB_PASSWORD|REDIS_PASSWORD|JWT_SECRET_BASE64|EMAIL_ENCRYPTION_KEY_BASE64|EMAIL_HMAC_KEY_BASE64|ADMIN_PASSWORD|DEMO_PASSWORD|LOCAL_MYSQL_ROOT_PASSWORD|AI_API_KEY)=/.test(line)).map(line => line.slice(line.indexOf('=') + 1)).filter(value => value.length >= 12)
const publicFiles = [...source, ...markdown, '.env.example', ...walk('web/dist').filter(path => /\.(js|css|html)$/.test(path))]
for (const path of publicFiles) assert(secrets.every(secret => !read(path).includes(secret)), `Local credential found in ${path}`)
const fixtureKey = 'fixture-only-secret-key-2026'
for (const path of walk('web/dist').filter(path => /\.(js|css|html)$/.test(path))) assert(!read(path).includes(fixtureKey), 'Server fixture credential entered frontend bundle')
const contextSource = read('backend/src/main/java/com/suishi/ai/mapper/AiContextMapper.java')
for (const sql of contextSource.matchAll(/@Select\("""([\s\S]*?)"""\)/g)) assert(!/n\.config|answer_a|answer_b|p\.history|user_profile|story_submission|story_reward/i.test(sql[1]), 'AI context query exceeded its projection')
const health = await (await fetch('http://127.0.0.1:8080/actuator/health')).json()
const meta = await (await fetch('http://127.0.0.1:8080/api/common/meta')).json()
const openapi = await (await fetch('http://127.0.0.1:8080/v3/api-docs')).json()
assert(health.status === 'UP' && meta.data.features.aiCompanion, 'AI runtime not ready')
const aiPaths = Object.keys(openapi.paths).filter(path => /\/ai\//.test(path))
assert(aiPaths.length === 8, 'Expected eight AI OpenAPI paths')
const report = {
  generatedAt: new Date().toISOString(), sourceHash, originalDocumentSha256,
  backend: { passed: backendTotal, unit, integration, database: 'MySQL 8.0.43 / isolated suishi_it', redis: 'Memurai Redis-compatible 8.2; not Redis7 certification' },
  frontend: { unitPassed: webUnit.numPassedTests, build: 'passed', browser: browser.stats },
  security: { localCredentialLeakFound: false, sourceProjectionChecked: true, protectedPayloadsTested: true, redirectsTested: true, reviewedKnowledgeHashTested: true, immutableStoryStateTested: true, markdownLinksChecked: links },
  runtime: { health: health.status, aiEnabled: true, openapiPaths: aiPaths },
  modelVerification: 'Local real HTTP/SSE compatible fixtures and no-key browser demo only; no paid or third-party live model called',
  screenshots: ['ai-no-key-desktop.png', 'ai-no-key-mobile.png', 'ai-admin.png'],
  limitations: ['Semantic fact validation still needs human review/evaluation', 'No production Docker/Redis7 verification', 'General partner switching and mini-program AI transport remain pending'],
}
writeFileSync(file('docs/testing/ai-results.json'), JSON.stringify(report, null, 2) + '\n')
console.log(JSON.stringify({ backendPassed: backendTotal, webUnitPassed: webUnit.numPassedTests, browserPassed: browser.stats.expected, linksChecked: links, health: health.status, livePaidModelCalled: false }))
