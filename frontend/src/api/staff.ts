import { ApiContractError, http } from './http'
import { isOrder } from './orders'
import type { Order } from '@/types/order'

export async function fetchStaffOrders(storeId: number): Promise<Order[]> {
  const response = await http.get<unknown>('/staff/orders', {
    params: { storeId },
  })
  if (!isOrderList(response.data)) {
    throw new ApiContractError('员工订单列表响应不符合契约')
  }
  return response.data.items
}

export async function startOrder(orderId: number): Promise<Order> {
  return postTransition(`/staff/orders/${orderId}/start`)
}

export async function markOrderReady(orderId: number): Promise<Order> {
  return postTransition(`/staff/orders/${orderId}/ready`)
}

export async function completeOrder(orderId: number): Promise<Order> {
  return postTransition(`/staff/orders/${orderId}/complete`)
}

async function postTransition(path: string): Promise<Order> {
  const response = await http.post<unknown>(path)
  if (!isOrder(response.data)) {
    throw new ApiContractError('员工订单转换响应不符合契约')
  }
  return response.data
}

function isOrderList(value: unknown): value is { items: Order[] } {
  if (!value || typeof value !== 'object') return false
  const items = (value as { items?: unknown }).items
  return Array.isArray(items) && items.every(isOrder)
}
