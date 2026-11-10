import { ApiContractError, http } from './http'
import type { HealthState, MenuCacheStats, SystemStatusResponse } from '@/types/system'

const HEALTH_STATES: HealthState[] = ['UP', 'DEGRADED', 'DOWN']

/**
 * HTTP 503 is an expected degraded snapshot, not an Axios failure. Treating it
 * as data lets the page distinguish "backend unreachable" from "backend alive,
 * database not ready". Other non-2xx responses still reject.
 */
export async function fetchSystemStatus(): Promise<SystemStatusResponse> {
  const response = await http.get<unknown>('/system/status', {
    validateStatus: (status) => status === 200 || status === 503,
  })

  if (!isSystemStatusResponse(response.data)) {
    throw new ApiContractError()
  }
  return response.data
}

export async function fetchMenuCacheStats(): Promise<MenuCacheStats> {
  const response = await http.get<unknown>('/system/menu-cache')
  if (!isMenuCacheStats(response.data)) {
    throw new ApiContractError('缓存统计响应不符合契约')
  }
  return response.data
}

function isMenuCacheStats(value: unknown): value is MenuCacheStats {
  if (!value || typeof value !== 'object') return false
  const candidate = value as Record<string, unknown>
  return ['hit', 'miss', 'bypass', 'negative', 'stampedeWait']
    .every((key) => typeof candidate[key] === 'number')
}

function isSystemStatusResponse(value: unknown): value is SystemStatusResponse {
  if (!value || typeof value !== 'object') {
    return false
  }
  const candidate = value as Partial<SystemStatusResponse>
  return (
    isHealthState(candidate.status) &&
    typeof candidate.checkedAt === 'string' &&
    isApplicationStatus(candidate.application) &&
    isDatabaseStatus(candidate.database)
  )
}

function isApplicationStatus(value: unknown): boolean {
  if (!value || typeof value !== 'object') {
    return false
  }
  const candidate = value as Record<string, unknown>
  return (
    isHealthState(candidate.status) &&
    typeof candidate.name === 'string' &&
    typeof candidate.version === 'string' &&
    typeof candidate.uptimeSeconds === 'number'
  )
}

function isDatabaseStatus(value: unknown): boolean {
  if (!value || typeof value !== 'object') {
    return false
  }
  const candidate = value as Record<string, unknown>
  return (
    isHealthState(candidate.status) &&
    typeof candidate.connected === 'boolean' &&
    typeof candidate.latencyMs === 'number' &&
    typeof candidate.schemaReady === 'boolean'
  )
}

function isHealthState(value: unknown): value is HealthState {
  return typeof value === 'string' && HEALTH_STATES.includes(value as HealthState)
}
