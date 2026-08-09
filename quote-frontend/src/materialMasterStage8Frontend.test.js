import { describe, expect, test } from 'vitest'
import { readFileSync } from 'fs'
import { dirname, resolve } from 'path'
import { fileURLToPath } from 'url'

const currentDir = dirname(fileURLToPath(import.meta.url))

const readComponent = (name) => readFileSync(resolve(currentDir, 'components', name), 'utf-8')

describe('material master stage 8 frontend', () => {
  test('delivered, production, and locked item pages show material identity and shape separately', () => {
    for (const component of ['DeliveredItemManage.vue', 'ProductionItemManage.vue', 'LockedItemManage.vue']) {
      const source = readComponent(component)

      expect(source).toContain('label="材料编码"')
      expect(source).toContain('label="材料类型"')
      expect(source).toContain('label="材料"')
      expect(source).toContain('label="材料形态"')
      expect(source).toContain('formatMaterialCode(row)')
      expect(source).toContain('formatMaterialType(row)')
      expect(source).toContain('formatMaterialName(row)')
      expect(source).toContain('formatMaterialShape(row)')
      expect(source).toContain('row?.engineeringMaterialCode')
      expect(source).toContain('row?.engineeringMaterialTypeName')
      expect(source).not.toContain('formatMaterialShape(row.materialData)')
      expect(source).not.toContain('data.materialType ||')
    }
  })
})
