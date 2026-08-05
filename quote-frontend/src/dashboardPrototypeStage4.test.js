import { describe, expect, test } from 'vitest'
import { existsSync, readFileSync } from 'node:fs'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

const currentDir = dirname(fileURLToPath(import.meta.url))
const dashboardDir = resolve(currentDir, 'components', 'dashboard')
const chartCardPath = resolve(dashboardDir, 'DashboardChartCard.vue')
const dashboardHomePath = resolve(dashboardDir, 'DashboardHome.vue')

const readSource = (path) => readFileSync(path, 'utf8')

describe('Dashboard prototype stage 4 ECharts chart card', () => {
  test('adds a reusable DashboardChartCard component with the approved props and select event', () => {
    expect(existsSync(chartCardPath)).toBe(true)

    const source = readSource(chartCardPath)
    expect(source).toContain("import * as echarts from 'echarts/core'")
    expect(source).toContain('echarts.use')
    expect(source).toContain('PieChart')
    expect(source).toContain('CanvasRenderer')
    expect(source).toContain('defineProps')
    expect(source).toContain('title:')
    expect(source).toContain('description:')
    expect(source).toContain('data:')
    expect(source).toContain('emptyText:')
    expect(source).toContain('palette:')
    expect(source).toContain('defineEmits')
    expect(source).toContain("emit('select'")
  })

  test('initializes, updates, resizes, and disposes ECharts safely', () => {
    expect(existsSync(chartCardPath)).toBe(true)

    const source = readSource(chartCardPath)
    expect(source).toContain('onMounted')
    expect(source).toContain('watch')
    expect(source).toContain('onBeforeUnmount')
    expect(source).toContain('echarts.init')
    expect(source).toContain('setOption')
    expect(source).toContain('.dispose()')
    expect(source).toContain("window.addEventListener('resize'")
    expect(source).toContain("window.removeEventListener('resize'")
    expect(source).toContain('.resize()')
  })

  test('uses solid animated pie options with tooltip, legend, no duplicate total, and clear empty state', () => {
    expect(existsSync(chartCardPath)).toBe(true)

    const source = readSource(chartCardPath)
    expect(source).toContain("type: 'pie'")
    expect(source).toContain("radius: '76%'")
    expect(source).toContain("animationType: 'expansion'")
    expect(source).toContain('animationDuration: 900')
    expect(source).toContain('tooltip')
    expect(source).toContain('legend')
    expect(source).toContain('borderWidth: 2')
    expect(source).not.toContain('graphic')
    expect(source).not.toContain('<el-tag')
    expect(source).not.toContain('formatDashboardNumber(total')
    expect(source).not.toContain("radius: ['")
    expect(source).toContain('dashboard-chart-empty')
    expect(source).toContain('hasData')
    expect(source).toContain('emptyText')
  })

  test('wires the dashboard home page to two ECharts chart cards and keeps page-level navigation', () => {
    const source = readSource(dashboardHomePath)

    expect(source).toContain("import DashboardChartCard from './DashboardChartCard.vue'")
    expect(source.match(/<DashboardChartCard/g)?.length).toBe(2)
    expect(source).toContain(':data="rfqStatusDistribution"')
    expect(source).toContain(':data="salesQuotedCustomerDistribution"')
    expect(source).toContain(':palette="rfqPalette"')
    expect(source).toContain(':palette="salesPalette"')
    expect(source).toContain("const rfqPalette = ['#F97316', '#22C55E', '#2563EB']")
    expect(source).toContain("const salesPalette = ['#2563EB', '#10B981', '#F59E0B', '#EF4444', '#06B6D4', '#8B5CF6']")
    expect(source).toContain('@select="goToItem"')
    expect(source).not.toContain('dashboard-donut-ring')
    expect(source).not.toContain('buildDonutStyle')
  })
})
