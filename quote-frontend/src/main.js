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
  },
  onPasswordChangeRequired: () => authStore.updateUser({ forcePasswordChange: true })
})

app.use(router)
app.use(ElementPlus, { locale: zhCn })

app.mount('#app')
