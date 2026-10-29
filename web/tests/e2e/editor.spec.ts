import { test, expect, type Page, type BrowserContext } from '@playwright/test'
import type { Draft } from '../../src/api/editor'

const password = 'Editor-browser-password-2026'
const cleanup: { id: string; headers: { Authorization: string } }[] = []
test.afterEach(async ({ request }) => {
  for (const fixture of cleanup.splice(0)) expect((await request.post('/api/admin/story/chapters/' + fixture.id + '/archive', { headers: fixture.headers, timeout: 10000 })).status()).toBe(200)
})
async function admin(page: Page) {
  const secret = process.env.ADMIN_PASSWORD
  test.skip(!secret, 'Load the local administrator environment before running editor integration tests.')
  await page.goto('/admin/login')
  await page.locator('input[name=identifier]').fill(process.env.ADMIN_USERNAME || 'demo_admin')
  await page.locator('input[name=password]').fill(secret!)
  const login = page.waitForResponse(response => response.url().endsWith('/api/admin/auth/login') && response.request().method() === 'POST')
  await page.locator('button[type=submit]').click()
  const token = (await (await login).json()).data.accessToken as string
  await expect(page).toHaveURL(/\/admin$/)
  await page.goto('/admin/stories')
  await expect(page.locator('.vue-flow__node')).toHaveCount(14)
  return { Authorization: `Bearer ${token}` }
}
async function create(page: Page, suffix: string) {
  await page.getByRole('button', { name: '新建篇章' }).click()
  await page.locator('input[name=chapterCode]').fill(`ED_${Date.now()}_${suffix}`)
  await page.locator('input[name=chapterTitleZh]').fill('编辑器自动联调 · 独立篇章')
  await page.locator('input[name=chapterTitleEn]').fill('Automated editor check · Independent chapter')
  await page.locator('textarea[name=chapterIntroZh]').fill('用于真实发布联调的测试内容，结束后归档。')
  await page.locator('textarea[name=chapterIntroEn]').fill('A real publication fixture, archived after the check.')
  const created = page.waitForResponse(response => response.url().endsWith('/api/admin/story/chapters') && response.request().method() === 'POST')
  await page.getByRole('button', { name: '创建独立草稿' }).click()
  const draft = (await (await created).json()).data as Draft
  await expect(page).toHaveURL(new RegExp(`chapter=${draft.chapterId}`))
  await expect(page.locator('.vue-flow__node')).toHaveCount(14)
  return draft
}
async function publish(page: Page) {
  await page.getByRole('button', { name: '审校并发布', exact: true }).click()
  const dialog = page.getByRole('dialog', { name: '审校当前草稿并发布' })
  await dialog.getByRole('checkbox', { name: '我已核对文化内容与依据' }).check()
  await dialog.getByRole('checkbox', { name: '我已核对中英文及剧情分支' }).check()
  await dialog.locator('textarea[name=publicationNote]').fill('自动联调：复制已有来源内容并核对图规则，不代表真实专家审校；测试后归档。')
  const result = page.waitForResponse(response => response.url().endsWith('/publish') && response.request().method() === 'POST')
  await dialog.getByRole('button', { name: '确认发布新版本' }).click()
  expect((await result).status()).toBe(200)
  await expect(dialog).toBeHidden()
  return (await (await result).json()).data as { version: number; storyId: string }
}
async function portal(page: Page, context: BrowserContext, chapter: string, prefix: string) {
  const name = `editor_${prefix}_${Date.now()}_${Math.random().toString(36).slice(2, 6)}`
  const created = await context.request.post('/api/portal/auth/register', { data: { username: name, email: `${name}@example.test`, password, cultureRole: 'GUIDE', adultConfirmed: true, acceptedTerms: true, termsVersion: '2026-10' } })
  expect(created.status()).toBe(201)
  await page.goto('/login'); await page.locator('input[name=identifier]').fill(name); await page.locator('input[name=password]').fill(password); await page.locator('button[type=submit]').click(); await expect(page).toHaveURL(/\/$/)
  await page.goto(`/stories/${chapter}`)
  await expect(page.getByRole('heading', { name: '编辑器自动联调 · 独立篇章' })).toBeVisible()
  await page.getByRole('button', { name: '与小满脚本伙伴开始' }).click()
  await expect(page.getByTestId('story-node-title')).toBeVisible()
}
async function chooseNode(page: Page, title: string) { await page.locator('.editor-node-index').getByRole('button', { name: title, exact: true }).click() }
async function previewContinue(page: Page) { await page.getByRole('dialog').getByRole('button', { name: '继续预览', exact: true }).click() }

test('Vue Flow drag, real connections, saved properties, isolated preview and publication reach the live engine', async ({ page, browser, baseURL }) => {
  test.setTimeout(120000)
  const faults: string[] = []; page.on('pageerror', error => faults.push(error.message))
  await page.setViewportSize({ width: 1600, height: 1050 })
  const headers = await admin(page), draft = await create(page, 'FLOW')
  cleanup.push({id:draft.chapterId,headers})
  const oldContext = await browser.newContext({ baseURL }), freshContext = await browser.newContext({ baseURL })
  try {
    expect((await publish(page)).version).toBe(1)
    const old = await oldContext.newPage(); await portal(old,oldContext,draft.chapterId,'old')
    await expect(old.getByTestId('story-node-title')).toHaveText('一封未写完的春信')

    await chooseNode(page,'一封未写完的春信')
    await page.locator('textarea[name=titleZh]').fill('画布发布的一封新春信')
    await page.locator('textarea[name=titleEn]').fill('A spring letter published from the canvas')
    await page.locator('.node-effects input[type=number]').first().fill('2')
    const opening = page.locator('.story-canvas-node[data-node-key=courtyard] h3')
    const box = await opening.boundingBox(); expect(box).not.toBeNull()
    await page.mouse.move(box!.x + 35,box!.y + 10); await page.mouse.down(); await page.mouse.move(box!.x + 95,box!.y + 60,{steps:15}); await page.mouse.up()

    const canvas = await page.locator('.editor-canvas').boundingBox(); expect(canvas).not.toBeNull()
    await page.getByTestId('add-narrative').dragTo(page.locator('.editor-canvas'), { targetPosition: { x: 160, y: 350 } })
    await expect(page.locator('.vue-flow__node')).toHaveCount(15)
    const inserted = await page.locator('input[name=nodeKey]').inputValue()
    await page.locator('textarea[name=titleZh]').fill('从画布走进真实故事')
    await page.locator('textarea[name=titleEn]').fill('From the canvas into a real story')
    await page.locator('textarea[name=bodyZh]').fill('这是独立测试篇章中的原创联调叙述，发布后由真实引擎读取。')
    await page.locator('textarea[name=bodyEn]').fill('An original integration scene in an independent test chapter, read by the live engine after publication.')
    await chooseNode(page,'画布发布的一封新春信')
    await page.locator('.node-outgoing button').click()
    await page.locator('select[name=edgeTarget]').selectOption(inserted)
    const source = page.locator(`.story-canvas-node[data-node-key="${inserted}"] .vue-flow__handle.source`)
    const target = page.locator('.story-canvas-node[data-node-key=first_path] .vue-flow__handle.target')
    const sourceBox = await source.boundingBox(), targetBox = await target.boundingBox(); expect(sourceBox).not.toBeNull(); expect(targetBox).not.toBeNull()
    await page.mouse.move(sourceBox!.x+sourceBox!.width/2,sourceBox!.y+sourceBox!.height/2); await page.mouse.down(); await page.mouse.move(targetBox!.x+targetBox!.width/2,targetBox!.y+targetBox!.height/2,{steps:25}); await page.mouse.up()
    await chooseNode(page,'一盘菜，两半线索')
    await page.locator('.dual-config').getByLabel('基础默契', { exact:true }).fill('3')
    await page.locator('.dual-config').getByLabel('答对额外默契', { exact:true }).fill('8')
    await page.locator('.dual-config').getByLabel('答对知识', { exact:true }).fill('4')
    await page.locator('.dual-config').getByLabel('协作窗口/h', { exact:true }).fill('12')
    await page.getByRole('button', { name:'校验图结构', exact:true }).click()
    await expect(page.getByTestId('graph-validation')).toContainText('图结构校验通过')
    const saving = page.waitForResponse(response => response.url().endsWith('/draft') && response.request().method() === 'PUT')
    await page.getByRole('button', { name:'保存草稿', exact:true }).click()
    const saved = (await (await saving).json()).data as Draft
    expect(saved.graph.nodes.find(n => n.id === 'courtyard')!.position).not.toEqual(draft.graph.nodes.find(n => n.id === 'courtyard')!.position)
    expect(saved.graph.edges.some(e => e.source === inserted && e.target === 'first_path')).toBe(true)
    await page.reload()
    await expect(page.locator('.vue-flow__node')).toHaveCount(15)
    await chooseNode(page,'画布发布的一封新春信')
    await expect(page.locator('textarea[name=titleEn]')).toHaveValue('A spring letter published from the canvas')
    await page.screenshot({path:'../docs/testing/editor-desktop.png',fullPage:true,animations:'disabled'})

    await page.getByRole('button', { name:'运行预览', exact:true }).click()
    await expect(page.getByTestId('preview-title')).toHaveText('画布发布的一封新春信')
    await previewContinue(page); await expect(page.getByTestId('preview-title')).toHaveText('从画布走进真实故事'); await previewContinue(page)
    await page.getByTestId('preview-option-learn').click(); await previewContinue(page); await page.getByTestId('preview-option-solar').click()
    await expect(page.getByTestId('preview-title')).toHaveText('一盘菜，两半线索')
    await page.reload(); await expect(page.getByTestId('preview-title')).toHaveText('一盘菜，两半线索')
    await page.locator('select[name=previewOwnAnswer]').selectOption('five_pungent'); await page.locator('select[name=previewCompanionAnswer]').selectOption('renewal')
    await page.getByRole('button', { name:'提交双方预览答案', exact:true }).click()
    await expect(page.locator('.editor-preview .story-feedback')).toContainText('+11')
    await previewContinue(page); await page.locator('textarea[name=previewTextZh]').fill('仅供隔离预览的文化回礼记录'); await page.getByRole('button', { name:'提交预览文字' }).click()
    await expect(page.getByTestId('preview-title')).toHaveText('今天的一小片春天')
    await page.locator('textarea[name=previewTextEn]').fill('An isolated preview of an everyday ritual.'); await page.getByRole('button', { name:'提交预览文字' }).click()
    await page.getByTestId('preview-option-study').click(); await page.getByRole('button', { name:'预览结算（不发印章）' }).click()
    await expect(page.getByTestId('preview-completed')).toBeVisible()
    await page.screenshot({path:'../docs/testing/editor-preview.png',fullPage:true,animations:'disabled'})
    await page.locator('.el-overlay:visible .el-dialog__headerbtn').click()
    expect((await publish(page)).version).toBe(2)
    await old.reload(); await expect(old.getByTestId('story-node-title')).toHaveText('一封未写完的春信')
    const fresh = await freshContext.newPage(); await portal(fresh,freshContext,draft.chapterId,'new')
    await expect(fresh.getByTestId('story-node-title')).toHaveText('画布发布的一封新春信')
    await fresh.getByRole('button', { name:'继续故事' }).click(); await expect(fresh.getByTestId('story-node-title')).toHaveText('从画布走进真实故事')
    await fresh.reload(); await expect(fresh.getByTestId('story-node-title')).toHaveText('从画布走进真实故事')
    await fresh.screenshot({path:'../docs/testing/editor-published-player.png',fullPage:true,animations:'disabled'})

    await page.getByRole('button', { name:/版本记录/ }).click(); await expect(page.locator('.version-record')).toHaveCount(2)
    await page.screenshot({path:'../docs/testing/editor-history.png',fullPage:true,animations:'disabled'})
    await page.locator('.version-record').last().getByRole('button', { name:'复制为草稿' }).click()
    await page.getByRole('button', { name:'继续', exact:true }).click()
    await expect(page.locator('.vue-flow__node')).toHaveCount(14)
    const latest = await page.request.get(`/api/admin/story/chapters/${draft.chapterId}/versions`,{headers})
    expect((await latest.json()).data).toHaveLength(2)
    await expect(page.locator('.editor-toolbar button').first()).toBeFocused()
    await page.setViewportSize({width:390,height:844}); await page.getByRole('button',{name:'Switch language'}).click()
    await expect.poll(() => page.locator('.skip-link').evaluate(el => el.getBoundingClientRect().bottom)).toBeLessThanOrEqual(0)
    expect(await page.evaluate(() => document.documentElement.scrollWidth)).toBeLessThanOrEqual(390)
    await page.screenshot({path:'../docs/testing/editor-mobile-english.png',fullPage:true,animations:'disabled'})
    expect(faults).toEqual([])
  } finally {
    await oldContext.close(); await freshContext.close()
  }
})

test('editor reports invalid branches, stale saves and real network errors without discarding local edits', async ({ page }) => {
  test.setTimeout(60000)
  const headers = await admin(page), draft = await create(page,'ERROR')
  cleanup.push({id:draft.chapterId,headers})
  try {
    await chooseNode(page,'一封未写完的春信')
    await page.locator('textarea[name=titleZh]').fill('本地尚未保存的标题')
    const external = JSON.parse(JSON.stringify(draft.graph)) as Draft['graph']
    external.nodes[0]!.content.title.zh = '另一个编辑者已保存的标题'
    expect((await page.request.put(`/api/admin/story/chapters/${draft.chapterId}/draft`,{headers,data:{expectedRevision:draft.revision,graph:external}})).status()).toBe(200)
    await page.getByRole('button',{name:'保存草稿',exact:true}).click()
    await expect(page.locator('.editor-notice[role=alert]')).toContainText('草稿已被修改')
    await expect(page.locator('textarea[name=titleZh]')).toHaveValue('本地尚未保存的标题')
    await page.screenshot({path:'../docs/testing/editor-conflict.png',fullPage:true,animations:'disabled'})
    await page.getByRole('button',{name:'重新加载',exact:true}).click(); await page.getByRole('button',{name:'继续',exact:true}).click()
    await expect(page.locator('textarea[name=titleZh]')).toHaveValue('另一个编辑者已保存的标题')
    await page.getByTestId('add-ending').click()
    await page.getByRole('button',{name:'校验图结构',exact:true}).click()
    await expect(page.getByTestId('graph-validation')).toContainText('发布前需要修正')
    await page.getByRole('button',{name:'删除此节点及关联连线'}).click()
    await page.getByRole('button',{name:'重新加载',exact:true}).click()
    const confirmation = page.getByRole('button',{name:'继续',exact:true}); if (await confirmation.isVisible()) await confirmation.click()
    await expect(page.locator('.vue-flow__node')).toHaveCount(14)
    const endpoint = `**/api/admin/story/chapters/${draft.chapterId}/draft`
    await page.route(endpoint,route => route.abort('connectionfailed'))
    await page.getByRole('button',{name:'重新加载',exact:true}).click()
    await expect(page.locator('.editor-notice[role=alert]')).toContainText('无法读取剧情')
    await page.unroute(endpoint)
    await page.getByRole('button',{name:'重新加载',exact:true}).click()
    await expect(page.locator('.editor-notice[role=alert]')).toHaveCount(0)
    await expect(page.locator('textarea[name=titleZh]')).toHaveValue('另一个编辑者已保存的标题')
  } finally { /* Fixture archiving is handled even after a timed-out browser action. */ }
})
