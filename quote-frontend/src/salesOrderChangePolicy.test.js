import { describe, expect, it } from 'vitest'
import { canSelectSalesChangeNotice } from './services/salesOrderChangePolicy'

describe('sales change notice selection policy', () => {
  it('allows all retryable states in manual mode', () => {
    expect(canSelectSalesChangeNotice({ status: 'PENDING' }, 'MANUAL')).toBe(true)
    expect(canSelectSalesChangeNotice({ status: 'FAILED' }, 'MANUAL')).toBe(true)
    expect(canSelectSalesChangeNotice({ status: 'RESULT_UNKNOWN' }, 'MANUAL')).toBe(true)
  })

  it('only allows failed and unknown notices for manual retry in automatic mode', () => {
    expect(canSelectSalesChangeNotice({ status: 'PENDING' }, 'AUTO')).toBe(false)
    expect(canSelectSalesChangeNotice({ status: 'FAILED' }, 'AUTO')).toBe(true)
    expect(canSelectSalesChangeNotice({ status: 'RESULT_UNKNOWN' }, 'AUTO')).toBe(true)
  })

  it('does not allow sending or already sent notices', () => {
    expect(canSelectSalesChangeNotice({ status: 'SENDING' }, 'MANUAL')).toBe(false)
    expect(canSelectSalesChangeNotice({ status: 'SENT' }, 'AUTO')).toBe(false)
  })
})
