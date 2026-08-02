import { describe, expect, test } from 'vitest'
import { readFileSync } from 'node:fs'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

const currentDir = dirname(fileURLToPath(import.meta.url))
const pageSource = readFileSync(resolve(currentDir, 'components', 'DeliveredItemManage.vue'), 'utf8')

describe('DeliveredItemManage MES callback stage 5 regression', () => {
  test('keeps MES callback guardrails while removing row-level MES submit actions', () => {
    expect(pageSource).toContain("CALLBACK_PENDING: 'warning'")
    expect(pageSource).toContain('statusText(row.mesSyncStatus)')
    expect(pageSource).toContain('handleBatchMesSync')
    expect(pageSource).not.toContain('const mesSyncAction = (row) =>')
    expect(pageSource).not.toContain('const handleMesSync = async')
    expect(pageSource).not.toContain('@click="handleMesSync(row)"')
  })

  test('keeps callback pending visible in filter, table tag and batch confirmation copy', () => {
    expect(pageSource).toContain("{ value: 'CALLBACK_PENDING', label: '待MES回调' }")
    expect(pageSource).toContain("CALLBACK_PENDING: 'warning'")
    expect(pageSource).toContain('MES状态')
    expect(pageSource).toContain('已成功、处理中、待MES回调的行会自动跳过')
  })
})
