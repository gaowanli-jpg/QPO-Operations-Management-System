import { describe, expect, test, vi } from 'vitest'
import { existsSync, readFileSync } from 'node:fs'
import { dirname, resolve } from 'node:path'
import { pathToFileURL, fileURLToPath } from 'node:url'

const currentDir = dirname(fileURLToPath(import.meta.url))
const dashboardDir = resolve(currentDir, 'components', 'dashboard')
const mockPath = resolve(dashboardDir, 'dashboardMockData.js')
const navigationPath = resolve(dashboardDir, 'dashboardNavigation.js')

const importFile = (path) => import(pathToFileURL(path).href)

describe('Dashboard prototype stage 2 data contract', () => {
  test('provides stable mock data sections for the dashboard prototype', async () => {
    expect(existsSync(mockPath)).toBe(true)

    const { dashboardMockData } = await importFile(mockPath)
    expect(dashboardMockData).toEqual(expect.objectContaining({
      summaryCards: expect.any(Array),
      rfqStatusDistribution: expect.any(Array),
      u9ReadyCustomerDistribution: expect.any(Array),
      customerMatrix: expect.any(Array),
      todoCards: expect.any(Array),
      attentionItems: expect.any(Array)
    }))
  })

  test('keeps mock statistics aligned to the approved dashboard demand scope', async () => {
    expect(existsSync(mockPath)).toBe(true)

    const { dashboardMockData } = await importFile(mockPath)
    expect(dashboardMockData.rfqStatusDistribution.map((item) => item.name)).toEqual(['未核价', '已核价', '已报价'])
    expect(dashboardMockData.u9ReadyCustomerDistribution.every((item) => item.customerName || item.name)).toBe(true)
    expect(dashboardMockData.u9ReadyCustomerDistribution.some((item) => item.name === '已生成提单')).toBe(false)

    const matrixSource = JSON.stringify(dashboardMockData.customerMatrix)
    expect(matrixSource).not.toContain('部分已提单')
    expect(matrixSource).not.toContain('已生成提单')
    expect(matrixSource).not.toContain('废弃')
    expect(matrixSource).not.toContain('partiallyReleased')
    expect(matrixSource).not.toContain('released')
    expect(matrixSource).not.toContain('discarded')
  })

  test('keeps sales order dashboard todos aligned to anomaly governance wording', async () => {
    expect(existsSync(mockPath)).toBe(true)

    const { dashboardMockData } = await importFile(mockPath)
    const todoSource = JSON.stringify(dashboardMockData.todoCards)
    const anomalyTodo = dashboardMockData.todoCards.find((item) => item.path === '/sales-match')

    expect(todoSource).not.toContain('待补全销售订单')
    expect(todoSource).not.toContain('按销售订单补全真实材料和生产快照')
    expect(anomalyTodo).toEqual(expect.objectContaining({
      title: '销售订单异常治理',
      description: '排查历史订单或自动补全异常',
      permission: 'market:sales-match:view'
    }))
  })

  test('does not embed secrets, real endpoints, or sensitive customer data in mock data', () => {
    expect(existsSync(mockPath)).toBe(true)

    const mockSource = readFileSync(mockPath, 'utf8')
    expect(mockSource).not.toMatch(/token|Authorization|client_secret|https?:\/\/|192\.168\./i)
    expect(mockSource).not.toMatch(/\/api\/(market|order-delivery|dashboard)/)
    expect(mockSource).toContain('示例')
  })
})

describe('Dashboard prototype stage 2 navigation helpers', () => {
  test('filters dashboard actions by permission and keeps public actions visible', async () => {
    expect(existsSync(navigationPath)).toBe(true)

    const { canShowDashboardAction } = await importFile(navigationPath)
    const authStore = { hasPermission: (permission) => permission === 'market:order:view' }

    expect(canShowDashboardAction({ permission: 'market:order:view' }, authStore)).toBe(true)
    expect(canShowDashboardAction({ permission: 'market:sales-order:view' }, authStore)).toBe(false)
    expect(canShowDashboardAction({}, authStore)).toBe(true)
    expect(canShowDashboardAction({ permission: 'market:order:view' }, {})).toBe(false)
  })

  test('formats numbers and centralizes dashboard router pushes', async () => {
    expect(existsSync(navigationPath)).toBe(true)

    const { formatDashboardNumber, goDashboardTarget } = await importFile(navigationPath)
    const router = { push: vi.fn(() => Promise.resolve()) }

    expect(formatDashboardNumber(12345)).toBe('12,345')
    expect(formatDashboardNumber(null)).toBe('0')
    expect(formatDashboardNumber('abc')).toBe('0')

    await goDashboardTarget(router, { path: '/orders', query: { status: '未核价' } })
    expect(router.push).toHaveBeenCalledWith({ path: '/orders', query: { status: '未核价' } })
  })
})

describe('Dashboard prototype stage 2 page wiring', () => {
  test('keeps mock data as fixture while production dashboard avoids business list APIs', () => {
    const dashboardSource = readFileSync(resolve(dashboardDir, 'DashboardHome.vue'), 'utf8')

    expect(dashboardSource).toContain("import { goDashboardTarget } from './dashboardNavigation'")
    expect(dashboardSource).toContain('summaryCards')
    expect(dashboardSource).toContain('rfqStatusDistribution')
    expect(dashboardSource).toContain('u9ReadyCustomerDistribution')
    expect(dashboardSource).toContain('customerMatrix')
    expect(dashboardSource).toContain('todoCards')
    expect(dashboardSource).toContain('attentionItems')
    expect(dashboardSource).not.toContain("import { dashboardMockData } from './dashboardMockData'")
    expect(dashboardSource).not.toContain('marketApi')
    expect(dashboardSource).not.toContain('deliveredItemApi')
    expect(dashboardSource).not.toContain('orderApi')
  })
})
