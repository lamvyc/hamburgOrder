export type HealthState = 'UP' | 'DEGRADED' | 'DOWN'

export interface ApplicationStatus {
  status: HealthState
  name: string
  version: string
  javaVersion: string
  uptimeSeconds: number
}

export interface DatabaseStatus {
  status: HealthState
  connected: boolean
  product: string | null
  version: string | null
  latencyMs: number
  schemaReady: boolean
  flywayVersion: string | null
  lastMigration: string | null
  code: string | null
  message: string | null
}

export interface SystemStatusResponse {
  status: HealthState
  checkedAt: string
  traceId: string | null
  application: ApplicationStatus
  database: DatabaseStatus
}

export interface ApiErrorBody {
  code: string
  message: string
  traceId?: string | null
  timestamp?: string
  path?: string
}

export interface UiError {
  code: string
  message: string
  traceId?: string
}

export interface MenuCacheStats {
  hit: number
  miss: number
  bypass: number
  negative: number
  stampedeWait: number
}
