import { describe, expect, test } from 'vitest'
import { readFileSync } from 'node:fs'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

const currentDir = dirname(fileURLToPath(import.meta.url))
const pageSource = readFileSync(resolve(currentDir, 'components', 'DeliveredItemManage.vue'), 'utf8')

describe('DeliveredItemManage MES dedup stage 5 display', () => {
  test('shows MES dedup trace fields and follower explanation in the detail drawer', () => {
    const drawerSource = pageSource.slice(pageSource.indexOf('<el-drawer'))

    for (const token of [
      'label="MES请求角色"',
      'mesRequestRoleText(selectedItem.mesRequestRole)',
      'label="主提单ID"',
      'selectedItem.mesMasterTicketId',
      'label="主请求ID"',
      'selectedItem.mesMasterRequestId',
      'label="去重键"',
      'selectedItem.mesDedupKey',
      'dedupFollowerNote(selectedItem)',
      '已关联同图号版本主请求，未重复发送MES'
    ]) {
      expect(drawerSource).toContain(token)
    }
  })

  test('renders MES batch dedup result counts with business labels', () => {
    for (const token of [
      "import { getTask } from '../services/taskApi'",
      'mesBatchResult',
      'waitMesBatchTaskResult(taskId)',
      'getTask(taskId)',
      'formatMesBatchCount(mesBatchResult, \'dedupFollowerCount\')',
      'formatMesBatchCount(mesBatchResult, \'reusedMaterialCount\')',
      'formatMesBatchCount(mesBatchResult, \'submittedToMesCount\')',
      '关联待回调',
      '复用已确认物料号',
      '实际提交MES'
    ]) {
      expect(pageSource).toContain(token)
    }
  })

  test('keeps follower rows from showing a repeat MES submit action while waiting', () => {
    const statusColumnSource = pageSource.slice(
      pageSource.indexOf('label="MES状态"'),
      pageSource.indexOf('label="WMS状态"')
    )

    expect(statusColumnSource).toContain('dedupFollowerNote(row)')
    expect(statusColumnSource).toContain('关联待回调')
    expect(pageSource).toContain("item.mesRequestRole !== 'FOLLOWER'")
    expect(pageSource).not.toContain('const mesSyncAction = (row) =>')
    expect(pageSource).not.toContain('@click="handleMesSync(row)"')
  })
})
