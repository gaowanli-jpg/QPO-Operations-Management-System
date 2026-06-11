<script setup>
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import { Box, Cpu, DataBoard, Document, OfficeBuilding, SetUp, Tickets, Tools, User } from '@element-plus/icons-vue'

const route = useRoute()

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
const defaultOpeneds = menus
  .filter((menu) => menu.children)
  .map((menu) => menu.path)
</script>

<template>
  <el-container class="aps-layout">
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
      </el-header>

      <el-main class="workspace">
        <router-view />
      </el-main>
    </el-container>
  </el-container>
</template>
