<script setup lang="ts">
import { watch } from 'vue'
import SiteFooter from '@/components/SiteFooter.vue'
import SiteHeader from '@/components/SiteHeader.vue'
import { useAuthStore } from '@/stores/auth'
import { useCartStore } from '@/stores/cart'

const auth = useAuthStore()
const cart = useCartStore()

// Router guards initialize auth. Only then may the local cart be bound to a
// user; clearing it during the initial anonymous render would lose a reloaded
// customer's cart even when the session is still valid.
watch(
  [() => auth.initialized, () => auth.user?.username ?? null],
  ([initialized, username]) => {
    if (!initialized) return
    cart.bindOwner(username)
  },
  { immediate: true },
)
</script>

<template>
  <div class="app-shell">
    <SiteHeader />
    <div class="app-main">
      <RouterView />
    </div>
    <SiteFooter />
  </div>
</template>

<style scoped>
.app-shell {
  display: flex;
  min-height: 100vh;
  flex-direction: column;
}

.app-main {
  flex: 1 0 auto;
}
</style>
