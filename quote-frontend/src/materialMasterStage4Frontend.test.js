import { describe, expect, test } from 'vitest'
import { readFileSync } from 'node:fs'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

const currentDir = dirname(fileURLToPath(import.meta.url))
const readSource = (...segments) => readFileSync(resolve(currentDir, ...segments), 'utf8')

const dictApi = readSource('services', 'dictApi.js')
const dictionaryStore = readSource('stores', 'dictionaryStore.js')
const materialProcess = readSource('components', 'MaterialProcessManage.vue')

describe('material master stage 4 frontend refactor', () => {
  test('dictApi exposes material type APIs and keeps material list filterable', () => {
    expect(dictApi).toContain('listMaterialTypes')
    expect(dictApi).toContain('/api/dict/material-types')
    expect(dictApi).toContain('saveMaterialType')
    expect(dictApi).toContain('deleteMaterialType')
    expect(dictApi).toContain('listMaterials = ({ enabledOnly } = {})')
    expect(dictApi).toContain('/api/dict/materials${buildQuery({ enabledOnly })}')
  })

  test('dictionary store caches material types with base dictionaries', () => {
    expect(dictionaryStore).toContain('materialTypes: []')
    expect(dictionaryStore).toContain('listMaterialTypes')
    expect(dictionaryStore).toContain('materialTypes: this.materialTypes')
    expect(dictionaryStore).toContain('this.materialTypes = materialTypes || []')
    expect(dictionaryStore).toContain('materialCode:')
    expect(dictionaryStore).toContain('materialTypeCode:')
    expect(dictionaryStore).toContain('materialTypeName:')
    expect(dictionaryStore).toContain('materialCategory:')
  })

  test('material process page keeps material type tab read-only for MES ownership', () => {
    expect(materialProcess).toContain('name="materialTypes"')
    expect(materialProcess).toContain('材料类型')
    expect(materialProcess).toContain('类型编码')
    expect(materialProcess).toContain('类型名称')
    expect(materialProcess).not.toContain('@click="openMaterialTypeDialog')
    expect(materialProcess).not.toContain('@click="deleteMaterialType')
  })

  test('material table and dialog use material code, business type, category, state and sort order', () => {
    for (const fragment of [
      'prop="materialCode" label="材料编码"',
      'prop="materialName" label="材料名称"',
      'label="材料类型"',
      'prop="materialCategory" label="材质分类"',
      'prop="sortNo" label="排序"',
      'label="状态"',
      'v-model="materialForm.materialCode" disabled',
      'v-model="materialForm.materialName" disabled',
      'v-model="materialForm.materialTypeId" filterable disabled',
      'v-model="materialForm.materialCategory" disabled'
    ]) {
      expect(materialProcess).toContain(fragment)
    }
    expect(materialProcess).not.toContain('@click="openMaterialDialog()"')
    expect(materialProcess).not.toContain('@click="deleteMaterial(scope.row)"')
    expect(materialProcess).not.toContain('prop="materialType" label="材质"')
    expect(materialProcess).not.toContain('v-model="materialForm.materialType"')
  })

  test('material list can search code, name, type and category with responsive layout', () => {
    expect(materialProcess).toContain('materialKeyword')
    expect(materialProcess).toContain('filteredMaterials')
    expect(materialProcess).toContain('materialCode')
    expect(materialProcess).toContain('materialTypeCode')
    expect(materialProcess).toContain('materialTypeName')
    expect(materialProcess).toContain('materialCategory')
    expect(materialProcess).toContain('material-filter-bar')
    expect(materialProcess).toContain('@media (max-width: 900px)')
  })
})
