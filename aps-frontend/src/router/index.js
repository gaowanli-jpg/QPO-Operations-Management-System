import { createRouter, createWebHistory } from 'vue-router'
import SyncView from '../views/SyncView.vue'
import TasksView from '../views/TasksView.vue'
import AssignedTasksView from '../views/AssignedTasksView.vue'
import PartInventoryView from '../views/PartInventoryView.vue'
import PartInventoryLocksView from '../views/PartInventoryLocksView.vue'
import PartProductionRequirementsView from '../views/PartProductionRequirementsView.vue'
import MaterialInventoryView from '../views/MaterialInventoryView.vue'
import MaterialLocksView from '../views/MaterialLocksView.vue'
import MaterialShortagesView from '../views/MaterialShortagesView.vue'
import DepartmentsView from '../views/DepartmentsView.vue'
import MachinesView from '../views/MachinesView.vue'
import ProcessesView from '../views/ProcessesView.vue'

const routes = [
  { path: '/', redirect: '/sync' },
  { path: '/sync', component: SyncView, meta: { title: 'ERP 同步' } },
  { path: '/tasks', redirect: '/assignment/pending' },
  { path: '/assignment', redirect: '/assignment/pending' },
  { path: '/assignment/pending', component: TasksView, meta: { title: '待分配' } },
  { path: '/assignment/assigned', component: AssignedTasksView, meta: { title: '已分配' } },
  { path: '/part-inventory', redirect: '/part-inventory/manage' },
  { path: '/part-inventory/manage', component: PartInventoryView, meta: { title: '零件库存维护' } },
  { path: '/part-inventory/locks', component: PartInventoryLocksView, meta: { title: '零件锁定记录' } },
  { path: '/part-inventory/requirements', component: PartProductionRequirementsView, meta: { title: '生产需求' } },
  { path: '/material-inventory', redirect: '/material-inventory/manage' },
  { path: '/material-inventory/manage', component: MaterialInventoryView, meta: { title: '材料库存维护' } },
  { path: '/material-inventory/locks', component: MaterialLocksView, meta: { title: '材料锁定记录' } },
  { path: '/material-inventory/shortages', component: MaterialShortagesView, meta: { title: '材料缺口' } },
  { path: '/departments', component: DepartmentsView, meta: { title: '部门管理' } },
  { path: '/processes', component: ProcessesView, meta: { title: '工艺管理' } },
  { path: '/machines', component: MachinesView, meta: { title: '机台能力' } }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

export default router
