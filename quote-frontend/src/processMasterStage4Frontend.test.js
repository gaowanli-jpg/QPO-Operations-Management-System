import { describe, expect, test } from 'vitest'
import { readFileSync } from 'node:fs'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

const currentDir = dirname(fileURLToPath(import.meta.url))
const readSource = (...segments) => readFileSync(resolve(currentDir, ...segments), 'utf8')

const dictApi = readSource('services', 'dictApi.js')
const dictionaryStore = readSource('stores', 'dictionaryStore.js')
const materialProcess = readSource('components', 'MaterialProcessManage.vue')

describe('process master stage 4 frontend refactor', () => {
  test('dictApi exposes two-level process master APIs', () => {
    expect(dictApi).toContain('listProcessCategories')
    expect(dictApi).toContain('/api/dict/process-categories')
    expect(dictApi).toContain('saveProcessCategory')
    expect(dictApi).toContain('listProcessDetails')
    expect(dictApi).toContain('/api/dict/process-details')
    expect(dictApi).toContain('saveProcessDetail')
  })

  test('dictionary store caches process categories and detail options', () => {
    expect(dictionaryStore).toContain('processCategories: []')
    expect(dictionaryStore).toContain('processDetails: []')
    expect(dictionaryStore).toContain('listProcessCategories')
    expect(dictionaryStore).toContain('listProcessDetails')
    expect(dictionaryStore).toContain('processDetailId')
    expect(dictionaryStore).toContain('processCode')
    expect(dictionaryStore).toContain('categoryName')
    expect(dictionaryStore).toContain('detailName')
    expect(dictionaryStore).toContain('hourlyRate')
    expect(dictionaryStore).toContain('processName: item.detailName')
    expect(dictionaryStore).toContain("calcType: '工时'")
  })

  test('material process page keeps MES-owned process category read-only and detail identity locked', () => {
    expect(materialProcess).toContain('工艺大类')
    expect(materialProcess).toContain('具体工艺')
    expect(materialProcess).toContain('工艺编码')
    expect(materialProcess).toContain('工艺名称')
    expect(materialProcess).toContain('小时价格')
    expect(materialProcess).toContain('来源')
    expect(materialProcess).toContain('状态')
    expect(materialProcess).toContain('备注')
    expect(materialProcess).toContain('具体备注')
    expect(materialProcess).not.toContain('@click="openProcessCategoryDialog')
    expect(materialProcess).not.toContain('@click="openProcessDetailDialog()"')
    expect(materialProcess).toContain('@click="openProcessDetailDialog(scope.row)"')
    expect(materialProcess).toContain('v-model="processDetailForm.categoryId" filterable disabled')
    expect(materialProcess).toContain('v-model="processDetailForm.detailName" readonly')
    expect(materialProcess).toContain('saveProcessDetail')
    expect(materialProcess).toContain("v-permission=\"'engineer:dict:process-manage'\"")
  })

  test('process page keeps other dictionary tabs and removes tier price editing from process master', () => {
    expect(materialProcess).toContain('name="materials"')
    expect(materialProcess).toContain('name="markupFees"')
    expect(materialProcess).toContain('name="assemblyMarkupFees"')
    expect(materialProcess).toContain('name="extraFees"')
    expect(materialProcess).not.toContain('processPriceFields')
    expect(materialProcess).not.toContain('price1To5')
    expect(materialProcess).not.toContain('price3000Plus')
  })

  test('process master layout has responsive safeguards', () => {
    expect(materialProcess).toContain('process-master-grid')
    expect(materialProcess).toContain('process-dialog-grid')
    expect(materialProcess).toContain('@media (max-width: 900px)')
  })
})
