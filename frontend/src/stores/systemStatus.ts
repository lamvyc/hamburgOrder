import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import { fetchSystemStatus } from '@/api/system'
import { toUiError } from '@/api/http'
import type { SystemStatusResponse, UiError } from '@/types/system'

export const useSystemStatusStore = defineStore('systemStatus', () => {
  const snapshot = ref<SystemStatusResponse | null>(null)
  const loading = ref(false)
  const backendReachable = ref(false)
  const error = ref<UiError | null>(null)
  const lastSuccessAt = ref<string | null>(null)

  const isDatabaseReady = computed(() => snapshot.value?.database.status === 'UP')

  async function refresh(): Promise<void> {
    if (loading.value) {
      return
    }

    loading.value = true
    error.value = null

    try {
      const next = await fetchSystemStatus()
      snapshot.value = next
      backendReachable.value = true
      if (next.status === 'UP') {
        lastSuccessAt.value = next.checkedAt
      }
    } catch (cause) {
      const uiError = toUiError(cause)
      error.value = uiError
      snapshot.value = null
      backendReachable.value =
        uiError.code !== 'BACKEND_UNREACHABLE' && uiError.code !== 'BACKEND_TIMEOUT'
    } finally {
      loading.value = false
    }
  }

  return {
    snapshot,
    loading,
    backendReachable,
    error,
    lastSuccessAt,
    isDatabaseReady,
    refresh,
  }
})
