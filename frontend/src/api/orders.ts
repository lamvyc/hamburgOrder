import { ApiContractError, http } from './http'
import type {
  CreateOrderRequest,
  CreateOrderResponse,
  Order,
  OrderEvent,
  OrderItem,
  OrderStatus,
} from '@/types/order'

const ORDER_STATUSES: OrderStatus[] = [
  'WAIT_PAY',
  'WAIT_MAKE',
  'MAKING',
  'READY',
  'COMPLETED',
  'CANCELLED',
]

export async function createOrder(
  request: CreateOrderRequest,
  idempotencyKey: string,
): Promise<CreateOrderResponse> {
  const response = await http.post<unknown>('/orders', request, {
    headers: {
      'Idempotency-Key': idempotencyKey,
    },
  })
  if (!isOrder(response.data)) {
    throw new ApiContractError('订单响应不符合契约')
  }
  const replayHeader = response.headers['idempotent-replayed']
  return {
    order: response.data,
    replayed: String(replayHeader ?? '').toLowerCase() === 'true',
  }
}

export async function payOrder(orderId: number): Promise<Order> {
  const response = await http.post<unknown>(`/orders/${orderId}/pay`)
  if (!isOrder(response.data)) {
    throw new ApiContractError('支付响应不符合契约')
  }
  return response.data
}

export async function cancelOrder(orderId: number): Promise<Order> {
  const response = await http.post<unknown>(`/orders/${orderId}/cancel`)
  if (!isOrder(response.data)) {
    throw new ApiContractError('取消订单响应不符合契约')
  }
  return response.data
}

export async function fetchOrderEvents(orderId: number): Promise<OrderEvent[]> {
  const response = await http.get<unknown>(`/orders/${orderId}/events`)
  if (!Array.isArray(response.data) || !response.data.every(isOrderEvent)) {
    throw new ApiContractError('订单事件响应不符合契约')
  }
  return response.data
}

export async function fetchOrder(orderId: number): Promise<Order> {
  const response = await http.get<unknown>(`/orders/${orderId}`)
  if (!isOrder(response.data)) {
    throw new ApiContractError('订单详情响应不符合契约')
  }
  return response.data
}

export function isOrder(value: unknown): value is Order {
  if (!isRecord(value) || !Array.isArray(value.items)) {
    return false
  }
  return (
    typeof value.id === 'number' &&
    typeof value.orderNo === 'string' &&
    isOrderStatus(value.status) &&
    typeof value.storeId === 'number' &&
    typeof value.storeName === 'string' &&
    typeof value.totalAmount === 'string' &&
    typeof value.discountAmount === 'string' &&
    typeof value.currency === 'string' &&
    typeof value.expiresAt === 'string' &&
    typeof value.createdAt === 'string' &&
    value.items.every(isOrderItem)
  )
}

function isOrderItem(value: unknown): value is OrderItem {
  if (!isRecord(value)) return false
  return (
    typeof value.productId === 'number' &&
    typeof value.productCode === 'string' &&
    typeof value.productName === 'string' &&
    typeof value.unitPrice === 'string' &&
    typeof value.quantity === 'number' &&
    typeof value.lineAmount === 'string'
  )
}

function isOrderEvent(value: unknown): value is OrderEvent {
  if (!isRecord(value)) return false
  return (
    typeof value.id === 'number' &&
    typeof value.eventType === 'string' &&
    (value.fromStatus === null || isOrderStatus(value.fromStatus)) &&
    isOrderStatus(value.toStatus) &&
    (value.operatorName === null || typeof value.operatorName === 'string') &&
    (value.reason === null || typeof value.reason === 'string') &&
    typeof value.createdAt === 'string'
  )
}

function isOrderStatus(value: unknown): value is OrderStatus {
  return typeof value === 'string' && ORDER_STATUSES.includes(value as OrderStatus)
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null
}
