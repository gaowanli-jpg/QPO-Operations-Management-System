import { describe, expect, test } from 'vitest'
import { readFileSync } from 'node:fs'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

const currentDir = dirname(fileURLToPath(import.meta.url))
const readComponent = (fileName) => readFileSync(resolve(currentDir, 'components', fileName), 'utf8')

describe('RFQ material number status display', () => {
  test('shows MES-confirmed material number status in inquiry order item details', () => {
    const component = readComponent('OrderManage.vue')

    expect(component).toContain('prop="materialNo" label="物料号"')
    expect(component).toContain('prop="materialNoStatus" label="物料号状态"')
    expect(component).toContain('materialStatusTagType(row.materialNoStatus)')
    expect(component).toContain("row.materialNo || '-'")
    expect(component).toContain("row.materialNoStatus || '未确认'")
  })
})
