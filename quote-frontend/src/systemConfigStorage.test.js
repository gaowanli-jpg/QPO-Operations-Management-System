import { describe, expect, test } from 'vitest'
import { existsSync, readFileSync } from 'node:fs'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

const currentDir = dirname(fileURLToPath(import.meta.url))
const readSource = (...segments) => readFileSync(resolve(currentDir, ...segments), 'utf8')
const readOptionalSource = (...segments) => {
  const file = resolve(currentDir, ...segments)
  return existsSync(file) ? readFileSync(file, 'utf8') : ''
}

const appVue = readSource('App.vue')
const routerSource = readSource('router', 'index.js')
const systemConfigApi = readOptionalSource('services', 'systemConfigApi.js')
const systemConfigManage = readOptionalSource('components', 'SystemConfigManage.vue')

describe('QPO system config storage frontend', () => {
  test('adds system config route and sidebar menu under system permissions', () => {
    expect(routerSource).toContain("const SystemConfigManage = () => import('../components/SystemConfigManage.vue')")
    expect(routerSource).toContain("{ path: '/system-config', component: SystemConfigManage, meta: { permission: 'system:config:view' } }")

    expect(appVue).toContain("'/system-config': { title: '系统配置', permission: 'system:config:view'")
    expect(appVue).toContain("{ key: 'system', title: '系统', paths: ['/users', '/roles', '/organization', '/customers', '/audit-logs', '/system-config'] }")
    expect(appVue).toContain("'/system-config': {")
    expect(appVue).toContain("title: '系统配置'")
  })

  test('defines storage config API service contract', () => {
    expect(systemConfigApi).toContain("export const listStorageConfigs = () => request('/api/system/storage-configs')")
    expect(systemConfigApi).toContain("export const getActiveStorageConfig = () => request('/api/system/storage-configs/active')")
    expect(systemConfigApi).toContain('export const saveStorageConfig = (payload) =>')
    expect(systemConfigApi).toContain("request(id ? `/api/system/storage-configs/${id}` : '/api/system/storage-configs'")
    expect(systemConfigApi).toContain('export const testStorageConfig = (id) => request(`/api/system/storage-configs/${id}/test`, {')
    expect(systemConfigApi).toContain("method: 'POST'")
    expect(systemConfigApi).toContain('export const activateStorageConfig = (id) => request(`/api/system/storage-configs/${id}/activate`, {')
    expect(systemConfigApi).toContain("method: 'PUT'")
    expect(systemConfigApi).toContain('export const disableStorageConfig = (id) => request(`/api/system/storage-configs/${id}/disable`, {')
  })

  test('renders storage config management page with permissions and duplicate-click guards', () => {
    expect(systemConfigManage).toContain('erp-page system-config-page')
    expect(systemConfigManage).toContain('<h1 class="erp-page-title">系统配置</h1>')
    expect(systemConfigManage).toContain('文件存储')
    expect(systemConfigManage).toContain('当前激活策略')
    expect(systemConfigManage).toContain('配置列表')
    expect(systemConfigManage).toContain('新增存储配置')
    expect(systemConfigManage).toContain('编辑配置')
    expect(systemConfigManage).toContain('测试连接')
    expect(systemConfigManage).toContain('激活配置')

    expect(systemConfigManage).toContain("v-permission=\"'system:config:storage:update'\"")
    expect(systemConfigManage).toContain("v-permission=\"'system:config:storage:test'\"")
    expect(systemConfigManage).toContain("v-permission=\"'system:config:storage:activate'\"")
    expect(systemConfigManage).toContain('type="password"')
    expect(systemConfigManage).toContain('show-password')
    expect(systemConfigManage).toContain('******')

    expect(systemConfigManage).toContain('usePendingAction')
    expect(systemConfigManage).toContain('systemConfigActionKeys')
    expect(systemConfigManage).toContain(':loading="isPending(systemConfigActionKeys.saveDialog)"')
    expect(systemConfigManage).toContain(':disabled="isPending(systemConfigActionKeys.saveDialog)"')
    expect(systemConfigManage).toContain('runAction(systemConfigActionKeys.saveDialog')
    expect(systemConfigManage).toContain('runAction(systemConfigActionKeys.test(row.id)')
    expect(systemConfigManage).toContain('runAction(systemConfigActionKeys.activate(row.id)')
    expect(systemConfigManage).toContain('只影响之后上传的新文件')
  })
})
