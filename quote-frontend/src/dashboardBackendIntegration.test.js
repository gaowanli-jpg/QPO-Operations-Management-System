import { describe, expect, test } from 'vitest'
import { existsSync, readFileSync } from 'node:fs'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

const currentDir = dirname(fileURLToPath(import.meta.url))
const dashboardDir = resolve(currentDir, 'components', 'dashboard')
const servicesDir = resolve(currentDir, 'services')
const dashboardHomePath = resolve(dashboardDir, 'DashboardHome.vue')
const dashboardApiPath = resolve(servicesDir, 'dashboardApi.js')

const readSource = (path) => readFileSync(path, 'utf8')

describe('Dashboard backend integration stage 4', () => {
  test('adds a read-only dashboard API service for the aggregate overview endpoint', () => {
    expect(existsSync(dashboardApiPath)).toBe(true)

    const source = readSource(dashboardApiPath)
    expect(source).toContain("import { request } from '../api'")
    expect(source).toContain('getDashboardOverview')
    expect(source).toContain("request('/api/dashboard/overview')")
    expect(source).not.toMatch(/\b(method:\s*'POST'|method:\s*'PUT'|method:\s*'DELETE'|method:\s*'PATCH')\b/)
  })

  test('dashboard home loads real overview data on entry and on refresh', () => {
    const source = readSource(dashboardHomePath)

    expect(source).toContain("import { getDashboardOverview } from '../../services/dashboardApi'")
    expect(source).toContain('const dashboardData = ref(emptyDashboardData())')
    expect(source).toContain('const loadDashboardOverview = async () =>')
    expect(source).toContain('await getDashboardOverview()')
    expect(source).toContain('onMounted(loadDashboardOverview)')
    expect(source).toContain('const refreshDashboard = () => loadDashboardOverview()')
    expect(source).toContain('@click="refreshDashboard"')
    expect(source).not.toContain("import { dashboardMockData } from './dashboardMockData'")
  })

  test('dashboard home exposes loading, failure, retry, and empty-safe state', () => {
    const source = readSource(dashboardHomePath)

    expect(source).toContain('v-loading="loading"')
    expect(source).toContain(':loading="loading"')
    expect(source).toContain('const loading = ref(false)')
    expect(source).toContain('const errorMessage = ref')
    expect(source).toContain('dashboard-error-state')
    expect(source).toContain('重试')
    expect(source).toContain('normalizeDashboardData')
    expect(source).toContain('summaryCards: []')
    expect(source).toContain('rfqStatusDistribution: []')
    expect(source).toContain('u9ReadyCustomerDistribution: []')
    expect(source).toContain('customerMatrix: []')
  })

  test('dashboard home declares initialization helpers before using them in refs', () => {
    const source = readSource(dashboardHomePath)

    expect(source.indexOf('const emptyDashboardData = () =>'))
      .toBeLessThan(source.indexOf('const dashboardData = ref(emptyDashboardData())'))
  })
  test('dashboard real data integration does not call business list or write APIs', () => {
    const source = readSource(dashboardHomePath)

    expect(source).not.toContain('marketApi')
    expect(source).not.toContain('deliveredItemApi')
    expect(source).not.toContain('orderApi')
    expect(source).not.toContain('listOrders')
    expect(source).not.toContain('listDeliveredItems')
    expect(source).not.toContain('listLockedItems')
    const forbiddenWriteApiPattern = new RegExp(
      `\\b(syncMesMaterial|submit${'WmsAllocation'}|syncSalesOrder|submitMesProduction|generateTicket)\\b`
    )
    expect(source).not.toMatch(forbiddenWriteApiPattern)
  })
})
