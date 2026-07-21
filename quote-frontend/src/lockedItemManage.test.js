import { describe, expect, test } from 'vitest'
import { readFileSync } from 'node:fs'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

const currentDir = dirname(fileURLToPath(import.meta.url))
const planSource = readFileSync(resolve(currentDir, '..', '..', 'docs', 'QPO-MES-WMS对接', '04-WMS锁库分配与锁库零件模块开发文档.md'), 'utf8')

describe('LockedItemManage guardrails', () => {
  test('freezes locked item route, menu permission, and four-character sidebar label', () => {
    expect(planSource).toContain('LockedItemManage.vue')
    expect(planSource).toContain('/locked-items')
    expect(planSource).toContain('market:locked-item:view')
    expect(planSource).toContain('锁库零件')
  })

  test('freezes locked item filters and table columns', () => {
    for (const token of [
      'customerName',
      'rfqNo',
      'partNo',
      'version',
      'materialNo',
      'internalProjectNo',
      'allocationType',
      'wms_production_order_no'
    ]) {
      expect(planSource).toContain(token)
    }

    for (const label of ['客户', 'RFQ', '图号', '版本', '物料号', '内部项目号', '分配类型', '在产订单号', '数量']) {
      expect(planSource).toContain(label)
    }
  })

  test('freezes allocation type display vocabulary', () => {
    expect(planSource).toContain('IN_STOCK_LOCK')
    expect(planSource).toContain('IN_PRODUCTION_LOCK')
    expect(planSource).toContain('PRODUCTION_REQUIRED')
    expect(planSource).toContain('已入库库存锁定')
    expect(planSource).toContain('在产库存锁定')
    expect(planSource).toContain('待生产数量')
  })
})
