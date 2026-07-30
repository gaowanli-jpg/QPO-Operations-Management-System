<template>
  <div class="erp-page system-config-page" v-permission="'system:config:view'">
    <header class="erp-page-header system-config-page-header">
      <div>
        <h1 class="erp-page-title">系统配置</h1>
        <p class="erp-page-description">管理文件存储策略和 MinIO 连接。</p>
      </div>
      <div class="erp-page-actions">
        <el-button v-permission="'system:config:storage:update'" type="primary" @click="openCreate">新增存储配置</el-button>
        <el-button :loading="loading" @click="loadAll">刷新</el-button>
      </div>
    </header>

    <section class="erp-surface system-config-active-card">
      <div class="erp-section-header">
        <h2 class="erp-section-title">当前激活策略</h2>
        <el-tag v-if="activeConfig" :type="storageTagType(activeConfig.storageType)">
          {{ storageTypeLabel(activeConfig.storageType) }}
        </el-tag>
      </div>
      <div v-if="activeConfig" class="system-config-active-body">
        <el-descriptions :column="2" border>
          <el-descriptions-item label="配置名称">{{ emptyText(activeConfig.name) }}</el-descriptions-item>
          <el-descriptions-item label="存储类型">{{ storageTypeLabel(activeConfig.storageType) }}</el-descriptions-item>
          <el-descriptions-item label="存储位置">{{ storageEndpoint(activeConfig) }}</el-descriptions-item>
          <el-descriptions-item label="Bucket">{{ emptyText(activeConfig.bucket) }}</el-descriptions-item>
          <el-descriptions-item label="启用状态">{{ activeConfig.enabled === false ? '停用' : '启用' }}</el-descriptions-item>
          <el-descriptions-item label="最后测试">{{ formatTestSummary(activeConfig) }}</el-descriptions-item>
        </el-descriptions>
      </div>
      <div v-else class="erp-empty-state">暂无激活的文件存储配置</div>
    </section>

    <section class="erp-table-surface system-config-table-surface">
      <div class="erp-table-header">
        <h2 class="erp-table-title">配置列表</h2>
        <span class="system-config-table-note">激活配置只影响之后上传的新文件，历史文件按元数据继续读取。</span>
      </div>
      <div class="erp-table-body">
        <el-table :data="configs" border v-loading="loading" empty-text="暂无文件存储配置">
          <el-table-column prop="name" label="配置名称" min-width="180" show-overflow-tooltip />
          <el-table-column label="类型" width="110">
            <template #default="{ row }">
              <el-tag :type="storageTagType(row.storageType)" effect="plain">{{ storageTypeLabel(row.storageType) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="状态" width="120">
            <template #default="{ row }">
              <el-tag v-if="row.active" type="success">当前激活</el-tag>
              <el-tag v-else-if="row.enabled === false" type="info">已停用</el-tag>
              <el-tag v-else type="warning">待激活</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="存储位置" min-width="260" show-overflow-tooltip>
            <template #default="{ row }">{{ storageEndpoint(row) }}</template>
          </el-table-column>
          <el-table-column prop="bucket" label="Bucket" min-width="150" show-overflow-tooltip />
          <el-table-column label="最后测试" min-width="200" show-overflow-tooltip>
            <template #default="{ row }">{{ formatTestSummary(row) }}</template>
          </el-table-column>
          <el-table-column prop="remark" label="备注" min-width="220" show-overflow-tooltip />
          <el-table-column label="操作" width="300" fixed="right">
            <template #default="{ row }">
              <div class="erp-table-actions system-config-row-actions">
                <el-button v-permission="'system:config:storage:update'" size="small" :disabled="isRowPending(row)" @click="openEdit(row)">编辑配置</el-button>
                <el-button
                  v-permission="'system:config:storage:test'"
                  size="small"
                  :loading="isPending(systemConfigActionKeys.test(row.id))"
                  :disabled="isRowPending(row)"
                  @click="testConfig(row)"
                >
                  测试连接
                </el-button>
                <el-button
                  v-permission="'system:config:storage:activate'"
                  size="small"
                  type="success"
                  plain
                  :loading="isPending(systemConfigActionKeys.activate(row.id))"
                  :disabled="row.active || row.enabled === false || isRowPending(row)"
                  @click="activateConfig(row)"
                >
                  激活配置
                </el-button>
                <el-button
                  v-permission="'system:config:storage:update'"
                  size="small"
                  type="warning"
                  plain
                  :loading="isPending(systemConfigActionKeys.disable(row.id))"
                  :disabled="row.active || row.enabled === false || isRowPending(row)"
                  @click="disableConfig(row)"
                >
                  停用
                </el-button>
              </div>
            </template>
          </el-table-column>
        </el-table>
      </div>
    </section>

    <el-dialog v-model="dialogVisible" :title="form.id ? '编辑配置' : '新增存储配置'" width="680px">
      <el-form ref="formRef" :model="form" label-width="132px" class="erp-dialog-form system-config-form">
        <el-form-item label="配置名称" required>
          <el-input v-model.trim="form.name" maxlength="100" show-word-limit placeholder="例如 本地上传目录 / 测试 MinIO" />
        </el-form-item>
        <el-form-item label="存储类型" required>
          <el-segmented v-model="form.storageType" :options="storageTypeOptions" :disabled="!!form.id" />
        </el-form-item>
        <el-form-item v-if="form.storageType === 'LOCAL'" label="本地根目录" required>
          <el-input v-model.trim="form.rootDir" placeholder="例如 /data/uploads" />
        </el-form-item>
        <template v-else>
          <el-form-item label="Endpoint" required>
            <el-input v-model.trim="form.endpoint" placeholder="例如 http://127.0.0.1:9000" />
          </el-form-item>
          <el-form-item label="Bucket" required>
            <el-input v-model.trim="form.bucket" placeholder="例如 qpo-drawings" />
          </el-form-item>
          <el-form-item label="Access Key" required>
            <el-input v-model.trim="form.accessKey" autocomplete="off" />
          </el-form-item>
          <el-form-item :label="form.id ? 'Access Secret' : 'Access Secret'" :required="!form.id">
            <el-input
              v-model="form.accessSecret"
              type="password"
              show-password
              autocomplete="new-password"
              :placeholder="form.id ? '留空沿用原密钥' : '请输入 MinIO secret'"
            />
            <span class="system-config-form-tip">编辑时不回显明文密钥；页面显示 ****** 仅代表已保存过密钥。</span>
          </el-form-item>
          <el-form-item label="Region">
            <el-input v-model.trim="form.region" placeholder="默认 us-east-1" />
          </el-form-item>
          <el-form-item label="访问选项">
            <div class="system-config-switches">
              <el-switch v-model="form.pathStyle" active-text="Path Style" />
              <el-switch v-model="form.publicReadEnabled" active-text="公开/代理可读" />
            </div>
          </el-form-item>
        </template>
        <el-form-item label="外部访问地址">
          <el-input v-model.trim="form.publicBaseUrl" placeholder="可选，例如 http://minio.internal/qpo-drawings" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model.trim="form.remark" type="textarea" :rows="3" maxlength="500" show-word-limit />
        </el-form-item>
      </el-form>
      <template #footer>
        <div class="erp-dialog-footer">
          <el-button @click="dialogVisible = false">取消</el-button>
          <el-button
            v-permission="'system:config:storage:update'"
            type="primary"
            :loading="isPending(systemConfigActionKeys.saveDialog)"
            :disabled="isPending(systemConfigActionKeys.saveDialog)"
            @click="saveConfig"
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
  activateStorageConfig,
  disableStorageConfig,
  getActiveStorageConfig,
  listStorageConfigs,
  saveStorageConfig,
  testStorageConfig
} from '../services/systemConfigApi'
import { usePendingAction } from '../composables/usePendingAction'

const loading = ref(false)
const configs = ref([])
const activeConfig = ref(null)
const dialogVisible = ref(false)
const formRef = ref()
const { isPending, runAction } = usePendingAction()

const systemConfigActionKeys = {
  saveDialog: 'system:config:storage:save-dialog',
  test: (configId) => `system:config:storage:test:${configId || 'none'}`,
  activate: (configId) => `system:config:storage:activate:${configId || 'none'}`,
  disable: (configId) => `system:config:storage:disable:${configId || 'none'}`
}

const storageTypeOptions = [
  { label: 'LOCAL', value: 'LOCAL' },
  { label: 'MINIO', value: 'MINIO' }
]

const form = reactive({
  id: null,
  name: '',
  storageType: 'LOCAL',
  rootDir: '',
  endpoint: '',
  bucket: '',
  accessKey: '',
  accessSecret: '',
  region: 'us-east-1',
  pathStyle: true,
  publicReadEnabled: true,
  publicBaseUrl: '',
  remark: '',
  enabled: true
})

const requestErrorMessage = (error, fallback) => error?.response?.data?.message || error?.payload?.message || error?.message || fallback

const normalizeStorageConfig = (row = {}) => {
  const config = row.configJson || row.config || {}
  return {
    ...row,
    storageType: row.storageType || row.storage_type || config.storageType || 'LOCAL',
    active: row.active === true,
    enabled: row.enabled !== false,
    rootDir: row.rootDir || row.root_dir || config.rootDir || '',
    endpoint: row.endpoint || config.endpoint || '',
    bucket: row.bucket || config.bucket || '',
    accessKey: row.accessKey || config.accessKey || '',
    accessSecret: row.accessSecret || config.accessSecret || '',
    region: row.region || config.region || 'us-east-1',
    pathStyle: row.pathStyle ?? config.pathStyle ?? true,
    publicReadEnabled: row.publicReadEnabled ?? config.publicReadEnabled ?? false,
    publicBaseUrl: row.publicBaseUrl || config.publicBaseUrl || '',
    lastTestStatus: row.lastTestStatus || row.last_test_status || '',
    lastTestMessage: row.lastTestMessage || row.last_test_message || '',
    lastTestAt: row.lastTestAt || row.last_test_at || '',
    remark: row.remark || ''
  }
}

const normalizeRows = (response) => {
  const rows = Array.isArray(response)
    ? response
    : (response?.records || response?.data || [])
  return (Array.isArray(rows) ? rows : []).map(normalizeStorageConfig)
}

const loadAll = async () => {
  loading.value = true
  try {
    const [listResponse, activeResponse] = await Promise.all([
      listStorageConfigs(),
      getActiveStorageConfig()
    ])
    configs.value = normalizeRows(listResponse)
    activeConfig.value = activeResponse ? normalizeStorageConfig(activeResponse.data || activeResponse) : configs.value.find(row => row.active) || null
  } catch (error) {
    configs.value = []
    activeConfig.value = null
    ElMessage.error(requestErrorMessage(error, '文件存储配置加载失败'))
  } finally {
    loading.value = false
  }
}

const resetForm = () => {
  form.id = null
  form.name = ''
  form.storageType = 'LOCAL'
  form.rootDir = ''
  form.endpoint = ''
  form.bucket = ''
  form.accessKey = ''
  form.accessSecret = ''
  form.region = 'us-east-1'
  form.pathStyle = true
  form.publicReadEnabled = true
  form.publicBaseUrl = ''
  form.remark = ''
  form.enabled = true
  formRef.value?.clearValidate?.()
}

const openCreate = () => {
  resetForm()
  dialogVisible.value = true
}

const openEdit = (row) => {
  const current = normalizeStorageConfig(row)
  form.id = current.id
  form.name = current.name || ''
  form.storageType = current.storageType
  form.rootDir = current.rootDir || ''
  form.endpoint = current.endpoint || ''
  form.bucket = current.bucket || ''
  form.accessKey = current.accessKey || ''
  form.accessSecret = ''
  form.region = current.region || 'us-east-1'
  form.pathStyle = current.pathStyle !== false
  form.publicReadEnabled = current.publicReadEnabled === true
  form.publicBaseUrl = current.publicBaseUrl || ''
  form.remark = current.remark || ''
  form.enabled = current.enabled !== false
  formRef.value?.clearValidate?.()
  dialogVisible.value = true
}

const validateForm = () => {
  if (!form.name) {
    ElMessage.warning('请填写配置名称')
    return false
  }
  if (form.storageType === 'LOCAL') {
    if (!form.rootDir) {
      ElMessage.warning('请填写本地根目录')
      return false
    }
    return true
  }
  if (!form.endpoint || !form.bucket || !form.accessKey) {
    ElMessage.warning('请填写 MinIO endpoint、bucket 和 accessKey')
    return false
  }
  if (!form.id && !form.accessSecret) {
    ElMessage.warning('新增 MinIO 配置必须填写 accessSecret')
    return false
  }
  return true
}

const buildPayload = () => {
  const configJson = form.storageType === 'LOCAL'
    ? {
        rootDir: form.rootDir,
        publicBaseUrl: form.publicBaseUrl || null
      }
    : {
        endpoint: form.endpoint,
        bucket: form.bucket,
        accessKey: form.accessKey,
        region: form.region || 'us-east-1',
        pathStyle: form.pathStyle,
        publicReadEnabled: form.publicReadEnabled,
        publicBaseUrl: form.publicBaseUrl || null,
        ...(form.accessSecret ? { accessSecret: form.accessSecret } : {})
      }

  return {
    id: form.id,
    name: form.name,
    storageType: form.storageType,
    enabled: form.enabled !== false,
    remark: form.remark,
    configJson,
    rootDir: form.rootDir,
    endpoint: form.endpoint,
    bucket: form.bucket,
    accessKey: form.accessKey,
    accessSecret: form.accessSecret || undefined,
    region: form.region,
    pathStyle: form.pathStyle,
    publicReadEnabled: form.publicReadEnabled,
    publicBaseUrl: form.publicBaseUrl
  }
}

const saveConfig = async () => {
  if (!validateForm()) return
  return runAction(systemConfigActionKeys.saveDialog, async () => {
    try {
      const response = await saveStorageConfig(buildPayload())
      ElMessage.success(response?.message || '文件存储配置已保存')
      dialogVisible.value = false
      await loadAll()
    } catch (error) {
      ElMessage.error(requestErrorMessage(error, '文件存储配置保存失败'))
    }
  })
}

const testConfig = async (row) => {
  return runAction(systemConfigActionKeys.test(row.id), async () => {
    try {
      const response = await testStorageConfig(row.id)
      ElMessage.success(response?.message || '测试连接完成')
      await loadAll()
    } catch (error) {
      ElMessage.error(requestErrorMessage(error, '测试连接失败，请检查配置和网络'))
    }
  })
}

const activateConfig = async (row) => {
  return runAction(systemConfigActionKeys.activate(row.id), async () => {
    try {
      await ElMessageBox.confirm(
        `确认激活配置 ${row.name || row.id}？激活只影响之后上传的新文件，历史文件保持原存储位置。`,
        '激活配置',
        { type: 'warning', confirmButtonText: '激活', cancelButtonText: '取消' }
      )
    } catch {
      return
    }
    try {
      const response = await activateStorageConfig(row.id)
      ElMessage.success(response?.message || '文件存储配置已激活')
      await loadAll()
    } catch (error) {
      ElMessage.error(requestErrorMessage(error, '激活配置失败'))
    }
  })
}

const disableConfig = async (row) => {
  return runAction(systemConfigActionKeys.disable(row.id), async () => {
    try {
      await ElMessageBox.confirm(`确认停用配置 ${row.name || row.id}？已激活配置不能停用。`, '停用配置', { type: 'warning' })
    } catch {
      return
    }
    try {
      const response = await disableStorageConfig(row.id)
      ElMessage.success(response?.message || '文件存储配置已停用')
      await loadAll()
    } catch (error) {
      ElMessage.error(requestErrorMessage(error, '停用配置失败'))
    }
  })
}

const isRowPending = (row) => (
  isPending(systemConfigActionKeys.test(row?.id))
  || isPending(systemConfigActionKeys.activate(row?.id))
  || isPending(systemConfigActionKeys.disable(row?.id))
)

const storageTypeLabel = (type) => ({
  LOCAL: 'LOCAL 本地',
  MINIO: 'MINIO 对象存储'
}[type] || type || '-')

const storageTagType = (type) => type === 'MINIO' ? 'success' : 'info'

const storageEndpoint = (row) => {
  if (!row) return '-'
  if (row.storageType === 'MINIO') {
    return row.publicBaseUrl || [row.endpoint, row.bucket].filter(Boolean).join(' / ') || '-'
  }
  return row.publicBaseUrl || row.rootDir || '-'
}

const formatDateTime = (value) => {
  if (!value) return ''
  return String(value).replace('T', ' ').slice(0, 19)
}

const formatTestSummary = (row) => {
  if (!row?.lastTestStatus && !row?.lastTestAt) return '未测试'
  const status = row.lastTestStatus === 'SUCCESS' ? '成功' : row.lastTestStatus === 'FAILED' ? '失败' : row.lastTestStatus
  return [status, formatDateTime(row.lastTestAt), row.lastTestMessage].filter(Boolean).join(' / ')
}

const emptyText = (value) => value || '-'

onMounted(loadAll)
</script>

<style scoped>
.system-config-active-card .erp-section-header,
.system-config-table-surface .erp-table-header {
  border-radius: calc(var(--erp-radius-lg) - 1px) calc(var(--erp-radius-lg) - 1px) 0 0;
}

.system-config-active-body {
  padding: var(--erp-space-4);
}

.system-config-table-note,
.system-config-form-tip {
  color: var(--erp-text-secondary);
  font-size: var(--erp-font-size-sm);
}

.system-config-table-surface .erp-table-body {
  padding: var(--erp-space-3);
}

.system-config-row-actions {
  justify-content: flex-start;
}

.system-config-form {
  max-width: 620px;
}

.system-config-switches {
  display: flex;
  flex-wrap: wrap;
  gap: var(--erp-space-4);
}

@media (max-width: 760px) {
  .system-config-form {
    max-width: none;
  }
}
</style>
