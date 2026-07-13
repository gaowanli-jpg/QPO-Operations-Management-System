<script setup>
import { computed, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Box, Cpu, DataBoard, Document, OfficeBuilding, SetUp, Tickets, Tools, User } from '@element-plus/icons-vue'
import { changePassword as changePasswordApi, logout as logoutApi } from './services/authApi'
import { authState, clearAuth, updateAuthUser } from './auth'

const route = useRoute()
const router = useRouter()

const menus = [
  { path: '/sync', label: 'ERP 同步', icon: DataBoard },
  {
    path: '/assignment',
    label: '分配中心',
    icon: Tickets,
    children: [
      { path: '/assignment/pending', label: '待分配' },
      { path: '/assignment/assigned', label: '已分配' }
    ]
  },
  { path: '/department-center', label: '部门中心', icon: OfficeBuilding },
  {
    path: '/part-inventory',
    label: '零件库存',
    icon: Box,
    children: [
      { path: '/part-inventory/manage', label: '零件库存维护' },
      { path: '/part-inventory/locks', label: '零件锁定记录' },
      { path: '/part-inventory/requirements', label: '生产需求' }
    ]
  },
  {
    path: '/material-inventory',
    label: '材料库存',
    icon: Document,
    children: [
      { path: '/material-inventory/manage', label: '材料库存维护' },
      { path: '/material-inventory/locks', label: '材料锁定记录' },
      { path: '/material-inventory/shortages', label: '材料缺口' }
    ]
  },
  { path: '/departments', label: '部门管理', icon: SetUp },
  { path: '/processes', label: '工艺管理', icon: Tools },
  { path: '/machines', label: '机台能力', icon: Cpu },
  { path: '/users', label: '系统管理：用户权限', icon: User }
]

const pageTitle = computed(() => route.meta?.title || 'APS')
const activeMenu = computed(() => route.path)
const isLoginPage = computed(() => route.path === '/login')
const displayName = computed(() => authState.user?.displayName || authState.user?.username || '未登录')
const userInitial = computed(() => displayName.value.slice(0, 1).toUpperCase())
const roleName = computed(() => ({
  ADMIN: '管理员'
}[authState.user?.role] || '未知角色'))
const mustChangePassword = computed(() => !!authState.user?.forcePasswordChange)
const defaultOpeneds = menus
  .filter((menu) => menu.children)
  .map((menu) => menu.path)

const passwordVisible = ref(false)
const forcePasswordMode = ref(false)
const passwordSaving = ref(false)
const passwordForm = reactive({
  oldPassword: '',
  newPassword: '',
  confirmPassword: ''
})

const resetPasswordForm = () => {
  Object.assign(passwordForm, {
    oldPassword: '',
    newPassword: '',
    confirmPassword: ''
  })
}

const openPasswordDialog = (force = false) => {
  forcePasswordMode.value = force
  resetPasswordForm()
  passwordVisible.value = true
}

watch(mustChangePassword, (value) => {
  if (value && !isLoginPage.value) {
    openPasswordDialog(true)
  }
}, { immediate: true })

const changePassword = async () => {
  if (!passwordForm.oldPassword) {
    ElMessage.warning('请输入原密码')
    return
  }
  if (!passwordForm.newPassword || passwordForm.newPassword.length < 8) {
    ElMessage.warning('新密码至少 8 位')
    return
  }
  if (passwordForm.newPassword !== passwordForm.confirmPassword) {
    ElMessage.warning('两次输入的新密码不一致')
    return
  }
  passwordSaving.value = true
  try {
    await changePasswordApi(passwordForm)
    updateAuthUser({ forcePasswordChange: false })
    ElMessage.success('密码已修改')
    passwordVisible.value = false
    forcePasswordMode.value = false
    resetPasswordForm()
  } catch (err) {
    ElMessage.error(err.message || '密码修改失败')
  } finally {
    passwordSaving.value = false
  }
}

const logout = async () => {
  try {
    await logoutApi()
  } catch {
    // 本地退出不依赖服务端响应。
  } finally {
    clearAuth()
    router.replace('/login')
  }
}
</script>

<template>
  <router-view v-if="isLoginPage" />
  <el-container v-else class="aps-layout">
    <el-aside width="236px" class="aps-sidebar">
      <div class="brand">
        <span class="brand-mark">APS</span>
        <span>生产准备中心</span>
      </div>

      <el-menu
        :default-active="activeMenu"
        :default-openeds="defaultOpeneds"
        class="aps-menu"
        background-color="#172331"
        text-color="#a9bbcc"
        active-text-color="#ffffff"
        router
      >
        <div class="menu-title">生产计划</div>

        <template v-for="menu in menus" :key="menu.path">
          <el-menu-item
            v-if="!menu.children"
            :index="menu.path"
          >
            <el-icon><component :is="menu.icon" /></el-icon>
            <span>{{ menu.label }}</span>
          </el-menu-item>

          <el-sub-menu v-else :index="menu.path">
            <template #title>
              <el-icon><component :is="menu.icon" /></el-icon>
              <span>{{ menu.label }}</span>
            </template>
            <el-menu-item
              v-for="child in menu.children"
              :key="child.path"
              :index="child.path"
            >
              {{ child.label }}
            </el-menu-item>
          </el-sub-menu>
        </template>
      </el-menu>
    </el-aside>

    <el-container class="aps-main-container">
      <el-header class="aps-header" height="70px">
        <div class="header-title">
          <p class="eyebrow">APS</p>
          <h1>{{ pageTitle }}</h1>
        </div>
        <div class="header-right">
          <el-avatar size="small">{{ userInitial }}</el-avatar>
          <span class="username">{{ displayName }}（{{ roleName }}）</span>
          <el-button text type="primary" @click="openPasswordDialog(false)">修改密码</el-button>
          <el-button text type="primary" @click="logout">退出</el-button>
        </div>
      </el-header>

      <el-main class="workspace">
        <router-view />
      </el-main>
    </el-container>
  </el-container>

  <el-dialog
    v-model="passwordVisible"
    :title="forcePasswordMode ? '首次使用请修改密码' : '修改密码'"
    width="440px"
    :close-on-click-modal="!forcePasswordMode"
    :close-on-press-escape="!forcePasswordMode"
    :show-close="!forcePasswordMode"
  >
    <el-alert
      v-if="forcePasswordMode"
      title="当前账号使用初始密码或管理员重置密码，必须修改后才能继续使用系统。"
      type="warning"
      show-icon
      :closable="false"
      class="password-alert"
    />
    <form class="form-grid user-dialog-form" @submit.prevent="changePassword">
      <label>原密码<el-input v-model="passwordForm.oldPassword" type="password" show-password autocomplete="current-password" /></label>
      <label>新密码<el-input v-model="passwordForm.newPassword" type="password" show-password autocomplete="new-password" /></label>
      <label>确认密码<el-input v-model="passwordForm.confirmPassword" type="password" show-password autocomplete="new-password" /></label>
    </form>
    <template #footer>
      <el-button v-if="!forcePasswordMode" @click="passwordVisible = false">取消</el-button>
      <el-button type="primary" :loading="passwordSaving" @click="changePassword">保存</el-button>
    </template>
  </el-dialog>
</template>
