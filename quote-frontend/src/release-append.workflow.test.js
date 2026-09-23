import { describe, expect, test } from 'vitest'
import { readFileSync } from 'node:fs'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

const currentDir = dirname(fileURLToPath(import.meta.url))
const readSource = (...segments) => readFileSync(resolve(currentDir, ...segments), 'utf8')

describe('release append module', () => {
  test('adds release append below release in the market sidebar with dedicated permission', () => {
    const app = readSource('App.vue')

    expect(app).toContain("paths: ['/market', '/quote-process', '/release', '/release-append', '/u9-sync'")
    expect(app).toContain("'/release-append': { title: '追加提单', permission: 'market:release-append:view'")
    expect(app.indexOf("'/release'")).toBeLessThan(app.indexOf("'/release-append'"))
    expect(app.indexOf("'/release-append'")).toBeLessThan(app.indexOf("'/u9-sync'"))
  })

  test('registers route guarded by release append view permission', () => {
    const router = readSource('router', 'index.js')

    expect(router).toContain("const ReleaseAppendManage = () => import('../components/ReleaseAppendManage.vue')")
    expect(router).toContain("{ path: '/release-append', component: ReleaseAppendManage, meta: { permission: 'market:release-append:view' } }")
  })

  test('exposes customer order selection and append upload APIs under release append namespace', () => {
    const orderApi = readSource('services', 'orderApi.js')
    const marketApi = readSource('services', 'marketApi.js')

    expect(orderApi).toContain("'release-append': '/api/market/release-append/customers'")
    expect(marketApi).toContain("'release-append': '/api/market/release-append/sales-orders'")
    expect(marketApi).toContain('export const appendSalesOrderRelease')
    expect(marketApi).toContain("multipartPost(`/api/market/release-append/sales-orders/${salesOrderId}/items`, formData)")
  })

  test('page follows customer target order excel drawing append workflow without order default matrix', () => {
    const component = readSource('components', 'ReleaseAppendManage.vue')

    expect(component).toContain('选择客户')
    expect(component).toContain('选择目标销售订单')
    expect(component).toContain('上传销售订单 Excel')
    expect(component).toContain('上传关联图纸')
    expect(component).toContain('上传订单附件文件夹')
    expect(component).toContain('v-model="drawingFolderFiles"')
    expect(component).toContain('v-model:folder-name="drawingFolderName"')
    expect(component).toContain("formData.append('drawingFolderFiles', file)")
    expect(component).toContain("formData.append('drawingFolderName'")
    expect(component).toContain('追加销售提单')
    expect(component).toContain('customer-scope="release-append"')
    expect(component).toContain('order-scope="release-append"')
    expect(component).toContain('market:release-append:create')
    expect(component).toContain('appendSalesOrderRelease')
    expect(component).not.toContain('sales-order-default-grid')
    expect(component).not.toContain('订单默认信息')
    expect(component).not.toContain('同步 U9')
  })

  test('uses the new order import step-card hierarchy and spacing', () => {
    const component = readSource('components', 'ReleaseAppendManage.vue')

    expect(component.match(/class="import-step append-step/g)).toHaveLength(4)
    expect(component).toContain('步骤 1：客户基础信息')
    expect(component).toContain('步骤 2：选择目标销售订单')
    expect(component).toContain('步骤 3：上传销售订单（Excel）')
    expect(component).toContain('步骤 4：上传关联图纸（PDF） / 上传订单附件文件夹')
    expect(component).toContain('class="step-panel upload-panel"')
    expect(component).toContain(':md="14" :lg="10" :xl="9" class="customer-input-col"')
    expect(component).toContain('.section-title {')
    expect(component).toContain('background: var(--erp-surface-soft)')
    expect(component).toContain('border-radius: var(--erp-radius-full)')
  })
})
