import { describe, expect, test } from 'vitest'
import { readFileSync } from 'node:fs'
import { fileURLToPath } from 'node:url'
import { dirname, resolve } from 'node:path'

const currentDir = dirname(fileURLToPath(import.meta.url))
const component = readFileSync(resolve(currentDir, 'components', 'PartSearchManage.vue'), 'utf8')
const router = readFileSync(resolve(currentDir, 'router', 'index.js'), 'utf8')
const appVue = readFileSync(resolve(currentDir, 'App.vue'), 'utf8')

describe('PartSearchManage', () => {
  test('registers an independent read-only part search menu route', () => {
    expect(router).toContain("const PartSearchManage = () => import('../components/PartSearchManage.vue')")
    expect(router).toContain("{ path: '/part-search', component: PartSearchManage, meta: { permission: 'market:part-search:view' } }")
    expect(appVue).toContain("'/part-search': { title: '零件查询', permission: 'market:part-search:view'")
    expect(appVue).toContain("paths: ['/orders', '/sales-orders', '/part-search', '/delivered-items', '/production-items', '/locked-items']")
  })

  test('shows rfq and sales part tabs with exact part number search and optional version', () => {
    expect(component).toContain('<h1 class="erp-page-title">零件查询</h1>')
    expect(component).toContain('<el-tab-pane label="询价零件" name="rfq"')
    expect(component).toContain('<el-tab-pane label="销售零件" name="sales"')
    expect(component).toContain('v-model="filters.partNo"')
    expect(component).toContain('placeholder="图号（必填，精确查询）"')
    expect(component).toContain('v-model="filters.version"')
    expect(component).toContain('placeholder="版本（可选，精确查询）"')
  })

  test('renders the required base columns and read-only detail drawer', () => {
    for (const column of [
      'prop="partNo" label="图号"',
      'prop="version" label="版本"',
      'label="未税单价"',
      'label="含税单价"',
      'prop="orderNo" label="所属订单号"',
      'prop="itemStatus" label="状态"'
    ]) {
      expect(component).toContain(column)
    }
    expect(component).toContain('@click="openDetail(scope.row)"')
    expect(component).toContain('<el-drawer v-model="detailVisible" title="零件详情"')
    expect(component).not.toContain('@click="save')
    expect(component).not.toContain('v-permission="\'market:order:update-item\'"')
  })
})
