import assert from 'node:assert/strict'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { chromium } from 'playwright'

const here = path.dirname(fileURLToPath(import.meta.url))
const evidenceDir = path.resolve(here, '../../docs/evidence')
const baseURL = process.env.E2E_BASE_URL ?? 'http://127.0.0.1:5173'

const browser = await chromium.launch({ channel: 'chrome', headless: true })
const context = await browser.newContext({
  baseURL,
  viewport: { width: 1440, height: 1000 },
  locale: 'zh-CN',
})
const page = await context.newPage()
const pageErrors = []
page.on('pageerror', (error) => pageErrors.push(error.message))

function stock(code) {
  return page.locator('.admin-product-row', { hasText: code }).locator('td').nth(3)
}

try {
  await page.goto('/login')
  await page.locator('input[autocomplete="username"]').fill('demo_admin')
  await page.locator('input[autocomplete="current-password"]').fill('Burger@123')
  await page.getByRole('button', { name: '登录', exact: true }).click()
  await page.waitForURL((url) => url.pathname === '/admin', { timeout: 15000 })
  await page.goto('/admin/catalog')
  await page.locator('.admin-product-row').first().waitFor()
  const before = (await stock('S1-CLASSIC').innerText()).trim()

  await page.locator('.batch-button').click()
  const dialog = page.locator('.el-dialog').filter({ hasText: '批量库存调整' })
  await dialog.waitFor()
  await dialog.locator('textarea').fill(
    '1001,1,E2E预览合法\n1002,0,E2E预览零增量\n1006,-1000,E2E预览库存不足',
  )
  await dialog.getByRole('button', { name: '校验预览' }).click()
  await dialog.locator('.preview-box').waitFor({ timeout: 10000 })
  assert.match(await dialog.locator('.preview-box').innerText(), /发现 2 行错误/)
  assert.match(await dialog.locator('.preview-box').innerText(), /INVALID_STOCK_DELTA/)
  assert.match(await dialog.locator('.preview-box').innerText(), /STOCK_INSUFFICIENT/)
  await page.screenshot({
    path: path.join(evidenceDir, 'stage-b5-batch-preview.png'),
    fullPage: true,
  })

  await dialog.getByRole('button', { name: '取消' }).click()
  await dialog.waitFor({ state: 'hidden', timeout: 10000 })
  assert.equal((await stock('S1-CLASSIC').innerText()).trim(), before)

  assert.deepEqual(pageErrors, [])
  console.log('B5 browser smoke passed: preview 3 lines, 2 errors, stock unchanged')
} finally {
  await browser.close()
}
