import { defineStore } from 'pinia'
import { ref } from 'vue'
import { cancelOrder, fetchOrder, fetchOrderEvents, payOrder } from '@/api/orders'
import { toUiError } from '@/api/http'
import type { Order, OrderEvent } from '@/types/order'
import type { UiError } from '@/types/system'

export const useOrderDetailStore = defineStore('orderDetail', () => {
  const order = ref<Order | null>(null)
  const events = ref<OrderEvent[]>([])
  const loading = ref(false)
  const paying = ref(false)
  const cancelling = ref(false)
  const error = ref<UiError | null>(null)

  async function load(orderId: number): Promise<void> {
    loading.value = true
    error.value = null
    try {
      const [nextOrder, nextEvents] = await Promise.all([
        fetchOrder(orderId),
        fetchOrderEvents(orderId),
      ])
      order.value = nextOrder
      events.value = nextEvents
    } catch (cause) {
      error.value = toUiError(cause)
    } finally {
      loading.value = false
    }
  }

  async function pay(orderId: number): Promise<void> {
    paying.value = true
    error.value = null
    try {
      order.value = await payOrder(orderId)
      events.value = await fetchOrderEvents(orderId)
    } catch (cause) {
      error.value = toUiError(cause)
      throw cause
    } finally {
      paying.value = false
    }
  }

  async function cancel(orderId: number): Promise<void> {
    cancelling.value = true
    error.value = null
    try {
      order.value = await cancelOrder(orderId)
      events.value = await fetchOrderEvents(orderId)
    } catch (cause) {
      error.value = toUiError(cause)
      throw cause
    } finally {
      cancelling.value = false
    }
  }

  return {
    order,
    events,
    loading,
    paying,
    cancelling,
    error,
    load,
    pay,
    cancel,
  }
})
