import { describe, expect, it } from 'vitest'
import { canHideSalesChangeNotice, canRestoreSalesChangeNotice, canSelectSalesChangeNotice } from './services/salesOrderChangePolicy'

describe('sales change notice selection policy', () => {
  it('allows all retryable states in manual mode', () => {
    expect(canSelectSalesChangeNotice({ status: 'PENDING' }, 'MANUAL')).toBe(true)
    expect(canSelectSalesChangeNotice({ status: 'FAILED' }, 'MANUAL')).toBe(true)
    expect(canSelectSalesChangeNotice({ status: 'RESULT_UNKNOWN' }, 'MANUAL')).toBe(true)
  })

  it('allows old pending backlog and retryable notices in automatic mode', () => {
    expect(canSelectSalesChangeNotice({ status: 'PENDING', autoDispatchEpoch: 2 }, 'AUTO', 3)).toBe(true)
    expect(canSelectSalesChangeNotice({ status: 'PENDING', autoDispatchEpoch: 3 }, 'AUTO', 3)).toBe(false)
    expect(canSelectSalesChangeNotice({ status: 'FAILED' }, 'AUTO', 3)).toBe(true)
    expect(canSelectSalesChangeNotice({ status: 'RESULT_UNKNOWN' }, 'AUTO', 3)).toBe(true)
  })

  it('does not allow sending or already sent notices', () => {
    expect(canSelectSalesChangeNotice({ status: 'SENDING' }, 'MANUAL')).toBe(false)
    expect(canSelectSalesChangeNotice({ status: 'SENT' }, 'AUTO')).toBe(false)
  })

  it('hides and restores only visible, retryable notices', () => {
    expect(canHideSalesChangeNotice({ status: 'PENDING', hidden: false })).toBe(true)
    expect(canHideSalesChangeNotice({ status: 'SENDING', hidden: false })).toBe(false)
    expect(canHideSalesChangeNotice({ status: 'SENT', hidden: false })).toBe(false)
    expect(canHideSalesChangeNotice({ status: 'FAILED', hidden: true })).toBe(false)
    expect(canRestoreSalesChangeNotice({ status: 'FAILED', hidden: true })).toBe(true)
    expect(canRestoreSalesChangeNotice({ status: 'SENT', hidden: true })).toBe(false)
  })

  it('never allows hidden notices to be sent', () => {
    expect(canSelectSalesChangeNotice({ status: 'FAILED', hidden: true }, 'MANUAL')).toBe(false)
    expect(canSelectSalesChangeNotice({ status: 'PENDING', hidden: true, autoDispatchEpoch: 0 }, 'AUTO', 3)).toBe(false)
  })
})
