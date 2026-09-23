import { describe, expect, test } from 'vitest'
import { readFileSync } from 'node:fs'
import { fileURLToPath } from 'node:url'
import { dirname, resolve } from 'node:path'

const currentDir = dirname(fileURLToPath(import.meta.url))
const component = readFileSync(resolve(currentDir, 'components', 'LinkedChangeManage.vue'), 'utf8')
const api = readFileSync(resolve(currentDir, 'services', 'linkedChangeApi.js'), 'utf8')
const router = readFileSync(resolve(currentDir, 'router', 'index.js'), 'utf8')
const app = readFileSync(resolve(currentDir, 'App.vue'), 'utf8')

describe('linked change notice workbench', () => {
  test('is registered before sales change in the change group', () => {
    expect(router).toContain("const LinkedChangeManage = () => import('../components/LinkedChangeManage.vue')")
    expect(router.indexOf("path: '/linked-changes'")).toBeLessThan(router.indexOf("path: '/sales-order-change'"))
    expect(app).toContain("'/linked-changes': { title: '关联变更', permission: 'change:linked-change:view'")
    expect(app).toContain("paths: ['/linked-changes', '/sales-order-change']")
  })

  test('renders common and advanced filters without exposing technical status filters', () => {
    expect(component).toContain('CustomerPickerDialog')
    expect(component).toContain("customer-scope=\"linked-change\"")
    expect(component).toContain("change:linked-change:customer-select")
    expect(component).toContain('销售订单号')
    expect(component).toContain('图号')
    expect(component).toContain('版本')
    expect(component).toContain('通知状态')
    expect(component).toContain('下游影响')
    expect(component).toContain('高级筛选')
    expect(component).toContain('收起筛选')
    expect(component).toContain('客户编码')
    expect(component).toContain('客户订单号')
    expect(component).toContain('内部项目号')
    expect(component).toContain('客户/订单/图号/版本')
    expect(component).toContain('触发来源')
    expect(component).toContain('start-placeholder="开始日期"')
    expect(component).toContain('WMS 锁库')
    expect(component).toContain('MES 生产')
    expect(component).toContain('锁库库存工单')
    expect(component).not.toContain('WMS状态')
    expect(component).not.toContain('MES生产状态')
    expect(component).not.toContain('库存工单状态')
    expect(component).toContain('关联废弃人')
    expect(component).toContain('标记已处理')
    expect(component).toContain('关联变更详情')
    expect(component).toContain("change:linked-change:handle")
    expect(component).toContain('v-model:current-page="pagination.page"')
    expect(component).toContain('pagination.total')
    expect(component).not.toContain('取消 WMS')
    expect(component).not.toContain('重试 MES')
  })

  test('uses the independent backend paths and keeps reads paged', () => {
    expect(api).toContain("const BASE = '/api/change/linked-changes'")
    expect(api).toContain('listLinkedChanges')
    expect(api).toContain('getLinkedChange')
    expect(api).toContain('handleLinkedChange')
    expect(api).toContain('reopenLinkedChange')
    expect(readFileSync(resolve(currentDir, 'services', 'orderApi.js'), 'utf8')).toContain("'linked-change': '/api/change/linked-changes/customers'")
    expect(component).toContain('page: pagination.page')
    expect(component).toContain('pageSize: pagination.pageSize')
    expect(component).not.toContain('clientPagination')
  })
})
