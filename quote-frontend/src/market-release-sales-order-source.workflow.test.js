import { describe, expect, test } from 'vitest'
import { readFileSync } from 'node:fs'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

const currentDir = dirname(fileURLToPath(import.meta.url))
const readSource = (...segments) => readFileSync(resolve(currentDir, ...segments), 'utf8')

describe('market release sales order source workflow', () => {
  test('ticket generation submits one RFQ batch with source identity per selected row', () => {
    const source = readSource('components', 'MarketRelease.vue')

    expect(source).toContain('rfqIds: selectedRfqIds.value')
    expect(source).toContain('releaseBatchKey')
    expect(source).toContain("sourceType: 'RFQ'")
    expect(source).toContain('sourceOrderId: Number(row?.sourceOrderId ?? row?.rfqId ?? selectedRfqId.value)')
    expect(source).toContain('sourceItemId: Number(row?.sourceItemId ?? row?.id)')
    expect(source).toContain('itemId: row.id')
    expect(source).toContain('const response = await generateTicket(payload)')
    expect(source).toContain('releaseResult.value = response')
    expect(source).toContain("router.push({ path: '/u9-sync', query: { salesOrderId: String(salesOrderId) } })")
    expect(source).toContain("v-permission=\"'market:u9-sync:view'\"")
    expect(source).not.toContain('rfqId: source.sourceOrderId')
    expect(source).not.toContain('successfulResults.push({ source, rows, response })')
    expect(source).not.toContain('SALES_ORDER')
    expect(source).not.toContain('const groupSelectedRowsBySource = (rows) => {')
  })

  test('formal release success automatically exports the production task notice for the derived sales order', () => {
    const source = readSource('components', 'MarketRelease.vue')
    const submitStart = source.indexOf('const submitTicket = async () => {')
    const submitEnd = source.indexOf('\nconst resolveDeliveryLeadTime', submitStart)
    const submitBlock = submitStart >= 0 && submitEnd > submitStart ? source.slice(submitStart, submitEnd) : ''
    const appendStart = source.indexOf('const appendToSalesOrder = async (targetOrder) => {')
    const appendEnd = source.indexOf('\nconst submitTicket = async () => {', appendStart)
    const appendBlock = appendStart >= 0 && appendEnd > appendStart ? source.slice(appendStart, appendEnd) : ''

    expect(source).toContain("import { downloadSalesOrderProductionTaskNotice } from '../services/orderApi'")
    expect(source).toContain('const autoExportProductionTaskNotice = async (result) => {')
    expect(source).toContain('downloadSalesOrderProductionTaskNotice(salesOrderId, `${salesOrderNo}-生产任务通知单.xlsx`)')
    expect(submitBlock).toContain('await autoExportProductionTaskNotice(response)')
    expect(submitBlock).toContain('生产任务通知单已开始导出')
    expect(appendBlock).not.toContain('autoExportProductionTaskNotice')
  })
})
