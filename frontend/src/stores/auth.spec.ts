import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import {
  fetchCsrfToken,
  fetchMe,
  login as loginRequest,
  logout as logoutRequest,
} from '@/api/auth'
import { useAuthStore } from './auth'
import type { AuthUser } from '@/types/auth'

vi.mock('@/api/auth', () => ({
  fetchCsrfToken: vi.fn(),
  fetchMe: vi.fn(),
  login: vi.fn(),
  logout: vi.fn(),
}))

const customer: AuthUser = {
  id: 1,
  username: 'demo_customer',
  displayName: '演示顾客',
  role: 'CUSTOMER',
}

describe('auth store', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.mocked(fetchCsrfToken).mockReset().mockResolvedValue({
      token: 'csrf-token',
      headerName: 'X-XSRF-TOKEN',
      parameterName: '_csrf',
    })
    vi.mocked(fetchMe).mockReset()
    vi.mocked(loginRequest).mockReset()
    vi.mocked(logoutRequest).mockReset()
  })

  it('initializes anonymously when /me returns null', async () => {
    vi.mocked(fetchMe).mockResolvedValue(null)
    const auth = useAuthStore()

    await auth.initialize()

    expect(auth.initialized).toBe(true)
    expect(auth.user).toBeNull()
    expect(auth.isAuthenticated).toBe(false)
    expect(fetchCsrfToken).toHaveBeenCalledTimes(1)
  })

  it('fetches a CSRF token before login and refreshes it afterwards', async () => {
    vi.mocked(loginRequest).mockResolvedValue(customer)
    const auth = useAuthStore()

    await auth.login('demo_customer', 'Burger@123')

    expect(auth.user).toEqual(customer)
    expect(auth.role).toBe('CUSTOMER')
    expect(fetchCsrfToken).toHaveBeenCalledTimes(2)
    expect(loginRequest).toHaveBeenCalledWith({
      username: 'demo_customer',
      password: 'Burger@123',
    })
  })

  it('surfaces a bad-credentials error without authenticating', async () => {
    vi.mocked(loginRequest).mockRejectedValue({
      isAxiosError: true,
      response: {
        status: 401,
        headers: {},
        data: {
          code: 'INVALID_CREDENTIALS',
          message: '用户名或密码错误',
          traceId: 'trace-auth-401',
        },
      },
    })
    const auth = useAuthStore()

    await expect(auth.login('demo_customer', 'wrong')).rejects.toBeDefined()

    expect(auth.user).toBeNull()
    expect(auth.error?.code).toBe('INVALID_CREDENTIALS')
    expect(auth.error?.traceId).toBe('trace-auth-401')
  })

  it('clears the user after logout and obtains a fresh CSRF token', async () => {
    vi.mocked(loginRequest).mockResolvedValue(customer)
    vi.mocked(logoutRequest).mockResolvedValue()
    const auth = useAuthStore()
    await auth.login('demo_customer', 'Burger@123')

    await auth.logout()

    expect(auth.user).toBeNull()
    expect(logoutRequest).toHaveBeenCalledTimes(1)
    expect(fetchCsrfToken).toHaveBeenCalledTimes(3)
  })
})
