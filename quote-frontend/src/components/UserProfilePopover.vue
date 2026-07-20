<template>
  <el-popover
    placement="bottom-end"
    trigger="click"
    :width="340"
    popper-class="profile-panel-popper"
  >
    <template #reference>
      <button type="button" class="profile-trigger" :title="displayName" aria-label="打开个人资料">
        <el-avatar size="small" class="profile-avatar">{{ userInitial }}</el-avatar>
        <span class="profile-trigger-name">{{ displayName }}</span>
        <el-icon class="profile-trigger-arrow"><ArrowDown /></el-icon>
      </button>
    </template>

    <section class="profile-panel">
      <header class="profile-panel-header">
        <el-avatar :size="44" class="profile-panel-avatar">{{ userInitial }}</el-avatar>
        <div class="profile-panel-title">
          <div class="profile-name" :title="displayName">{{ displayName }}</div>
          <div class="profile-account" :title="profile.username">{{ valueOrDash(profile.username) }}</div>
        </div>
      </header>

      <div class="profile-section">
        <div class="profile-section-title">权限身份</div>
        <div class="profile-row">
          <span class="profile-label">角色</span>
          <span class="profile-value" :title="roleNames">{{ valueOrDash(roleNames) }}</span>
        </div>
      </div>

      <div class="profile-section">
        <div class="profile-section-title">组织信息</div>
        <div class="profile-row">
          <span class="profile-label">部门</span>
          <span class="profile-value" :title="profile.deptName">{{ valueOrDash(profile.deptName) }}</span>
        </div>
        <div class="profile-row">
          <span class="profile-label">岗位</span>
          <span class="profile-value" :title="profile.postName">{{ valueOrDash(profile.postName) }}</span>
        </div>
      </div>

      <div class="profile-section">
        <div class="profile-section-title">联系方式</div>
        <div class="profile-row">
          <span class="profile-label">手机</span>
          <span class="profile-value" :title="profile.phone">{{ valueOrDash(profile.phone) }}</span>
        </div>
        <div class="profile-row">
          <span class="profile-label">邮箱</span>
          <span class="profile-value" :title="profile.email">{{ valueOrDash(profile.email) }}</span>
        </div>
      </div>

      <footer class="profile-actions">
        <el-button text type="primary" @click="openProfileDialog">编辑联系方式</el-button>
        <el-button text type="primary" @click="emit('change-password')">修改密码</el-button>
        <el-button text type="danger" @click="emit('logout')">退出登录</el-button>
      </footer>
    </section>
  </el-popover>

  <el-dialog
    v-model="profileDialogVisible"
    title="编辑联系方式"
    width="420px"
    destroy-on-close
  >
    <el-form
      ref="profileFormRef"
      :model="profileForm"
      :rules="profileRules"
      label-width="72px"
      class="profile-form"
    >
      <el-form-item label="手机" prop="phone">
        <el-input
          v-model.trim="profileForm.phone"
          clearable
          maxlength="30"
          placeholder="可留空"
          autocomplete="tel"
        />
      </el-form-item>
      <el-form-item label="邮箱" prop="email">
        <el-input
          v-model.trim="profileForm.email"
          clearable
          maxlength="120"
          placeholder="可留空"
          autocomplete="email"
        />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="profileDialogVisible = false">取消</el-button>
      <el-button type="primary" :loading="profileSaving" @click="saveProfile">保存</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { computed, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { ArrowDown } from '@element-plus/icons-vue'
import { useAuthStore } from '../stores/authStore'

const emit = defineEmits(['change-password', 'logout'])
const authStore = useAuthStore()

const profile = computed(() => authStore.user || {})
const displayName = computed(() => authStore.displayName)
const userInitial = computed(() => displayName.value.slice(0, 1).toUpperCase())
const roleNames = computed(() => {
  if (Array.isArray(profile.value.roleNames) && profile.value.roleNames.length > 0) {
    return profile.value.roleNames.join(' / ')
  }
  return authStore.roleName
})
const profileDialogVisible = ref(false)
const profileSaving = ref(false)
const profileFormRef = ref()
const profileForm = reactive({
  phone: '',
  email: ''
})

const phonePattern = /^[0-9+\-\s()]{6,30}$/
const emailPattern = /^[^\s@]+@[^\s@]+\.[^\s@]+$/

const validatePhone = (_rule, value, callback) => {
  if (!value || phonePattern.test(value)) {
    callback()
    return
  }
  callback(new Error('手机号格式不正确'))
}

const validateEmail = (_rule, value, callback) => {
  if (!value || emailPattern.test(value)) {
    callback()
    return
  }
  callback(new Error('邮箱格式不正确'))
}

const profileRules = {
  phone: [{ validator: validatePhone, trigger: 'blur' }],
  email: [{ validator: validateEmail, trigger: 'blur' }]
}

const openProfileDialog = () => {
  profileForm.phone = profile.value.phone || ''
  profileForm.email = profile.value.email || ''
  profileDialogVisible.value = true
  profileFormRef.value?.clearValidate()
}

const saveProfile = async () => {
  await profileFormRef.value?.validate()
  profileSaving.value = true
  try {
    const response = await authStore.updateMyProfile({
      phone: profileForm.phone.trim(),
      email: profileForm.email.trim()
    })
    if (!response?.success) {
      throw new Error(response?.message || '联系方式保存失败')
    }
    ElMessage.success('联系方式已更新')
    profileDialogVisible.value = false
  } catch (error) {
    ElMessage.error(error.response?.data?.message || error.message || '联系方式保存失败')
  } finally {
    profileSaving.value = false
  }
}

const valueOrDash = (value) => {
  const text = Array.isArray(value) ? value.join(' / ') : value
  return text == null || String(text).trim() === '' ? '未填写' : String(text).trim()
}
</script>

<style scoped>
.profile-trigger {
  display: inline-flex;
  align-items: center;
  gap: var(--erp-space-2);
  max-width: 240px;
  min-height: 40px;
  padding: var(--erp-space-1) var(--erp-space-2);
  color: var(--erp-text);
  background: var(--erp-surface-muted);
  border: 1px solid var(--erp-border-soft);
  border-radius: var(--erp-radius-full);
  cursor: pointer;
  transition:
    background-color var(--erp-duration-fast) var(--erp-ease-standard),
    border-color var(--erp-duration-fast) var(--erp-ease-standard),
    box-shadow var(--erp-duration-fast) var(--erp-ease-standard);
}

.profile-trigger:hover {
  background: var(--erp-primary-soft);
  border-color: var(--erp-primary);
}

.profile-trigger:focus-visible {
  outline: 2px solid var(--erp-primary);
  outline-offset: 2px;
}

.profile-avatar,
.profile-panel-avatar {
  flex: 0 0 auto;
  color: var(--erp-text-inverse);
  background: var(--erp-primary);
}

.profile-trigger-name {
  min-width: 0;
  max-width: 150px;
  overflow: hidden;
  font-size: var(--erp-font-size-sm);
  font-weight: var(--erp-font-weight-bold);
  text-overflow: ellipsis;
  white-space: nowrap;
}

.profile-trigger-arrow {
  flex: 0 0 auto;
  color: var(--erp-text-secondary);
  font-size: var(--erp-font-size-xs);
}

.profile-panel {
  display: flex;
  flex-direction: column;
  gap: var(--erp-space-4);
  color: var(--erp-text);
}

.profile-panel-header {
  display: flex;
  align-items: center;
  gap: var(--erp-space-3);
  padding-bottom: var(--erp-space-3);
  border-bottom: 1px solid var(--erp-border-soft);
}

.profile-panel-title {
  min-width: 0;
}

.profile-name {
  overflow: hidden;
  font-size: var(--erp-font-size-md);
  font-weight: var(--erp-font-weight-bold);
  line-height: var(--erp-line-height-tight);
  text-overflow: ellipsis;
  white-space: nowrap;
}

.profile-account {
  overflow: hidden;
  margin-top: 2px;
  color: var(--erp-text-secondary);
  font-size: var(--erp-font-size-xs);
  text-overflow: ellipsis;
  white-space: nowrap;
}

.profile-section {
  display: grid;
  gap: var(--erp-space-2);
}

.profile-section-title {
  color: var(--erp-text-secondary);
  font-size: var(--erp-font-size-xs);
  font-weight: var(--erp-font-weight-bold);
}

.profile-row {
  display: grid;
  grid-template-columns: 64px minmax(0, 1fr);
  align-items: center;
  gap: var(--erp-space-3);
  min-height: 28px;
}

.profile-label {
  color: var(--erp-text-secondary);
  font-size: var(--erp-font-size-xs);
}

.profile-value {
  min-width: 0;
  overflow: hidden;
  color: var(--erp-text);
  font-size: var(--erp-font-size-sm);
  text-align: right;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.profile-actions {
  display: flex;
  flex-wrap: wrap;
  justify-content: flex-end;
  gap: var(--erp-space-2);
  padding-top: var(--erp-space-3);
  border-top: 1px solid var(--erp-border-soft);
}

.profile-form {
  padding-top: var(--erp-space-2);
}

@media (max-width: 640px) {
  .profile-trigger-name {
    display: none;
  }
}
</style>
