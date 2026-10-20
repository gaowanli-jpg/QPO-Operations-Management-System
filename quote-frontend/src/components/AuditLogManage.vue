<template>
  <div class="erp-page audit-log-page">
    <header class="erp-page-header audit-log-page-header">
      <div>
        <h1 class="erp-page-title">操作日志</h1>
        <p class="erp-page-description">查询系统关键操作、账号安全事件和数据变更记录，用于追溯问题和审计。</p>
      </div>
      <div class="erp-page-actions">
        <el-button v-permission="'system:audit:retention'" @click="openRetentionSetting">日志保留策略</el-button>
        <el-button v-permission="'system:audit:export'" :loading="isPending(auditActionKeys.exportLogs)" :disabled="isPending(auditActionKeys.exportLogs)" @click="exportLogs">导出 CSV</el-button>
        <el-button :loading="loading" @click="loadLogs">刷新</el-button>
      </div>
    </header>

    <section class="erp-toolbar audit-toolbar">
      <div class="audit-toolbar-main">
        <div class="erp-toolbar-group audit-primary-filters">
          <el-date-picker
            v-model="filters.timeRange"
            type="datetimerange"
            start-placeholder="开始时间"
            end-placeholder="结束时间"
            value-format="YYYY-MM-DD HH:mm:ss"
            range-separator="至"
            class="audit-time-range"
          />
          <el-input v-model.trim="filters.username" placeholder="操作人" clearable class="audit-filter-control" @keyup.enter="searchLogs" />
          <el-select v-model="filters.module" placeholder="模块" clearable class="audit-filter-control">
            <el-option v-for="module in optionData.modules" :key="module" :label="moduleLabel(module)" :value="module" />
          </el-select>
          <el-select v-model="filters.result" placeholder="结果" clearable class="audit-filter-control">
            <el-option label="成功" value="SUCCESS" />
            <el-option label="失败" value="FAILED" />
          </el-select>
          <el-button type="primary" :loading="loading" @click="searchLogs">查询</el-button>
          <el-button @click="resetFilters">重置</el-button>
          <el-button plain @click="advancedFiltersVisible = !advancedFiltersVisible">
            {{ advancedFiltersVisible ? '收起筛选' : '高级筛选' }}
          </el-button>
        </div>

      </div>

      <div v-if="advancedFiltersVisible" class="erp-toolbar-group audit-advanced-filters">
        <el-select v-model="filters.role" placeholder="角色" clearable class="audit-filter-control">
          <el-option v-for="role in optionData.roles" :key="role" :label="roleLabel(role)" :value="role" />
        </el-select>
        <el-select v-model="filters.action" placeholder="动作" clearable filterable class="audit-filter-control">
          <el-option
            v-for="action in actionOptions"
            :key="action.value"
            :label="action.label"
            :value="action.value"
          />
        </el-select>
        <el-select v-model="filters.targetType" placeholder="对象类型" clearable filterable class="audit-filter-control">
          <el-option v-for="targetType in optionData.targetTypes" :key="targetType" :label="targetType" :value="targetType" />
        </el-select>
        <el-input v-model.trim="filters.targetId" placeholder="对象 ID" clearable class="audit-filter-control" @keyup.enter="searchLogs" />
        <el-input v-model.trim="filters.requestId" placeholder="Request ID" clearable class="audit-request-id-control" @keyup.enter="searchLogs" />
        <el-input v-model.trim="filters.keyword" placeholder="关键词" clearable class="audit-keyword-control" @keyup.enter="searchLogs" />
      </div>
    </section>

    <section class="erp-table-surface audit-table-surface">
      <div class="erp-table-body">
        <el-table :data="logs" border v-loading="loading">
          <el-table-column prop="createdAt" label="时间" width="180" show-overflow-tooltip>
            <template #default="{ row }">{{ formatDateTime(row.createdAt) }}</template>
          </el-table-column>
          <el-table-column prop="username" label="操作人" width="130" show-overflow-tooltip />
          <el-table-column label="角色" width="110">
            <template #default="{ row }">
              <el-tag size="small" type="info">{{ roleLabel(row.role) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="模块" width="120" show-overflow-tooltip>
            <template #default="{ row }">{{ moduleLabel(row.module) }}</template>
          </el-table-column>
          <el-table-column prop="actionLabel" label="动作" min-width="180" show-overflow-tooltip>
            <template #default="{ row }">{{ row.actionLabel || row.action }}</template>
          </el-table-column>
          <el-table-column label="对象" min-width="220" show-overflow-tooltip>
            <template #default="{ row }">{{ targetLabel(row) }}</template>
          </el-table-column>
          <el-table-column label="结果" width="100">
            <template #default="{ row }">
              <el-tag size="small" :type="resultTagType(row.result)">{{ resultLabel(row.result) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="message" label="摘要" min-width="260" show-overflow-tooltip />
          <el-table-column label="操作" width="110" fixed="right">
            <template #default="{ row }">
              <div class="erp-table-actions">
                <el-button size="small" @click="openDetail(row)">查看详情</el-button>
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
          @size-change="loadLogs"
          @current-change="loadLogs"
        />
      </div>
    </section>

    <el-drawer v-model="detailVisible" title="操作日志详情" size="620px" class="audit-detail-drawer">
      <div v-if="currentLog" class="audit-detail">
        <section class="erp-surface audit-detail-section">
          <h2 class="erp-section-title">基础信息</h2>
          <el-descriptions :column="1" border>
            <el-descriptions-item label="日志 ID">{{ currentLog.id }}</el-descriptions-item>
            <el-descriptions-item label="时间">{{ formatDateTime(currentLog.createdAt) }}</el-descriptions-item>
            <el-descriptions-item label="操作人">{{ emptyText(currentLog.username) }}</el-descriptions-item>
            <el-descriptions-item label="角色">{{ roleLabel(currentLog.role) }}</el-descriptions-item>
            <el-descriptions-item label="模块">{{ moduleLabel(currentLog.module) }}</el-descriptions-item>
            <el-descriptions-item label="动作">{{ currentLog.actionLabel || currentLog.action }}</el-descriptions-item>
            <el-descriptions-item label="结果">{{ resultLabel(currentLog.result) }}</el-descriptions-item>
            <el-descriptions-item label="对象">{{ targetLabel(currentLog) }}</el-descriptions-item>
            <el-descriptions-item label="摘要">{{ emptyText(currentLog.message) }}</el-descriptions-item>
            <el-descriptions-item v-if="currentLog.errorMessage" label="错误">{{ currentLog.errorMessage }}</el-descriptions-item>
          </el-descriptions>
        </section>

        <section class="erp-surface audit-detail-section">
          <h2 class="erp-section-title">请求信息</h2>
          <el-descriptions :column="1" border>
            <el-descriptions-item label="Request ID">{{ emptyText(currentLog.requestId) }}</el-descriptions-item>
            <el-descriptions-item label="IP">{{ emptyText(currentLog.ipAddress) }}</el-descriptions-item>
            <el-descriptions-item label="方法">{{ emptyText(currentLog.requestMethod) }}</el-descriptions-item>
            <el-descriptions-item label="路径">{{ emptyText(currentLog.requestPath) }}</el-descriptions-item>
            <el-descriptions-item label="User-Agent">{{ emptyText(currentLog.userAgent) }}</el-descriptions-item>
          </el-descriptions>
        </section>

        <section class="erp-surface audit-json-section">
          <div class="audit-json-header">
            <h2 class="erp-section-title">操作前数据</h2>
            <el-button size="small" @click="copyJson(formatAuditJson(currentLog.beforeData))">复制</el-button>
          </div>
          <el-table
            v-if="localizedAuditRows(currentLog.beforeData).length"
            :data="localizedAuditRows(currentLog.beforeData)"
            border
            size="small"
            class="audit-json-table"
          >
            <el-table-column prop="label" label="字段" width="150" show-overflow-tooltip />
            <el-table-column prop="value" label="内容" show-overflow-tooltip />
          </el-table>
          <pre class="audit-json-block">{{ formatAuditJson(currentLog.beforeData) }}</pre>
        </section>

        <section class="erp-surface audit-json-section">
          <div class="audit-json-header">
            <h2 class="erp-section-title">操作后数据</h2>
            <el-button size="small" @click="copyJson(formatAuditJson(currentLog.afterData))">复制</el-button>
          </div>
          <el-table
            v-if="localizedAuditRows(currentLog.afterData).length"
            :data="localizedAuditRows(currentLog.afterData)"
            border
            size="small"
            class="audit-json-table"
          >
            <el-table-column prop="label" label="字段" width="150" show-overflow-tooltip />
            <el-table-column prop="value" label="内容" show-overflow-tooltip />
          </el-table>
          <pre class="audit-json-block">{{ formatAuditJson(currentLog.afterData) }}</pre>
        </section>
      </div>
    </el-drawer>

    <el-drawer v-model="retentionVisible" title="日志保留策略" size="520px" class="audit-retention-drawer">
      <div v-loading="retentionLoading" class="audit-retention">
        <section class="erp-surface audit-detail-section">
          <h2 class="erp-section-title">自动清理设置</h2>
          <el-alert
            title="开启后，系统会按设置的执行时间自动归档并清理超过保留天数的操作日志。关闭后不会自动删除日志。"
            type="info"
            :closable="false"
            show-icon
            class="retention-alert"
          />
          <el-form label-width="132px" class="retention-form">
            <el-form-item label="自动清理">
              <el-switch
                v-model="retentionForm.enabled"
                active-text="开启"
                inactive-text="关闭"
              />
            </el-form-item>
            <el-form-item label="保留天数">
              <el-input-number
                v-model="retentionForm.retentionDays"
                :min="1"
                :max="3650"
                :step="30"
                controls-position="right"
              />
              <span class="retention-form-tip">超过该天数的日志会进入清理范围。</span>
            </el-form-item>
            <el-form-item label="清理前归档">
              <el-switch
                v-model="retentionForm.archiveBeforeCleanup"
                active-text="导出 CSV 后删除"
                inactive-text="直接删除"
              />
            </el-form-item>
            <el-form-item label="执行时间">
              <el-input v-model.trim="retentionForm.cleanupTime" placeholder="02:00" class="retention-time-input" />
              <span class="retention-form-tip">使用 24 小时 HH:mm 格式。</span>
            </el-form-item>
          </el-form>
        </section>

        <section class="erp-surface audit-detail-section">
          <h2 class="erp-section-title">最近一次执行</h2>
          <el-descriptions :column="1" border>
            <el-descriptions-item label="状态">{{ retentionStatusLabel(retentionForm.lastRunStatus) }}</el-descriptions-item>
            <el-descriptions-item label="时间">{{ formatDateTime(retentionForm.lastRunAt) }}</el-descriptions-item>
            <el-descriptions-item label="清理数量">{{ retentionForm.lastRunDeletedCount ?? 0 }}</el-descriptions-item>
            <el-descriptions-item label="说明">{{ emptyText(retentionForm.lastRunMessage) }}</el-descriptions-item>
            <el-descriptions-item label="归档文件">{{ emptyText(retentionForm.lastArchiveFile) }}</el-descriptions-item>
          </el-descriptions>
        </section>

        <div class="retention-footer">
          <el-button @click="retentionVisible = false">取消</el-button>
          <el-button v-permission="'system:audit:retention'" :loading="isPending(auditActionKeys.cleanupRetention)" :disabled="isPending(auditActionKeys.cleanupRetention)" @click="runRetentionCleanup">立即执行一次</el-button>
          <el-button v-permission="'system:audit:retention'" type="primary" :loading="isPending(auditActionKeys.saveRetention)" :disabled="isPending(auditActionKeys.saveRetention)" @click="saveRetentionSetting">保存设置</el-button>
        </div>
      </div>
    </el-drawer>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  exportAuditLogs,
  getAuditLog,
  getAuditLogOptions,
  getAuditLogRetentionSetting,
  listAuditLogs,
  runAuditLogRetentionCleanup,
  updateAuditLogRetentionSetting
} from '../services/auditApi'
import { auditDataRows, formatAuditJson } from '../utils/auditLogFormat'
import { usePendingAction } from '../composables/usePendingAction'

const MODULE_LABELS = {
  AUTH: '认证',
  SYSTEM: '系统',
  DICT: '字典',
  ENGINEER: '工程',
  MARKET: '市场',
  ORDER: '订单',
  DELIVERY: '交付'
}

const ROLE_LABELS = {
  ADMIN: '管理员',
  MARKET: '市场',
  ENGINEER: '工程',
  SYSTEM: '系统'
}

const loading = ref(false)
const retentionVisible = ref(false)
const retentionLoading = ref(false)
const detailVisible = ref(false)
const advancedFiltersVisible = ref(false)
const currentLog = ref(null)
const logs = ref([])
const { isPending, runAction } = usePendingAction()

const auditActionKeys = {
  exportLogs: 'system:audit:export',
  saveRetention: 'system:audit:retention-save',
  cleanupRetention: 'system:audit:retention-cleanup'
}
const pagination = reactive({
  page: 1,
  pageSize: 20,
  total: 0
})
const optionData = reactive({
  roles: ['ADMIN', 'MARKET', 'ENGINEER', 'SYSTEM'],
  modules: ['AUTH', 'SYSTEM', 'DICT', 'ENGINEER', 'MARKET', 'ORDER', 'DELIVERY'],
  actions: [],
  targetTypes: []
})
const filters = reactive({
  timeRange: defaultTimeRange(),
  username: '',
  role: '',
  module: '',
  action: '',
  targetType: '',
  targetId: '',
  requestId: '',
  result: '',
  keyword: ''
})
const retentionForm = reactive({
  enabled: false,
  retentionDays: 180,
  archiveBeforeCleanup: true,
  cleanupTime: '02:00',
  lastRunAt: '',
  lastRunDeletedCount: 0,
  lastRunStatus: 'NEVER',
  lastRunMessage: '',
  lastArchiveFile: ''
})

const actionOptions = computed(() => {
  if (!filters.module) return optionData.actions
  return optionData.actions.filter((action) => action.module === filters.module)
})

const loadOptions = async () => {
  try {
    const response = await getAuditLogOptions()
    optionData.roles = response?.roles?.length ? response.roles : optionData.roles
    optionData.modules = response?.modules?.length ? response.modules : optionData.modules
    optionData.actions = normalizeActions(response?.actions || [])
    optionData.targetTypes = response?.targetTypes || []
  } catch (error) {
    ElMessage.warning(error.response?.data?.message || '操作日志筛选项加载失败。')
  }
}

const loadLogs = async () => {
  loading.value = true
  try {
    const response = await listAuditLogs(buildParams())
    logs.value = response?.records || []
    pagination.total = response?.total || 0
    pagination.page = response?.page || pagination.page
    pagination.pageSize = response?.pageSize || pagination.pageSize
  } catch (error) {
    ElMessage.error(error.response?.data?.message || '操作日志加载失败。')
  } finally {
    loading.value = false
  }
}

const openRetentionSetting = async () => {
  retentionVisible.value = true
  await loadRetentionSetting()
}

const loadRetentionSetting = async () => {
  retentionLoading.value = true
  try {
    applyRetentionSetting(await getAuditLogRetentionSetting())
  } catch (error) {
    ElMessage.error(error.response?.data?.message || error.message || '日志保留策略加载失败。')
  } finally {
    retentionLoading.value = false
  }
}

const exportLogs = async () => {
  return runAction(auditActionKeys.exportLogs, async () => {
    const result = await exportAuditLogs(buildExportParams())
    if (result?.success === false) {
      ElMessage.error(result.message || '操作日志导出失败。')
      return
    }
    ElMessage.success('操作日志 CSV 已开始下载。')
  })
}

const saveRetentionSetting = async () => {
  return runAction(auditActionKeys.saveRetention, async () => {
    try {
      const response = await updateAuditLogRetentionSetting(buildRetentionPayload())
      applyRetentionSetting(response?.data || response)
      ElMessage.success(response?.message || '操作日志保留策略已更新。')
    } catch (error) {
      ElMessage.error(error.response?.data?.message || error.message || '操作日志保留策略保存失败。')
    }
  })
}

const runRetentionCleanup = async () => {
  return runAction(auditActionKeys.cleanupRetention, async () => {
    try {
      await ElMessageBox.confirm(
        '系统会按当前策略归档并清理到期操作日志。是否立即执行一次？',
        '立即执行日志清理',
        { type: 'warning', confirmButtonText: '执行', cancelButtonText: '取消' }
      )
    } catch {
      return
    }
    try {
      const response = await runAuditLogRetentionCleanup()
      const result = response?.data || response
      retentionForm.lastRunAt = result?.runAt || ''
      retentionForm.lastRunDeletedCount = result?.deletedCount || 0
      retentionForm.lastRunStatus = result?.status || retentionForm.lastRunStatus
      retentionForm.lastRunMessage = result?.message || response?.message || ''
      retentionForm.lastArchiveFile = result?.archiveFile || ''
      ElMessage.success(response?.message || result?.message || '操作日志清理已执行。')
      await loadLogs()
    } catch (error) {
      ElMessage.error(error.response?.data?.message || error.message || '操作日志清理执行失败。')
    }
  })
}
const searchLogs = () => {
  pagination.page = 1
  loadLogs()
}

const resetFilters = () => {
  filters.timeRange = defaultTimeRange()
  filters.username = ''
  filters.role = ''
  filters.module = ''
  filters.action = ''
  filters.targetType = ''
  filters.targetId = ''
  filters.requestId = ''
  filters.result = ''
  filters.keyword = ''
  searchLogs()
}

const openDetail = async (row) => {
  currentLog.value = row
  detailVisible.value = true
  try {
    currentLog.value = await getAuditLog(row.id)
  } catch (error) {
    ElMessage.error(error.response?.data?.message || '操作日志详情加载失败。')
  }
}

const buildParams = () => {
  const params = {
    page: pagination.page,
    pageSize: pagination.pageSize
  }
  if (filters.timeRange?.[0]) params.startTime = filters.timeRange[0]
  if (filters.timeRange?.[1]) params.endTime = filters.timeRange[1]
  for (const key of ['username', 'role', 'module', 'action', 'targetType', 'targetId', 'requestId', 'result', 'keyword']) {
    if (filters[key]) params[key] = filters[key]
  }
  return params
}


const buildExportParams = () => {
  const params = { ...buildParams() }
  delete params.page
  delete params.pageSize
  return params
}

const buildRetentionPayload = () => ({
  enabled: retentionForm.enabled,
  retentionDays: retentionForm.retentionDays,
  archiveBeforeCleanup: retentionForm.archiveBeforeCleanup,
  cleanupTime: retentionForm.cleanupTime
})

const applyRetentionSetting = (setting = {}) => {
  retentionForm.enabled = !!setting.enabled
  retentionForm.retentionDays = setting.retentionDays || 180
  retentionForm.archiveBeforeCleanup = setting.archiveBeforeCleanup !== false
  retentionForm.cleanupTime = setting.cleanupTime || '02:00'
  retentionForm.lastRunAt = setting.lastRunAt || ''
  retentionForm.lastRunDeletedCount = setting.lastRunDeletedCount || 0
  retentionForm.lastRunStatus = setting.lastRunStatus || 'NEVER'
  retentionForm.lastRunMessage = setting.lastRunMessage || ''
  retentionForm.lastArchiveFile = setting.lastArchiveFile || ''
}

const localizedAuditRows = (value) => auditDataRows(value)

const copyJson = async (text) => {
  try {
    await navigator.clipboard.writeText(text || '')
    ElMessage.success('已复制到剪贴板。')
  } catch {
    ElMessage.error('复制失败，请手动选中内容复制。')
  }
}
const normalizeActions = (actions) => actions.map((action) => (
  typeof action === 'string'
    ? { value: action, label: action, module: '' }
    : { value: action.value, label: action.label || action.value, module: action.module || '' }
)).filter((action) => action.value)

const formatDateTime = (value) => {
  if (!value) return '-'
  return String(value).replace('T', ' ').slice(0, 19)
}


const targetLabel = (row) => {
  const targetType = row?.targetType || '-'
  const targetId = row?.targetId || '-'
  return `${targetType} / ${targetId}`
}

const resultLabel = (result) => ({
  SUCCESS: '成功',
  FAILED: '失败'
}[result] || result || '成功')

const resultTagType = (result) => result === 'FAILED' ? 'danger' : 'success'

const retentionStatusLabel = (status) => ({
  NEVER: '未执行',
  SUCCESS: '成功',
  FAILED: '失败',
  DISABLED: '未开启'
}[status] || status || '未执行')

const moduleLabel = (module) => MODULE_LABELS[module] || module || '-'

const roleLabel = (role) => ROLE_LABELS[role] || role || '-'

const emptyText = (value) => value || '-'

function defaultTimeRange() {
  const end = new Date()
  const start = new Date(end.getTime() - 7 * 24 * 60 * 60 * 1000)
  return [formatDateForQuery(start), formatDateForQuery(end)]
}

function formatDateForQuery(date) {
  const pad = (value) => String(value).padStart(2, '0')
  return [
    date.getFullYear(),
    pad(date.getMonth() + 1),
    pad(date.getDate())
  ].join('-') + ' ' + [
    pad(date.getHours()),
    pad(date.getMinutes()),
    pad(date.getSeconds())
  ].join(':')
}

onMounted(async () => {
  await loadOptions()
  await loadLogs()
})
</script>

<style scoped>
.audit-toolbar {
  align-items: stretch;
  flex-direction: column;
  gap: 10px;
  padding: 12px;
  box-sizing: border-box;
}

.audit-toolbar-main {
  display: flex;
  width: 100%;
  gap: 12px;
  row-gap: 8px;
  column-gap: 10px;
  align-items: flex-start;
  justify-content: space-between;
  box-sizing: border-box;
}

.audit-primary-filters {
  flex: 1 1 760px;
  min-width: 0;
  justify-content: flex-start;
  row-gap: 8px;
  column-gap: 10px;
  box-sizing: border-box;
}

.audit-advanced-filters {
  width: 100%;
  padding: 12px;
  border: 1px solid var(--erp-border-soft);
  border-radius: 6px;
  background: var(--erp-surface-soft);
  justify-content: flex-start;
  row-gap: 8px;
  column-gap: 10px;
  box-sizing: border-box;
}

.audit-time-range {
  width: 360px;
  flex: 0 0 360px;
}

.audit-filter-control {
  width: 140px;
  flex: 0 0 140px;
}

.audit-keyword-control {
  width: 180px;
  flex: 0 0 180px;
}

.audit-request-id-control {
  width: 220px;
  flex: 0 0 220px;
}

.audit-table-surface .erp-table-body {
  padding: var(--erp-space-3);
}

.audit-detail {
  display: flex;
  flex-direction: column;
  gap: var(--erp-space-4);
}

.audit-retention {
  display: flex;
  flex-direction: column;
  gap: var(--erp-space-4);
}

.retention-alert {
  margin-bottom: var(--erp-space-4);
}

.retention-form {
  margin-top: var(--erp-space-3);
}

.retention-time-input {
  width: 160px;
}

.retention-form-tip {
  margin-left: var(--erp-space-3);
  color: var(--erp-text-muted);
  font-size: var(--erp-font-size-sm);
}

.retention-footer {
  display: flex;
  justify-content: flex-end;
  gap: var(--erp-space-2);
  padding: var(--erp-space-3) 0 0;
}

.audit-detail-section,
.audit-json-section {
  padding: var(--erp-space-4);
}

.audit-detail-section .erp-section-title,
.audit-json-section .erp-section-title {
  margin-bottom: var(--erp-space-3);
}

.audit-json-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--erp-space-3);
  margin-bottom: var(--erp-space-3);
}

.audit-json-table {
  margin-bottom: var(--erp-space-3);
}

.audit-json-table :deep(.cell) {
  white-space: pre-wrap;
  word-break: break-word;
}

.audit-json-block {
  max-height: 280px;
  overflow: auto;
  margin: 0;
  padding: var(--erp-space-3);
  color: var(--erp-text);
  font-family: Consolas, "Courier New", monospace;
  font-size: var(--erp-font-size-sm);
  line-height: 1.55;
  white-space: pre-wrap;
  word-break: break-word;
  background: var(--erp-bg-subtle);
  border: 1px solid var(--erp-border-soft);
  border-radius: var(--erp-radius-lg);
}

@media (max-width: 1280px) {
  .audit-toolbar-main {
    flex-direction: column;
    align-items: stretch;
  }

  .audit-primary-filters {
    justify-content: flex-start;
  }
}

@media (max-width: 760px) {
  .audit-primary-filters .audit-filter-control,
  .audit-advanced-filters .audit-filter-control,
  .audit-advanced-filters .audit-request-id-control,
  .audit-advanced-filters .audit-keyword-control {
    width: auto;
    min-width: 120px;
    flex: 1 1 140px;
  }

  .audit-time-range {
    width: 100%;
    flex: 1 1 100%;
  }
}
</style>
