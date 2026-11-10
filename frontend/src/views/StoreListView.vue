<script setup lang="ts">
import { computed, onMounted } from 'vue'
import { useStoreListStore } from '@/stores/storeList'
import type { Store } from '@/types/catalog'

type TagType = 'success' | 'warning' | 'danger' | 'info' | 'primary'

const storeList = useStoreListStore()

const countsVisible = computed(
  () => !storeList.error && (storeList.loaded || storeList.items.length > 0),
)
const storeCountLabel = computed(() => (countsVisible.value ? storeList.items.length : '—'))
const orderableCountLabel = computed(() =>
  countsVisible.value ? storeList.orderableCount : '—',
)

onMounted(() => {
  void storeList.load()
})

function statusLabel(store: Store): string {
  if (store.businessStatus === 'CLOSED') return '休息中'
  if (!store.acceptingOrders) return '暂停接单'
  return '接单中'
}

function statusType(store: Store): TagType {
  if (store.businessStatus === 'CLOSED') return 'info'
  if (!store.acceptingOrders) return 'warning'
  return 'success'
}

function storeIndex(index: number): string {
  return String(index + 1).padStart(2, '0')
}

function coordinate(value: number): string {
  return value.toFixed(4)
}
</script>

<template>
  <main class="page">
    <section class="hero">
      <div class="hero-copy">
        <p class="eyebrow">STAGE A1 / STORE SELECTION</p>
        <h1>今天，<br /><span>去哪家店？</span></h1>
        <p class="lede">
          三家门店，三种营业节奏。先选一家，再浏览它自己的菜单；
          门店状态、接单状态与商品归属都由后端真实查询。
        </p>
        <div class="hero-stats" aria-live="polite">
          <div>
            <strong>{{ storeCountLabel }}</strong>
            <span>家门店</span>
          </div>
          <div>
            <strong>{{ orderableCountLabel }}</strong>
            <span>家接单中</span>
          </div>
        </div>
      </div>

      <aside class="hero-card">
        <div class="card-heading">
          <span>STORE INDEX</span>
          <span>A1</span>
        </div>
        <p class="hero-card-title">营业与接单是两件事。</p>
        <p class="hero-card-copy">
          门店可能正在营业，却因为繁忙暂停接单；也可能已经休息。
          页面不把两个状态合并成一个“开/关”。
        </p>
        <div class="data-row">
          <span>接口</span>
          <code>GET /api/stores</code>
        </div>
        <div class="data-row">
          <span>数据源</span>
          <strong>MySQL 8.4 · Flyway managed</strong>
        </div>
      </aside>
    </section>

    <section class="store-section" :aria-busy="storeList.loading">
      <div class="section-heading">
        <div>
          <p class="eyebrow">SELECT A COUNTER</p>
          <h2>门店列表</h2>
        </div>
        <p class="section-meta">选择门店不会切换购物车<br />购物车要到 A2 才接入</p>
      </div>

      <el-alert
        v-if="storeList.error"
        class="store-alert"
        type="error"
        :closable="false"
        show-icon
        title="门店列表加载失败"
      >
        <p>{{ storeList.error.message }}</p>
        <p class="alert-meta">
          错误码 <code>{{ storeList.error.code }}</code>
          <span v-if="storeList.error.traceId">
            · traceId <code>{{ storeList.error.traceId }}</code>
          </span>
        </p>
        <el-button class="retry-button" size="small" @click="storeList.refresh">
          重新加载
        </el-button>
      </el-alert>

      <div v-if="storeList.loading" class="store-grid" aria-label="正在加载门店">
        <div v-for="index in 3" :key="index" class="store-card skeleton-card">
          <span class="skeleton-line short"></span>
          <span class="skeleton-line title"></span>
          <span class="skeleton-line"></span>
          <span class="skeleton-line medium"></span>
        </div>
      </div>

      <div v-else-if="storeList.items.length > 0" class="store-grid">
        <RouterLink
          v-for="(store, index) in storeList.items"
          :key="store.id"
          :to="{ name: 'store-menu', params: { storeId: store.id } }"
          class="store-card"
          :class="[
            `accent-${index % 3}`,
            { 'is-unavailable': !store.orderable },
          ]"
          :aria-label="`查看${store.name}菜单`"
        >
          <div class="store-top">
            <span class="store-number">{{ storeIndex(index) }}</span>
            <el-tag :type="statusType(store)" effect="dark" round>
              {{ statusLabel(store) }}
            </el-tag>
          </div>

          <h3>{{ store.name }}</h3>
          <p class="store-address">{{ store.address }}</p>

          <dl class="store-meta">
            <div>
              <dt>营业时间</dt>
              <dd>{{ store.openingHours }}</dd>
            </div>
            <div>
              <dt>坐标</dt>
              <dd>{{ coordinate(store.latitude) }}, {{ coordinate(store.longitude) }}</dd>
            </div>
          </dl>

          <div class="store-footer">
            <span>{{ store.orderable ? '现在接单' : '可浏览菜单' }}</span>
            <span class="arrow" aria-hidden="true">→</span>
          </div>
        </RouterLink>
      </div>

      <el-empty
        v-else-if="!storeList.error"
        description="暂时没有可展示的门店"
      />
    </section>

    <section class="scope-note">
      <span class="scope-index">A1 / READ ONLY</span>
      <p>
        本轮只实现门店与菜单的只读查询。登录、购物车、库存与下单分别留到 A2、A3；
        页面不会提前放置假的“加入购物车”按钮。
      </p>
    </section>
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
  grid-template-columns: minmax(0, 1.35fr) minmax(300px, 0.65fr);
  gap: clamp(34px, 6vw, 82px);
  align-items: start;
  padding: clamp(54px, 8vw, 92px) 0 clamp(48px, 6vw, 68px);
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
  margin: 14px 0 26px;
  font-family: var(--font-display);
  font-size: clamp(3.5rem, 7.4vw, 6.2rem);
  font-weight: 500;
  line-height: 0.92;
  letter-spacing: -0.065em;
}

h1 span {
  color: var(--ketchup);
  font-style: italic;
}

.lede {
  max-width: 54ch;
  margin: 0 0 30px;
  color: var(--muted-strong);
  font-size: clamp(0.98rem, 1.2vw, 1.08rem);
  line-height: 1.85;
}

.hero-stats {
  display: flex;
  gap: 30px;
  padding-top: 22px;
  border-top: 1px solid var(--line-strong);
}

.hero-stats div {
  display: flex;
  align-items: baseline;
  gap: 7px;
}

.hero-stats strong {
  color: var(--ketchup);
  font-family: var(--font-display);
  font-size: 2.4rem;
  font-weight: 500;
  line-height: 1;
}

.hero-stats span {
  color: var(--muted);
  font-size: 0.74rem;
}

.hero-card {
  padding: 23px;
  border: 1.5px solid var(--ink);
  background: #fffdf7;
  box-shadow: 7px 7px 0 var(--mustard);
}

.card-heading {
  display: flex;
  justify-content: space-between;
  padding-bottom: 13px;
  border-bottom: 1px dashed var(--line-strong);
  font-family: var(--font-mono);
  font-size: 0.62rem;
  letter-spacing: 0.13em;
}

.hero-card-title {
  margin: 22px 0 10px;
  font-family: var(--font-display);
  font-size: 1.55rem;
  line-height: 1.2;
  letter-spacing: -0.03em;
}

.hero-card-copy {
  margin: 0 0 22px;
  color: var(--muted-strong);
  font-size: 0.8rem;
  line-height: 1.75;
}

.data-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 10px 0;
  border-top: 1px solid var(--line);
  font-size: 0.72rem;
}

.data-row span {
  color: var(--muted);
  font-family: var(--font-mono);
  font-size: 0.62rem;
  text-transform: uppercase;
}

.data-row code,
.data-row strong {
  font-size: 0.68rem;
}

.store-section {
  padding: clamp(46px, 6vw, 70px) 0;
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
  font-size: clamp(2rem, 4vw, 3.1rem);
  font-weight: 500;
  line-height: 1.04;
  letter-spacing: -0.045em;
}

.section-meta {
  margin: 0;
  color: var(--muted);
  font-family: var(--font-mono);
  font-size: 0.68rem;
  line-height: 1.8;
  text-align: right;
}

.store-alert {
  margin-top: 24px;
  border: 1.5px solid var(--ink);
  border-radius: 2px;
}

.store-alert p {
  margin: 0;
}

.alert-meta {
  margin-top: 4px !important;
  font-size: 0.76rem;
}

.retry-button {
  margin-top: 10px;
}

.store-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 22px;
  margin-top: 30px;
}

.store-card {
  --card-accent: var(--ketchup);
  display: flex;
  min-height: 350px;
  padding: 22px;
  border: 1.5px solid var(--ink);
  border-radius: 18px;
  background: #fffdf8;
  box-shadow: 5px 5px 0 var(--card-accent);
  color: var(--ink);
  flex-direction: column;
  text-decoration: none;
  transition:
    transform 160ms ease,
    box-shadow 160ms ease;
}

.store-card:hover,
.store-card:focus-visible {
  transform: translate(-2px, -3px);
  box-shadow: 8px 8px 0 var(--card-accent);
}

.accent-0 {
  --card-accent: var(--ketchup);
}

.accent-1 {
  --card-accent: var(--mustard);
}

.accent-2 {
  --card-accent: var(--green);
}

.store-card.is-unavailable {
  --card-accent: #9b9184;
  background: #f4eee2;
}

.store-top {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
}

.store-number {
  color: var(--card-accent);
  font-family: var(--font-mono);
  font-size: 0.7rem;
  font-weight: 700;
}

.store-card h3 {
  margin: 30px 0 7px;
  font-family: var(--font-display);
  font-size: 2rem;
  font-weight: 500;
  letter-spacing: -0.04em;
}

.store-address {
  min-height: 3.2em;
  margin: 0;
  color: var(--muted-strong);
  font-size: 0.8rem;
  line-height: 1.6;
}

.store-meta {
  display: grid;
  gap: 12px;
  margin: 22px 0;
  padding: 16px 0;
  border-top: 1px solid var(--line);
  border-bottom: 1px solid var(--line);
}

.store-meta div {
  display: flex;
  justify-content: space-between;
  gap: 12px;
}

.store-meta dt {
  color: var(--muted);
  font-family: var(--font-mono);
  font-size: 0.62rem;
}

.store-meta dd {
  margin: 0;
  font-size: 0.72rem;
  font-weight: 600;
  text-align: right;
}

.store-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: auto;
  color: var(--muted-strong);
  font-size: 0.72rem;
  font-weight: 600;
}

.arrow {
  color: var(--card-accent);
  font-size: 1.3rem;
  line-height: 1;
}

.skeleton-card {
  gap: 13px;
  box-shadow: none;
  pointer-events: none;
}

.skeleton-line {
  display: block;
  width: 100%;
  height: 14px;
  border-radius: 4px;
  background: linear-gradient(90deg, #e9e0d1 20%, #f5efe4 50%, #e9e0d1 80%);
  background-size: 200% 100%;
  animation: shimmer 1.4s infinite linear;
}

.skeleton-line.short {
  width: 24%;
}

.skeleton-line.medium {
  width: 62%;
}

.skeleton-line.title {
  width: 72%;
  height: 34px;
  margin-top: 28px;
}

.scope-note {
  display: grid;
  grid-template-columns: 130px minmax(0, 1fr);
  gap: 24px;
  padding-top: 26px;
}

.scope-index {
  color: var(--ketchup);
  font-family: var(--font-mono);
  font-size: 0.65rem;
  letter-spacing: 0.1em;
}

.scope-note p {
  max-width: 80ch;
  margin: 0;
  color: var(--muted);
  font-size: 0.76rem;
  line-height: 1.8;
}

@keyframes shimmer {
  to {
    background-position: -200% 0;
  }
}

@media (max-width: 960px) {
  .hero {
    grid-template-columns: 1fr;
  }

  .hero-card {
    max-width: 560px;
  }

  .store-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 660px) {
  .page {
    padding: 0 18px 34px;
  }

  .hero {
    padding-top: 44px;
  }

  h1 {
    font-size: clamp(3.25rem, 16vw, 4.6rem);
  }

  .section-heading {
    align-items: start;
    flex-direction: column;
    gap: 10px;
  }

  .section-meta {
    text-align: left;
  }

  .store-grid {
    grid-template-columns: 1fr;
  }

  .store-card {
    min-height: 320px;
  }

  .scope-note {
    grid-template-columns: 1fr;
    gap: 8px;
  }
}

@media (prefers-reduced-motion: reduce) {
  .store-card {
    transition: none;
  }

  .skeleton-line {
    animation: none;
  }
}
</style>
