const decimalFormatter = new Intl.NumberFormat('zh-CN', {
  minimumFractionDigits: 2,
  maximumFractionDigits: 2,
})

/**
 * Formats a server-owned decimal string for display only. The cart and order
 * server must never treat the formatted value as authoritative.
 */
export function formatPrice(decimal: string): string {
  if (decimal.trim() === '') {
    return '—'
  }
  const value = Number(decimal)
  if (!Number.isFinite(value)) {
    return '—'
  }
  return `¥${decimalFormatter.format(value)}`
}
