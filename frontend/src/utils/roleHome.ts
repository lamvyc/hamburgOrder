import type { UserRole } from '@/types/auth'

export function homeForRole(role: UserRole | null): string {
  if (role === 'STAFF') return '/staff'
  if (role === 'ADMIN') return '/admin'
  return '/'
}

export function safeRedirectTarget(value: unknown): string | null {
  if (typeof value !== 'string') return null
  if (!value.startsWith('/') || value.startsWith('//')) return null
  if (value.startsWith('/login')) return null
  return value
}
