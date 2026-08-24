import { describe, expect, test } from 'vitest'
import { existsSync, readFileSync } from 'node:fs'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'
import {
  applyReleaseItemDataReplacementResult,
  collectReleaseItemDraftReplacementConflicts
} from './components/marketReleaseDataReplacement'

const currentDir = dirname(fileURLToPath(import.meta.url))
const readOptionalSource = (...segments) => {
  const path = resolve(currentDir, ...segments)
  return existsSync(path) ? readFileSync(path, 'utf8') : ''
}

const marketReleaseSource = readOptionalSource('components', 'MarketRelease.vue')
const dialogSource = readOptionalSource('components', 'market-release', 'ReleaseItemDataReplacementDialog.vue')

const rowKey = (row) => row?.releaseItemKey || `${row?.sourceType || 'RFQ'}:${row?.sourceItemId ?? row?.id ?? ''}`

const blockBetween = (source, start, end) => {
  const startIndex = source.indexOf(start)
  const endIndex = source.indexOf(end, startIndex)
  expect(startIndex, `Expected source to include start marker ${start}`).toBeGreaterThanOrEqual(0)
  expect(endIndex, `Expected source to include end marker ${end}`).toBeGreaterThan(startIndex)
  return source.slice(startIndex, endIndex)
}

describe('release item data replacement stage 6 state convergence', () => {
  test('applies affected rows while only resetting drafts for fields replaced by Excel', () => {
    const itemList = [
      {
        releaseItemKey: 'RFQ:1',
        id: 1,
        sourceType: 'RFQ',
        sourceItemId: 1,
        description: 'old desc',
        descriptionDraft: 'local desc draft',
        remark: 'old remark',
        remarkDraft: 'local remark draft',
        customerOrderNo: 'old order',
        customerOrderNoDraft: 'local order draft',
        remainingQty: 10
      },
      {
        releaseItemKey: 'RFQ:2',
        id: 2,
        sourceType: 'RFQ',
        sourceItemId: 2,
        description: 'qty row',
        descriptionDraft: 'qty row draft',
        remainingQty: 8
      }
    ]
    const selectedItemIdMap = {
      'RFQ:1': { ...itemList[0] },
      'RFQ:3': {
        releaseItemKey: 'RFQ:3',
        id: 3,
        sourceType: 'RFQ',
        sourceItemId: 3,
        description: 'other selected',
        descriptionDraft: 'other draft',
        remainingQty: 9
      }
    }
    const acceptedQtyMap = {
      'RFQ:1': 2,
      'RFQ:2': 4,
      'RFQ:3': 7
    }

    const result = applyReleaseItemDataReplacementResult({
      itemList,
      selectedItemIdMap,
      acceptedQtyMap,
      rowKey,
      response: {
        affectedRows: [
          {
            releaseItemKey: 'RFQ:1',
            id: 1,
            sourceType: 'RFQ',
            sourceItemId: 1,
            description: 'db desc',
            remark: 'db remark unchanged by Excel',
            customerOrderNo: 'db order',
            remainingQty: 10
          },
          {
            releaseItemKey: 'RFQ:2',
            id: 2,
            sourceType: 'RFQ',
            sourceItemId: 2,
            description: 'qty row from db',
            remainingQty: 8
          }
        ],
        acceptedQtyUpdates: [
          { releaseItemKey: 'RFQ:2', itemId: 2, acceptedQty: 5 }
        ],
        previewRows: [
          {
            itemId: 1,
            status: 'CHANGED',
            changes: [
              { field: 'description' },
              { field: 'customerOrderNo' }
            ]
          },
          { itemId: 2, status: 'QUANTITY_ONLY', changes: [] }
        ]
      }
    })

    expect(result.itemList[0]).toMatchObject({
      description: 'db desc',
      descriptionDraft: 'db desc',
      customerOrderNo: 'db order',
      customerOrderNoDraft: 'db order',
      remark: 'db remark unchanged by Excel',
      remarkDraft: 'local remark draft'
    })
    expect(selectedItemIdMap['RFQ:1']).toMatchObject({
      description: 'db desc',
      descriptionDraft: 'db desc',
      customerOrderNo: 'db order',
      customerOrderNoDraft: 'db order',
      remarkDraft: 'local remark draft'
    })
    expect(selectedItemIdMap['RFQ:2']).toMatchObject({
      releaseItemKey: 'RFQ:2',
      description: 'qty row from db'
    })
    expect(acceptedQtyMap).toMatchObject({
      'RFQ:1': 2,
      'RFQ:2': 5,
      'RFQ:3': 7
    })
    expect(selectedItemIdMap['RFQ:3']).toMatchObject({
      descriptionDraft: 'other draft'
    })
  })

  test('detects only same-field unsaved drafts that will be overwritten by preview changes', () => {
    const conflicts = collectReleaseItemDraftReplacementConflicts({
      previewRows: [
        {
          itemId: 1,
          changes: [
            { field: 'description', label: '物料描述' },
            { field: 'customerOrderNo', label: '客户订单号' }
          ]
        },
        {
          itemId: 2,
          changes: [{ field: 'remark', label: '备注' }]
        }
      ],
      selectedRowsByKey: {
        'RFQ:1': {
          releaseItemKey: 'RFQ:1',
          description: 'old desc',
          descriptionDraft: 'unsaved desc',
          customerOrderNo: 'same order',
          customerOrderNoDraft: 'same order',
          remark: 'old remark',
          remarkDraft: 'unsaved but Excel does not touch remark'
        },
        'RFQ:2': {
          releaseItemKey: 'RFQ:2',
          remark: 'same remark',
          remarkDraft: 'same remark'
        }
      }
    })

    expect(conflicts).toEqual([
      {
        releaseItemKey: 'RFQ:1',
        field: 'description',
        label: '物料描述'
      }
    ])
  })

  test('preserves Excel-unaffected drafts and warns for current visible rows even when they are not selected', () => {
    const itemList = [
      {
        releaseItemKey: 'RFQ:8',
        id: 8,
        sourceType: 'RFQ',
        sourceItemId: 8,
        description: 'old desc',
        descriptionDraft: 'visible unsaved desc',
        remark: 'old remark',
        remarkDraft: 'visible unsaved remark',
        remainingQty: 6
      }
    ]
    const selectedItemIdMap = {}
    const acceptedQtyMap = {}

    const conflicts = collectReleaseItemDraftReplacementConflicts({
      previewRows: [
        {
          itemId: 8,
          changes: [{ field: 'description', label: '物料描述' }]
        }
      ],
      selectedRowsByKey: Object.fromEntries(itemList.map(row => [rowKey(row), row])),
      rowKey
    })
    const result = applyReleaseItemDataReplacementResult({
      itemList,
      selectedItemIdMap,
      acceptedQtyMap,
      rowKey,
      draftRowsByKey: Object.fromEntries(itemList.map(row => [rowKey(row), row])),
      response: {
        affectedRows: [
          {
            releaseItemKey: 'RFQ:8',
            id: 8,
            sourceType: 'RFQ',
            sourceItemId: 8,
            description: 'db desc',
            remark: 'db remark unchanged by Excel',
            remainingQty: 6
          }
        ],
        acceptedQtyUpdates: [],
        previewRows: [
          {
            itemId: 8,
            changes: [{ field: 'description', label: '物料描述' }]
          }
        ]
      }
    })

    expect(conflicts).toEqual([
      { releaseItemKey: 'RFQ:8', field: 'description', label: '物料描述' }
    ])
    expect(result.itemList[0]).toMatchObject({
      description: 'db desc',
      descriptionDraft: 'db desc',
      remark: 'db remark unchanged by Excel',
      remarkDraft: 'visible unsaved remark'
    })
    expect(selectedItemIdMap).toEqual({})
  })

  test('market release wires confirm results without clearing unrelated selection state', () => {
    expect(marketReleaseSource).toContain("import { applyReleaseItemDataReplacementResult")
    expect(marketReleaseSource).toContain(':selected-rows-by-key="dataReplacementDraftRowsByKey"')
    expect(marketReleaseSource).toContain('@confirmed="handleDataReplacementConfirmed"')

    const handlerBlock = blockBetween(
      marketReleaseSource,
      'const handleDataReplacementConfirmed = async (response) =>',
      'const openDrawingReplaceDialog = () =>'
    )
    expect(handlerBlock).toContain('applyReleaseItemDataReplacementResult')
    expect(handlerBlock).toContain('itemList.value = applied.itemList')
    expect(handlerBlock).toContain('selectedItemIdMap')
    expect(handlerBlock).toContain('acceptedQtyMap')
    expect(handlerBlock).toContain('draftRowsByKey: dataReplacementDraftRowsByKey.value')
    expect(handlerBlock).toContain('await syncReleaseItemTableSelection()')
    expect(handlerBlock).not.toContain('clearSelectedRows()')
  })

  test('dialog warns about draft overwrites and resets stale preview state on source change or close', () => {
    expect(dialogSource).toContain("import { collectReleaseItemDraftReplacementConflicts")
    expect(dialogSource).toContain('selectedRowsByKey')
    expect(dialogSource).toContain('draftConflictSummary')
    expect(dialogSource).toContain('v-if="draftConflictSummary"')
    expect(dialogSource).toContain('@closed="resetDialogState"')
    expect(dialogSource).toContain("emit('confirmed', {")
    expect(dialogSource).toContain('previewRows: previewRows.value')

    const sourceWatcher = blockBetween(
      dialogSource,
      'watch(sourceSignature, (next, previous) =>',
      'const clearPreviewState = () =>'
    )
    expect(sourceWatcher).toContain('resetDialogState()')
    expect(sourceWatcher).not.toContain('clearPreviewState()')
  })

  test('dialog paginates preview rows with the shared client-side pagination helper', () => {
    expect(dialogSource).toContain("import { CLIENT_PAGE_SIZES, DEFAULT_CLIENT_PAGE_SIZE, clampPage, paginateRows } from '../../utils/clientPagination'")
    expect(dialogSource).toContain(':data="pagedPreviewRows"')
    expect(dialogSource).toContain('const previewPagination = reactive({ page: 1, pageSize: DEFAULT_CLIENT_PAGE_SIZE })')
    expect(dialogSource).toContain('const previewRowTotal = computed(() => previewRows.value.length)')
    expect(dialogSource).toContain('const pagedPreviewRows = computed(() => paginateRows(previewRows.value, previewPagination))')
    expect(dialogSource).toContain('previewPagination.page = clampPage(previewPagination, previewRowTotal.value)')
    expect(dialogSource).toContain('previewPagination.page = 1')
    expect(dialogSource).toContain('layout="total, sizes, prev, pager, next, jumper"')
    expect(dialogSource).toContain(':page-sizes="CLIENT_PAGE_SIZES"')
  })

  test('formal ticket generation no longer caps accepted quantity by inquiry remaining quantity', () => {
    const submitBlock = blockBetween(
      marketReleaseSource,
      'const submitTicket = async () =>',
      'const resolveDeliveryLeadTime = (row) =>'
    )
    expect(submitBlock).not.toContain('overRemaining')
    expect(submitBlock).toContain('acceptedQtyMap[rowKey(row)]')
    expect(submitBlock).toContain('defaultAcceptedQtyFor(row)')
  })
})
