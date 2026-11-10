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

try {
  await page.goto('/login')
  await page.locator('input[autocomplete="username"]').fill('demo_admin')
  await page.locator('input[autocomplete="current-password"]').fill('Burger@123')
  await page.getByRole('button', { name: '登录', exact: true }).click()
  await page.waitForURL((url) => url.pathname === '/admin', { timeout: 15000 })
  await page.goto('/admin/catalog')
  await page.locator('.admin-product-row').first().waitFor()

  assert.equal(await page.locator('.admin-product-row').count(), 5)
  await page.locator('.load-more').waitFor()
  await page.waitForFunction(() => Array.from(
    document.querySelectorAll('.admin-product-row .el-tag'),
  ).every((tag) => tag.getBoundingClientRect().width > 20))
  await page.screenshot({
    path: path.join(evidenceDir, 'stage-b3-admin-pagination.png'),
    fullPage: true,
  })

  await page.locator('.load-more').click()
  await page.waitForFunction(
    () => document.querySelectorAll('.admin-product-row').length === 7,
    undefined,
    { timeout: 10000 },
  )
  assert.equal(await page.locator('.load-more').count(), 0)

  await page.locator('.catalog-tools .el-switch').click()
  await page.waitForFunction(
    () => document.querySelectorAll('.admin-product-row').length === 5,
    undefined,
    { timeout: 10000 },
  )
  await page.locator('.load-more').click()
  await page.waitForFunction(
    () => document.querySelectorAll('.admin-product-row').length === 6,
    undefined,
    { timeout: 10000 },
  )
  assert.equal(await page.locator('.admin-product-row .el-tag', { hasText: '下架' }).count(), 0)

  assert.deepEqual(pageErrors, [])
  console.log('B3 browser smoke passed: keyset page 5 -> 7, on-sale filter -> 6')
} finally {
  await browser.close()
}
