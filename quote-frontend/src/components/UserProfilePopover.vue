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
        <el-button text type="primary" @click="emit('change-password')">修改密码</el-button>
        <el-button text type="danger" @click="emit('logout')">退出登录</el-button>
      </footer>
    </section>
  </el-popover>
</template>

<script setup>
import { computed } from 'vue'
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
  justify-content: flex-end;
  gap: var(--erp-space-2);
  padding-top: var(--erp-space-3);
  border-top: 1px solid var(--erp-border-soft);
}

@media (max-width: 640px) {
  .profile-trigger-name {
    display: none;
  }
}
</style>
