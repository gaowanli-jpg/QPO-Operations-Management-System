import { createRouter, createWebHistory } from 'vue-router'
import SyncView from '../views/SyncView.vue'
import TasksView from '../views/TasksView.vue'
import AssignedTasksView from '../views/AssignedTasksView.vue'
import DepartmentCenterView from '../views/DepartmentCenterView.vue'
import PartInventoryView from '../views/PartInventoryView.vue'
import PartInventoryLocksView from '../views/PartInventoryLocksView.vue'
import PartProductionRequirementsView from '../views/PartProductionRequirementsView.vue'
import MaterialInventoryView from '../views/MaterialInventoryView.vue'
import MaterialLocksView from '../views/MaterialLocksView.vue'
import MaterialShortagesView from '../views/MaterialShortagesView.vue'
import DepartmentsView from '../views/DepartmentsView.vue'
import MachinesView from '../views/MachinesView.vue'
import ProcessesView from '../views/ProcessesView.vue'
import UsersView from '../views/UsersView.vue'
import LoginView from '../views/LoginView.vue'
import { authState, hasRole, verifyAuth } from '../auth'

const routes = [
  { path: '/login', component: LoginView },
  { path: '/', redirect: '/sync' },
  { path: '/sync', component: SyncView, meta: { title: 'ERP 同步', roles: ['ADMIN'] } },
  { path: '/tasks', redirect: '/assignment/pending' },
  { path: '/assignment', redirect: '/assignment/pending' },
  { path: '/assignment/pending', component: TasksView, meta: { title: '待分配', roles: ['ADMIN'] } },
  { path: '/assignment/assigned', component: AssignedTasksView, meta: { title: '已分配', roles: ['ADMIN'] } },
  { path: '/department-center', component: DepartmentCenterView, meta: { title: '部门中心', roles: ['ADMIN'] } },
  { path: '/part-inventory', redirect: '/part-inventory/manage' },
  { path: '/part-inventory/manage', component: PartInventoryView, meta: { title: '零件库存维护', roles: ['ADMIN'] } },
  { path: '/part-inventory/locks', component: PartInventoryLocksView, meta: { title: '零件锁定记录', roles: ['ADMIN'] } },
  { path: '/part-inventory/requirements', component: PartProductionRequirementsView, meta: { title: '生产需求', roles: ['ADMIN'] } },
  { path: '/material-inventory', redirect: '/material-inventory/manage' },
  { path: '/material-inventory/manage', component: MaterialInventoryView, meta: { title: '材料库存维护', roles: ['ADMIN'] } },
  { path: '/material-inventory/locks', component: MaterialLocksView, meta: { title: '材料锁定记录', roles: ['ADMIN'] } },
  { path: '/material-inventory/shortages', component: MaterialShortagesView, meta: { title: '材料缺口', roles: ['ADMIN'] } },
  { path: '/departments', component: DepartmentsView, meta: { title: '部门管理', roles: ['ADMIN'] } },
  { path: '/processes', component: ProcessesView, meta: { title: '工艺管理', roles: ['ADMIN'] } },
  { path: '/machines', component: MachinesView, meta: { title: '机台能力', roles: ['ADMIN'] } },
  { path: '/users', component: UsersView, meta: { title: '用户权限', roles: ['ADMIN'] } }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

router.beforeEach(async (to) => {
  if (to.path === '/login') {
    return true
  }
  if (!authState.token) {
    return { path: '/login', query: { redirect: to.fullPath } }
  }
  const valid = await verifyAuth()
  if (!valid) {
    return { path: '/login', query: { redirect: to.fullPath } }
  }
  const roles = to.meta?.roles
  if (roles?.length && !hasRole(roles)) {
    return '/sync'
  }
  return true
})

export default router
