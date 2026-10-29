import fs from 'node:fs'
import path from 'node:path'
import assert from 'node:assert/strict'
import { createHash } from 'node:crypto'

function walk(directory) { return fs.readdirSync(directory,{withFileTypes:true}).flatMap(item=>item.isDirectory()?walk(path.join(directory,item.name)):[path.join(directory,item.name)]) }
function digest(file) { return createHash('sha256').update(fs.readFileSync(file)).digest('hex') }
const suites=[]
for(const directory of ['backend/target/surefire-reports','backend/target/failsafe-reports']) {
  for(const file of fs.readdirSync(directory).filter(name=>/^TEST-.*\.xml$/.test(name))) {
    const header=fs.readFileSync(path.join(directory,file),'utf8').match(/<testsuite\b[^>]*>/)?.[0]
    assert(header,`Missing JUnit suite: ${file}`)
    const attribute=name=>header.match(new RegExp(`\\b${name}="([^"]*)"`))?.[1]
    suites.push({name:attribute('name'),tests:Number(attribute('tests')),failures:Number(attribute('failures')),errors:Number(attribute('errors')),skipped:Number(attribute('skipped')),seconds:Number(attribute('time'))})
  }
}
assert(suites.reduce((n,s)=>n+s.tests,0)>=72,'Run all backend tests, including the editor')
assert(suites.every(s=>s.failures===0&&s.errors===0&&s.skipped===0),'Backend failures or skipped integration tests')
const unit=JSON.parse(fs.readFileSync('docs/testing/editor-web-unit.json','utf8'))
assert(unit.success&&unit.numPassedTests>=18&&unit.numFailedTests===0&&unit.numPendingTests===0,'Run frontend unit tests')
const browser=JSON.parse(fs.readFileSync('web/test-results/results.json','utf8'))
assert(browser.stats.expected>=12&&browser.stats.unexpected===0&&browser.stats.skipped===0&&browser.stats.flaky===0,'Run the full browser suite without skips or retries')
const audit=JSON.parse(fs.readFileSync('docs/testing/editor-npm-audit.json','utf8'))
assert.equal(audit.metadata.vulnerabilities.total,0,'Resolve npm audit findings')
const sourceFiles=walk('web/src').filter(file=>/\.(vue|ts|css)$/.test(file))
const builtAt=fs.statSync('web/dist/index.html').mtimeMs
assert(sourceFiles.every(file=>fs.statSync(file).mtimeMs<=builtAt),'Rebuild after editing website sources')
const clientFiles=[...sourceFiles,...walk('web/dist').filter(file=>/\.(js|html|css)$/.test(file))]
assert(clientFiles.every(file=>!/\bsk-[A-Za-z0-9_-]{20,}|-----BEGIN (?:RSA |EC )?PRIVATE KEY-----/.test(fs.readFileSync(file,'utf8'))),'Credential-like material in client code (limited pattern scan)')
const migration='backend/src/main/resources/db/migration/V6__story_editor.sql'
assert.equal(digest(migration),digest('backend/target/classes/db/migration/V6__story_editor.sql'),'Migration differs from the compiled resource')
const health=await(await fetch('http://127.0.0.1:8080/actuator/health',{signal:AbortSignal.timeout(5000)})).json()
assert.equal(health.status,'UP')
const web=await fetch('http://127.0.0.1:5173',{signal:AbortSignal.timeout(5000)});assert(web.ok)
const openapi=await(await fetch('http://127.0.0.1:8080/v3/api-docs',{signal:AbortSignal.timeout(10000)})).json()
const operations=Object.entries(openapi.paths).filter(([p])=>p.startsWith('/api/admin/story')).flatMap(([p,item])=>Object.keys(item).filter(key=>['get','post','put'].includes(key)).map(method=>`${method.toUpperCase()} ${p}`))
assert(operations.length>=13,'Running backend does not expose the editor endpoints')
let linksChecked=0
for(const file of ['README.md','TASKS.md','docs/story-editor.md','docs/testing/editor-validation.md']) {
  for(const [,target] of fs.readFileSync(file,'utf8').matchAll(/\]\(([^)]+)\)/g)) {
    if(/^(https?:|#)/.test(target))continue
    const resolved=path.resolve(path.dirname(file),target.split('#')[0])
    assert(resolved===path.resolve('docs/testing/editor-results.json')||fs.existsSync(resolved),`Broken local link in ${file}: ${target}`);linksChecked++
  }
}
const productionSources=walk('backend/src/main/java').filter(file=>file.endsWith('.java'))
const report={recordedAt:new Date().toISOString(),backend:{total:suites.reduce((n,s)=>n+s.tests,0),suites,migrationSha256:digest(migration),jarSha256:digest('backend/target/suishi-backend-0.1.0.jar')},frontend:{unitPassed:unit.numPassedTests,browserPassed:browser.stats.expected,browserFailed:browser.stats.unexpected,skipped:browser.stats.skipped,browserDurationMs:browser.stats.duration,productionBuildAt:new Date(builtAt).toISOString()},dependencies:{vueFlow:JSON.parse(fs.readFileSync('web/package.json','utf8')).dependencies['@vue-flow/core'],vueI18n:JSON.parse(fs.readFileSync('web/package.json','utf8')).dependencies['vue-i18n'],npmReportedVulnerabilities:audit.metadata.vulnerabilities.total,lockSha256:digest('web/package-lock.json')},runtime:{backendHealth:health.status,websiteHttpStatus:web.status,editorOperations:operations},review:{localLinksChecked:linksChecked,clientCredentialPatternScan:'passed; limited pattern scan, not a complete security audit',realMobileDevicesTested:false,paidModelCalled:false},sourceSha256:Object.fromEntries([...sourceFiles,...productionSources].sort().map(file=>[file.replaceAll('\\','/'),digest(file)]))}
fs.writeFileSync('docs/testing/editor-results.json',JSON.stringify(report,null,2)+'\n')
console.log(JSON.stringify({backendTests:report.backend.total,webUnit:unit.numPassedTests,browserFlows:browser.stats.expected,npmAuditFindings:0,editorOperations:operations.length,localLinks:linksChecked}))
