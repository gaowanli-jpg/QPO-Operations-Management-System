<template>
  <div class="erp-page role-manage-page">
    <header class="erp-page-header">
      <div>
        <h1 class="erp-page-title">角色管理</h1>
        <p class="erp-page-description">配置角色启停、权限授权和数据范围入口。</p>
      </div>
      <div class="erp-page-actions">
        <el-button v-permission="'system:role:create'" type="primary" @click="openCreate">新增角色</el-button>
      </div>
    </header>

    <section class="erp-toolbar role-toolbar">
      <div class="erp-toolbar-group">
        <strong>角色列表</strong>
        <span class="role-toolbar-note">禁用角色后，该角色不再为用户贡献权限。</span>
      </div>
    </section>

    <section class="erp-table-surface role-table-surface">
      <div class="erp-table-body">
        <el-table :data="roles" border v-loading="loading">
          <el-table-column prop="roleCode" label="角色编码" min-width="150" show-overflow-tooltip />
          <el-table-column prop="roleName" label="角色名称" min-width="150" show-overflow-tooltip />
          <el-table-column prop="description" label="说明" min-width="220" show-overflow-tooltip />
          <el-table-column label="数据范围" width="130">
            <template #default="{ row }">{{ dataScopeLabel(row.dataScope) }}</template>
          </el-table-column>
          <el-table-column label="状态" width="110">
            <template #default="{ row }">
              <el-tag :type="row.enabled ? 'success' : 'info'">{{ row.enabled ? '启用' : '禁用' }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="340" fixed="right">
            <template #default="{ row }">
              <div class="erp-table-actions">
                <el-button v-permission="'system:role:update'" size="small" :disabled="isRoleRowActionPending(row)" @click="openEdit(row)">编辑</el-button>
                <el-button v-permission="'system:role:assign-permission'" size="small" :disabled="isRoleRowActionPending(row)" @click="openPermissionDrawer(row)">授权</el-button>
                <el-button v-permission="'system:role:assign-data-scope'" size="small" :disabled="isRoleRowActionPending(row)" @click="openDataScopeDrawer(row)">数据范围</el-button>
                <el-button
                  v-permission="'system:role:update'"
                  size="small"
                  :type="row.enabled ? 'warning' : 'success'"
                  :loading="isRoleStatusPending(row)"
                  :disabled="isRoleRowActionPending(row)"
                  @click="toggleRole(row)"
                >
                  {{ row.enabled ? '禁用' : '启用' }}
                </el-button>
                <el-button
                  v-permission="'system:role:delete'"
                  size="small"
                  type="danger"
                  plain
                  class="erp-danger-action"
                  :loading="isRoleDeletePending(row)"
                  :disabled="row.superAdmin || isRoleRowActionPending(row)"
                  @click="removeRole(row)"
                >
                  删除
                </el-button>
              </div>
            </template>
          </el-table-column>
        </el-table>
      </div>
    </section>

    <el-dialog v-model="dialogVisible" :title="form.id ? '编辑角色' : '新增角色'" width="520px">
      <el-form :model="form" label-width="96px" class="erp-dialog-form">
        <el-form-item label="角色编码">
          <el-input v-model.trim="form.roleCode" :disabled="!!form.id" placeholder="如 MARKET_MANAGER" />
        </el-form-item>
        <el-form-item label="角色名称">
          <el-input v-model.trim="form.roleName" />
        </el-form-item>
        <el-form-item label="说明">
          <el-input v-model.trim="form.description" type="textarea" :rows="3" />
        </el-form-item>
        <el-form-item label="数据范围">
          <el-select v-model="form.dataScope" style="width: 100%">
            <el-option-group v-for="group in dataScopeOptionGroups" :key="group.label" :label="group.label">
              <el-option v-for="item in group.options" :key="item.value" :label="item.label" :value="item.value" />
            </el-option-group>
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-switch v-model="form.enabled" active-text="启用" inactive-text="禁用" />
        </el-form-item>
      </el-form>
      <template #footer>
        <div class="erp-dialog-footer">
          <el-button @click="dialogVisible = false">取消</el-button>
          <el-button v-permission="['system:role:create', 'system:role:update']" type="primary" :loading="isPending(roleActionKeys.saveDialog)" :disabled="isPending(roleActionKeys.saveDialog)" @click="saveRoleForm">保存</el-button>
        </div>
      </template>
    </el-dialog>

    <el-drawer v-model="permissionDrawerVisible" title="角色授权" size="720px">
      <div class="role-drawer-body">
        <p class="role-drawer-help">勾选该角色拥有的菜单、按钮和接口权限。</p>
        <div class="permission-tree-toolbar">
          <el-input
            v-model.trim="permissionTreeSearch"
            clearable
            placeholder="搜索权限名称或编码"
            class="permission-tree-search"
          />
          <el-switch v-model="showSelectedOnly" active-text="只看已选" />
          <div class="permission-tree-actions">
            <el-button size="small" @click="expandPermissionTree">展开全部</el-button>
            <el-button size="small" @click="collapsePermissionTree">收起全部</el-button>
          </div>
        </div>
        <el-tree
          ref="permissionTreeRef"
          class="permission-tree"
          :data="permissionTree"
          node-key="key"
          show-checkbox
          default-expand-all
          :props="permissionTreeProps"
          :filter-node-method="filterPermissionNode"
          @check="handlePermissionTreeCheck"
        >
          <template #default="{ data }">
            <div class="permission-tree-node" :class="{ 'permission-tree-group': isPermissionGroup(data) }">
              <span class="permission-tree-name">{{ data.label || data.permissionName || data.permissionCode }}</span>
              <el-tag v-if="data.permissionType" size="small" effect="plain">{{ permissionTypeLabel(data.permissionType) }}</el-tag>
              <el-tooltip v-if="data.permissionCode" :content="data.permissionCode" placement="top">
                <span class="permission-code">{{ data.permissionCode }}</span>
              </el-tooltip>
            </div>
          </template>
        </el-tree>
      </div>
      <template #footer>
        <div class="erp-dialog-footer">
          <el-button @click="permissionDrawerVisible = false">取消</el-button>
          <el-button v-permission="'system:role:assign-permission'" type="primary" :loading="permissionDrawerSavePending" :disabled="permissionDrawerSavePending" @click="savePermissions">保存授权</el-button>
        </div>
      </template>
    </el-drawer>

    <el-drawer v-model="dataScopeDrawerVisible" title="数据范围" size="560px">
      <div class="role-drawer-body">
        <el-alert
          title="QPO 数据范围只保留全部数据、指定客户、仅本人、无数据；推荐使用全部数据或指定客户。"
          type="info"
          show-icon
          :closable="false"
        />
        <el-form label-width="96px" class="erp-dialog-form">
          <el-form-item label="范围类型">
            <el-select v-model="dataScopeForm.dataScope" style="width: 100%">
              <el-option v-for="item in dataScopeOptions" :key="item.value" :label="item.label" :value="item.value" />
            </el-select>
          </el-form-item>
          <el-alert
            v-if="dataScopeForm.dataScope === 'SELF'"
            title="仅查看当前用户导入/创建的订单。"
            type="info"
            show-icon
            :closable="false"
          />
          <el-form-item v-if="isCustomerDataScope" label="指定客户">
            <p class="data-scope-help">指定客户数据范围只按订单客户名称匹配。</p>
            <el-select
              v-model="dataScopeForm.customerNames"
              multiple
              filterable
              clearable
              collapse-tags
              collapse-tags-tooltip
              placeholder="请选择客户"
              style="width: 100%"
            >
              <el-option v-for="customer in customerOptions" :key="customer" :label="customer" :value="customer" />
            </el-select>
          </el-form-item>
        </el-form>
      </div>
      <template #footer>
        <div class="erp-dialog-footer">
          <el-button @click="dataScopeDrawerVisible = false">取消</el-button>
          <el-button v-permission="'system:role:assign-data-scope'" type="primary" :loading="dataScopeDrawerSavePending" :disabled="dataScopeDrawerSavePending" @click="saveDataScope">保存数据范围</el-button>
        </div>
      </template>
    </el-drawer>
  </div>
</template>

<script setup>
import { computed, nextTick, onMounted, reactive, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  assignRoleDataScope,
  assignRolePermissions,
  deleteRole,
  getRoleDataScope,
  getRolePermissions,
  listDataScopeCustomers,
  listPermissionTree,
  listRoles,
  saveRole
} from '../services/adminApi'
import { usePendingAction } from '../composables/usePendingAction'

const roles = ref([])
const permissionTree = ref([])
const loading = ref(false)
const dialogVisible = ref(false)
const permissionDrawerVisible = ref(false)
const dataScopeDrawerVisible = ref(false)
const currentRole = ref(null)
const permissionCodes = ref([])
const permissionTreeSearch = ref('')
const showSelectedOnly = ref(false)
const customerOptions = ref([])
const permissionTreeRef = ref(null)
const permissionTreeProps = { label: 'label', children: 'children', disabled: 'disabled' }
const { isPending, runAction } = usePendingAction()

const roleActionKeys = {
  saveDialog: 'system:role:save-dialog',
  permissionsDrawer: (roleId) => `system:role:permissions-drawer:${roleId || 'none'}`,
  dataScopeDrawer: (roleId) => `system:role:data-scope-drawer:${roleId || 'none'}`,
  status: (roleId) => `system:role:status:${roleId || 'none'}`,
  delete: (roleId) => `system:role:delete:${roleId || 'none'}`
}

const permissionDrawerSavePending = computed(() => isPending(roleActionKeys.permissionsDrawer(currentRole.value?.id)))
const dataScopeDrawerSavePending = computed(() => isPending(roleActionKeys.dataScopeDrawer(currentRole.value?.id)))
const isRoleStatusPending = (row) => isPending(roleActionKeys.status(row?.id))
const isRoleDeletePending = (row) => isPending(roleActionKeys.delete(row?.id))
const isRoleRowActionPending = (row) => isRoleStatusPending(row) || isRoleDeletePending(row)

const form = reactive({
  id: null,
  roleCode: '',
  roleName: '',
  description: '',
  dataScope: 'ALL',
  enabled: true
})

const dataScopeForm = reactive({
  dataScope: 'ALL',
  deptIds: [],
  customerNames: []
})

const dataScopeOptionGroups = [
  {
    label: '常用范围',
    options: [
      { value: 'ALL', label: '全部数据' },
      { value: 'CUSTOMER', label: '指定客户' },
      { value: 'SELF', label: '仅本人' },
      { value: 'NONE', label: '无数据' }
    ]
  }
]

const dataScopeOptions = dataScopeOptionGroups.flatMap(group => group.options)
const dataScopeOptionValues = new Set(dataScopeOptions.map(item => item.value))
const isCustomerDataScope = computed(() => dataScopeForm.dataScope === 'CUSTOMER')
const editableDataScope = (value) => dataScopeOptionValues.has(value) ? value : 'ALL'
const dataScopeLabel = (value) => dataScopeOptions.find(item => item.value === editableDataScope(value))?.label || '-'

const loadAll = async () => {
  loading.value = true
  try {
    const [roleRows, permissionRows] = await Promise.all([listRoles(), listPermissionTree()])
    roles.value = roleRows || []
    permissionTree.value = permissionRows || []
  } finally {
    loading.value = false
  }
}

const resetForm = () => {
  form.id = null
  form.roleCode = ''
  form.roleName = ''
  form.description = ''
  form.dataScope = 'ALL'
  form.enabled = true
}

const openCreate = () => {
  resetForm()
  dialogVisible.value = true
}

const openEdit = (row) => {
  form.id = row.id
  form.roleCode = row.roleCode
  form.roleName = row.roleName
  form.description = row.description || ''
  form.dataScope = editableDataScope(row.dataScope)
  form.enabled = row.enabled !== false
  dialogVisible.value = true
}

const saveRoleForm = async () => {
  if (!form.roleCode || !form.roleName) {
    ElMessage.warning('请填写角色编码和角色名称')
    return
  }
  return runAction(roleActionKeys.saveDialog, async () => {
    await saveRole({ ...form })
    ElMessage.success('角色已保存')
    dialogVisible.value = false
    await loadAll()
  })
}

const toggleRole = async (row) => {
  return runAction(roleActionKeys.status(row.id), async () => {
    await ElMessageBox.confirm(`确认${row.enabled ? '禁用' : '启用'}角色 ${row.roleName || row.roleCode}？`, '确认操作')
    await saveRole({ ...row, enabled: !row.enabled })
    ElMessage.success('角色状态已更新')
    await loadAll()
  })
}

const removeRole = async (row) => {
  return runAction(roleActionKeys.delete(row.id), async () => {
    await ElMessageBox.confirm(`确认删除角色 ${row.roleName || row.roleCode}？`, '删除角色确认', { type: 'warning' })
    await deleteRole(row.id)
    ElMessage.success('角色已删除')
    await loadAll()
  })
}

const openPermissionDrawer = async (row) => {
  currentRole.value = row
  permissionTreeSearch.value = ''
  showSelectedOnly.value = false
  permissionCodes.value = await getRolePermissions(row.id) || []
  permissionDrawerVisible.value = true
  await syncPermissionTreeChecked()
}

const savePermissions = async () => {
  return runAction(roleActionKeys.permissionsDrawer(currentRole.value?.id), async () => {
    const checkedKeys = permissionTreeRef.value?.getCheckedKeys(false) || []
    const halfCheckedKeys = permissionTreeRef.value?.getHalfCheckedKeys() || []
    permissionCodes.value = collectPermissionCodes([...checkedKeys, ...halfCheckedKeys])
    await assignRolePermissions(currentRole.value.id, permissionCodes.value)
    ElMessage.success('角色权限已更新')
    permissionDrawerVisible.value = false
  })
}

const collectPermissionCodes = (keys = []) => {
  const permissionByKey = new Map()
  walkPermissionTree(permissionTree.value, (node) => {
    if (node?.key && node?.permissionCode) {
      permissionByKey.set(node.key, node.permissionCode)
    }
  })
  return [...new Set(keys.map(key => permissionByKey.get(key)).filter(Boolean))]
}

const walkPermissionTree = (nodes = [], visitor) => {
  for (const node of nodes || []) {
    visitor(node)
    if (node.children?.length) {
      walkPermissionTree(node.children, visitor)
    }
  }
}

const handlePermissionTreeCheck = (_node, checkedState) => {
  const checkedKeys = checkedState?.checkedKeys || []
  const halfCheckedKeys = checkedState?.halfCheckedKeys || []
  permissionCodes.value = collectPermissionCodes([...checkedKeys, ...halfCheckedKeys])
}

const syncPermissionTreeChecked = async () => {
  await nextTick()
  const tree = permissionTreeRef.value
  if (!tree) return
  tree.setCheckedKeys([], false)
  permissionCodes.value.forEach(code => tree.setChecked(code, true, false))
  applyPermissionTreeFilter()
}

const applyPermissionTreeFilter = () => {
  permissionTreeRef.value?.filter(`${permissionTreeSearch.value}|${showSelectedOnly.value}|${permissionCodes.value.join(',')}`)
}

const filterPermissionNode = (_filterValue, data) => {
  const keyword = permissionTreeSearch.value.trim().toLowerCase()
  const matchesSearch = !keyword || isPermissionNodeMatchingSearch(data, keyword)
  const matchesSelected = !showSelectedOnly.value || isPermissionNodeSelected(data)
  return matchesSearch && matchesSelected
}

const isPermissionNodeMatchingSearch = (node, keyword) => {
  if (!node) return false
  const text = [
    node.label,
    node.permissionName,
    node.permissionCode,
    node.groupName,
    node.permissionType
  ].filter(Boolean).join(' ').toLowerCase()
  return text.includes(keyword) || (node.children || []).some(child => isPermissionNodeMatchingSearch(child, keyword))
}

const isPermissionNodeSelected = (node) => {
  if (!node) return false
  if (node.permissionCode && permissionCodes.value.includes(node.permissionCode)) {
    return true
  }
  return (node.children || []).some(child => isPermissionNodeSelected(child))
}

const setPermissionTreeExpanded = (expanded) => {
  const nodesMap = permissionTreeRef.value?.store?.nodesMap || {}
  Object.values(nodesMap).forEach(node => {
    node.expanded = expanded
  })
}

const expandPermissionTree = () => setPermissionTreeExpanded(true)

const collapsePermissionTree = () => setPermissionTreeExpanded(false)

const isPermissionGroup = (node) => node?.key?.startsWith('group:')

const permissionTypeLabel = (type) => {
  const labels = {
    MENU: '菜单',
    BUTTON: '按钮',
    API: '接口'
  }
  return labels[type] || type
}

const loadDataScopeOptions = async () => {
  customerOptions.value = await listDataScopeCustomers() || []
}

const openDataScopeDrawer = async (row) => {
  currentRole.value = row
  const [response] = await Promise.all([
    getRoleDataScope(row.id),
    loadDataScopeOptions()
  ])
  dataScopeForm.dataScope = editableDataScope(response?.dataScope || row.dataScope)
  dataScopeForm.deptIds = []
  dataScopeForm.customerNames = response?.customerNames || []
  dataScopeDrawerVisible.value = true
}

const buildDataScopePayload = (formState) => {
  const dataScope = editableDataScope(formState.dataScope)
  switch (dataScope) {
    case 'CUSTOMER':
      return {
        dataScope,
        deptIds: [],
        customerNames: [...formState.customerNames]
      }
    default:
      return {
        dataScope,
        deptIds: [],
        customerNames: []
      }
  }
}

const saveDataScope = async () => {
  return runAction(roleActionKeys.dataScopeDrawer(currentRole.value?.id), async () => {
    const payload = buildDataScopePayload(dataScopeForm)
    await assignRoleDataScope(currentRole.value.id, payload)
    ElMessage.success('数据范围已更新')
    dataScopeDrawerVisible.value = false
    await loadAll()
  })
}

watch([permissionTreeSearch, showSelectedOnly, permissionCodes], () => {
  if (permissionDrawerVisible.value) {
    applyPermissionTreeFilter()
  }
})

onMounted(loadAll)
</script>

<style scoped>
.role-toolbar-note,
.role-drawer-help,
.permission-code {
  color: var(--erp-text-secondary);
  font-size: var(--erp-font-size-sm);
}

.role-table-surface .erp-table-body {
  padding: var(--erp-space-3);
}

.role-drawer-body {
  display: grid;
  gap: var(--erp-space-4);
}

.permission-tree-toolbar {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: var(--erp-space-3);
  padding: var(--erp-space-3);
  border: 1px solid var(--erp-border-subtle);
  border-radius: var(--erp-radius-md);
  background: var(--erp-surface-muted);
}

.permission-tree-search {
  flex: 1 1 260px;
  min-width: 220px;
}

.permission-tree-actions {
  display: flex;
  gap: var(--erp-space-2);
}

.permission-tree {
  width: 100%;
  max-height: calc(100vh - 260px);
  overflow: auto;
  border: 1px solid var(--erp-border-subtle);
  border-radius: var(--erp-radius-md);
  padding: var(--erp-space-2);
  background: var(--erp-surface);
}

.permission-tree :deep(.el-tree-node__content) {
  min-height: 36px;
  padding-right: var(--erp-space-2);
}

.permission-tree-node {
  display: flex;
  align-items: center;
  gap: var(--erp-space-2);
  min-width: 0;
  width: 100%;
}

.permission-tree-name {
  min-width: 0;
  max-width: 220px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-weight: 500;
}

.permission-tree-group .permission-tree-name {
  font-weight: 700;
}

.permission-tree-node .permission-code {
  flex: 1 1 auto;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.data-scope-help {
  margin: 0 0 var(--erp-space-2);
  color: var(--erp-text-secondary);
  font-size: var(--erp-font-size-sm);
  line-height: 1.6;
}

.permission-code {
  margin-left: var(--erp-space-2);
}
</style>
