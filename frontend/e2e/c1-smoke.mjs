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
  const first = await context.request.get('/api/stores/1/menu')
  const second = await context.request.get('/api/stores/1/menu')
  assert.equal(first.status(), 200)
  assert.equal(second.status(), 200)
  assert.equal(first.headers()['x-cache'], 'MISS')
  assert.equal(second.headers()['x-cache'], 'HIT')
  assert.deepEqual(await first.json(), await second.json())

  await page.goto('/stores/1')
  await page.locator('.product-row').first().waitFor()
  await page.screenshot({
    path: path.join(evidenceDir, 'stage-c1-menu-cache.png'),
    fullPage: true,
  })

  assert.deepEqual(pageErrors, [])
  console.log('C1 browser smoke passed: menu X-Cache MISS -> HIT')
} finally {
  await browser.close()
}
