<script setup lang="ts">
import { ElMessage } from 'element-plus'
import { computed, onMounted, ref } from 'vue'
import {
  adjustAdminStock,
  adjustAdminStockBatch,
  previewAdminStockBatch,
  fetchAdminAudit,
  fetchAdminProductPage,
  updateAdminProduct,
} from '@/api/admin'
import { fetchStores } from '@/api/catalog'
import { toUiError } from '@/api/http'
import type { AdminAuditLog, AdminProduct, AdminStockPreview } from '@/types/admin'
import type { Store } from '@/types/catalog'
import type { UiError } from '@/types/system'
import { formatPrice } from '@/utils/format'

const stores = ref<Store[]>([])
const selectedStoreId = ref<number | null>(null)
const products = ref<AdminProduct[]>([])
const audit = ref<AdminAuditLog[]>([])
const loading = ref(false)
const loadingMore = ref(false)
const saving = ref(false)
const onlyOnSale = ref(false)
const nextCursor = ref<number | null>(null)
const hasMore = ref(false)
const error = ref<UiError | null>(null)

const editVisible = ref(false)
const editTarget = ref<AdminProduct | null>(null)
const editPrice = ref('')
const editOnSale = ref(true)

const stockVisible = ref(false)
const stockTarget = ref<AdminProduct | null>(null)
const stockDelta = ref('1')
const stockReason = ref('')

const batchVisible = ref(false)
const batchText = ref('')
const batchSaving = ref(false)
const batchPreviewing = ref(false)
const previewResult = ref<AdminStockPreview | null>(null)

const selectedStore = computed(
  () => stores.value.find((store) => store.id === selectedStoreId.value) ?? null,
)

async function loadStores(): Promise<void> {
  loading.value = true
  error.value = null
  try {
    const response = await fetchStores()
    stores.value = response.items
    if (response.items.length > 0) {
      selectedStoreId.value = response.items[0].id
      await loadProducts()
    }
  } catch (cause) {
    error.value = toUiError(cause)
  } finally {
    loading.value = false
  }
}

async function loadProducts(): Promise<void> {
  if (!selectedStoreId.value) return
  loading.value = true
  error.value = null
  try {
    const page = await fetchAdminProductPage(selectedStoreId.value, {
      limit: 5,
      onSale: onlyOnSale.value ? true : null,
    })
    products.value = page.items
    nextCursor.value = page.nextCursor
    hasMore.value = page.hasMore
    audit.value = await fetchAdminAudit(12)
  } catch (cause) {
    error.value = toUiError(cause)
  } finally {
    loading.value = false
  }
}

async function selectStore(storeId: number): Promise<void> {
  if (selectedStoreId.value === storeId) return
  selectedStoreId.value = storeId
  await loadProducts()
}

async function reloadForFilter(): Promise<void> {
  nextCursor.value = null
  hasMore.value = false
  await loadProducts()
}

async function loadMore(): Promise<void> {
  if (!selectedStoreId.value || !nextCursor.value || loadingMore.value) return
  loadingMore.value = true
  error.value = null
  try {
    const page = await fetchAdminProductPage(selectedStoreId.value, {
      afterId: nextCursor.value,
      limit: 5,
      onSale: onlyOnSale.value ? true : null,
    })
    products.value = [...products.value, ...page.items]
    nextCursor.value = page.nextCursor
    hasMore.value = page.hasMore
  } catch (cause) {
    error.value = toUiError(cause)
  } finally {
    loadingMore.value = false
  }
}

function openEdit(product: AdminProduct): void {
  editTarget.value = product
  editPrice.value = product.price
  editOnSale.value = product.onSale
  editVisible.value = true
}

async function saveEdit(): Promise<void> {
  if (!editTarget.value) return
  saving.value = true
  error.value = null
  try {
    await updateAdminProduct(editTarget.value.id, {
      price: editPrice.value,
      onSale: editOnSale.value,
      version: editTarget.value.version,
    })
    editVisible.value = false
    ElMessage.success('商品设置已保存')
    await loadProducts()
  } catch (cause) {
    error.value = toUiError(cause)
  } finally {
    saving.value = false
  }
}

function openStock(product: AdminProduct): void {
  stockTarget.value = product
  stockDelta.value = '1'
  stockReason.value = ''
  stockVisible.value = true
}

function openBatch(): void {
  batchText.value = ''
  previewResult.value = null
  batchVisible.value = true
}

function parseBatchText() {
  const lines = batchText.value
    .split('\n')
    .map((line) => line.trim())
    .filter(Boolean)
    .map((line) => {
      const parts = line.split(',')
      return {
        productId: Number(parts[0]),
        delta: Number(parts[1]),
        reason: parts.slice(2).join(',').trim(),
      }
    })
  if (lines.length === 0 || lines.some((line) => (
    !Number.isInteger(line.productId)
    || !Number.isInteger(line.delta)
    || !line.reason
  ))) {
    error.value = {
      code: 'INVALID_BATCH',
      message: '每行必须是 productId,delta,reason，且 delta 非 0',
    }
    return null
  }
  return lines
}

async function previewBatch(): Promise<void> {
  if (!selectedStoreId.value) return
  const lines = parseBatchText()
  if (!lines) return
  batchPreviewing.value = true
  error.value = null
  try {
    previewResult.value = await previewAdminStockBatch(selectedStoreId.value, lines)
  } catch (cause) {
    error.value = toUiError(cause)
  } finally {
    batchPreviewing.value = false
  }
}

async function saveBatch(): Promise<void> {
  if (!selectedStoreId.value) return
  const lines = parseBatchText()
  if (!lines) return
  batchSaving.value = true
  error.value = null
  try {
    const result = await adjustAdminStockBatch(
      selectedStoreId.value,
      lines,
      crypto.randomUUID(),
    )
    batchVisible.value = false
    ElMessage.success(`批次 #${result.batchId} 已应用 ${result.appliedCount} 行`)
    await loadProducts()
  } catch (cause) {
    error.value = toUiError(cause)
  } finally {
    batchSaving.value = false
  }
}

async function saveStock(): Promise<void> {
  if (!stockTarget.value) return
  const delta = Number(stockDelta.value)
  if (!Number.isInteger(delta) || delta === 0) {
    error.value = { code: 'INVALID_STOCK_DELTA', message: '调整量必须是非零整数' }
    return
  }
  saving.value = true
  error.value = null
  try {
    await adjustAdminStock(stockTarget.value.id, {
      delta,
      version: stockTarget.value.stockVersion,
      reason: stockReason.value,
    })
    stockVisible.value = false
    ElMessage.success(`库存调整 ${delta > 0 ? '+' : ''}${delta} 成功`)
    await loadProducts()
  } catch (cause) {
    error.value = toUiError(cause)
  } finally {
    saving.value = false
  }
}

function formatTime(value: string): string {
  return new Date(value).toLocaleString('zh-CN', { hour12: false })
}

onMounted(() => {
  void loadStores()
})
</script>

<template>
  <main class="page">
    <div class="back-row">
      <RouterLink class="back-link" to="/admin">← 管理员入口</RouterLink>
      <span>ADMIN / B5</span>
    </div>

    <section class="hero">
      <div>
        <p class="eyebrow">STAGE B5 / ADMIN CATALOG</p>
        <h1>商品与库存</h1>
        <p class="lede">
          价格、上下架和可售库存都有版本号。提交旧版本会得到 409，
          不会覆盖另一位管理员的修改；每次调整写入库存流水和审计日志。
        </p>
      </div>
      <aside class="admin-stamp">
        <span>ADMIN ONLY</span>
        <strong>{{ products.length }}</strong>
        <small>PRODUCTS</small>
      </aside>
    </section>

    <section class="catalog-section" :aria-busy="loading">
      <div class="section-heading">
        <div>
          <p class="eyebrow">AUTHORIZED STORES</p>
          <h2>门店商品</h2>
        </div>
        <div class="catalog-tools">
          <el-button class="batch-button" size="small" @click="openBatch">
            批量调整
          </el-button>
          <el-switch
            v-model="onlyOnSale"
            active-text="只看上架"
            inactive-text="全部商品"
            @change="reloadForFilter"
          />
          <p class="section-meta">价格/上下架使用商品版本；库存使用库存版本<br />旧版本 → 409</p>
        </div>
      </div>

      <el-alert
        v-if="error"
        class="admin-alert"
        type="error"
        :closable="false"
        show-icon
        title="管理操作失败"
      >
        <p>{{ error.message }}</p>
        <p class="alert-meta">
          错误码 <code>{{ error.code }}</code>
          <span v-if="error.traceId">· traceId <code>{{ error.traceId }}</code></span>
        </p>
        <el-button size="small" @click="loadProducts">刷新</el-button>
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

      <div v-if="loading" class="loading-card">正在读取商品与库存…</div>

      <template v-else-if="selectedStore">
        <div class="admin-products">
          <table>
            <thead>
              <tr>
                <th>商品</th>
                <th>价格</th>
                <th>状态</th>
                <th>可售</th>
                <th>预占</th>
                <th>版本</th>
                <th>操作</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="product in products" :key="product.id" class="admin-product-row">
                <td>
                  <code>{{ product.code }}</code>
                  <strong>{{ product.name }}</strong>
                  <small>{{ product.categoryName }}</small>
                </td>
                <td>{{ formatPrice(product.price) }}</td>
                <td>
                  <el-tag :type="product.onSale ? 'success' : 'info'" effect="dark" round>
                    {{ product.onSale ? '上架' : '下架' }}
                  </el-tag>
                </td>
                <td>{{ product.available }}</td>
                <td>{{ product.reserved }}</td>
                <td>
                  <code>p{{ product.version }} / s{{ product.stockVersion }}</code>
                </td>
                <td class="row-actions">
                  <el-button class="edit-button" size="small" @click="openEdit(product)">
                    改价/上下架
                  </el-button>
                  <el-button class="stock-button" size="small" @click="openStock(product)">
                    调库存
                  </el-button>
                </td>
              </tr>
            </tbody>
          </table>
        </div>

        <div v-if="hasMore" class="load-more-row">
          <el-button class="load-more" :loading="loadingMore" @click="loadMore">
            加载更多
          </el-button>
          <span>游标分页，不用 OFFSET</span>
        </div>

        <section class="audit-section">
          <div class="section-heading">
            <div>
              <p class="eyebrow">ADMIN AUDIT</p>
              <h3>最近操作</h3>
            </div>
          </div>
          <div v-if="audit.length > 0" class="audit-list">
            <article v-for="entry in audit" :key="entry.id" class="audit-row">
              <span>{{ formatTime(entry.createdAt) }}</span>
              <strong>{{ entry.actorName }}</strong>
              <code>{{ entry.action }}</code>
              <p>{{ entry.targetType }} #{{ entry.targetId }} · {{ entry.detail }}</p>
            </article>
          </div>
          <p v-else class="audit-empty">还没有管理操作。</p>
        </section>
      </template>

      <el-empty v-else-if="!loading && !error" description="没有可管理的门店" />
    </section>

    <aside class="scope-note">
      <span>B5 / PREVIEW + IDEMPOTENCY</span>
      <p>
        批量导入先 dry-run：逐行返回可用/错误与预计前后值，不写库存；
        正式提交仍复用 B4 的全有或全无、幂等批次。
      </p>
    </aside>

    <el-dialog v-model="editVisible" title="商品设置" width="min(460px, 92vw)">
      <div class="dialog-form">
        <label>价格（元）</label>
        <el-input v-model="editPrice" placeholder="例如 32.00" />
        <label>上架</label>
        <el-switch v-model="editOnSale" active-text="上架" inactive-text="下架" />
        <p class="version-hint">当前商品版本 {{ editTarget?.version }}</p>
      </div>
      <template #footer>
        <el-button @click="editVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="saveEdit">保存设置</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="batchVisible" title="批量库存调整" width="min(560px, 94vw)">
      <div class="dialog-form">
        <p class="batch-help">
          每行一条：<code>productId,delta,reason</code>。整批全有或全无，失败会全部回滚。
        </p>
        <el-input
          v-model="batchText"
          type="textarea"
          :rows="7"
          placeholder="1001,1,到货补货&#10;1002,-1,盘点损耗"
          @input="previewResult = null"
        />
        <div v-if="previewResult" class="preview-box">
          <p :class="previewResult.valid ? 'preview-ok' : 'preview-bad'">
            {{ previewResult.valid ? '校验通过，可以提交' : `发现 ${previewResult.errorCount} 行错误` }}
          </p>
          <article
            v-for="line in previewResult.lines"
            :key="line.lineNo"
            class="preview-line"
            :class="{ invalid: !line.valid }"
          >
            <span>第 {{ line.lineNo }} 行 · #{{ line.productId }}</span>
            <strong>{{ line.valid ? '可用' : line.code }}</strong>
            <em>
              {{ line.valid
                ? `${line.availableBefore} → ${line.availableAfter}`
                : line.message }}
            </em>
          </article>
        </div>
      </div>
      <template #footer>
        <el-button @click="batchVisible = false">取消</el-button>
        <el-button :loading="batchPreviewing" @click="previewBatch">校验预览</el-button>
        <el-button type="primary" :loading="batchSaving" @click="saveBatch">
          提交批次
        </el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="stockVisible" title="调整可售库存" width="min(460px, 92vw)">
      <div class="dialog-form">
        <label>调整量（正数补货，负数扣减）</label>
        <el-input v-model="stockDelta" placeholder="例如 5 或 -3" />
        <label>原因</label>
        <el-input v-model="stockReason" placeholder="例如 到货补货 / 盘点损耗" />
        <p class="version-hint">当前库存版本 {{ stockTarget?.stockVersion }}</p>
      </div>
      <template #footer>
        <el-button @click="stockVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="saveStock">保存调整</el-button>
      </template>
    </el-dialog>
  </main>
</template>

<style scoped>
.page {
  width: min(1220px, 100%);
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
}

.hero {
  display: grid;
  grid-template-columns: minmax(0, 1.35fr) minmax(240px, 0.65fr);
  gap: clamp(30px, 6vw, 70px);
  align-items: start;
  padding: clamp(46px, 6vw, 70px) 0 40px;
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
  font-size: clamp(3.2rem, 7vw, 5.2rem);
  font-weight: 500;
  line-height: 0.95;
  letter-spacing: -0.06em;
}

.lede {
  max-width: 58ch;
  margin: 0;
  color: var(--muted-strong);
  font-size: 0.92rem;
  line-height: 1.85;
}

.admin-stamp {
  display: grid;
  justify-items: start;
  padding: 24px;
  border: 1.5px solid var(--ink);
  background: var(--ink);
  color: var(--paper);
  box-shadow: 6px 6px 0 var(--mustard);
}

.admin-stamp span,
.admin-stamp small {
  color: rgba(244, 239, 228, 0.58);
  font-family: var(--font-mono);
  font-size: 0.6rem;
  letter-spacing: 0.1em;
}

.admin-stamp strong {
  margin: 16px 0 5px;
  font-family: var(--font-display);
  font-size: 2.3rem;
  font-weight: 500;
}

.catalog-section {
  padding: 42px 0;
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

h3 {
  margin: 6px 0 0;
  font-family: var(--font-display);
  font-size: 1.7rem;
  font-weight: 500;
}

.catalog-tools {
  display: grid;
  justify-items: end;
  gap: 8px;
}

.section-meta {
  margin: 0;
  color: var(--muted);
  font-family: var(--font-mono);
  font-size: 0.62rem;
  line-height: 1.8;
  text-align: right;
}

.batch-help {
  margin: 0 0 6px;
  color: var(--muted);
  font-size: 0.7rem;
  line-height: 1.7;
}

.batch-help code {
  font-size: 0.65rem;
}

.preview-box {
  margin-top: 10px;
  padding: 10px 12px;
  border: 1px solid var(--line-strong);
  background: #fffdf8;
}

.preview-box p {
  margin: 0 0 6px;
  font-family: var(--font-mono);
  font-size: 0.64rem;
}

.preview-ok {
  color: var(--green-strong, #2f6b43);
}

.preview-bad {
  color: var(--ketchup);
}

.preview-line {
  display: grid;
  grid-template-columns: 120px 160px minmax(0, 1fr);
  gap: 8px;
  padding: 4px 0;
  border-top: 1px solid var(--line);
  font-size: 0.68rem;
}

.preview-line span,
.preview-line em {
  color: var(--muted);
  font-family: var(--font-mono);
  font-size: 0.58rem;
  font-style: normal;
}

.preview-line strong {
  color: var(--green-strong, #2f6b43);
  font-size: 0.58rem;
  white-space: nowrap;
}

.preview-line.invalid strong,
.preview-line.invalid em {
  color: var(--ketchup);
}

.load-more-row {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 12px;
  padding: 18px 0 4px;
}

.load-more-row span {
  color: var(--muted);
  font-family: var(--font-mono);
  font-size: 0.6rem;
}

.admin-alert {
  margin-top: 20px;
  border: 1.5px solid var(--ink);
  border-radius: 2px;
}

.admin-alert p {
  margin: 0 0 4px;
}

.alert-meta {
  font-size: 0.72rem;
}

.store-tabs {
  display: flex;
  gap: 10px;
  flex-wrap: wrap;
  margin-top: 24px;
}

.store-tab {
  display: grid;
  gap: 3px;
  min-width: 190px;
  padding: 12px 14px;
  border: 1.5px solid var(--line-strong);
  background: #fffdf8;
  color: var(--ink);
  text-align: left;
  cursor: pointer;
}

.store-tab span {
  color: var(--ketchup);
  font-family: var(--font-mono);
  font-size: 0.56rem;
}

.store-tab strong {
  font-family: var(--font-display);
  font-size: 1.1rem;
  font-weight: 500;
}

.store-tab small {
  color: var(--muted);
  font-size: 0.62rem;
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
  margin-top: 22px;
  padding: 38px;
  border: 1.5px dashed var(--line-strong);
  color: var(--muted);
  font-family: var(--font-mono);
  font-size: 0.7rem;
  text-align: center;
}

.admin-products {
  margin-top: 24px;
  overflow-x: auto;
  border-top: 1.5px solid var(--ink);
}

table {
  width: 100%;
  border-collapse: collapse;
  font-size: 0.75rem;
}

th {
  padding: 10px 8px;
  color: var(--muted);
  font-family: var(--font-mono);
  font-size: 0.58rem;
  font-weight: 500;
  text-align: left;
  white-space: nowrap;
}

td {
  padding: 13px 8px;
  border-top: 1px solid var(--line);
  vertical-align: middle;
}

td:first-child {
  display: grid;
  gap: 2px;
  justify-items: start;
  min-width: 170px;
}

td code {
  color: var(--muted);
  font-size: 0.58rem;
}

td strong {
  font-family: var(--font-display);
  font-size: 1.02rem;
  font-weight: 500;
}

td small {
  color: var(--muted);
  font-size: 0.6rem;
}

.row-actions {
  display: flex;
  gap: 6px;
  flex-wrap: wrap;
}

.audit-section {
  padding-top: 32px;
}

.audit-list {
  margin-top: 16px;
  border-top: 1px solid var(--ink);
}

.audit-row {
  display: grid;
  grid-template-columns: 145px 120px 110px minmax(0, 1fr);
  gap: 12px;
  align-items: baseline;
  padding: 11px 4px;
  border-bottom: 1px solid var(--line);
}

.audit-row span {
  color: var(--muted);
  font-family: var(--font-mono);
  font-size: 0.58rem;
}

.audit-row strong {
  font-size: 0.7rem;
}

.audit-row code {
  justify-self: start;
  padding: 0;
  background: transparent;
  color: var(--ketchup);
  font-size: 0.58rem;
}

.audit-row p {
  margin: 0;
  overflow: hidden;
  color: var(--muted);
  font-family: var(--font-mono);
  font-size: 0.58rem;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.audit-empty {
  margin-top: 14px;
  color: var(--muted);
  font-size: 0.7rem;
}

.dialog-form {
  display: grid;
  gap: 9px;
}

.dialog-form label {
  margin-top: 8px;
  color: var(--muted-strong);
  font-size: 0.72rem;
}

.version-hint {
  margin: 8px 0 0;
  color: var(--muted);
  font-family: var(--font-mono);
  font-size: 0.62rem;
}

.scope-note {
  display: grid;
  grid-template-columns: 160px minmax(0, 1fr);
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
  max-width: 82ch;
  margin: 0;
  color: var(--muted);
  font-size: 0.75rem;
  line-height: 1.8;
}

@media (max-width: 900px) {
  .hero {
    grid-template-columns: 1fr;
  }

  .admin-stamp {
    max-width: 380px;
  }

  .audit-row {
    grid-template-columns: 1fr 1fr;
  }

  .audit-row p {
    grid-column: 1 / -1;
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

  .scope-note {
    grid-template-columns: 1fr;
    gap: 8px;
  }
}
</style>
