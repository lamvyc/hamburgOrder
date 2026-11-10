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
  const concurrent = await Promise.all(
    Array.from({ length: 5 }, () => context.request.get('/api/stores/1/menu')),
  )
  for (const response of concurrent) {
    assert.equal(response.status(), 200)
  }

  const hit = await context.request.get('/api/stores/1/menu')
  assert.equal(hit.status(), 200)
  assert.equal(hit.headers()['x-cache'], 'HIT')

  const statsResponse = await context.request.get('/api/system/menu-cache')
  assert.equal(statsResponse.status(), 200)
  const stats = await statsResponse.json()
  assert.ok(stats.miss >= 1)
  assert.ok(stats.hit >= 1)
  assert.ok(stats.stampedeWait >= 0)

  const missingOne = await context.request.get('/api/stores/999/menu')
  const missingTwo = await context.request.get('/api/stores/999/menu')
  assert.equal(missingOne.status(), 404)
  assert.equal(missingTwo.status(), 404)
  const afterMissing = await (await context.request.get('/api/system/menu-cache')).json()
  assert.ok(afterMissing.negative >= 2)

  await page.goto('/stores/1')
  await page.locator('.product-row').first().waitFor()
  await page.screenshot({
    path: path.join(evidenceDir, 'stage-c2-cache-hardening.png'),
    fullPage: true,
  })

  assert.deepEqual(pageErrors, [])
  console.log(`C2 browser smoke passed: cache stats ${JSON.stringify(afterMissing)}`)
} finally {
  await browser.close()
}
