import { describe, expect, test } from 'vitest'
import { readFileSync } from 'node:fs'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

const currentDir = dirname(fileURLToPath(import.meta.url))
const dashboardSource = () => readFileSync(resolve(currentDir, 'components', 'dashboard', 'DashboardHome.vue'), 'utf8')

describe('Dashboard prototype stage 3 visual workbench', () => {
  test('composes the approved dashboard regions with QPO surface and muted utility classes', () => {
    const source = dashboardSource()

    expect(source).toContain('dashboard-home')
    expect(source).toContain('dashboard-hero')
    expect(source).toContain('dashboard-summary-grid')
    expect(source).toContain('dashboard-chart-grid')
    expect(source).toContain('dashboard-workbench-grid')
    expect(source).toContain('dashboard-surface')
    expect(source).toContain('dashboard-muted')
    expect(source).toContain('客户状态交叉表')
    expect(source).toContain('待办快捷入口')
    expect(source).toContain('最近异常 / 关注项')
  })

  test('renders two modern pie-like chart cards without introducing the stage 4 ECharts wrapper yet', () => {
    const source = dashboardSource()

    expect(source).toContain('dashboard-chart-shell')
    expect(source).toContain('dashboard-donut-ring')
    expect(source).toContain('dashboard-donut-center')
    expect(source).toContain('dashboard-chart-legend')
    expect(source).toContain('rfqChartStyle')
    expect(source).toContain('salesChartStyle')
    expect(source).not.toContain('DashboardChartCard')
    expect(source).not.toContain('echarts.init')
  })

  test('keeps the page professional and not like a temporary prototype note or marketing hero', () => {
    const source = dashboardSource()

    expect(source).not.toContain('阶段 2 mock 数据和导航辅助')
    expect(source).not.toContain('dashboard-stage-note')
    expect(source).not.toContain('linear-gradient')
    expect(source).not.toContain('hero-image')
    expect(source).not.toContain('decorative')
  })

  test('uses stable hover feedback without layout-shifting transforms on cards', () => {
    const source = dashboardSource()

    expect(source).toContain('.dashboard-summary-card:hover')
    expect(source).toContain('.dashboard-action-item:hover')
    expect(source).not.toMatch(/\.dashboard-summary-card:hover[\s\S]*?transform:/)
    expect(source).not.toMatch(/\.dashboard-action-item:hover[\s\S]*?transform:/)
  })
})
