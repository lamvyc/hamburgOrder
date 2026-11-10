import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createOrder } from '@/api/orders'
import { useCartStore } from './cart'
import { useCheckoutStore } from './checkout'
import type { MenuProduct, Store } from '@/types/catalog'
import type { Order } from '@/types/order'

vi.mock('@/api/orders', () => ({
  createOrder: vi.fn(),
}))

class MemoryStorage implements Storage {
  private data = new Map<string, string>()

  get length(): number {
    return this.data.size
  }

  clear(): void {
    this.data.clear()
  }

  getItem(key: string): string | null {
    return this.data.get(key) ?? null
  }

  key(index: number): string | null {
    return Array.from(this.data.keys())[index] ?? null
  }

  removeItem(key: string): void {
    this.data.delete(key)
  }

  setItem(key: string, value: string): void {
    this.data.set(key, value)
  }
}

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

const classic: MenuProduct = {
  id: 1001,
  code: 'S1-CLASSIC',
  name: '经典牛肉堡',
  description: '手打牛肉饼',
  price: '32.00',
}

const cola: MenuProduct = {
  id: 1006,
  code: 'S1-COLA',
  name: '冰镇可乐',
  description: '玻璃瓶装',
  price: '10.00',
}

const order: Order = {
  id: 9,
  orderNo: 'BO20261109190000ABC123',
  status: 'WAIT_PAY',
  storeId: 1,
  storeName: '中央车站店',
  totalAmount: '64.00',
  discountAmount: '0.00',
  currency: 'CNY',
  expiresAt: '2026-11-09T11:30:00Z',
  createdAt: '2026-11-09T11:00:00Z',
  items: [
    {
      productId: 1001,
      productCode: 'S1-CLASSIC',
      productName: '经典牛肉堡',
      unitPrice: '32.00',
      quantity: 2,
      lineAmount: '64.00',
    },
  ],
}

describe('checkout store', () => {
  beforeEach(() => {
    vi.stubGlobal('localStorage', new MemoryStorage())
    setActivePinia(createPinia())
    vi.mocked(createOrder).mockReset()
  })

  it('submits only store/product/quantity, clears the cart and pending key', async () => {
    vi.mocked(createOrder).mockResolvedValue({ order, replayed: false })
    const cart = useCartStore()
    cart.addItem(central, classic, 'demo_customer')
    cart.addItem(central, classic, 'demo_customer')
    const checkout = useCheckoutStore()

    const created = await checkout.submit(cart)

    expect(created.id).toBe(9)
    expect(createOrder).toHaveBeenCalledTimes(1)
    const [request, key] = vi.mocked(createOrder).mock.calls[0]
    expect(request).toEqual({
      storeId: 1,
      items: [{ productId: 1001, quantity: 2 }],
    })
    expect(key).toMatch(/^[A-Za-z0-9._-]{8,64}$/)
    expect(cart.hasItems).toBe(false)
    expect(checkout.submitting).toBe(false)
    expect(checkout.error).toBeNull()
  })

  it('keeps the same key for a retry of the same cart', async () => {
    vi.mocked(createOrder).mockRejectedValueOnce({
      isAxiosError: true,
      response: {
        status: 503,
        headers: {},
        data: {
          code: 'DATABASE_UNAVAILABLE',
          message: '数据库暂时不可用',
          traceId: 'trace-checkout-503',
        },
      },
    })
    const cart = useCartStore()
    cart.addItem(central, classic, 'demo_customer')
    const checkout = useCheckoutStore()

    await expect(checkout.submit(cart)).rejects.toBeDefined()
    expect(checkout.error?.code).toBe('DATABASE_UNAVAILABLE')
    const firstKey = vi.mocked(createOrder).mock.calls[0][1]

    vi.mocked(createOrder).mockResolvedValueOnce({ order, replayed: true })
    await checkout.submit(cart)

    const secondKey = vi.mocked(createOrder).mock.calls[1][1]
    expect(secondKey).toBe(firstKey)
    expect(cart.hasItems).toBe(false)
  })

  it('sends the selected coupon and changes the key when the coupon changes', async () => {
    vi.mocked(createOrder).mockRejectedValue({
      isAxiosError: true,
      code: 'ERR_NETWORK',
    })
    const cart = useCartStore()
    cart.addItem(central, classic, 'demo_customer')
    const checkout = useCheckoutStore()

    await expect(checkout.submit(cart, 42)).rejects.toBeDefined()
    const firstKey = vi.mocked(createOrder).mock.calls[0][1]
    expect(vi.mocked(createOrder).mock.calls[0][0].userCouponId).toBe(42)

    await expect(checkout.submit(cart, 43)).rejects.toBeDefined()
    const secondKey = vi.mocked(createOrder).mock.calls[1][1]
    expect(vi.mocked(createOrder).mock.calls[1][0].userCouponId).toBe(43)
    expect(secondKey).not.toBe(firstKey)
  })

  it('uses a new key when the cart content changes after a failure', async () => {
    vi.mocked(createOrder).mockRejectedValue({
      isAxiosError: true,
      code: 'ERR_NETWORK',
      message: 'Network Error',
    })
    const cart = useCartStore()
    cart.addItem(central, classic, 'demo_customer')
    const checkout = useCheckoutStore()

    await expect(checkout.submit(cart)).rejects.toBeDefined()
    const firstKey = vi.mocked(createOrder).mock.calls[0][1]

    cart.addItem(central, cola, 'demo_customer')
    await expect(checkout.submit(cart)).rejects.toBeDefined()
    const secondKey = vi.mocked(createOrder).mock.calls[1][1]

    expect(secondKey).not.toBe(firstKey)
  })
})
