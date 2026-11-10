<script setup lang="ts">
import { computed, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { CartStoreMismatchError, useCartStore } from '@/stores/cart'
import { useStoreMenuStore } from '@/stores/storeMenu'
import type { MenuProduct, Store } from '@/types/catalog'
import { formatPrice } from '@/utils/format'


type TagType = 'success' | 'warning' | 'danger' | 'info' | 'primary'

const route = useRoute()
const router = useRouter()
const menuStore = useStoreMenuStore()
const auth = useAuthStore()
const cart = useCartStore()

const storeId = computed(() => Number(route.params.storeId))
const menu = computed(() => menuStore.menu)
const store = computed<Store | null>(() => menu.value?.store ?? null)
const productCount = computed(
  () => menu.value?.categories.reduce((total, category) => total + category.products.length, 0) ?? 0,
)

watch(
  storeId,
  (id) => {
    if (Number.isInteger(id) && id > 0) {
      void menuStore.load(id)
    }
  },
  { immediate: true },
)

function statusLabel(value: Store): string {
  if (value.businessStatus === 'CLOSED') return '休息中'
  if (!value.acceptingOrders) return '暂停接单'
  return '接单中'
}

function statusType(value: Store): TagType {
  if (value.businessStatus === 'CLOSED') return 'info'
  if (!value.acceptingOrders) return 'warning'
  return 'success'
}

function alertType(value: Store): 'warning' | 'info' {
  return value.businessStatus === 'CLOSED' ? 'info' : 'warning'
}

function alertTitle(value: Store): string {
  return value.businessStatus === 'CLOSED' ? '门店已休息' : '门店暂时不接单'
}

function reload(): void {
  if (Number.isInteger(storeId.value) && storeId.value > 0) {
    void menuStore.load(storeId.value)
  }
}

function goLogin(): void {
  void router.push({ name: 'login', query: { redirect: route.fullPath } })
}

async function addToCart(product: MenuProduct): Promise<void> {
  if (!auth.user) {
    goLogin()
    return
  }
  if (!auth.isCustomer || !store.value) {
    ElMessage.info('员工与管理员账号不参与顾客购物车')
    return
  }

  try {
    cart.addItem(store.value, product, auth.user.username)
    ElMessage.success(`${product.name} 已加入购物车`)
  } catch (cause) {
    if (!(cause instanceof CartStoreMismatchError)) {
      ElMessage.error('加入购物车失败')
      return
    }
    try {
      await ElMessageBox.confirm(
        `购物车已有「${cause.currentStoreName}」的商品。切换门店会清空旧购物车，是否继续？`,
        '切换门店？',
        {
          confirmButtonText: '清空并加入',
          cancelButtonText: '保留原购物车',
          type: 'warning',
        },
      )
      cart.replaceWith(store.value, product, auth.user.username)
      ElMessage.success(`已切换门店并加入 ${product.name}`)
    } catch {
      // Cancel is not an error.
    }
  }
}
</script>

<template>
  <main class="page">
    <div class="back-row">
      <RouterLink class="back-link" to="/">← 全部门店</RouterLink>
      <span class="back-meta">MENU / READ ONLY</span>
    </div>

    <div v-if="menuStore.loading" class="loading-block" aria-label="正在加载菜单">
      <span class="skeleton-line title"></span>
      <span class="skeleton-line medium"></span>
      <div class="loading-rows">
        <span v-for="index in 4" :key="index" class="skeleton-row"></span>
      </div>
    </div>

    <template v-else-if="menu && store">
      <section class="store-hero">
        <div class="store-copy">
          <p class="eyebrow">STORE MENU / {{ store.code }}</p>
          <h1>{{ store.name }}</h1>
          <p class="lede">{{ store.address }}</p>
          <div class="store-facts">
            <span>
              <small>营业时间</small>
              {{ store.openingHours }}
            </span>
            <span>
              <small>菜单规模</small>
              {{ menu.categories.length }} 类 · {{ productCount }} 项
            </span>
            <span>
              <small>坐标</small>
              {{ store.latitude.toFixed(4) }}, {{ store.longitude.toFixed(4) }}
            </span>
          </div>
        </div>

        <aside class="status-panel">
          <div class="status-panel-head">
            <span>STORE STATUS</span>
            <el-tag :type="statusType(store)" effect="dark" round>
              {{ statusLabel(store) }}
            </el-tag>
          </div>
          <p class="status-title">
            {{ store.orderable ? '现在可以接单。' : '菜单可看，暂不接单。' }}
          </p>
          <p class="status-copy">
            菜单查询只返回当前门店已上架商品。价格由服务端以
            <code>DECIMAL</code> 保存，并以字符串返回。
          </p>
          <div class="status-line">
            <span>门店编号</span>
            <strong>#{{ String(store.id).padStart(3, '0') }}</strong>
          </div>
          <div class="status-line">
            <span>接单状态</span>
            <strong>{{ store.acceptingOrders ? '愿意接单' : '暂停接单' }}</strong>
          </div>
        </aside>
      </section>

      <el-alert
        v-if="!store.orderable"
        class="store-alert"
        :type="alertType(store)"
        :closable="false"
        show-icon
        :title="alertTitle(store)"
      >
        仍然可以浏览菜单；购物车和下单不在 A1 范围内。
      </el-alert>

      <nav v-if="menu.categories.length > 0" class="category-nav" aria-label="菜单分类">
        <a
          v-for="(category, index) in menu.categories"
          :key="category.code"
          :href="`#category-${category.code}`"
          class="category-link"
        >
          <span>{{ String(index + 1).padStart(2, '0') }}</span>
          {{ category.name }}
          <small>{{ category.products.length }}</small>
        </a>
      </nav>

      <section
        v-for="(category, categoryIndex) in menu.categories"
        :id="`category-${category.code}`"
        :key="category.code"
        class="menu-group"
      >
        <div class="group-head">
          <span class="group-index">{{ String(categoryIndex + 1).padStart(2, '0') }}</span>
          <h2>{{ category.name }}</h2>
          <span class="group-line" aria-hidden="true"></span>
          <span class="group-count">{{ category.products.length }} 项</span>
        </div>

        <div class="product-list">
          <article
            v-for="(product, productIndex) in category.products"
            :key="product.id"
            class="product-row"
          >
            <div class="product-index">{{ String(productIndex + 1).padStart(2, '0') }}</div>
            <div class="product-copy">
              <div class="product-title">
                <h3>{{ product.name }}</h3>
                <code>{{ product.code }}</code>
              </div>
              <p>{{ product.description }}</p>
            </div>
            <div class="product-side">
              <div class="product-price">{{ formatPrice(product.price) }}</div>
              <button
                v-if="auth.isCustomer"
                class="add-button"
                type="button"
                @click="addToCart(product)"
              >
                加入购物车
              </button>
              <button
                v-else-if="!auth.user"
                class="add-button"
                type="button"
                @click="goLogin"
              >
                登录后加入
              </button>
              <span v-else class="role-hint">仅顾客可使用购物车</span>
            </div>
          </article>
        </div>
      </section>

      <el-empty
        v-if="menu.categories.length === 0"
        description="这家门店暂时没有上架商品"
      />

      <aside class="menu-note">
        <span>A1 / MENU CONTRACT</span>
        <p>
          <code>GET /api/stores/{{ store.id }}/menu</code>
          只读取当前门店、已上架商品，并按分类与排序字段稳定返回。
          价格仅用于展示，成交依据永远在服务端。
        </p>
      </aside>

      <aside v-if="auth.isCustomer && cart.hasItems" class="cart-bar">
        <div class="cart-bar-copy">
          <span>{{ cart.storeId === store.id ? '当前门店购物车' : `购物车属于 ${cart.storeName}` }}</span>
          <strong>{{ cart.totalQuantity }} 件 · {{ formatPrice(cart.subtotal) }}</strong>
        </div>
        <RouterLink class="cart-bar-link" to="/cart">查看购物车 →</RouterLink>
      </aside>
    </template>

    <section v-else-if="menuStore.error" class="error-block" aria-live="polite">
      <el-alert
        type="error"
        :closable="false"
        show-icon
        title="菜单加载失败"
      >
        <p>{{ menuStore.error.message }}</p>
        <p class="alert-meta">
          错误码 <code>{{ menuStore.error.code }}</code>
          <span v-if="menuStore.error.traceId">
            · traceId <code>{{ menuStore.error.traceId }}</code>
          </span>
        </p>
        <div class="error-actions">
          <el-button size="small" @click="reload">重新加载</el-button>
          <RouterLink class="back-link" to="/">返回选店</RouterLink>
        </div>
      </el-alert>
    </section>
  </main>
</template>

<style scoped>
.page {
  width: min(1180px, 100%);
  margin: 0 auto;
  padding: 0 32px 48px;
}

.back-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 24px 0 0;
}

.back-link {
  color: var(--muted-strong);
  font-size: 0.78rem;
  font-weight: 600;
  text-decoration: none;
  border-bottom: 1px solid transparent;
}

.back-link:hover,
.back-link:focus-visible {
  color: var(--ketchup);
  border-color: var(--ketchup);
}

.back-meta {
  color: var(--muted);
  font-family: var(--font-mono);
  font-size: 0.62rem;
  letter-spacing: 0.12em;
}

.store-hero {
  display: grid;
  grid-template-columns: minmax(0, 1.35fr) minmax(300px, 0.65fr);
  gap: clamp(32px, 6vw, 76px);
  align-items: stretch;
  padding: clamp(38px, 6vw, 66px) 0 clamp(38px, 5vw, 56px);
  border-bottom: 1.5px solid var(--ink);
}

.eyebrow {
  margin: 0;
  color: var(--muted);
  font-family: var(--font-mono);
  font-size: 0.68rem;
  font-weight: 600;
  letter-spacing: 0.14em;
  text-transform: uppercase;
}

h1 {
  margin: 15px 0 18px;
  font-family: var(--font-display);
  font-size: clamp(3.2rem, 7vw, 5.6rem);
  font-weight: 500;
  line-height: 0.95;
  letter-spacing: -0.065em;
}

.lede {
  max-width: 50ch;
  margin: 0 0 28px;
  color: var(--muted-strong);
  font-size: 0.95rem;
  line-height: 1.8;
}

.store-facts {
  display: flex;
  flex-wrap: wrap;
  gap: 20px 30px;
  padding-top: 18px;
  border-top: 1px solid var(--line-strong);
}

.store-facts span {
  display: grid;
  gap: 4px;
  font-family: var(--font-mono);
  font-size: 0.7rem;
}

.store-facts small {
  color: var(--muted);
  font-family: var(--font-sans);
  font-size: 0.62rem;
}

.status-panel {
  display: flex;
  padding: 22px;
  border: 1.5px solid var(--ink);
  background: var(--ink);
  color: var(--paper);
  flex-direction: column;
  box-shadow: 6px 6px 0 var(--mustard);
}

.status-panel-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding-bottom: 14px;
  border-bottom: 1px dashed rgba(244, 239, 228, 0.35);
  font-family: var(--font-mono);
  font-size: 0.62rem;
  letter-spacing: 0.12em;
}

.status-title {
  margin: 24px 0 9px;
  font-family: var(--font-display);
  font-size: 1.65rem;
  line-height: 1.25;
  letter-spacing: -0.03em;
}

.status-copy {
  margin: 0 0 22px;
  color: rgba(244, 239, 228, 0.72);
  font-size: 0.76rem;
  line-height: 1.75;
}

.status-copy code {
  background: rgba(244, 239, 228, 0.14);
  color: var(--mustard);
}

.status-line {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  padding-top: 11px;
  border-top: 1px solid rgba(244, 239, 228, 0.2);
  font-family: var(--font-mono);
  font-size: 0.66rem;
}

.status-line span {
  color: rgba(244, 239, 228, 0.56);
}

.store-alert {
  margin-top: 22px;
  border: 1.5px solid var(--ink);
  border-radius: 2px;
}

.category-nav {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
  padding: 26px 0 4px;
}

.category-link {
  display: inline-flex;
  align-items: center;
  gap: 7px;
  padding: 8px 12px;
  border: 1px solid var(--line-strong);
  border-radius: 999px;
  color: var(--muted-strong);
  font-size: 0.72rem;
  font-weight: 600;
  text-decoration: none;
}

.category-link span {
  color: var(--ketchup);
  font-family: var(--font-mono);
  font-size: 0.58rem;
}

.category-link small {
  display: grid;
  width: 18px;
  height: 18px;
  place-items: center;
  border-radius: 50%;
  background: var(--ink);
  color: var(--paper);
  font-family: var(--font-mono);
  font-size: 0.58rem;
}

.category-link:hover,
.category-link:focus-visible {
  background: var(--ink);
  border-color: var(--ink);
  color: var(--paper);
}

.menu-group {
  padding: 38px 0 8px;
  scroll-margin-top: 110px;
}

.group-head {
  display: flex;
  align-items: baseline;
  gap: 14px;
}

.group-index {
  color: var(--ketchup);
  font-family: var(--font-mono);
  font-size: 0.66rem;
  font-weight: 700;
}

.group-head h2 {
  margin: 0;
  font-family: var(--font-display);
  font-size: clamp(1.8rem, 3.4vw, 2.7rem);
  font-weight: 500;
  letter-spacing: -0.04em;
}

.group-line {
  height: 1px;
  background: var(--line-strong);
  flex: 1;
}

.group-count {
  color: var(--muted);
  font-family: var(--font-mono);
  font-size: 0.65rem;
}

.product-list {
  margin-top: 18px;
  border-top: 1px solid var(--ink);
}

.product-row {
  display: grid;
  grid-template-columns: 42px minmax(0, 1fr) auto;
  gap: 16px;
  align-items: center;
  padding: 20px 8px;
  border-bottom: 1px solid var(--line);
  transition: background-color 140ms ease;
}

.product-row:hover {
  background: rgba(255, 253, 248, 0.72);
}

.product-index {
  color: var(--muted);
  font-family: var(--font-mono);
  font-size: 0.64rem;
}

.product-title {
  display: flex;
  align-items: baseline;
  gap: 10px;
  flex-wrap: wrap;
}

.product-title h3 {
  margin: 0;
  font-family: var(--font-display);
  font-size: 1.38rem;
  font-weight: 500;
  letter-spacing: -0.03em;
}

.product-title code {
  color: var(--muted);
  font-size: 0.58rem;
}

.product-copy p {
  margin: 5px 0 0;
  color: var(--muted-strong);
  font-size: 0.78rem;
  line-height: 1.65;
}

.product-side {
  display: flex;
  align-items: flex-end;
  gap: 8px;
  flex-direction: column;
}

.product-price {
  font-family: var(--font-display);
  font-size: 1.4rem;
  font-variant-numeric: tabular-nums;
  letter-spacing: -0.02em;
  white-space: nowrap;
}

.add-button {
  padding: 5px 9px;
  border: 1px solid var(--ink);
  border-radius: 999px;
  background: transparent;
  color: var(--ink);
  font-size: 0.62rem;
  font-weight: 700;
  cursor: pointer;
  white-space: nowrap;
}

.add-button:hover,
.add-button:focus-visible {
  background: var(--ketchup);
  border-color: var(--ketchup);
  color: #fff8ec;
}

.role-hint {
  color: var(--muted);
  font-family: var(--font-mono);
  font-size: 0.56rem;
  white-space: nowrap;
}

.cart-bar {
  position: sticky;
  z-index: 10;
  bottom: 16px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 18px;
  margin: 26px 0 4px;
  padding: 14px 18px;
  border: 1.5px solid var(--ink);
  background: var(--ink);
  color: var(--paper);
  box-shadow: 5px 5px 0 var(--mustard);
}

.cart-bar-copy {
  display: grid;
  gap: 3px;
}

.cart-bar-copy span {
  color: rgba(244, 239, 228, 0.58);
  font-family: var(--font-mono);
  font-size: 0.58rem;
  letter-spacing: 0.08em;
}

.cart-bar-copy strong {
  font-family: var(--font-display);
  font-size: 1.15rem;
  font-weight: 500;
}

.cart-bar-link {
  color: var(--mustard);
  font-size: 0.72rem;
  font-weight: 700;
  text-decoration: none;
  white-space: nowrap;
}

.cart-bar-link:hover {
  color: var(--paper);
}

.menu-note {
  display: grid;
  grid-template-columns: 160px minmax(0, 1fr);
  gap: 20px;
  margin-top: 38px;
  padding-top: 22px;
  border-top: 1.5px solid var(--ink);
}

.menu-note span {
  color: var(--ketchup);
  font-family: var(--font-mono);
  font-size: 0.62rem;
  letter-spacing: 0.1em;
}

.menu-note p {
  max-width: 80ch;
  margin: 0;
  color: var(--muted);
  font-size: 0.76rem;
  line-height: 1.8;
}

.error-block {
  max-width: 720px;
  margin: 52px auto;
}

.error-block p {
  margin: 0;
}

.alert-meta {
  margin-top: 5px !important;
  font-size: 0.76rem;
}

.error-actions {
  display: flex;
  align-items: center;
  gap: 16px;
  margin-top: 12px;
}

.loading-block {
  padding: 72px 0;
}

.skeleton-line {
  display: block;
  width: 100%;
  height: 16px;
  margin-bottom: 13px;
  border-radius: 4px;
  background: linear-gradient(90deg, #e9e0d1 20%, #f5efe4 50%, #e9e0d1 80%);
  background-size: 200% 100%;
  animation: shimmer 1.4s infinite linear;
}

.skeleton-line.title {
  width: 45%;
  height: 58px;
}

.skeleton-line.medium {
  width: 30%;
}

.loading-rows {
  display: grid;
  gap: 12px;
  margin-top: 44px;
}

.skeleton-row {
  display: block;
  height: 68px;
  border-radius: 6px;
  background: rgba(255, 253, 248, 0.65);
}

@keyframes shimmer {
  to {
    background-position: -200% 0;
  }
}

@media (max-width: 900px) {
  .store-hero {
    grid-template-columns: 1fr;
  }

  .status-panel {
    max-width: 560px;
  }
}

@media (max-width: 620px) {
  .page {
    padding: 0 18px 34px;
  }

  .back-row {
    padding-top: 18px;
  }

  h1 {
    font-size: clamp(3rem, 16vw, 4.4rem);
  }

  .store-facts {
    display: grid;
    grid-template-columns: 1fr;
  }

  .product-row {
    grid-template-columns: 28px minmax(0, 1fr);
    gap: 10px;
  }

  .product-side {
    grid-column: 2;
    align-items: center;
    flex-direction: row;
    padding-top: 4px;
  }

  .product-price {
    font-size: 1.2rem;
  }

  .cart-bar {
    bottom: 8px;
  }

  .menu-note {
    grid-template-columns: 1fr;
    gap: 8px;
  }
}

@media (prefers-reduced-motion: reduce) {
  .product-row {
    transition: none;
  }

  .skeleton-line {
    animation: none;
  }
}
</style>
