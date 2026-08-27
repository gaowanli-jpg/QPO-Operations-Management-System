import { describe, expect, test } from 'vitest'
import { readFileSync } from 'node:fs'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

const currentDir = dirname(fileURLToPath(import.meta.url))
const readSource = (...segments) => readFileSync(resolve(currentDir, ...segments), 'utf8').replace(/\r\n/g, '\n')

describe('engineering option picker remote paging mode', () => {
  test('keeps local item mode while adding remote mode props and state', () => {
    const source = readSource('components', 'EngineeringOptionPickerDialog.vue')

    expect(source).toContain('remote: {')
    expect(source).toContain('remoteLoader: {')
    expect(source).toContain('pageSizeOptions: {')
    expect(source).toContain('defaultPageSize: {')
    expect(source).toContain('const remotePage = ref(1)')
    expect(source).toContain('const remotePageSize = ref(normalizeRemotePageSize(props.defaultPageSize))')
    expect(source).toContain('const remoteTotal = ref(0)')
    expect(source).toContain('const remoteRecords = ref([])')
    expect(source).toContain('const remoteLoading = ref(false)')
    expect(source).toContain('const remoteError = ref(\'\')')
    expect(source).toContain('const displayItems = computed(() => (props.remote ? remoteRecords.value : props.items))')
    expect(source).toContain('if (props.remote) return remoteRecords.value')
    expect(source).toContain('filteredItems.value.slice(start, start + PROCESS_PICKER_PAGE_SIZE)')
  })

  test('loads the first remote page on open and reloads on paging changes', () => {
    const source = readSource('components', 'EngineeringOptionPickerDialog.vue')

    expect(source).toContain('@opened="handleOpened"')
    expect(source).toContain('const handleOpened = () => {')
    expect(source).toContain('loadRemotePage(1)')
    expect(source).toContain('const handleRemotePageSizeChange = () => loadRemotePage(1)')
    expect(source).toContain('const goPickerPage = (page) => {')
    expect(source).toContain('if (props.remote) {')
    expect(source).toContain('loadRemotePage(page)')
    expect(source).toContain('v-model="remotePageSize"')
    expect(source).toContain('@change="handleRemotePageSizeChange"')
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
