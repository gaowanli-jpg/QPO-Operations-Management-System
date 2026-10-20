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

const systemConfigManage = readSource('components', 'SystemConfigManage.vue')
const ssoApi = readOptionalSource('services', 'systemDataCenterSsoConfigApi.js')
const ssoPanel = readOptionalSource('components', 'system-config', 'SystemDataCenterSsoConfigPanel.vue')

describe('system data center SSO config frontend', () => {
  test('defines a dedicated data center SSO config API service', () => {
    expect(ssoApi).toContain("export const listDataCenterSsoConfigs = () => request('/api/system/data-center-sso-configs')")
    expect(ssoApi).toContain('export const saveDataCenterSsoConfig = (payload) =>')
    expect(ssoApi).toContain("request(id ? `/api/system/data-center-sso-configs/${id}` : '/api/system/data-center-sso-configs'")
    expect(ssoApi).toContain('export const testDataCenterSsoConfig = (id) => request(`/api/system/data-center-sso-configs/${id}/test`, {')
    expect(ssoApi).toContain('export const activateDataCenterSsoConfig = (id) => request(`/api/system/data-center-sso-configs/${id}/activate`, {')
    expect(ssoApi).toContain('export const disableDataCenterSsoConfig = (id) => request(`/api/system/data-center-sso-configs/${id}/disable`, {')
  })

  test('adds a data center login tab to the existing system config page', () => {
    expect(systemConfigManage).toContain("import SystemDataCenterSsoConfigPanel from './system-config/SystemDataCenterSsoConfigPanel.vue'")
    expect(systemConfigManage).toContain('name="dataCenterSso"')
    expect(systemConfigManage).toContain('数据中心登录')
    expect(systemConfigManage).toContain('<SystemDataCenterSsoConfigPanel />')
  })

  test('renders SSO config maintenance with permissions, pending guards, and no plaintext secret echo', () => {
    expect(ssoPanel).toContain('system-data-center-sso-config-panel')
    expect(ssoPanel).toContain('数据中心登录')
    expect(ssoPanel).toContain('listDataCenterSsoConfigs')
    expect(ssoPanel).toContain('saveDataCenterSsoConfig')
    expect(ssoPanel).toContain('testDataCenterSsoConfig')
    expect(ssoPanel).toContain('activateDataCenterSsoConfig')
    expect(ssoPanel).toContain('disableDataCenterSsoConfig')
    expect(ssoPanel).toContain("v-permission=\"'system:config:sso:update'\"")
    expect(ssoPanel).toContain("v-permission=\"'system:config:sso:test'\"")
    expect(ssoPanel).toContain("v-permission=\"'system:config:sso:activate'\"")
    expect(ssoPanel).toContain("saveDialog: 'system:config:sso:save-dialog'")
    expect(ssoPanel).toContain('test: (configId) => `system:config:sso:test:${configId || \'none\'}`')
    expect(ssoPanel).toContain('activate: (configId) => `system:config:sso:activate:${configId || \'none\'}`')
    expect(ssoPanel).toContain('disable: (configId) => `system:config:sso:disable:${configId || \'none\'}`')
    expect(ssoPanel).toContain(':loading="isPending(dataCenterSsoActionKeys.saveDialog)"')
    expect(ssoPanel).toContain(':loading="isPending(dataCenterSsoActionKeys.test(row.id))"')
    expect(ssoPanel).toContain(':loading="isPending(dataCenterSsoActionKeys.activate(row.id))"')
    expect(ssoPanel).toContain(':loading="isPending(dataCenterSsoActionKeys.disable(row.id))"')
    expect(ssoPanel).toContain('runAction(dataCenterSsoActionKeys.saveDialog')
    expect(ssoPanel).toContain('runAction(dataCenterSsoActionKeys.test(row.id)')
    expect(ssoPanel).toContain('runAction(dataCenterSsoActionKeys.activate(row.id)')
    expect(ssoPanel).toContain('runAction(dataCenterSsoActionKeys.disable(row.id)')
    expect(ssoPanel).toContain('sharedSecretConfigured')
    expect(ssoPanel).toContain('type="password"')
    expect(ssoPanel).toContain('show-password')
    expect(ssoPanel).toContain('secretOrUndefined')
    expect(ssoPanel).toContain("sharedSecret: secretOrUndefined(form.sharedSecret)")
    expect(ssoPanel).toContain('留空或 ****** 沿用原 SSO 密钥')
    expect(ssoPanel).toContain('******')
    expect(ssoPanel).not.toContain('localStorage')
    expect(ssoPanel).not.toContain('sessionStorage')
  })
})
