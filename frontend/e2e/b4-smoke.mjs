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

function row(code) {
  return page.locator('.admin-product-row', { hasText: code })
}

async function stock(code) {
  return Number((await row(code).locator('td').nth(3).innerText()).trim())
}

async function batch(text) {
  await page.locator('.batch-button').click()
  const dialog = page.locator('.el-dialog').filter({ hasText: '批量库存调整' })
  await dialog.waitFor()
  await dialog.locator('textarea').fill(text)
  await dialog.getByRole('button', { name: '提交批次' }).click()
  await dialog.waitFor({ state: 'hidden', timeout: 10000 })
}

try {
  await page.goto('/login')
  await page.locator('input[autocomplete="username"]').fill('demo_admin')
  await page.locator('input[autocomplete="current-password"]').fill('Burger@123')
  await page.getByRole('button', { name: '登录', exact: true }).click()
  await page.waitForURL((url) => url.pathname === '/admin', { timeout: 15000 })
  await page.goto('/admin/catalog')
  await page.locator('.admin-product-row').first().waitFor()

  const classicBefore = await stock('S1-CLASSIC')
  const cheeseBefore = await stock('S1-CHEESE')

  await batch('1001,1,E2E批次补货\n1002,1,E2E批次补货')
  await page.waitForFunction(
    (expected) => {
      const rows = Array.from(document.querySelectorAll('.admin-product-row'))
      const classic = rows.find((r) => r.textContent.includes('S1-CLASSIC'))
      return classic && Number(classic.querySelectorAll('td')[3].textContent) === expected
    },
    classicBefore + 1,
    { timeout: 10000 },
  )
  assert.equal(await stock('S1-CLASSIC'), classicBefore + 1)
  assert.equal(await stock('S1-CHEESE'), cheeseBefore + 1)
  await page.locator('.audit-list').waitFor()
  assert.match(await page.locator('.audit-list').innerText(), /STOCK_BATCH/)
  await page.waitForTimeout(300)
  await page.screenshot({
    path: path.join(evidenceDir, 'stage-b4-stock-batch.png'),
    fullPage: true,
  })

  await batch('1001,-1,E2E批次恢复\n1002,-1,E2E批次恢复')
  await page.waitForFunction(
    (expected) => {
      const rows = Array.from(document.querySelectorAll('.admin-product-row'))
      const classic = rows.find((r) => r.textContent.includes('S1-CLASSIC'))
      return classic && Number(classic.querySelectorAll('td')[3].textContent) === expected
    },
    classicBefore,
    { timeout: 10000 },
  )
  assert.equal(await stock('S1-CHEESE'), cheeseBefore)

  assert.deepEqual(pageErrors, [])
  console.log(`B4 browser smoke passed: batch +1 -> ${classicBefore + 1} -> ${classicBefore}, audit`)
} finally {
  await browser.close()
}
