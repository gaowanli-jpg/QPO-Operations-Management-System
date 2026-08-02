import { describe, expect, test } from 'vitest'
import { readFileSync } from 'node:fs'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

const currentDir = dirname(fileURLToPath(import.meta.url))
const pageSource = readFileSync(resolve(currentDir, 'components', 'DeliveredItemManage.vue'), 'utf8')

describe('DeliveredItemManage MES callback detail display', () => {
  test('shows callback pending state in filters, table labels, and batch skip copy', () => {
    expect(pageSource).toContain("{ value: 'CALLBACK_PENDING', label: '待MES回调' }")
    expect(pageSource).toContain("CALLBACK_PENDING: 'warning'")
    expect(pageSource).toContain('statusText(row.mesSyncStatus)')
    expect(pageSource).not.toContain("return { label: '待回调', type: 'warning', disabled: true }")
    expect(pageSource).not.toContain('@click="handleMesSync(row)"')
    expect(pageSource).toContain('已成功、处理中、待MES回调的行会自动跳过')
  })

  test('shows MES callback trace fields in the delivered item detail drawer', () => {
    const drawerSource = pageSource.slice(pageSource.indexOf('<el-drawer'))

    for (const token of [
      'label="MES请求ID"',
      'selectedItem.mesRequestId',
      'label="MES任务号"',
      'selectedItem.mesExternalTaskId',
      'label="回调时间"',
      'formatDateTime(selectedItem.mesCallbackReceivedAt)',
      'label="状态摘要"',
      'callbackStatusSummary(selectedItem)'
    ]) {
      expect(drawerSource).toContain(token)
    }

    expect(pageSource).toContain('selectedItem.mesCallbackMessage || selectedItem.mesSyncMessage')
  })
})
