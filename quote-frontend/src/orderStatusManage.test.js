import { describe, expect, test, vi } from 'vitest'
import { readFileSync } from 'node:fs'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'
import { usePendingAction } from './composables/usePendingAction'

const currentDir = dirname(fileURLToPath(import.meta.url))
const readSource = (...segments) => readFileSync(resolve(currentDir, ...segments), 'utf8')

const orderManage = readSource('components', 'OrderManage.vue')
const orderApi = readSource('services', 'orderApi.js')

const blockBetween = (source, start, end) => {
  const startIndex = source.indexOf(start)
  const endIndex = source.indexOf(end, startIndex)
  expect(startIndex, `Expected source to include start marker ${start}`).toBeGreaterThanOrEqual(0)
  expect(endIndex, `Expected source to include end marker ${end}`).toBeGreaterThan(startIndex)
  return source.slice(startIndex, endIndex)
}

const deferred = () => {
  let resolve
  const promise = new Promise((done) => {
    resolve = done
  })
  return { promise, resolve }
}

describe('order status management stage 0 guardrails', () => {
  test('freezes the six order management status filters', () => {
    expect(orderManage).toContain('ORDER_MANAGEMENT_STATUSES')
    for (const status of ['未核价', '已核价', '已报价', '部分已提单', '已生成提单', '废弃']) {
      expect(orderManage).toContain(status)
    }
  })

  test('freezes status action permissions under order management', () => {
    for (const permission of [
      'market:order:rollback-quoted',
      'market:order:rollback-priced',
      'market:order:discard',
      'market:order:restore'
    ]) {
      expect(orderManage).toContain(permission)
    }
  })

  test('freezes action labels and immutable released-order boundary', () => {
    expect(orderManage).toContain('退回已核价')
    expect(orderManage).toContain('退回未核价')
    expect(orderManage).toContain('废弃')
    expect(orderManage).toContain('恢复为已核价')
    expect(orderManage).toContain('部分已提单')
    expect(orderManage).toContain('已生成提单')
    expect(orderManage).toContain('订单已生成提单，不能废弃或回退')
  })

  test('freezes order status action APIs', () => {
    expect(orderApi).toContain('rollbackQuotedOrder')
    expect(orderApi).toContain('/api/order-management/orders/${rfqId}/rollback-quoted')
    expect(orderApi).toContain('rollbackPricedOrder')
    expect(orderApi).toContain('/api/order-management/orders/${rfqId}/rollback-priced')
    expect(orderApi).toContain('discardOrder')
    expect(orderApi).toContain('/api/order-management/orders/${rfqId}/discard')
    expect(orderApi).toContain('restoreOrder')
    expect(orderApi).toContain('/api/order-management/orders/${rfqId}/restore')
  })

  test('wires the status filter into the order query params', () => {
    expect(orderManage).toContain('v-model="statusFilter"')
    expect(orderManage).toContain('v-for="status in ORDER_MANAGEMENT_STATUSES"')
    expect(orderManage).toContain('status: statusFilter.value || undefined')
    expect(orderManage).toContain('statusFilter.value = \'\'')
  })

  test('renders allowed row status actions with permission guards only for mutable states', () => {
    expect(orderManage).toContain('rowStatusActions(scope.row)')
    expect(orderManage).toContain('v-permission="action.permission"')
    expect(orderManage).toContain('@click="handleStatusAction(scope.row, action)"')
    expect(orderManage).toContain('ORDER_STATUS_ACTIONS.filter(action => action.status === row.status)')
    expect(orderManage).toContain('return []')
    expect(orderManage).toContain('RELEASED_ORDER_STATUSES.includes(row.status)')
    expect(orderManage).toContain('Number(row.ticketCount || 0) > 0')
  })

  test('confirms and executes status actions then refreshes list and opened detail', () => {
    expect(orderManage).toContain('ElMessageBox.confirm')
    expect(orderManage).toContain('action.handler(row.id, { reason: action.reason })')
    expect(orderManage).toContain('await loadOrders()')
    expect(orderManage).toContain('await refreshCurrentDetail()')
    expect(orderManage).toContain('ElMessage.success(response.message')
    expect(orderManage).toContain('error.response?.data?.message || action.errorMessage')
  })

  test('uses order-scoped pending keys for status changes and supplement drawings', () => {
    expect(orderManage).toContain("import { usePendingAction } from '../composables/usePendingAction'")
    expect(orderManage).toContain('const { isPending, runAction } = usePendingAction()')
    expect(orderManage).toContain("statusChange: (orderId) => `order:status-change:${orderId || 'none'}`")
    expect(orderManage).toContain("supplementDrawings: (orderId) => `order:supplement-drawings:${orderId || 'none'}`")
    expect(orderManage).toContain(':loading="isOrderStatusPending(scope.row)"')
    expect(orderManage).toContain(':disabled="isOrderStatusPending(scope.row)"')
    expect(orderManage).toContain(':pending="supplementUploadPending"')
    expect(orderManage).toContain('v-model:files="supplementFiles"')
  })

  test('wraps order status changes and supplement uploads with runAction guards', () => {
    const statusBlock = blockBetween(orderManage, 'const performStatusAction = async (row, action) =>', 'const submitSupplementDrawings = async () =>')
    const supplementBlock = blockBetween(orderManage, 'const submitSupplementDrawings = async () =>', 'const formatRoute = (route) =>')

    expect(statusBlock).toContain('return runAction(orderActionKeys.statusChange(row.id), async () =>')
    expect(statusBlock).toContain('await action.handler(row.id, { reason: action.reason })')
    expect(supplementBlock).toContain('return runAction(orderActionKeys.supplementDrawings(rfqId), async () =>')
    expect(supplementBlock).toContain('await supplementDrawings(rfqId, formData)')
  })

  test('does not ship frontend helpers for retired history-order delete', () => {
    expect(orderApi).not.toContain('export const deleteOrder')
    expect(orderApi).not.toContain('/api/order-management/history-orders/customers')
    expect(orderApi).not.toContain("'history-order'")
  })

  test('status action key suppresses duplicate order status requests while pending', async () => {
    const { runAction } = usePendingAction()
    const pendingRequest = deferred()
    const updateOrderStatus = vi.fn(() => pendingRequest.promise)

    const firstClick = runAction('order:status-change:88', updateOrderStatus)
    const secondClick = runAction('order:status-change:88', updateOrderStatus)

    await expect(secondClick).resolves.toBeUndefined()
    expect(updateOrderStatus).toHaveBeenCalledTimes(1)

    pendingRequest.resolve({ message: 'ok' })

    await expect(firstClick).resolves.toEqual({ message: 'ok' })
    expect(updateOrderStatus).toHaveBeenCalledTimes(1)
  })
})
