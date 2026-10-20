import { describe, expect, test } from 'vitest'
import { readFileSync } from 'node:fs'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

const currentDir = dirname(fileURLToPath(import.meta.url))
const readSource = (...segments) => readFileSync(resolve(currentDir, ...segments), 'utf8').replace(/\r\n/g, '\n')

const dictApi = readSource('services', 'dictApi.js')
const engineerApi = readSource('services', 'engineerApi.js')
const materialProcess = readSource('components', 'MaterialProcessManage.vue')
const optionPicker = readSource('components', 'EngineeringOptionPickerDialog.vue')
const itemPricing = readSource('components', 'ItemPricing.vue')

describe('common material management and pricing picker defaults', () => {
  test('dictApi exposes paged common material filters and preview-confirm endpoints', () => {
    expect(dictApi).toContain('pageMaterials = ({ enabledOnly, keyword, page, pageSize, commonScope, materialTypeCodes, categoryName } = {})')
    expect(dictApi).toContain('buildQuery({ enabledOnly, keyword, page, pageSize, commonScope, materialTypeCodes, categoryName })')
    expect(dictApi).toContain('updateMaterialCommonStatus')
    expect(dictApi).toContain('/api/dict/materials/common-status')
    expect(dictApi).toContain('previewMaterialCommonImport')
    expect(dictApi).toContain('/api/dict/materials/common-status/import-preview')
    expect(dictApi).toContain('listMaterialU9CategoryNames')
    expect(dictApi).toContain('/api/dict/materials/u9-category-names')
    expect(dictApi).toContain('FormData')
  })

  test('engineer material picker forwards common scope to remote paging', () => {
    expect(engineerApi).toContain('pagePricingMaterialOptions')
    expect(optionPicker).toContain('defaultMaterialCommonScope')
    expect(optionPicker).toContain('MATERIAL_COMMON_SCOPE_OPTIONS')
    expect(optionPicker).toContain("{ label: '常用', value: 'COMMON' }")
    expect(optionPicker).toContain("{ label: '全部', value: 'ALL' }")
    expect(optionPicker).toContain("{ label: '不常用', value: 'UNCOMMON' }")
    expect(optionPicker).toContain('v-model="materialCommonScopeFilter"')
    expect(optionPicker).toContain('params.commonScope = materialCommonScopeFilter.value')
    expect(optionPicker).toContain('watch(materialCommonScopeFilter')
  })

  test('single-item pricing opens material picker on common materials by default', () => {
    const dialogStart = itemPricing.indexOf('<EngineeringOptionPickerDialog\n      v-model="materialPickerVisible"')
    const dialogEnd = itemPricing.indexOf('@material-price-updated="handleMaterialPriceUpdated"', dialogStart)
    const dialogBlock = itemPricing.slice(dialogStart, dialogEnd)

    expect(dialogBlock).toContain('type="material"')
    expect(dialogBlock).toContain('default-material-common-scope="COMMON"')
    expect(dialogBlock).toContain(':remote-loader="pagePricingMaterialOptions"')
  })

  test('material process page adds a server-paged common material workbench', () => {
    expect(materialProcess).toContain('label="常用材料" name="commonMaterials"')
    expect(materialProcess).toContain('common-material-workbench')
    expect(materialProcess).toContain('commonMaterialLeftPagination')
    expect(materialProcess).toContain('commonMaterialRightPagination')
    expect(materialProcess).toContain("commonScope: 'COMMON'")
    expect(materialProcess).toContain("commonScope: 'UNCOMMON'")
    expect(materialProcess).toContain('commonMaterialLeftTypeCodes')
    expect(materialProcess).toContain('commonMaterialRightTypeCodes')
    expect(materialProcess).toContain('commonMaterialLeftCategoryName')
    expect(materialProcess).toContain('commonMaterialRightCategoryName')
    expect(materialProcess).toContain('loadCommonMaterialLeftCategoryNames')
    expect(materialProcess).toContain('loadCommonMaterialRightCategoryNames')
    expect(materialProcess).toContain('materialTypeCodes: commonMaterialLeftTypeCodes.value')
    expect(materialProcess).toContain('materialTypeCodes: commonMaterialRightTypeCodes.value')
    expect(materialProcess).toContain('categoryName: commonMaterialLeftCategoryName.value')
    expect(materialProcess).toContain('categoryName: commonMaterialRightCategoryName.value')
    expect(materialProcess).toContain('selectedCommonMaterialRows')
    expect(materialProcess).toContain('selectedUncommonMaterialRows')
    expect(materialProcess).toContain('markSelectedMaterialsCommon')
    expect(materialProcess).toContain('markSelectedMaterialsUncommon')
    expect(materialProcess).not.toContain('allMaterials.filter(row => row.commonMaterial')
  })

  test('common material import previews material-code matches before applying changes', () => {
    expect(materialProcess).toContain('commonMaterialImportPreviewVisible')
    expect(materialProcess).toContain('previewMaterialCommonImport')
    expect(materialProcess).toContain('confirmCommonMaterialImport')
    expect(materialProcess).toContain('commonImportPreview.matchedMaterialIds')
    expect(materialProcess).toContain('uploadedCount')
    expect(materialProcess).toContain('matchedCount')
    expect(materialProcess).toContain('notFoundCount')
    expect(materialProcess).toContain('duplicateCount')
  })
})
