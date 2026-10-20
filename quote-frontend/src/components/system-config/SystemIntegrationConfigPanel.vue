<template>
  <div class="system-integration-config-panel">
    <section class="erp-surface system-config-panel-toolbar">
      <div>
        <h2 class="erp-section-title">出站接口</h2>
        <p class="erp-page-description">统一配置数据中心连接，并按业务功能维护 MES / WMS / U9 的数据中心接口 path。</p>
      </div>
      <div class="erp-page-actions system-integration-create-actions">
        <el-button v-permission="'system:config:integration:update'" type="primary" @click="openDataCenterCreate">新增数据中心配置</el-button>
        <el-button :loading="loading" @click="loadAll">刷新</el-button>
      </div>
    </section>

    <section class="erp-surface system-data-center-overview">
      <div class="erp-section-header">
        <h2 class="erp-section-title">当前数据中心链路</h2>
        <el-tag :type="activeDataCenterConfig ? 'success' : 'warning'" effect="plain">
          {{ activeDataCenterConfig ? '已激活' : '未激活' }}
        </el-tag>
      </div>
      <div class="system-data-center-overview-grid">
        <article class="system-data-center-overview-card">
          <span>统一认证</span>
          <strong>{{ activeDataCenterConfig?.name || '未配置数据中心连接' }}</strong>
          <p>{{ activeDataCenterConfig?.baseUrl || '请新增并激活数据中心连接配置' }}</p>
        </article>
        <article class="system-data-center-overview-card">
          <span>业务转发</span>
          <strong>MES / WMS / U9</strong>
          <p>QPO 只调用数据中心，由数据中心转发到各业务系统。</p>
        </article>
        <article class="system-data-center-overview-card">
          <span>接口治理</span>
          <strong>{{ enabledEndpointCount }} / {{ endpointConfigs.length }} 已启用</strong>
          <p>每个功能独立维护 path、timeoutMs、enabled、remark。</p>
        </article>
      </div>
    </section>

    <section class="erp-table-surface system-config-table-surface">
      <div class="erp-table-header">
        <div>
          <h2 class="erp-table-title">数据中心连接配置</h2>
          <span class="system-config-table-note">维护 QPO 获取数据中心 token 所需的 Base URL、Token URL、Client ID、clientSecret、grant_type 和超时。</span>
        </div>
      </div>
      <div class="erp-table-body">
        <el-table :data="dataCenterConfigs" border v-loading="loading" empty-text="暂无数据中心连接配置">
          <el-table-column prop="name" label="配置名称" min-width="170" show-overflow-tooltip />
          <el-table-column prop="baseUrl" label="Base URL" min-width="230" show-overflow-tooltip />
          <el-table-column prop="tokenUrl" label="Token URL" min-width="260" show-overflow-tooltip />
          <el-table-column prop="clientId" label="Client ID" min-width="170" show-overflow-tooltip />
          <el-table-column label="clientSecret" width="140">
            <template #default="{ row }">
              <el-tag :type="row.clientSecretConfigured ? 'success' : 'warning'" effect="plain">
                {{ row.clientSecretConfigured ? '******' : '未配置' }}
              </el-tag>
            </template>
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
          <el-table-column prop="remark" label="remark" min-width="190" show-overflow-tooltip />
          <el-table-column label="操作" width="330" fixed="right">
            <template #default="{ row }">
              <div class="erp-table-actions system-config-row-actions">
                <el-button v-permission="'system:config:integration:update'" size="small" :disabled="isDataCenterRowPending(row)" @click="openDataCenterEdit(row)">编辑连接</el-button>
                <el-button
                  v-permission="'system:config:integration:test'"
                  size="small"
                  :loading="isPending(dataCenterActionKeys.testToken(row.id))"
                  :disabled="isDataCenterRowPending(row)"
                  @click="testDataCenterTokenRow(row)"
                >
                  测试 token
                </el-button>
                <el-button
                  v-permission="'system:config:integration:activate'"
                  size="small"
                  type="success"
                  plain
                  :loading="isPending(dataCenterActionKeys.activate(row.id))"
                  :disabled="row.active || row.enabled === false || isDataCenterRowPending(row)"
                  @click="activateDataCenterConfigRow(row)"
                >
                  激活连接
                </el-button>
                <el-button
                  v-permission="'system:config:integration:update'"
                  size="small"
                  type="warning"
                  plain
                  :loading="isPending(dataCenterActionKeys.disable(row.id))"
                  :disabled="row.active || row.enabled === false || isDataCenterRowPending(row)"
                  @click="disableDataCenterConfigRow(row)"
                >
                  停用
                </el-button>
              </div>
            </template>
          </el-table-column>
        </el-table>
      </div>
    </section>

    <section class="erp-table-surface system-config-table-surface">
      <div class="erp-table-header">
        <div>
          <h2 class="erp-table-title">功能接口配置</h2>
          <span class="system-config-table-note">每个 endpointCode 独立配置数据中心 path、timeoutMs、enabled、remark；业务含义仍对应 MES / WMS / U9。</span>
          <span class="system-config-table-note">默认功能：MES_MATERIAL_RESOLVE / WMS_INVENTORY_ALLOCATE / U9_SALES_ORDER_SYNC / MES_PRODUCTION_DISPATCH</span>
        </div>
      </div>
      <div class="erp-table-body">
        <el-table :data="endpointConfigs" border v-loading="loading" empty-text="暂无功能接口配置">
          <el-table-column prop="endpointCode" label="endpointCode" min-width="210" show-overflow-tooltip />
          <el-table-column prop="displayName" label="功能" min-width="140" show-overflow-tooltip />
          <el-table-column prop="targetSystemCode" label="targetSystemCode" width="150">
            <template #default="{ row }">
              <el-tag effect="plain">{{ row.targetSystemCode }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="method" label="方法" width="90" />
          <el-table-column prop="path" label="path" min-width="240" show-overflow-tooltip />
          <el-table-column prop="timeoutMs" label="timeoutMs" width="120" />
          <el-table-column label="enabled" width="110">
            <template #default="{ row }">
              <el-tag :type="row.enabled === false ? 'info' : 'success'" effect="plain">{{ row.enabled === false ? '停用' : '启用' }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="requestAdapter" label="requestAdapter" min-width="190" show-overflow-tooltip />
          <el-table-column prop="responseAdapter" label="responseAdapter" min-width="210" show-overflow-tooltip />
          <el-table-column prop="remark" label="remark" min-width="200" show-overflow-tooltip />
          <el-table-column label="操作" width="230" fixed="right">
            <template #default="{ row }">
              <div class="erp-table-actions system-config-row-actions">
                <el-button v-permission="'system:config:integration:update'" size="small" :disabled="isEndpointRowPending(row)" @click="openEndpointEdit(row)">编辑功能接口</el-button>
                <el-button
                  v-permission="'system:config:integration:test'"
                  size="small"
                  :loading="isPending(dataCenterActionKeys.endpointTest(row.endpointCode))"
                  :disabled="isEndpointRowPending(row)"
                  @click="testDataCenterEndpointRow(row)"
                >
                  测试接口
                </el-button>
              </div>
            </template>
          </el-table-column>
        </el-table>
      </div>
    </section>

    <el-collapse class="system-integration-legacy-collapse">
      <el-collapse-item title="直连 fallback 兼容区" name="legacy">
        <div class="system-integration-legacy-note">
          旧 U9 / MES / WMS 直连配置仅用于数据中心配置缺失或应急 fallback，新出站主链路以数据中心连接配置和功能接口配置为准。
        </div>
        <el-table :data="legacyConfigs" border size="small" v-loading="loading" empty-text="暂无直连 fallback 配置">
          <el-table-column prop="systemCode" label="系统" width="90" />
          <el-table-column prop="name" label="配置名称" min-width="170" show-overflow-tooltip />
          <el-table-column prop="baseUrl" label="Base URL" min-width="240" show-overflow-tooltip />
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
        </el-table>
      </el-collapse-item>
    </el-collapse>

    <el-dialog v-model="dataCenterDialogVisible" :title="dataCenterForm.id ? '编辑数据中心连接' : '新增数据中心配置'" width="760px">
      <el-form ref="dataCenterFormRef" :model="dataCenterForm" label-width="170px" class="erp-dialog-form system-integration-form">
        <el-form-item label="配置名称" required>
          <el-input v-model.trim="dataCenterForm.name" maxlength="100" show-word-limit placeholder="例如 数据中心测试环境" />
        </el-form-item>
        <el-form-item label="Base URL" required>
          <el-input v-model.trim="dataCenterForm.baseUrl" placeholder="例如 http://192.168.70.250:8080" />
        </el-form-item>
        <el-form-item label="Token URL" required>
          <el-input v-model.trim="dataCenterForm.tokenUrl" placeholder="/realms/api-platform/protocol/openid-connect/token" />
        </el-form-item>
        <el-form-item label="Client ID" required>
          <el-input v-model.trim="dataCenterForm.clientId" autocomplete="off" />
        </el-form-item>
        <el-form-item :label="dataCenterForm.id ? 'clientSecret' : 'clientSecret'" :required="!dataCenterForm.id">
          <el-input v-model="dataCenterForm.clientSecret" type="password" show-password autocomplete="new-password" :placeholder="dataCenterForm.id ? '留空或 ****** 沿用原 secret' : '请输入数据中心 clientSecret'" />
          <span class="system-config-form-tip">明文只在本弹窗输入，不在列表、日志或文档中展示；****** 表示后端已保存密钥。</span>
        </el-form-item>
        <el-form-item label="grant_type">
          <el-input v-model.trim="dataCenterForm.grantType" disabled />
        </el-form-item>
        <el-form-item label="connectTimeoutMs">
          <el-input-number v-model="dataCenterForm.connectTimeoutMs" :min="1000" :step="1000" class="system-integration-form-control" />
        </el-form-item>
        <el-form-item label="readTimeoutMs">
          <el-input-number v-model="dataCenterForm.readTimeoutMs" :min="1000" :step="1000" class="system-integration-form-control" />
        </el-form-item>
        <el-form-item label="tokenExpireSkewSeconds">
          <el-input-number v-model="dataCenterForm.tokenExpireSkewSeconds" :min="0" :step="10" class="system-integration-form-control" />
        </el-form-item>
        <el-form-item label="enabled">
          <el-switch v-model="dataCenterForm.enabled" active-text="启用" inactive-text="停用" />
        </el-form-item>
        <el-form-item label="remark">
          <el-input v-model.trim="dataCenterForm.remark" type="textarea" :rows="3" maxlength="500" show-word-limit />
        </el-form-item>
      </el-form>
      <template #footer>
        <div class="erp-dialog-footer">
          <el-button @click="dataCenterDialogVisible = false">取消</el-button>
          <el-button
            v-permission="'system:config:integration:update'"
            type="primary"
            :loading="isPending(dataCenterActionKeys.saveDialog)"
            :disabled="isPending(dataCenterActionKeys.saveDialog)"
            @click="saveDataCenterConfigForm"
          >
            保存
          </el-button>
        </div>
      </template>
    </el-dialog>

    <el-dialog v-model="endpointDialogVisible" title="编辑功能接口" width="680px">
      <el-form ref="endpointFormRef" :model="endpointForm" label-width="150px" class="erp-dialog-form system-integration-form">
        <el-form-item label="endpointCode">
          <el-input v-model="endpointForm.endpointCode" disabled />
        </el-form-item>
        <el-form-item label="targetSystemCode">
          <el-input v-model="endpointForm.targetSystemCode" disabled />
        </el-form-item>
        <el-form-item label="方法">
          <el-segmented v-model="endpointForm.method" :options="methodOptions" />
        </el-form-item>
        <el-form-item label="path" required>
          <el-input v-model.trim="endpointForm.path" placeholder="数据中心分配的接口路径" />
        </el-form-item>
        <el-form-item label="timeoutMs">
          <el-input-number v-model="endpointForm.timeoutMs" :min="1000" :step="1000" class="system-integration-form-control" />
        </el-form-item>
        <el-form-item label="enabled">
          <el-switch v-model="endpointForm.enabled" active-text="启用" inactive-text="停用" />
        </el-form-item>
        <el-form-item label="requestAdapter">
          <el-input v-model="endpointForm.requestAdapter" disabled />
        </el-form-item>
        <el-form-item label="responseAdapter">
          <el-input v-model="endpointForm.responseAdapter" disabled />
        </el-form-item>
        <el-form-item label="remark">
          <el-input v-model.trim="endpointForm.remark" type="textarea" :rows="3" maxlength="500" show-word-limit />
        </el-form-item>
      </el-form>
      <template #footer>
        <div class="erp-dialog-footer">
          <el-button @click="endpointDialogVisible = false">取消</el-button>
          <el-button
            v-permission="'system:config:integration:update'"
            type="primary"
            :loading="isPending(dataCenterActionKeys.endpointSaveDialog)"
            :disabled="isPending(dataCenterActionKeys.endpointSaveDialog)"
            @click="saveDataCenterEndpointForm"
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
  activateDataCenterConfig,
  disableDataCenterConfig,
  listDataCenterConfigs,
  listDataCenterEndpoints,
  listIntegrationConfigs,
  saveDataCenterConfig,
  saveDataCenterEndpoint,
  testDataCenterEndpoint,
  testDataCenterToken
} from '../../services/systemConfigApi'
import { usePendingAction } from '../../composables/usePendingAction'

const loading = ref(false)
const dataCenterConfigs = ref([])
const endpointConfigs = ref([])
const legacyConfigs = ref([])
const dataCenterDialogVisible = ref(false)
const endpointDialogVisible = ref(false)
const dataCenterFormRef = ref()
const endpointFormRef = ref()
const { isPending, runAction } = usePendingAction()

const dataCenterActionKeys = {
  saveDialog: 'system:config:data-center:save-dialog',
  endpointSaveDialog: 'system:config:data-center-endpoint:save-dialog',
  testToken: (configId) => `system:config:data-center:test-token:${configId || 'none'}`,
  activate: (configId) => `system:config:data-center:activate:${configId || 'none'}`,
  disable: (configId) => `system:config:data-center:disable:${configId || 'none'}`,
  endpointTest: (endpointCode) => `system:config:data-center-endpoint:test:${endpointCode || 'none'}`
}

const methodOptions = [
  { label: 'POST', value: 'POST' },
  { label: 'GET', value: 'GET' }
]

const dataCenterForm = reactive({
  id: null,
  name: '',
  enabled: true,
  baseUrl: '',
  tokenUrl: '',
  clientId: '',
  clientSecret: '',
  grantType: 'client_credentials',
  connectTimeoutMs: 5000,
  readTimeoutMs: 30000,
  tokenExpireSkewSeconds: 60,
  remark: ''
})

const endpointForm = reactive({
  endpointCode: '',
  targetSystemCode: '',
  method: 'POST',
  path: '',
  enabled: true,
  timeoutMs: null,
  requestAdapter: '',
  responseAdapter: '',
  requestSchemaJson: {},
  responseSchemaJson: {},
  remark: ''
})

const requestErrorMessage = (error, fallback) => error?.response?.data?.message || error?.payload?.message || error?.message || fallback

const activeDataCenterConfig = computed(() => dataCenterConfigs.value.find(row => row.active) || null)
const enabledEndpointCount = computed(() => endpointConfigs.value.filter(row => row.enabled !== false).length)

const normalizeRows = (response, normalizer) => {
  const rows = Array.isArray(response) ? response : (response?.records || response?.data || [])
  return (Array.isArray(rows) ? rows : []).map(normalizer)
}

const normalizeDataCenterConfig = (row = {}) => ({
  ...row,
  active: row.active === true,
  enabled: row.enabled !== false,
  baseUrl: row.baseUrl || '',
  tokenUrl: row.tokenUrl || '',
  clientId: row.clientId || '',
  clientSecret: row.clientSecret || '',
  clientSecretConfigured: row.clientSecretConfigured === true || row.clientSecret === '******',
  grantType: row.grantType || 'client_credentials',
  connectTimeoutMs: row.connectTimeoutMs || 5000,
  readTimeoutMs: row.readTimeoutMs || 30000,
  tokenExpireSkewSeconds: row.tokenExpireSkewSeconds ?? 60,
  lastTestStatus: row.lastTestStatus || '',
  lastTestMessage: row.lastTestMessage || '',
  lastTestAt: row.lastTestAt || '',
  remark: row.remark || ''
})

const normalizeEndpointConfig = (row = {}) => ({
  ...row,
  endpointCode: row.endpointCode || '',
  displayName: row.displayName || '',
  targetSystemCode: row.targetSystemCode || '',
  method: row.method || 'POST',
  path: row.path || '',
  enabled: row.enabled !== false,
  timeoutMs: row.timeoutMs ?? null,
  requestAdapter: row.requestAdapter || '',
  responseAdapter: row.responseAdapter || '',
  requestSchemaJson: row.requestSchemaJson || {},
  responseSchemaJson: row.responseSchemaJson || {},
  lastTestStatus: row.lastTestStatus || '',
  lastTestMessage: row.lastTestMessage || '',
  lastTestAt: row.lastTestAt || '',
  remark: row.remark || ''
})

const normalizeLegacyConfig = (row = {}) => ({
  ...row,
  systemCode: row.systemCode || '-',
  active: row.active === true,
  enabled: row.enabled !== false,
  baseUrl: row.baseUrl || '',
  lastTestStatus: row.lastTestStatus || '',
  lastTestMessage: row.lastTestMessage || '',
  lastTestAt: row.lastTestAt || '',
  remark: row.remark || ''
})

const loadAll = async () => {
  loading.value = true
  try {
    const [dataCenterResponse, endpointResponse, legacyResponse] = await Promise.all([
      listDataCenterConfigs(),
      listDataCenterEndpoints(),
      listIntegrationConfigs()
    ])
    dataCenterConfigs.value = normalizeRows(dataCenterResponse, normalizeDataCenterConfig)
    endpointConfigs.value = normalizeRows(endpointResponse, normalizeEndpointConfig)
    legacyConfigs.value = normalizeRows(legacyResponse, normalizeLegacyConfig)
  } catch (error) {
    dataCenterConfigs.value = []
    endpointConfigs.value = []
    legacyConfigs.value = []
    ElMessage.error(requestErrorMessage(error, '出站接口配置加载失败'))
  } finally {
    loading.value = false
  }
}

const resetDataCenterForm = () => {
  dataCenterForm.id = null
  dataCenterForm.name = ''
  dataCenterForm.enabled = true
  dataCenterForm.baseUrl = ''
  dataCenterForm.tokenUrl = ''
  dataCenterForm.clientId = ''
  dataCenterForm.clientSecret = ''
  dataCenterForm.grantType = 'client_credentials'
  dataCenterForm.connectTimeoutMs = 5000
  dataCenterForm.readTimeoutMs = 30000
  dataCenterForm.tokenExpireSkewSeconds = 60
  dataCenterForm.remark = ''
  dataCenterFormRef.value?.clearValidate?.()
}

const openDataCenterCreate = () => {
  resetDataCenterForm()
  dataCenterDialogVisible.value = true
}

const openDataCenterEdit = (row) => {
  const current = normalizeDataCenterConfig(row)
  Object.assign(dataCenterForm, {
    ...current,
    clientSecret: current.clientSecretConfigured ? '******' : ''
  })
  dataCenterDialogVisible.value = true
}

const validateDataCenterForm = () => {
  if (!dataCenterForm.name || !dataCenterForm.baseUrl || !dataCenterForm.tokenUrl || !dataCenterForm.clientId) {
    ElMessage.warning('请填写数据中心配置名称、Base URL、Token URL 和 Client ID')
    return false
  }
  if (!dataCenterForm.id && !dataCenterForm.clientSecret) {
    ElMessage.warning('新增数据中心配置必须填写 clientSecret')
    return false
  }
  return true
}

const secretOrUndefined = (value) => (!value || value === '******') ? undefined : value

const buildDataCenterPayload = () => ({
  id: dataCenterForm.id,
  name: dataCenterForm.name,
  enabled: dataCenterForm.enabled !== false,
  baseUrl: dataCenterForm.baseUrl,
  tokenUrl: dataCenterForm.tokenUrl,
  clientId: dataCenterForm.clientId,
  clientSecret: secretOrUndefined(dataCenterForm.clientSecret),
  grantType: dataCenterForm.grantType || 'client_credentials',
  connectTimeoutMs: dataCenterForm.connectTimeoutMs,
  readTimeoutMs: dataCenterForm.readTimeoutMs,
  tokenExpireSkewSeconds: dataCenterForm.tokenExpireSkewSeconds,
  remark: dataCenterForm.remark
})

const saveDataCenterConfigForm = async () => {
  if (!validateDataCenterForm()) return
  return runAction(dataCenterActionKeys.saveDialog, async () => {
    try {
      const response = await saveDataCenterConfig(buildDataCenterPayload())
      ElMessage.success(response?.message || '数据中心连接配置已保存')
      dataCenterDialogVisible.value = false
      await loadAll()
    } catch (error) {
      ElMessage.error(requestErrorMessage(error, '数据中心连接配置保存失败'))
    }
  })
}

const testDataCenterTokenRow = async (row) => {
  return runAction(dataCenterActionKeys.testToken(row.id), async () => {
    try {
      const response = await testDataCenterToken(row.id)
      ElMessage.success(response?.message || '数据中心 token 测试完成')
      await loadAll()
    } catch (error) {
      ElMessage.error(requestErrorMessage(error, '数据中心 token 测试失败'))
    }
  })
}

const activateDataCenterConfigRow = async (row) => {
  return runAction(dataCenterActionKeys.activate(row.id), async () => {
    try {
      await ElMessageBox.confirm(`确认激活数据中心连接 ${row.name || row.id}？同一时间只允许一个数据中心连接激活。`, '激活数据中心连接', {
        type: 'warning',
        confirmButtonText: '激活',
        cancelButtonText: '取消'
      })
    } catch {
      return
    }
    try {
      const response = await activateDataCenterConfig(row.id)
      ElMessage.success(response?.message || '数据中心连接已激活')
      await loadAll()
    } catch (error) {
      ElMessage.error(requestErrorMessage(error, '激活数据中心连接失败'))
    }
  })
}

const disableDataCenterConfigRow = async (row) => {
  return runAction(dataCenterActionKeys.disable(row.id), async () => {
    try {
      await ElMessageBox.confirm(`确认停用数据中心连接 ${row.name || row.id}？已激活连接不能停用。`, '停用数据中心连接', { type: 'warning' })
    } catch {
      return
    }
    try {
      const response = await disableDataCenterConfig(row.id)
      ElMessage.success(response?.message || '数据中心连接已停用')
      await loadAll()
    } catch (error) {
      ElMessage.error(requestErrorMessage(error, '停用数据中心连接失败'))
    }
  })
}

const saveDataCenterEndpointForm = async () => {
  if (!endpointForm.endpointCode || !endpointForm.path) {
    ElMessage.warning('请填写功能接口 path')
    return
  }
  return runAction(dataCenterActionKeys.endpointSaveDialog, async () => {
    try {
      const response = await saveDataCenterEndpoint(endpointForm.endpointCode, buildEndpointPayload())
      ElMessage.success(response?.message || '功能接口配置已保存')
      endpointDialogVisible.value = false
      await loadAll()
    } catch (error) {
      ElMessage.error(requestErrorMessage(error, '功能接口配置保存失败'))
    }
  })
}

const testDataCenterEndpointRow = async (row) => {
  return runAction(dataCenterActionKeys.endpointTest(row.endpointCode), async () => {
    try {
      const response = await testDataCenterEndpoint(row.endpointCode)
      ElMessage.success(response?.message || '功能接口测试完成')
      await loadAll()
    } catch (error) {
      ElMessage.error(requestErrorMessage(error, '功能接口测试失败'))
    }
  })
}

const isDataCenterRowPending = (row) => (
  isPending(dataCenterActionKeys.testToken(row?.id))
  || isPending(dataCenterActionKeys.activate(row?.id))
  || isPending(dataCenterActionKeys.disable(row?.id))
)

const isEndpointRowPending = (row) => (
  isPending(dataCenterActionKeys.endpointTest(row?.endpointCode))
)

const openEndpointEdit = (row) => {
  Object.assign(endpointForm, normalizeEndpointConfig(row))
  endpointDialogVisible.value = true
}

const buildEndpointPayload = () => ({
  endpointCode: endpointForm.endpointCode,
  method: endpointForm.method || 'POST',
  path: endpointForm.path,
  enabled: endpointForm.enabled !== false,
  timeoutMs: endpointForm.timeoutMs,
  requestSchemaJson: endpointForm.requestSchemaJson || {},
  responseSchemaJson: endpointForm.responseSchemaJson || {},
  remark: endpointForm.remark
})

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

.system-data-center-overview .erp-section-header,
.system-config-table-surface .erp-table-header {
  border-radius: calc(var(--erp-radius-lg) - 1px) calc(var(--erp-radius-lg) - 1px) 0 0;
}

.system-data-center-overview-grid {
  display: grid;
  gap: var(--erp-space-3);
  grid-template-columns: repeat(auto-fit, minmax(240px, 1fr));
  padding: var(--erp-space-4);
}

.system-data-center-overview-card {
  background: var(--erp-surface-muted);
  border: 1px solid var(--erp-border-color);
  border-radius: var(--erp-radius-md);
  display: grid;
  gap: var(--erp-space-1);
  padding: var(--erp-space-3);
}

.system-data-center-overview-card span,
.system-config-table-note,
.system-config-form-tip,
.system-integration-legacy-note {
  color: var(--erp-text-secondary);
  font-size: var(--erp-font-size-sm);
}

.system-data-center-overview-card strong {
  color: var(--erp-text-primary);
}

.system-data-center-overview-card p,
.system-config-table-note {
  margin: 0;
}

.system-config-table-surface .erp-table-body {
  padding: var(--erp-space-3);
}

.system-integration-legacy-collapse {
  background: var(--erp-surface);
  border: 1px solid var(--erp-border-color);
  border-radius: var(--erp-radius-lg);
  padding: 0 var(--erp-space-3);
}

.system-integration-legacy-note {
  margin-bottom: var(--erp-space-3);
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
