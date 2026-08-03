<template>
  <div class="system-integration-config-panel">
    <section class="erp-surface system-config-panel-toolbar">
      <div>
        <h2 class="erp-section-title">出站接口</h2>
        <p class="erp-page-description">管理 QPO 出站接口/数据中心配置；数据中心负责转发到 MES、WMS、U9，旧直连配置仅作为 deprecated fallback。</p>
      </div>
      <div class="erp-page-actions system-integration-create-actions">
        <el-button v-permission="'system:config:integration:update'" type="primary" @click="openCreate('U9')">新增直连 U9 fallback</el-button>
        <el-button v-permission="'system:config:integration:update'" @click="openCreate('MES')">新增直连 MES fallback</el-button>
        <el-button v-permission="'system:config:integration:update'" @click="openCreate('WMS')">新增直连 WMS fallback</el-button>
        <el-button :loading="loading" @click="loadAll">刷新</el-button>
      </div>
    </section>

    <section class="erp-surface system-integration-active-card">
      <div class="erp-section-header">
        <h2 class="erp-section-title">当前出站配置概览</h2>
        <span class="system-config-table-note">数据中心连接配置为新口径；旧 U9 / MES / WMS 激活配置仅用于兼容 fallback。</span>
      </div>
      <div class="system-integration-summary-grid">
        <article v-for="system in systemCards" :key="system.code" class="system-integration-summary-card">
          <div class="system-integration-summary-title">
            <strong>{{ system.label }}</strong>
            <el-tag :type="system.active ? 'success' : system.reserved ? 'info' : 'warning'" effect="plain">
              {{ system.active ? '已激活' : system.reserved ? '预留' : '未配置' }}
            </el-tag>
          </div>
          <p class="system-integration-summary-line">{{ system.name }}</p>
          <p class="system-integration-summary-line">{{ system.baseUrl }}</p>
          <p class="system-integration-summary-line">{{ system.lastTest }}</p>
        </article>
      </div>
    </section>

    <section class="erp-surface system-integration-data-center-card">
      <div class="erp-section-header">
        <h2 class="erp-section-title">数据中心连接配置</h2>
        <el-tag type="success" effect="plain">新口径</el-tag>
      </div>
      <p class="system-config-table-note">用于维护 QPO 调用数据中心的 Base URL、Token URL、Client ID、Client Secret、grant_type 和超时；页面配置缺失时回退到 ERP_DATA_CENTER_* 环境变量。</p>
      <div class="system-data-center-endpoint-grid">
        <article class="system-data-center-endpoint-card">
          <strong>MES_MATERIAL_RESOLVE</strong>
          <span>获取物料号 / MES / POST</span>
        </article>
        <article class="system-data-center-endpoint-card">
          <strong>WMS_INVENTORY_ALLOCATE</strong>
          <span>获取库存信息 / WMS / POST</span>
        </article>
        <article class="system-data-center-endpoint-card">
          <strong>U9_SALES_ORDER_SYNC</strong>
          <span>U9同步 / U9 / POST</span>
        </article>
        <article class="system-data-center-endpoint-card">
          <strong>MES_PRODUCTION_DISPATCH</strong>
          <span>下发 MES 生产 / MES / POST</span>
        </article>
      </div>
      <p class="system-config-table-note">功能接口配置按 endpointCode 唯一维护 path、method、timeout、requestAdapter、responseAdapter 和说明性 schema；写操作继续由 system:config:integration:update 控制。</p>
    </section>

    <section class="erp-table-surface system-config-table-surface">
      <div class="erp-table-header">
        <h2 class="erp-table-title">直连 fallback 配置列表</h2>
        <span class="system-config-table-note">仅用于数据中心配置缺失或应急回退；密钥字段仅展示 ******，留空或传 ****** 时后端沿用原密钥。</span>
      </div>
      <div class="erp-table-body">
        <el-table :data="configs" border v-loading="loading" empty-text="暂无出站接口配置">
          <el-table-column label="系统" width="90">
            <template #default="{ row }">
              <el-tag effect="plain">{{ row.systemCode }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="name" label="配置名称" min-width="170" show-overflow-tooltip />
          <el-table-column prop="baseUrl" label="Base URL" min-width="230" show-overflow-tooltip />
          <el-table-column label="认证方式" width="150">
            <template #default="{ row }">{{ authTypeLabel(row.authType) }}</template>
          </el-table-column>
          <el-table-column label="状态" width="120">
            <template #default="{ row }">
              <el-tag v-if="row.active" type="success">已激活</el-tag>
              <el-tag v-else-if="row.enabled === false" type="info">已停用</el-tag>
              <el-tag v-else type="warning">待激活</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="最后测试" min-width="210" show-overflow-tooltip>
            <template #default="{ row }">{{ formatTestSummary(row) }}</template>
          </el-table-column>
          <el-table-column prop="remark" label="备注" min-width="220" show-overflow-tooltip />
          <el-table-column label="操作" width="330" fixed="right">
            <template #default="{ row }">
              <div class="erp-table-actions system-config-row-actions">
                <el-button v-permission="'system:config:integration:update'" size="small" :disabled="isIntegrationRowPending(row)" @click="openEdit(row)">编辑配置</el-button>
                <el-button
                  v-permission="'system:config:integration:test'"
                  size="small"
                  :loading="isPending(integrationActionKeys.test(row.id))"
                  :disabled="isIntegrationRowPending(row)"
                  @click="testIntegrationConfigRow(row)"
                >
                  测试连接
                </el-button>
                <el-button
                  v-permission="'system:config:integration:activate'"
                  size="small"
                  type="success"
                  plain
                  :loading="isPending(integrationActionKeys.activate(row.id))"
                  :disabled="row.active || row.enabled === false || isIntegrationRowPending(row)"
                  @click="activateIntegrationConfigRow(row)"
                >
                  激活配置
                </el-button>
                <el-button
                  v-permission="'system:config:integration:update'"
                  size="small"
                  type="warning"
                  plain
                  :loading="isPending(integrationActionKeys.disable(row.id))"
                  :disabled="row.active || row.enabled === false || isIntegrationRowPending(row)"
                  @click="disableIntegrationConfigRow(row)"
                >
                  停用
                </el-button>
              </div>
            </template>
          </el-table-column>
        </el-table>
      </div>
    </section>

    <el-dialog v-model="dialogVisible" :title="form.id ? '编辑出站接口配置' : '新增出站接口配置'" width="760px">
      <el-form ref="formRef" :model="form" label-width="160px" class="erp-dialog-form system-integration-form">
        <el-form-item label="系统类型" required>
          <el-segmented v-model="form.systemCode" :options="systemOptions" :disabled="!!form.id" @change="syncAuthType" />
        </el-form-item>
        <el-form-item label="配置名称" required>
          <el-input v-model.trim="form.name" maxlength="100" show-word-limit placeholder="例如 MES 测试环境 / U9 正式环境" />
        </el-form-item>
        <el-form-item label="Base URL" required>
          <el-input v-model.trim="form.baseUrl" placeholder="例如 https://u9.example.com" />
        </el-form-item>
        <el-form-item label="认证方式" required>
          <el-select v-model="form.authType" class="system-integration-form-control">
            <el-option v-for="item in authTypeOptions" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
        </el-form-item>

        <template v-if="form.systemCode === 'MES'">
          <el-form-item label="materialPath" required>
            <el-input v-model.trim="form.materialPath" placeholder="/api/mes/materials/resolve" />
          </el-form-item>
          <el-form-item label="authToken">
            <el-input v-model="form.authToken" type="password" show-password autocomplete="new-password" :placeholder="form.id ? '留空或 ****** 沿用原 token' : 'Bearer token，可为空'" />
            <span class="system-config-form-tip">列表和弹窗不展示明文 token，****** 表示后端已保存密钥。</span>
          </el-form-item>
          <el-form-item label="timeoutMs">
            <el-input-number v-model="form.timeoutMs" :min="1000" :step="1000" class="system-integration-form-control" />
          </el-form-item>
        </template>

        <template v-else-if="form.systemCode === 'WMS'">
          <el-form-item label="allocationPath" required>
            <el-input v-model.trim="form.allocationPath" placeholder="/api/wms/inventory/allocate" />
          </el-form-item>
          <el-form-item label="authToken">
            <el-input v-model="form.authToken" type="password" show-password autocomplete="new-password" :placeholder="form.id ? '留空或 ****** 沿用原 token' : 'Bearer token，可为空'" />
            <span class="system-config-form-tip">列表和弹窗不展示明文 token，****** 表示后端已保存密钥。</span>
          </el-form-item>
          <el-form-item label="timeoutMs">
            <el-input-number v-model="form.timeoutMs" :min="1000" :step="1000" class="system-integration-form-control" />
          </el-form-item>
        </template>

        <template v-else>
          <el-form-item label="authLoginPath" required>
            <el-input v-model.trim="form.authLoginPath" placeholder="/webapi/OAuth2/AuthLogin" />
          </el-form-item>
          <el-form-item label="salesOrderCreatePath" required>
            <el-input v-model.trim="form.salesOrderCreatePath" placeholder="/webapi/SO/Create" />
          </el-form-item>
          <el-form-item label="userCode" required>
            <el-input v-model.trim="form.userCode" autocomplete="off" />
          </el-form-item>
          <el-form-item label="entCode" required>
            <el-input v-model.trim="form.entCode" autocomplete="off" />
          </el-form-item>
          <el-form-item label="orgCode" required>
            <el-input v-model.trim="form.orgCode" autocomplete="off" />
          </el-form-item>
          <el-form-item label="clientId" required>
            <el-input v-model.trim="form.clientId" autocomplete="off" />
          </el-form-item>
          <el-form-item :label="form.id ? 'clientSecret' : 'clientSecret'" :required="!form.id">
            <el-input v-model="form.clientSecret" type="password" show-password autocomplete="new-password" :placeholder="form.id ? '留空或 ****** 沿用原 secret' : '请输入 U9 clientSecret'" />
            <span class="system-config-form-tip">新增时必须填写 clientSecret；编辑时留空或传 ****** 会沿用原密钥。</span>
          </el-form-item>
          <el-form-item label="connectTimeoutMs">
            <el-input-number v-model="form.connectTimeoutMs" :min="1000" :step="1000" class="system-integration-form-control" />
          </el-form-item>
          <el-form-item label="readTimeoutMs">
            <el-input-number v-model="form.readTimeoutMs" :min="1000" :step="1000" class="system-integration-form-control" />
          </el-form-item>
        </template>

        <el-form-item label="启用状态">
          <el-switch v-model="form.enabled" active-text="允许激活" inactive-text="停用" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model.trim="form.remark" type="textarea" :rows="3" maxlength="500" show-word-limit />
        </el-form-item>
      </el-form>
      <template #footer>
        <div class="erp-dialog-footer">
          <el-button @click="dialogVisible = false">取消</el-button>
          <el-button
            v-permission="'system:config:integration:update'"
            type="primary"
            :loading="isPending(integrationActionKeys.saveDialog)"
            :disabled="isPending(integrationActionKeys.saveDialog)"
            @click="saveIntegrationConfigForm"
          >
            保存
          </el-button>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  activateIntegrationConfig,
  disableIntegrationConfig,
  getActiveIntegrationConfigs,
  listIntegrationConfigs,
  saveIntegrationConfig,
  testIntegrationConfig as testIntegrationConfigRequest
} from '../../services/systemConfigApi'
import { usePendingAction } from '../../composables/usePendingAction'

const loading = ref(false)
const configs = ref([])
const activeConfigs = ref([])
const dialogVisible = ref(false)
const formRef = ref()
const { isPending, runAction } = usePendingAction()

const integrationActionKeys = {
  saveDialog: 'system:config:integration:save-dialog',
  test: (configId) => `system:config:integration:test:${configId || 'none'}`,
  activate: (configId) => `system:config:integration:activate:${configId || 'none'}`,
  disable: (configId) => `system:config:integration:disable:${configId || 'none'}`
}

const systemOptions = [
  { label: 'U9', value: 'U9' },
  { label: 'MES', value: 'MES' },
  { label: 'WMS', value: 'WMS' }
]

const authTypeOptions = [
  { label: 'NONE', value: 'NONE' },
  { label: 'BEARER_TOKEN', value: 'BEARER_TOKEN' },
  { label: 'U9_AUTH_LOGIN', value: 'U9_AUTH_LOGIN' }
]

const defaultPaths = {
  U9: { authLoginPath: '/webapi/OAuth2/AuthLogin', salesOrderCreatePath: '/webapi/SO/Create' },
  MES: { materialPath: '/api/mes/materials/resolve' },
  WMS: { allocationPath: '/api/wms/inventory/allocate' }
}

const form = reactive({
  id: null,
  systemCode: 'U9',
  name: '',
  enabled: true,
  authType: 'U9_AUTH_LOGIN',
  baseUrl: '',
  materialPath: defaultPaths.MES.materialPath,
  allocationPath: defaultPaths.WMS.allocationPath,
  authToken: '',
  timeoutMs: 10000,
  authLoginPath: defaultPaths.U9.authLoginPath,
  salesOrderCreatePath: defaultPaths.U9.salesOrderCreatePath,
  userCode: '',
  entCode: '',
  orgCode: '',
  clientId: '',
  clientSecret: '',
  connectTimeoutMs: 5000,
  readTimeoutMs: 30000,
  remark: ''
})

const requestErrorMessage = (error, fallback) => error?.response?.data?.message || error?.payload?.message || error?.message || fallback
const emptyText = (value) => value || '-'

const normalizeIntegrationConfig = (row = {}) => ({
  ...row,
  systemCode: row.systemCode || 'U9',
  active: row.active === true,
  enabled: row.enabled !== false,
  authType: row.authType || (row.systemCode === 'U9' ? 'U9_AUTH_LOGIN' : (row.authToken ? 'BEARER_TOKEN' : 'NONE')),
  baseUrl: row.baseUrl || '',
  materialPath: row.materialPath || defaultPaths.MES.materialPath,
  allocationPath: row.allocationPath || defaultPaths.WMS.allocationPath,
  authToken: row.authToken || '',
  timeoutMs: row.timeoutMs || 10000,
  authLoginPath: row.authLoginPath || defaultPaths.U9.authLoginPath,
  salesOrderCreatePath: row.salesOrderCreatePath || defaultPaths.U9.salesOrderCreatePath,
  clientSecret: row.clientSecret || '',
  connectTimeoutMs: row.connectTimeoutMs || 5000,
  readTimeoutMs: row.readTimeoutMs || 30000,
  lastTestStatus: row.lastTestStatus || '',
  lastTestMessage: row.lastTestMessage || '',
  lastTestAt: row.lastTestAt || '',
  remark: row.remark || ''
})

const normalizeRows = (response) => {
  const rows = Array.isArray(response) ? response : (response?.records || response?.data || [])
  return (Array.isArray(rows) ? rows : []).map(normalizeIntegrationConfig)
}

const loadAll = async () => {
  loading.value = true
  try {
    const [listResponse, activeResponse] = await Promise.all([
      listIntegrationConfigs(),
      getActiveIntegrationConfigs()
    ])
    configs.value = normalizeRows(listResponse)
    activeConfigs.value = normalizeRows(activeResponse)
  } catch (error) {
    configs.value = []
    activeConfigs.value = []
    ElMessage.error(requestErrorMessage(error, '出站接口配置加载失败'))
  } finally {
    loading.value = false
  }
}

const systemCards = computed(() => [
  ...systemOptions.map(option => {
    const active = activeConfigs.value.find(row => row.systemCode === option.value) || configs.value.find(row => row.systemCode === option.value && row.active)
    return {
      code: option.value,
      label: option.label,
      active: !!active,
      reserved: false,
      name: active?.name || '未配置激活配置',
      baseUrl: active?.baseUrl || '未配置 Base URL',
      lastTest: formatTestSummary(active)
    }
  }),
  {
    code: 'DATA_CENTER',
    label: '数据中心',
    active: false,
    reserved: true,
    name: '数据中心连接配置',
    baseUrl: 'ERP_DATA_CENTER_* / 页面配置 fallback',
    lastTest: '功能接口配置按 endpointCode 唯一维护'
  }
])

const resetForm = (systemCode = 'U9') => {
  form.id = null
  form.systemCode = systemCode
  form.name = ''
  form.enabled = true
  form.baseUrl = ''
  form.materialPath = defaultPaths.MES.materialPath
  form.allocationPath = defaultPaths.WMS.allocationPath
  form.authToken = ''
  form.timeoutMs = 10000
  form.authLoginPath = defaultPaths.U9.authLoginPath
  form.salesOrderCreatePath = defaultPaths.U9.salesOrderCreatePath
  form.userCode = ''
  form.entCode = ''
  form.orgCode = ''
  form.clientId = ''
  form.clientSecret = ''
  form.connectTimeoutMs = 5000
  form.readTimeoutMs = 30000
  form.remark = ''
  syncAuthType()
  formRef.value?.clearValidate?.()
}

const syncAuthType = () => {
  form.authType = form.systemCode === 'U9' ? 'U9_AUTH_LOGIN' : (form.authToken ? 'BEARER_TOKEN' : 'NONE')
}

const openCreate = (systemCode) => {
  resetForm(systemCode)
  dialogVisible.value = true
}

const openEdit = (row) => {
  const current = normalizeIntegrationConfig(row)
  resetForm(current.systemCode)
  Object.assign(form, {
    ...current,
    authToken: current.authToken === '******' ? '******' : '',
    clientSecret: current.clientSecret === '******' ? '******' : ''
  })
  dialogVisible.value = true
}

const validateForm = () => {
  if (!form.name) {
    ElMessage.warning('请填写配置名称')
    return false
  }
  if (!form.baseUrl) {
    ElMessage.warning('请填写 Base URL')
    return false
  }
  if (form.systemCode === 'MES' && !form.materialPath) {
    ElMessage.warning('请填写 materialPath')
    return false
  }
  if (form.systemCode === 'WMS' && !form.allocationPath) {
    ElMessage.warning('请填写 allocationPath')
    return false
  }
  if (form.systemCode === 'U9') {
    if (!form.authLoginPath || !form.salesOrderCreatePath || !form.userCode || !form.entCode || !form.orgCode || !form.clientId) {
      ElMessage.warning('请填写 U9 必填字段')
      return false
    }
    if (!form.id && !form.clientSecret) {
      ElMessage.warning('新增直连 U9 fallback 必须填写 clientSecret')
      return false
    }
  }
  return true
}

const secretOrUndefined = (value) => (!value || value === '******') ? undefined : value

const buildPayload = () => ({
  id: form.id,
  systemCode: form.systemCode,
  name: form.name,
  enabled: form.enabled !== false,
  authType: form.authType,
  baseUrl: form.baseUrl,
  materialPath: form.systemCode === 'MES' ? form.materialPath : undefined,
  allocationPath: form.systemCode === 'WMS' ? form.allocationPath : undefined,
  authToken: ['MES', 'WMS'].includes(form.systemCode) ? secretOrUndefined(form.authToken) : undefined,
  timeoutMs: ['MES', 'WMS'].includes(form.systemCode) ? form.timeoutMs : undefined,
  authLoginPath: form.systemCode === 'U9' ? form.authLoginPath : undefined,
  salesOrderCreatePath: form.systemCode === 'U9' ? form.salesOrderCreatePath : undefined,
  userCode: form.systemCode === 'U9' ? form.userCode : undefined,
  entCode: form.systemCode === 'U9' ? form.entCode : undefined,
  orgCode: form.systemCode === 'U9' ? form.orgCode : undefined,
  clientId: form.systemCode === 'U9' ? form.clientId : undefined,
  clientSecret: form.systemCode === 'U9' ? secretOrUndefined(form.clientSecret) : undefined,
  connectTimeoutMs: form.systemCode === 'U9' ? form.connectTimeoutMs : undefined,
  readTimeoutMs: form.systemCode === 'U9' ? form.readTimeoutMs : undefined,
  remark: form.remark
})

const saveIntegrationConfigForm = async () => {
  if (!validateForm()) return
  return runAction(integrationActionKeys.saveDialog, async () => {
    try {
      const response = await saveIntegrationConfig(buildPayload())
      ElMessage.success(response?.message || '出站接口配置已保存')
      dialogVisible.value = false
      await loadAll()
    } catch (error) {
      ElMessage.error(requestErrorMessage(error, '出站接口配置保存失败'))
    }
  })
}

const testIntegrationConfigRow = async (row) => {
  return runAction(integrationActionKeys.test(row.id), async () => {
    try {
      const response = await testIntegrationConfigRequest(row.id)
      ElMessage.success(response?.message || '测试连接完成')
      await loadAll()
    } catch (error) {
      ElMessage.error(requestErrorMessage(error, '测试连接失败，请检查配置和网络'))
    }
  })
}

const activateIntegrationConfigRow = async (row) => {
  return runAction(integrationActionKeys.activate(row.id), async () => {
    try {
      await ElMessageBox.confirm(`确认激活 ${row.systemCode} 配置 ${row.name || row.id}？同一系统只允许一个激活配置。`, '激活出站接口配置', {
        type: 'warning',
        confirmButtonText: '激活',
        cancelButtonText: '取消'
      })
    } catch {
      return
    }
    try {
      const response = await activateIntegrationConfig(row.id)
      ElMessage.success(response?.message || '出站接口配置已激活')
      await loadAll()
    } catch (error) {
      ElMessage.error(requestErrorMessage(error, '激活配置失败'))
    }
  })
}

const disableIntegrationConfigRow = async (row) => {
  return runAction(integrationActionKeys.disable(row.id), async () => {
    try {
      await ElMessageBox.confirm(`确认停用 ${row.systemCode} 配置 ${row.name || row.id}？已激活配置不能停用。`, '停用出站接口配置', { type: 'warning' })
    } catch {
      return
    }
    try {
      const response = await disableIntegrationConfig(row.id)
      ElMessage.success(response?.message || '出站接口配置已停用')
      await loadAll()
    } catch (error) {
      ElMessage.error(requestErrorMessage(error, '停用配置失败'))
    }
  })
}

const isIntegrationRowPending = (row) => (
  isPending(integrationActionKeys.test(row?.id))
  || isPending(integrationActionKeys.activate(row?.id))
  || isPending(integrationActionKeys.disable(row?.id))
)

const authTypeLabel = (value) => value || 'NONE'

const formatDateTime = (value) => {
  if (!value) return ''
  return String(value).replace('T', ' ').slice(0, 19)
}

const formatTestSummary = (row) => {
  if (!row?.lastTestStatus && !row?.lastTestAt) return '未测试'
  const status = row.lastTestStatus === 'SUCCESS' ? '成功' : row.lastTestStatus === 'FAILED' ? '失败' : row.lastTestStatus
  return [status, formatDateTime(row.lastTestAt), row.lastTestMessage].filter(Boolean).join(' / ')
}

onMounted(loadAll)
</script>

<style scoped>
.system-integration-config-panel {
  display: grid;
  gap: var(--erp-space-4);
}

.system-config-panel-toolbar {
  align-items: center;
  display: flex;
  gap: var(--erp-space-4);
  justify-content: space-between;
  padding: var(--erp-space-4);
}

.system-integration-create-actions,
.system-config-row-actions {
  flex-wrap: wrap;
  justify-content: flex-start;
}

.system-integration-active-card .erp-section-header,
.system-integration-data-center-card .erp-section-header,
.system-config-table-surface .erp-table-header {
  border-radius: calc(var(--erp-radius-lg) - 1px) calc(var(--erp-radius-lg) - 1px) 0 0;
}

.system-integration-summary-grid {
  display: grid;
  gap: var(--erp-space-3);
  grid-template-columns: repeat(auto-fit, minmax(220px, 1fr));
  padding: var(--erp-space-4);
}

.system-integration-summary-card {
  border: 1px solid var(--erp-border-color);
  border-radius: var(--erp-radius-md);
  padding: var(--erp-space-3);
}

.system-integration-summary-title {
  align-items: center;
  display: flex;
  justify-content: space-between;
  margin-bottom: var(--erp-space-2);
}

.system-integration-summary-line,
.system-config-table-note,
.system-config-form-tip {
  color: var(--erp-text-secondary);
  font-size: var(--erp-font-size-sm);
  margin: 0;
}

.system-integration-data-center-card {
  padding-bottom: var(--erp-space-4);
}

.system-integration-data-center-card .system-config-table-note {
  padding: 0 var(--erp-space-4);
}

.system-data-center-endpoint-grid {
  display: grid;
  gap: var(--erp-space-3);
  grid-template-columns: repeat(auto-fit, minmax(230px, 1fr));
  padding: var(--erp-space-4);
}

.system-data-center-endpoint-card {
  background: var(--erp-surface-muted);
  border: 1px solid var(--erp-border-color);
  border-radius: var(--erp-radius-md);
  display: grid;
  gap: var(--erp-space-1);
  padding: var(--erp-space-3);
}

.system-data-center-endpoint-card strong {
  color: var(--erp-text-primary);
  font-family: var(--erp-font-family-mono, monospace);
  font-size: var(--erp-font-size-sm);
}

.system-data-center-endpoint-card span {
  color: var(--erp-text-secondary);
  font-size: var(--erp-font-size-sm);
}

.system-config-table-surface .erp-table-body {
  padding: var(--erp-space-3);
}

.system-integration-form {
  max-width: 700px;
}

.system-integration-form-control {
  width: 240px;
}

@media (max-width: 760px) {
  .system-config-panel-toolbar {
    align-items: flex-start;
    flex-direction: column;
  }

  .system-integration-form,
  .system-integration-form-control {
    max-width: none;
    width: 100%;
  }
}
</style>
