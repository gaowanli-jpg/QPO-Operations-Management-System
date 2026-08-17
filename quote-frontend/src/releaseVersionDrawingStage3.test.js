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
    expect(apiSource).toContain('updateReleaseItemFields')
    expect(apiSource).toContain('/api/market/release/items/${itemId}/fields')
  })

  test('market release edits row part description surface and remark like version fields', () => {
    const source = readSource('components', 'MarketRelease.vue')
    const draftSource = readSource('components', 'marketReleaseDrafts.js')
    const removedField = 'customer' + 'Requirement'
    const fieldSaveBlock = source.slice(
      source.indexOf('const saveReleaseItemFields = async (row) =>'),
      source.indexOf('const handleSelectionChange')
    )
    const fieldPersistBlock = source.slice(
      source.indexOf('const persistReleaseItemFields = async (row) =>'),
      source.indexOf('const orderFieldPayloadForBulkSave')
    )
    const materialIndex = source.indexOf('prop="engineeringMaterialName" label="材料"')
    const descriptionDraftIndex = source.indexOf('v-model="row.descriptionDraft"')
    const surfaceTreatmentDraftIndex = source.indexOf('v-model="row.surfaceTreatmentDraft"')
    const remarkDraftIndex = source.indexOf('v-model="row.remarkDraft"')
    const statusIndex = source.indexOf('prop="status"', remarkDraftIndex)

    expect(source).toContain('updateReleaseItemFields')
    expect(source).toContain('fields: (releaseItemKey) => `market:release:fields:${releaseItemKey}`')
    expect(source).toContain('const isFieldSaving = (row) => isPending(releaseActionKeys.fields(rowKey(row)))')
    expect(source).toContain('v-model="row.descriptionDraft"')
    expect(source).toContain('v-model="row.surfaceTreatmentDraft"')
    expect(source).toContain('v-model="row.remarkDraft"')
    expect(source).not.toContain(`v-model="row.${removedField}Draft"`)
    expect(source).toContain(':loading="isFieldSaving(row)"')
    expect(source).toContain('@click.stop="saveReleaseItemFields(row)"')
    expect(source).toContain('hydrateReleaseItemDrafts')
    expect(source).toContain("from './marketReleaseDrafts'")
    expect(draftSource).toContain("descriptionDraft: 'description'")
    expect(draftSource).toContain("surfaceTreatmentDraft: 'surfaceTreatment'")
    expect(draftSource).toContain("remarkDraft: 'remark'")
    expect(draftSource).not.toContain(`${removedField}Draft: '${removedField}'`)
    expect(materialIndex).toBeGreaterThanOrEqual(0)
    expect(descriptionDraftIndex).toBeGreaterThan(materialIndex)
    expect(surfaceTreatmentDraftIndex).toBeGreaterThan(descriptionDraftIndex)
    expect(remarkDraftIndex).toBeGreaterThan(surfaceTreatmentDraftIndex)
    expect(statusIndex).toBeGreaterThan(remarkDraftIndex)

    expect(fieldSaveBlock).toContain('return runAction(releaseActionKeys.fields(rowKey(row)), async () =>')
    expect(fieldSaveBlock).toContain('await persistReleaseItemFields(row)')
    expect(fieldPersistBlock).toContain('updateReleaseItemFields(row.id, {')
    expect(fieldPersistBlock).toContain('sourceType: row.sourceType || \'RFQ\'')
    expect(fieldPersistBlock).toContain('sourceOrderId: row.sourceOrderId')
    expect(fieldPersistBlock).toContain('rfqId: row.rfqId || selectedRfqId.value')
    expect(fieldPersistBlock).toContain('description: row.descriptionDraft')
    expect(fieldPersistBlock).toContain('surfaceTreatment: row.surfaceTreatmentDraft')
    expect(fieldPersistBlock).toContain('remark: row.remarkDraft')
    expect(fieldPersistBlock).not.toContain(`${removedField}: row.${removedField}Draft`)
    expect(source).toContain('itemList.value.splice(index, 1, saved)')
    expect(source).toContain('selectedItemIdMap[key] = { ...selectedItemIdMap[key], ...saved }')
    expect(fieldSaveBlock).not.toContain('clearSelectedRows()')
    expect(fieldSaveBlock).not.toContain('fetchReleaseItemsBatch()')
  })

  test('market release edits one row version without clearing cross-page selection', () => {
    const source = readSource('components', 'MarketRelease.vue')
    const saveBlock = source.slice(
      source.indexOf('const saveReleaseItemVersion = async (row) =>'),
      source.indexOf('const handleSelectionChange')
    )
    const persistBlock = source.slice(
      source.indexOf('const persistReleaseItemVersion = async (row) =>'),
      source.indexOf('const persistReleaseItemFields = async (row) =>')
    )

    expect(source).toContain('updateReleaseItemVersion')
    expect(source).toContain('version: (itemId) => `market:release:version:${itemId}`')
    expect(source).toContain('const isVersionSaving = (row) => isPending(releaseActionKeys.version(row?.id))')
    expect(source).toContain('class="row-version-editor"')
    expect(source).toContain('v-model="row.versionDraft"')
    expect(source).toContain(':loading="isVersionSaving(row)"')
    expect(source).toContain('@click.stop="saveReleaseItemVersion(row)"')

    expect(saveBlock).toContain('return runAction(releaseActionKeys.version(row.id), async () =>')
    expect(saveBlock).toContain('await persistReleaseItemVersion(row)')
    expect(persistBlock).toContain('updateReleaseItemVersion(row.id, {')
    expect(persistBlock).toContain('rfqId: rowRfqId')
    expect(persistBlock).toContain('version')
    expect(source).toContain('itemList.value.splice(index, 1, saved)')
    expect(source).toContain('selectedItemIdMap[key] = { ...selectedItemIdMap[key], ...saved }')
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
    expect(source).toContain('selectedItemIdMap[key] = { ...selectedItemIdMap[key], ...saved }')
    expect(submitBlock).toContain('const orderedSelectedRows = selectedRowsInItemOrder()')
    expect(submitBlock).toContain('items: orderedSelectedRows.map(row => ({')
  })
})
