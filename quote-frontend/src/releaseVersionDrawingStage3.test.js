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

  test('market release edits row part description surface remark and customer requirement like version fields', () => {
    const source = readSource('components', 'MarketRelease.vue')
    const draftSource = readSource('components', 'marketReleaseDrafts.js')
    const fieldSaveBlock = source.slice(
      source.indexOf('const saveReleaseItemFields = async (row) =>'),
      source.indexOf('const handleSelectionChange')
    )
    const materialIndex = source.indexOf('prop="engineeringMaterialName" label="材料"')
    const descriptionDraftIndex = source.indexOf('v-model="row.descriptionDraft"')
    const surfaceTreatmentDraftIndex = source.indexOf('v-model="row.surfaceTreatmentDraft"')
    const remarkDraftIndex = source.indexOf('v-model="row.remarkDraft"')
    const customerRequirementDraftIndex = source.indexOf('v-model="row.customerRequirementDraft"')
    const statusIndex = source.indexOf('prop="status"', customerRequirementDraftIndex)

    expect(source).toContain('updateReleaseItemFields')
    expect(source).toContain('fields: (releaseItemKey) => `market:release:fields:${releaseItemKey}`')
    expect(source).toContain('const isFieldSaving = (row) => isPending(releaseActionKeys.fields(rowKey(row)))')
    expect(source).toContain('v-model="row.descriptionDraft"')
    expect(source).toContain('v-model="row.surfaceTreatmentDraft"')
    expect(source).toContain('v-model="row.remarkDraft"')
    expect(source).toContain('v-model="row.customerRequirementDraft"')
    expect(source).toContain(':loading="isFieldSaving(row)"')
    expect(source).toContain('@click.stop="saveReleaseItemFields(row)"')
    expect(source).toContain("import { hydrateReleaseItemDrafts } from './marketReleaseDrafts'")
    expect(draftSource).toContain("descriptionDraft: 'description'")
    expect(draftSource).toContain("surfaceTreatmentDraft: 'surfaceTreatment'")
    expect(draftSource).toContain("remarkDraft: 'remark'")
    expect(draftSource).toContain("customerRequirementDraft: 'customerRequirement'")
    expect(materialIndex).toBeGreaterThanOrEqual(0)
    expect(descriptionDraftIndex).toBeGreaterThan(materialIndex)
    expect(surfaceTreatmentDraftIndex).toBeGreaterThan(descriptionDraftIndex)
    expect(remarkDraftIndex).toBeGreaterThan(surfaceTreatmentDraftIndex)
    expect(customerRequirementDraftIndex).toBeGreaterThan(remarkDraftIndex)
    expect(statusIndex).toBeGreaterThan(customerRequirementDraftIndex)

    expect(fieldSaveBlock).toContain('return runAction(releaseActionKeys.fields(rowKey(row)), async () =>')
    expect(fieldSaveBlock).toContain('updateReleaseItemFields(row.id, {')
    expect(fieldSaveBlock).toContain('sourceType: row.sourceType || \'RFQ\'')
    expect(fieldSaveBlock).toContain('sourceOrderId: row.sourceOrderId')
    expect(fieldSaveBlock).toContain('rfqId: row.rfqId || selectedRfqId.value')
    expect(fieldSaveBlock).toContain('description: row.descriptionDraft')
    expect(fieldSaveBlock).toContain('surfaceTreatment: row.surfaceTreatmentDraft')
    expect(fieldSaveBlock).toContain('remark: row.remarkDraft')
    expect(fieldSaveBlock).toContain('customerRequirement: row.customerRequirementDraft')
    expect(fieldSaveBlock).toContain('itemList.value.splice(index, 1, saved)')
    expect(fieldSaveBlock).toContain('selectedItemIdMap[rowKey(row)] = { ...selectedItemIdMap[rowKey(row)], ...saved }')
    expect(fieldSaveBlock).not.toContain('clearSelectedRows()')
    expect(fieldSaveBlock).not.toContain('fetchReleaseItemsBatch()')
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
    expect(saveBlock).toContain('selectedItemIdMap[rowKey(row)] = { ...selectedItemIdMap[rowKey(row)], ...saved }')
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
    expect(source).toContain('selectedItemIdMap[rowKey(row)] = { ...selectedItemIdMap[rowKey(row)], ...saved }')
    expect(submitBlock).toContain('const orderedSelectedRows = selectedRowsInItemOrder()')
    expect(submitBlock).toContain('items: orderedSelectedRows.map(row => ({')
  })
})
