import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import {
  fetchCsrfToken,
  fetchMe,
  login as loginRequest,
  logout as logoutRequest,
} from '@/api/auth'
import { toUiError } from '@/api/http'
import type { AuthUser, UserRole } from '@/types/auth'
import type { UiError } from '@/types/system'

export const useAuthStore = defineStore('auth', () => {
  const user = ref<AuthUser | null>(null)
  const initialized = ref(false)
  const loading = ref(false)
  const error = ref<UiError | null>(null)

  const isAuthenticated = computed(() => user.value !== null)
  const role = computed<UserRole | null>(() => user.value?.role ?? null)
  const isCustomer = computed(() => role.value === 'CUSTOMER')
  const isStaff = computed(() => role.value === 'STAFF')
  const isAdmin = computed(() => role.value === 'ADMIN')

  async function initialize(): Promise<void> {
    if (initialized.value || loading.value) {
      return
    }
    loading.value = true
    error.value = null
    try {
      await fetchCsrfToken()
      user.value = await fetchMe()
    } catch (cause) {
      error.value = toUiError(cause)
      user.value = null
    } finally {
      initialized.value = true
      loading.value = false
    }
  }

  async function login(username: string, password: string): Promise<AuthUser> {
    loading.value = true
    error.value = null
    try {
      // A fresh page might not have run initialize() yet. Fetching the cookie
      // first makes login independent of render/route ordering.
      await fetchCsrfToken()
      const authenticated = await loginRequest({ username, password })
      user.value = authenticated
      initialized.value = true
      await refreshCsrfAfterSessionChange()
      return authenticated
    } catch (cause) {
      error.value = toUiError(cause)
      user.value = null
      throw cause
    } finally {
      loading.value = false
    }
  }

  async function logout(): Promise<void> {
    error.value = null
    try {
      await logoutRequest()
      user.value = null
      initialized.value = true
      await refreshCsrfAfterSessionChange()
    } catch (cause) {
      error.value = toUiError(cause)
      throw cause
    }
  }

  async function refreshCsrfAfterSessionChange(): Promise<void> {
    try {
      await fetchCsrfToken()
    } catch {
      // The session itself is valid; a later page load can fetch a new token.
      // Do not turn a successful login/logout into a misleading auth failure.
    }
  }

  return {
    user,
    initialized,
    loading,
    error,
    isAuthenticated,
    role,
    isCustomer,
    isStaff,
    isAdmin,
    initialize,
    login,
    logout,
  }
})
