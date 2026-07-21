import { describe, expect, test } from 'vitest'
import { existsSync, readFileSync } from 'node:fs'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

const currentDir = dirname(fileURLToPath(import.meta.url))
const pagePath = resolve(currentDir, 'components', 'LockedItemManage.vue')
const pageSource = existsSync(pagePath) ? readFileSync(pagePath, 'utf8') : ''
const routerSource = readFileSync(resolve(currentDir, 'router', 'index.js'), 'utf8')
const appSource = readFileSync(resolve(currentDir, 'App.vue'), 'utf8')
const apiSource = readFileSync(resolve(currentDir, 'services', 'deliveredItemApi.js'), 'utf8')

describe('LockedItemManage guardrails', () => {
  test('wires locked item route, menu permission, and four-character sidebar label', () => {
    expect(pageSource).toContain('<h1 class="erp-page-title">锁库零件</h1>')
    expect(routerSource).toContain('LockedItemManage')
    expect(routerSource).toContain("path: '/locked-items'")
    expect(routerSource).toContain("permission: 'market:locked-item:view'")
    expect(appSource).toContain("'/locked-items': { title: '锁库零件', permission: 'market:locked-item:view'")
  })

  test('loads locked item allocation rows through delivered item API service', () => {
    expect(apiSource).toContain('listLockedItems')
    expect(apiSource).toContain('/api/order-delivery/allocations')
    expect(apiSource).toContain('listTicketAllocations')
    expect(apiSource).toContain('/api/order-delivery/tickets/${ticketId}/allocations')
    expect(pageSource).toContain('listLockedItems')
    expect(pageSource).toContain('listTicketAllocations')
  })

  test('renders locked item filters and table columns', () => {
    for (const token of [
      'customerName',
      'rfqNo',
      'partNo',
      'version',
      'materialNo',
      'internalProjectNo',
      'allocationType',
      'wmsProductionOrderNo'
    ]) {
      expect(pageSource).toContain(token)
    }

    for (const label of ['客户', 'RFQ号', '图号', '版本', '物料号', '内部项目号', '分配类型', '在产订单号', '数量']) {
      expect(pageSource).toContain(label)
    }
  })

  test('localizes allocation types and uses stable tag types', () => {
    expect(pageSource).toContain('IN_STOCK_LOCK')
    expect(pageSource).toContain('IN_PRODUCTION_LOCK')
    expect(pageSource).toContain('PRODUCTION_REQUIRED')
    expect(pageSource).toContain('已入库库存锁定')
    expect(pageSource).toContain('在产库存锁定')
    expect(pageSource).toContain('待生产数量')
    expect(pageSource).toContain("type: 'success'")
    expect(pageSource).toContain("type: 'warning'")
    expect(pageSource).toContain("type: 'danger'")
  })

  test('supports ticket-specific result entry and Element Plus error feedback', () => {
    expect(pageSource).toContain('route.query.ticketId')
    expect(pageSource).toContain('锁库零件列表加载失败。')
    expect(pageSource).toContain('ElMessage.error')
  })
})
