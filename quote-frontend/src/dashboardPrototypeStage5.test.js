import { describe, expect, test } from 'vitest'
import { existsSync, readFileSync } from 'node:fs'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

const currentDir = dirname(fileURLToPath(import.meta.url))
const dashboardDir = resolve(currentDir, 'components', 'dashboard')

const readSource = (fileName) => readFileSync(resolve(dashboardDir, fileName), 'utf8')

describe('Dashboard prototype stage 5 interactive workbench components', () => {
  test('extracts the four approved dashboard interaction components', () => {
    expect(existsSync(resolve(dashboardDir, 'DashboardSummaryCard.vue'))).toBe(true)
    expect(existsSync(resolve(dashboardDir, 'DashboardTodoCard.vue'))).toBe(true)
    expect(existsSync(resolve(dashboardDir, 'DashboardAttentionList.vue'))).toBe(true)
    expect(existsSync(resolve(dashboardDir, 'DashboardCustomerMatrix.vue'))).toBe(true)
  })

  test('summary cards format numbers, hide unauthorized entries, and emit navigation only', () => {
    const source = readSource('DashboardSummaryCard.vue')

    expect(source).toContain("import { canShowDashboardAction, formatDashboardNumber } from './dashboardNavigation'")
    expect(source).toContain('canShowDashboardAction')
    expect(source).toContain('v-if="canShow"')
    expect(source).toContain('defineEmits')
    expect(source).toContain("emit('select'")
    expect(source).toContain('trendText')
    expect(source).not.toContain('/api/')
    expect(source).not.toContain('marketApi')
  })

  test('todo and attention sections filter by permission and never call write APIs', () => {
    const todoSource = readSource('DashboardTodoCard.vue')
    const attentionSource = readSource('DashboardAttentionList.vue')
    const combined = `${todoSource}\n${attentionSource}`

    expect(todoSource).toContain('visibleItems')
    expect(todoSource).toContain('canShowDashboardAction')
    expect(todoSource).toContain("emit('select'")
    expect(attentionSource).toContain('dashboard-severity-danger')
    expect(attentionSource).toContain('dashboard-severity-warning')
    expect(attentionSource).toContain('dashboard-severity-info')
    expect(attentionSource).toContain('visibleItems')
    expect(attentionSource).toContain('canShowDashboardAction')
    expect(attentionSource).toContain("emit('select'")
    expect(combined).not.toMatch(/\b(post|put|delete|patch|submit|sync|allocate|dispatch|apply)\b/i)
    expect(combined).not.toContain('/api/')
  })

  test('customer matrix keeps only pre-ticket status columns, weakens zeroes, and emits query navigation', () => {
    const source = readSource('DashboardCustomerMatrix.vue')

    expect(source).toContain('rfqUnpriced')
    expect(source).toContain('rfqPriced')
    expect(source).toContain('rfqQuoted')
    expect(source).toContain('u9Ready')
    expect(source).toContain('totalFocus')
    expect(source).toContain('dashboard-matrix-value dashboard-matrix-button')
    expect(source).toContain('dashboard-matrix-value dashboard-matrix-total')
    expect(source).toContain('dashboard-number-muted')
    expect(source).toContain('dashboard-matrix-empty')
    expect(source).toContain('isZeroValue')
    expect(source).toContain("emit('rfq-select'")
    expect(source).toContain("emit('u9-select'")
    expect(source).toContain('text-overflow: ellipsis')
    expect(source).not.toContain('partiallyReleased')
    expect(source).not.toContain('released')
    expect(source).not.toContain('discarded')
    expect(source).not.toContain('部分已提单')
    expect(source).not.toContain('已生成提单')
    expect(source).not.toContain('废弃')
  })

  test('dashboard home delegates interaction rendering to components while keeping page-level navigation', () => {
    const source = readSource('DashboardHome.vue')

    expect(source).toContain("import DashboardSummaryCard from './DashboardSummaryCard.vue'")
    expect(source).toContain("import DashboardTodoCard from './DashboardTodoCard.vue'")
    expect(source).toContain("import DashboardAttentionList from './DashboardAttentionList.vue'")
    expect(source).toContain("import DashboardCustomerMatrix from './DashboardCustomerMatrix.vue'")
    expect(source).toContain('<DashboardSummaryCard')
    expect(source).toContain('<DashboardCustomerMatrix')
    expect(source).toContain('<DashboardTodoCard')
    expect(source).toContain('<DashboardAttentionList')
    expect(source).toContain('@rfq-select="goToOrders"')
    expect(source).toContain('@u9-select="goToU9Sync"')
    expect(source).toContain('@select="goToItem"')
    expect(source).not.toContain('v-for="todo in visibleTodoCards"')
    expect(source).not.toContain('v-for="item in visibleAttentionItems"')
  })

  test('dashboard mock U9 ready entry follows sales order sync query contract', () => {
    const source = readSource('dashboardMockData.js')

    expect(source).toContain("key: 'salesReleased'")
    expect(source).toContain("title: '销售已提单'")
    expect(source).toContain("target: { path: '/u9-sync', query: { materialReadyStatus: 'READY' } }")
    expect(source).not.toContain("key: 'salesQuoted'")
    expect(source).toContain("key: 'u9SyncReady'")
    expect(source).toContain("query: { materialReadyStatus: 'READY' }")
    expect(source).not.toContain("query: { status: '已提单' }")
  })
})
