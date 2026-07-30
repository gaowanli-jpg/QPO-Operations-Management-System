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

const systemConfigApi = readSource('services', 'systemConfigApi.js')
const systemConfigManage = readSource('components', 'SystemConfigManage.vue')
const integrationPanel = readOptionalSource('components', 'system-config', 'SystemIntegrationConfigPanel.vue')

describe('QPO system integration config frontend', () => {
  test('defines system integration config API service contract', () => {
    expect(systemConfigApi).toContain("export const listIntegrationConfigs = (params = {}) => request('/api/system/integration-configs', { params })")
    expect(systemConfigApi).toContain("export const getActiveIntegrationConfigs = () => request('/api/system/integration-configs/active')")
    expect(systemConfigApi).toContain('export const saveIntegrationConfig = (payload) =>')
    expect(systemConfigApi).toContain("request(id ? `/api/system/integration-configs/${id}` : '/api/system/integration-configs'")
    expect(systemConfigApi).toContain('export const testIntegrationConfig = (id) => request(`/api/system/integration-configs/${id}/test`, {')
    expect(systemConfigApi).toContain('export const activateIntegrationConfig = (id) => request(`/api/system/integration-configs/${id}/activate`, {')
    expect(systemConfigApi).toContain('export const disableIntegrationConfig = (id) => request(`/api/system/integration-configs/${id}/disable`, {')
  })

  test('splits system config page into storage and integration tabs', () => {
    expect(systemConfigManage).toContain('el-tabs')
    expect(systemConfigManage).toContain('name="storage"')
    expect(systemConfigManage).toContain('name="integration"')
    expect(systemConfigManage).toContain('<SystemStorageConfigPanel />')
    expect(systemConfigManage).toContain('<SystemIntegrationConfigPanel />')
  })

  test('renders U9 MES WMS integration table dialog and data center reserved card', () => {
    expect(integrationPanel).toContain('system-integration-config-panel')
    expect(integrationPanel).toContain('当前激活配置概览')
    expect(integrationPanel).toContain('系统对接配置列表')
    expect(integrationPanel).toContain('新增对接配置')
    expect(integrationPanel).toContain('编辑对接配置')
    expect(integrationPanel).toContain('U9')
    expect(integrationPanel).toContain('MES')
    expect(integrationPanel).toContain('WMS')
    expect(integrationPanel).toContain('数据中心')
    expect(integrationPanel).toContain('预留')
    expect(integrationPanel).toContain('当前版本未接入')
    expect(integrationPanel).toContain('authLoginPath')
    expect(integrationPanel).toContain('salesOrderCreatePath')
    expect(integrationPanel).toContain('materialPath')
    expect(integrationPanel).toContain('allocationPath')
    expect(integrationPanel).toContain('clientSecret')
    expect(integrationPanel).toContain('authToken')
    expect(integrationPanel).toContain('show-password')
    expect(integrationPanel).toContain('******')
  })

  test('protects integration writes with permissions and duplicate-click guards', () => {
    expect(integrationPanel).toContain("v-permission=\"'system:config:integration:update'\"")
    expect(integrationPanel).toContain("v-permission=\"'system:config:integration:test'\"")
    expect(integrationPanel).toContain("v-permission=\"'system:config:integration:activate'\"")
    expect(integrationPanel).toContain("saveDialog: 'system:config:integration:save-dialog'")
    expect(integrationPanel).toContain("test: (configId) => `system:config:integration:test:${configId || 'none'}`")
    expect(integrationPanel).toContain("activate: (configId) => `system:config:integration:activate:${configId || 'none'}`")
    expect(integrationPanel).toContain("disable: (configId) => `system:config:integration:disable:${configId || 'none'}`")
    expect(integrationPanel).toContain(':loading="isPending(integrationActionKeys.saveDialog)"')
    expect(integrationPanel).toContain(':disabled="isPending(integrationActionKeys.saveDialog)"')
    expect(integrationPanel).toContain(':loading="isPending(integrationActionKeys.test(row.id))"')
    expect(integrationPanel).toContain(':disabled="isIntegrationRowPending(row)"')
    expect(integrationPanel).toContain(':loading="isPending(integrationActionKeys.activate(row.id))"')
    expect(integrationPanel).toContain(':loading="isPending(integrationActionKeys.disable(row.id))"')
    expect(integrationPanel).toContain('runAction(integrationActionKeys.saveDialog')
    expect(integrationPanel).toContain('runAction(integrationActionKeys.test(row.id)')
    expect(integrationPanel).toContain('runAction(integrationActionKeys.activate(row.id)')
    expect(integrationPanel).toContain('runAction(integrationActionKeys.disable(row.id)')
  })
})
