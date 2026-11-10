import { defineStore } from 'pinia'
import { ref } from 'vue'
import { fetchMenu } from '@/api/catalog'
import { toUiError } from '@/api/http'
import type { MenuResponse } from '@/types/catalog'
import type { UiError } from '@/types/system'

export const useStoreMenuStore = defineStore('storeMenu', () => {
  const menu = ref<MenuResponse | null>(null)
  const loading = ref(false)
  const error = ref<UiError | null>(null)

  // A route change can leave an older request in flight. Only the newest
  // request may update state; otherwise a slow store 1 response could overwrite
  // the menu the user just opened for store 2.
  let requestSequence = 0

  async function load(storeId: number): Promise<void> {
    const sequence = ++requestSequence
    loading.value = true
    error.value = null
    menu.value = null

    try {
      const next = await fetchMenu(storeId)
      if (sequence !== requestSequence) {
        return
      }
      menu.value = next
    } catch (cause) {
      if (sequence !== requestSequence) {
        return
      }
      error.value = toUiError(cause)
      menu.value = null
    } finally {
      if (sequence === requestSequence) {
        loading.value = false
      }
    }
  }

  return {
    menu,
    loading,
    error,
    load,
  }
})
