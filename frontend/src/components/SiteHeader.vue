<script setup lang="ts">
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { useCartStore } from '@/stores/cart'
import { homeForRole } from '@/utils/roleHome'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()
const cart = useCartStore()

const isCatalog = computed(
  () => route.name === 'store-list' || route.name === 'store-menu',
)
const phase = computed(() => {
  const value = route.meta.phase
  return typeof value === 'string' ? value : 'STAGE A2 · SESSION'
})
const roleLabel = computed(() => {
  if (auth.role === 'CUSTOMER') return '顾客'
  if (auth.role === 'STAFF') return '员工'
  if (auth.role === 'ADMIN') return '管理员'
  return ''
})
const cartCount = computed(() => (auth.isCustomer ? cart.totalQuantity : 0))

async function handleLogout(): Promise<void> {
  try {
    await auth.logout()
    await router.push('/')
  } catch {
    // The header stays authenticated when the server rejects logout; the
    // login/profile surface owns the detailed error message.
  }
}
</script>

<template>
  <header class="site-header">
    <div class="header-inner">
      <RouterLink class="brand" to="/" aria-label="Burger Order Lab 首页">
        <span class="brand-mark" aria-hidden="true">B</span>
        <span class="brand-copy">
          <strong>Burger Order Lab</strong>
          <small>多门店点餐 · 实战工程</small>
        </span>
      </RouterLink>

      <div class="header-side">
        <nav class="nav" aria-label="主导航">
          <RouterLink class="nav-link" :class="{ active: isCatalog }" to="/">
            选店
          </RouterLink>
          <RouterLink
            v-if="auth.isCustomer"
            class="nav-link cart-link"
            :class="{ active: route.name === 'cart' }"
            to="/cart"
          >
            购物车
            <span v-if="cartCount > 0" class="cart-count">{{ cartCount }}</span>
          </RouterLink>
          <RouterLink
            v-if="auth.isStaff"
            class="nav-link"
            :class="{ active: route.name === 'staff-workspace' }"
            to="/staff"
          >
            员工工作台
          </RouterLink>
          <RouterLink
            v-if="auth.isAdmin"
            class="nav-link"
            :class="{ active: route.name === 'admin-entry' }"
            to="/admin"
          >
            管理入口
          </RouterLink>
          <RouterLink
            class="nav-link"
            :class="{ active: route.name === 'system-status' }"
            to="/system"
          >
            系统状态
          </RouterLink>
        </nav>

        <div class="account-zone">
          <template v-if="auth.user">
            <RouterLink class="user-chip" :to="homeForRole(auth.role)">
              <span>{{ roleLabel }}</span>
              <strong>{{ auth.user.displayName }}</strong>
            </RouterLink>
            <button class="logout-button" type="button" @click="handleLogout">
              退出
            </button>
          </template>
          <RouterLink v-else class="login-link" to="/login">登录</RouterLink>
        </div>

        <span class="phase-pill">
          <span class="live-dot" aria-hidden="true"></span>
          {{ phase }}
        </span>
      </div>
    </div>
  </header>
</template>

<style scoped>
.site-header {
  position: sticky;
  z-index: 20;
  top: 0;
  border-bottom: 1px solid var(--line-strong);
  background: rgba(244, 239, 228, 0.94);
  backdrop-filter: blur(10px);
}

.header-inner {
  display: flex;
  width: min(1180px, 100%);
  margin: 0 auto;
  padding: 16px 32px;
  align-items: center;
  justify-content: space-between;
  gap: 18px;
}

.brand {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-shrink: 0;
  text-decoration: none;
}

.brand-mark {
  display: grid;
  width: 40px;
  height: 40px;
  place-items: center;
  border: 1.5px solid var(--ink);
  border-radius: 50%;
  background: var(--ketchup);
  color: #fff8ec;
  font-family: var(--font-display);
  font-size: 1.4rem;
  font-style: italic;
  box-shadow: 2px 2px 0 var(--ink);
}

.brand-copy {
  display: grid;
  gap: 2px;
}

.brand-copy strong {
  font-size: 0.95rem;
  letter-spacing: -0.01em;
}

.brand-copy small {
  color: var(--muted);
  font-size: 0.69rem;
  letter-spacing: 0.07em;
}

.header-side {
  display: flex;
  align-items: center;
  gap: 18px;
  min-width: 0;
  flex-wrap: wrap;
  justify-content: flex-end;
}

.nav {
  display: flex;
  align-items: center;
  gap: 15px;
  flex-wrap: wrap;
}

.nav-link {
  position: relative;
  padding: 5px 0;
  color: var(--muted-strong);
  font-size: 0.75rem;
  font-weight: 600;
  text-decoration: none;
  white-space: nowrap;
}

.cart-link {
  display: inline-flex;
  align-items: center;
  gap: 5px;
}

.cart-count {
  display: grid;
  min-width: 18px;
  height: 18px;
  padding: 0 4px;
  place-items: center;
  border-radius: 999px;
  background: var(--ketchup);
  color: #fff8ec;
  font-family: var(--font-mono);
  font-size: 0.58rem;
}

.nav-link::after {
  position: absolute;
  right: 0;
  bottom: 0;
  left: 0;
  height: 2px;
  background: var(--ketchup);
  content: "";
  transform: scaleX(0);
  transform-origin: left;
  transition: transform 160ms ease;
}

.nav-link:hover,
.nav-link.active {
  color: var(--ink);
}

.nav-link.active::after {
  transform: scaleX(1);
}

.account-zone {
  display: flex;
  align-items: center;
  gap: 10px;
  padding-left: 16px;
  border-left: 1px solid var(--line);
}

.login-link {
  color: var(--ink);
  font-size: 0.76rem;
  font-weight: 700;
  text-decoration: underline;
  text-underline-offset: 4px;
}

.user-chip {
  display: grid;
  gap: 1px;
  max-width: 140px;
  padding: 5px 10px;
  border: 1px solid var(--line-strong);
  border-radius: 6px;
  background: rgba(255, 253, 248, 0.7);
  text-decoration: none;
}

.user-chip span {
  color: var(--muted);
  font-family: var(--font-mono);
  font-size: 0.55rem;
  letter-spacing: 0.08em;
}

.user-chip strong {
  overflow: hidden;
  font-size: 0.7rem;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.logout-button {
  padding: 0;
  border: 0;
  background: none;
  color: var(--muted);
  font-size: 0.66rem;
  text-decoration: underline;
  text-underline-offset: 3px;
  cursor: pointer;
}

.logout-button:hover {
  color: var(--ketchup);
}

.phase-pill {
  display: inline-flex;
  align-items: center;
  gap: 7px;
  padding: 7px 10px;
  border: 1px solid var(--line-strong);
  border-radius: 999px;
  background: rgba(255, 253, 248, 0.7);
  font-family: var(--font-mono);
  font-size: 0.58rem;
  letter-spacing: 0.08em;
  white-space: nowrap;
}

.live-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: var(--green);
  box-shadow: 0 0 0 3px rgba(39, 106, 78, 0.13);
}

@media (max-width: 1080px) {
  .phase-pill {
    display: none;
  }
}

@media (max-width: 780px) {
  .header-inner {
    padding: 14px 18px;
    align-items: flex-start;
    flex-direction: column;
    gap: 10px;
  }

  .header-side {
    width: 100%;
    justify-content: space-between;
    align-items: center;
    gap: 10px 14px;
  }

  .nav {
    gap: 13px;
  }

  .account-zone {
    padding-left: 0;
    border-left: 0;
  }
}

@media (prefers-reduced-motion: reduce) {
  .nav-link::after {
    transition: none;
  }
}
</style>
