import type { StoreBusinessStatus } from './catalog'

export type UserRole = 'CUSTOMER' | 'STAFF' | 'ADMIN'

export interface AuthUser {
  id: number
  username: string
  displayName: string
  role: UserRole
}

export interface CsrfTokenResponse {
  token: string
  headerName: string
  parameterName: string
}

export interface StaffStore {
  id: number
  code: string
  name: string
  address: string
  businessStatus: StoreBusinessStatus
  acceptingOrders: boolean
  orderable: boolean
}

export interface StaffWorkspaceResponse {
  stores: StaffStore[]
}

export interface AdminEntryResponse {
  role: 'ADMIN'
  available: boolean
  message: string
  checkedAt: string
}
