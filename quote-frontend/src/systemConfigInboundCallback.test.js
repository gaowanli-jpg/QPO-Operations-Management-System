import { describe, expect, test } from 'vitest'
import { readFileSync } from 'node:fs'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

const currentDir = dirname(fileURLToPath(import.meta.url))
const readSource = (...segments) => readFileSync(resolve(currentDir, ...segments), 'utf8')

const systemConfigApi = readSource('services', 'systemConfigApi.js')
const inboundPanel = readSource('components', 'system-config', 'SystemInboundCallbackConfigPanel.vue')
const outboundPanel = readSource('components', 'system-config', 'SystemIntegrationConfigPanel.vue')

describe('system inbound callback config frontend stage 6', () => {
  test('defines inbound callback config API methods', () => {
    expect(systemConfigApi).toContain("export const listInboundCallbackConfigs = () => request('/api/system/inbound-callback-configs')")
    expect(systemConfigApi).toContain("export const getMesMaterialCallbackConfig = () => request('/api/system/inbound-callback-configs/mes-material-callback')")
    expect(systemConfigApi).toContain('export const saveMesMaterialCallbackConfig = (payload) =>')
    expect(systemConfigApi).toContain("request('/api/system/inbound-callback-configs/mes-material-callback', {")
    expect(systemConfigApi).toContain("method: 'PUT'")
    expect(systemConfigApi).toContain("export const rotateMesMaterialCallbackToken = () => request('/api/system/inbound-callback-configs/mes-material-callback/token', {")
    expect(systemConfigApi).toContain("export const validateMesMaterialCallbackConfig = () => request('/api/system/inbound-callback-configs/mes-material-callback/validate', {")
  })

  test('renders MES callback card without outbound U9 or WMS forms', () => {
    expect(inboundPanel).toContain('system-inbound-callback-config-panel')
    expect(inboundPanel).toContain('MES 物料号回调')
    expect(inboundPanel).toContain('callbackUrl')
    expect(inboundPanel).toContain('configSource')
    expect(inboundPanel).toContain('tokenConfigured')
    expect(inboundPanel).toContain('tokenUpdatedAt')
    expect(inboundPanel).toContain('publicBaseUrl')
    expect(inboundPanel).toContain('tokenEnabled')
    expect(inboundPanel).toContain('enabled')
    expect(inboundPanel).toContain('remark')
    expect(inboundPanel).not.toContain('新增 U9 配置')
    expect(inboundPanel).not.toContain('新增 WMS 配置')
    expect(inboundPanel).not.toContain('clientSecret')
    expect(inboundPanel).not.toContain('allocationPath')
    expect(outboundPanel).not.toContain('MES 回调 token')
  })

  test('shows data center as caller and MES as business source', () => {
    expect(inboundPanel).toContain('调用方：数据中心')
    expect(inboundPanel).toContain('业务来源：MES')
    expect(inboundPanel).toContain('数据中心后续异步调用 QPO 写回 MES 物料号')
    expect(inboundPanel).toContain('回调 URL 和 token 由 QPO 管理员在入站接口页提供给数据中心')
    expect(inboundPanel).toContain('真实 token 需单独通过受控渠道交付数据中心')
    expect(inboundPanel).toContain('token 只展示一次')
    expect(inboundPanel).not.toContain('提供给 MES')
    expect(inboundPanel).not.toContain('交付 MES')
    expect(outboundPanel).not.toContain('QPO_INBOUND_CALLBACK_TOKEN')
    expect(outboundPanel).not.toContain('MES 回调 token')
  })

  test('wires edit validate token rotate and copy actions with permissions', () => {
    expect(inboundPanel).toContain("v-permission=\"'system:config:inbound:update'\"")
    expect(inboundPanel).toContain("v-permission=\"'system:config:inbound:token-rotate'\"")
    expect(inboundPanel).toContain('保存配置')
    expect(inboundPanel).toContain('校验配置')
    expect(inboundPanel).toContain('生成 token')
    expect(inboundPanel).toContain('轮换 token')
    expect(inboundPanel).toContain('复制回调地址')
    expect(inboundPanel).toContain('getMesMaterialCallbackConfig')
    expect(inboundPanel).toContain('saveMesMaterialCallbackConfig')
    expect(inboundPanel).toContain('rotateMesMaterialCallbackToken')
    expect(inboundPanel).toContain('validateMesMaterialCallbackConfig')
    expect(inboundPanel).toContain('navigator.clipboard.writeText')
    expect(inboundPanel).toContain('ElMessageBox.confirm')
    expect(inboundPanel).toContain("saveConfig: 'system:config:inbound:save-config'")
    expect(inboundPanel).toContain("validateConfig: 'system:config:inbound:validate-config'")
    expect(inboundPanel).toContain("rotateToken: 'system:config:inbound:rotate-token'")
    expect(inboundPanel).toContain(':loading="isPending(inboundActionKeys.saveConfig)"')
    expect(inboundPanel).toContain(':loading="isPending(inboundActionKeys.validateConfig)"')
    expect(inboundPanel).toContain(':loading="isPending(inboundActionKeys.rotateToken)"')
  })

  test('shows generated token only in one-time dialog and never persists plaintext', () => {
    expect(inboundPanel).toContain('tokenDialogVisible')
    expect(inboundPanel).toContain('plainToken')
    expect(inboundPanel).toContain('token 只展示一次')
    expect(inboundPanel).toContain('关闭后无法再次查看')
    expect(inboundPanel).not.toContain('localStorage')
    expect(inboundPanel).not.toContain('sessionStorage')
  })
})
