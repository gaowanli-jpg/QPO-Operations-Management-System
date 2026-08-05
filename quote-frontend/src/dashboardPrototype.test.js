import { describe, expect, test } from 'vitest'
import { existsSync, readFileSync } from 'node:fs'
import { dirname, resolve } from 'node:path'
import { pathToFileURL, fileURLToPath } from 'node:url'

const currentDir = dirname(fileURLToPath(import.meta.url))
const frontendRoot = resolve(currentDir, '..')
const dashboardDir = resolve(currentDir, 'components', 'dashboard')
const readSource = (...segments) => readFileSync(resolve(currentDir, ...segments), 'utf8')
const importFile = (path) => import(pathToFileURL(path).href)

describe('Dashboard prototype phase 6 acceptance guard', () => {
  test('keeps the dashboard route as the login landing page entry', () => {
    const routerSource = readSource('router', 'index.js')

    expect(routerSource).toContain("const DashboardHome = () => import('../components/dashboard/DashboardHome.vue')")
    expect(routerSource).toContain("{ path: '/', redirect: '/dashboard' }")
    expect(routerSource).toContain("{ path: '/dashboard', component: DashboardHome }")
    expect(routerSource).not.toContain("{ path: '/dashboard', component: DashboardHome, meta:")
  })

  test('keeps the sidebar dashboard menu visible at the top of App shell', () => {
    const appSource = readSource('App.vue')

    expect(appSource).toContain("{ key: 'dashboard', title: '首页', paths: ['/dashboard'] }")
    expect(appSource).toContain("'/dashboard': { title: '首页看板'")
    expect(appSource).toContain("if (path === '/dashboard') return true")
  })

  test('keeps mock dashboard statistics inside the approved business scope', async () => {
    const { dashboardMockData } = await importFile(resolve(dashboardDir, 'dashboardMockData.js'))

    expect(dashboardMockData.rfqStatusDistribution.map((item) => item.name)).toEqual(['未核价', '已核价', '已报价'])
    expect(dashboardMockData.salesQuotedCustomerDistribution.every((item) => item.customerId && (item.customerName || item.name))).toBe(true)
    expect(dashboardMockData.salesQuotedCustomerDistribution.every((item) => item.targetPath === '/sales-orders')).toBe(true)
    expect(dashboardMockData.salesQuotedCustomerDistribution.every((item) => item.query?.status === '已报价')).toBe(true)
    expect(dashboardMockData.salesQuotedCustomerDistribution.some((item) => item.name === '已报价')).toBe(false)
  })

  test('does not use post-ticket statuses as main dashboard chart data', () => {
    const dashboardHomeSource = readFileSync(resolve(dashboardDir, 'DashboardHome.vue'), 'utf8')
    const mockSource = readFileSync(resolve(dashboardDir, 'dashboardMockData.js'), 'utf8')
    const chartDataSource = `${dashboardHomeSource}\n${mockSource}`

    expect(chartDataSource).not.toContain('部分已提单')
    expect(chartDataSource).not.toContain('已生成提单')
    expect(chartDataSource).not.toContain('废弃')
    expect(chartDataSource).not.toContain('partiallyReleased')
    expect(chartDataSource).not.toContain('released')
    expect(chartDataSource).not.toContain('discarded')
  })

  test('uses ECharts through the dashboard chart component and avoids business write APIs', () => {
    const dashboardHomeSource = readFileSync(resolve(dashboardDir, 'DashboardHome.vue'), 'utf8')
    const chartCardSource = readFileSync(resolve(dashboardDir, 'DashboardChartCard.vue'), 'utf8')
    const dashboardComponentSources = [
      'DashboardHome.vue',
      'DashboardSummaryCard.vue',
      'DashboardTodoCard.vue',
      'DashboardAttentionList.vue',
      'DashboardCustomerMatrix.vue',
      'DashboardChartCard.vue'
    ].map((fileName) => readFileSync(resolve(dashboardDir, fileName), 'utf8')).join('\n')

    expect(dashboardHomeSource).toContain("import DashboardChartCard from './DashboardChartCard.vue'")
    expect(chartCardSource).toContain("import * as echarts from 'echarts/core'")
    expect(chartCardSource).toContain('PieChart')
    expect(dashboardComponentSources).not.toContain('/api/')
    expect(dashboardComponentSources).not.toContain('marketApi')
    expect(dashboardComponentSources).not.toContain('deliveredItemApi')
    expect(dashboardComponentSources).not.toContain('u9SyncApi')
    expect(dashboardComponentSources).not.toContain('syncSalesOrder')
    expect(dashboardComponentSources).not.toContain('syncMaterial')
    expect(dashboardComponentSources).not.toContain('allocateInventory')
    expect(dashboardComponentSources).not.toContain('mesProduction')
  })

  test('keeps package dependency metadata aligned without adding another UI library', () => {
    const packageJson = JSON.parse(readFileSync(resolve(frontendRoot, 'package.json'), 'utf8'))
    const packageLock = JSON.parse(readFileSync(resolve(frontendRoot, 'package-lock.json'), 'utf8'))
    const forbiddenUiLibraries = ['ant-design-vue', 'naive-ui', 'view-ui-plus', 'vuetify', '@arco-design/web-vue']

    expect(packageJson.dependencies.echarts).toBeDefined()
    expect(packageLock.packages[''].dependencies.echarts).toBe(packageJson.dependencies.echarts)
    expect(packageLock.packages['node_modules/echarts']).toBeDefined()
    expect(existsSync(resolve(frontendRoot, 'package-lock.json'))).toBe(true)
    for (const libraryName of forbiddenUiLibraries) {
      expect(packageJson.dependencies[libraryName]).toBeUndefined()
      expect(packageLock.packages[`node_modules/${libraryName}`]).toBeUndefined()
    }
  })
})
