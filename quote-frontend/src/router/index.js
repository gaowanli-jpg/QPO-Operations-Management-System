import { createRouter, createWebHistory } from 'vue-router'
import LoginView from '../components/LoginView.vue'
import { useAuthStore } from '../stores/authStore'

const MarketDashboard = () => import('../components/MarketDashboard.vue')
const MarketRelease = () => import('../components/MarketRelease.vue')
const QuoteProcess = () => import('../components/QuoteProcess.vue')
const GlobalSetup = () => import('../components/GlobalSetup.vue')
const ItemPricing = () => import('../components/ItemPricing.vue')
const MaterialProcessManage = () => import('../components/MaterialProcessManage.vue')
const OrderManage = () => import('../components/OrderManage.vue')
const HistoryOrderManage = () => import('../components/HistoryOrderManage.vue')
const UserManage = () => import('../components/UserManage.vue')
const QuoteTemplateManage = () => import('../components/QuoteTemplateManage.vue')
const AuditLogManage = () => import('../components/AuditLogManage.vue')

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
  { path: '/audit-logs', component: AuditLogManage, meta: { roles: ['ADMIN'] } },
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
