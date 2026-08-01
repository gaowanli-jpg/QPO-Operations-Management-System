import { describe, expect, test } from 'vitest'
import { readFileSync } from 'node:fs'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

const currentDir = dirname(fileURLToPath(import.meta.url))
const pageSource = readFileSync(resolve(currentDir, 'components', 'DeliveredItemManage.vue'), 'utf8')

describe('DeliveredItemManage MES callback stage 5 regression', () => {
  test('keeps original MES action logic while adding callback pending guardrails', () => {
    const actionSource = pageSource.slice(
      pageSource.indexOf('const mesSyncAction = (row) =>'),
      pageSource.indexOf('const handleMesSync = async')
    )

    expect(actionSource).toContain("row.mesSyncStatus === 'SUCCESS') return null")
    expect(actionSource).toContain("row.mesSyncStatus === 'PROCESSING'")
    expect(actionSource).toContain("return { label: '处理中', type: 'warning', disabled: true }")
    expect(actionSource).toContain("row.mesSyncStatus === 'FAILED'")
    expect(actionSource).toContain("return { label: '重试', type: 'warning', disabled: false }")
    expect(actionSource).toContain("row.mesSyncStatus === 'CALLBACK_PENDING'")
    expect(actionSource).toContain("return { label: '待回调', type: 'warning', disabled: true }")
    expect(actionSource).toContain("return { label: '获取物料号', type: 'success', disabled: false }")
  })

  test('keeps callback pending visible in filter, table tag and batch confirmation copy', () => {
    expect(pageSource).toContain("{ value: 'CALLBACK_PENDING', label: '待MES回调' }")
    expect(pageSource).toContain("CALLBACK_PENDING: 'warning'")
    expect(pageSource).toContain('MES状态')
    expect(pageSource).toContain('已成功、处理中、待MES回调的行会自动跳过')
  })
})
