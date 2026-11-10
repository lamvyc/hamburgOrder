import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { fetchStores } from '@/api/catalog'
import { useStoreListStore } from './storeList'
import type { Store, StoreListResponse } from '@/types/catalog'

vi.mock('@/api/catalog', () => ({
  fetchStores: vi.fn(),
}))

const stores: Store[] = [
  {
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
  },
  {
    id: 2,
    code: 'RIVERSIDE',
    name: '滨江店',
    address: '浦东新区滨江大道 66 号',
    latitude: 31.2397,
    longitude: 121.4998,
    businessStatus: 'OPEN',
    acceptingOrders: false,
    orderable: false,
    openingHours: '11:00–23:00',
  },
]

const response: StoreListResponse = { items: stores }

describe('store list store', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.mocked(fetchStores).mockReset()
  })

  it('loads stores and counts only orderable stores', async () => {
    vi.mocked(fetchStores).mockResolvedValue(response)
    const store = useStoreListStore()

    await store.load()

    expect(store.items).toEqual(stores)
    expect(store.orderableCount).toBe(1)
    expect(store.loaded).toBe(true)
    expect(store.error).toBeNull()
  })

  it('reports a backend failure without pretending the list is loaded', async () => {
    vi.mocked(fetchStores).mockRejectedValue({
      isAxiosError: true,
      code: 'ERR_NETWORK',
      message: 'Network Error',
    })
    const store = useStoreListStore()

    await store.load()

    expect(store.items).toEqual([])
    expect(store.loaded).toBe(false)
    expect(store.error?.code).toBe('BACKEND_UNREACHABLE')
  })
})
