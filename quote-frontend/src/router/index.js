import { createRouter, createWebHistory } from 'vue-router'
import MarketDashboard from '../components/MarketDashboard.vue'
import MarketRelease from '../components/MarketRelease.vue'
import QuoteProcess from '../components/QuoteProcess.vue'
import GlobalSetup from '../components/GlobalSetup.vue'
import ItemPricing from '../components/ItemPricing.vue'
import MaterialProcessManage from '../components/MaterialProcessManage.vue'
import OrderManage from '../components/OrderManage.vue'
import HistoryOrderManage from '../components/HistoryOrderManage.vue'
import LoginView from '../components/LoginView.vue'
import UserManage from '../components/UserManage.vue'
import QuoteTemplateManage from '../components/QuoteTemplateManage.vue'
import { useAuthStore } from '../stores/authStore'

const routes = [
  { path: '/login', component: LoginView },
  { path: '/', redirect: '/market' },
  { path: '/market', component: MarketDashboard, meta: { roles: ['MARKET', 'ADMIN'] } },
  { path: '/quote-process', component: QuoteProcess, meta: { roles: ['MARKET', 'ADMIN'] } },
  { path: '/release', component: MarketRelease, meta: { roles: ['MARKET', 'ADMIN'] } },
  { path: '/engineering', component: GlobalSetup, meta: { roles: ['ENGINEER', 'ADMIN'] } },
  { path: '/material-process', component: MaterialProcessManage, meta: { roles: ['ENGINEER', 'ADMIN'] } },
  { path: '/orders', component: OrderManage, meta: { roles: ['MARKET', 'ADMIN'] } },
  { path: '/history-orders', component: HistoryOrderManage, meta: { roles: ['MARKET', 'ADMIN'] } },
  { path: '/users', component: UserManage, meta: { roles: ['ADMIN'] } },
  { path: '/quote-template', component: QuoteTemplateManage, meta: { roles: ['ADMIN'] } },
  { path: '/pricing', component: ItemPricing, meta: { roles: ['ENGINEER', 'ADMIN'] } }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

const roleHome = {
  ADMIN: '/market',
  MARKET: '/market',
  ENGINEER: '/engineering'
}

router.beforeEach(async (to) => {
  const authStore = useAuthStore()
  if (to.path === '/login') {
    return true
  }
  if (!authStore.token) {
    return { path: '/login', query: { redirect: to.fullPath } }
  }
  const valid = await authStore.verifyAuth()
  if (!valid) {
    return { path: '/login', query: { redirect: to.fullPath } }
  }
  const roles = to.meta?.roles
  if (roles?.length && !authStore.hasRole(roles)) {
    return roleHome[authStore.user?.role] || '/login'
  }
  return true
})

export default router
