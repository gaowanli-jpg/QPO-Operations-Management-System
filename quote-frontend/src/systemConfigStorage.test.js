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
const systemStoragePanel = readOptionalSource('components', 'system-config', 'SystemStorageConfigPanel.vue')

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
    expect(systemConfigManage).toContain('SystemStorageConfigPanel')
    expect(systemConfigManage).toContain('SystemIntegrationConfigPanel')
    expect(systemConfigManage).toContain('SystemInboundCallbackConfigPanel')
    expect(systemConfigManage).toContain('v-model="activeTab"')
    expect(systemConfigManage).toContain('文件存储')
    expect(systemConfigManage).toContain('出站接口')
    expect(systemConfigManage).toContain('入站接口')

    expect(systemStoragePanel).toContain('system-storage-config-panel')
    expect(systemStoragePanel).toContain('当前激活策略')
    expect(systemStoragePanel).toContain('配置列表')
    expect(systemStoragePanel).toContain('新增存储配置')
    expect(systemStoragePanel).toContain('编辑配置')
    expect(systemStoragePanel).toContain('测试连接')
    expect(systemStoragePanel).toContain('激活配置')

    expect(systemStoragePanel).toContain("v-permission=\"'system:config:storage:update'\"")
    expect(systemStoragePanel).toContain("v-permission=\"'system:config:storage:test'\"")
    expect(systemStoragePanel).toContain("v-permission=\"'system:config:storage:activate'\"")
    expect(systemStoragePanel).toContain('type="password"')
    expect(systemStoragePanel).toContain('show-password')
    expect(systemStoragePanel).toContain('******')

    expect(systemStoragePanel).toContain('usePendingAction')
    expect(systemStoragePanel).toContain('systemConfigActionKeys')
    expect(systemStoragePanel).toContain(':loading="isPending(systemConfigActionKeys.saveDialog)"')
    expect(systemStoragePanel).toContain(':disabled="isPending(systemConfigActionKeys.saveDialog)"')
    expect(systemStoragePanel).toContain('runAction(systemConfigActionKeys.saveDialog')
    expect(systemStoragePanel).toContain('runAction(systemConfigActionKeys.test(row.id)')
    expect(systemStoragePanel).toContain('runAction(systemConfigActionKeys.activate(row.id)')
    expect(systemStoragePanel).toContain('只影响之后上传的新文件')
  })
})
