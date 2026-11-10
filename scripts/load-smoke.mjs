import { performance } from 'node:perf_hooks'

const baseUrl = (process.env.LOAD_BASE_URL ?? 'http://127.0.0.1:18080').replace(/\/$/, '')
const path = process.env.LOAD_PATH ?? '/api/stores/1/menu'
const requests = Number(process.env.LOAD_REQUESTS ?? 200)
const concurrency = Number(process.env.LOAD_CONCURRENCY ?? 20)
const warmup = Number(process.env.LOAD_WARMUP ?? 5)
const p95Limit = Number(process.env.LOAD_P95_LIMIT_MS ?? 1000)

if (!Number.isInteger(requests) || requests < 1 || requests > 5000) {
  throw new Error('LOAD_REQUESTS must be 1..5000')
}
if (!Number.isInteger(concurrency) || concurrency < 1 || concurrency > 200) {
  throw new Error('LOAD_CONCURRENCY must be 1..200')
}

const cacheResults = new Map()

async function once() {
  const started = performance.now()
  try {
    const response = await fetch(`${baseUrl}${path}`, {
      headers: { Accept: 'application/json' },
    })
    await response.arrayBuffer()
    const cache = response.headers.get('x-cache') ?? 'NONE'
    cacheResults.set(cache, (cacheResults.get(cache) ?? 0) + 1)
    return { ms: performance.now() - started, ok: response.ok, status: response.status }
  } catch (error) {
    return { ms: performance.now() - started, ok: false, status: 0, error: String(error) }
  }
}

for (let i = 0; i < warmup; i++) {
  await once()
}

const durations = []
let errors = 0
let next = 0
const startedAll = performance.now()

async function worker() {
  while (true) {
    const index = next++
    if (index >= requests) return
    const result = await once()
    durations.push(result.ms)
    if (!result.ok) errors++
  }
}

await Promise.all(Array.from({ length: concurrency }, () => worker()))
const elapsed = performance.now() - startedAll

durations.sort((a, b) => a - b)
const percentile = (p) => {
  if (durations.length === 0) return null
  const index = Math.min(durations.length - 1, Math.max(0, Math.ceil(p * durations.length) - 1))
  return Number(durations[index].toFixed(2))
}

const report = {
  baseUrl,
  path,
  requests,
  concurrency,
  warmup,
  errors,
  elapsedMs: Number(elapsed.toFixed(2)),
  throughputRps: Number((requests / (elapsed / 1000)).toFixed(2)),
  minMs: Number((durations[0] ?? 0).toFixed(2)),
  p50Ms: percentile(0.50),
  p95Ms: percentile(0.95),
  p99Ms: percentile(0.99),
  maxMs: Number((durations[durations.length - 1] ?? 0).toFixed(2)),
  cache: Object.fromEntries([...cacheResults.entries()].sort()),
  p95LimitMs: p95Limit,
}

console.log(JSON.stringify(report, null, 2))

if (errors > 0) {
  console.error(`load smoke failed: ${errors} errors`)
  process.exit(1)
}
if (report.p95Ms !== null && report.p95Ms > p95Limit) {
  console.error(`load smoke failed: p95 ${report.p95Ms}ms > ${p95Limit}ms`)
  process.exit(1)
}
