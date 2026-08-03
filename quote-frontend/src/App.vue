<template>
  <el-config-provider>
    <router-view v-if="isLoginPage" />
    <div v-else class="erp-layout">
      <el-aside :width="sidebarWidth" class="sidebar" :class="{ 'sidebar-collapsed': isSidebarCollapsed }">
        <div class="logo-box">
          <span class="logo-icon">QPO</span>
          <span class="logo-text">QPO运营管理</span>
        </div>

        <el-menu
          :default-active="activeMenu"
          class="el-menu-vertical"
          :collapse="isSidebarCollapsed"
          :collapse-transition="false"
          router
        >
          <template v-for="group in visibleMenuGroups" :key="group.key">
            <div class="menu-section">{{ group.title }}</div>
            <el-menu-item v-for="item in group.items" :key="item.path" :index="item.path">
              <el-icon><component :is="item.icon" /></el-icon>
              <span>{{ item.title }}</span>
            </el-menu-item>
          </template>
        </el-menu>
      </el-aside>

      <el-container class="main-container">
        <el-header class="header">
          <div class="header-left">
            <button
              type="button"
              class="shell-icon-button"
              :aria-label="isSidebarCollapsed ? '展开侧边栏' : '收起侧边栏'"
              :title="isSidebarCollapsed ? '展开侧边栏' : '收起侧边栏'"
              @click="toggleSidebar"
            >
              <el-icon><component :is="isSidebarCollapsed ? Expand : Fold" /></el-icon>
            </button>
            <div class="header-copy">
              <div class="shell-title" :title="shellMeta.title">{{ shellMeta.title }}</div>
              <div class="shell-subtitle" :title="shellMeta.description">{{ shellMeta.description }}</div>
            </div>
          </div>
          <div class="header-right">
            <UserProfilePopover
              :logout-pending="logoutPending"
              @change-password="openPasswordDialog(false)"
              @logout="logout"
            />
          </div>
        </el-header>

        <el-main class="app-main">
          <router-view />
        </el-main>
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
        <el-form ref="passwordFormRef" :model="passwordForm" :rules="passwordRules" label-width="96px">
          <el-form-item label="原密码" prop="oldPassword">
            <el-input v-model="passwordForm.oldPassword" type="password" show-password autocomplete="current-password" />
          </el-form-item>
          <el-form-item label="新密码" prop="newPassword">
            <el-input v-model="passwordForm.newPassword" type="password" show-password autocomplete="new-password" />
          </el-form-item>
          <el-form-item label="确认密码" prop="confirmPassword">
            <el-input v-model="passwordForm.confirmPassword" type="password" show-password autocomplete="new-password" />
          </el-form-item>
        </el-form>
        <template #footer>
          <el-button v-if="!forcePasswordMode" @click="passwordVisible = false">取消</el-button>
          <el-button type="primary" :loading="passwordSaving" :disabled="passwordSaving" @click="changePassword">保存</el-button>
        </template>
      </el-dialog>
    </div>
  </el-config-provider>
</template>

<script setup>
import { computed, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { DataBoard, Document, Expand, Files, Fold, Setting, Tools, User } from '@element-plus/icons-vue'
import { useAuthStore } from './stores/authStore'
import UserProfilePopover from './components/UserProfilePopover.vue'

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()

const activeMenu = computed(() => {
  if (route.path === '/pricing') return '/engineering'
  return route.path
})
const isLoginPage = computed(() => route.path === '/login')
const isSidebarCollapsed = ref(false)
const sidebarWidth = computed(() => isSidebarCollapsed.value ? '72px' : '200px')
const mustChangePassword = computed(() => authStore.mustChangePassword)


const menuGroups = [
  { key: 'market', title: '市场', paths: ['/market', '/sales-match', '/quote-process', '/release', '/u9-sync', '/delivery-documents', '/quote-template'] },
  { key: 'engineer', title: '工程', paths: ['/engineering', '/material-process'] },
  { key: 'orders', title: '订单', paths: ['/orders', '/sales-orders', '/delivered-items', '/locked-items'] },
  { key: 'system', title: '系统', paths: ['/users', '/roles', '/organization', '/customers', '/audit-logs', '/system-config'] }
]

const menuMetaByPath = {
  '/market': { title: '新单导入', permission: 'market:rfq:view', icon: DataBoard },
  '/sales-match': { title: '销售匹配', permission: 'market:sales-match:view', icon: Files },
  '/quote-process': { title: '报价处理', permission: 'market:quote-process:view', icon: Document },
  '/release': { title: '议价提单', permission: 'market:release:view', icon: Files },
  '/u9-sync': { title: 'U9同步', permission: 'market:u9-sync:view', icon: Document },
  '/delivery-documents': { title: '提单表据', permission: 'market:delivery-document:view', icon: Document },
  '/quote-template': { title: '报价模板', permission: 'market:template:view', icon: Files },
  '/engineering': { title: '零件核价', permission: 'engineer:setup:view', icon: Setting },
  '/material-process': { title: '材料工艺', permission: 'engineer:dict:view', icon: Tools },
  '/orders': { title: '询价订单', permission: 'market:order:view', icon: Document },
  '/sales-orders': { title: '销售订单', permission: 'market:sales-order:view', icon: Files },
  '/delivered-items': { title: '已提零件', permission: 'market:delivered-item:view', icon: Files },
  '/locked-items': { title: '锁库零件', permission: 'market:locked-item:view', icon: Files },
  '/users': { title: '用户管理', permission: 'system:user:view', icon: User },
  '/roles': { title: '角色权限', permission: 'system:role:view', icon: User },
  '/organization': { title: '组织档案', permission: 'system:org:view', icon: Files },
  '/customers': { title: '客户资料', permission: 'system:customer:view', icon: User },
  '/audit-logs': { title: '操作日志', permission: 'system:audit:view', icon: Document },
  '/system-config': { title: '系统配置', permission: 'system:config:view', icon: Setting }
}

const flattenMenus = (menus = []) => menus.flatMap((menu) => [
  menu,
  ...flattenMenus(menu.children || [])
])

const backendMenuByPath = computed(() => Object.fromEntries(
  flattenMenus(authStore.menus).filter((menu) => menu.path).map((menu) => [menu.path, menu])
))

const visibleMenuGroups = computed(() => {
  const backendPaths = new Set(Object.keys(backendMenuByPath.value))
  const hasBackendMenus = backendPaths.size > 0

  return menuGroups
    .map((group) => ({
      ...group,
       items: group.paths
         .filter((path) => {
          if (hasBackendMenus) {
            return backendPaths.has(path)
          }
          return authStore.hasPermission(menuMetaByPath[path]?.permission)
        })
        .map((path) => {
          const meta = menuMetaByPath[path]
          return {
            path,
            title: meta.title,
            icon: meta.icon
          }
        })
    }))
    .filter((group) => group.items.length > 0)
})

const shellMetaByPath = {
  '/market': {
    title: '新单导入',
    description: '导入客户询价表和图纸，推送工程评估。'
  },
  '/sales-match': {
    title: '销售匹配',
    description: '选择销售订单，预览并补全历史生产快照。'
  },
  '/quote-process': {
    title: '报价处理',
    description: '维护报价交期，生成客户报价输出。'
  },
  '/release': {
    title: '议价提单',
    description: '处理议价结果并生成生产交付资料。'
  },
  '/u9-sync': {
    title: 'U9同步',
    description: '按销售订单整单预览和同步已确认物料号到 U9。'
  },
  '/delivery-documents': {
    title: '提单表据',
    description: '选择已提单订单，查看并导出已生成提单行和工艺表行。'
  },
  '/engineering': {
    title: '零件核价',
    description: '选择订单并配置零件默认材料、形态和工艺路线。'
  },
  '/pricing': {
    title: '逐件核价',
    description: '逐项完成材料、工艺和费用核算。'
  },
  '/material-process': {
    title: '材料工艺',
    description: '维护工程核价使用的材料、工艺和费用字典。'
  },
  '/orders': {
    title: '询价订单',
    description: '查看询价订单、图纸、提单和工艺表状态。'
  },
  '/sales-orders': {
    title: '销售订单',
    description: '查看销售订单、零件明细、提单行和工艺表状态。'
  },
  '/delivered-items': {
    title: '已提零件',
    description: '仅展示尚未完成物料号获取和锁库流程的提单零件。'
  },
  '/locked-items': {
    title: '锁库零件',
    description: '查看 WMS 返回后的库存锁定、在产锁定和待生产拆分明细。'
  },
  '/users': {
    title: '用户管理',
    description: '维护账号状态、角色和密码安全。'
  },
  '/roles': {
    title: '角色权限',
    description: '配置角色启停、功能授权和数据范围。'
  },
  '/organization': {
    title: '组织档案',
    description: '维护部门和岗位这两类用户身份属性。'
  },
  '/customers': {
    title: '客户资料',
    description: '维护客户编码、简称和名称，作为全系统客户选择来源。'
  },
  '/audit-logs': {
    title: '操作日志',
    description: '追踪账号、报价、订单、字典和系统配置的关键变更。'
  },
  '/system-config': {
    title: '系统配置',
    description: '管理文件存储策略和 MinIO 连接。'
  },
  '/quote-template': {
    title: '报价模板',
    description: '维护客户报价单导出模板。'
  }
}

const shellMeta = computed(() => shellMetaByPath[route.path] || {
  title: 'QPO运营管理',
  description: '当前系统状态：正常运行'
})

const passwordVisible = ref(false)
const forcePasswordMode = ref(false)
const passwordSaving = ref(false)
const logoutPending = ref(false)
const passwordFormRef = ref()
const passwordForm = reactive({
  oldPassword: '',
  newPassword: '',
  confirmPassword: ''
})

const validateConfirmPassword = (_rule, value, callback) => {
  if (value !== passwordForm.newPassword) {
    callback(new Error('两次输入的新密码不一致'))
    return
  }
  callback()
}

const passwordRules = {
  oldPassword: [{ required: true, message: '请输入原密码', trigger: 'blur' }],
  newPassword: [
    { required: true, message: '请输入新密码', trigger: 'blur' },
    { min: 8, message: '新密码至少 8 位', trigger: 'blur' }
  ],
  confirmPassword: [
    { required: true, message: '请确认新密码', trigger: 'blur' },
    { validator: validateConfirmPassword, trigger: 'blur' }
  ]
}


const toggleSidebar = () => {
  isSidebarCollapsed.value = !isSidebarCollapsed.value
}

const resetPasswordForm = () => {
  passwordForm.oldPassword = ''
  passwordForm.newPassword = ''
  passwordForm.confirmPassword = ''
  passwordFormRef.value?.clearValidate()
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
  if (passwordSaving.value) return
  await passwordFormRef.value?.validate()
  passwordSaving.value = true
  try {
    await authStore.changePassword({
      oldPassword: passwordForm.oldPassword,
      newPassword: passwordForm.newPassword,
      confirmPassword: passwordForm.confirmPassword
    })
    ElMessage.success('密码已修改')
    passwordVisible.value = false
    forcePasswordMode.value = false
    resetPasswordForm()
  } catch (error) {
    ElMessage.error(error.response?.data?.message || error.message || '密码修改失败')
  } finally {
    passwordSaving.value = false
  }
}

const logout = async () => {
  if (logoutPending.value) return
  logoutPending.value = true
  try {
    await authStore.logout()
    router.replace('/login')
  } finally {
    logoutPending.value = false
  }
}
</script>

<style>
html, body, #app {
  margin: 0;
  padding: 0;
  height: 100%;
  font-family: var(--erp-font-sans);
}

.erp-layout {
  display: flex;
  height: 100vh;
  overflow: hidden;
  background: var(--erp-bg);
}

.sidebar {
  z-index: var(--erp-z-sticky);
  display: flex;
  flex-direction: column;
  overflow: hidden;
  background: var(--erp-rail);
  box-shadow: none;
  transition: width var(--erp-duration-base) var(--erp-ease-standard);
}

.logo-box {
  display: flex;
  align-items: center;
  gap: var(--erp-space-3);
  min-height: 72px;
  padding: 0 var(--erp-space-4);
  color: var(--erp-rail-text);
  border-bottom: 1px solid var(--erp-rail-border);
  transition:
    gap var(--erp-duration-base) var(--erp-ease-standard),
    padding var(--erp-duration-base) var(--erp-ease-standard);
}

.logo-icon {
  display: inline-grid;
  place-items: center;
  width: 42px;
  height: 42px;
  flex: 0 0 42px;
  padding: 0;
  color: var(--erp-rail);
  font-size: var(--erp-font-size-sm);
  font-weight: var(--erp-font-weight-bold);
  background: var(--erp-accent);
  border: 0;
  border-radius: var(--erp-radius-lg);
}

.logo-text {
  min-width: 0;
  color: var(--erp-rail-text);
  font-size: var(--erp-font-size-lg);
  font-weight: var(--erp-font-weight-bold);
  line-height: var(--erp-line-height-tight);
}

.sidebar-collapsed .logo-box {
  justify-content: center;
  gap: 0;
  padding: 0;
}

.sidebar-collapsed .logo-text,
.sidebar-collapsed .menu-section {
  display: none;
}

.el-menu-vertical {
  flex: 1;
  padding: var(--erp-space-3) var(--erp-space-3) var(--erp-space-4);
  border-right: none !important;
  overflow-y: auto;
  background: var(--erp-rail) !important;
  transition: padding var(--erp-duration-base) var(--erp-ease-standard);
}

.sidebar-collapsed .el-menu-vertical {
  width: 100% !important;
  padding: var(--erp-space-3) var(--erp-space-2) var(--erp-space-4);
}

.menu-section {
  padding: var(--erp-space-4) var(--erp-space-3) var(--erp-space-1);
  color: var(--erp-rail-muted);
  font-size: var(--erp-font-size-xs);
  font-weight: var(--erp-font-weight-bold);
  line-height: var(--erp-line-height-tight);
}

.menu-section:first-child {
  padding-top: var(--erp-space-1);
}

.sidebar .el-menu-item {
  position: relative;
  height: 40px;
  min-height: 40px;
  margin: var(--erp-space-1) 0;
  padding: 0 var(--erp-space-3) !important;
  color: var(--erp-rail-text) !important;
  border-radius: var(--erp-radius-lg);
  background: transparent !important;
  transition:
    background-color var(--erp-duration-fast) var(--erp-ease-standard),
    color var(--erp-duration-fast) var(--erp-ease-standard);
}

.sidebar-collapsed .el-menu-item,
.sidebar-collapsed .el-menu-tooltip__trigger {
  justify-content: center;
  padding: 0 !important;
}

.sidebar .el-menu-item:hover {
  color: var(--erp-rail-active) !important;
  background: var(--erp-rail-hover) !important;
}

.sidebar .el-menu-item.is-active {
  color: var(--erp-rail-active) !important;
  background: var(--erp-rail-active-bg) !important;
  font-weight: var(--erp-font-weight-bold);
}

.sidebar .el-menu-item.is-active::before {
  width: 3px;
  height: 18px;
  margin-right: var(--erp-space-2);
  background: var(--erp-accent);
  border-radius: var(--erp-radius-full);
  content: '';
}

.sidebar .el-menu-item .el-icon {
  margin-right: var(--erp-space-2);
  color: inherit;
}

.sidebar-collapsed .el-menu-item.is-active::before {
  position: absolute;
  left: 0;
  margin-right: 0;
}

.sidebar-collapsed .el-menu-item .el-icon {
  margin-right: 0;
}

.sidebar-collapsed .el-menu-item span {
  display: none;
}

.main-container {
  min-width: 0;
  flex: 1;
}

.header {
  z-index: var(--erp-z-sticky);
  display: flex;
  align-items: center;
  justify-content: space-between;
  height: 64px;
  padding: 0 var(--erp-space-5);
  background: var(--erp-surface);
  border-bottom: 1px solid var(--erp-border);
  box-shadow: var(--erp-shadow-none);
}

.header-left,
.header-right {
  display: flex;
  align-items: center;
  min-width: 0;
}

.header-left {
  gap: var(--erp-space-3);
}

.header-right {
  gap: var(--erp-space-3);
  justify-content: flex-end;
}

.shell-icon-button {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 36px;
  height: 36px;
  flex: 0 0 36px;
  color: var(--erp-text-secondary);
  background: transparent;
  border: 0;
  border-radius: var(--erp-radius-lg);
  cursor: pointer;
  transition:
    background-color var(--erp-duration-fast) var(--erp-ease-standard),
    color var(--erp-duration-fast) var(--erp-ease-standard);
}

.shell-icon-button:hover {
  color: var(--erp-primary);
  background: var(--erp-primary-soft);
}

.shell-icon-button:focus-visible {
  outline: 2px solid var(--erp-primary);
  outline-offset: 2px;
}

.header-copy {
  min-width: 0;
  overflow: hidden;
}

.shell-title {
  overflow: hidden;
  color: var(--erp-text);
  font-size: var(--erp-font-size-lg);
  font-weight: var(--erp-font-weight-bold);
  line-height: var(--erp-line-height-tight);
  text-overflow: ellipsis;
  white-space: nowrap;
}

.shell-subtitle {
  overflow: hidden;
  margin-top: 2px;
  color: var(--erp-text-secondary);
  font-size: var(--erp-font-size-xs);
  line-height: var(--erp-line-height-tight);
  text-overflow: ellipsis;
  white-space: nowrap;
}

.app-main {
  min-width: 0;
  background: var(--erp-bg);
  padding: var(--erp-space-6);
  overflow-y: auto;
}

.password-alert {
  margin-bottom: var(--erp-space-4);
}

@media (max-width: 960px) {
  .header {
    height: auto;
    min-height: 64px;
    align-items: flex-start;
    flex-direction: column;
    gap: var(--erp-space-2);
    padding: var(--erp-space-3) var(--erp-space-4);
  }

  .header-right {
    width: 100%;
    justify-content: space-between;
  }

  .app-main {
    padding: var(--erp-space-4);
  }
}
</style>
