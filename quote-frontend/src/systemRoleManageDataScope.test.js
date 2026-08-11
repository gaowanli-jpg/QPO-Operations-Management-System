import { describe, expect, test } from 'vitest'
import { readFileSync } from 'node:fs'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

const currentDir = dirname(fileURLToPath(import.meta.url))
const readSource = (...segments) => readFileSync(resolve(currentDir, ...segments), 'utf8')

const roleManage = readSource('components', 'SystemRoleManage.vue')

describe('QPO role management data scope guardrails', () => {
  test('presents the recommended QPO data scope vocabulary', () => {
    expect(roleManage).toContain("value: 'ALL'")
    expect(roleManage).toContain("value: 'CUSTOMER'")
    expect(roleManage).toContain("value: 'SELF'")
    expect(roleManage).toContain("value: 'NONE'")
    expect(roleManage).not.toContain("value: 'DEPT'")
    expect(roleManage).not.toContain("value: 'DEPT_AND_CHILD'")
    expect(roleManage).not.toContain("value: 'CUSTOM'")
  })

  test('shows customer configuration only for customer data scope', () => {
    expect(roleManage).toContain('isCustomerDataScope')
    expect(roleManage).toContain('v-if="isCustomerDataScope"')
    expect(roleManage).toContain('customerCodes')
    expect(roleManage).toContain(':value="customer.customerCode"')
  })

  test('saves customer data scope by U9 customer code with full option labels', () => {
    expect(roleManage).toContain('dataScopeForm.customerCodes')
    expect(roleManage).toContain('response?.customerCodes')
    expect(roleManage).toContain('customerCodes: [...formState.customerCodes]')
    expect(roleManage).not.toContain('customerNames: [...formState.customerNames]')
    expect(roleManage).toContain('formatCustomerOption(customer)')
  })

  test('removes department data scope configuration from role management', () => {
    expect(roleManage).not.toContain('isDepartmentCompatibilityScope')
    expect(roleManage).not.toContain('isCustomDepartmentScope')
    expect(roleManage).not.toContain('dataScopeDeptTreeRef')
    expect(roleManage).not.toContain('listDataScopeDepartments')
  })

  test('normalizes hidden data scope fields before saving', () => {
    expect(roleManage).toContain('buildDataScopePayload(dataScopeForm)')
    expect(roleManage).toContain("case 'CUSTOMER'")
    expect(roleManage).toContain('deptIds: []')
    expect(roleManage).toContain('customerCodes: []')
    expect(roleManage).toContain('default:')
  })
})
