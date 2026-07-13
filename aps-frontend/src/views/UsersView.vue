<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { createUser, deleteUser as deleteUserApi, listUsers, updateUser } from '../services/userApi'
import ApsStatusTag from '../components/ApsStatusTag.vue'

const users = ref([])
const loading = ref(false)
const saving = ref(false)
const error = ref('')
const notice = ref('')
const dialogVisible = ref(false)
const passwordVisible = ref(false)
const editingId = ref(null)
const passwordTarget = ref(null)
const newPassword = ref('')

const form = reactive({
  username: '',
  password: '',
  displayName: '',
  role: 'ADMIN',
  enabled: true
})

const roleLabel = (role) => ({
  ADMIN: '管理员'
}[role] || role || '-')

const isProtectedUser = (row) => String(row?.username || '').trim().toLowerCase() === 'admin'
const isLocked = (row) => row.lockedUntil && new Date(row.lockedUntil).getTime() > Date.now()

const run = async (fn, successMessage = '') => {
  loading.value = true
  error.value = ''
  notice.value = ''
  try {
    await fn()
    if (successMessage) notice.value = successMessage
  } catch (err) {
    error.value = err.message || '操作失败'
  } finally {
    loading.value = false
  }
}

const loadUsers = async () => {
  users.value = await listUsers()
}

const resetForm = () => {
  editingId.value = null
  Object.assign(form, {
    username: '',
    password: '',
    displayName: '',
    role: 'ADMIN',
    enabled: true
  })
}

const openCreate = () => {
  resetForm()
  dialogVisible.value = true
}

const openEdit = (row) => {
  editingId.value = row.id
  Object.assign(form, {
    username: row.username || '',
    password: '',
    displayName: row.displayName || '',
    role: row.role || 'ADMIN',
    enabled: row.enabled !== false
  })
  dialogVisible.value = true
}

const saveUser = async () => {
  if (!form.username.trim()) {
    ElMessage.warning('请输入账号')
    return
  }
  if (!editingId.value && (!form.password || form.password.length < 8)) {
    ElMessage.warning('初始密码至少 8 位')
    return
  }
  saving.value = true
  error.value = ''
  notice.value = ''
  try {
    const payload = {
      username: form.username.trim(),
      displayName: form.displayName.trim(),
      role: 'ADMIN',
      enabled: form.enabled
    }
    if (form.password) payload.password = form.password
    const response = editingId.value
      ? await updateUser(editingId.value, payload)
      : await createUser(payload)
    notice.value = response?.message || '保存成功'
    dialogVisible.value = false
    await loadUsers()
  } catch (err) {
    error.value = err.message || '保存失败'
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
  error.value = ''
  notice.value = ''
  try {
    const response = await updateUser(passwordTarget.value.id, { password: newPassword.value })
    notice.value = response?.message || '密码已重置'
    passwordVisible.value = false
    await loadUsers()
  } catch (err) {
    error.value = err.message || '密码重置失败'
  } finally {
    saving.value = false
  }
}

const toggleEnabled = async (row) => {
  await ElMessageBox.confirm(`确认${row.enabled ? '禁用' : '启用'}账号 ${row.username}？`, '确认操作')
  await run(async () => {
    const response = await updateUser(row.id, { enabled: !row.enabled })
    await loadUsers()
    notice.value = response?.message || '状态已更新'
  })
}

const deleteUser = async (row) => {
  if (isProtectedUser(row)) {
    ElMessage.warning('初始管理员账号不可删除')
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
  await run(async () => {
    const response = await deleteUserApi(row.id)
    await loadUsers()
    notice.value = response?.message || '用户已删除'
  })
}

onMounted(() => run(loadUsers))
</script>

<template>
  <section class="content-grid">
    <p v-if="error" class="message error">{{ error }}</p>
    <p v-else-if="notice" class="message notice">{{ notice }}</p>

    <section class="panel">
      <div class="section-head">
        <h2>用户权限</h2>
        <div class="section-actions">
          <span>当前仅开放管理员角色</span>
          <el-button :disabled="loading" @click="run(loadUsers)">刷新</el-button>
          <el-button type="primary" :disabled="loading" @click="openCreate">新增管理员</el-button>
        </div>
      </div>

      <el-table :data="users" border stripe class="aps-data-table" empty-text="暂无用户" row-key="id" v-loading="loading">
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column prop="username" label="账号" min-width="130" />
        <el-table-column prop="displayName" label="显示名称" min-width="150" />
        <el-table-column label="角色" width="120">
          <template #default="{ row }">
            <ApsStatusTag :value="roleLabel(row.role)" tone="muted" />
          </template>
        </el-table-column>
        <el-table-column label="状态" width="110">
          <template #default="{ row }">
            <ApsStatusTag :value="row.enabled ? '启用' : '禁用'" :tone="row.enabled ? 'ok' : 'bad'" />
          </template>
        </el-table-column>
        <el-table-column label="安全状态" width="130">
          <template #default="{ row }">
            <ApsStatusTag v-if="isLocked(row)" value="已锁定" tone="bad" />
            <ApsStatusTag v-else-if="row.forcePasswordChange" value="需改密" tone="warn" />
            <ApsStatusTag v-else value="正常" tone="ok" />
          </template>
        </el-table-column>
        <el-table-column prop="lastLoginAt" label="最近登录" min-width="180" />
        <el-table-column prop="createdAt" label="创建时间" min-width="180" />
        <el-table-column label="操作" width="284" fixed="right">
          <template #default="{ row }">
            <div class="row-actions">
              <el-button type="primary" link @click="openEdit(row)">编辑</el-button>
              <el-button type="primary" link @click="openPassword(row)">重置密码</el-button>
              <el-button :type="row.enabled ? 'warning' : 'success'" link @click="toggleEnabled(row)">
                {{ row.enabled ? '禁用' : '启用' }}
              </el-button>
              <el-button type="danger" link :disabled="isProtectedUser(row)" @click="deleteUser(row)">删除</el-button>
            </div>
          </template>
        </el-table-column>
      </el-table>
    </section>

    <el-dialog v-model="dialogVisible" :title="editingId ? '编辑管理员' : '新增管理员'" width="460px">
      <form class="form-grid user-dialog-form" @submit.prevent="saveUser">
        <label>账号<el-input v-model="form.username" :disabled="!!editingId" placeholder="必填" /></label>
        <label>显示名称<el-input v-model="form.displayName" /></label>
        <label>角色
          <el-select v-model="form.role" disabled>
            <el-option label="管理员" value="ADMIN" />
          </el-select>
        </label>
        <label>状态<el-switch v-model="form.enabled" active-text="启用" inactive-text="禁用" /></label>
        <label v-if="!editingId">初始密码<el-input v-model="form.password" type="password" show-password placeholder="至少 8 位" /></label>
      </form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="saveUser">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="passwordVisible" title="重置密码" width="420px">
      <form class="form-grid user-dialog-form" @submit.prevent="resetPassword">
        <label>账号<el-input :model-value="passwordTarget?.username || ''" disabled /></label>
        <label>新密码<el-input v-model="newPassword" type="password" show-password placeholder="至少 8 位" /></label>
      </form>
      <template #footer>
        <el-button @click="passwordVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="resetPassword">确定</el-button>
      </template>
    </el-dialog>
  </section>
</template>
