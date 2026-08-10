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
    expect(source).toContain('customerMatrix')
    expect(source).toContain('visibleTodoCards')
    expect(source).toContain('visibleAttentionItems')
  })

  test('keeps the chart area structured as two reusable dashboard chart cards', () => {
    const source = dashboardSource()

    expect(source).toContain('DashboardChartCard')
    expect(source).toContain(':data="rfqStatusDistribution"')
    expect(source).toContain(':data="u9ReadyCustomerDistribution"')
    expect(source).toContain('@select="goToItem"')
  })

  test('keeps the page professional and not like a temporary prototype note or marketing hero', () => {
    const source = dashboardSource()

    expect(source).not.toContain('阶段 2')
    expect(source).not.toContain('阶段 3')
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
