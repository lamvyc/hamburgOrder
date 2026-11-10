import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import { fetchStores } from '@/api/catalog'
import { toUiError } from '@/api/http'
import type { Store } from '@/types/catalog'
import type { UiError } from '@/types/system'

export const useStoreListStore = defineStore('storeList', () => {
  const items = ref<Store[]>([])
  const loading = ref(false)
  const loaded = ref(false)
  const error = ref<UiError | null>(null)

  const orderableCount = computed(() => items.value.filter((store) => store.orderable).length)

  async function load(force = false): Promise<void> {
    if (loading.value || (loaded.value && !force)) {
      return
    }

    loading.value = true
    error.value = null
    try {
      const response = await fetchStores()
      items.value = response.items
      loaded.value = true
    } catch (cause) {
      error.value = toUiError(cause)
      loaded.value = false
    } finally {
      loading.value = false
    }
  }

  function refresh(): Promise<void> {
    return load(true)
  }

  return {
    items,
    loading,
    loaded,
    error,
    orderableCount,
    load,
    refresh,
  }
})
