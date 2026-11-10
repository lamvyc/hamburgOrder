<script setup lang="ts">
import { ElMessage, ElMessageBox } from 'element-plus'
import { computed, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { useOrderDetailStore } from '@/stores/orderDetail'
import type { OrderEvent, OrderStatus } from '@/types/order'
import { formatPrice } from '@/utils/format'

type TagType = 'success' | 'warning' | 'danger' | 'info' | 'primary'

const route = useRoute()
const store = useOrderDetailStore()
const order = computed(() => store.order)
const loading = computed(() => store.loading)
const error = computed(() => store.error)
const orderId = computed(() => Number(route.params.orderId))
const totalQuantity = computed(
  () => order.value?.items.reduce((sum, item) => sum + item.quantity, 0) ?? 0,
)
const headline = computed(() => {
  if (!order.value) return '订单详情。'
  if (order.value.status === 'COMPLETED') return '订单已完成。'
  if (order.value.status === 'READY') return '等待取餐。'
  if (order.value.status === 'MAKING') return '正在制作。'
  if (order.value.status === 'WAIT_MAKE') return '等待制作。'
  if (order.value.status === 'CANCELLED') return '订单已取消。'
  return '订单已创建。'
})

const statusCopy: Record<OrderStatus, string> = {
  WAIT_PAY: '等待支付',
  WAIT_MAKE: '等待制作',
  MAKING: '制作中',
  READY: '待取餐',
  COMPLETED: '已完成',
  CANCELLED: '已取消',
}

const eventCopy: Record<string, string> = {
  CREATED: '订单创建',
  PAYMENT_SUCCEEDED: '支付成功',
  MAKING_STARTED: '开始制作',
  READY_FOR_PICKUP: '待取餐',
  COMPLETED: '订单完成',
  CANCELLED: '订单取消',
}

function statusLabel(value: OrderStatus): string {
  return statusCopy[value]
}

function statusType(value: OrderStatus): TagType {
  if (value === 'WAIT_PAY') return 'warning'
  if (value === 'CANCELLED') return 'info'
  if (value === 'COMPLETED') return 'success'
  return 'primary'
}

function formatDateTime(value: string | undefined): string {
  if (!value) return '—'
  return new Date(value).toLocaleString('zh-CN', { hour12: false })
}

function eventLabel(event: OrderEvent): string {
  return eventCopy[event.eventType] ?? event.eventType
}

async function load(): Promise<void> {
  await store.load(orderId.value)
}

async function cancel(): Promise<void> {
  try {
    await ElMessageBox.confirm(
      '取消会释放预占库存，并使订单进入 CANCELLED。此操作不可撤销。',
      '取消订单？',
      {
        confirmButtonText: '确认取消',
        cancelButtonText: '保留订单',
        type: 'warning',
      },
    )
    await store.cancel(orderId.value)
    ElMessage.success('订单已取消，库存已释放')
  } catch {
    // Cancel is not an error; a real conflict is in store.error.
  }
}

async function pay(): Promise<void> {
  try {
    await ElMessageBox.confirm(
      '这是 A4 模拟支付，不会真实扣款。支付成功后订单进入 WAIT_MAKE，reserved 库存被提交。',
      '模拟支付？',
      {
        confirmButtonText: '确认支付',
        cancelButtonText: '取消',
        type: 'warning',
      },
    )
    await store.pay(orderId.value)
    ElMessage.success('模拟支付成功')
  } catch {
    // Cancel is not an error; a real payment error is in store.error.
  }
}

onMounted(() => {
  void load()
})
</script>

<template>
  <main class="page">
    <div class="back-row">
      <RouterLink class="back-link" to="/">← 返回选店</RouterLink>
      <span>ORDER / B1</span>
    </div>

    <div v-if="loading" class="loading-block">
      <span class="skeleton-line title"></span>
      <span class="skeleton-line medium"></span>
    </div>

    <template v-else-if="order">
      <section class="hero">
        <div>
          <p class="eyebrow">ORDER DETAIL / {{ order.orderNo }}</p>
          <h1>{{ headline }}</h1>
          <p class="lede">
            {{ order.storeName }} · {{ totalQuantity }} 件商品。
            {{ order.status === 'WAIT_PAY'
              ? '库存已经预占；可以使用 A4 模拟支付进入制作队列。'
              : '状态转换由后端条件更新与事件轨迹共同保证。' }}
          </p>
          <div class="hero-tags">
            <el-tag :type="statusType(order.status)" effect="dark" round>
              {{ statusLabel(order.status) }}
            </el-tag>
            <span class="order-no">{{ order.orderNo }}</span>
          </div>
        </div>

        <aside class="total-card">
          <span>TOTAL / {{ order.currency }}</span>
          <strong>{{ formatPrice(order.totalAmount) }}</strong>
          <small>以服务端成交快照为准</small>
        </aside>
      </section>

      <el-alert
        v-if="order.status === 'WAIT_PAY'"
        class="pay-alert"
        type="info"
        :closable="false"
        show-icon
        title="等待支付（A4 模拟支付）"
      >
        订单在 {{ formatDateTime(order.expiresAt) }} 前有效。
        模拟支付不会真实扣款；支付成功后 reserved 库存提交，订单进入 WAIT_MAKE。
        <div class="pay-actions">
          <el-button
            class="pay-button"
            type="primary"
            :loading="store.paying"
            :disabled="store.paying || store.cancelling"
            @click="pay"
          >
            模拟支付
          </el-button>
          <el-button
            class="cancel-button"
            :loading="store.cancelling"
            :disabled="store.paying || store.cancelling"
            @click="cancel"
          >
            取消订单
          </el-button>
          <span>取消会释放 reserved 与 available；超时扫描在后台执行。</span>
        </div>
      </el-alert>

      <el-alert
        v-if="store.error && order"
        class="pay-alert"
        type="error"
        :closable="false"
        show-icon
        title="订单操作失败"
      >
        <p>{{ store.error.message }}</p>
        <p class="alert-meta">
          错误码 <code>{{ store.error.code }}</code>
          <span v-if="store.error.traceId">· traceId <code>{{ store.error.traceId }}</code></span>
        </p>
      </el-alert>

      <section class="order-section">
        <div class="section-heading">
          <div>
            <p class="eyebrow">ITEMS SNAPSHOT</p>
            <h2>订单明细</h2>
          </div>
          <div class="meta">
            <span>创建时间 {{ formatDateTime(order.createdAt) }}</span>
            <span>截止时间 {{ formatDateTime(order.expiresAt) }}</span>
          </div>
        </div>

        <div class="item-table">
          <article v-for="item in order.items" :key="item.productId" class="item-row">
            <div class="item-copy">
              <div class="item-title">
                <h3>{{ item.productName }}</h3>
                <code>{{ item.productCode }}</code>
              </div>
              <p>成交单价 {{ formatPrice(item.unitPrice) }}</p>
            </div>
            <span class="quantity">× {{ item.quantity }}</span>
            <strong class="line-total">{{ formatPrice(item.lineAmount) }}</strong>
          </article>
        </div>

        <div class="summary">
          <div class="summary-breakdown">
            <span v-if="order.discountAmount !== '0.00'">
              商品小计 {{ formatPrice((Number(order.totalAmount) + Number(order.discountAmount)).toFixed(2)) }}
            </span>
            <span v-if="order.discountAmount !== '0.00'" class="discount-line">
              优惠券 − {{ formatPrice(order.discountAmount) }}
            </span>
            <span>{{ order.discountAmount === '0.00' ? '服务端计算小计' : '应付' }}</span>
          </div>
          <strong>{{ formatPrice(order.totalAmount) }}</strong>
        </div>
      </section>

      <section class="timeline">
        <div class="section-heading">
          <div>
            <p class="eyebrow">EVENT TRAIL</p>
            <h2>状态轨迹</h2>
          </div>
          <p class="meta">操作者、时间、前后状态与原因</p>
        </div>
        <div class="timeline-list">
          <article v-for="event in store.events" :key="event.id" class="timeline-item">
            <span class="timeline-time">{{ formatDateTime(event.createdAt) }}</span>
            <div>
              <strong>{{ eventLabel(event) }}</strong>
              <p>
                {{ event.fromStatus ?? '—' }} → {{ event.toStatus }}
                <span v-if="event.operatorName">· {{ event.operatorName }}</span>
                <span v-if="event.reason">· {{ event.reason }}</span>
              </p>
            </div>
          </article>
        </div>
      </section>

      <aside class="scope-note">
        <span>B1 / COUPON LIFECYCLE</span>
        <p>
          下单锁券、支付核销、取消/超时释放；优惠券状态与订单、库存在同一事务边界内。
          员工只能操作所属门店的 WAIT_MAKE → MAKING → READY → COMPLETED。
        </p>
      </aside>
    </template>

    <section v-else-if="error" class="error-block" aria-live="polite">
      <el-alert type="error" :closable="false" show-icon title="订单加载失败">
        <p>{{ error.message }}</p>
        <p class="alert-meta">
          错误码 <code>{{ error.code }}</code>
          <span v-if="error.traceId">· traceId <code>{{ error.traceId }}</code></span>
        </p>
        <el-button size="small" @click="load">重新加载</el-button>
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
  justify-content: space-between;
  padding-top: 24px;
  color: var(--muted);
  font-family: var(--font-mono);
  font-size: 0.62rem;
  letter-spacing: 0.1em;
}

.back-link {
  color: var(--muted-strong);
  font-family: var(--font-sans);
  font-size: 0.78rem;
  font-weight: 600;
  text-decoration: none;
  border-bottom: 1px solid transparent;
}

.back-link:hover {
  color: var(--ketchup);
  border-color: var(--ketchup);
}

.hero {
  display: grid;
  grid-template-columns: minmax(0, 1.35fr) minmax(260px, 0.65fr);
  gap: clamp(30px, 6vw, 72px);
  align-items: start;
  padding: clamp(42px, 6vw, 72px) 0 40px;
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
  margin: 0 0 22px;
  color: var(--muted-strong);
  font-size: 0.94rem;
  line-height: 1.85;
}

.hero-tags {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
}

.order-no {
  color: var(--muted);
  font-family: var(--font-mono);
  font-size: 0.68rem;
}

.total-card {
  display: grid;
  justify-items: start;
  padding: 24px;
  border: 1.5px solid var(--ink);
  background: var(--ink);
  color: var(--paper);
  box-shadow: 6px 6px 0 var(--mustard);
}

.total-card span,
.total-card small {
  color: rgba(244, 239, 228, 0.58);
  font-family: var(--font-mono);
  font-size: 0.6rem;
  letter-spacing: 0.1em;
}

.total-card strong {
  margin: 18px 0 7px;
  font-family: var(--font-display);
  font-size: 2.2rem;
  font-weight: 500;
}

.pay-alert {
  margin-top: 22px;
  border: 1.5px solid var(--ink);
  border-radius: 2px;
}

.pay-alert p {
  margin: 0 0 4px;
}

.pay-actions {
  display: flex;
  align-items: center;
  gap: 14px;
  margin-top: 12px;
  flex-wrap: wrap;
}

.pay-actions span {
  color: var(--muted);
  font-size: 0.7rem;
}

.pay-button {
  border: 1px solid var(--ink);
  border-radius: 4px;
  background: var(--ink);
  box-shadow: 2px 2px 0 var(--mustard);
  font-weight: 700;
}

.pay-button:hover,
.pay-button:focus-visible {
  background: var(--ketchup);
  border-color: var(--ink);
}

.cancel-button {
  border: 1px solid var(--line-strong);
  background: #fffdf8;
  color: var(--muted-strong);
}

.cancel-button:hover,
.cancel-button:focus-visible {
  border-color: var(--ketchup);
  color: var(--ketchup);
}

.alert-meta {
  font-size: 0.72rem;
}

.order-section {
  padding: 42px 0;
  border-bottom: 1.5px solid var(--ink);
}

.section-heading {
  display: flex;
  align-items: end;
  justify-content: space-between;
  gap: 20px;
}

h2 {
  margin: 8px 0 0;
  font-family: var(--font-display);
  font-size: clamp(2rem, 4vw, 3rem);
  font-weight: 500;
  letter-spacing: -0.045em;
}

.meta {
  display: grid;
  gap: 3px;
  color: var(--muted);
  font-family: var(--font-mono);
  font-size: 0.62rem;
  text-align: right;
}

.item-table {
  margin-top: 24px;
  border-top: 1.5px solid var(--ink);
}

.item-row {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 50px 110px;
  gap: 16px;
  align-items: center;
  padding: 18px 6px;
  border-bottom: 1px solid var(--line);
}

.item-title {
  display: flex;
  align-items: baseline;
  gap: 9px;
  flex-wrap: wrap;
}

.item-title h3 {
  margin: 0;
  font-family: var(--font-display);
  font-size: 1.3rem;
  font-weight: 500;
}

.item-title code {
  color: var(--muted);
  font-size: 0.58rem;
}

.item-copy p {
  margin: 4px 0 0;
  color: var(--muted);
  font-size: 0.68rem;
}

.quantity {
  color: var(--muted-strong);
  font-family: var(--font-mono);
  font-size: 0.72rem;
  text-align: center;
}

.line-total {
  font-family: var(--font-display);
  font-size: 1.25rem;
  font-weight: 500;
  text-align: right;
  white-space: nowrap;
}

.summary {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 20px;
  margin-top: 22px;
  padding: 18px 20px;
  border: 1.5px solid var(--ink);
  background: #fffdf8;
  box-shadow: 4px 4px 0 var(--mustard);
}

.summary span {
  color: var(--muted);
  font-family: var(--font-mono);
  font-size: 0.63rem;
}

.summary-breakdown {
  display: grid;
  gap: 5px;
}

.discount-line {
  color: var(--ketchup);
}

.summary strong {
  font-family: var(--font-display);
  font-size: 1.8rem;
  font-weight: 500;
}

.timeline {
  padding: 38px 0;
  border-bottom: 1.5px solid var(--ink);
}

.timeline-list {
  display: grid;
  margin-top: 20px;
  border-top: 1px solid var(--ink);
}

.timeline-item {
  display: grid;
  grid-template-columns: 150px minmax(0, 1fr);
  gap: 18px;
  padding: 14px 4px;
  border-bottom: 1px solid var(--line);
}

.timeline-time {
  color: var(--muted);
  font-family: var(--font-mono);
  font-size: 0.62rem;
}

.timeline-item strong {
  font-family: var(--font-display);
  font-size: 1.05rem;
  font-weight: 500;
}

.timeline-item p {
  margin: 3px 0 0;
  color: var(--muted);
  font-family: var(--font-mono);
  font-size: 0.62rem;
}

.scope-note {
  display: grid;
  grid-template-columns: 180px minmax(0, 1fr);
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

.error-block {
  max-width: 720px;
  margin: 52px auto;
}

.error-block p {
  margin: 0 0 6px;
}

.alert-meta {
  font-size: 0.72rem;
}

.loading-block {
  padding: 80px 0;
}

.skeleton-line {
  display: block;
  width: 100%;
  height: 18px;
  margin-bottom: 12px;
  border-radius: 4px;
  background: linear-gradient(90deg, #e9e0d1 20%, #f5efe4 50%, #e9e0d1 80%);
  background-size: 200% 100%;
  animation: shimmer 1.4s infinite linear;
}

.skeleton-line.title {
  width: 46%;
  height: 58px;
}

.skeleton-line.medium {
  width: 28%;
}

@keyframes shimmer {
  to {
    background-position: -200% 0;
  }
}

@media (max-width: 900px) {
  .hero {
    grid-template-columns: 1fr;
  }

  .total-card {
    max-width: 420px;
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

  .meta {
    text-align: left;
  }

  .item-row {
    grid-template-columns: minmax(0, 1fr) auto;
  }

  .line-total {
    grid-column: 1 / -1;
    text-align: left;
  }

  .timeline-item {
    grid-template-columns: 1fr;
    gap: 4px;
  }

  .scope-note {
    grid-template-columns: 1fr;
    gap: 8px;
  }
}

@media (prefers-reduced-motion: reduce) {
  .skeleton-line {
    animation: none;
  }
}
</style>
