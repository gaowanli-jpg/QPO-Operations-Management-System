import { describe, expect, test } from 'vitest'
import { readFileSync } from 'node:fs'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

const currentDir = dirname(fileURLToPath(import.meta.url))
const repoRoot = resolve(currentDir, '..', '..')
const readRepo = (...segments) => readFileSync(resolve(repoRoot, ...segments), 'utf8').replace(/\r\n/g, '\n')
const readSource = (...segments) => readFileSync(resolve(currentDir, ...segments), 'utf8')

const integrationPanel = readSource('components', 'system-config', 'SystemIntegrationConfigPanel.vue')
const inboundPanel = readSource('components', 'system-config', 'SystemInboundCallbackConfigPanel.vue')
const systemConfigDoc = readRepo('docs', 'QPO系统配置', '05-数据中心统一对接开发文档.md')
const permissionAdminDoc = readRepo('docs', 'QPO权限用户体系', '03-权限体系部署与管理员使用说明.md')
const mesProductionDoc = readRepo('docs', 'QPO-MES-WMS对接', '09-提供给MES的待生产下发接口对接说明.md')
const salesOrderU9Doc = readRepo('docs', 'QPO报价状态流转', '16-销售订单U9同步模块开发文档.md')
const envExample = readRepo('erp.env.example')

describe('data center documentation frontend characterization', () => {
  test('outbound and inbound pages expose data center as the integration boundary', () => {
    expect(integrationPanel).toContain('数据中心连接配置')
    expect(integrationPanel).toContain('功能接口配置')
    expect(integrationPanel).toContain('统一配置数据中心连接，并按业务功能维护 MES / WMS / U9 的数据中心接口 path。')
    expect(integrationPanel).toContain('MES_MATERIAL_RESOLVE')
    expect(integrationPanel).toContain('WMS_INVENTORY_ALLOCATE')
    expect(integrationPanel).toContain('U9_SALES_ORDER_SYNC')
    expect(integrationPanel).toContain('MES_PRODUCTION_DISPATCH')
    expect(integrationPanel).toContain('直连 fallback 兼容区')
    expect(integrationPanel).not.toContain('当前版本未接入')
    expect(inboundPanel).toContain('调用方：数据中心')
    expect(inboundPanel).toContain('业务来源：MES')
    expect(inboundPanel).toContain('真实 token 需单独通过受控渠道交付数据中心')
  })

  test('deployment and interface docs describe data center routing without real secrets', () => {
    expect(systemConfigDoc).toContain('QPO 出站调用统一走数据中心')
    expect(systemConfigDoc).toContain('数据中心可使用 QPO 入站接口文档和 QPO 管理员提供的 token 调用 QPO')
    expect(permissionAdminDoc).toContain('QPO 出站调用默认走数据中心')
    expect(permissionAdminDoc).toContain('ERP_DATA_CENTER_TOKEN_URL')
    expect(permissionAdminDoc).toContain('ERP_DATA_CENTER_CLIENT_SECRET')
    expect(mesProductionDoc).toContain('提供给数据中心的待生产下发接口对接说明')
    expect(mesProductionDoc).toContain('MES_PRODUCTION_DISPATCH')
    expect(salesOrderU9Doc).toContain('U9_SALES_ORDER_SYNC')
    expect(salesOrderU9Doc).toContain('QPO -> 数据中心 -> U9')
    expect(envExample).toContain('ERP_DATA_CENTER_CLIENT_SECRET=')
    expect(envExample.split('\n').find((line) => line.startsWith('ERP_DATA_CENTER_CLIENT_SECRET='))).toBe('ERP_DATA_CENTER_CLIENT_SECRET=')

    const safeArtifacts = [systemConfigDoc, permissionAdminDoc, mesProductionDoc, salesOrderU9Doc, envExample]
    const bearerJwtPrefix = `Authorization: Bearer ${'eyJ'}`
    const accessTokenPrefix = `access_token=${'eyJ'}`
    const populatedClientSecret = `ERP_DATA_CENTER_CLIENT_SECRET=${'X'}`
    for (const content of safeArtifacts) {
      expect(content).not.toContain(bearerJwtPrefix)
      expect(content).not.toContain(accessTokenPrefix)
      expect(content).not.toContain(populatedClientSecret)
      expect(content).not.toContain('QPO_INBOUND_CALLBACK_TOKEN=')
      expect(content).not.toMatch(/ERP_DATA_CENTER_CLIENT_SECRET=[A-Za-z0-9+/=_-]+/)
      expect(content).not.toMatch(/client_secret=[A-Za-z0-9+/=_-]{12,}/)
    }
  })
})
