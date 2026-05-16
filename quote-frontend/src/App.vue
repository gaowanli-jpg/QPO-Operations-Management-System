<template>
  <el-config-provider>
    <router-view v-if="isLoginPage" />
    <div v-else class="erp-layout">
      <el-aside width="240px" class="sidebar">
        <div class="logo-box">
          <span class="logo-icon">ERP</span>
          <span class="logo-text">智能运营管理</span>
        </div>

        <el-menu
          :default-active="activeMenu"
          class="el-menu-vertical"
          background-color="#304156"
          text-color="#bfcbd9"
          active-text-color="#409EFF"
          router
        >
          <div class="menu-title">核心业务流</div>

          <el-menu-item v-if="canSee(['MARKET', 'ADMIN'])" index="/market">
            <el-icon><DataBoard /></el-icon>
            <span>市场部：新单导入中心</span>
          </el-menu-item>

          <el-menu-item v-if="canSee(['MARKET', 'ADMIN'])" index="/release">
            <el-icon><Tickets /></el-icon>
            <span>市场部：议价与提单</span>
          </el-menu-item>

          <el-menu-item v-if="canSee(['ENGINEER', 'ADMIN'])" index="/engineering">
            <el-icon><Setting /></el-icon>
            <span>工程部：工艺与核价</span>
          </el-menu-item>

          <el-menu-item v-if="canSee(['ENGINEER', 'ADMIN'])" index="/material-process">
            <el-icon><Tools /></el-icon>
            <span>工程部：材料与工艺管理</span>
          </el-menu-item>

          <el-menu-item v-if="canSee(['MARKET', 'ENGINEER', 'WAREHOUSE', 'ADMIN'])" index="/orders">
            <el-icon><Document /></el-icon>
            <span>订单管理</span>
          </el-menu-item>

          <el-menu-item v-if="canSee(['ADMIN'])" index="/history-orders">
            <el-icon><Files /></el-icon>
            <span>历史订单管理</span>
          </el-menu-item>

          <el-menu-item v-if="canSee(['WAREHOUSE', 'ADMIN'])" index="/warehouse">
            <el-icon><Box /></el-icon>
            <span>仓库部：库存管理</span>
          </el-menu-item>
        </el-menu>
      </el-aside>

      <el-container class="main-container">
        <el-header class="header">
          <div class="header-left">
            <el-icon class="collapse-btn"><Fold /></el-icon>
            <span class="breadcrumb">当前系统状态：正常运行</span>
          </div>
          <div class="header-right">
            <el-avatar size="small">{{ userInitial }}</el-avatar>
            <span class="username">{{ displayName }}（{{ roleName }}）</span>
            <el-button text type="primary" @click="logout">退出</el-button>
          </div>
        </el-header>

        <el-main class="app-main">
          <router-view v-slot="{ Component }">
            <transition name="fade-transform" mode="out-in">
              <component :is="Component" />
            </transition>
          </router-view>
        </el-main>
      </el-container>
    </div>
  </el-config-provider>
</template>

<script setup>
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import axios from 'axios'
import { DataBoard, Setting, Fold, Tickets, Box, Tools, Document, Files } from '@element-plus/icons-vue'
import { authState, clearAuth, hasRole } from './auth'
import { API_BASE_URL } from './config'

const route = useRoute()
const router = useRouter()

const activeMenu = computed(() => route.path)
const isLoginPage = computed(() => route.path === '/login')
const displayName = computed(() => authState.user?.displayName || authState.user?.username || '未登录')
const userInitial = computed(() => displayName.value.slice(0, 1).toUpperCase())
const roleName = computed(() => ({
  ADMIN: '管理员',
  MARKET: '市场部',
  ENGINEER: '工程部',
  WAREHOUSE: '仓库部'
}[authState.user?.role] || '未知角色'))

const canSee = (roles) => hasRole(roles)

const logout = async () => {
  try {
    await axios.post(`${API_BASE_URL}/api/auth/logout`)
  } catch {
    // 本地退出不依赖服务端响应。
  } finally {
    clearAuth()
    router.replace('/login')
  }
}
</script>

<style>
html, body, #app {
  margin: 0;
  padding: 0;
  height: 100%;
  font-family: 'Helvetica Neue', Helvetica, 'PingFang SC', 'Hiragino Sans GB', 'Microsoft YaHei', Arial, sans-serif;
}

.erp-layout {
  display: flex;
  height: 100vh;
  overflow: hidden;
}

.sidebar {
  background-color: #304156;
  transition: width 0.3s;
  box-shadow: 2px 0 6px rgba(0, 21, 41, .35);
  z-index: 1000;
  display: flex;
  flex-direction: column;
}

.logo-box {
  height: 60px;
  display: flex;
  align-items: center;
  justify-content: center;
  background-color: #2b3649;
  color: #fff;
  font-size: 19px;
  font-weight: 700;
}

.logo-icon {
  margin-right: 8px;
  font-size: 15px;
  border: 1px solid rgba(255, 255, 255, 0.5);
  border-radius: 4px;
  padding: 2px 5px;
}

.logo-text {
  color: #fff;
}

.el-menu-vertical {
  border-right: none !important;
  flex: 1;
}

.menu-title {
  color: #6e7a8b;
  font-size: 12px;
  padding: 15px 20px 5px;
  text-transform: uppercase;
}

.header {
  height: 60px;
  background-color: #fff;
  box-shadow: 0 1px 4px rgba(0, 21, 41, .08);
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 20px;
  z-index: 999;
}

.header-left,
.header-right {
  display: flex;
  align-items: center;
}

.collapse-btn {
  font-size: 20px;
  color: #606266;
  margin-right: 15px;
}

.breadcrumb {
  font-size: 14px;
  color: #909399;
}

.username {
  margin-left: 10px;
  margin-right: 10px;
  font-size: 14px;
  color: #606266;
  font-weight: 500;
}

.app-main {
  background-color: #f0f2f5;
  padding: 20px;
  overflow-y: auto;
}

.fade-transform-leave-active,
.fade-transform-enter-active {
  transition: all .3s;
}

.fade-transform-enter-from {
  opacity: 0;
  transform: translateX(-30px);
}

.fade-transform-leave-to {
  opacity: 0;
  transform: translateX(30px);
}
</style>
