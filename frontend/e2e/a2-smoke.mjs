import assert from 'node:assert/strict'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { chromium } from 'playwright'

const here = path.dirname(fileURLToPath(import.meta.url))
const evidenceDir = path.resolve(here, '../../docs/evidence')
const baseURL = process.env.E2E_BASE_URL ?? 'http://127.0.0.1:5173'
const password = 'Burger@123'

const browser = await chromium.launch({ channel: 'chrome', headless: true })
const context = await browser.newContext({
  baseURL,
  viewport: { width: 1440, height: 1000 },
  locale: 'zh-CN',
})
const page = await context.newPage()
const pageErrors = []
page.on('pageerror', (error) => pageErrors.push(error.message))

async function screenshot(name, fullPage = true) {
  await page.screenshot({
    path: path.join(evidenceDir, name),
    fullPage,
  })
}

async function login(username, expectedPath) {
  await page.goto('/login')
  await page.locator('input[autocomplete="username"]').fill(username)
  await page.locator('input[autocomplete="current-password"]').fill(password)
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
  await page.goto('/login')
  await page.locator('.login-card').waitFor()
  await screenshot('stage-a2-login.png')

  await login('demo_customer', '/')
  assert.match(await page.locator('.user-chip').innerText(), /演示顾客/)
  await screenshot('stage-a2-customer-home.png')

  await page.goto('/stores/1')
  await page.locator('.product-row').first().waitFor()
  await page.locator('.add-button').first().click()
  await page.locator('.cart-count').waitFor({ timeout: 10000 })
  assert.equal(await page.locator('.cart-count').innerText(), '1')
  await screenshot('stage-a2-menu-cart.png', false)

  await page.goto('/cart')
  await page.getByText('经典牛肉堡', { exact: true }).waitFor()
  assert.match(await page.locator('.summary-total').innerText(), /¥32\.00/)
  await screenshot('stage-a2-cart.png')

  await page.goto('/stores/2')
  await page.locator('.product-row').first().waitFor()
  await page.locator('.add-button').first().click()
  const switchDialog = page.locator('.el-message-box')
  await switchDialog.waitFor({ timeout: 10000 })
  assert.match(await switchDialog.innerText(), /中央车站店/)
  await page.getByRole('button', { name: '保留原购物车' }).click()
  await switchDialog.waitFor({ state: 'hidden' })
  assert.equal(await page.locator('.cart-count').innerText(), '1')
  assert.match(await page.locator('.cart-bar-copy').innerText(), /中央车站店/)

  await page.locator('.add-button').first().click()
  await switchDialog.waitFor({ timeout: 10000 })
  await page.getByRole('button', { name: '清空并加入' }).click()
  await switchDialog.waitFor({ state: 'hidden' })
  assert.match(await page.locator('.cart-bar-copy').innerText(), /当前门店购物车/)

  await page.goto('/staff')
  await page.waitForURL((url) => url.pathname === '/', { timeout: 10000 })

  await logout()
  assert.equal(await page.locator('.cart-count').count(), 0)
  await page.waitForFunction(
    () => localStorage.getItem('burger-order.cart.v1') === null,
  )

  await login('demo_staff', '/staff')
  await page.locator('.store-tab').first().waitFor({ timeout: 10000 })
  assert.match(await page.locator('.store-tab').first().innerText(), /中央车站店/)
  await screenshot('stage-a2-staff.png')
  await page.goto('/admin')
  await page.waitForURL((url) => url.pathname === '/staff', { timeout: 10000 })

  await logout()
  await login('demo_admin', '/admin')
  await page.locator('.admin-card').waitFor({ timeout: 10000 })
  assert.match(await page.locator('.admin-card').innerText(), /管理基础入口已启用/)
  await screenshot('stage-a2-admin.png')
  await page.goto('/staff')
  await page.waitForURL((url) => url.pathname === '/admin', { timeout: 10000 })

  assert.deepEqual(pageErrors, [])
  console.log('A2 browser smoke passed: customer login + CSRF + cart + staff/admin role entries')
} finally {
  await browser.close()
}
