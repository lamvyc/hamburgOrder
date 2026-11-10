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
  await page.locator('input[autocomplete="username"]').fill('demo_customer')
  await page.locator('input[autocomplete="current-password"]').fill('Burger@123')
  await page.getByRole('button', { name: '登录', exact: true }).click()
  await page.waitForURL((url) => url.pathname === '/', { timeout: 15000 })
  await page.locator('.user-chip').waitFor()

  await page.goto('/stores/1')
  await page.locator('.product-row').first().waitFor()
  await page.locator('.product-row', { hasText: '经典牛肉堡' }).locator('.add-button').click()
  await page.locator('.cart-count').waitFor()
  await page.goto('/cart')
  await page.getByRole('button', { name: '提交订单', exact: true }).click()
  await page.waitForURL((url) => /^\/orders\/\d+$/.test(url.pathname), { timeout: 20000 })
  await page.locator('.order-no').waitFor()
  const orderUrl = page.url()
  const orderNo = (await page.locator('.order-no').innerText()).trim()

  await page.getByRole('button', { name: '取消订单', exact: true }).click()
  await page.getByRole('button', { name: '确认取消', exact: true }).click()
  await page.locator('.el-overlay').waitFor({ state: 'hidden', timeout: 10000 })
  await page.locator('.hero-tags .el-tag', { hasText: '已取消' }).waitFor({ timeout: 10000 })
  await page.locator('.timeline-item').nth(1).waitFor()
  assert.match(await page.locator('.timeline').innerText(), /订单取消/)
  assert.equal(await page.getByRole('button', { name: '模拟支付', exact: true }).count(), 0)
  await page.waitForTimeout(250)
  await page.screenshot({
    path: path.join(evidenceDir, 'stage-a5-cancelled-order.png'),
    fullPage: true,
  })

  await page.reload()
  await page.locator('.hero-tags .el-tag', { hasText: '已取消' }).waitFor({ timeout: 10000 })
  assert.match(await page.locator('.hero').innerText(), /订单已取消/)

  assert.deepEqual(pageErrors, [])
  console.log(`A5 browser smoke passed: ${orderNo} cancelled and stock release visible`)
} finally {
  await browser.close()
}
