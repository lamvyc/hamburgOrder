import { ApiContractError, http } from './http'
import type { CouponTemplate, UserCoupon } from '@/types/coupon'

export async function fetchAvailableCoupons(): Promise<CouponTemplate[]> {
  const response = await http.get<unknown>('/coupons/available')
  if (!Array.isArray(response.data) || !response.data.every(isCouponTemplate)) {
    throw new ApiContractError('优惠券列表响应不符合契约')
  }
  return response.data
}

export async function fetchMyCoupons(): Promise<UserCoupon[]> {
  const response = await http.get<unknown>('/coupons/mine')
  if (!Array.isArray(response.data) || !response.data.every(isUserCoupon)) {
    throw new ApiContractError('我的优惠券响应不符合契约')
  }
  return response.data
}

export async function claimCoupon(templateId: number): Promise<UserCoupon> {
  const response = await http.post<unknown>(`/coupons/${templateId}/claim`)
  if (!isUserCoupon(response.data)) {
    throw new ApiContractError('领取优惠券响应不符合契约')
  }
  return response.data
}

function isCouponTemplate(value: unknown): value is CouponTemplate {
  if (!isRecord(value)) return false
  return (
    typeof value.id === 'number' &&
    typeof value.code === 'string' &&
    typeof value.name === 'string' &&
    typeof value.discountAmount === 'string' &&
    typeof value.minOrderAmount === 'string' &&
    typeof value.remainingQuantity === 'number' &&
    typeof value.claimed === 'boolean' &&
    typeof value.validFrom === 'string' &&
    typeof value.validTo === 'string'
  )
}

function isUserCoupon(value: unknown): value is UserCoupon {
  if (!isRecord(value)) return false
  return (
    typeof value.id === 'number' &&
    typeof value.templateId === 'number' &&
    typeof value.code === 'string' &&
    typeof value.name === 'string' &&
    typeof value.discountAmount === 'string' &&
    typeof value.minOrderAmount === 'string' &&
    (value.status === 'UNUSED' || value.status === 'LOCKED' || value.status === 'USED') &&
    (value.orderId === null || typeof value.orderId === 'number') &&
    typeof value.claimedAt === 'string' &&
    (value.usedAt === null || typeof value.usedAt === 'string')
  )
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null
}
