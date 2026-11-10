export type OrderStatus =
  | 'WAIT_PAY'
  | 'WAIT_MAKE'
  | 'MAKING'
  | 'READY'
  | 'COMPLETED'
  | 'CANCELLED'

export interface OrderItem {
  productId: number
  productCode: string
  productName: string
  unitPrice: string
  quantity: number
  lineAmount: string
}

export interface Order {
  id: number
  orderNo: string
  status: OrderStatus
  storeId: number
  storeName: string
  totalAmount: string
  discountAmount: string
  currency: string
  expiresAt: string
  createdAt: string
  items: OrderItem[]
}

export interface CreateOrderRequest {
  storeId: number
  items: Array<{
    productId: number
    quantity: number
  }>
  userCouponId?: number | null
}

export interface CreateOrderResponse {
  order: Order
  replayed: boolean
}

export interface OrderEvent {
  id: number
  eventType: string
  fromStatus: OrderStatus | null
  toStatus: OrderStatus
  operatorName: string | null
  reason: string | null
  createdAt: string
}
