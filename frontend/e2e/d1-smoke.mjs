import assert from 'node:assert/strict'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { chromium } from 'playwright'

const here = path.dirname(fileURLToPath(import.meta.url))
const evidenceDir = path.resolve(here, '../../docs/evidence')
const baseURL = process.env.E2E_BASE_URL ?? 'http://127.0.0.1:18081'

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
  const health = await context.request.get('/api/system/status')
  assert.equal(health.status(), 200)
  const stores = await context.request.get('/api/stores')
  assert.equal(stores.status(), 200)

  await page.goto('/login')
  await page.locator('input[autocomplete="username"]').fill('demo_customer')
  await page.locator('input[autocomplete="current-password"]').fill('Burger@123')
  await page.getByRole('button', { name: '登录', exact: true }).click()
  await page.waitForURL((url) => url.pathname === '/', { timeout: 15000 })
  await page.locator('.user-chip').waitFor()

  await page.goto('/stores/1')
  await page.locator('.product-row').first().waitFor()
  await page.screenshot({
    path: path.join(evidenceDir, 'stage-d1-container.png'),
    fullPage: true,
  })

  assert.deepEqual(pageErrors, [])
  console.log('D1 browser smoke passed: containerized frontend + nginx proxy + session login')
} finally {
  await browser.close()
}
