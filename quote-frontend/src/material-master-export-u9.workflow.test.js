import { describe, expect, test } from 'vitest'
import { readFileSync } from 'fs'
import { dirname, resolve } from 'path'
import { fileURLToPath } from 'url'

const currentDir = dirname(fileURLToPath(import.meta.url))

const readSource = (...segments) => readFileSync(resolve(currentDir, ...segments), 'utf-8')

describe('material master U9 export workflow', () => {
  test('delivery document tables display material identity fields', () => {
    const ticketRows = readSource('components', 'delivery-document', 'DeliveryTicketRowsTable.vue')
    const processRows = readSource('components', 'delivery-document', 'ProcessSheetRowsTable.vue')

    for (const source of [ticketRows, processRows]) {
      expect(source).toContain('prop="engineeringMaterialCode" label="材料编码"')
      expect(source).toContain('prop="engineeringMaterialTypeName" label="材料类型"')
      expect(source).toContain('prop="engineeringMaterialCategory" label="材质分类"')
      expect(source).toContain('prop="engineeringMaterialName" label="材料"')
    }
  })

  test('u9 preview shows engineering material code while keeping MES material number as U9 identity', () => {
    const source = readSource('components', 'SalesOrderU9Sync.vue')

    expect(source).toContain('prop="materialNo" label="物料号"')
    expect(source).toContain('prop="engineeringMaterialCode" label="材料编码"')
    expect(source).toContain('prop="engineeringMaterialTypeName" label="材料类型"')
    expect(source).toContain('U9同步使用已确认物料号')
  })

  test('material shape helpers do not treat material type as shape', () => {
    for (const component of ['DeliveredItemManage.vue', 'ProductionItemManage.vue', 'LockedItemManage.vue']) {
      const source = readSource('components', component)
      expect(source).not.toContain('data.materialType ||')
      expect(source).not.toContain('materialData.materialType ||')
      expect(source).toContain('data.shape || data.materialShape')
    }
  })
})
