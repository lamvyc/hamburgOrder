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

function stockCell() {
  return page.locator('.admin-product-row', { hasText: 'S1-CLASSIC' }).locator('td').nth(3)
}

async function adjustStock(delta, reason) {
  await page.locator('.admin-product-row', { hasText: 'S1-CLASSIC' })
    .locator('.stock-button').click()
  const dialog = page.locator('.el-dialog').filter({ hasText: '调整可售库存' })
  await dialog.waitFor()
  await dialog.getByPlaceholder('例如 5 或 -3').fill(String(delta))
  await dialog.getByPlaceholder('例如 到货补货 / 盘点损耗').fill(reason)
  await dialog.getByRole('button', { name: '保存调整' }).click()
  await dialog.waitFor({ state: 'hidden', timeout: 10000 })
}

try {
  await page.goto('/login')
  await page.locator('input[autocomplete="username"]').fill('demo_admin')
  await page.locator('input[autocomplete="current-password"]').fill('Burger@123')
  await page.getByRole('button', { name: '登录', exact: true }).click()
  await page.waitForURL((url) => url.pathname === '/admin', { timeout: 15000 })
  await page.locator('.admin-card').waitFor()
  await page.locator('.console-link').click()
  await page.waitForURL((url) => url.pathname === '/admin/catalog', { timeout: 10000 })
  await page.locator('.admin-product-row').first().waitFor()

  const before = Number((await stockCell().innerText()).trim())
  assert.ok(Number.isInteger(before))

  await adjustStock(1, 'E2E 补货')
  await page.locator('.admin-product-row', { hasText: 'S1-CLASSIC' }).waitFor()
  const afterPlus = Number((await stockCell().innerText()).trim())
  assert.equal(afterPlus, before + 1)
  await page.locator('.audit-list').waitFor()
  assert.match(await page.locator('.audit-list').innerText(), /STOCK_ADJUST/)
  assert.match(await page.locator('.audit-row').first().innerText(), /E2E 补货/)

  await page.screenshot({
    path: path.join(evidenceDir, 'stage-b2-admin-catalog.png'),
    fullPage: true,
  })

  await adjustStock(-1, 'E2E 恢复')
  const restored = Number((await stockCell().innerText()).trim())
  assert.equal(restored, before)

  assert.deepEqual(pageErrors, [])
  console.log(`B2 browser smoke passed: admin stock ${before} -> ${afterPlus} -> ${restored} with audit`)
} finally {
  await browser.close()
}
