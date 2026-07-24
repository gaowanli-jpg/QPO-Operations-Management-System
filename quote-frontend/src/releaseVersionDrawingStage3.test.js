import { describe, expect, test } from 'vitest'
import { readFileSync } from 'node:fs'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

const currentDir = dirname(fileURLToPath(import.meta.url))
const readSource = (...segments) => readFileSync(resolve(currentDir, ...segments), 'utf8')

describe('release version edit stage 3', () => {
  test('market api exposes item version update endpoint', () => {
    const apiSource = readSource('services', 'marketApi.js')

    expect(apiSource).toContain('updateReleaseItemVersion')
    expect(apiSource).toContain('/api/market/release/items/${itemId}/version')
  })

  test('market release edits one row version without clearing cross-page selection', () => {
    const source = readSource('components', 'MarketRelease.vue')
    const saveBlock = source.slice(
      source.indexOf('const saveReleaseItemVersion = async (row) =>'),
      source.indexOf('const handleSelectionChange')
    )

    expect(source).toContain('updateReleaseItemVersion')
    expect(source).toContain('version: (itemId) => `market:release:version:${itemId}`')
    expect(source).toContain('const isVersionSaving = (row) => isPending(releaseActionKeys.version(row?.id))')
    expect(source).toContain('class="row-version-editor"')
    expect(source).toContain('v-model="row.versionDraft"')
    expect(source).toContain(':loading="isVersionSaving(row)"')
    expect(source).toContain('@click.stop="saveReleaseItemVersion(row)"')

    expect(saveBlock).toContain('return runAction(releaseActionKeys.version(row.id), async () =>')
    expect(saveBlock).toContain('updateReleaseItemVersion(row.id, {')
    expect(saveBlock).toContain('rfqId: rowRfqId')
    expect(saveBlock).toContain('version: row.versionDraft')
    expect(saveBlock).toContain('itemList.value.splice(index, 1, saved)')
    expect(saveBlock).toContain('selectedItemIdMap[row.id] = { ...selectedItemIdMap[row.id], ...saved }')
    expect(saveBlock).not.toContain('clearSelectedRows()')
    expect(saveBlock).not.toContain('fetchReleaseItemsBatch()')
  })

  test('generated tickets continue to use selected rows after version save refreshes the selected map', () => {
    const source = readSource('components', 'MarketRelease.vue')
    const submitBlock = source.slice(
      source.indexOf('const submitTicket = async () =>'),
      source.indexOf('const resolveDeliveryLeadTime')
    )

    expect(source).toContain('const selectedRows = computed(() => Object.values(selectedItemIdMap))')
    expect(source).toContain('selectedItemIdMap[row.id] = { ...selectedItemIdMap[row.id], ...saved }')
    expect(submitBlock).toContain('const orderedSelectedRows = selectedRowsInItemOrder()')
    expect(submitBlock).toContain('items: rows.map(row => ({')
  })
})
