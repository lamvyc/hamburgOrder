<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { fetchAdminEntry } from '@/api/auth'
import { toUiError } from '@/api/http'
import type { AdminEntryResponse } from '@/types/auth'
import type { UiError } from '@/types/system'

const entry = ref<AdminEntryResponse | null>(null)
const loading = ref(false)
const error = ref<UiError | null>(null)

async function load(): Promise<void> {
  loading.value = true
  error.value = null
  try {
    entry.value = await fetchAdminEntry()
  } catch (cause) {
    error.value = toUiError(cause)
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  void load()
})
</script>

<template>
  <main class="page">
    <section class="hero">
      <div>
        <p class="eyebrow">STAGE A2 / ADMIN ENTRY</p>
        <h1>管理员入口</h1>
        <p class="lede">
          这是真正受后端保护的 ADMIN 入口，不是前端隐藏菜单。
          完整门店、商品、优惠与统计管理留到后续阶段。
        </p>
      </div>
      <aside class="entry-stamp">
        <span>ACCESS</span>
        <strong>ADMIN</strong>
        <small>ROLE REQUIRED</small>
      </aside>
    </section>

    <section class="entry-section" :aria-busy="loading">
      <el-alert
        v-if="error"
        type="error"
        :closable="false"
        show-icon
        title="管理员入口加载失败"
      >
        <p>{{ error.message }}</p>
        <p class="alert-meta">
          错误码 <code>{{ error.code }}</code>
          <span v-if="error.traceId">· traceId <code>{{ error.traceId }}</code></span>
        </p>
        <el-button size="small" @click="load">重新加载</el-button>
      </el-alert>

      <div v-else-if="loading" class="loading-card">正在读取管理员入口…</div>

      <div v-else-if="entry" class="admin-card">
        <div class="admin-head">
          <span>BASE ENTRY</span>
          <el-tag type="success" effect="dark" round>已授权</el-tag>
        </div>
        <h2>管理基础入口已启用</h2>
        <p>{{ entry.message }}</p>
        <dl>
          <div>
            <dt>角色</dt>
            <dd>{{ entry.role }}</dd>
          </div>
          <div>
            <dt>可用</dt>
            <dd>{{ entry.available ? '是' : '否' }}</dd>
          </div>
          <div>
            <dt>检查时间</dt>
            <dd>{{ new Date(entry.checkedAt).toLocaleString('zh-CN', { hour12: false }) }}</dd>
          </div>
        </dl>

        <RouterLink class="console-link" to="/admin/catalog">
          进入商品与库存管理 →
        </RouterLink>

        <div class="planned">
          <span>后续阶段</span>
          <ul>
            <li>门店与营业/接单状态管理</li>
            <li>商品上架、价格与库存管理</li>
            <li>优惠券活动、配额与核销</li>
            <li>订单统计、图表与导出</li>
          </ul>
        </div>
      </div>
    </section>

    <aside class="scope-note">
      <span>A2 / BASE ONLY</span>
      <p>
        本轮没有实现任何管理写入。这里展示的是真实角色校验通过后的入口信息，
        不伪造门店、订单或统计数据。
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
  grid-template-columns: minmax(0, 1.3fr) minmax(260px, 0.7fr);
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

.entry-stamp {
  display: grid;
  padding: 26px;
  border: 1.5px solid var(--ink);
  background: var(--ketchup);
  color: #fff8ec;
  box-shadow: 7px 7px 0 var(--ink);
  transform: rotate(1deg);
}

.entry-stamp span,
.entry-stamp small {
  font-family: var(--font-mono);
  font-size: 0.62rem;
  letter-spacing: 0.14em;
}

.entry-stamp strong {
  margin: 18px 0 10px;
  font-family: var(--font-display);
  font-size: 3.4rem;
  font-style: italic;
  font-weight: 500;
  line-height: 1;
}

.entry-section {
  padding: 42px 0;
  border-bottom: 1.5px solid var(--ink);
}

.entry-section p {
  margin: 0 0 6px;
}

.entry-section :deep(.el-alert) {
  border: 1.5px solid var(--ink);
  border-radius: 2px;
}

.alert-meta {
  font-size: 0.72rem;
}

.loading-card {
  padding: 40px;
  border: 1.5px dashed var(--line-strong);
  color: var(--muted);
  font-family: var(--font-mono);
  font-size: 0.72rem;
  text-align: center;
}

.admin-card {
  max-width: 760px;
  padding: 26px;
  border: 1.5px solid var(--ink);
  background: #fffdf8;
  box-shadow: 6px 6px 0 var(--mustard);
}

.admin-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding-bottom: 14px;
  border-bottom: 1px dashed var(--line-strong);
  font-family: var(--font-mono);
  font-size: 0.62rem;
  letter-spacing: 0.12em;
}

.admin-card h2 {
  margin: 24px 0 9px;
  font-family: var(--font-display);
  font-size: 2rem;
  font-weight: 500;
  letter-spacing: -0.04em;
}

.admin-card > p {
  color: var(--muted-strong);
  font-size: 0.82rem;
  line-height: 1.75;
}

.admin-card dl {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 14px;
  margin: 24px 0;
  padding: 16px 0;
  border-top: 1px solid var(--line);
  border-bottom: 1px solid var(--line);
}

.admin-card dt {
  color: var(--muted);
  font-family: var(--font-mono);
  font-size: 0.6rem;
}

.admin-card dd {
  margin: 4px 0 0;
  font-size: 0.75rem;
  font-weight: 600;
}

.console-link {
  display: inline-block;
  margin: 18px 0 4px;
  padding: 10px 14px;
  border: 1.5px solid var(--ink);
  background: var(--ink);
  color: var(--paper);
  font-size: 0.78rem;
  font-weight: 700;
  text-decoration: none;
  box-shadow: 3px 3px 0 var(--mustard);
}

.console-link:hover {
  background: var(--ketchup);
  border-color: var(--ketchup);
}

.planned {
  display: grid;
  grid-template-columns: 100px minmax(0, 1fr);
  gap: 14px;
}

.planned > span {
  color: var(--ketchup);
  font-family: var(--font-mono);
  font-size: 0.6rem;
  letter-spacing: 0.08em;
}

.planned ul {
  display: grid;
  gap: 6px;
  margin: 0;
  padding: 0;
  color: var(--muted-strong);
  font-size: 0.76rem;
  list-style: none;
}

.planned li::before {
  margin-right: 7px;
  color: var(--ketchup);
  content: "+";
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

  .entry-stamp {
    max-width: 360px;
  }
}

@media (max-width: 620px) {
  .page {
    padding: 0 18px 34px;
  }

  .admin-card dl {
    grid-template-columns: 1fr;
  }

  .planned,
  .scope-note {
    grid-template-columns: 1fr;
    gap: 8px;
  }
}
</style>
