import { ApiContractError, http } from './http'
import type {
  AdminAuditLog,
  AdminProduct,
  AdminProductPage,
  AdminStock,
  AdminStockBatch,
  AdminStockBatchLine,
  AdminStockPreview,
} from '@/types/admin'

export async function fetchAdminProducts(storeId: number): Promise<AdminProduct[]> {
  const response = await http.get<unknown>(`/admin/stores/${storeId}/products`)
  if (!Array.isArray(response.data) || !response.data.every(isAdminProduct)) {
    throw new ApiContractError('管理员商品响应不符合契约')
  }
  return response.data
}

export async function fetchAdminProductPage(
  storeId: number,
  params: { afterId?: number | null; limit?: number; onSale?: boolean | null },
): Promise<AdminProductPage> {
  const response = await http.get<unknown>(`/admin/stores/${storeId}/products/page`, {
    params: {
      limit: params.limit ?? 10,
      ...(params.afterId == null ? {} : { afterId: params.afterId }),
      ...(params.onSale == null ? {} : { onSale: params.onSale }),
    },
  })
  if (!isAdminProductPage(response.data)) {
    throw new ApiContractError('管理商品分页响应不符合契约')
  }
  return response.data
}

export async function updateAdminProduct(
  productId: number,
  body: { price: string; onSale: boolean; version: number },
): Promise<AdminProduct> {
  const response = await http.patch<unknown>(`/admin/products/${productId}`, body)
  if (!isAdminProduct(response.data)) {
    throw new ApiContractError('商品更新响应不符合契约')
  }
  return response.data
}

export async function adjustAdminStock(
  productId: number,
  body: { delta: number; version: number; reason: string },
): Promise<AdminStock> {
  const response = await http.post<unknown>(
    `/admin/products/${productId}/stock-adjustments`,
    body,
  )
  if (!isAdminStock(response.data)) {
    throw new ApiContractError('库存调整响应不符合契约')
  }
  return response.data
}

export async function adjustAdminStockBatch(
  storeId: number,
  lines: AdminStockBatchLine[],
  idempotencyKey: string,
): Promise<AdminStockBatch> {
  const response = await http.post<unknown>(
    `/admin/stores/${storeId}/stock-adjustments/batch`,
    { lines },
    { headers: { 'Idempotency-Key': idempotencyKey } },
  )
  if (!isAdminStockBatch(response.data)) {
    throw new ApiContractError('批量库存响应不符合契约')
  }
  return response.data
}

export async function previewAdminStockBatch(
  storeId: number,
  lines: AdminStockBatchLine[],
): Promise<AdminStockPreview> {
  const response = await http.post<unknown>(
    `/admin/stores/${storeId}/stock-adjustments/batch/preview`,
    { lines },
  )
  if (!isAdminStockPreview(response.data)) {
    throw new ApiContractError('批量预览响应不符合契约')
  }
  return response.data
}

export async function fetchAdminAudit(limit = 10): Promise<AdminAuditLog[]> {
  const response = await http.get<unknown>('/admin/audit-logs', { params: { limit } })
  if (!Array.isArray(response.data) || !response.data.every(isAdminAudit)) {
    throw new ApiContractError('审计日志响应不符合契约')
  }
  return response.data
}

function isAdminProduct(value: unknown): value is AdminProduct {
  if (!isRecord(value)) return false
  return (
    typeof value.id === 'number' &&
    typeof value.storeId === 'number' &&
    typeof value.code === 'string' &&
    typeof value.name === 'string' &&
    typeof value.categoryName === 'string' &&
    typeof value.price === 'string' &&
    typeof value.onSale === 'boolean' &&
    typeof value.version === 'number' &&
    typeof value.available === 'number' &&
    typeof value.reserved === 'number' &&
    typeof value.stockVersion === 'number'
  )
}

function isAdminStockBatch(value: unknown): value is AdminStockBatch {
  if (!isRecord(value) || !Array.isArray(value.lines)) return false
  return (
    typeof value.batchId === 'number' &&
    typeof value.appliedCount === 'number' &&
    value.lines.every((line) => (
      isRecord(line) &&
      typeof line.productId === 'number' &&
      typeof line.delta === 'number' &&
      typeof line.reason === 'string' &&
      typeof line.availableBefore === 'number' &&
      typeof line.availableAfter === 'number' &&
      typeof line.stockVersionAfter === 'number'
    ))
  )
}

function isAdminStockPreview(value: unknown): value is AdminStockPreview {
  if (!isRecord(value) || !Array.isArray(value.lines)) return false
  return (
    typeof value.valid === 'boolean' &&
    typeof value.errorCount === 'number' &&
    value.lines.every((line) => (
      isRecord(line) &&
      typeof line.lineNo === 'number' &&
      typeof line.valid === 'boolean' &&
      (line.code === null || typeof line.code === 'string') &&
      (line.message === null || typeof line.message === 'string')
    ))
  )
}

function isAdminProductPage(value: unknown): value is AdminProductPage {
  if (!isRecord(value) || !Array.isArray(value.items)) return false
  return (
    value.items.every(isAdminProduct) &&
    (value.nextCursor === null || typeof value.nextCursor === 'number') &&
    typeof value.hasMore === 'boolean'
  )
}

function isAdminStock(value: unknown): value is AdminStock {
  if (!isRecord(value)) return false
  return (
    typeof value.productId === 'number' &&
    typeof value.available === 'number' &&
    typeof value.reserved === 'number' &&
    typeof value.version === 'number'
  )
}

function isAdminAudit(value: unknown): value is AdminAuditLog {
  if (!isRecord(value)) return false
  return (
    typeof value.id === 'number' &&
    typeof value.actorName === 'string' &&
    typeof value.action === 'string' &&
    typeof value.targetType === 'string' &&
    typeof value.targetId === 'number' &&
    typeof value.detail === 'string' &&
    typeof value.createdAt === 'string'
  )
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null
}
