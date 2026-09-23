import { createRouter, createWebHistory } from 'vue-router'
import LoginView from '../components/LoginView.vue'
import { useAuthStore } from '../stores/authStore'

const DataCenterSsoCallback = () => import('../views/DataCenterSsoCallback.vue')
const DashboardHome = () => import('../components/dashboard/DashboardHome.vue')
const MarketDashboard = () => import('../components/MarketDashboard.vue')
const MarketRelease = () => import('../components/MarketRelease.vue')
const ReleaseAppendManage = () => import('../components/ReleaseAppendManage.vue')
const SalesOrderU9Sync = () => import('../components/SalesOrderU9Sync.vue')
const DeliveryDocumentManage = () => import('../components/DeliveryDocumentManage.vue')
const QuoteProcess = () => import('../components/QuoteProcess.vue')
const GlobalSetup = () => import('../components/GlobalSetup.vue')
const ItemPricing = () => import('../components/ItemPricing.vue')
const MaterialProcessManage = () => import('../components/MaterialProcessManage.vue')
const OrderManage = () => import('../components/OrderManage.vue')
const SalesOrderManage = () => import('../components/SalesOrderManage.vue')
const PartSearchManage = () => import('../components/PartSearchManage.vue')
const DeliveredItemManage = () => import('../components/DeliveredItemManage.vue')
const ProductionItemManage = () => import('../components/ProductionItemManage.vue')
const LockedItemManage = () => import('../components/LockedItemManage.vue')
const SalesOrderChangeManage = () => import('../components/SalesOrderChangeManage.vue')
const LinkedChangeManage = () => import('../components/LinkedChangeManage.vue')
const UserManage = () => import('../components/UserManage.vue')
const SystemRoleManage = () => import('../components/SystemRoleManage.vue')
const SystemOrgManage = () => import('../components/SystemOrgManage.vue')
const CustomerManage = () => import('../components/CustomerManage.vue')
const QuoteTemplateManage = () => import('../components/QuoteTemplateManage.vue')
const AuditLogManage = () => import('../components/AuditLogManage.vue')
const SystemConfigManage = () => import('../components/SystemConfigManage.vue')

const routes = [
  { path: '/login', component: LoginView },
  { path: '/sso/datacenter/callback', component: DataCenterSsoCallback },
  { path: '/', redirect: '/dashboard' },
  { path: '/dashboard', component: DashboardHome },
  { path: '/market', component: MarketDashboard, meta: { permission: 'market:rfq:view' } },
  { path: '/quote-process', component: QuoteProcess, meta: { permission: 'market:quote-process:view' } },
  { path: '/release', component: MarketRelease, meta: { permission: 'market:release:view' } },
  { path: '/release-append', component: ReleaseAppendManage, meta: { permission: 'market:release-append:view' } },
  { path: '/u9-sync', component: SalesOrderU9Sync, meta: { permission: 'market:u9-sync:view' } },
  { path: '/delivery-documents', component: DeliveryDocumentManage, meta: { permission: 'market:delivery-document:view' } },
  { path: '/engineering', component: GlobalSetup, meta: { permission: 'engineer:setup:view' } },
  { path: '/material-process', component: MaterialProcessManage, meta: { permission: 'engineer:dict:view' } },
  { path: '/orders', component: OrderManage, meta: { permission: 'market:order:view' } },
  { path: '/sales-orders', component: SalesOrderManage, meta: { permission: 'market:sales-order:view' } },
  { path: '/part-search', component: PartSearchManage, meta: { permission: 'market:part-search:view' } },
  { path: '/history-orders', redirect: '/sales-orders' },
  { path: '/delivered-items', component: DeliveredItemManage, meta: { permission: 'market:delivered-item:view' } },
  { path: '/production-items', component: ProductionItemManage, meta: { permission: 'market:production-item:view' } },
  { path: '/locked-items', component: LockedItemManage, meta: { permission: 'market:locked-item:view' } },
  { path: '/linked-changes', component: LinkedChangeManage, meta: { permission: 'change:linked-change:view' } },
  { path: '/sales-order-change', component: SalesOrderChangeManage, meta: { permission: 'change:sales-order:view' } },
  { path: '/users', component: UserManage, meta: { permission: 'system:user:view' } },
  { path: '/roles', component: SystemRoleManage, meta: { permission: 'system:role:view' } },
  { path: '/organization', component: SystemOrgManage, meta: { permission: 'system:org:view' } },
  { path: '/customers', component: CustomerManage, meta: { permission: 'system:customer:view' } },
  { path: '/audit-logs', component: AuditLogManage, meta: { permission: 'system:audit:view' } },
  { path: '/system-config', component: SystemConfigManage, meta: { permission: 'system:config:view' } },
  { path: '/quote-template', component: QuoteTemplateManage, meta: { permission: 'market:template:view' } },
  { path: '/pricing', component: ItemPricing, meta: { permission: 'engineer:pricing:view' } }
]

const PUBLIC_ROUTES = ['/login', '/sso/datacenter/callback']

const router = createRouter({
  history: createWebHistory(),
  routes
})

const flattenMenus = (menus = []) => menus.flatMap((menu) => [
  menu,
  ...flattenMenus(menu.children || [])
])

const firstPermittedPath = (authStore) => {
  const menuPath = flattenMenus(authStore.menus)
    .map((menu) => menu.path)
    .find(Boolean)
  if (menuPath) {
    return menuPath
  }

  const route = routes.find((item) => {
    const permission = item.meta?.permission
    return permission && authStore.hasPermission(permission)
  })

  return route?.path || '/login'
}

router.beforeEach(async (to) => {
  const authStore = useAuthStore()
  if (PUBLIC_ROUTES.includes(to.path)) {
    return true
  }
  if (!authStore.token) {
    return { path: '/login', query: { redirect: to.fullPath } }
  }
  const valid = await authStore.verifyAuth()
  if (!valid) {
    return { path: '/login', query: { redirect: to.fullPath } }
  }
  if (authStore.permissions.length === 0 || authStore.menus.length === 0) {
    await authStore.loadPermissionInfo()
  }
  const permission = to.meta?.permission
  if (permission && !authStore.hasPermission(permission)) {
    return firstPermittedPath(authStore)
  }
  return true
})

export default router
