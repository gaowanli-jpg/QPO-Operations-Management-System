import { describe, expect, test } from 'vitest'
import { readFileSync } from 'node:fs'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

const currentDir = dirname(fileURLToPath(import.meta.url))
const readSource = (...segments) => readFileSync(resolve(currentDir, ...segments), 'utf8').replace(/\r\n/g, '\n')

describe('engineering option picker remote paging mode', () => {
  test('uses remote paging as the only option source without legacy local ten-row paging', () => {
    const source = readSource('components', 'EngineeringOptionPickerDialog.vue')

    expect(source).toContain('remoteLoader: {')
    expect(source).toContain('pageSizeOptions: {')
    expect(source).toContain('defaultPageSize: {')
    expect(source).toContain('const page = ref(1)')
    expect(source).toContain('const remotePageSize = ref(normalizeRemotePageSize(props.defaultPageSize))')
    expect(source).toContain('const remoteTotal = ref(0)')
    expect(source).toContain('const remoteRecords = ref([])')
    expect(source).toContain('const remoteLoading = ref(false)')
    expect(source).toContain('const remoteError = ref(\'\')')
    expect(source).toContain('const pagedItems = computed(() => remoteRecords.value)')
    expect(source).not.toContain('const PROCESS_PICKER_PAGE_SIZE = 10')
    expect(source).not.toContain('processPickerPage')
    expect(source).not.toContain('filteredItems.value.slice')
    expect(source).not.toContain('props.items')
    expect(source).not.toContain('remote: {')
  })

  test('loads the first remote page on open and reloads on paging changes', () => {
    const source = readSource('components', 'EngineeringOptionPickerDialog.vue')

    expect(source).toContain('@opened="handleOpened"')
    expect(source).toContain('const handleOpened = () => {')
    expect(source).toContain('loadRemotePage(1)')
    expect(source).toContain('const handleRemotePageSizeChange = () => loadRemotePage(1)')
    expect(source).toContain('const goPickerPage = (page) => {')
    expect(source).toContain('loadRemotePage(page)')
    expect(source).toContain('v-model="remotePageSize"')
    expect(source).toContain('@change="handleRemotePageSizeChange"')
  })

  test('process picker combines arrangement navigation with the existing editable table', () => {
    const source = readSource('components', 'EngineeringOptionPickerDialog.vue')

    expect(source).toContain('processArrangementTreeLoader: {')
    expect(source).toContain('process-picker-layout')
    expect(source).toContain('process-arrangement-picker-panel')
    expect(source).toContain(':data="processArrangementTree"')
    expect(source).toContain('@node-click="selectProcessArrangementNode"')
    expect(source).toContain('processSourceTypeFilter')
    expect(source).toContain('selectedProcessArrangementPathLabel')
    expect(source).toContain('collectProcessArrangementPickerFilter')
    expect(source).toContain('feeItemIds: pickerFilter.feeItemIds.join(\',\')')
    expect(source).toContain('label="小时价格"')
    expect(source).toContain('saveInlineProcessRate(row)')
    expect(source).toContain('label="所在层级"')
  })

  test('material picker exposes type tabs and sends selected type codes to remote paging', () => {
    const source = readSource('components', 'EngineeringOptionPickerDialog.vue')

    expect(source).toContain('MATERIAL_TYPE_FILTER_OPTIONS')
    expect(source).toContain('label: \'原材料\', value: \'MATERIAL,RAW_MATERIAL\'')
    expect(source).toContain('label: \'半成品\', value: \'SEMI\'')
    expect(source).toContain('label: \'成品\', value: \'PRODUCT\'')
    expect(source).toContain('v-if="type === \'material\'"')
    expect(source).toContain('v-model="materialTypeFilter"')
    expect(source).toContain('params.materialTypeCodes = materialTypeFilter.value')
    expect(source).toContain('watch(materialTypeFilter, () => {')
  })

  test('debounces keyword search and ignores stale remote responses', () => {
    const source = readSource('components', 'EngineeringOptionPickerDialog.vue')

    expect(source).toContain('const remoteSearchTimer = ref(null)')
    expect(source).toContain('const remoteRequestSeq = ref(0)')
    expect(source).toContain('clearRemoteSearchTimer()')
    expect(source).toContain('remoteSearchTimer.value = window.setTimeout(() => loadRemotePage(1), 300)')
    expect(source).toContain('const requestSeq = remoteRequestSeq.value + 1')
    expect(source).toContain('remoteRequestSeq.value = requestSeq')
    expect(source).toContain('if (requestSeq !== remoteRequestSeq.value) return')
  })

  test('shows remote loading and errors without dropping selected rows', () => {
    const source = readSource('components', 'EngineeringOptionPickerDialog.vue')

    expect(source).toContain(':loading="remoteLoading"')
    expect(source).toContain('v-if="remoteError"')
    expect(source).toContain(':title="remoteError"')
    expect(source).toContain('remoteError.value = error?.message || \'选项加载失败，请稍后重试。\'')
    expect(source).toContain('const selectedItemSnapshot = ref(null)')
    expect(source).toContain('selectedItemSnapshot.value = row')
    expect(source).toContain('selectedItemSnapshot.value && keyOf(selectedItemSnapshot.value) === currentKey')
    expect(source).not.toContain('remoteRecords.value = []\n    remoteError.value = error')
  })
})
