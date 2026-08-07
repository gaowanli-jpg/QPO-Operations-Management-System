import { describe, expect, test } from 'vitest'
import { hydrateReleaseItemDrafts } from './components/marketReleaseDrafts'

describe('market release draft persistence', () => {
  test('preserves selected row drafts when a page is reloaded', () => {
    const freshRow = {
      id: 301,
      salesOrderDate: '2026-08-08',
      customerOrderNo: 'SERVER-ORDER',
      customerProjectNo: 'SERVER-PROJECT',
      customerOrderTitle: 'Server title',
      orderType: 'Official',
      internalProjectNo: 'SERVER-INTERNAL'
    }
    const selectedRow = {
      ...freshRow,
      salesOrderDateDraft: '2026-08-09',
      customerOrderNoDraft: 'DRAFT-ORDER',
      customerProjectNoDraft: 'DRAFT-PROJECT',
      customerOrderTitleDraft: 'Draft title',
      orderTypeDraft: 'Forecast',
      internalProjectNoDraft: 'DRAFT-INTERNAL'
    }

    const hydrated = hydrateReleaseItemDrafts(freshRow, selectedRow)

    expect(hydrated.customerOrderNo).toBe('SERVER-ORDER')
    expect(hydrated.salesOrderDateDraft).toBe('2026-08-09')
    expect(hydrated.customerOrderNoDraft).toBe('DRAFT-ORDER')
    expect(hydrated.customerProjectNoDraft).toBe('DRAFT-PROJECT')
    expect(hydrated.customerOrderTitleDraft).toBe('Draft title')
    expect(hydrated.orderTypeDraft).toBe('Forecast')
    expect(hydrated.internalProjectNoDraft).toBe('DRAFT-INTERNAL')
  })

  test('initializes drafts from persisted values for an unselected row', () => {
    const hydrated = hydrateReleaseItemDrafts({
      id: 302,
      version: 'A',
      description: 'Part',
      salesOrderDate: '2026-08-08',
      customerOrderNo: null
    })

    expect(hydrated.versionDraft).toBe('A')
    expect(hydrated.descriptionDraft).toBe('Part')
    expect(hydrated.salesOrderDateDraft).toBe('2026-08-08')
    expect(hydrated.customerOrderNoDraft).toBe('')
  })
})
