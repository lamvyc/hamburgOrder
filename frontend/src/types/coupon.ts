export interface CouponTemplate {
  id: number
  code: string
  name: string
  discountAmount: string
  minOrderAmount: string
  remainingQuantity: number
  claimed: boolean
  validFrom: string
  validTo: string
}

export interface UserCoupon {
  id: number
  templateId: number
  code: string
  name: string
  discountAmount: string
  minOrderAmount: string
  status: 'UNUSED' | 'LOCKED' | 'USED'
  orderId: number | null
  claimedAt: string
  usedAt: string | null
}
