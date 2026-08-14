import { describe, expect, test } from 'vitest'
import { readFileSync } from 'node:fs'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

const currentDir = dirname(fileURLToPath(import.meta.url))
const readSource = (...segments) => readFileSync(resolve(currentDir, ...segments), 'utf8')

describe('material master stage 10 frontend closure', () => {
  test('material type APIs stay available for legacy compatibility while the page is read-only', () => {
    const dictApi = readSource('services', 'dictApi.js')
    const materialProcess = readSource('components', 'MaterialProcessManage.vue')

    expect(dictApi).toContain('listMaterialTypes')
    expect(dictApi).toContain('saveMaterialType')
    expect(dictApi).toContain('deleteMaterialType')
    expect(dictApi).toContain('/api/dict/material-types')
    expect(materialProcess).toContain("v-permission=\"'engineer:dict:material-manage'\"")
    expect(materialProcess).toContain('name="materialTypes"')
    expect(materialProcess).not.toContain('@click="openMaterialTypeDialog')
    expect(materialProcess).not.toContain('@click="deleteMaterialType')
  })

  test('material maintenance, picker, global defaults and pricing submit identify material by code', () => {
    const materialProcess = readSource('components', 'MaterialProcessManage.vue')
    const optionPicker = readSource('components', 'EngineeringOptionPickerDialog.vue')
    const globalSetup = readSource('components', 'GlobalSetup.vue')
    const itemPricing = readSource('components', 'ItemPricing.vue')

    expect(materialProcess).toContain('v-model="materialForm.materialCode" disabled')
    expect(materialProcess).toContain('v-model="materialForm.materialName" disabled')
    expect(materialProcess).toContain('v-model="materialForm.materialTypeId" filterable disabled')
    expect(materialProcess).toContain('v-model="materialForm.materialCategory" disabled')
    expect(materialProcess).not.toContain('@click="openMaterialDialog()"')
    expect(materialProcess).not.toContain('@click="deleteMaterial(scope.row)"')
    expect(optionPicker).toContain('prop="materialCode"')
    expect(optionPicker).toContain('item?.materialCode')
    expect(globalSetup).toContain(':selected-key="config.materialCode"')
    expect(globalSetup).toContain('config.materialCode = material.materialCode')
    expect(itemPricing).toContain(':selected-key="matForm.materialCode"')
    expect(itemPricing).toContain('materialCode: matForm.materialCode')
    expect(itemPricing).toContain('materialTypeCode: matForm.materialTypeCode')
    expect(itemPricing).not.toContain('find(item => item.materialName === matForm.materialName)')
  })

  test('delivered production and locked pages keep material type separate from material shape', () => {
    for (const component of ['DeliveredItemManage.vue', 'ProductionItemManage.vue', 'LockedItemManage.vue']) {
      const source = readSource('components', component)
      expect(source).toContain('formatMaterialCode')
      expect(source).toContain('formatMaterialType')
      expect(source).toContain('formatMaterialShape')
      expect(source).toContain('engineeringMaterialCode')
      expect(source).toContain('engineeringMaterialTypeName')
      expect(source).toContain('data.shape || data.materialShape')
      expect(source).not.toContain('data.materialType ||')
      expect(source).not.toContain('materialData.materialType ||')
    }
  })
})
