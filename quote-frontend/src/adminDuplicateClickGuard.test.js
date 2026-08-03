import { describe, expect, test, vi } from 'vitest'
import { existsSync, readFileSync } from 'node:fs'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'
import { usePendingAction } from './composables/usePendingAction'

const currentDir = dirname(fileURLToPath(import.meta.url))
const readSource = (...segments) => readFileSync(resolve(currentDir, ...segments), 'utf8')
const readOptionalSource = (...segments) => {
  const file = resolve(currentDir, ...segments)
  return existsSync(file) ? readFileSync(file, 'utf8') : ''
}

const sources = {
  users: readSource('components', 'UserManage.vue'),
  roles: readSource('components', 'SystemRoleManage.vue'),
  org: readSource('components', 'SystemOrgManage.vue'),
  dict: readSource('components', 'MaterialProcessManage.vue'),
  template: readSource('components', 'QuoteTemplateManage.vue'),
  audit: readSource('components', 'AuditLogManage.vue'),
  systemConfig: [
    readSource('components', 'SystemConfigManage.vue'),
    readOptionalSource('components', 'system-config', 'SystemStorageConfigPanel.vue'),
    readOptionalSource('components', 'system-config', 'SystemIntegrationConfigPanel.vue')
  ].join('\n')
}

const blockBetween = (source, start, end) => {
  const startIndex = source.indexOf(start)
  const endIndex = source.indexOf(end, startIndex)
  expect(startIndex, `Expected source to include start marker ${start}`).toBeGreaterThanOrEqual(0)
  expect(endIndex, `Expected source to include end marker ${end}`).toBeGreaterThan(startIndex)
  return source.slice(startIndex, endIndex)
}

const deferred = () => {
  let resolve
  const promise = new Promise((done) => {
    resolve = done
  })
  return { promise, resolve }
}

describe('admin duplicate click guards', () => {
  test('system management pages register pending action keys for dialog, drawer, row and button writes', () => {
    for (const [key, source] of Object.entries(sources)) {
      if (key === 'systemConfig') {
        expect(source).toContain("import { usePendingAction } from '../../composables/usePendingAction'")
      } else {
        expect(source).toContain("import { usePendingAction } from '../composables/usePendingAction'")
      }
      expect(source).toContain('const { isPending, runAction } = usePendingAction()')
    }

    expect(sources.users).toContain("saveDialog: 'system:user:save-dialog'")
    expect(sources.users).toContain("resetPasswordDialog: (userId) => `system:user:reset-password:${userId || 'none'}`")
    expect(sources.users).toContain("status: (userId) => `system:user:status:${userId || 'none'}`")
    expect(sources.users).toContain("delete: (userId) => `system:user:delete:${userId || 'none'}`")

    expect(sources.roles).toContain("saveDialog: 'system:role:save-dialog'")
    expect(sources.roles).toContain("permissionsDrawer: (roleId) => `system:role:permissions-drawer:${roleId || 'none'}`")
    expect(sources.roles).toContain("dataScopeDrawer: (roleId) => `system:role:data-scope-drawer:${roleId || 'none'}`")
    expect(sources.roles).toContain("status: (roleId) => `system:role:status:${roleId || 'none'}`")
    expect(sources.roles).toContain("delete: (roleId) => `system:role:delete:${roleId || 'none'}`")

    expect(sources.org).toContain("saveDepartmentDialog: 'system:org:department-save-dialog'")
    expect(sources.org).toContain("savePostDialog: 'system:org:post-save-dialog'")
    expect(sources.org).toContain("departmentStatus: (deptId) => `system:org:department-status:${deptId || 'none'}`")
    expect(sources.org).toContain("postDelete: (postId) => `system:org:post-delete:${postId || 'none'}`")

    expect(sources.dict).toContain("saveMaterialDialog: 'engineer:dict:material-save-dialog'")
    expect(sources.dict).toContain("saveProcessDialog: 'engineer:dict:process-save-dialog'")
    expect(sources.dict).toContain("saveMarkupFeeDialog: 'engineer:dict:markup-fee-save-dialog'")
    expect(sources.dict).toContain("deleteExtraFee: (feeId) => `engineer:dict:extra-fee-delete:${feeId || 'none'}`")

    expect(sources.template).toContain("upload: 'market:template:upload'")
    expect(sources.template).toContain("saveConfig: 'market:template:config-save'")
    expect(sources.audit).toContain("exportLogs: 'system:audit:export'")
    expect(sources.audit).toContain("saveRetention: 'system:audit:retention-save'")
    expect(sources.audit).toContain("cleanupRetention: 'system:audit:retention-cleanup'")

    expect(sources.systemConfig).toContain("saveDialog: 'system:config:storage:save-dialog'")
    expect(sources.systemConfig).toContain("test: (configId) => `system:config:storage:test:${configId || 'none'}`")
    expect(sources.systemConfig).toContain("activate: (configId) => `system:config:storage:activate:${configId || 'none'}`")
    expect(sources.systemConfig).toContain("disable: (configId) => `system:config:storage:disable:${configId || 'none'}`")
    expect(sources.systemConfig).toContain("saveDialog: 'system:config:integration:save-dialog'")
    expect(sources.systemConfig).toContain("test: (configId) => `system:config:integration:test:${configId || 'none'}`")
    expect(sources.systemConfig).toContain("activate: (configId) => `system:config:integration:activate:${configId || 'none'}`")
    expect(sources.systemConfig).toContain("disable: (configId) => `system:config:integration:disable:${configId || 'none'}`")
  })

  test('user management uses dialog and row pending states for save, reset, status and delete', () => {
    const saveBlock = blockBetween(sources.users, 'const saveUser = async () =>', 'const openPassword = (row) =>')
    const resetBlock = blockBetween(sources.users, 'const resetPassword = async () =>', 'const toggleEnabled = async (row) =>')
    const statusBlock = blockBetween(sources.users, 'const toggleEnabled = async (row) =>', 'const deleteUser = async (row) =>')
    const deleteBlock = blockBetween(sources.users, 'const deleteUser = async (row) =>', 'const isLocked = (row) =>')

    expect(sources.users).toContain(':loading="isPending(userActionKeys.saveDialog)"')
    expect(sources.users).toContain(':disabled="isPending(userActionKeys.saveDialog)"')
    expect(sources.users).toContain(':loading="resetPasswordPending"')
    expect(sources.users).toContain(':disabled="resetPasswordPending"')
    expect(sources.users).toContain(':loading="isUserStatusPending(row)"')
    expect(sources.users).toContain(':disabled="isUserRowActionPending(row)"')
    expect(sources.users).toContain(':loading="isUserDeletePending(row)"')
    expect(sources.users).toContain(':disabled="isProtectedUser(row) || isUserRowActionPending(row)"')
    expect(saveBlock).toContain('return runAction(userActionKeys.saveDialog, async () =>')
    expect(saveBlock).toContain('await saveUserRequest')
    expect(resetBlock).toContain('return runAction(userActionKeys.resetPasswordDialog(passwordTarget.value?.id), async () =>')
    expect(resetBlock).toContain('await changeUserPassword')
    expect(statusBlock).toContain('return runAction(userActionKeys.status(row.id), async () =>')
    expect(statusBlock).toContain('await updateUserStatus(row.id, !row.enabled)')
    expect(deleteBlock).toContain('return runAction(userActionKeys.delete(row.id), async () =>')
    expect(deleteBlock).toContain('await deleteUserRequest(row.id)')
  })

  test('role management uses row pending and drawer scoped guards', () => {
    const saveBlock = blockBetween(sources.roles, 'const saveRoleForm = async () =>', 'const toggleRole = async (row) =>')
    const statusBlock = blockBetween(sources.roles, 'const toggleRole = async (row) =>', 'const removeRole = async (row) =>')
    const deleteBlock = blockBetween(sources.roles, 'const removeRole = async (row) =>', 'const openPermissionDrawer = async (row) =>')
    const permissionBlock = blockBetween(sources.roles, 'const savePermissions = async () =>', 'const collectPermissionCodes = (keys = []) =>')
    const dataScopeBlock = blockBetween(sources.roles, 'const saveDataScope = async () =>', 'watch([permissionTreeSearch')

    expect(sources.roles).toContain(':loading="isPending(roleActionKeys.saveDialog)"')
    expect(sources.roles).toContain(':disabled="isPending(roleActionKeys.saveDialog)"')
    expect(sources.roles).toContain(':loading="isRoleStatusPending(row)"')
    expect(sources.roles).toContain(':disabled="isRoleRowActionPending(row)"')
    expect(sources.roles).toContain(':loading="isRoleDeletePending(row)"')
    expect(sources.roles).toContain(':disabled="row.superAdmin || isRoleRowActionPending(row)"')
    expect(sources.roles).toContain(':loading="permissionDrawerSavePending"')
    expect(sources.roles).toContain(':disabled="permissionDrawerSavePending"')
    expect(sources.roles).toContain(':loading="dataScopeDrawerSavePending"')
    expect(sources.roles).toContain(':disabled="dataScopeDrawerSavePending"')
    expect(saveBlock).toContain('return runAction(roleActionKeys.saveDialog, async () =>')
    expect(statusBlock).toContain('return runAction(roleActionKeys.status(row.id), async () =>')
    expect(deleteBlock).toContain('return runAction(roleActionKeys.delete(row.id), async () =>')
    expect(permissionBlock).toContain('return runAction(roleActionKeys.permissionsDrawer(currentRole.value?.id), async () =>')
    expect(dataScopeBlock).toContain('return runAction(roleActionKeys.dataScopeDrawer(currentRole.value?.id), async () =>')
  })

  test('organization and dictionary pages use dialog scoped saves and row scoped deletes/status changes', () => {
    const departmentSaveBlock = blockBetween(sources.org, 'const submitDepartment = async () =>', 'const toggleDepartmentStatus = async (row) =>')
    const postSaveBlock = blockBetween(sources.org, 'const submitPost = async () =>', 'const togglePostStatus = async (row) =>')
    const materialSaveBlock = blockBetween(sources.dict, 'const saveMaterial = async () =>', 'const saveProcess = async () =>')
    const extraDeleteBlock = blockBetween(sources.dict, 'const deleteExtraFee = async (row) =>', 'onMounted(loadAll)')

    expect(sources.org).toContain(':loading="isPending(orgActionKeys.saveDepartmentDialog)"')
    expect(sources.org).toContain(':disabled="isPending(orgActionKeys.saveDepartmentDialog)"')
    expect(sources.org).toContain(':loading="isDepartmentStatusPending(row)"')
    expect(sources.org).toContain(':disabled="isDepartmentRowActionPending(row)"')
    expect(sources.org).toContain(':loading="isPostDeletePending(row)"')
    expect(sources.org).toContain(':disabled="isPostRowActionPending(row)"')
    expect(departmentSaveBlock).toContain('return runAction(orgActionKeys.saveDepartmentDialog, async () =>')
    expect(postSaveBlock).toContain('return runAction(orgActionKeys.savePostDialog, async () =>')

    expect(sources.dict).toContain(':loading="isPending(dictActionKeys.saveMaterialDialog)"')
    expect(sources.dict).toContain(':disabled="isPending(dictActionKeys.saveMaterialDialog)"')
    expect(sources.dict).toContain(':loading="isMaterialDeletePending(scope.row)"')
    expect(sources.dict).toContain(':disabled="isMaterialDeletePending(scope.row)"')
    expect(sources.dict).toContain(':loading="isExtraFeeDeletePending(scope.row)"')
    expect(sources.dict).toContain(':disabled="isExtraFeeDeletePending(scope.row)"')
    expect(materialSaveBlock).toContain('return runAction(dictActionKeys.saveMaterialDialog, async () =>')
    expect(extraDeleteBlock).toContain('return runAction(dictActionKeys.deleteExtraFee(row.id), async () =>')
  })

  test('template upload and audit writes use button level pending guards', () => {
    const templateConfigBlock = blockBetween(sources.template, 'const saveTemplateConfig = async () =>', 'const uploadTemplate = async () =>')
    const templateBlock = blockBetween(sources.template, 'const uploadTemplate = async () =>', 'const formatDateTime = (value) =>')
    const exportBlock = blockBetween(sources.audit, 'const exportLogs = async () =>', 'const saveRetentionSetting = async () =>')
    const saveRetentionBlock = blockBetween(sources.audit, 'const saveRetentionSetting = async () =>', 'const runRetentionCleanup = async () =>')
    const cleanupBlock = blockBetween(sources.audit, 'const runRetentionCleanup = async () =>', 'const searchLogs = () =>')

    expect(sources.template).toContain(':loading="isPending(quoteTemplateActionKeys.saveConfig)"')
    expect(sources.template).toContain(':disabled="isPending(quoteTemplateActionKeys.saveConfig)"')
    expect(templateConfigBlock).toContain('return runAction(quoteTemplateActionKeys.saveConfig, async () =>')
    expect(templateConfigBlock).toContain('await updateQuoteTemplateConfig')

    expect(sources.template).toContain(':disabled="!selectedFile || isPending(quoteTemplateActionKeys.upload)"')
    expect(sources.template).toContain(':loading="isPending(quoteTemplateActionKeys.upload)"')
    expect(templateBlock).toContain('return runAction(quoteTemplateActionKeys.upload, async () =>')
    expect(templateBlock).toContain('await saveQuoteTemplate(formData)')

    expect(sources.audit).toContain(':loading="isPending(auditActionKeys.exportLogs)"')
    expect(sources.audit).toContain(':disabled="isPending(auditActionKeys.exportLogs)"')
    expect(sources.audit).toContain(':loading="isPending(auditActionKeys.cleanupRetention)"')
    expect(sources.audit).toContain(':disabled="isPending(auditActionKeys.cleanupRetention)"')
    expect(sources.audit).toContain(':loading="isPending(auditActionKeys.saveRetention)"')
    expect(sources.audit).toContain(':disabled="isPending(auditActionKeys.saveRetention)"')
    expect(exportBlock).toContain('return runAction(auditActionKeys.exportLogs, async () =>')
    expect(saveRetentionBlock).toContain('return runAction(auditActionKeys.saveRetention, async () =>')
    expect(cleanupBlock).toContain('return runAction(auditActionKeys.cleanupRetention, async () =>')
    expect(cleanupBlock).toContain('await ElMessageBox.confirm')
    expect(cleanupBlock).toContain('await runAuditLogRetentionCleanup()')
  })

  test('system config page uses dialog and row pending states for storage writes', () => {
    const saveBlock = blockBetween(sources.systemConfig, 'const saveConfig = async () =>', 'const testConfig = async (row) =>')
    const testBlock = blockBetween(sources.systemConfig, 'const testConfig = async (row) =>', 'const activateConfig = async (row) =>')
    const activateBlock = blockBetween(sources.systemConfig, 'const activateConfig = async (row) =>', 'const disableConfig = async (row) =>')
    const disableBlock = blockBetween(sources.systemConfig, 'const disableConfig = async (row) =>', 'const isRowPending = (row) =>')

    expect(sources.systemConfig).toContain(':loading="isPending(systemConfigActionKeys.saveDialog)"')
    expect(sources.systemConfig).toContain(':disabled="isPending(systemConfigActionKeys.saveDialog)"')
    expect(sources.systemConfig).toContain(':loading="isPending(systemConfigActionKeys.test(row.id))"')
    expect(sources.systemConfig).toContain(':disabled="isRowPending(row)"')
    expect(sources.systemConfig).toContain(':loading="isPending(systemConfigActionKeys.activate(row.id))"')
    expect(sources.systemConfig).toContain(':disabled="row.active || row.enabled === false || isRowPending(row)"')
    expect(sources.systemConfig).toContain(':loading="isPending(systemConfigActionKeys.disable(row.id))"')
    expect(saveBlock).toContain('return runAction(systemConfigActionKeys.saveDialog, async () =>')
    expect(testBlock).toContain('return runAction(systemConfigActionKeys.test(row.id), async () =>')
    expect(activateBlock).toContain('return runAction(systemConfigActionKeys.activate(row.id), async () =>')
    expect(disableBlock).toContain('return runAction(systemConfigActionKeys.disable(row.id), async () =>')
  })

  test('system config page uses dialog and row pending states for integration writes', () => {
    const saveBlock = blockBetween(sources.systemConfig, 'const saveIntegrationConfigForm = async () =>', 'const testIntegrationConfigRow = async (row) =>')
    const testBlock = blockBetween(sources.systemConfig, 'const testIntegrationConfigRow = async (row) =>', 'const activateIntegrationConfigRow = async (row) =>')
    const activateBlock = blockBetween(sources.systemConfig, 'const activateIntegrationConfigRow = async (row) =>', 'const disableIntegrationConfigRow = async (row) =>')
    const disableBlock = blockBetween(sources.systemConfig, 'const disableIntegrationConfigRow = async (row) =>', 'const isIntegrationRowPending = (row) =>')

    expect(sources.systemConfig).toContain(':loading="isPending(integrationActionKeys.saveDialog)"')
    expect(sources.systemConfig).toContain(':disabled="isPending(integrationActionKeys.saveDialog)"')
    expect(sources.systemConfig).toContain(':loading="isPending(integrationActionKeys.test(row.id))"')
    expect(sources.systemConfig).toContain(':disabled="isIntegrationRowPending(row)"')
    expect(sources.systemConfig).toContain(':loading="isPending(integrationActionKeys.activate(row.id))"')
    expect(sources.systemConfig).toContain(':loading="isPending(integrationActionKeys.disable(row.id))"')
    expect(saveBlock).toContain('return runAction(integrationActionKeys.saveDialog, async () =>')
    expect(testBlock).toContain('return runAction(integrationActionKeys.test(row.id), async () =>')
    expect(activateBlock).toContain('return runAction(integrationActionKeys.activate(row.id), async () =>')
    expect(disableBlock).toContain('return runAction(integrationActionKeys.disable(row.id), async () =>')
  })

  test('admin row action key suppresses duplicate API calls while pending', async () => {
    const { runAction } = usePendingAction()
    const pendingRequest = deferred()
    const deleteUser = vi.fn(() => pendingRequest.promise)

    const firstClick = runAction('system:user:delete:42', deleteUser)
    const secondClick = runAction('system:user:delete:42', deleteUser)

    await expect(secondClick).resolves.toBeUndefined()
    expect(deleteUser).toHaveBeenCalledTimes(1)

    pendingRequest.resolve({ message: 'ok' })

    await expect(firstClick).resolves.toEqual({ message: 'ok' })
    expect(deleteUser).toHaveBeenCalledTimes(1)
  })
})
