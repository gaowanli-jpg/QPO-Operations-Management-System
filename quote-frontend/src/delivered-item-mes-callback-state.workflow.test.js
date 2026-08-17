import { describe, expect, test } from 'vitest'
import { readFileSync } from 'node:fs'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

const currentDir = dirname(fileURLToPath(import.meta.url))
const pageSource = readFileSync(resolve(currentDir, 'components', 'DeliveredItemManage.vue'), 'utf8')

describe('DeliveredItemManage MES callback pending state', () => {
  test('lists callback pending as a selectable MES status', () => {
    expect(pageSource).toContain("value: 'CALLBACK_PENDING'")
    expect(pageSource).toContain("label: '待MES回调'")
  })

  test('keeps callback pending visible while batch submission skips waiting rows', () => {
    expect(pageSource).toContain("CALLBACK_PENDING: 'warning'")
    expect(pageSource).toContain('statusText(row.mesSyncStatus)')
    expect(pageSource).not.toContain("label: '待回调'")
    expect(pageSource).not.toContain('@click="handleMesSync(row)"')
    expect(pageSource).toContain('已成功、处理中、待MES回调的行会自动跳过')
  })
})
