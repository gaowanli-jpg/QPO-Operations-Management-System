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

  test('disables repeated MES material requests while waiting for callback', () => {
    expect(pageSource).toContain("row.mesSyncStatus === 'CALLBACK_PENDING'")
    expect(pageSource).toContain("label: '待回调'")
    expect(pageSource).toContain('已成功、处理中、待MES回调的行会自动跳过')
  })
})
