import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import type { MenuProduct, Store } from '@/types/catalog'

export const CART_STORAGE_KEY = 'burger-order.cart.v1'
const MAX_QUANTITY = 99

export interface CartLine {
  productId: number
  code: string
  name: string
  priceAtAdd: string
  quantity: number
}

interface CartState {
  ownerUsername: string | null
  storeId: number | null
  storeCode: string | null
  storeName: string | null
  items: CartLine[]
}

export class CartStoreMismatchError extends Error {
  readonly currentStoreName: string

  constructor(currentStoreName: string) {
    super('当前购物车属于另一家门店')
    this.name = 'CartStoreMismatchError'
    this.currentStoreName = currentStoreName
  }
}

export const useCartStore = defineStore('cart', () => {
  const state = ref<CartState>(loadCart() ?? emptyState())

  const ownerUsername = computed(() => state.value.ownerUsername)
  const storeId = computed(() => state.value.storeId)
  const storeCode = computed(() => state.value.storeCode)
  const storeName = computed(() => state.value.storeName)
  const items = computed(() => state.value.items)
  const totalQuantity = computed(() =>
    state.value.items.reduce((total, item) => total + item.quantity, 0),
  )
  const hasItems = computed(() => state.value.items.length > 0)

  const subtotal = computed(() => {
    const cents = state.value.items.reduce((total, item) => {
      return total + priceToCents(item.priceAtAdd) * item.quantity
    }, 0)
    return (cents / 100).toFixed(2)
  })

  function addItem(store: Store, product: MenuProduct, username: string): void {
    if (!username) {
      throw new Error('购物车必须绑定已登录顾客')
    }
    if (state.value.ownerUsername !== username) {
      state.value = emptyState()
      state.value.ownerUsername = username
    }

    if (state.value.storeId !== null && state.value.storeId !== store.id) {
      throw new CartStoreMismatchError(state.value.storeName ?? '其他门店')
    }

    if (state.value.storeId === null) {
      state.value.storeId = store.id
      state.value.storeCode = store.code
      state.value.storeName = store.name
    }

    const existing = state.value.items.find((item) => item.productId === product.id)
    if (existing) {
      existing.quantity = Math.min(MAX_QUANTITY, existing.quantity + 1)
    } else {
      state.value.items.push({
        productId: product.id,
        code: product.code,
        name: product.name,
        priceAtAdd: product.price,
        quantity: 1,
      })
    }
    persist()
  }

  function replaceWith(store: Store, product: MenuProduct, username: string): void {
    state.value = emptyState()
    state.value.ownerUsername = username
    addItem(store, product, username)
  }

  function increase(productId: number): void {
    const item = state.value.items.find((line) => line.productId === productId)
    if (!item) return
    item.quantity = Math.min(MAX_QUANTITY, item.quantity + 1)
    persist()
  }

  function decrease(productId: number): void {
    const item = state.value.items.find((line) => line.productId === productId)
    if (!item) return
    if (item.quantity <= 1) {
      state.value.items = state.value.items.filter((line) => line.productId !== productId)
      if (state.value.items.length === 0) {
        resetStoreButKeepOwner()
      }
    } else {
      item.quantity -= 1
    }
    persist()
  }

  function remove(productId: number): void {
    state.value.items = state.value.items.filter((line) => line.productId !== productId)
    if (state.value.items.length === 0) {
      resetStoreButKeepOwner()
    }
    persist()
  }

  function clear(): void {
    state.value = emptyState()
    if (hasStorage()) {
      localStorage.removeItem(CART_STORAGE_KEY)
    }
  }

  /**
   * The local cart is user-scoped. On login/logout the shell calls this with
   * the current username; switching accounts cannot inherit another user's cart.
   */
  function bindOwner(username: string | null): void {
    if (state.value.ownerUsername === username) {
      return
    }
    state.value = emptyState()
    state.value.ownerUsername = username
    if (username === null) {
      if (hasStorage()) localStorage.removeItem(CART_STORAGE_KEY)
      return
    }
    persist()
  }

  function resetStoreButKeepOwner(): void {
    state.value.storeId = null
    state.value.storeCode = null
    state.value.storeName = null
  }

  function persist(): void {
    if (!hasStorage()) return
    localStorage.setItem(CART_STORAGE_KEY, JSON.stringify(state.value))
  }

  return {
    ownerUsername,
    storeId,
    storeCode,
    storeName,
    items,
    totalQuantity,
    subtotal,
    hasItems,
    addItem,
    replaceWith,
    increase,
    decrease,
    remove,
    clear,
    bindOwner,
  }
})

function emptyState(): CartState {
  return {
    ownerUsername: null,
    storeId: null,
    storeCode: null,
    storeName: null,
    items: [],
  }
}

function loadCart(): CartState | null {
  if (!hasStorage()) return null
  try {
    const raw = localStorage.getItem(CART_STORAGE_KEY)
    if (!raw) return null
    const parsed = JSON.parse(raw) as unknown
    return isCartState(parsed) ? parsed : null
  } catch {
    return null
  }
}

function isCartState(value: unknown): value is CartState {
  if (!value || typeof value !== 'object') return false
  const candidate = value as Partial<CartState>
  if (!Array.isArray(candidate.items)) return false
  if (candidate.ownerUsername !== null && typeof candidate.ownerUsername !== 'string') return false
  if (candidate.storeId !== null && typeof candidate.storeId !== 'number') return false
  if (candidate.storeName !== null && typeof candidate.storeName !== 'string') return false
  if (candidate.storeCode !== null && typeof candidate.storeCode !== 'string') return false
  return candidate.items.every(isCartLine)
}

function isCartLine(value: unknown): value is CartLine {
  if (!value || typeof value !== 'object') return false
  const line = value as Partial<CartLine>
  return (
    typeof line.productId === 'number' &&
    typeof line.code === 'string' &&
    typeof line.name === 'string' &&
    typeof line.priceAtAdd === 'string' &&
    typeof line.quantity === 'number' &&
    Number.isInteger(line.quantity) &&
    line.quantity > 0 &&
    line.quantity <= MAX_QUANTITY
  )
}

function priceToCents(value: string): number {
  const price = Number(value)
  return Number.isFinite(price) ? Math.round(price * 100) : 0
}

function hasStorage(): boolean {
  return typeof localStorage !== 'undefined'
}
