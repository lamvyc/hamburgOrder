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

async function login(username, expectedPath) {
  await page.goto('/login')
  await page.locator('input[autocomplete="username"]').fill(username)
  await page.locator('input[autocomplete="current-password"]').fill('Burger@123')
  await page.getByRole('button', { name: '登录', exact: true }).click()
  await page.waitForURL((url) => url.pathname === expectedPath, { timeout: 15000 })
  await page.locator('.user-chip').waitFor({ timeout: 10000 })
}

async function logout() {
  await page.locator('.logout-button').click()
  await page.waitForURL((url) => url.pathname === '/', { timeout: 10000 })
  await page.locator('.login-link').waitFor({ timeout: 10000 })
}

try {
  await login('demo_customer', '/')
  await page.goto('/stores/1')
  await page.locator('.product-row').first().waitFor()
  await page.locator('.product-row', { hasText: '经典牛肉堡' }).locator('.add-button').click()
  await page.locator('.cart-count').waitFor({ timeout: 10000 })
  await page.goto('/cart')
  await page.getByRole('button', { name: '提交订单', exact: true }).click()
  await page.waitForURL((url) => /^\/orders\/\d+$/.test(url.pathname), { timeout: 20000 })
  await page.locator('.order-no').waitFor({ timeout: 10000 })

  const orderUrl = page.url()
  const orderNo = (await page.locator('.order-no').innerText()).trim()

  await page.getByRole('button', { name: '模拟支付', exact: true }).click()
  await page.getByRole('button', { name: '确认支付', exact: true }).click()
  await page.locator('.el-overlay').waitFor({ state: 'hidden', timeout: 10000 })
  await page.locator('.hero-tags .el-tag', { hasText: '等待制作' }).waitFor({ timeout: 10000 })
  await page.locator('.timeline-item').nth(1).waitFor({ timeout: 10000 })
  await page.waitForTimeout(250)
  await page.screenshot({
    path: path.join(evidenceDir, 'stage-a4-paid-order.png'),
    fullPage: true,
  })

  await logout()
  await login('demo_staff', '/staff')
  await page.locator('.work-order').first().waitFor({ timeout: 10000 })
  const orderCard = page.locator('.work-order', { hasText: orderNo })
  await orderCard.waitFor({ timeout: 10000 })
  assert.match(await orderCard.innerText(), /等待制作/)
  await page.screenshot({
    path: path.join(evidenceDir, 'stage-a4-staff-workbench.png'),
    fullPage: true,
  })

  await orderCard.locator('.action-button').click()
  await page.locator('.work-order', { hasText: orderNo }).locator('.el-tag', { hasText: '制作中' }).waitFor({ timeout: 10000 })
  await page.locator('.work-order', { hasText: orderNo }).locator('.action-button').click()
  await page.locator('.work-order', { hasText: orderNo }).locator('.el-tag', { hasText: '待取餐' }).waitFor({ timeout: 10000 })
  await page.locator('.work-order', { hasText: orderNo }).locator('.action-button').click()
  await page.locator('.work-order', { hasText: orderNo }).waitFor({ state: 'detached', timeout: 10000 })

  await logout()
  await login('demo_customer', '/')
  await page.goto(orderUrl)
  await page.locator('.hero-tags .el-tag', { hasText: '已完成' }).waitFor({ timeout: 10000 })
  await page.locator('.timeline-item').nth(4).waitFor({ timeout: 10000 })
  assert.match(await page.locator('.timeline').innerText(), /订单完成/)
  await page.waitForTimeout(300)
  await page.screenshot({
    path: path.join(evidenceDir, 'stage-a4-completed-order.png'),
    fullPage: true,
  })

  assert.deepEqual(pageErrors, [])
  console.log(`A4 browser smoke passed: ${orderNo} paid -> staff workflow -> completed`)
} finally {
  await browser.close()
}
