<template>
  <div class="erp-page user-manage-page">
    <header class="erp-page-header user-page-header">
      <div>
        <h1 class="erp-page-title">用户管理</h1>
        <p class="erp-page-description">维护登录账号、部门角色和启用状态。</p>
      </div>
      <div class="erp-page-actions user-page-actions">
        <el-button v-permission="'system:user:create'" type="primary" @click="openCreate">新增用户</el-button>
      </div>
    </header>

    <section class="erp-toolbar user-toolbar">
      <div class="erp-toolbar-group">
        <strong>账号列表</strong>
        <span class="user-toolbar-note">启用、禁用、重置密码和删除账号都在这里处理。</span>
      </div>
    </section>

    <section class="erp-table-surface user-table-surface">
      <div class="erp-table-body">
        <el-table :data="users" border v-loading="loading">
          <el-table-column prop="id" label="ID" width="80" />
          <el-table-column prop="username" label="账号" min-width="130" show-overflow-tooltip />
          <el-table-column prop="displayName" label="显示名称" min-width="150" show-overflow-tooltip />
          <el-table-column prop="deptName" label="部门" min-width="130" show-overflow-tooltip />
          <el-table-column prop="postName" label="岗位" min-width="130" show-overflow-tooltip />
          <el-table-column label="角色" min-width="220" show-overflow-tooltip>
            <template #default="{ row }">
              <div class="user-role-tags">
                <el-tag
                  v-for="role in roleTags(row)"
                  :key="role.code"
                  size="small"
                  effect="plain"
                >
                  {{ role.label }}
                </el-tag>
              </div>
            </template>
          </el-table-column>
          <el-table-column label="状态" width="110">
            <template #default="{ row }">
              <el-tag :type="row.enabled ? 'success' : 'info'">{{ row.enabled ? '启用' : '禁用' }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="安全状态" width="160">
            <template #default="{ row }">
              <el-tag v-if="isLocked(row)" type="danger" size="small">已锁定</el-tag>
              <el-tag v-else-if="row.forcePasswordChange" type="warning" size="small">需改密</el-tag>
              <el-tag v-else type="success" size="small">正常</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="lastLoginAt" label="最近登录" min-width="180" />
          <el-table-column prop="createdAt" label="创建时间" min-width="180" />
          <el-table-column label="操作" width="330" fixed="right">
            <template #default="{ row }">
              <div class="erp-table-actions user-row-actions">
                <el-button v-permission="'system:user:update'" size="small" @click="openEdit(row)">编辑</el-button>
                <el-button v-permission="'system:user:reset-password'" size="small" @click="openPassword(row)">重置密码</el-button>
                <el-button v-permission="'system:user:disable'" size="small" :type="row.enabled ? 'warning' : 'success'" @click="toggleEnabled(row)">
                  {{ row.enabled ? '禁用' : '启用' }}
                </el-button>
                <el-button v-permission="'system:user:delete'" size="small" type="danger" plain class="erp-danger-action" :disabled="isProtectedUser(row)" @click="deleteUser(row)">
                  删除
                </el-button>
              </div>
            </template>
          </el-table-column>
        </el-table>
      </div>
    </section>

    <el-dialog v-model="dialogVisible" :title="editingId ? '编辑用户' : '新增用户'" width="460px">
      <el-form :model="form" label-width="90px" class="erp-dialog-form">
        <el-form-item label="账号">
          <el-input v-model.trim="form.username" :disabled="!!editingId" />
        </el-form-item>
        <el-form-item label="显示名称">
          <el-input v-model.trim="form.displayName" />
        </el-form-item>
        <el-form-item label="角色">
          <el-select v-model="form.roleCodes" multiple collapse-tags collapse-tags-tooltip style="width: 100%">
            <el-option
              v-for="role in roleOptions"
              :key="role.roleCode"
              :label="role.roleName || role.roleCode"
              :value="role.roleCode"
              :disabled="role.enabled === false"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-switch v-model="form.enabled" active-text="启用" inactive-text="禁用" />
        </el-form-item>
        <el-form-item v-if="!editingId" label="初始密码">
          <el-input v-model="form.password" type="password" show-password />
        </el-form-item>
      </el-form>
      <template #footer>
        <div class="erp-dialog-footer">
          <el-button @click="dialogVisible = false">取消</el-button>
          <el-button v-permission="['system:user:create', 'system:user:update']" type="primary" :loading="saving" @click="saveUser">保存</el-button>
        </div>
      </template>
    </el-dialog>

    <el-dialog v-model="passwordVisible" title="重置密码" width="420px">
      <el-form label-width="90px" class="erp-dialog-form">
        <el-form-item label="账号">
          <el-input :model-value="passwordTarget?.username" disabled />
        </el-form-item>
        <el-form-item label="新密码">
          <el-input v-model="newPassword" type="password" show-password />
        </el-form-item>
      </el-form>
      <template #footer>
        <div class="erp-dialog-footer">
          <el-button @click="passwordVisible = false">取消</el-button>
          <el-button v-permission="'system:user:reset-password'" type="primary" :loading="saving" @click="resetPassword">确定</el-button>
        </div>
      </template>
    </el-dialog>
  </div>
</template>
<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  changeUserPassword,
  deleteUser as deleteUserRequest,
  listRoles,
  listUsers,
  saveUser as saveUserRequest,
  updateUserStatus
} from '../services/adminApi'

const users = ref([])
const roleOptions = ref([])
const loading = ref(false)
const saving = ref(false)
const dialogVisible = ref(false)
const passwordVisible = ref(false)
const editingId = ref(null)
const passwordTarget = ref(null)
const newPassword = ref('')

const form = reactive({
  username: '',
  password: '',
  displayName: '',
  deptId: null,
  postId: null,
  roleCodes: ['MARKET_USER'],
  enabled: true
})

const fallbackRoleLabels = {
  ADMIN: '管理员',
  MARKET: '市场部',
  ENGINEER: '工程部',
  SUPER_ADMIN: '超级管理员',
  MARKET_USER: '市场专员',
  MARKET_MANAGER: '市场主管',
  ENGINEER_USER: '工程核价员',
  ENGINEER_MANAGER: '工程主管',
  AUDITOR: '审计员',
  READONLY: '只读查看员'
}

const legacyRoleCode = (role) => ({
  ADMIN: 'SUPER_ADMIN',
  MARKET: 'MARKET_USER',
  ENGINEER: 'ENGINEER_USER'
}[role] || role)

const roleLabel = (role) => {
  const option = roleOptions.value.find(item => item.roleCode === role)
  return option?.roleName || fallbackRoleLabels[role] || role
}

const roleTags = (row) => {
  const codes = Array.isArray(row.roleCodes) && row.roleCodes.length
    ? row.roleCodes
    : [legacyRoleCode(row.role)]
  return codes.filter(Boolean).map(code => ({ code, label: roleLabel(code) }))
}

const protectedUsernames = new Set(['admin', 'market', 'engineer'])

const isProtectedUser = (row) => protectedUsernames.has(String(row.username || '').trim().toLowerCase())

const loadUsers = async () => {
  loading.value = true
  try {
    const response = await listUsers()
    users.value = response || []
  } finally {
    loading.value = false
  }
}

const loadRoles = async () => {
  const response = await listRoles()
  roleOptions.value = (response || []).filter(role => role.deleted !== true)
}

const resetForm = () => {
  editingId.value = null
  form.username = ''
  form.password = ''
  form.displayName = ''
  form.deptId = null
  form.postId = null
  form.roleCodes = ['MARKET_USER']
  form.enabled = true
}

const openCreate = () => {
  resetForm()
  dialogVisible.value = true
}

const openEdit = (row) => {
  editingId.value = row.id
  form.username = row.username
  form.password = ''
  form.displayName = row.displayName
  form.deptId = row.deptId || null
  form.postId = row.postId || null
  form.roleCodes = Array.isArray(row.roleCodes) && row.roleCodes.length
    ? [...row.roleCodes]
    : [legacyRoleCode(row.role)]
  form.enabled = row.enabled
  dialogVisible.value = true
}

const saveUser = async () => {
  if (!form.username) {
    ElMessage.warning('请输入账号')
    return
  }
  if (!editingId.value && (!form.password || form.password.length < 8)) {
    ElMessage.warning('初始密码至少 8 位')
    return
  }
  if (!form.roleCodes.length) {
    ElMessage.warning('请至少选择一个角色')
    return
  }
  saving.value = true
  try {
    const payload = {
      username: form.username,
      displayName: form.displayName,
      deptId: form.deptId,
      postId: form.postId,
      roleCodes: form.roleCodes,
      enabled: form.enabled
    }
    if (form.password) payload.password = form.password
    const response = await saveUserRequest(editingId.value ? { ...payload, id: editingId.value } : payload)
    ElMessage.success(response.message || '保存成功')
    dialogVisible.value = false
    await loadUsers()
  } finally {
    saving.value = false
  }
}

const openPassword = (row) => {
  passwordTarget.value = row
  newPassword.value = ''
  passwordVisible.value = true
}

const resetPassword = async () => {
  if (!newPassword.value || newPassword.value.length < 8) {
    ElMessage.warning('新密码至少 8 位')
    return
  }
  saving.value = true
  try {
    await changeUserPassword(passwordTarget.value.id, {
      password: newPassword.value
    })
    ElMessage.success('密码已重置，用户下次登录必须修改密码')
    passwordVisible.value = false
    await loadUsers()
  } finally {
    saving.value = false
  }
}

const toggleEnabled = async (row) => {
  await ElMessageBox.confirm(`确认${row.enabled ? '禁用' : '启用'}账号 ${row.username}？`, '确认操作')
  await updateUserStatus(row.id, !row.enabled)
  ElMessage.success('状态已更新')
  await loadUsers()
}

const deleteUser = async (row) => {
  if (isProtectedUser(row)) {
    ElMessage.warning('初始账号不可删除')
    return
  }
  await ElMessageBox.confirm(
    `确认彻底删除账号 ${row.username}？删除后无法恢复。`,
    '删除用户确认',
    {
      type: 'warning',
      confirmButtonText: '彻底删除',
      cancelButtonText: '取消'
    }
  )
  await deleteUserRequest(row.id)
  ElMessage.success('用户已删除')
  await loadUsers()
}

const isLocked = (row) => row.lockedUntil && new Date(row.lockedUntil).getTime() > Date.now()

onMounted(async () => {
  await loadRoles()
  await loadUsers()
})
</script>

<style scoped>
.user-toolbar-note {
  color: var(--erp-text-secondary);
  font-size: var(--erp-font-size-sm);
}

.user-table-surface .erp-table-body {
  padding: var(--erp-space-3);
}

.user-row-actions {
  justify-content: flex-start;
}

.user-role-tags {
  display: flex;
  flex-wrap: wrap;
  gap: var(--erp-space-1);
}
</style>
