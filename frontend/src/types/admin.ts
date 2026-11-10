export interface AdminProduct {
  id: number
  storeId: number
  code: string
  name: string
  categoryName: string
  price: string
  onSale: boolean
  version: number
  available: number
  reserved: number
  stockVersion: number
}

export interface AdminStock {
  productId: number
  available: number
  reserved: number
  version: number
}

export interface AdminAuditLog {
  id: number
  actorName: string
  action: string
  targetType: string
  targetId: number
  detail: string
  createdAt: string
}

export interface AdminProductPage {
  items: AdminProduct[]
  nextCursor: number | null
  hasMore: boolean
}

export interface AdminStockBatchLine {
  productId: number
  delta: number
  reason: string
}

export interface AdminStockBatch {
  batchId: number
  appliedCount: number
  lines: Array<AdminStockBatchLine & {
    availableBefore: number
    availableAfter: number
    stockVersionAfter: number
  }>
}

export interface AdminStockPreviewLine {
  lineNo: number
  productId: number | null
  delta: number | null
  reason: string | null
  valid: boolean
  code: string | null
  message: string | null
  availableBefore: number | null
  availableAfter: number | null
}

export interface AdminStockPreview {
  valid: boolean
  errorCount: number
  lines: AdminStockPreviewLine[]
}
