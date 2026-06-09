<script setup>
import { computed } from 'vue'
import { useRoute } from 'vue-router'

const route = useRoute()

const menus = [
  { path: '/sync', label: 'ERP 同步' },
  {
    path: '/assignment',
    label: '分配中心',
    children: [
      { path: '/assignment/pending', label: '待分配' },
      { path: '/assignment/assigned', label: '已分配' }
    ]
  },
  {
    path: '/part-inventory',
    label: '零件库存',
    children: [
      { path: '/part-inventory/manage', label: '零件库存维护' },
      { path: '/part-inventory/locks', label: '零件锁定记录' },
      { path: '/part-inventory/requirements', label: '生产需求' }
    ]
  },
  { path: '/material-inventory', label: '材料库存' },
  { path: '/departments', label: '部门管理' },
  { path: '/processes', label: '工艺管理' },
  { path: '/machines', label: '机台能力' }
]

const pageTitle = computed(() => route.meta?.title || 'APS')
const isMenuActive = (menu) => menu.children
  ? menu.children.some((child) => route.path === child.path)
  : route.path === menu.path
</script>

<template>
  <main class="shell">
    <aside class="rail">
      <div class="brand">
        <span class="brand-mark">APS</span>
        <span>生产准备中心</span>
      </div>
      <nav aria-label="APS 模块">
        <template v-for="menu in menus" :key="menu.path">
          <RouterLink
            v-if="!menu.children"
            :to="menu.path"
            :class="{ active: isMenuActive(menu) }"
          >
            {{ menu.label }}
          </RouterLink>
          <div v-else class="nav-group" :class="{ open: isMenuActive(menu) }">
            <RouterLink :to="menu.children[0].path" class="nav-group-title" :class="{ active: isMenuActive(menu) }">
              {{ menu.label }}
            </RouterLink>
            <div class="nav-children">
              <RouterLink
                v-for="child in menu.children"
                :key="child.path"
                :to="child.path"
                :class="{ active: route.path === child.path }"
              >
                {{ child.label }}
              </RouterLink>
            </div>
          </div>
        </template>
      </nav>
    </aside>

    <section class="workspace">
      <header class="topbar">
        <div>
          <p class="eyebrow">APS</p>
          <h1>{{ pageTitle }}</h1>
        </div>
      </header>

      <router-view />
    </section>
  </main>
</template>
