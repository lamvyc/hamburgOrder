import { describe, expect, it } from 'vitest'
import { formatPrice } from './format'

describe('formatPrice', () => {
  it('renders a server decimal string as CNY with two fraction digits', () => {
    expect(formatPrice('32.00')).toBe('¥32.00')
  })

  it('groups thousands without converting the source of truth', () => {
    expect(formatPrice('1288.5')).toBe('¥1,288.50')
  })

  it('returns a dash for a malformed decimal', () => {
    expect(formatPrice('not-a-price')).toBe('—')
  })
})
