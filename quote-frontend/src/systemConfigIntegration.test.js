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
const inboundPanel = readOptionalSource('components', 'system-config', 'SystemInboundCallbackConfigPanel.vue')

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

  test('splits system config page into storage outbound and inbound tabs', () => {
    expect(systemConfigManage).toContain('el-tabs')
    expect(systemConfigManage).toContain('name="storage"')
    expect(systemConfigManage).toContain('name="outbound"')
    expect(systemConfigManage).toContain('name="inbound"')
    expect(systemConfigManage).toContain('<SystemStorageConfigPanel />')
    expect(systemConfigManage).toContain('<SystemIntegrationConfigPanel />')
    expect(systemConfigManage).toContain('<SystemInboundCallbackConfigPanel />')
    expect(systemConfigManage).toContain('文件存储')
    expect(systemConfigManage).toContain('出站接口')
    expect(systemConfigManage).toContain('入站接口')
    expect(systemConfigManage).not.toContain('name="integration"')
  })

  test('renders U9 MES WMS fallback table and data center endpoint copy', () => {
    expect(integrationPanel).toContain('system-integration-config-panel')
    expect(integrationPanel).toContain('出站接口')
    expect(integrationPanel).toContain('当前出站配置概览')
    expect(integrationPanel).toContain('直连 fallback 配置列表')
    expect(integrationPanel).toContain('新增出站接口配置')
    expect(integrationPanel).toContain('编辑出站接口配置')
    expect(integrationPanel).toContain('U9')
    expect(integrationPanel).toContain('MES')
    expect(integrationPanel).toContain('WMS')
    expect(integrationPanel).toContain('数据中心连接配置')
    expect(integrationPanel).toContain('功能接口配置')
    expect(integrationPanel).toContain('MES_MATERIAL_RESOLVE')
    expect(integrationPanel).toContain('WMS_INVENTORY_ALLOCATE')
    expect(integrationPanel).toContain('U9_SALES_ORDER_SYNC')
    expect(integrationPanel).toContain('MES_PRODUCTION_DISPATCH')
    expect(integrationPanel).toContain('authLoginPath')
    expect(integrationPanel).toContain('salesOrderCreatePath')
    expect(integrationPanel).toContain('materialPath')
    expect(integrationPanel).toContain('allocationPath')
    expect(integrationPanel).toContain('clientSecret')
    expect(integrationPanel).toContain('authToken')
    expect(integrationPanel).toContain('show-password')
    expect(integrationPanel).toContain('******')
    expect(integrationPanel).not.toContain('当前版本未接入')
    expect(integrationPanel).not.toContain('MES 回调 token')
  })

  test('renders inbound callback panel for MES material callback', () => {
    expect(inboundPanel).toContain('system-inbound-callback-config-panel')
    expect(inboundPanel).toContain('入站接口')
    expect(inboundPanel).toContain('MES 物料号回调')
    expect(inboundPanel).toContain('/api/integrations/mes/material-callback')
    expect(inboundPanel).toContain('callbackUrl')
    expect(inboundPanel).toContain('configSource')
    expect(inboundPanel).toContain('tokenConfigured')
    expect(inboundPanel).toContain('保存配置')
    expect(inboundPanel).toContain('生成 token')
    expect(inboundPanel).toContain('复制回调地址')
    expect(inboundPanel).not.toContain('后续阶段接入')
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
