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

async function login() {
  await page.goto('/login')
  await page.locator('input[autocomplete="username"]').fill('demo_customer')
  await page.locator('input[autocomplete="current-password"]').fill('Burger@123')
  await page.getByRole('button', { name: '登录', exact: true }).click()
  await page.waitForURL((url) => url.pathname === '/', { timeout: 15000 })
  await page.locator('.user-chip').waitFor({ timeout: 10000 })
}

try {
  await login()

  await page.goto('/stores/1')
  await page.locator('.product-row').first().waitFor({ timeout: 10000 })

  const classic = page.locator('.product-row', { hasText: '经典牛肉堡' })
  await classic.locator('.add-button').click()
  await page.locator('.cart-count').waitFor({ timeout: 10000 })
  await classic.locator('.add-button').click()
  await page.locator('.cart-count').filter({ hasText: '2' }).waitFor({ timeout: 10000 })

  const cola = page.locator('.product-row', { hasText: '冰镇可乐' })
  await cola.locator('.add-button').click()
  await page.locator('.cart-count').filter({ hasText: '3' }).waitFor({ timeout: 10000 })

  await page.goto('/cart')
  await page.getByText('经典牛肉堡', { exact: true }).waitFor()
  assert.match(await page.locator('.summary-total').innerText(), /¥74\.00/)
  await page.screenshot({
    path: path.join(evidenceDir, 'stage-a3-cart-ready.png'),
    fullPage: true,
  })

  await page.getByRole('button', { name: '提交订单', exact: true }).click()
  await page.waitForURL((url) => /^\/orders\/\d+$/.test(url.pathname), { timeout: 20000 })
  await page.locator('.order-no').waitFor({ timeout: 10000 })

  const orderUrl = page.url()
  const orderId = Number(orderUrl.split('/').pop())
  assert.ok(Number.isInteger(orderId) && orderId > 0)
  assert.match(await page.locator('.order-no').innerText(), /^BO\d{14}[A-F0-9]{6}$/)
  assert.match(await page.locator('.total-card').innerText(), /¥74\.00/)
  assert.match(await page.locator('.pay-alert').innerText(), /A4|等待支付/)
  assert.equal(await page.locator('.cart-count').count(), 0)

  await page.screenshot({
    path: path.join(evidenceDir, 'stage-a3-order-detail.png'),
    fullPage: true,
  })

  await page.reload()
  await page.locator('.order-no').waitFor({ timeout: 10000 })
  assert.match(await page.locator('.hero').innerText(), /等待支付/)

  await page.locator('.logout-button').click()
  await page.waitForURL((url) => url.pathname === '/', { timeout: 10000 })
  await page.goto(orderUrl)
  await page.waitForURL((url) => url.pathname === '/login', { timeout: 10000 })

  assert.deepEqual(pageErrors, [])
  console.log(`A3 browser smoke passed: cart -> order ${orderId} -> reload -> auth redirect`)
} finally {
  await browser.close()
}
