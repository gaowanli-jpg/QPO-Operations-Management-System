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
  test('delivered item MES and WMS actions use ticket-scoped pending keys', () => {
    const source = readSource('components', 'DeliveredItemManage.vue')

    expect(source).toContain("import { usePendingAction } from '../composables/usePendingAction'")
    expect(source).toContain('const { isPending, runAction } = usePendingAction()')
    expect(source).toContain('mesSync: (ticketId) => `delivery:mes-sync:${ticketId}`')
    expect(source).toContain('wmsAllocation: (ticketId) => `delivery:wms-allocation:${ticketId}`')
    expect(source).toContain(':loading="isMesSyncPending(row)"')
    expect(source).toContain(':loading="isWmsAllocationPending(row)"')
    expect(source).toContain(':disabled="mesSyncAction(row).disabled || isDeliveredRowActionPending(row)"')
    expect(source).toContain(':disabled="wmsAllocationAction(row).disabled || isDeliveredRowActionPending(row)"')
  })

  test('delivered item MES and WMS handlers wrap submissions in runAction', () => {
    const source = readSource('components', 'DeliveredItemManage.vue')
    const mesBlock = blockBetween(source, 'const handleMesSync = async (row) =>', 'const wmsAllocationAction = (row) =>')
    const wmsBlock = blockBetween(source, 'const handleWmsAllocation = async (row) =>', 'const openLockedItems = (row) =>')

    expect(mesBlock).toContain('return runAction(deliveredActionKeys.mesSync(row.id), async () =>')
    expect(mesBlock).toContain('await syncMesMaterial(row.id)')
    expect(wmsBlock).toContain('return runAction(deliveredActionKeys.wmsAllocation(row.id), async () =>')
    expect(wmsBlock).toContain('await submitWmsAllocation(row.id)')
  })

  test('delivery document exports use button-level pending loading', () => {
    const source = readSource('components', 'DeliveryDocumentManage.vue')
    const ticketTable = readSource('components', 'delivery-document', 'DeliveryTicketRowsTable.vue')
    const processTable = readSource('components', 'delivery-document', 'ProcessSheetRowsTable.vue')
    const exportBlock = blockBetween(source, 'const exportDeliveryDocumentExcel = async (type) =>', '</script>')

    expect(source).toContain("import { usePendingAction } from '../composables/usePendingAction'")
    expect(source).toContain('exportTickets: () => `delivery:document:export:tickets:${selectedRfqId.value || \'none\'}`')
    expect(source).toContain('exportProcessSheets: () => `delivery:document:export:process-sheets:${selectedRfqId.value || \'none\'}`')
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

  test('WMS allocation key suppresses duplicate submitWmsAllocation calls while pending', async () => {
    const { runAction } = usePendingAction()
    const pendingRequest = deferred()
    const submitWmsAllocation = vi.fn(() => pendingRequest.promise)

    const firstClick = runAction('delivery:wms-allocation:310', submitWmsAllocation)
    const secondClick = runAction('delivery:wms-allocation:310', submitWmsAllocation)

    await expect(secondClick).resolves.toBeUndefined()
    expect(submitWmsAllocation).toHaveBeenCalledTimes(1)

    pendingRequest.resolve({ taskId: 7001 })

    await expect(firstClick).resolves.toEqual({ taskId: 7001 })
    expect(submitWmsAllocation).toHaveBeenCalledTimes(1)
  })
})
