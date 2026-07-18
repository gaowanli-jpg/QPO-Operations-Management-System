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
                <el-button v-permission="'system:role:update'" size="small" @click="openEdit(row)">编辑</el-button>
                <el-button v-permission="'system:role:assign-permission'" size="small" @click="openPermissionDrawer(row)">授权</el-button>
                <el-button v-permission="'system:role:assign-data-scope'" size="small" @click="openDataScopeDrawer(row)">数据范围</el-button>
                <el-button v-permission="'system:role:update'" size="small" :type="row.enabled ? 'warning' : 'success'" @click="toggleRole(row)">
                  {{ row.enabled ? '禁用' : '启用' }}
                </el-button>
                <el-button v-permission="'system:role:delete'" size="small" type="danger" plain class="erp-danger-action" :disabled="row.superAdmin" @click="removeRole(row)">
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
            <el-option v-for="item in dataScopeOptions" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-switch v-model="form.enabled" active-text="启用" inactive-text="禁用" />
        </el-form-item>
      </el-form>
      <template #footer>
        <div class="erp-dialog-footer">
          <el-button @click="dialogVisible = false">取消</el-button>
          <el-button v-permission="['system:role:create', 'system:role:update']" type="primary" :loading="saving" @click="saveRoleForm">保存</el-button>
        </div>
      </template>
    </el-dialog>

    <el-drawer v-model="permissionDrawerVisible" title="角色授权" size="520px">
      <div class="role-drawer-body">
        <p class="role-drawer-help">勾选该角色拥有的菜单、按钮和接口权限。</p>
        <el-checkbox-group v-model="permissionCodes" class="permission-list">
          <el-checkbox
            v-for="permission in permissions"
            :key="permission.permissionCode"
            :label="permission.permissionCode"
            :disabled="permission.enabled === false"
          >
            {{ permission.permissionName || permission.permissionCode }}
            <span class="permission-code">{{ permission.permissionCode }}</span>
          </el-checkbox>
        </el-checkbox-group>
      </div>
      <template #footer>
        <div class="erp-dialog-footer">
          <el-button @click="permissionDrawerVisible = false">取消</el-button>
          <el-button v-permission="'system:role:assign-permission'" type="primary" :loading="saving" @click="savePermissions">保存授权</el-button>
        </div>
      </template>
    </el-drawer>

    <el-drawer v-model="dataScopeDrawerVisible" title="数据范围" size="480px">
      <div class="role-drawer-body">
        <el-form label-width="96px" class="erp-dialog-form">
          <el-form-item label="范围类型">
            <el-select v-model="dataScopeForm.dataScope" style="width: 100%">
              <el-option v-for="item in dataScopeOptions" :key="item.value" :label="item.label" :value="item.value" />
            </el-select>
          </el-form-item>
          <el-form-item label="部门 ID">
            <el-input v-model.trim="dataScopeForm.deptIdsText" placeholder="多个部门 ID 用英文逗号分隔" />
          </el-form-item>
          <el-form-item label="客户名称">
            <el-input v-model.trim="dataScopeForm.customerNamesText" placeholder="多个客户用英文逗号分隔" />
          </el-form-item>
        </el-form>
      </div>
      <template #footer>
        <div class="erp-dialog-footer">
          <el-button @click="dataScopeDrawerVisible = false">取消</el-button>
          <el-button v-permission="'system:role:assign-data-scope'" type="primary" :loading="saving" @click="saveDataScope">保存数据范围</el-button>
        </div>
      </template>
    </el-drawer>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  assignRoleDataScope,
  assignRolePermissions,
  deleteRole,
  getRoleDataScope,
  getRolePermissions,
  listPermissions,
  listRoles,
  saveRole
} from '../services/adminApi'

const roles = ref([])
const permissions = ref([])
const loading = ref(false)
const saving = ref(false)
const dialogVisible = ref(false)
const permissionDrawerVisible = ref(false)
const dataScopeDrawerVisible = ref(false)
const currentRole = ref(null)
const permissionCodes = ref([])

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
  deptIdsText: '',
  customerNamesText: ''
})

const dataScopeOptions = [
  { value: 'ALL', label: '全部数据' },
  { value: 'DEPT', label: '本部门' },
  { value: 'DEPT_AND_CHILD', label: '本部门及子部门' },
  { value: 'SELF', label: '仅本人' },
  { value: 'CUSTOMER', label: '指定客户' },
  { value: 'CUSTOM', label: '指定部门' },
  { value: 'NONE', label: '无业务数据' }
]

const dataScopeLabel = (value) => dataScopeOptions.find(item => item.value === value)?.label || value || '-'

const loadAll = async () => {
  loading.value = true
  try {
    const [roleRows, permissionRows] = await Promise.all([listRoles(), listPermissions()])
    roles.value = roleRows || []
    permissions.value = permissionRows || []
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
  form.dataScope = row.dataScope || 'ALL'
  form.enabled = row.enabled !== false
  dialogVisible.value = true
}

const saveRoleForm = async () => {
  if (!form.roleCode || !form.roleName) {
    ElMessage.warning('请填写角色编码和角色名称')
    return
  }
  saving.value = true
  try {
    await saveRole({ ...form })
    ElMessage.success('角色已保存')
    dialogVisible.value = false
    await loadAll()
  } finally {
    saving.value = false
  }
}

const toggleRole = async (row) => {
  await ElMessageBox.confirm(`确认${row.enabled ? '禁用' : '启用'}角色 ${row.roleName || row.roleCode}？`, '确认操作')
  await saveRole({ ...row, enabled: !row.enabled })
  ElMessage.success('角色状态已更新')
  await loadAll()
}

const removeRole = async (row) => {
  await ElMessageBox.confirm(`确认删除角色 ${row.roleName || row.roleCode}？`, '删除角色确认', { type: 'warning' })
  await deleteRole(row.id)
  ElMessage.success('角色已删除')
  await loadAll()
}

const openPermissionDrawer = async (row) => {
  currentRole.value = row
  permissionCodes.value = await getRolePermissions(row.id) || []
  permissionDrawerVisible.value = true
}

const savePermissions = async () => {
  saving.value = true
  try {
    await assignRolePermissions(currentRole.value.id, permissionCodes.value)
    ElMessage.success('角色权限已更新')
    permissionDrawerVisible.value = false
  } finally {
    saving.value = false
  }
}

const openDataScopeDrawer = async (row) => {
  currentRole.value = row
  const response = await getRoleDataScope(row.id)
  dataScopeForm.dataScope = response?.dataScope || row.dataScope || 'ALL'
  dataScopeForm.deptIdsText = (response?.deptIds || []).join(',')
  dataScopeForm.customerNamesText = (response?.customerNames || []).join(',')
  dataScopeDrawerVisible.value = true
}

const parseDeptIds = (value) => String(value || '')
  .split(',')
  .map(item => Number(item.trim()))
  .filter(Number.isFinite)

const parseNames = (value) => String(value || '')
  .split(',')
  .map(item => item.trim())
  .filter(Boolean)

const saveDataScope = async () => {
  saving.value = true
  try {
    await assignRoleDataScope(currentRole.value.id, {
      dataScope: dataScopeForm.dataScope,
      deptIds: parseDeptIds(dataScopeForm.deptIdsText),
      customerNames: parseNames(dataScopeForm.customerNamesText)
    })
    ElMessage.success('数据范围已更新')
    dataScopeDrawerVisible.value = false
    await loadAll()
  } finally {
    saving.value = false
  }
}

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

.permission-list {
  display: grid;
  gap: var(--erp-space-2);
}

.permission-code {
  margin-left: var(--erp-space-2);
}
</style>
