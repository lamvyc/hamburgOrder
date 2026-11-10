import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { cancelOrder, fetchOrder, fetchOrderEvents, payOrder } from '@/api/orders'
import { useOrderDetailStore } from './orderDetail'
import type { Order, OrderEvent } from '@/types/order'

vi.mock('@/api/orders', () => ({
  fetchOrder: vi.fn(),
  fetchOrderEvents: vi.fn(),
  payOrder: vi.fn(),
  cancelOrder: vi.fn(),
}))

const waitingOrder: Order = {
  id: 7,
  orderNo: 'BO20261109200000ABCDEF',
  status: 'WAIT_PAY',
  storeId: 1,
  storeName: '中央车站店',
  totalAmount: '32.00',
  discountAmount: '0.00',
  currency: 'CNY',
  expiresAt: '2026-11-09T12:30:00Z',
  createdAt: '2026-11-09T12:00:00Z',
  items: [
    {
      productId: 1001,
      productCode: 'S1-CLASSIC',
      productName: '经典牛肉堡',
      unitPrice: '32.00',
      quantity: 1,
      lineAmount: '32.00',
    },
  ],
}

const createdEvent: OrderEvent = {
  id: 1,
  eventType: 'CREATED',
  fromStatus: null,
  toStatus: 'WAIT_PAY',
  operatorName: '演示顾客',
  reason: '顾客创建订单',
  createdAt: '2026-11-09T12:00:00Z',
}

const paidEvent: OrderEvent = {
  id: 2,
  eventType: 'PAYMENT_SUCCEEDED',
  fromStatus: 'WAIT_PAY',
  toStatus: 'WAIT_MAKE',
  operatorName: '演示顾客',
  reason: '模拟支付成功',
  createdAt: '2026-11-09T12:01:00Z',
}

describe('order detail store', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.mocked(fetchOrder).mockReset()
    vi.mocked(fetchOrderEvents).mockReset()
    vi.mocked(payOrder).mockReset()
    vi.mocked(cancelOrder).mockReset()
  })

  it('loads the order and its event trail together', async () => {
    vi.mocked(fetchOrder).mockResolvedValue(waitingOrder)
    vi.mocked(fetchOrderEvents).mockResolvedValue([createdEvent])
    const store = useOrderDetailStore()

    await store.load(7)

    expect(store.order?.id).toBe(7)
    expect(store.events).toEqual([createdEvent])
    expect(store.loading).toBe(false)
    expect(store.error).toBeNull()
  })

  it('pays and refreshes order/events without dropping the detail', async () => {
    const paidOrder = { ...waitingOrder, status: 'WAIT_MAKE' as const }
    vi.mocked(fetchOrder)
      .mockResolvedValueOnce(waitingOrder)
      .mockResolvedValueOnce(paidOrder)
    vi.mocked(fetchOrderEvents)
      .mockResolvedValueOnce([createdEvent])
      .mockResolvedValueOnce([createdEvent, paidEvent])
    vi.mocked(payOrder).mockResolvedValue(paidOrder)
    const store = useOrderDetailStore()
    await store.load(7)

    await store.pay(7)

    expect(store.order?.status).toBe('WAIT_MAKE')
    expect(store.events).toHaveLength(2)
    expect(store.paying).toBe(false)
  })

  it('cancels an unpaid order and refreshes the event trail', async () => {
    const cancelledOrder = { ...waitingOrder, status: 'CANCELLED' as const }
    const cancelledEvent: OrderEvent = {
      id: 2,
      eventType: 'CANCELLED',
      fromStatus: 'WAIT_PAY',
      toStatus: 'CANCELLED',
      operatorName: '演示顾客',
      reason: '顾客取消订单',
      createdAt: '2026-11-09T12:02:00Z',
    }
    vi.mocked(fetchOrder).mockResolvedValue(waitingOrder)
    vi.mocked(fetchOrderEvents)
      .mockResolvedValueOnce([createdEvent])
      .mockResolvedValueOnce([createdEvent, cancelledEvent])
    vi.mocked(cancelOrder).mockResolvedValue(cancelledOrder)
    const store = useOrderDetailStore()
    await store.load(7)

    await store.cancel(7)

    expect(store.order?.status).toBe('CANCELLED')
    expect(store.events).toHaveLength(2)
    expect(store.events[1].eventType).toBe('CANCELLED')
  })

  it('surfaces a cancel conflict and keeps the order visible', async () => {
    vi.mocked(fetchOrder).mockResolvedValue(waitingOrder)
    vi.mocked(fetchOrderEvents).mockResolvedValue([createdEvent])
    vi.mocked(cancelOrder).mockRejectedValue({
      isAxiosError: true,
      response: {
        status: 409,
        headers: {},
        data: {
          code: 'ORDER_STATUS_CONFLICT',
          message: '订单状态已变化，无法取消',
          traceId: 'trace-cancel-conflict',
        },
      },
    })
    const store = useOrderDetailStore()
    await store.load(7)

    await expect(store.cancel(7)).rejects.toBeDefined()

    expect(store.order?.status).toBe('WAIT_PAY')
    expect(store.error?.code).toBe('ORDER_STATUS_CONFLICT')
  })

  it('surfaces payment conflicts and keeps the last known order', async () => {
    vi.mocked(fetchOrder).mockResolvedValue(waitingOrder)
    vi.mocked(fetchOrderEvents).mockResolvedValue([createdEvent])
    vi.mocked(payOrder).mockRejectedValue({
      isAxiosError: true,
      response: {
        status: 409,
        headers: {},
        data: {
          code: 'ORDER_EXPIRED',
          message: '订单已过期，无法支付',
          traceId: 'trace-pay-expired',
        },
      },
    })
    const store = useOrderDetailStore()
    await store.load(7)

    await expect(store.pay(7)).rejects.toBeDefined()

    expect(store.order?.id).toBe(7)
    expect(store.error?.code).toBe('ORDER_EXPIRED')
    expect(store.paying).toBe(false)
  })
})
