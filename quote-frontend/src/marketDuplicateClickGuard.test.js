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

describe('market duplicate click guards', () => {
  test('market RFQ import uses a pending action key for submit loading and duplicate guard', () => {
    const source = readSource('components', 'MarketDashboard.vue')
    const submitBlock = blockBetween(source, 'const submitRfqPackage = async () =>', '</script>')

    expect(source).toContain("import { usePendingAction } from '../composables/usePendingAction'")
    expect(source).toContain("const IMPORT_RFQ_ACTION_KEY = 'market:rfq:import'")
    expect(source).toContain(':loading="isPending(IMPORT_RFQ_ACTION_KEY)"')
    expect(source).toContain(':disabled="isPending(IMPORT_RFQ_ACTION_KEY)"')
    expect(submitBlock).toContain('return runAction(IMPORT_RFQ_ACTION_KEY, async () =>')
    expect(submitBlock).toContain('await importRfq(formData)')
  })

  test('market release registers pending keys for import, drawings, lead-time, version and generation actions', () => {
    const source = readSource('components', 'MarketRelease.vue')

    expect(source).toContain("import { usePendingAction } from '../composables/usePendingAction'")
    expect(source).toContain('const { isPending, runAction } = usePendingAction()')
    expect(source).toContain("importSelection: 'market:release:import-selection'")
    expect(source).toContain("replaceDrawings: 'market:release:replace-drawings'")
    expect(source).toContain("shiftInternalLeadTime: 'market:release:shift-internal-lead-time'")
    expect(source).toContain("setInternalTargetLeadTime: 'market:release:set-internal-target-lead-time'")
    expect(source).toContain('version: (itemId) => `market:release:version:${itemId}`')
    expect(source).toContain('internalLeadTime: (itemId) => `market:release:internal-lead-time:${itemId}`')
    expect(source).toContain('generateTicket: () => `market:release:generate-ticket:${selectedOrderIds.value.join')
  })

  test('market release wraps write actions with runAction duplicate guards', () => {
    const source = readSource('components', 'MarketRelease.vue')

    const versionBlock = blockBetween(source, 'const saveReleaseItemVersion = async (row) =>', 'const handleSelectionChange')
    expect(versionBlock).toContain('return runAction(releaseActionKeys.version(row.id), async () =>')
    expect(versionBlock).toContain('await updateReleaseItemVersion(row.id')

    const importBlock = blockBetween(source, 'const handleSelectionExcelChange = async (uploadFile) =>', 'const openDrawingReplaceDialog')
    expect(importBlock).toContain('return runAction(releaseActionKeys.importSelection, async () =>')
    expect(importBlock).toContain('await parsePartSelectionBatch(formData)')

    const replaceBlock = blockBetween(source, 'const submitReplacementDrawings = async () =>', 'const adjustInternalLeadTime')
    expect(replaceBlock).toContain('return runAction(releaseActionKeys.replaceDrawings, async () =>')
    expect(replaceBlock).toContain('await replaceReleaseDrawings(formData)')

    const shiftBlock = blockBetween(source, 'const adjustInternalLeadTime = async (direction) =>', 'const setInternalTargetLeadTime')
    expect(shiftBlock).toContain('return runAction(releaseActionKeys.shiftInternalLeadTime, async () =>')
    expect(shiftBlock).toContain('await shiftInternalTargetLeadTime')

    const setBlock = blockBetween(source, 'const setInternalTargetLeadTime = async () =>', 'const saveInternalTargetLeadTime')
    expect(setBlock).toContain('return runAction(releaseActionKeys.setInternalTargetLeadTime, async () =>')
    expect(setBlock).toContain('await setInternalTargetLeadTimeRequest')

    const rowLeadTimeBlock = blockBetween(source, 'const saveInternalTargetLeadTime = async (row) =>', 'const submitTicket')
    expect(rowLeadTimeBlock).toContain('return runAction(releaseActionKeys.internalLeadTime(row.id), async () =>')
    expect(rowLeadTimeBlock).toContain('await updateInternalTargetLeadTime')

    const submitBlock = blockBetween(source, 'const submitTicket = async () =>', 'const resolveDeliveryLeadTime')
    expect(submitBlock).toContain('return runAction(releaseActionKeys.generateTicket(), async () =>')
    expect(submitBlock).toContain('await ElMessageBox.confirm')
    expect(submitBlock).toContain('await generateTicket(payload)')
  })

  test('market action keys suppress duplicate API calls while pending', async () => {
    const { runAction } = usePendingAction()
    const pendingRequest = deferred()
    const generateTicketApi = vi.fn(() => pendingRequest.promise)

    const firstClick = runAction('market:release:generate-ticket:101', generateTicketApi)
    const secondClick = runAction('market:release:generate-ticket:101', generateTicketApi)

    await expect(secondClick).resolves.toBeUndefined()
    expect(generateTicketApi).toHaveBeenCalledTimes(1)

    pendingRequest.resolve({ success: true })

    await expect(firstClick).resolves.toEqual({ success: true })
    expect(generateTicketApi).toHaveBeenCalledTimes(1)
  })
})
