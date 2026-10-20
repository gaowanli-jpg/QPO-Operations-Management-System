import { describe, expect, test } from 'vitest'
import {
  collectReleaseRowsWithDraftChanges,
  hasReleaseDraftChange,
  hydrateReleaseItemDrafts,
  releaseDraftChangeSummary
} from './components/marketReleaseDrafts'

describe('market release draft persistence', () => {
  test('retains manufacturing field drafts across pagination and detects cleared values', () => {
    const fresh = {
      id: 301,
      manufacturingProcessType: '加工',
      reservedField1: '备用1',
      reservedField2: '备用2',
      reservedField3: '备用3'
    }
    const hydrated = hydrateReleaseItemDrafts(fresh, {
      ...fresh,
      manufacturingProcessTypeDraft: '人工覆盖',
      reservedField1Draft: '',
      reservedField2Draft: '新备用2',
      reservedField3Draft: '新备用3'
    })
    expect(hydrated.manufacturingProcessTypeDraft).toBe('人工覆盖')
    expect(hydrated.reservedField1Draft).toBe('')
    expect(hydrated.reservedField2Draft).toBe('新备用2')
    expect(hydrated.reservedField3Draft).toBe('新备用3')
    expect(hasReleaseDraftChange(hydrated, 'reservedField1Draft', 'reservedField1')).toBe(true)
    expect(releaseDraftChangeSummary(hydrated)).toMatchObject({ orderFields: true, any: true })
  })

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
      materialName: 'Material note',
      salesOrderDate: '2026-08-08',
      customerOrderNo: null
    })

    expect(hydrated.versionDraft).toBe('A')
    expect(hydrated.materialNameDraft).toBe('Material note')
    expect(hydrated.descriptionDraft).toBeUndefined()
    expect(hydrated.salesOrderDateDraft).toBe('2026-08-08')
    expect(hydrated.customerOrderNoDraft).toBe('')
  })

  test('collects unique release rows with unsaved draft changes', () => {
    const selectedRows = [
      {
        id: 401,
        releaseItemKey: 'RFQ:401',
        version: 'A',
        versionDraft: 'A'
      },
      {
        id: 402,
        releaseItemKey: 'RFQ:402',
        customerOrderNo: null,
        customerOrderNoDraft: 'CO-402'
      }
    ]
    const currentPageRows = [
      {
        id: 401,
        releaseItemKey: 'RFQ:401',
        version: 'A',
        versionDraft: 'B'
      },
      {
        id: 403,
        releaseItemKey: 'RFQ:403',
        materialName: 'Same material name',
        materialNameDraft: 'Same material name'
      }
    ]

    const changedRows = collectReleaseRowsWithDraftChanges(
      [...selectedRows, ...currentPageRows],
      row => row.releaseItemKey
    )

    expect(changedRows.map(row => row.id)).toEqual([401, 402])
    expect(changedRows[0].versionDraft).toBe('B')
    expect(hasReleaseDraftChange({ remark: null, remarkDraft: '' }, 'remarkDraft', 'remark')).toBe(false)
    expect(releaseDraftChangeSummary(changedRows[0])).toMatchObject({
      version: true,
      internalTargetLeadTime: false,
      fields: false,
      orderFields: false,
      any: true
    })
    expect(releaseDraftChangeSummary(changedRows[1])).toMatchObject({
      orderFields: true,
      any: true
    })
  })
})
