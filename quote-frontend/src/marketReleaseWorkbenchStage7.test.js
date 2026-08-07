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

  test('renders six item order-field editors and one save action before status', () => {
    const source = readSource('components', 'MarketRelease.vue')
    const customerRequirementIndex = source.indexOf('prop="customerRequirement" label="客户需求"')
    const orderFieldIndexes = [
      source.indexOf('label="下单日期" width="150"', customerRequirementIndex),
      source.indexOf('label="客户订单号" width="190"', customerRequirementIndex),
      source.indexOf('label="客户项目号" width="190"', customerRequirementIndex),
      source.indexOf('label="客户下单抬头" width="210"', customerRequirementIndex),
      source.indexOf('label="订单类型" width="170"', customerRequirementIndex),
      source.indexOf('label="内部项目号" width="190"', customerRequirementIndex),
      source.indexOf('label="订单信息操作" width="136"', customerRequirementIndex)
    ]
    const statusIndex = source.indexOf('prop="status" label="状态"', customerRequirementIndex)

    expect(orderFieldIndexes.every(index => index > customerRequirementIndex)).toBe(true)
    expect(orderFieldIndexes.every((index, position) => position === 0 || index > orderFieldIndexes[position - 1])).toBe(true)
    expect(statusIndex).toBeGreaterThan(orderFieldIndexes.at(-1))
    expect(source).toContain('v-model="row.salesOrderDateDraft"')
    expect(source).toContain('v-model="row.customerOrderNoDraft"')
    expect(source).toContain('v-model="row.customerProjectNoDraft"')
    expect(source).toContain('v-model="row.customerOrderTitleDraft"')
    expect(source).toContain('v-model="row.orderTypeDraft"')
    expect(source).toContain('v-model="row.internalProjectNoDraft"')
    expect(source).toContain('@click.stop="saveReleaseItemOrderFields(row)"')
    expect(source).toContain('保存订单信息')
    expect(source).toContain('orderFieldPlaceholder(row,')
  })

  test('saves all six item fields once and keeps partial rows editable', () => {
    const source = readSource('components', 'MarketRelease.vue')
    const saveBlock = source.slice(
      source.indexOf('const saveReleaseItemOrderFields = async (row) =>'),
      source.indexOf('const handleSelectionChange')
    )

    expect(source).toContain('updateReleaseItemOrderFields')
    expect(source).toContain('const canEditReleaseItemOrderFields = (row) => !isFullyReleasedReleaseItem(row)')
    expect(source).toContain('orderFields: (releaseItemKey) =>')
    expect(source).toContain('const isOrderFieldsSaving = (row) =>')
    expect(saveBlock).toContain('const response = await updateReleaseItemOrderFields(row.id, {')
    expect(saveBlock).toContain('const rowRfqId = row?.rfqId || row?.sourceOrderId || selectedRfqId.value')
    expect(saveBlock).toContain('rfqId: rowRfqId')
    expect(saveBlock).toContain('salesOrderDate: normalizeOrderFieldDraft(row.salesOrderDateDraft)')
    expect(saveBlock).toContain('customerOrderNo: normalizeOrderFieldDraft(row.customerOrderNoDraft)')
    expect(saveBlock).toContain('customerProjectNo: normalizeOrderFieldDraft(row.customerProjectNoDraft)')
    expect(saveBlock).toContain('customerOrderTitle: normalizeOrderFieldDraft(row.customerOrderTitleDraft)')
    expect(saveBlock).toContain('orderType: normalizeOrderFieldDraft(row.orderTypeDraft)')
    expect(saveBlock).toContain('internalProjectNo: normalizeOrderFieldDraft(row.internalProjectNoDraft)')
    expect(saveBlock).toContain('if (selectedItemIdMap[key])')
    expect(saveBlock).toContain("requestErrorMessage(error, '零件订单信息保存失败。')")
    expect(saveBlock.match(/updateReleaseItemOrderFields\(/g)).toHaveLength(1)
  })

  test('keeps selected row drafts when paged records are reloaded', () => {
    const source = readSource('components', 'MarketRelease.vue')
    const setItemListBlock = source.slice(
      source.indexOf('const setItemList = (rows) =>'),
      source.indexOf('const remainingQtyFor')
    )

    expect(source).toContain("import { hydrateReleaseItemDrafts } from './marketReleaseDrafts'")
    expect(setItemListBlock).toContain('hydrateReleaseItemDrafts(row, selectedItemIdMap[rowKey(row)])')
  })

  test('blocks formal generation while a selected row has unsaved order drafts', () => {
    const source = readSource('components', 'MarketRelease.vue')
    const submitBlock = source.slice(
      source.indexOf('const submitTicket = async () =>'),
      source.indexOf('const resolveDeliveryLeadTime')
    )

    expect(source).toContain('const hasUnsavedOrderFieldDraft = (row) =>')
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
    expect(source).not.toMatch(/openDerivedOrder(?:SalesOrder|U9|DeliveredItems)[\s\S]{0,500}(syncSalesOrderU9|syncMesMaterial|submitWmsAllocation)/)
  })
})
