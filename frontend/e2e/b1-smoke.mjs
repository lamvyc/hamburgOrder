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
  const classic = page.locator('.product-row', { hasText: '经典牛肉堡' })
  await classic.locator('.add-button').click()
  await classic.locator('.add-button').click()
  await page.locator('.cart-count').filter({ hasText: '2' }).waitFor()
  await page.goto('/cart')
  await page.locator('.coupon-section').waitFor()

  const welcome = page.locator('.coupon-template', { hasText: 'WELCOME5' })
  await welcome.waitFor()
  const claim = welcome.getByRole('button', { name: /领取|已领取/ })
  if ((await claim.innerText()).trim() === '领取') {
    await claim.click()
    await page.locator('.coupon-template', { hasText: 'WELCOME5' })
      .getByRole('button', { name: '已领取' }).waitFor({ timeout: 10000 })
  }

  const choice = page.locator('.coupon-choice', { hasText: 'WELCOME5' })
  await choice.waitFor({ timeout: 10000 })
  if (!(await choice.getAttribute('class') ?? '').includes('active')) {
    await choice.click()
  }
  await page.locator('.coupon-choice.active', { hasText: 'WELCOME5' }).waitFor()
  assert.match(await page.locator('.summary-total').innerText(), /59\.00/)
  await page.evaluate(() => window.scrollTo(0, 0))
  await page.waitForTimeout(150)
  await page.screenshot({
    path: path.join(evidenceDir, 'stage-b1-coupon-cart.png'),
    fullPage: true,
  })

  await page.getByRole('button', { name: '提交订单', exact: true }).click()
  await page.waitForURL((url) => /^\/orders\/\d+$/.test(url.pathname), { timeout: 20000 })
  await page.locator('.order-no').waitFor()
  assert.match(await page.locator('.total-card').innerText(), /59\.00/)
  assert.match(await page.locator('.summary').innerText(), /优惠券.*5\.00/)
  await page.screenshot({
    path: path.join(evidenceDir, 'stage-b1-coupon-order.png'),
    fullPage: true,
  })

  // Release the coupon so this smoke scenario is repeatable against the dev DB.
  await page.getByRole('button', { name: '取消订单', exact: true }).click()
  await page.getByRole('button', { name: '确认取消', exact: true }).click()
  await page.locator('.el-overlay').waitFor({ state: 'hidden', timeout: 10000 })
  await page.locator('.hero-tags .el-tag', { hasText: '已取消' }).waitFor({ timeout: 10000 })

  assert.deepEqual(pageErrors, [])
  console.log('B1 browser smoke passed: claim coupon -> select -> order with 5.00 discount -> release')
} finally {
  await browser.close()
}
