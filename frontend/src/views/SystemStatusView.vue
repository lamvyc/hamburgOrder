<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { fetchMenuCacheStats } from '@/api/system'
import { useSystemStatusStore } from '@/stores/systemStatus'
import type { HealthState, MenuCacheStats } from '@/types/system'

type DisplayState = HealthState | 'UNKNOWN' | 'CHECKING'
type TagType = 'success' | 'warning' | 'danger' | 'info' | 'primary'

const store = useSystemStatusStore()
const cacheStats = ref<MenuCacheStats | null>(null)
const cacheLoading = ref(false)

const stateLabels: Record<DisplayState, string> = {
  UP: '正常',
  DEGRADED: '降级',
  DOWN: '不可用',
  UNKNOWN: '未知',
  CHECKING: '检测中',
}

const appState = computed<DisplayState>(() => {
  if (store.error) {
    return store.backendReachable ? 'DEGRADED' : 'DOWN'
  }
  if (store.loading && !store.snapshot) {
    return 'CHECKING'
  }
  return store.snapshot?.application.status ?? 'UNKNOWN'
})

const databaseState = computed<DisplayState>(() => {
  if (store.loading && !store.snapshot && !store.error) {
    return 'CHECKING'
  }
  if (!store.backendReachable) {
    return 'UNKNOWN'
  }
  return store.snapshot?.database.status ?? 'UNKNOWN'
})

const application = computed(() => store.snapshot?.application ?? null)
const database = computed(() => store.snapshot?.database ?? null)
const isRefreshing = computed(() => store.loading || cacheLoading.value)
const checkedAt = computed(() => formatDateTime(store.snapshot?.checkedAt))
const lastSuccessAt = computed(() => formatDateTime(store.lastSuccessAt))
const traceId = computed(() => store.snapshot?.traceId ?? store.error?.traceId ?? '—')

const applicationNote = computed(() => {
  if (appState.value === 'CHECKING') {
    return '正在请求后端，确认进程是否可响应。'
  }
  if (appState.value === 'UP') {
    return 'Spring Boot 进程可响应 HTTP；此项不依赖数据库。'
  }
  if (appState.value === 'DOWN') {
    return '浏览器没有拿到后端响应，先检查 8080 端口与启动日志。'
  }
  return '后端可达，但状态响应或应用状态尚未恢复正常。'
})

const databaseNote = computed(() => {
  if (databaseState.value === 'CHECKING') {
    return '正在执行真实连接校验与迁移读取。'
  }
  if (databaseState.value === 'UP') {
    return '连接、迁移标记与 Flyway 历史均可读取。'
  }
  if (!store.backendReachable) {
    return '后端不可达，当前无法独立判断数据库状态。'
  }
  return database.value?.message ?? '数据库状态未知。'
})

function tagType(state: DisplayState): TagType {
  if (state === 'UP') return 'success'
  if (state === 'DEGRADED') return 'warning'
  if (state === 'DOWN') return 'danger'
  return 'info'
}

function stateTone(state: DisplayState): string {
  return `tone-${state.toLowerCase()}`
}

function formatDateTime(value: string | null | undefined): string {
  if (!value) return '—'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return '—'
  return new Intl.DateTimeFormat('zh-CN', {
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit',
    hour12: false,
  }).format(date)
}

function formatDuration(totalSeconds: number | undefined): string {
  if (totalSeconds === undefined) return '—'
  const seconds = Math.max(0, Math.floor(totalSeconds))
  const hours = Math.floor(seconds / 3600)
  const minutes = Math.floor((seconds % 3600) / 60)
  const rest = seconds % 60
  if (hours > 0) return `${hours}h ${minutes}m`
  if (minutes > 0) return `${minutes}m ${rest}s`
  return `${rest}s`
}

function booleanText(value: boolean | undefined): string {
  if (value === undefined) return '—'
  return value ? '是' : '否'
}

async function loadCacheStats(): Promise<void> {
  cacheLoading.value = true
  try {
    cacheStats.value = await fetchMenuCacheStats()
  } catch {
    cacheStats.value = null
  } finally {
    cacheLoading.value = false
  }
}

async function refreshAll(): Promise<void> {
  await store.refresh()
  await loadCacheStats()
}

function migrationText(): string {
  const current = database.value
  if (!current?.flywayVersion) return '—'
  const description = current.lastMigration ? ` · ${current.lastMigration}` : ''
  return `V${current.flywayVersion}${description}`
}

onMounted(() => {
  void refreshAll()
})
</script>

<template>
  <main class="page">
    <section class="hero">
      <div class="hero-copy">
        <p class="eyebrow">ENGINEERING FIRST / LEARNING ALONGSIDE</p>
        <h1>先把地基<br /><span>接通。</span></h1>
        <p class="lede">
          这个页面把 Java 进程、MySQL、Flyway、菜单缓存与 Prometheus
          指标拆开验证；缓存命中不会被伪装成业务成功。
        </p>
        <div class="hero-actions">
          <el-button
            class="refresh-button"
            type="primary"
            :loading="isRefreshing"
            @click="refreshAll"
          >
            {{ isRefreshing ? '正在检测' : '重新检测' }}
          </el-button>
          <a class="text-link" href="/actuator/health" target="_blank" rel="noreferrer">
            Actuator 健康端点 <span aria-hidden="true">↗</span>
          </a>
        </div>
      </div>

      <aside class="ticket" aria-label="阶段 0 健康检查约定">
        <div class="ticket-head">
          <span>HEALTH CONTRACT</span>
          <span>NO. 00</span>
        </div>
        <ol>
          <li>
            <span>01</span>
            <p>进程活着，不等于数据库已经就绪。</p>
          </li>
          <li>
            <span>02</span>
            <p>数据库失败，不能伪装成 HTTP 200。</p>
          </li>
          <li>
            <span>03</span>
            <p>每次失败都有可追踪的 traceId。</p>
          </li>
        </ol>
        <p class="ticket-foot">真实查询，不用固定成功响应冒充数据库验证。</p>
      </aside>
    </section>

    <section class="status-section" aria-live="polite" :aria-busy="store.loading">
      <div class="section-heading">
        <div>
          <p class="section-kicker">LIVE CHECKS</p>
          <h2>两张体检单</h2>
        </div>
        <p class="section-meta">
          最近成功 {{ lastSuccessAt }}<br />
          traceId <code>{{ traceId }}</code>
        </p>
      </div>

      <el-alert
        v-if="store.error"
        class="status-alert"
        type="error"
        :closable="false"
        show-icon
        title="这次检测没有拿到完整状态"
      >
        <p>{{ store.error.message }}</p>
        <p class="alert-meta">
          错误码 <code>{{ store.error.code }}</code>
          <span v-if="store.error.traceId">· traceId <code>{{ store.error.traceId }}</code></span>
        </p>
      </el-alert>

      <el-alert
        v-else-if="database && database.status !== 'UP'"
        class="status-alert"
        :type="database.status === 'DOWN' ? 'error' : 'warning'"
        :closable="false"
        show-icon
        :title="database.message ?? '数据库尚未就绪'"
      >
        <p class="alert-meta">
          错误码 <code>{{ database.code ?? 'UNKNOWN' }}</code>
          <span v-if="database.latencyMs >= 0">· 探活耗时 {{ database.latencyMs }} ms</span>
        </p>
      </el-alert>

      <div class="status-grid">
        <article class="status-card" :class="stateTone(appState)">
          <div class="card-head">
            <div>
              <span class="card-index">01 / PROCESS</span>
              <h3>应用进程</h3>
            </div>
            <el-tag :type="tagType(appState)" effect="dark" round>
              {{ stateLabels[appState] }}
            </el-tag>
          </div>

          <div class="status-summary">
            <span class="big-state">{{ stateLabels[appState] }}</span>
            <p>{{ applicationNote }}</p>
          </div>

          <dl class="metrics">
            <div>
              <dt>服务名</dt>
              <dd>{{ application?.name ?? '—' }}</dd>
            </div>
            <div>
              <dt>构建版本</dt>
              <dd>{{ application?.version ?? '—' }}</dd>
            </div>
            <div>
              <dt>Java</dt>
              <dd>{{ application?.javaVersion ?? '—' }}</dd>
            </div>
            <div>
              <dt>已运行</dt>
              <dd>{{ formatDuration(application?.uptimeSeconds) }}</dd>
            </div>
          </dl>

          <div class="endpoint">
            <code>GET /api/system/liveness</code>
            <span>不查询数据库</span>
          </div>
        </article>

        <article class="status-card" :class="stateTone(databaseState)">
          <div class="card-head">
            <div>
              <span class="card-index">02 / DATABASE</span>
              <h3>MySQL 就绪</h3>
            </div>
            <el-tag :type="tagType(databaseState)" effect="dark" round>
              {{ stateLabels[databaseState] }}
            </el-tag>
          </div>

          <div class="status-summary">
            <span class="big-state">{{ stateLabels[databaseState] }}</span>
            <p>{{ databaseNote }}</p>
          </div>

          <dl class="metrics">
            <div>
              <dt>产品 / 版本</dt>
              <dd>
                {{ database?.product ?? '—' }}
                {{ database?.version ? ` ${database.version}` : '' }}
              </dd>
            </div>
            <div>
              <dt>连接成功</dt>
              <dd>{{ booleanText(database?.connected) }}</dd>
            </div>
            <div>
              <dt>探活耗时</dt>
              <dd>{{ database ? `${database.latencyMs} ms` : '—' }}</dd>
            </div>
            <div>
              <dt>迁移标记</dt>
              <dd>{{ database ? (database.schemaReady ? '已就绪' : '未就绪') : '—' }}</dd>
            </div>
            <div class="metric-wide">
              <dt>Flyway 当前版本</dt>
              <dd>{{ migrationText() }}</dd>
            </div>
          </dl>

          <details class="sql-details">
            <summary>查看探活 SQL</summary>
            <pre><code>SELECT
  (SELECT meta_value FROM system_metadata
    WHERE meta_key = 'schema_ready') AS schema_ready,
  (SELECT version FROM flyway_schema_history
    WHERE success = 1
    ORDER BY installed_rank DESC LIMIT 1) AS flyway_version</code></pre>
          </details>

          <div class="endpoint">
            <code>GET /api/system/status</code>
            <span>连接校验 + 迁移读取</span>
          </div>
        </article>
      </div>

      <section class="observability-section" aria-live="polite">
        <div class="section-heading">
          <div>
            <p class="section-kicker">C3 / OBSERVABILITY</p>
            <h2>缓存与请求指标</h2>
          </div>
          <p class="section-meta">
            进程本地计数 · 重启归零<br />
            <a class="text-link" href="/actuator/prometheus" target="_blank" rel="noreferrer">
              Prometheus 指标 ↗
            </a>
          </p>
        </div>

        <div class="cache-grid">
          <article class="cache-metric cache-metric-hit">
            <span>HIT</span>
            <strong>{{ cacheStats?.hit ?? '—' }}</strong>
            <small>直接来自 Redis</small>
          </article>
          <article class="cache-metric cache-metric-miss">
            <span>MISS</span>
            <strong>{{ cacheStats?.miss ?? '—' }}</strong>
            <small>回源并写入</small>
          </article>
          <article class="cache-metric cache-metric-bypass">
            <span>BYPASS</span>
            <strong>{{ cacheStats?.bypass ?? '—' }}</strong>
            <small>缓存降级</small>
          </article>
          <article class="cache-metric cache-metric-negative">
            <span>NEGATIVE</span>
            <strong>{{ cacheStats?.negative ?? '—' }}</strong>
            <small>缺失门店负缓存</small>
          </article>
          <article class="cache-metric cache-metric-stampede">
            <span>STAMPEDE WAIT</span>
            <strong>{{ cacheStats?.stampedeWait ?? '—' }}</strong>
            <small>single-flight 等待</small>
          </article>
        </div>
      </section>

      <p class="checked-at">本次检测：{{ checkedAt }}</p>
    </section>

    <section class="flow-section">
      <div class="section-heading">
        <div>
          <p class="section-kicker">CALL CHAIN</p>
          <h2>一次状态查询怎么走</h2>
        </div>
        <p class="section-meta">从浏览器到数据库，再到页面更新</p>
      </div>

      <ol class="flow-list">
        <li>
          <span>01</span>
          <strong>Vue</strong>
          <small>SystemStatusView.vue</small>
        </li>
        <li>
          <span>02</span>
          <strong>Axios</strong>
          <small>api/system.ts</small>
        </li>
        <li>
          <span>03</span>
          <strong>Vite Proxy</strong>
          <small>vite.config.ts</small>
        </li>
        <li>
          <span>04</span>
          <strong>Controller</strong>
          <small>PlatformHealthController</small>
        </li>
        <li>
          <span>05</span>
          <strong>Service</strong>
          <small>PlatformHealthService</small>
        </li>
        <li>
          <span>06</span>
          <strong>Mapper</strong>
          <small>SystemStatusMapper</small>
        </li>
        <li>
          <span>07</span>
          <strong>MySQL</strong>
          <small>system_metadata / flyway_schema_history</small>
        </li>
      </ol>

      <p class="flow-note">
        <strong>/liveness</strong> 只验证进程能响应；<strong>/status</strong>
        会建立数据库连接并读取迁移记录。两者混在一起，就会失去故障定位能力。
      </p>
    </section>

    <section class="boundary">
      <article class="boundary-panel boundary-done">
        <p class="section-kicker">IN SCOPE</p>
        <h2>当前已落地</h2>
        <ul>
          <li>阶段 0 + A/B/C 的订单、库存、支付、取消、优惠券与管理端闭环</li>
          <li>MySQL 8.4.13 + Redis 7.4，Flyway V1–V13</li>
          <li>Session/CSRF、幂等、条件更新、乐观锁与审计</li>
          <li>菜单 cache-aside、负缓存、TTL 抖动与 single-flight</li>
          <li>Prometheus 指标、缓存计数与 traceId 日志</li>
          <li>66 个后端测试、30 个前端测试、12 条浏览器路径</li>
        </ul>
      </article>
      <article class="boundary-panel">
        <p class="section-kicker">NOT YET</p>
        <h2>刻意留到后面</h2>
        <ul>
          <li>Redis 分布式 single-flight / 多实例失效与计数聚合</li>
          <li>CI/CD、镜像发布、备份恢复与故障演练</li>
          <li>支付回调、退款、对账与真实支付渠道</li>
          <li>个人订单列表、百分比券、管理端批量导入</li>
          <li>生产监控、容量规划与安全审计</li>
        </ul>
      </article>
    </section>
  </main>
</template>

<style scoped>
.page {
  width: min(1180px, 100%);
  margin: 0 auto;
  padding: 0 32px 42px;
}

.hero {
  display: grid;
  grid-template-columns: minmax(0, 1.35fr) minmax(280px, 0.85fr);
  gap: clamp(32px, 6vw, 80px);
  align-items: start;
  padding: clamp(54px, 9vw, 96px) 0 clamp(48px, 7vw, 72px);
  border-bottom: 1.5px solid var(--ink);
}

.eyebrow,
.section-kicker,
.card-index {
  margin: 0;
  color: var(--muted);
  font-family: var(--font-mono);
  font-size: 0.68rem;
  font-weight: 600;
  letter-spacing: 0.14em;
  text-transform: uppercase;
}

h1,
h2,
h3,
p {
  margin-top: 0;
}

h1 {
  max-width: 9ch;
  margin-bottom: 26px;
  font-family: var(--font-display);
  font-size: clamp(3.7rem, 8vw, 6.7rem);
  font-weight: 500;
  line-height: 0.88;
  letter-spacing: -0.065em;
}

h1 span {
  color: var(--ketchup);
  font-style: italic;
}

.lede {
  max-width: 54ch;
  margin-bottom: 28px;
  color: var(--muted-strong);
  font-size: clamp(0.98rem, 1.2vw, 1.08rem);
  line-height: 1.85;
}

.hero-actions {
  display: flex;
  align-items: center;
  gap: 18px;
  flex-wrap: wrap;
}

.refresh-button {
  min-width: 126px;
  border: 1.5px solid var(--ink);
  border-radius: 999px;
  background: var(--ink);
  box-shadow: 3px 3px 0 var(--mustard);
  font-weight: 600;
}

.refresh-button:hover,
.refresh-button:focus-visible {
  background: var(--ketchup);
  border-color: var(--ink);
}

.text-link {
  color: var(--ink);
  font-size: 0.82rem;
  font-weight: 600;
  text-decoration: none;
  text-underline-offset: 4px;
  border-bottom: 1px solid var(--ink);
}

.text-link:hover {
  color: var(--ketchup);
  border-color: var(--ketchup);
}

.ticket {
  position: relative;
  padding: 22px;
  border: 1.5px solid var(--ink);
  background: #fffdf7;
  box-shadow: 8px 8px 0 var(--mustard);
  transform: rotate(1.3deg);
}

.ticket::after {
  position: absolute;
  top: -10px;
  left: 28px;
  width: 62px;
  height: 18px;
  background: rgba(229, 170, 50, 0.7);
  content: "";
  transform: rotate(-3deg);
}

.ticket-head {
  display: flex;
  justify-content: space-between;
  padding-bottom: 13px;
  border-bottom: 1px dashed var(--line-strong);
  font-family: var(--font-mono);
  font-size: 0.62rem;
  letter-spacing: 0.13em;
}

.ticket ol {
  display: grid;
  gap: 17px;
  margin: 21px 0;
  padding: 0;
  list-style: none;
}

.ticket li {
  display: grid;
  grid-template-columns: 28px 1fr;
  gap: 9px;
  align-items: start;
}

.ticket li span {
  padding-top: 2px;
  color: var(--ketchup);
  font-family: var(--font-mono);
  font-size: 0.68rem;
  font-weight: 700;
}

.ticket li p {
  margin: 0;
  font-family: var(--font-display);
  font-size: 1.13rem;
  line-height: 1.45;
  letter-spacing: -0.015em;
}

.ticket-foot {
  margin: 0;
  padding-top: 14px;
  border-top: 1px dashed var(--line-strong);
  color: var(--muted);
  font-size: 0.72rem;
  line-height: 1.6;
}

.status-section,
.flow-section {
  padding: clamp(46px, 6vw, 68px) 0;
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
  font-size: 0.7rem;
  line-height: 1.8;
  text-align: right;
}

.section-meta code {
  color: var(--ink);
  overflow-wrap: anywhere;
}

.status-alert {
  margin-top: 24px;
  border: 1.5px solid var(--ink);
  border-radius: 2px;
  box-shadow: 4px 4px 0 rgba(30, 27, 23, 0.14);
}

.status-alert p {
  margin: 0;
}

.alert-meta {
  margin-top: 5px !important;
  font-size: 0.76rem;
  opacity: 0.85;
}

.status-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 24px;
  margin-top: 28px;
}

.status-card {
  --tone: var(--muted);
  position: relative;
  overflow: hidden;
  padding: 23px;
  border: 1.5px solid var(--ink);
  border-radius: 20px;
  background: #fffdf8;
  box-shadow: 6px 6px 0 var(--tone);
}

.status-card::before {
  position: absolute;
  inset: 0 0 auto;
  height: 7px;
  background: var(--tone);
  content: "";
}

.tone-up {
  --tone: var(--green);
}

.tone-degraded {
  --tone: var(--mustard);
}

.tone-down {
  --tone: var(--ketchup);
}

.tone-checking {
  --tone: #8a8074;
}

.tone-unknown {
  --tone: #9a9186;
}

.card-head {
  display: flex;
  align-items: start;
  justify-content: space-between;
  gap: 14px;
  padding-top: 7px;
}

.card-head h3 {
  margin: 6px 0 0;
  font-family: var(--font-display);
  font-size: 1.65rem;
  font-weight: 500;
  letter-spacing: -0.03em;
}

.status-summary {
  display: grid;
  grid-template-columns: minmax(124px, 0.55fr) minmax(0, 1.45fr);
  gap: 18px;
  align-items: center;
  margin: 22px 0 20px;
  padding: 17px 0;
  border-top: 1px solid var(--line);
  border-bottom: 1px solid var(--line);
}

.big-state {
  color: var(--tone);
  font-family: var(--font-display);
  font-size: clamp(2.1rem, 3.8vw, 3.25rem);
  line-height: 1;
  letter-spacing: -0.055em;
  white-space: nowrap;
}

.status-summary p {
  margin: 0;
  color: var(--muted-strong);
  font-size: 0.84rem;
  line-height: 1.75;
}

.metrics {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 16px 20px;
  margin: 0;
}

.metrics div {
  min-width: 0;
}

.metric-wide {
  grid-column: 1 / -1;
}

.metrics dt {
  margin-bottom: 4px;
  color: var(--muted);
  font-family: var(--font-mono);
  font-size: 0.62rem;
  letter-spacing: 0.08em;
  text-transform: uppercase;
}

.metrics dd {
  margin: 0;
  font-size: 0.88rem;
  font-weight: 650;
  line-height: 1.45;
  overflow-wrap: anywhere;
}

.endpoint {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-top: 22px;
  padding: 10px 12px;
  border: 1px dashed var(--line-strong);
  background: rgba(244, 239, 228, 0.5);
}

.endpoint code {
  font-size: 0.67rem;
}

.endpoint span {
  color: var(--muted);
  font-size: 0.65rem;
  text-align: right;
}

.sql-details {
  margin-top: 18px;
  border-top: 1px solid var(--line);
}

.sql-details summary {
  padding-top: 14px;
  color: var(--muted-strong);
  font-family: var(--font-mono);
  font-size: 0.68rem;
  cursor: pointer;
}

.sql-details pre {
  overflow-x: auto;
  margin: 10px 0 0;
  padding: 12px;
  border: 1px solid var(--line);
  background: #f6f0e4;
  font-size: 0.67rem;
  line-height: 1.65;
}

.observability-section {
  padding-top: 38px;
  border-top: 1.5px solid var(--ink);
}

.cache-grid {
  display: grid;
  grid-template-columns: repeat(5, minmax(0, 1fr));
  gap: 10px;
  margin-top: 18px;
}

.cache-metric {
  display: grid;
  gap: 5px;
  padding: 15px;
  border: 1.5px solid var(--ink);
  background: #fffdf8;
  box-shadow: 3px 3px 0 var(--mustard);
}

.cache-metric span,
.cache-metric small {
  color: var(--muted);
  font-family: var(--font-mono);
  font-size: 0.58rem;
  letter-spacing: 0.06em;
}

.cache-metric strong {
  font-family: var(--font-display);
  font-size: 2rem;
  font-weight: 500;
}

.checked-at {
  margin: 18px 0 0;
  color: var(--muted);
  font-family: var(--font-mono);
  font-size: 0.68rem;
  text-align: right;
}

.flow-list {
  display: grid;
  grid-template-columns: repeat(7, minmax(0, 1fr));
  margin: 30px 0 0;
  padding: 0;
  list-style: none;
  border: 1.5px solid var(--ink);
  background: #fffdf8;
}

.flow-list li {
  position: relative;
  min-width: 0;
  padding: 17px 14px;
}

.flow-list li + li {
  border-left: 1px solid var(--line-strong);
}

.flow-list span,
.flow-list strong,
.flow-list small {
  display: block;
}

.flow-list span {
  margin-bottom: 18px;
  color: var(--ketchup);
  font-family: var(--font-mono);
  font-size: 0.62rem;
  font-weight: 700;
}

.flow-list strong {
  margin-bottom: 5px;
  font-size: 0.82rem;
}

.flow-list small {
  color: var(--muted);
  font-family: var(--font-mono);
  font-size: 0.59rem;
  line-height: 1.45;
  overflow-wrap: anywhere;
}

.flow-note {
  max-width: 88ch;
  margin: 20px 0 0;
  color: var(--muted-strong);
  font-size: 0.82rem;
  line-height: 1.8;
}

.flow-note strong {
  color: var(--ink);
  font-family: var(--font-mono);
  font-size: 0.75rem;
}

.boundary {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 24px;
  padding: clamp(46px, 6vw, 68px) 0;
}

.boundary-panel {
  padding: 26px;
  border: 1.5px solid var(--ink);
  background: #fffdf8;
}

.boundary-done {
  background: var(--ink);
  color: var(--paper);
  box-shadow: 6px 6px 0 var(--ketchup);
}

.boundary-done .section-kicker {
  color: var(--mustard);
}

.boundary-panel h2 {
  margin-bottom: 20px;
  font-size: 2rem;
}

.boundary-panel ul {
  display: grid;
  gap: 9px;
  margin: 0;
  padding: 0;
  list-style: none;
}

.boundary-panel li {
  position: relative;
  padding-left: 19px;
  font-size: 0.84rem;
  line-height: 1.6;
}

.boundary-panel li::before {
  position: absolute;
  left: 0;
  color: var(--ketchup);
  content: "+";
  font-weight: 700;
}

.boundary-done li::before {
  color: var(--mustard);
}

@media (max-width: 920px) {
  .hero {
    grid-template-columns: 1fr;
    gap: 38px;
  }

  .ticket {
    max-width: 540px;
    transform: rotate(0.7deg);
  }

  .cache-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .status-grid,
  .boundary {
    grid-template-columns: 1fr;
  }

  .flow-list {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }

  .flow-list li {
    border-bottom: 1px solid var(--line-strong);
  }

  .flow-list li:nth-child(3n + 1) {
    border-left: 0;
  }

  .flow-list li:nth-child(n + 4) {
    border-bottom: 0;
  }
}

@media (max-width: 620px) {
  .page {
    padding: 0 18px 32px;
  }

  .hero {
    padding-top: 44px;
  }

  h1 {
    font-size: clamp(3.4rem, 18vw, 5rem);
  }

  .hero-actions {
    align-items: stretch;
    flex-direction: column;
  }

  .refresh-button {
    width: 100%;
  }

  .text-link {
    align-self: flex-start;
  }

  .section-heading {
    align-items: start;
    flex-direction: column;
    gap: 10px;
  }

  .section-meta {
    text-align: left;
  }

  .status-summary {
    grid-template-columns: 1fr;
    gap: 9px;
  }

  .big-state {
    font-size: 2.6rem;
  }

  .metrics {
    grid-template-columns: 1fr;
  }

  .metric-wide {
    grid-column: auto;
  }

  .endpoint {
    align-items: start;
    flex-direction: column;
  }

  .endpoint span {
    text-align: left;
  }

  .flow-list {
    grid-template-columns: 1fr;
  }

  .flow-list li + li {
    border-top: 1px solid var(--line-strong);
    border-left: 0;
  }

  .flow-list li:nth-child(n) {
    border-bottom: 0;
  }

  .flow-list span {
    margin-bottom: 8px;
  }
}

@media (prefers-reduced-motion: no-preference) {
  .refresh-button,
  .text-link,
  .status-card {
    transition:
      color 160ms ease,
      background-color 160ms ease,
      border-color 160ms ease,
      box-shadow 160ms ease;
  }

  .tone-checking .big-state {
    animation: breathe 1.4s ease-in-out infinite;
  }
}

@keyframes breathe {
  0%,
  100% {
    opacity: 0.45;
  }
  50% {
    opacity: 1;
  }
}

@media (prefers-reduced-motion: reduce) {
  .ticket {
    transform: none;
  }
}
</style>
