import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { fetchSystemStatus } from '@/api/system'
import { useSystemStatusStore } from './systemStatus'
import type { SystemStatusResponse } from '@/types/system'

vi.mock('@/api/system', () => ({
  fetchSystemStatus: vi.fn(),
}))

const healthySnapshot: SystemStatusResponse = {
  status: 'UP',
  checkedAt: '2026-11-09T10:00:00Z',
  traceId: 'trace-healthy-01',
  application: {
    status: 'UP',
    name: 'burger-order-api',
    version: '0.1.0',
    javaVersion: '21.0.12',
    uptimeSeconds: 42,
  },
  database: {
    status: 'UP',
    connected: true,
    product: 'MySQL',
    version: '8.4.13',
    latencyMs: 5,
    schemaReady: true,
    flywayVersion: '1',
    lastMigration: 'create system metadata',
    code: null,
    message: null,
  },
}

describe('system status store', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.mocked(fetchSystemStatus).mockReset()
  })

  it('stores a healthy snapshot and marks the backend reachable', async () => {
    vi.mocked(fetchSystemStatus).mockResolvedValue(healthySnapshot)
    const store = useSystemStatusStore()

    await store.refresh()

    expect(store.snapshot).toEqual(healthySnapshot)
    expect(store.backendReachable).toBe(true)
    expect(store.isDatabaseReady).toBe(true)
    expect(store.error).toBeNull()
    expect(store.lastSuccessAt).toBe(healthySnapshot.checkedAt)
    expect(store.loading).toBe(false)
  })

  it('does not label a degraded 503 snapshot as a successful check', async () => {
    const degradedSnapshot: SystemStatusResponse = {
      ...healthySnapshot,
      status: 'DEGRADED',
      traceId: 'trace-degraded-01',
      database: {
        ...healthySnapshot.database,
        status: 'DOWN',
        connected: false,
        product: null,
        version: null,
        latencyMs: 3005,
        schemaReady: false,
        flywayVersion: null,
        lastMigration: null,
        code: 'DATABASE_UNAVAILABLE',
        message: '无法连接数据库',
      },
    }
    vi.mocked(fetchSystemStatus)
      .mockResolvedValueOnce(healthySnapshot)
      .mockResolvedValueOnce(degradedSnapshot)
    const store = useSystemStatusStore()

    await store.refresh()
    await store.refresh()

    expect(store.backendReachable).toBe(true)
    expect(store.snapshot?.status).toBe('DEGRADED')
    expect(store.isDatabaseReady).toBe(false)
    expect(store.lastSuccessAt).toBe(healthySnapshot.checkedAt)
  })

  it('treats a network failure as an unreachable backend, not a database failure', async () => {
    vi.mocked(fetchSystemStatus).mockRejectedValue({
      isAxiosError: true,
      code: 'ERR_NETWORK',
      message: 'Network Error',
    })
    const store = useSystemStatusStore()

    await store.refresh()

    expect(store.backendReachable).toBe(false)
    expect(store.snapshot).toBeNull()
    expect(store.error?.code).toBe('BACKEND_UNREACHABLE')
    expect(store.isDatabaseReady).toBe(false)
  })

  it('keeps the backend reachable when an unexpected HTTP error returns a trace id', async () => {
    vi.mocked(fetchSystemStatus).mockRejectedValue({
      isAxiosError: true,
      response: {
        status: 500,
        headers: { 'x-trace-id': 'trace-server-500' },
        data: {
          code: 'INTERNAL_ERROR',
          message: '服务暂时不可用，请稍后重试',
          traceId: 'trace-server-500',
        },
      },
    })
    const store = useSystemStatusStore()

    await store.refresh()

    expect(store.backendReachable).toBe(true)
    expect(store.snapshot).toBeNull()
    expect(store.error).toEqual({
      code: 'INTERNAL_ERROR',
      message: '服务暂时不可用，请稍后重试',
      traceId: 'trace-server-500',
    })
  })
})
