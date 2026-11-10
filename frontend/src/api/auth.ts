import { ApiContractError, http } from './http'
import type {
  AdminEntryResponse,
  AuthUser,
  CsrfTokenResponse,
  StaffStore,
  StaffWorkspaceResponse,
  UserRole,
} from '@/types/auth'

const ROLES: UserRole[] = ['CUSTOMER', 'STAFF', 'ADMIN']

export async function fetchCsrfToken(): Promise<CsrfTokenResponse> {
  const response = await http.get<unknown>('/auth/csrf')
  if (!isCsrfTokenResponse(response.data)) {
    throw new ApiContractError('CSRF 响应不符合契约')
  }
  return response.data
}

export async function login(credentials: {
  username: string
  password: string
}): Promise<AuthUser> {
  const response = await http.post<unknown>('/auth/login', credentials)
  if (!isAuthUser(response.data)) {
    throw new ApiContractError('登录响应不符合契约')
  }
  return response.data
}

/**
 * 401 is a normal "no session" result, not a UI error. The browser still keeps
 * the CSRF cookie, so the login form can immediately POST after this call.
 */
export async function fetchMe(): Promise<AuthUser | null> {
  const response = await http.get<unknown>('/auth/me', {
    validateStatus: (status) => status === 200 || status === 401,
  })
  if (response.status === 401) {
    return null
  }
  if (!isAuthUser(response.data)) {
    throw new ApiContractError('当前用户响应不符合契约')
  }
  return response.data
}

export async function logout(): Promise<void> {
  await http.post('/auth/logout')
}

export async function fetchStaffWorkspace(): Promise<StaffWorkspaceResponse> {
  const response = await http.get<unknown>('/staff/workspace')
  if (!isStaffWorkspaceResponse(response.data)) {
    throw new ApiContractError('员工工作区响应不符合契约')
  }
  return response.data
}

export async function fetchAdminEntry(): Promise<AdminEntryResponse> {
  const response = await http.get<unknown>('/admin/entry')
  if (!isAdminEntryResponse(response.data)) {
    throw new ApiContractError('管理员入口响应不符合契约')
  }
  return response.data
}

function isCsrfTokenResponse(value: unknown): value is CsrfTokenResponse {
  if (!isRecord(value)) return false
  return (
    typeof value.token === 'string' &&
    typeof value.headerName === 'string' &&
    typeof value.parameterName === 'string'
  )
}

function isAuthUser(value: unknown): value is AuthUser {
  if (!isRecord(value)) return false
  return (
    typeof value.id === 'number' &&
    typeof value.username === 'string' &&
    typeof value.displayName === 'string' &&
    isRole(value.role)
  )
}

function isStaffWorkspaceResponse(value: unknown): value is StaffWorkspaceResponse {
  if (!isRecord(value) || !Array.isArray(value.stores)) return false
  return value.stores.every(isStaffStore)
}

function isStaffStore(value: unknown): value is StaffStore {
  if (!isRecord(value)) return false
  return (
    typeof value.id === 'number' &&
    typeof value.code === 'string' &&
    typeof value.name === 'string' &&
    typeof value.address === 'string' &&
    (value.businessStatus === 'OPEN' || value.businessStatus === 'CLOSED') &&
    typeof value.acceptingOrders === 'boolean' &&
    typeof value.orderable === 'boolean'
  )
}

function isAdminEntryResponse(value: unknown): value is AdminEntryResponse {
  if (!isRecord(value)) return false
  return (
    value.role === 'ADMIN' &&
    typeof value.available === 'boolean' &&
    typeof value.message === 'string' &&
    typeof value.checkedAt === 'string'
  )
}

function isRole(value: unknown): value is UserRole {
  return typeof value === 'string' && ROLES.includes(value as UserRole)
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null
}
