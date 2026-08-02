import { describe, expect, test, vi } from 'vitest'
import { readFileSync } from 'node:fs'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'
import { usePendingAction } from './composables/usePendingAction'

const currentDir = dirname(fileURLToPath(import.meta.url))
const readSource = (...segments) => readFileSync(resolve(currentDir, ...segments), 'utf8')

const blockBetween = (source, start, end) => {
  const startIndex = source.indexOf(start)
  const endIndex = source.indexOf(end, startIndex)
  expect(startIndex, `Expected source to include start marker ${start}`).toBeGreaterThanOrEqual(0)
  expect(endIndex, `Expected source to include end marker ${end}`).toBeGreaterThan(startIndex)
  return source.slice(startIndex, endIndex)
}

const deferred = () => {
  let resolve
  const promise = new Promise((done) => {
    resolve = done
  })
  return { promise, resolve }
}

describe('delivery duplicate click guards', () => {
  test('delivered item MES and WMS toolbar actions use batch-scoped pending keys', () => {
    const source = readSource('components', 'DeliveredItemManage.vue')

    expect(source).toContain("import { usePendingAction } from '../composables/usePendingAction'")
    expect(source).toContain('const { isPending, runAction } = usePendingAction()')
    expect(source).toContain("batchMesSync: () => 'delivery:mes-sync:batch'")
    expect(source).toContain("batchWmsAllocation: () => 'delivery:wms-allocation:batch'")
    expect(source).toContain(':loading="isBatchMesSyncPending"')
    expect(source).toContain(':loading="isBatchWmsAllocationPending"')
    expect(source).toContain(':disabled="selectedTicketIds.length === 0 || isBatchMesSyncPending"')
    expect(source).toContain(':disabled="selectedTicketIds.length === 0 || isBatchWmsAllocationPending"')
    expect(source).not.toContain('mesSync: (ticketId) => `delivery:mes-sync:${ticketId}`')
    expect(source).not.toContain('wmsAllocation: (ticketId) => `delivery:wms-allocation:${ticketId}`')
  })

  test('delivered item MES and WMS batch handlers wrap submissions in runAction', () => {
    const source = readSource('components', 'DeliveredItemManage.vue')
    const mesBlock = blockBetween(source, 'const handleBatchMesSync = async () =>', 'const handleBatchWmsAllocation = async () =>')
    const wmsBlock = blockBetween(source, 'const handleBatchWmsAllocation = async () =>', 'const openLockedItems = (row) =>')

    expect(mesBlock).toContain('return runAction(deliveredActionKeys.batchMesSync(), async () =>')
    expect(mesBlock).toContain('await syncMesMaterialBatch(selectedTicketIds.value)')
    expect(wmsBlock).toContain('return runAction(deliveredActionKeys.batchWmsAllocation(), async () =>')
    expect(wmsBlock).toContain('await submitWmsAllocationBatch(selectedTicketIds.value)')
  })

  test('delivery document exports use button-level pending loading', () => {
    const source = readSource('components', 'DeliveryDocumentManage.vue')
    const ticketTable = readSource('components', 'delivery-document', 'DeliveryTicketRowsTable.vue')
    const processTable = readSource('components', 'delivery-document', 'ProcessSheetRowsTable.vue')
    const exportBlock = blockBetween(source, 'const exportDeliveryDocumentExcel = async (type) =>', '</script>')

    expect(source).toContain("import { usePendingAction } from '../composables/usePendingAction'")
    expect(source).toContain('exportTickets: () => `delivery:document:export:tickets:${sourceKey.value}`')
    expect(source).toContain('exportProcessSheets: () => `delivery:document:export:process-sheets:${sourceKey.value}`')
    expect(source).toContain(':export-loading="ticketExportLoading"')
    expect(source).toContain(':export-loading="processSheetExportLoading"')
    expect(exportBlock).toContain('return runAction(actionKey(), async () =>')
    expect(exportBlock).toContain('await downloadDeliveryDocumentExport(')

    for (const tableSource of [ticketTable, processTable]) {
      expect(tableSource).toContain('exportLoading: {')
      expect(tableSource).toContain(':loading="exportLoading"')
      expect(tableSource).toContain(':disabled="!exportable || exportLoading"')
    }
  })

  test('locked item list keeps query loading scoped to the table', () => {
    const source = readSource('components', 'LockedItemManage.vue')

    expect(source).toContain('const loading = ref(false)')
    expect(source).toContain('v-loading="loading"')
    expect(source).toContain('loading.value = true')
    expect(source).toContain('loading.value = false')
    expect(source).not.toContain('usePendingAction')
  })

  test('WMS batch allocation key suppresses duplicate submitWmsAllocationBatch calls while pending', async () => {
    const { runAction } = usePendingAction()
    const pendingRequest = deferred()
    const submitWmsAllocationBatch = vi.fn(() => pendingRequest.promise)

    const firstClick = runAction('delivery:wms-allocation:batch', submitWmsAllocationBatch)
    const secondClick = runAction('delivery:wms-allocation:batch', submitWmsAllocationBatch)

    await expect(secondClick).resolves.toBeUndefined()
    expect(submitWmsAllocationBatch).toHaveBeenCalledTimes(1)

    pendingRequest.resolve({ taskId: 7001 })

    await expect(firstClick).resolves.toEqual({ taskId: 7001 })
    expect(submitWmsAllocationBatch).toHaveBeenCalledTimes(1)
  })
})
