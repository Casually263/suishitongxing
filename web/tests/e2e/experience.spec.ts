import { test, expect, type Page, type BrowserContext } from '@playwright/test'

const password = 'Seasonal-UI-test-password-2026'
async function enter(page: Page, context: BrowserContext) {
  const username = `ui_${Date.now()}_${Math.random().toString(36).slice(2, 6)}`
  const registered = await context.request.post('/api/portal/auth/register', { data: { username, email: `${username}@example.test`, password, cultureRole: 'GUIDE', adultConfirmed: true, acceptedTerms: true, termsVersion: '2026-10' } })
  expect(registered.status()).toBe(201)
  await page.goto('/login')
  await page.locator('input[name="identifier"]').fill(username)
  await page.locator('input[name="password"]').fill(password)
  await page.locator('button[type="submit"]').click()
  await expect(page).toHaveURL(/\/$/)
  await expect(page.getByRole('link', { name: '展开这一章' })).toBeVisible()
}
async function fits(page: Page, width: number) {
  expect(await page.evaluate(() => document.documentElement.scrollWidth)).toBeLessThanOrEqual(width)
}

test('24-term wheel supports keyboard exploration and all four original seasonal themes', async ({ page, context }) => {
  const faults: string[] = []; page.on('pageerror', error => faults.push(error.message))
  await page.setViewportSize({ width: 1440, height: 1050 })
  await enter(page, context)
  const wheel = page.getByTestId('solar-wheel')
  await expect(wheel.getByRole('button')).toHaveCount(24)
  await wheel.locator('[data-term-index="0"]').focus()
  await page.keyboard.press('ArrowRight')
  await expect(wheel.locator('[data-term-index="1"]')).toBeFocused()
  await expect(wheel.locator('[data-term-index="1"]')).toHaveAttribute('aria-pressed', 'true')
  await expect(page.locator('.term-preview')).toContainText('篇章筹备中')
  for (const [label, theme, index] of [['春生', 'spring', 0], ['夏长', 'summer', 9], ['秋收', 'autumn', 12], ['冬藏', 'winter', 21]] as const) {
    await page.locator('.season-tabs').getByRole('button', { name: label }).click()
    await expect(page.locator('.experience-home')).toHaveAttribute('data-season', theme)
    await expect(wheel.locator(`[data-term-index="${index}"]`)).toHaveAttribute('aria-pressed', 'true')
    await fits(page, 1440)
    await page.screenshot({ path: `../docs/testing/ui-home-${theme}.png`, fullPage: true, animations: 'disabled' })
  }
  await page.locator('select[name=solarTerm]').selectOption('21')
  await expect(wheel.locator('[data-term-index="21"]')).toHaveAttribute('aria-pressed', 'true')
  await expect(page.locator('.term-preview')).toContainText('Winter Solstice')
  await page.getByRole('button', { name: '去看看立春篇章' }).click()
  await expect(page.getByRole('link', { name: '展开这一章' })).toHaveAttribute('href', '/stories/1')
  await expect(page.locator('.calendar-date-note')).toContainText('年度日期与倒计时尚未开放')
  await page.setViewportSize({ width: 390, height: 844 })
  await fits(page, 390)
  await page.screenshot({ path: '../docs/testing/ui-home-mobile.png', fullPage: true, animations: 'disabled' })
  expect(faults).toEqual([])
})

test('English portal, empty collections and reader stay usable at 360px and 768px', async ({ page, context }) => {
  const faults: string[] = []; page.on('pageerror', error => faults.push(error.message))
  await enter(page, context)
  await page.getByRole('button', { name: 'Switch language' }).click()
  await page.setViewportSize({ width: 360, height: 800 })
  for (const [path, heading] of [['/', 'Find a story'], ['/collaboration', 'Two perspectives. One spring.'], ['/gifts', 'A little of your world, in return'], ['/passport', 'Keep a season. Keep a memory.'], ['/stories/1', 'Lichun · A letter of spring']] as const) {
    await page.goto(path)
    await expect(page.getByRole('heading', { name: new RegExp(heading.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')) }).first()).toBeVisible()
    await expect(page.locator('[aria-busy="true"]')).toHaveCount(0)
    await fits(page, 360)
    if (path === '/passport') {
      await expect(page.getByTestId('stamp-count')).toHaveText('00')
      await expect(page.locator('.stamp-cell.earned')).toHaveCount(0)
    }
    await page.screenshot({ path: `../docs/testing/ui-en-${path === '/' ? 'home' : path.replaceAll('/', '-')}-mobile.png`, fullPage: true, animations: 'disabled' })
    await page.setViewportSize({ width: 768, height: 1024 })
    await fits(page, 768)
    await page.setViewportSize({ width: 360, height: 800 })
  }
  await page.setViewportSize({ width: 1440, height: 1000 })
  await page.screenshot({ path: '../docs/testing/ui-reader-start-desktop.png', fullPage: true, animations: 'disabled' })
  await page.setViewportSize({ width: 360, height: 800 })
  await page.getByRole('button', { name: 'Start with scripted Xiaoman' }).click()
  await expect(page.getByTestId('story-node-title')).toBeVisible()
  await page.getByRole('button', { name: 'Toggle larger reading text' }).click()
  await expect(page.locator('.experience-reader')).toHaveClass(/large-reading/)
  await page.getByRole('checkbox', { name: 'Bilingual view' }).uncheck()
  await expect(page.locator('.story-reader>.parallel-text')).toHaveCount(0)
  await fits(page, 360)
  expect(faults).toEqual([])
})

test('real requests show loading, network failure and recovery without inventing stamp records', async ({ page, context }) => {
  await enter(page, context)
  let release: () => void = () => {}
  const gate = new Promise<void>(resolve => { release = resolve })
  await page.route('**/api/portal/stories/rewards', async route => { await gate; await route.continue() })
  try {
    await page.goto('/passport')
    await expect(page.getByRole('status')).toContainText('正在展开')
    await expect(page.locator('.stamp-wall')).toHaveCount(0)
  } finally { release() }
  await expect(page.getByTestId('stamp-count')).toHaveText('00')
  await page.unroute('**/api/portal/stories/rewards')
  await page.route('**/api/portal/stories/rewards', route => route.abort('connectionfailed'))
  await page.goto('/passport')
  await expect(page.getByRole('alert')).toContainText('无法读取印章记录')
  await expect(page.locator('.stamp-wall')).toHaveCount(0)
  await page.screenshot({ path: '../docs/testing/ui-network-error.png', fullPage: true, animations: 'disabled' })
  await page.unroute('**/api/portal/stories/rewards')
  await page.getByRole('button', { name: '重新加载' }).click()
  await expect(page.getByTestId('stamp-count')).toHaveText('00')
  await expect(page.getByRole('alert')).toHaveCount(0)
})
