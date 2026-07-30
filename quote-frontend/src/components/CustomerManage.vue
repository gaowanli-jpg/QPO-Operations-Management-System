<template>
  <div class="erp-page customer-manage-page">
    <header class="erp-page-header customer-page-header">
      <div>
        <h1 class="erp-page-title">客户资料</h1>
        <p class="erp-page-description">维护 QPO 全系统客户编码、简称和名称，供客户选择、数据权限和后续 U9 销售订单使用。</p>
      </div>
      <div class="erp-page-actions customer-page-actions">
        <el-button v-permission="'system:customer:create'" type="primary" @click="openCreate">新增客户</el-button>
      </div>
    </header>

    <section class="erp-toolbar customer-toolbar">
      <div class="erp-toolbar-group customer-filter-group">
        <el-select v-model="filters.field" class="customer-filter-field" aria-label="筛选字段">
          <el-option label="全部" value="all" />
          <el-option label="客户编码" value="code" />
          <el-option label="客户简称" value="shortName" />
          <el-option label="客户名称" value="name" />
        </el-select>
        <el-input
          v-model.trim="filters.keyword"
          clearable
          class="customer-keyword-input"
          placeholder="输入客户编码、简称或名称"
          @keyup.enter="searchCustomers"
        />
      </div>
      <div class="erp-toolbar-group customer-toolbar-actions">
        <el-button type="primary" :loading="loading" @click="searchCustomers">查询</el-button>
        <el-button @click="resetFilters">重置</el-button>
        <el-button :loading="loading" @click="loadCustomers">刷新</el-button>
      </div>
    </section>

    <section class="erp-table-surface customer-table-surface">
      <div class="erp-table-body">
        <el-table
          :data="customers"
          border
          v-loading="loading"
          empty-text="暂无客户资料"
        >
          <el-table-column prop="customerCode" label="客户编码" min-width="160" show-overflow-tooltip />
          <el-table-column prop="customerShortName" label="客户简称" min-width="180" show-overflow-tooltip />
          <el-table-column prop="customerName" label="客户名称" min-width="260" show-overflow-tooltip />
          <el-table-column label="状态" width="100">
            <template #default="{ row }">
              <el-tag :type="row.enabled === false ? 'info' : 'success'">
                {{ row.enabled === false ? '停用' : '启用' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="updatedAt" label="更新时间" min-width="180">
            <template #default="{ row }">{{ formatDateTime(row.updatedAt) }}</template>
          </el-table-column>
          <el-table-column label="操作" width="180" fixed="right">
            <template #default="{ row }">
              <div class="erp-table-actions customer-row-actions">
                <el-button v-permission="'system:customer:update'" size="small" :disabled="isCustomerRowPending(row)" @click="openEdit(row)">编辑</el-button>
                <el-button
                  v-permission="'system:customer:delete'"
                  size="small"
                  type="danger"
                  plain
                  class="erp-danger-action"
                  :loading="isCustomerDeletePending(row)"
                  :disabled="isCustomerRowPending(row)"
                  @click="deleteCustomer(row)"
                >
                  删除
                </el-button>
              </div>
            </template>
          </el-table-column>
        </el-table>
      </div>
      <div class="erp-pagination">
        <el-pagination
          v-model:current-page="pagination.page"
          v-model:page-size="pagination.pageSize"
          :total="pagination.total"
          :page-sizes="[20, 50, 100]"
          layout="total, sizes, prev, pager, next, jumper"
          :pager-count="7"
          background
          @size-change="handlePageSizeChange"
          @current-change="loadCustomers"
        />
      </div>
    </section>

    <el-dialog v-model="dialogVisible" :title="form.id ? '编辑客户' : '新增客户'" width="520px">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="96px" class="erp-dialog-form">
        <el-form-item label="客户编码" prop="customerCode">
          <el-input v-model.trim="form.customerCode" placeholder="例如 C0001" maxlength="200" show-word-limit />
        </el-form-item>
        <el-form-item label="客户简称" prop="customerShortName">
          <el-input v-model.trim="form.customerShortName" placeholder="例如 ABC" maxlength="200" show-word-limit />
        </el-form-item>
        <el-form-item label="客户名称" prop="customerName">
          <el-input v-model.trim="form.customerName" placeholder="客户完整名称" maxlength="200" show-word-limit />
        </el-form-item>
      </el-form>
      <template #footer>
        <div class="erp-dialog-footer">
          <el-button @click="dialogVisible = false">取消</el-button>
          <el-button
            v-permission="form.id ? 'system:customer:update' : 'system:customer:create'"
            type="primary"
            :loading="isPending(customerActionKeys.saveDialog)"
            :disabled="isPending(customerActionKeys.saveDialog)"
            @click="saveCustomer"
          >
            保存
          </el-button>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  createCustomer,
  deleteCustomer as deleteCustomerRequest,
  listCustomers,
  updateCustomer
} from '../services/customerApi'
import { usePendingAction } from '../composables/usePendingAction'

const customers = ref([])
const loading = ref(false)
const dialogVisible = ref(false)
const formRef = ref()
const { isPending, runAction } = usePendingAction()

const customerActionKeys = {
  saveDialog: 'system:customer:save-dialog',
  delete: (customerId) => `system:customer:delete:${customerId || 'none'}`
}

const filters = reactive({
  field: 'all',
  keyword: ''
})

const pagination = reactive({
  page: 1,
  pageSize: 20,
  total: 0
})

const form = reactive({
  id: null,
  customerCode: '',
  customerShortName: '',
  customerName: '',
  enabled: true
})

const rules = {
  customerCode: [{ required: true, message: '请填写客户编码', trigger: 'blur' }],
  customerShortName: [{ required: true, message: '请填写客户简称', trigger: 'blur' }],
  customerName: [{ required: true, message: '请填写客户名称', trigger: 'blur' }]
}

const requestErrorMessage = (error, fallback) => error?.response?.data?.message || error?.payload?.message || error?.message || fallback

const buildParams = () => ({
  field: filters.field || 'all',
  keyword: filters.keyword || undefined,
  page: pagination.page,
  pageSize: pagination.pageSize
})

const loadCustomers = async () => {
  loading.value = true
  try {
    const response = await listCustomers(buildParams())
    customers.value = response?.records || []
    pagination.total = response?.total || 0
    pagination.page = response?.page || pagination.page
    pagination.pageSize = response?.pageSize || pagination.pageSize
  } catch (error) {
    customers.value = []
    ElMessage.error(requestErrorMessage(error, '客户资料加载失败'))
  } finally {
    loading.value = false
  }
}

const searchCustomers = () => {
  pagination.page = 1
  loadCustomers()
}

const resetFilters = () => {
  filters.field = 'all'
  filters.keyword = ''
  searchCustomers()
}

const handlePageSizeChange = () => {
  pagination.page = 1
  loadCustomers()
}

const resetForm = () => {
  form.id = null
  form.customerCode = ''
  form.customerShortName = ''
  form.customerName = ''
  form.enabled = true
  formRef.value?.clearValidate()
}

const openCreate = () => {
  resetForm()
  dialogVisible.value = true
}

const openEdit = (row) => {
  form.id = row.id
  form.customerCode = row.customerCode || ''
  form.customerShortName = row.customerShortName || ''
  form.customerName = row.customerName || ''
  form.enabled = row.enabled !== false
  formRef.value?.clearValidate()
  dialogVisible.value = true
}

const saveCustomer = async () => {
  await formRef.value?.validate()
  return runAction(customerActionKeys.saveDialog, async () => {
    const payload = {
      customerCode: form.customerCode,
      customerShortName: form.customerShortName,
      customerName: form.customerName,
      enabled: form.enabled !== false
    }
    const response = form.id
      ? await updateCustomer(form.id, payload)
      : await createCustomer(payload)
    ElMessage.success(response?.message || '客户资料已保存')
    dialogVisible.value = false
    await loadCustomers()
  })
}

const isCustomerDeletePending = (row) => isPending(customerActionKeys.delete(row?.id))
const isCustomerRowPending = (row) => isCustomerDeletePending(row)

const deleteCustomer = async (row) => {
  return runAction(customerActionKeys.delete(row.id), async () => {
    await ElMessageBox.confirm(
      `确认删除客户 ${row.customerName || row.customerShortName || row.customerCode}？已被订单或数据权限引用的客户会被后端拒绝删除。`,
      '删除客户确认',
      {
        type: 'warning',
        confirmButtonText: '删除',
        cancelButtonText: '取消'
      }
    )
    await deleteCustomerRequest(row.id)
    ElMessage.success('客户资料已删除')
    await loadCustomers()
  })
}

const formatDateTime = (value) => {
  if (!value) return '-'
  return String(value).replace('T', ' ').slice(0, 19)
}

onMounted(loadCustomers)
</script>

<style scoped>
.customer-toolbar {
  align-items: flex-start;
}

.customer-filter-group {
  flex: 1;
  align-items: flex-start;
}

.customer-filter-field {
  width: 140px;
}

.customer-keyword-input {
  width: 280px;
}

.customer-toolbar-actions {
  flex: 0 0 auto;
}

.customer-table-surface .erp-table-body {
  padding: var(--erp-space-3);
}

.customer-row-actions {
  justify-content: flex-start;
}

@media (max-width: 760px) {
  .customer-filter-group,
  .customer-toolbar-actions {
    width: 100%;
  }

  .customer-filter-field,
  .customer-keyword-input {
    width: 100%;
  }
}
</style>
