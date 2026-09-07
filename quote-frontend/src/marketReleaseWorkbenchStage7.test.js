import { describe, expect, test } from 'vitest'
import { readFileSync } from 'node:fs'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

const currentDir = dirname(fileURLToPath(import.meta.url))
const readSource = (...segments) => readFileSync(resolve(currentDir, ...segments), 'utf8')

describe('market release workbench stage 7', () => {
  test('exposes one API for atomically saving all RFQ item order fields', () => {
    const apiSource = readSource('services', 'marketApi.js')

    expect(apiSource).toContain('export const updateReleaseItemOrderFields = (itemId, payload) =>')
    expect(apiSource).toContain("postJson(`/api/market/release/items/${itemId}/order-fields`, payload)")
  })

  test('groups the seven order defaults and explains one-batch generation semantics', () => {
    const source = readSource('components', 'MarketRelease.vue')

    expect(source).toContain('订单默认信息')
    expect(source).toContain('一批生成一张销售订单')
    expect(source).toContain('零件值优先，空值继承订单默认信息')
    expect(source).toContain('追加销售提单零件值优先，空值继承目标销售订单头，左侧必填项不参与')
    expect(source).toContain('label="下单日期"')
    expect(source).toContain('v-model="releaseForm.documentTypeCode"')
    expect(source).toContain('v-model="releaseForm.salesOrderDate"')
    expect(source).toContain('v-model="releaseForm.customerOrderNo"')
    expect(source).toContain('v-model="releaseForm.customerProjectNo"')
    expect(source).toContain('v-model="releaseForm.customerOrderTitle"')
    expect(source).toContain('v-model="releaseForm.orderType"')
    expect(source).toContain('v-model="releaseForm.internalProjectNo"')
    expect(source).not.toContain('label="开工日期"')
  })

  test('renders six item order-field editors with independent save actions before status', () => {
    const source = readSource('components', 'MarketRelease.vue')
    const tableIndex = source.indexOf('class="release-selection-table"')
    const orderFieldIndexes = [
      source.indexOf('label="下单日期" width="220"', tableIndex),
      source.indexOf('label="客户订单号" width="220"', tableIndex),
      source.indexOf('label="客户项目号" width="220"', tableIndex),
      source.indexOf('label="客户下单抬头" width="240"', tableIndex),
      source.indexOf('label="订单类型" width="220"', tableIndex),
      source.indexOf('label="内部项目号" width="220"', tableIndex)
    ]
    const statusIndex = source.indexOf('prop="status" label="状态"', tableIndex)

    expect(source).not.toContain('prop="' + 'customer' + 'Requirement"')
    expect(source).not.toContain('label="订单信息操作"')
    expect(source).not.toContain('保存订单信息')
    expect(orderFieldIndexes.every(index => index > tableIndex)).toBe(true)
    expect(orderFieldIndexes.every((index, position) => position === 0 || index > orderFieldIndexes[position - 1])).toBe(true)
    expect(statusIndex).toBeGreaterThan(orderFieldIndexes.at(-1))
    expect(source).toContain('v-model="row.salesOrderDateDraft"')
    expect(source).toContain('v-model="row.customerOrderNoDraft"')
    expect(source).toContain('v-model="row.customerProjectNoDraft"')
    expect(source).toContain('v-model="row.customerOrderTitleDraft"')
    expect(source).toContain('v-model="row.orderTypeDraft"')
    expect(source).toContain('v-model="row.internalProjectNoDraft"')
    expect(source).toContain('@click.stop="saveReleaseItemOrderField(row, \'salesOrderDate\')"')
    expect(source).toContain('@click.stop="saveReleaseItemOrderField(row, \'customerOrderNo\')"')
    expect(source).toContain('@click.stop="saveReleaseItemOrderField(row, \'customerProjectNo\')"')
    expect(source).toContain('@click.stop="saveReleaseItemOrderField(row, \'customerOrderTitle\')"')
    expect(source).toContain('@click.stop="saveReleaseItemOrderField(row, \'orderType\')"')
    expect(source).toContain('@click.stop="saveReleaseItemOrderField(row, \'internalProjectNo\')"')
    expect(source).toContain('orderFieldPlaceholder(row,')
  })

  test('saves one item order field at a time and preserves unrelated drafts', () => {
    const source = readSource('components', 'MarketRelease.vue')
    const saveBlock = source.slice(
      source.indexOf('const saveReleaseItemOrderField = async (row, field) =>'),
      source.indexOf('const handleSelectionChange')
    )

    expect(source).toContain('updateReleaseItemOrderFields')
    expect(source).toContain('const canEditReleaseItemOrderFields = (row) => row?.canSelect !== false')
    expect(source).toContain('orderField: (releaseItemKey, field) =>')
    expect(source).toContain('const isOrderFieldSaving = (row, field) =>')
    expect(saveBlock).toContain('const response = await updateReleaseItemOrderFields(row.id, {')
    expect(saveBlock).toContain('const rowRfqId = row?.rfqId || row?.sourceOrderId || selectedRfqId.value')
    expect(saveBlock).toContain('rfqId: rowRfqId')
    expect(saveBlock).toContain('const payload = orderFieldPayloadForSingleSave(row, field)')
    expect(saveBlock).toContain('const saved = mergeSavedOrderFieldDrafts(row, savedData, field)')
    expect(saveBlock).toContain('if (selectedItemIdMap[key])')
    expect(saveBlock).toContain("requestErrorMessage(error, '零件订单信息保存失败。')")
    expect(saveBlock.match(/updateReleaseItemOrderFields\(/g)).toHaveLength(1)
    expect(source).toContain('const orderFieldPayloadForSingleSave = (row, field) =>')
    expect(source).toContain('const mergeSavedOrderFieldDrafts = (row, savedData, savedField) =>')
  })

  test('keeps selected row drafts when paged records are reloaded', () => {
    const source = readSource('components', 'MarketRelease.vue')
    const setItemListBlock = source.slice(
      source.indexOf('const setItemList = (rows) =>'),
      source.indexOf('const remainingQtyFor')
    )

    expect(source).toContain('hydrateReleaseItemDrafts')
    expect(source).toContain("from './marketReleaseDrafts'")
    expect(setItemListBlock).toContain('hydrateReleaseItemDrafts(row, selectedItemIdMap[rowKey(row)])')
  })

  test('blocks formal generation while a selected row has unsaved order drafts', () => {
    const source = readSource('components', 'MarketRelease.vue')
    const submitBlock = source.slice(
      source.indexOf('const submitTicket = async () =>'),
      source.indexOf('const resolveDeliveryLeadTime')
    )

    expect(source).toContain('const hasUnsavedOrderFieldDraft = (row, targetField) =>')
    expect(source).toContain('const selectedRowsWithUnsavedOrderFields = computed(() =>')
    expect(source).toContain('selectedRowsWithUnsavedOrderFields.length > 0')
    expect(source).toContain('selectedRowsWithUnsavedOrderFields.value.length > 0')
    expect(source).toContain('请先保存已选零件的订单信息草稿')
    expect(submitBlock).not.toMatch(/salesOrderDateDraft|customerOrderNoDraft|customerProjectNoDraft|customerOrderTitleDraft|orderTypeDraft|internalProjectNoDraft/)
  })

  test('shows the complete derived-order result and only performs read-only route jumps', () => {
    const source = readSource('components', 'MarketRelease.vue')

    expect(source).toContain('releaseResult.salesOrderNo')
    expect(source).toContain('releaseResult.documentNo')
    expect(source).toContain('releaseResult.sourceRfqIds?.length')
    expect(source).toContain('releaseResult.ticketCount')
    expect(source).toContain('releaseResult.confirmedMaterialCount')
    expect(source).toContain('releaseResult.pendingMaterialCount')
    expect(source).toContain('查看销售订单')
    expect(source).toContain('进入 U9 同步')
    expect(source).toContain('查看已提零件')
    expect(source).toContain("router.push({ path: '/sales-orders', query: { salesOrderId: String(salesOrderId) } })")
    expect(source).toContain("router.push({ path: '/u9-sync', query: { salesOrderId: String(salesOrderId) } })")
    expect(source).toContain("router.push({ path: '/delivered-items', query: { salesOrderId: String(salesOrderId) } })")
    expect(source).toContain("v-permission=\"'market:sales-order:view'\"")
    expect(source).toContain("v-permission=\"'market:u9-sync:view'\"")
    expect(source).toContain("v-permission=\"'market:delivered-item:view'\"")
    const forbiddenDerivedWritePattern = new RegExp(
      `openDerivedOrder(?:SalesOrder|U9|DeliveredItems)[\\s\\S]{0,500}(syncSalesOrderU9|syncMesMaterial|submit${'WmsAllocation'})`
    )
    expect(source).not.toMatch(forbiddenDerivedWritePattern)
  })
})
