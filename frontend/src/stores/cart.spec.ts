import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { CartStoreMismatchError, useCartStore } from './cart'
import type { MenuProduct, Store } from '@/types/catalog'

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

const riverside: Store = {
  ...central,
  id: 2,
  code: 'RIVERSIDE',
  name: '滨江店',
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

describe('cart store', () => {
  beforeEach(() => {
    vi.stubGlobal('localStorage', new MemoryStorage())
    setActivePinia(createPinia())
  })

  it('adds items for one store and computes quantity and subtotal', () => {
    const cart = useCartStore()

    cart.addItem(central, classic, 'demo_customer')
    cart.addItem(central, classic, 'demo_customer')
    cart.addItem(central, cola, 'demo_customer')

    expect(cart.storeId).toBe(1)
    expect(cart.totalQuantity).toBe(3)
    expect(cart.items).toHaveLength(2)
    expect(cart.items[0].quantity).toBe(2)
    expect(cart.subtotal).toBe('74.00')
    expect(cart.ownerUsername).toBe('demo_customer')
  })

  it('persists and restores the local cart', () => {
    const cart = useCartStore()
    cart.addItem(central, classic, 'demo_customer')
    cart.increase(classic.id)

    setActivePinia(createPinia())
    const restored = useCartStore()

    expect(restored.storeName).toBe('中央车站店')
    expect(restored.items[0].quantity).toBe(2)
    expect(restored.subtotal).toBe('64.00')
  })

  it('rejects mixing stores until the caller explicitly replaces the cart', () => {
    const cart = useCartStore()
    cart.addItem(central, classic, 'demo_customer')

    expect(() => cart.addItem(riverside, classic, 'demo_customer'))
      .toThrow(CartStoreMismatchError)

    cart.replaceWith(riverside, { ...classic, id: 2001, code: 'S2-CLASSIC', price: '32.00' }, 'demo_customer')

    expect(cart.storeId).toBe(2)
    expect(cart.items).toHaveLength(1)
  })

  it('clears a cart when the local owner changes', () => {
    const cart = useCartStore()
    cart.addItem(central, classic, 'customer_a')
    cart.bindOwner('customer_b')

    expect(cart.items).toEqual([])
    expect(cart.ownerUsername).toBe('customer_b')
  })

  it('caps quantity at 99 for local display', () => {
    const cart = useCartStore()
    cart.addItem(central, classic, 'demo_customer')
    for (let i = 0; i < 120; i++) {
      cart.increase(classic.id)
    }

    expect(cart.items[0].quantity).toBe(99)
  })
})
