import { createApp } from 'vue'
import ElementPlus from 'element-plus'
import zhCn from 'element-plus/es/locale/lang/zh-cn'
import 'element-plus/dist/index.css'
import './style.css'
import * as ElementPlusIconsVue from '@element-plus/icons-vue'
import { createPinia } from 'pinia'
import App from './App.vue'
import router from './router'
import { configureApiAuth } from './api'
import { useAuthStore } from './stores/authStore'

const app = createApp(App)
const pinia = createPinia()

for (const [key, component] of Object.entries(ElementPlusIconsVue)) {
  app.component(key, component)
}

app.use(pinia)

const authStore = useAuthStore()

configureApiAuth({
  getToken: () => authStore.token,
  onUnauthorized: () => {
    authStore.clearAuth()
    if (router.currentRoute.value.path !== '/login') {
      router.replace({ path: '/login', query: { redirect: router.currentRoute.value.fullPath } })
    }
  }
})


const applyPermissionVisibility = (el, binding) => {
  const value = binding.value
  const allowed = Array.isArray(value)
    ? authStore.hasAnyPermission(value)
    : authStore.hasPermission(value)

  if (!el.dataset.permissionDisplay) {
    el.dataset.permissionDisplay = el.style.display || '__empty__'
  }

  el.style.display = allowed
    ? (el.dataset.permissionDisplay === '__empty__' ? '' : el.dataset.permissionDisplay)
    : 'none'
}

app.directive('permission', {
  mounted: applyPermissionVisibility,
  updated: applyPermissionVisibility
})

app.use(router)
app.use(ElementPlus, { locale: zhCn })

app.mount('#app')
