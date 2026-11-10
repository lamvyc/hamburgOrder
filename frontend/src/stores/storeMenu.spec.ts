import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { fetchMenu } from '@/api/catalog'
import { useStoreMenuStore } from './storeMenu'
import type { MenuResponse, Store } from '@/types/catalog'

vi.mock('@/api/catalog', () => ({
  fetchMenu: vi.fn(),
}))

const central: Store = {
  id: 1,
  code: 'CENTRAL',
  name: '中央车站店',
  address: '静安区南京西路 88 号',
  latitude: 31.2304,
  longitude: 121.4737,
  businessStatus: 'OPEN',
  acceptingOrders: true,
  orderable: true,
  openingHours: '10:00–22:00',
}

function menuFor(store: Store): MenuResponse {
  return {
    store,
    categories: [
      {
        code: 'BURGER',
        name: '汉堡',
        products: [
          {
            id: store.id * 1000 + 1,
            code: `S${store.id}-CLASSIC`,
            name: '经典牛肉堡',
            description: '手打牛肉饼',
            price: '32.00',
          },
        ],
      },
    ],
  }
}

function deferred<T>() {
  let resolve!: (value: T) => void
  let reject!: (reason: unknown) => void
  const promise = new Promise<T>((res, rej) => {
    resolve = res
    reject = rej
  })
  return { promise, resolve, reject }
}

describe('store menu store', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.mocked(fetchMenu).mockReset()
  })

  it('loads a menu for the selected store', async () => {
    vi.mocked(fetchMenu).mockResolvedValue(menuFor(central))
    const store = useStoreMenuStore()

    await store.load(1)

    expect(store.menu?.store.id).toBe(1)
    expect(store.loading).toBe(false)
    expect(store.error).toBeNull()
  })

  it('ignores a stale response when the user switches stores quickly', async () => {
    const first = deferred<MenuResponse>()
    const second = deferred<MenuResponse>()
    const riverside = { ...central, id: 2, code: 'RIVERSIDE', name: '滨江店' }
    vi.mocked(fetchMenu)
      .mockReturnValueOnce(first.promise)
      .mockReturnValueOnce(second.promise)

    const store = useStoreMenuStore()
    const firstLoad = store.load(1)
    const secondLoad = store.load(2)

    second.resolve(menuFor(riverside))
    await secondLoad
    first.resolve(menuFor(central))
    await firstLoad

    expect(store.menu?.store.id).toBe(2)
    expect(store.loading).toBe(false)
  })

  it('surfaces a 404 as a store-not-found error', async () => {
    vi.mocked(fetchMenu).mockRejectedValue({
      isAxiosError: true,
      response: {
        status: 404,
        headers: { 'x-trace-id': 'trace-menu-404' },
        data: {
          code: 'STORE_NOT_FOUND',
          message: '门店不存在',
          traceId: 'trace-menu-404',
        },
      },
    })
    const store = useStoreMenuStore()

    await store.load(999)

    expect(store.menu).toBeNull()
    expect(store.error).toEqual({
      code: 'STORE_NOT_FOUND',
      message: '门店不存在',
      traceId: 'trace-menu-404',
    })
  })
})
