import { defineStore } from 'pinia'
import { ref } from 'vue'
import { createOrder } from '@/api/orders'
import { toUiError } from '@/api/http'
import type { CartLine } from './cart'
import type { Order } from '@/types/order'
import type { UiError } from '@/types/system'

export const CHECKOUT_STORAGE_KEY = 'burger-order.checkout.v1'

interface CheckoutCart {
  storeId: number | null
  items: CartLine[]
  clear: () => void
}

interface PendingCheckout {
  key: string
  fingerprint: string
}

export const useCheckoutStore = defineStore('checkout', () => {
  const pending = ref<PendingCheckout | null>(loadPending())
  const submitting = ref(false)
  const error = ref<UiError | null>(null)

  async function submit(
    cart: CheckoutCart,
    userCouponId: number | null = null,
  ): Promise<Order> {
    if (!cart.storeId || cart.items.length === 0) {
      throw new Error('购物车为空，无法提交订单')
    }
    if (submitting.value) {
      throw new Error('订单正在提交，请勿重复点击')
    }

    const fingerprint = cartFingerprint(cart, userCouponId)
    if (!pending.value || pending.value.fingerprint !== fingerprint) {
      // A changed cart must never reuse the old key: the server would answer
      // IDEMPOTENCY_CONFLICT by design.
      pending.value = { key: newIdempotencyKey(), fingerprint }
      persist(pending.value)
    }
    const key = pending.value.key

    submitting.value = true
    error.value = null
    try {
      const response = await createOrder({
        storeId: cart.storeId,
        items: cart.items
          .map((item) => ({ productId: item.productId, quantity: item.quantity }))
          .sort((a, b) => a.productId - b.productId),
        ...(userCouponId === null ? {} : { userCouponId }),
      }, key)

      pending.value = null
      removePending()
      cart.clear()
      return response.order
    } catch (cause) {
      error.value = toUiError(cause)
      throw cause
    } finally {
      submitting.value = false
    }
  }

  return {
    submitting,
    error,
    submit,
  }
})

function cartFingerprint(cart: CheckoutCart, userCouponId: number | null): string {
  const items = [...cart.items]
    .sort((a, b) => a.productId - b.productId)
    .map((item) => `${item.productId}:${item.quantity}`)
    .join(',')
  return `${cart.storeId}|coupon:${userCouponId ?? ''}|${items}`
}

function newIdempotencyKey(): string {
  if (typeof crypto !== 'undefined' && typeof crypto.randomUUID === 'function') {
    return crypto.randomUUID()
  }
  return `checkout-${Date.now()}-${Math.random().toString(36).slice(2, 10)}`
}

function loadPending(): PendingCheckout | null {
  if (typeof localStorage === 'undefined') return null
  try {
    const raw = localStorage.getItem(CHECKOUT_STORAGE_KEY)
    if (!raw) return null
    const parsed = JSON.parse(raw) as Partial<PendingCheckout>
    return typeof parsed.key === 'string' && typeof parsed.fingerprint === 'string'
      ? { key: parsed.key, fingerprint: parsed.fingerprint }
      : null
  } catch {
    return null
  }
}

function persist(value: PendingCheckout): void {
  if (typeof localStorage === 'undefined') return
  localStorage.setItem(CHECKOUT_STORAGE_KEY, JSON.stringify(value))
}

function removePending(): void {
  if (typeof localStorage === 'undefined') return
  localStorage.removeItem(CHECKOUT_STORAGE_KEY)
}
