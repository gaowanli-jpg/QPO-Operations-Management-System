<template>
  <div class="erp-page customer-manage-page">
    <header class="erp-page-header customer-page-header">
      <div>
        <h1 class="erp-page-title">客户资料</h1>
        <p class="erp-page-description">来自 U9 的只读客户主数据，供 QPO 全系统客户选择和数据权限使用。</p>
      </div>
      <div class="erp-page-actions customer-page-actions">
        <el-button
          v-permission="'system:customer:sync'"
          :loading="scheduleLoading"
          :disabled="loading || syncLoading || scheduleLoading"
          @click="openScheduleDialog"
        >
          定时同步策略
        </el-button>
        <el-button
          v-permission="'system:customer:sync'"
          type="primary"
          :loading="syncLoading"
          :disabled="loading || syncLoading || scheduleLoading"
          @click="syncU9Customers"
        >
          同步 U9 客户
        </el-button>
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
        <el-button type="primary" :loading="loading" :disabled="syncLoading" @click="searchCustomers">查询</el-button>
        <el-button :disabled="loading || syncLoading" @click="resetFilters">重置</el-button>
        <el-button :loading="loading" :disabled="syncLoading" @click="loadCustomers">刷新</el-button>
      </div>
    </section>

    <el-alert
      v-if="syncResult"
      class="customer-sync-result"
      :type="syncResult.success === false ? 'error' : 'success'"
      :closable="true"
      show-icon
      @close="syncResult = null"
    >
      <template #title>
        {{ syncResult.success === false ? 'U9 客户同步失败' : 'U9 客户同步完成' }}
      </template>
      <template #default>
        已获取 {{ syncResult.fetchedCount || 0 }} 条，更新 {{ syncResult.upsertedCount || 0 }} 条，停用 {{ syncResult.disabledCount || 0 }} 条。
        <span v-if="syncResult.safeMessage || syncResult.message">{{ syncResult.safeMessage || syncResult.message }}</span>
      </template>
    </el-alert>

    <section class="erp-table-surface customer-table-surface">
      <div class="erp-table-body customer-table-body">
        <el-table
          :data="customers"
          border
          v-loading="loading"
          empty-text="暂无 U9 客户资料"
        >
          <el-table-column prop="customerCode" label="客户编码" min-width="150" show-overflow-tooltip />
          <el-table-column prop="customerShortName" label="客户简称" min-width="170" show-overflow-tooltip />
          <el-table-column prop="customerName" label="客户名称" min-width="260" show-overflow-tooltip />
          <el-table-column label="有效状态" width="110">
            <template #default="{ row }">
              <el-tag :type="row.enabled === false ? 'info' : 'success'">
                {{ row.enabled === false ? '停用' : '启用' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="lastSyncedAt" label="最近同步时间" min-width="180">
            <template #default="{ row }">{{ formatDateTime(row.lastSyncedAt) }}</template>
          </el-table-column>
          <el-table-column label="来源" width="110">
            <template #default="{ row }">
              <span class="customer-source-text">U9{{ row.u9OrgCode ? ` / ${row.u9OrgCode}` : '' }}</span>
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

    <el-dialog
      v-model="scheduleDialogVisible"
      title="定时同步策略"
      width="min(560px, 92vw)"
      class="customer-schedule-dialog"
      destroy-on-close
    >
      <div v-loading="scheduleLoading" class="customer-schedule-panel">
        <el-alert
          class="customer-schedule-alert"
          type="info"
          :closable="false"
          show-icon
          title="这里保存后立即作为系统运行策略；环境变量只在数据库策略缺失时作为兜底默认值。"
        />
        <el-form label-width="150px" class="customer-schedule-form">
          <el-form-item label="开启定时同步">
            <el-switch v-model="scheduleForm.enabled" active-text="开启" inactive-text="关闭" />
          </el-form-item>
          <el-form-item label="启动后首次执行">
            <el-input-number
              v-model="scheduleForm.startupDelaySeconds"
              :min="0"
              :max="86400"
              :step="30"
              controls-position="right"
            />
            <span class="customer-schedule-unit">秒</span>
          </el-form-item>
          <el-form-item label="完成后再次执行">
            <el-input-number
              v-model="scheduleForm.fixedDelayMinutes"
              :min="1"
              :max="10080"
              :step="5"
              controls-position="right"
            />
            <span class="customer-schedule-unit">分钟</span>
          </el-form-item>
        </el-form>
        <el-descriptions :column="1" size="small" border class="customer-schedule-status">
          <el-descriptions-item label="配置来源">{{ scheduleForm.fallback ? '环境兜底' : '数据库策略' }}</el-descriptions-item>
          <el-descriptions-item label="上次定时状态">{{ scheduleStatusLabel(scheduleForm.lastScheduledStatus) }}</el-descriptions-item>
          <el-descriptions-item label="上次定时时间">{{ formatDateTime(scheduleForm.lastScheduledRunAt) }}</el-descriptions-item>
          <el-descriptions-item label="同步批次">{{ scheduleForm.lastScheduledSyncRunId || '-' }}</el-descriptions-item>
          <el-descriptions-item label="说明">{{ scheduleForm.lastScheduledMessage || '-' }}</el-descriptions-item>
        </el-descriptions>
      </div>
      <template #footer>
        <div class="customer-schedule-footer">
          <el-button :disabled="scheduleSaving" @click="scheduleDialogVisible = false">取消</el-button>
          <el-button type="primary" :loading="scheduleSaving" :disabled="scheduleLoading || scheduleSaving" @click="saveScheduleSetting">保存策略</el-button>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { getCustomerSyncSchedule, listCustomers, saveCustomerSyncSchedule, syncCustomers } from '../services/customerApi'

const customers = ref([])
const loading = ref(false)
const syncLoading = ref(false)
const syncResult = ref(null)
const scheduleDialogVisible = ref(false)
const scheduleLoading = ref(false)
const scheduleSaving = ref(false)

const filters = reactive({
  field: 'all',
  keyword: ''
})

const pagination = reactive({
  page: 1,
  pageSize: 20,
  total: 0
})

const scheduleForm = reactive({
  enabled: false,
  startupDelaySeconds: 30,
  fixedDelayMinutes: 30,
  lastScheduledRunAt: '',
  lastScheduledSyncRunId: null,
  lastScheduledStatus: 'NEVER',
  lastScheduledMessage: '',
  fallback: false
})

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

const syncU9Customers = async () => {
  if (syncLoading.value) return
  syncLoading.value = true
  try {
    const response = await syncCustomers()
    syncResult.value = response || { success: false, message: '同步接口未返回结果' }
    if (response?.success === false) {
      ElMessage.error(response.message || response.safeMessage || 'U9 客户同步失败')
      return
    }
    ElMessage.success(response?.message || 'U9 客户同步完成')
    await loadCustomers()
  } catch (error) {
    syncResult.value = null
    ElMessage.error(requestErrorMessage(error, 'U9 客户同步失败'))
  } finally {
    syncLoading.value = false
  }
}

const openScheduleDialog = async () => {
  if (scheduleLoading.value) return
  scheduleDialogVisible.value = true
  scheduleLoading.value = true
  try {
    const response = await getCustomerSyncSchedule()
    applyScheduleSetting(response)
  } catch (error) {
    ElMessage.error(requestErrorMessage(error, '定时同步策略加载失败'))
  } finally {
    scheduleLoading.value = false
  }
}

const saveScheduleSetting = async () => {
  if (scheduleSaving.value) return
  scheduleSaving.value = true
  try {
    const payload = {
      enabled: !!scheduleForm.enabled,
      startupDelayMs: Number(scheduleForm.startupDelaySeconds || 0) * 1000,
      fixedDelayMs: Number(scheduleForm.fixedDelayMinutes || 1) * 60 * 1000
    }
    const response = await saveCustomerSyncSchedule(payload)
    applyScheduleSetting(response)
    ElMessage.success('定时同步策略已保存')
    scheduleDialogVisible.value = false
  } catch (error) {
    ElMessage.error(requestErrorMessage(error, '定时同步策略保存失败'))
  } finally {
    scheduleSaving.value = false
  }
}

const applyScheduleSetting = (setting = {}) => {
  scheduleForm.enabled = !!setting.enabled
  scheduleForm.startupDelaySeconds = Math.round((setting.startupDelayMs ?? 30000) / 1000)
  scheduleForm.fixedDelayMinutes = Math.max(1, Math.round((setting.fixedDelayMs ?? 1800000) / 60000))
  scheduleForm.lastScheduledRunAt = setting.lastScheduledRunAt || ''
  scheduleForm.lastScheduledSyncRunId = setting.lastScheduledSyncRunId || null
  scheduleForm.lastScheduledStatus = setting.lastScheduledStatus || 'NEVER'
  scheduleForm.lastScheduledMessage = setting.lastScheduledMessage || ''
  scheduleForm.fallback = !!setting.fallback
}

const scheduleStatusLabel = (status) => ({
  SUCCESS: '成功',
  FAILED: '失败',
  NEVER: '未执行'
}[status] || status || '-')

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

.customer-sync-result {
  margin: 0;
}

.customer-schedule-panel {
  display: flex;
  flex-direction: column;
  gap: var(--erp-space-4);
}

.customer-schedule-alert {
  margin: 0;
}

.customer-schedule-form {
  max-width: 100%;
}

.customer-schedule-form :deep(.el-input-number) {
  width: 180px;
  max-width: calc(100vw - 220px);
}

.customer-schedule-unit {
  margin-left: var(--erp-space-2);
  color: var(--erp-text-secondary);
}

.customer-schedule-status {
  width: 100%;
}

.customer-schedule-footer {
  display: flex;
  justify-content: flex-end;
  gap: var(--erp-space-2);
}

.customer-table-surface .customer-table-body {
  padding: var(--erp-space-3);
  overflow-x: auto;
}

.customer-source-text {
  color: var(--erp-text-secondary);
  font-size: var(--erp-font-size-sm);
}

@media (max-width: 760px) {
  .customer-filter-group,
  .customer-toolbar-actions,
  .customer-page-actions {
    width: 100%;
  }

  .customer-filter-field,
  .customer-keyword-input,
  .customer-page-actions .el-button {
    width: 100%;
  }

  .customer-schedule-form :deep(.el-form-item) {
    display: block;
  }

  .customer-schedule-form :deep(.el-form-item__label) {
    width: auto !important;
    justify-content: flex-start;
  }

  .customer-schedule-form :deep(.el-form-item__content) {
    margin-left: 0 !important;
  }

  .customer-schedule-form :deep(.el-input-number) {
    width: calc(100vw - 120px);
    max-width: 100%;
  }

  .customer-schedule-footer {
    flex-direction: column-reverse;
  }

  .customer-schedule-footer .el-button {
    width: 100%;
    margin-left: 0;
  }
}
</style>
