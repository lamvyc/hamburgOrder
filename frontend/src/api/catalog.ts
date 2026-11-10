import { ApiContractError, http } from './http'
import type {
  MenuCategory,
  MenuProduct,
  MenuResponse,
  Store,
  StoreBusinessStatus,
  StoreListResponse,
} from '@/types/catalog'

const STORE_STATUSES: StoreBusinessStatus[] = ['OPEN', 'CLOSED']

export async function fetchStores(): Promise<StoreListResponse> {
  const response = await http.get<unknown>('/stores')
  if (!isStoreListResponse(response.data)) {
    throw new ApiContractError('门店列表响应不符合契约')
  }
  return response.data
}

export async function fetchMenu(storeId: number): Promise<MenuResponse> {
  const response = await http.get<unknown>(`/stores/${storeId}/menu`)
  if (!isMenuResponse(response.data)) {
    throw new ApiContractError('菜单响应不符合契约')
  }
  return response.data
}

function isStoreListResponse(value: unknown): value is StoreListResponse {
  if (!isRecord(value) || !Array.isArray(value.items)) {
    return false
  }
  return value.items.every(isStore)
}

function isStore(value: unknown): value is Store {
  if (!isRecord(value)) {
    return false
  }
  return (
    typeof value.id === 'number' &&
    typeof value.code === 'string' &&
    typeof value.name === 'string' &&
    typeof value.address === 'string' &&
    typeof value.latitude === 'number' &&
    typeof value.longitude === 'number' &&
    isStoreStatus(value.businessStatus) &&
    typeof value.acceptingOrders === 'boolean' &&
    typeof value.orderable === 'boolean' &&
    typeof value.openingHours === 'string'
  )
}

function isMenuResponse(value: unknown): value is MenuResponse {
  if (!isRecord(value) || !isStore(value.store) || !Array.isArray(value.categories)) {
    return false
  }
  return value.categories.every(isMenuCategory)
}

function isMenuCategory(value: unknown): value is MenuCategory {
  if (!isRecord(value) || !Array.isArray(value.products)) {
    return false
  }
  return (
    typeof value.code === 'string' &&
    typeof value.name === 'string' &&
    value.products.every(isMenuProduct)
  )
}

function isMenuProduct(value: unknown): value is MenuProduct {
  if (!isRecord(value)) {
    return false
  }
  return (
    typeof value.id === 'number' &&
    typeof value.code === 'string' &&
    typeof value.name === 'string' &&
    typeof value.description === 'string' &&
    typeof value.price === 'string'
  )
}

function isStoreStatus(value: unknown): value is StoreBusinessStatus {
  return typeof value === 'string' && STORE_STATUSES.includes(value as StoreBusinessStatus)
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null
}
