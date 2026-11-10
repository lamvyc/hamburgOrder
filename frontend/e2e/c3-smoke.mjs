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
  await context.request.get('/api/stores/1/menu')
  await context.request.get('/api/stores/1/menu')
  await page.goto('/system')
  await page.locator('.observability-section').waitFor({ timeout: 15000 })
  await page.locator('.cache-metric-hit').waitFor()
  assert.match(await page.locator('.cache-metric-hit').innerText(), /HIT/)

  const prometheus = await context.request.get('/actuator/prometheus')
  assert.equal(prometheus.status(), 200)
  const metrics = await prometheus.text()
  assert.match(metrics, /burger_menu_cache_requests_total/)
  assert.match(metrics, /http_server_requests_seconds_count/)

  await page.screenshot({
    path: path.join(evidenceDir, 'stage-c3-observability.png'),
    fullPage: true,
  })

  assert.deepEqual(pageErrors, [])
  console.log('C3 browser smoke passed: cache panel + Prometheus metrics')
} finally {
  await browser.close()
}
