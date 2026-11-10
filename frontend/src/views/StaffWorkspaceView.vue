<script setup lang="ts">
import { ElMessage } from 'element-plus'
import { computed, onMounted, ref } from 'vue'
import { fetchStaffWorkspace } from '@/api/auth'
import { toUiError } from '@/api/http'
import { completeOrder, fetchStaffOrders, markOrderReady, startOrder } from '@/api/staff'
import { useAuthStore } from '@/stores/auth'
import type { StaffStore } from '@/types/auth'
import type { Order, OrderStatus } from '@/types/order'
import type { UiError } from '@/types/system'
import { formatPrice } from '@/utils/format'

type TagType = 'success' | 'warning' | 'danger' | 'info' | 'primary'

const auth = useAuthStore()
const stores = ref<StaffStore[]>([])
const selectedStoreId = ref<number | null>(null)
const orders = ref<Order[]>([])
const loading = ref(false)
const actionId = ref<number | null>(null)
const error = ref<UiError | null>(null)

const selectedStore = computed(
  () => stores.value.find((store) => store.id === selectedStoreId.value) ?? null,
)

const statusCopy: Record<OrderStatus, string> = {
  WAIT_PAY: '等待支付',
  WAIT_MAKE: '等待制作',
  MAKING: '制作中',
  READY: '待取餐',
  COMPLETED: '已完成',
  CANCELLED: '已取消',
}

function statusLabel(status: OrderStatus): string {
  return statusCopy[status]
}

function statusType(status: OrderStatus): TagType {
  if (status === 'WAIT_MAKE') return 'warning'
  if (status === 'MAKING') return 'primary'
  if (status === 'READY') return 'success'
  return 'info'
}

function actionLabel(status: OrderStatus): string {
  if (status === 'WAIT_MAKE') return '开始制作'
  if (status === 'MAKING') return '标记待取餐'
  if (status === 'READY') return '完成订单'
  return '不可操作'
}

function formatTime(value: string): string {
  return new Date(value).toLocaleString('zh-CN', { hour12: false })
}

async function loadWorkspace(): Promise<void> {
  loading.value = true
  error.value = null
  try {
    const workspace = await fetchStaffWorkspace()
    stores.value = workspace.stores
    if (workspace.stores.length > 0) {
      selectedStoreId.value = workspace.stores[0].id
      await loadOrders()
    }
  } catch (cause) {
    error.value = toUiError(cause)
  } finally {
    loading.value = false
  }
}

async function loadOrders(): Promise<void> {
  if (!selectedStoreId.value) return
  loading.value = true
  error.value = null
  try {
    orders.value = await fetchStaffOrders(selectedStoreId.value)
  } catch (cause) {
    error.value = toUiError(cause)
    orders.value = []
  } finally {
    loading.value = false
  }
}

async function selectStore(storeId: number): Promise<void> {
  if (selectedStoreId.value === storeId) return
  selectedStoreId.value = storeId
  await loadOrders()
}

async function advance(order: Order): Promise<void> {
  actionId.value = order.id
  error.value = null
  try {
    let updated: Order
    if (order.status === 'WAIT_MAKE') {
      updated = await startOrder(order.id)
    } else if (order.status === 'MAKING') {
      updated = await markOrderReady(order.id)
    } else if (order.status === 'READY') {
      updated = await completeOrder(order.id)
    } else {
      return
    }

    if (updated.status === 'COMPLETED') {
      orders.value = orders.value.filter((item) => item.id !== order.id)
      ElMessage.success(`${updated.orderNo} 已完成`)
    } else {
      orders.value = orders.value.map((item) => (item.id === updated.id ? updated : item))
      ElMessage.success(`${updated.orderNo} → ${statusLabel(updated.status)}`)
    }
  } catch (cause) {
    error.value = toUiError(cause)
  } finally {
    actionId.value = null
  }
}

onMounted(() => {
  void loadWorkspace()
})
</script>

<template>
  <main class="page">
    <section class="hero">
      <div>
        <p class="eyebrow">STAGE A4 / STAFF WORKBENCH</p>
        <h1>员工工作台</h1>
        <p class="lede">
          只显示所属门店的活跃订单。店员操作由后端按 membership
          与订单状态校验，按钮只是入口，不是权限。
        </p>
      </div>
      <aside class="identity-card">
        <span>当前登录</span>
        <strong>{{ auth.user?.displayName }}</strong>
        <small>{{ auth.user?.username }} · STAFF</small>
        <el-tag type="success" effect="dark" round>已认证</el-tag>
      </aside>
    </section>

    <section class="workbench" :aria-busy="loading">
      <div class="section-heading">
        <div>
          <p class="eyebrow">AUTHORIZED STORES</p>
          <h2>门店队列</h2>
        </div>
        <p class="section-meta">WAIT_MAKE → MAKING → READY → COMPLETED<br />取消与超时在 A5</p>
      </div>

      <el-alert
        v-if="error"
        class="work-alert"
        type="error"
        :closable="false"
        show-icon
        title="工作台操作失败"
      >
        <p>{{ error.message }}</p>
        <p class="alert-meta">
          错误码 <code>{{ error.code }}</code>
          <span v-if="error.traceId">· traceId <code>{{ error.traceId }}</code></span>
        </p>
        <el-button size="small" @click="loadWorkspace">重新加载</el-button>
      </el-alert>

      <div v-if="stores.length > 0" class="store-tabs" role="tablist" aria-label="选择门店">
        <button
          v-for="store in stores"
          :key="store.id"
          class="store-tab"
          :class="{ active: selectedStoreId === store.id }"
          type="button"
          role="tab"
          :aria-selected="selectedStoreId === store.id"
          @click="selectStore(store.id)"
        >
          <span>{{ store.code }}</span>
          <strong>{{ store.name }}</strong>
          <small>{{ store.acceptingOrders ? '接单中' : '暂停接单' }}</small>
        </button>
      </div>

      <div v-if="loading" class="loading-card">正在读取门店队列…</div>

      <template v-else-if="selectedStore">
        <div v-if="orders.length > 0" class="order-list">
          <article v-for="order in orders" :key="order.id" class="work-order">
            <div class="order-head">
              <div>
                <span class="order-no">{{ order.orderNo }}</span>
                <span class="order-time">{{ formatTime(order.createdAt) }}</span>
              </div>
              <el-tag :type="statusType(order.status)" effect="dark" round>
                {{ statusLabel(order.status) }}
              </el-tag>
            </div>

            <ul class="order-items">
              <li v-for="item in order.items" :key="item.productId">
                <span>{{ item.quantity }} × {{ item.productName }}</span>
                <strong>{{ formatPrice(item.lineAmount) }}</strong>
              </li>
            </ul>

            <div class="order-foot">
              <span>合计 {{ formatPrice(order.totalAmount) }}</span>
              <el-button
                class="action-button"
                type="primary"
                :loading="actionId === order.id"
                :disabled="actionId !== null"
                @click="advance(order)"
              >
                {{ actionLabel(order.status) }}
              </el-button>
            </div>
          </article>
        </div>

        <el-empty
          v-else
          :description="`${selectedStore.name} 当前没有待处理订单`"
        />
      </template>

      <el-empty v-else-if="!loading && !error" description="当前账号尚未绑定门店" />
    </section>

    <aside class="scope-note">
      <span>A4 / STATE MACHINE</span>
      <p>
        每次转换使用条件更新并检查影响行数，操作者、前后状态与原因写入
        order_events。重复点击或非法跳转返回 409，不会靠后端抛 500 掩盖。
      </p>
    </aside>
  </main>
</template>

<style scoped>
.page {
  width: min(1180px, 100%);
  margin: 0 auto;
  padding: 0 32px 48px;
}

.hero {
  display: grid;
  grid-template-columns: minmax(0, 1.3fr) minmax(280px, 0.7fr);
  gap: clamp(30px, 6vw, 70px);
  align-items: start;
  padding: clamp(50px, 7vw, 78px) 0 42px;
  border-bottom: 1.5px solid var(--ink);
}

.eyebrow {
  margin: 0;
  color: var(--muted);
  font-family: var(--font-mono);
  font-size: 0.66rem;
  font-weight: 600;
  letter-spacing: 0.13em;
}

h1 {
  margin: 14px 0 18px;
  font-family: var(--font-display);
  font-size: clamp(3.4rem, 7vw, 5.4rem);
  font-weight: 500;
  line-height: 0.95;
  letter-spacing: -0.06em;
}

.lede {
  max-width: 56ch;
  margin: 0;
  color: var(--muted-strong);
  font-size: 0.94rem;
  line-height: 1.85;
}

.identity-card {
  display: grid;
  gap: 6px;
  justify-items: start;
  padding: 23px;
  border: 1.5px solid var(--ink);
  background: var(--ink);
  color: var(--paper);
  box-shadow: 6px 6px 0 var(--mustard);
}

.identity-card > span {
  color: rgba(244, 239, 228, 0.55);
  font-family: var(--font-mono);
  font-size: 0.6rem;
  letter-spacing: 0.12em;
}

.identity-card strong {
  margin-top: 12px;
  font-family: var(--font-display);
  font-size: 1.7rem;
  font-weight: 500;
}

.identity-card small {
  margin-bottom: 12px;
  color: rgba(244, 239, 228, 0.65);
  font-family: var(--font-mono);
  font-size: 0.65rem;
}

.workbench {
  padding: 46px 0;
  border-bottom: 1.5px solid var(--ink);
}

.section-heading {
  display: flex;
  align-items: end;
  justify-content: space-between;
  gap: 24px;
}

h2 {
  margin: 8px 0 0;
  font-family: var(--font-display);
  font-size: clamp(2rem, 4vw, 3rem);
  font-weight: 500;
  letter-spacing: -0.045em;
}

.section-meta {
  margin: 0;
  color: var(--muted);
  font-family: var(--font-mono);
  font-size: 0.64rem;
  line-height: 1.8;
  text-align: right;
}

.work-alert {
  margin-top: 22px;
  border: 1.5px solid var(--ink);
  border-radius: 2px;
}

.work-alert p {
  margin: 0 0 5px;
}

.alert-meta {
  font-size: 0.72rem;
}

.store-tabs {
  display: flex;
  gap: 10px;
  flex-wrap: wrap;
  margin-top: 26px;
}

.store-tab {
  display: grid;
  gap: 3px;
  min-width: 190px;
  padding: 13px 15px;
  border: 1.5px solid var(--line-strong);
  background: #fffdf8;
  color: var(--ink);
  text-align: left;
  cursor: pointer;
}

.store-tab span {
  color: var(--ketchup);
  font-family: var(--font-mono);
  font-size: 0.58rem;
  letter-spacing: 0.08em;
}

.store-tab strong {
  font-family: var(--font-display);
  font-size: 1.15rem;
  font-weight: 500;
}

.store-tab small {
  color: var(--muted);
  font-size: 0.65rem;
}

.store-tab.active {
  border-color: var(--ink);
  background: var(--ink);
  color: var(--paper);
  box-shadow: 3px 3px 0 var(--mustard);
}

.store-tab.active small {
  color: rgba(244, 239, 228, 0.62);
}

.loading-card {
  margin-top: 24px;
  padding: 40px;
  border: 1.5px dashed var(--line-strong);
  color: var(--muted);
  font-family: var(--font-mono);
  font-size: 0.72rem;
  text-align: center;
}

.order-list {
  display: grid;
  gap: 18px;
  margin-top: 24px;
}

.work-order {
  padding: 20px;
  border: 1.5px solid var(--ink);
  background: #fffdf8;
  box-shadow: 4px 4px 0 var(--green);
}

.order-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 14px;
  padding-bottom: 12px;
  border-bottom: 1px solid var(--line);
}

.order-head > div {
  display: flex;
  align-items: baseline;
  gap: 12px;
  flex-wrap: wrap;
}

.order-no {
  font-family: var(--font-mono);
  font-size: 0.7rem;
  font-weight: 700;
}

.order-time {
  color: var(--muted);
  font-family: var(--font-mono);
  font-size: 0.6rem;
}

.order-items {
  display: grid;
  gap: 8px;
  margin: 16px 0;
  padding: 0;
  list-style: none;
}

.order-items li {
  display: flex;
  justify-content: space-between;
  gap: 14px;
  color: var(--muted-strong);
  font-size: 0.78rem;
}

.order-items strong {
  font-family: var(--font-mono);
  font-size: 0.7rem;
}

.order-foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding-top: 12px;
  border-top: 1px solid var(--line);
  font-family: var(--font-mono);
  font-size: 0.7rem;
}

.action-button {
  border: 1px solid var(--ink);
  border-radius: 4px;
  background: var(--ink);
  font-weight: 700;
}

.action-button:hover {
  background: var(--ketchup);
  border-color: var(--ink);
}

.scope-note {
  display: grid;
  grid-template-columns: 150px minmax(0, 1fr);
  gap: 20px;
  padding-top: 24px;
}

.scope-note span {
  color: var(--ketchup);
  font-family: var(--font-mono);
  font-size: 0.62rem;
  letter-spacing: 0.1em;
}

.scope-note p {
  max-width: 80ch;
  margin: 0;
  color: var(--muted);
  font-size: 0.75rem;
  line-height: 1.8;
}

@media (max-width: 900px) {
  .hero {
    grid-template-columns: 1fr;
  }

  .identity-card {
    max-width: 520px;
  }
}

@media (max-width: 620px) {
  .page {
    padding: 0 18px 34px;
  }

  .section-heading {
    align-items: flex-start;
    flex-direction: column;
    gap: 8px;
  }

  .section-meta {
    text-align: left;
  }

  .order-head,
  .order-foot {
    align-items: flex-start;
    flex-direction: column;
  }

  .action-button {
    width: 100%;
  }

  .scope-note {
    grid-template-columns: 1fr;
    gap: 8px;
  }
}
</style>
