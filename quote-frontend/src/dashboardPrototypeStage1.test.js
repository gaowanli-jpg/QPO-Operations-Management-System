import { describe, expect, test } from 'vitest'
import { existsSync, readFileSync } from 'node:fs'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

const currentDir = dirname(fileURLToPath(import.meta.url))
const readSource = (...segments) => readFileSync(resolve(currentDir, ...segments), 'utf8')

describe('Dashboard prototype stage 1 shell', () => {
  test('declares ECharts as the only new chart dependency', () => {
    const packageJson = JSON.parse(readFileSync(resolve(currentDir, '..', 'package.json'), 'utf8'))

    expect(packageJson.dependencies.echarts).toBeDefined()
  })

  test('adds dashboard as the default route without a business module permission', () => {
    const routerSource = readSource('router', 'index.js')

    expect(routerSource).toContain("const DashboardHome = () => import('../components/dashboard/DashboardHome.vue')")
    expect(routerSource).toContain("{ path: '/', redirect: '/dashboard' }")
    expect(routerSource).toContain("{ path: '/dashboard', component: DashboardHome }")
    expect(routerSource).not.toContain("{ path: '/dashboard', component: DashboardHome, meta:")
  })

  test('keeps the dashboard visible at the top of the sidebar even before backend menu metadata exists', () => {
    const appSource = readSource('App.vue')

    expect(appSource).toContain("{ key: 'dashboard', title: '首页', paths: ['/dashboard'] }")
    expect(appSource).toContain("'/dashboard': { title: '首页看板'")
    expect(appSource).toContain("if (path === '/dashboard') return true")
    expect(appSource).toContain("'/dashboard': {")
    expect(appSource).toContain("title: '首页看板'")
    expect(appSource).toContain("description: '查看询价订单、销售订单、待办和异常关注项。'")
  })

  test('creates a non-blank dashboard skeleton page with refresh and placeholder areas', () => {
    const componentPath = resolve(currentDir, 'components', 'dashboard', 'DashboardHome.vue')
    expect(existsSync(componentPath)).toBe(true)

    const dashboardSource = readFileSync(componentPath, 'utf8')
    expect(dashboardSource).toContain('<h1 class="erp-page-title">首页看板</h1>')
    expect(dashboardSource).toContain('数据更新时间')
    expect(dashboardSource).toContain('刷新')
    expect(dashboardSource).toContain('dashboard-placeholder-grid')
    expect(dashboardSource).toContain('询价订单状态分布')
    expect(dashboardSource).toContain('销售订单已报价客户分布')
  })
})
