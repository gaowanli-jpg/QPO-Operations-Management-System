<template>
  <div class="system-inbound-callback-config-panel">
    <section class="erp-surface system-inbound-callback-toolbar">
      <div class="system-inbound-title-block">
        <h2 class="erp-section-title">入站接口</h2>
        <p class="erp-page-description">管理外部系统调用 QPO 的开放接口入口、统一机器 token 和启用状态。</p>
      </div>
      <div class="erp-page-actions system-inbound-callback-actions">
        <div class="system-inbound-open-api-token-card">
          <div class="system-inbound-token-heading">
            <span>统一 token 状态</span>
            <el-tag :type="openApiTokenStatus.tokenConfigured ? 'success' : 'danger'" effect="plain">
              {{ openApiTokenStatus.tokenConfigured ? '已配置' : '未配置' }}
            </el-tag>
          </div>
          <strong>QPO_OPEN_API_TOKEN</strong>
          <span>{{ openApiTokenStatusText }}</span>
        </div>
        <el-button :loading="loading" @click="loadConfig">刷新</el-button>
        <el-button
          v-permission="'system:config:inbound:token-rotate'"
          type="warning"
          plain
          :loading="isPending(inboundActionKeys.rotateOpenApiToken)"
          :disabled="isPending(inboundActionKeys.rotateOpenApiToken)"
          @click="handleRotateOpenApiToken"
        >
          {{ openApiTokenStatus.tokenConfigured ? '轮换 token' : '生成 token' }}
        </el-button>
      </div>
    </section>

    <section class="erp-table-surface system-inbound-callback-table-surface">
      <div class="erp-table-header">
        <div>
          <h2 class="erp-table-title">入站接口配置</h2>
          <span class="system-inbound-table-note">调用方：数据中心；业务来源：MES / EXTERNAL_SALES_ORDER_SYNC。接口列表只维护路径、启停、来源系统和说明；MES 物料号回调、销售订单外部同步查询等入站开放接口统一使用 QPO_OPEN_API_TOKEN，不再按接口行生成独立 token。真实 token 需单独通过受控渠道交付数据中心或外部调用方。</span>
        </div>
      </div>

      <div class="erp-table-body">
        <el-table :data="inboundConfigs" border v-loading="loading" empty-text="暂无入站接口配置">
          <el-table-column label="接口名称" min-width="210" show-overflow-tooltip>
            <template #default="{ row }">
              <div class="system-inbound-name-cell">
                <strong>{{ row.name || callbackName(row) }}</strong>
                <span>{{ row.callbackCode }}</span>
              </div>
            </template>
          </el-table-column>
          <el-table-column label="来源系统" width="170">
            <template #default="{ row }">
              <el-tag type="info" effect="plain">{{ row.sourceSystemCode || '-' }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="接口路径" min-width="310" show-overflow-tooltip>
            <template #default="{ row }">
              <div class="system-inbound-url-cell">
                <div>
                  <strong>{{ emptyText(row.callbackPath) }}</strong>
                  <span>{{ emptyText(row.callbackUrl) }}</span>
                </div>
                <el-button size="small" @click="copyCallbackUrl(row)">复制回调地址</el-button>
              </div>
            </template>
          </el-table-column>
          <el-table-column label="接口状态" width="120">
            <template #default="{ row }">
              <el-tag :type="row.enabled ? 'success' : 'info'" effect="plain">
                {{ row.enabled ? '已启用' : '已停用' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="认证方式" width="190">
            <template #default="{ row }">
              <el-tag effect="plain">{{ row.authMode || OPEN_API_TOKEN_AUTH_MODE }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="configSource" label="配置来源" width="170">
            <template #default="{ row }">
              <el-tag :type="row.configSource === 'DB' ? 'success' : 'warning'" effect="plain">
                {{ configSourceLabel(row.configSource) }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="最近校验" min-width="230" show-overflow-tooltip>
            <template #default="{ row }">
              <div class="system-inbound-status-stack">
                <strong>{{ validateStatusText(row) }}</strong>
                <span>{{ validateMessageText(row) }}</span>
              </div>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="300" fixed="right">
            <template #default="{ row }">
              <div class="erp-table-actions system-inbound-row-actions">
                <el-button size="small" @click="openEditDrawer(row)">编辑</el-button>
                <el-button size="small" @click="copyCallbackUrl(row)">复制回调地址</el-button>
                <el-button
                  v-permission="'system:config:inbound:update'"
                  size="small"
                  :loading="isPending(inboundActionKeys.validateConfig)"
                  :disabled="!supportsConfigMaintenance(row) || isPending(inboundActionKeys.validateConfig)"
                  @click="validateConfig(row)"
                >
                  校验配置
                </el-button>
              </div>
            </template>
          </el-table-column>
        </el-table>
      </div>
    </section>

    <el-drawer
      v-model="editDrawerVisible"
      title="编辑入站接口"
      size="560px"
      destroy-on-close
      class="system-inbound-edit-drawer"
    >
      <div class="system-inbound-drawer-body">
        <section class="system-inbound-drawer-summary">
          <div>
            <span>接口名称</span>
            <strong>{{ selectedConfig.name || callbackName(selectedConfig) }}</strong>
          </div>
          <div>
            <span>接口编码</span>
            <strong>{{ selectedConfig.callbackCode }}</strong>
          </div>
          <div>
            <span>来源系统</span>
            <strong>{{ selectedConfig.sourceSystemCode || '-' }}</strong>
          </div>
          <div>
            <span>认证方式</span>
            <strong>{{ selectedConfig.authMode || OPEN_API_TOKEN_AUTH_MODE }}</strong>
          </div>
        </section>

        <el-alert
          v-if="!supportsConfigMaintenance(selectedConfig)"
          title="该入站接口当前仅展示统一 token 鉴权口径；元数据保存和校验会在对应后端阶段接入。"
          type="info"
          show-icon
          :closable="false"
        />

        <el-form :model="form" label-width="132px" class="system-inbound-form">
          <el-form-item label="QPO 访问基础地址">
            <el-input v-model.trim="form.publicBaseUrl" placeholder="例如 http://192.168.70.21:8088" />
            <span class="system-inbound-form-tip">用于拼接给外部系统调用的完整地址；留空时后端会按环境变量 fallback。</span>
          </el-form-item>
          <el-form-item label="接口启用">
            <el-switch v-model="form.enabled" active-text="启用" inactive-text="停用" />
          </el-form-item>
          <el-form-item label="备注">
            <el-input v-model.trim="form.remark" type="textarea" :rows="4" maxlength="500" show-word-limit />
          </el-form-item>
        </el-form>

        <section class="system-inbound-readonly-meta">
          <div>
            <span>接口路径</span>
            <strong>{{ emptyText(selectedConfig.callbackPath) }}</strong>
          </div>
          <div>
            <span>完整访问地址</span>
            <strong>{{ emptyText(selectedConfig.callbackUrl) }}</strong>
          </div>
          <div>
            <span>配置来源</span>
            <strong>{{ configSourceLabel(selectedConfig.configSource) }}</strong>
          </div>
          <div>
            <span>最近校验</span>
            <strong>{{ validateStatusText(selectedConfig) }} / {{ validateMessageText(selectedConfig) }}</strong>
          </div>
        </section>
      </div>

      <template #footer>
        <div class="system-inbound-drawer-footer">
          <el-button
            v-permission="'system:config:inbound:update'"
            :loading="isPending(inboundActionKeys.validateConfig)"
            :disabled="!supportsConfigMaintenance(selectedConfig) || isPending(inboundActionKeys.validateConfig)"
            @click="validateConfig(selectedConfig)"
          >
            校验配置
          </el-button>
          <el-button
            v-permission="'system:config:inbound:update'"
            type="primary"
            :loading="isPending(inboundActionKeys.saveConfig)"
            :disabled="!supportsConfigMaintenance(selectedConfig) || isPending(inboundActionKeys.saveConfig)"
            @click="saveConfig"
          >
            保存配置
          </el-button>
        </div>
      </template>
    </el-drawer>

    <el-dialog
      v-model="tokenDialogVisible"
      title="QPO_OPEN_API_TOKEN 已生成"
      width="620px"
      @closed="clearPlainToken"
    >
      <el-alert
        title="token 只展示一次，关闭后无法再次查看，请立即复制并通过受控渠道交付外部调用方。"
        type="warning"
        show-icon
        :closable="false"
      />
      <div class="system-inbound-token-box">
        <el-input v-model="plainToken" type="textarea" :rows="3" readonly />
      </div>
      <template #footer>
        <div class="erp-dialog-footer">
          <el-button @click="tokenDialogVisible = false">关闭</el-button>
          <el-button type="primary" @click="copyPlainToken">复制 token</el-button>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  getOpenApiTokenStatus,
  listInboundCallbackConfigs,
  rotateOpenApiToken,
  saveInboundCallbackConfig,
  validateInboundCallbackConfig
} from '../../services/systemConfigApi'
import { usePendingAction } from '../../composables/usePendingAction'

const OPEN_API_TOKEN_AUTH_MODE = 'QPO_OPEN_API_TOKEN'
const MES_MATERIAL_CALLBACK = 'MES_MATERIAL_CALLBACK'
const SALES_ORDER_SYNC_QUERY = 'SALES_ORDER_SYNC_QUERY'
const MES_MATERIAL_CALLBACK_PATH = '/api/integrations/mes/material-callback'
const SALES_ORDER_SYNC_PATH = '/api/integrations/sales-orders'

const interfaceApiPaths = {
  [MES_MATERIAL_CALLBACK]: 'mes-material-callback',
  [SALES_ORDER_SYNC_QUERY]: 'sales-order-sync-query'
}

const loading = ref(false)
const editDrawerVisible = ref(false)
const tokenDialogVisible = ref(false)
const plainToken = ref('')
const inboundConfigs = ref([])
const selectedConfig = ref(defaultConfig(MES_MATERIAL_CALLBACK))
const openApiTokenStatus = ref(defaultOpenApiTokenStatus())
const form = reactive({
  publicBaseUrl: '',
  enabled: true,
  remark: ''
})
const { isPending, runAction } = usePendingAction()

const inboundActionKeys = {
  saveConfig: 'system:config:inbound:save-config',
  validateConfig: 'system:config:inbound:validate-config',
  rotateOpenApiToken: 'system:config:inbound:rotate-open-api-token'
}

const requestErrorMessage = (error, fallback) => error?.response?.data?.message || error?.payload?.message || error?.message || fallback

const openApiTokenStatusText = computed(() => {
  if (!openApiTokenStatus.value.tokenConfigured) {
    return openApiTokenStatus.value.message || '统一 token 未配置，请先生成 QPO_OPEN_API_TOKEN'
  }
  const mask = openApiTokenStatus.value.tokenMask || '已配置'
  const updatedAt = formatDateTime(openApiTokenStatus.value.tokenUpdatedAt)
  return updatedAt ? `脱敏标识：${mask} / 更新时间：${updatedAt}` : `脱敏标识：${mask}`
})

function defaultOpenApiTokenStatus() {
  return {
    tokenConfigured: false,
    tokenMask: '',
    tokenUpdatedAt: '',
    message: '统一 token 未配置，请先生成 QPO_OPEN_API_TOKEN'
  }
}

function defaultConfig(callbackCode = MES_MATERIAL_CALLBACK) {
  if (callbackCode === SALES_ORDER_SYNC_QUERY) {
    return {
      callbackCode: SALES_ORDER_SYNC_QUERY,
      sourceSystemCode: 'EXTERNAL_SALES_ORDER_SYNC',
      name: '销售订单外部同步查询',
      enabled: true,
      publicBaseUrl: '',
      callbackPath: SALES_ORDER_SYNC_PATH,
      callbackUrl: SALES_ORDER_SYNC_PATH,
      authMode: OPEN_API_TOKEN_AUTH_MODE,
      configSource: 'BUILT_IN',
      lastValidateStatus: '',
      lastValidateMessage: '',
      lastValidateAt: '',
      remark: ''
    }
  }

  return {
    callbackCode: MES_MATERIAL_CALLBACK,
    sourceSystemCode: 'MES',
    name: 'MES 物料号回调',
    enabled: true,
    publicBaseUrl: '',
    callbackPath: MES_MATERIAL_CALLBACK_PATH,
    callbackUrl: MES_MATERIAL_CALLBACK_PATH,
    authMode: OPEN_API_TOKEN_AUTH_MODE,
    configSource: 'ENV',
    lastValidateStatus: '',
    lastValidateMessage: '',
    lastValidateAt: '',
    remark: ''
  }
}

const normalizeConfig = (row = {}) => {
  const callbackCode = row.callbackCode || row.interfaceCode || MES_MATERIAL_CALLBACK
  const base = defaultConfig(callbackCode)
  const callbackPath = row.callbackPath || base.callbackPath
  const publicBaseUrl = row.publicBaseUrl || ''
  return {
    ...base,
    ...row,
    callbackCode,
    enabled: row.enabled !== false,
    publicBaseUrl,
    callbackPath,
    callbackUrl: row.callbackUrl || callbackUrl(publicBaseUrl, callbackPath),
    authMode: row.authMode || OPEN_API_TOKEN_AUTH_MODE,
    configSource: row.configSource || base.configSource,
    remark: row.remark || ''
  }
}

const normalizeOpenApiTokenStatus = (response) => {
  const data = response?.data ?? response ?? {}
  return {
    ...defaultOpenApiTokenStatus(),
    ...data,
    tokenConfigured: data.tokenConfigured === true,
    tokenMask: data.tokenMask || '',
    tokenUpdatedAt: data.tokenUpdatedAt || '',
    message: data.message || ''
  }
}

const responseRows = (response) => {
  const data = response?.data ?? response
  if (Array.isArray(data)) return data
  if (Array.isArray(data?.records)) return data.records
  return data ? [data] : []
}

const ensureKnownConfigs = (rows = []) => {
  const byCode = new Map(rows.map((row) => [row.callbackCode, row]))
  if (!byCode.has(MES_MATERIAL_CALLBACK)) {
    byCode.set(MES_MATERIAL_CALLBACK, defaultConfig(MES_MATERIAL_CALLBACK))
  }
  if (!byCode.has(SALES_ORDER_SYNC_QUERY)) {
    byCode.set(SALES_ORDER_SYNC_QUERY, defaultConfig(SALES_ORDER_SYNC_QUERY))
  }
  return [MES_MATERIAL_CALLBACK, SALES_ORDER_SYNC_QUERY]
    .map((code) => normalizeConfig(byCode.get(code)))
    .concat(rows.filter((row) => ![MES_MATERIAL_CALLBACK, SALES_ORDER_SYNC_QUERY].includes(row.callbackCode)))
}

const syncForm = (row) => {
  form.publicBaseUrl = row.publicBaseUrl || ''
  form.enabled = row.enabled !== false
  form.remark = row.remark || ''
}

const upsertConfig = (row = {}) => {
  const normalized = normalizeConfig(row)
  const index = inboundConfigs.value.findIndex((item) => item.callbackCode === normalized.callbackCode)
  if (index >= 0) {
    inboundConfigs.value.splice(index, 1, normalized)
  } else {
    inboundConfigs.value.push(normalized)
  }
  if (selectedConfig.value.callbackCode === normalized.callbackCode) {
    selectedConfig.value = normalized
    syncForm(normalized)
  }
  return normalized
}

const loadOpenApiTokenStatus = async () => {
  const response = await getOpenApiTokenStatus()
  openApiTokenStatus.value = normalizeOpenApiTokenStatus(response)
  return openApiTokenStatus.value
}

const loadConfig = async () => {
  loading.value = true
  try {
    const [configsResponse] = await Promise.all([
      listInboundCallbackConfigs(),
      loadOpenApiTokenStatus()
    ])
    const rows = responseRows(configsResponse).map(normalizeConfig)
    inboundConfigs.value = ensureKnownConfigs(rows)
    const current = inboundConfigs.value.find((row) => row.callbackCode === selectedConfig.value.callbackCode) || inboundConfigs.value[0]
    selectedConfig.value = current
    syncForm(current)
  } catch (error) {
    inboundConfigs.value = ensureKnownConfigs([])
    selectedConfig.value = inboundConfigs.value[0]
    syncForm(selectedConfig.value)
    openApiTokenStatus.value = defaultOpenApiTokenStatus()
    ElMessage.error(requestErrorMessage(error, '入站接口配置加载失败'))
  } finally {
    loading.value = false
  }
}

const openEditDrawer = (row) => {
  selectedConfig.value = normalizeConfig(row)
  syncForm(selectedConfig.value)
  editDrawerVisible.value = true
}

const buildPayload = () => ({
  publicBaseUrl: form.publicBaseUrl || null,
  enabled: form.enabled !== false,
  remark: form.remark || null
})

const saveConfig = async () => {
  return runAction(inboundActionKeys.saveConfig, async () => {
    if (!supportsConfigMaintenance(selectedConfig.value)) {
      ElMessage.warning('该入站接口的保存接口待接入')
      return
    }
    try {
      const response = await saveInboundCallbackConfig(interfaceApiCode(selectedConfig.value), buildPayload())
      const updated = upsertConfig(response?.data || response)
      selectedConfig.value = updated
      editDrawerVisible.value = false
      ElMessage.success(response?.message || '入站接口配置已保存')
    } catch (error) {
      ElMessage.error(requestErrorMessage(error, '入站接口配置保存失败'))
    }
  })
}

const validateConfig = async (row = selectedConfig.value) => {
  return runAction(inboundActionKeys.validateConfig, async () => {
    if (!supportsConfigMaintenance(row)) {
      ElMessage.warning('该入站接口的校验接口待接入')
      return
    }
    try {
      const response = await validateInboundCallbackConfig(interfaceApiCode(row))
      const updated = upsertConfig(response?.data || response)
      selectedConfig.value = updated
      ElMessage.success(response?.message || '配置校验完成')
    } catch (error) {
      ElMessage.error(requestErrorMessage(error, '配置校验失败'))
    }
  })
}

const handleRotateOpenApiToken = async () => {
  return runAction(inboundActionKeys.rotateOpenApiToken, async () => {
    if (openApiTokenStatus.value.tokenConfigured) {
      try {
        await ElMessageBox.confirm(
          '轮换后旧 token 将立即对所有入站开放接口失效。外部系统未同步新 token 前，MES 回调和销售订单同步都会被拒绝。确认继续？',
          '轮换 QPO_OPEN_API_TOKEN',
          { type: 'warning', confirmButtonText: '确认轮换', cancelButtonText: '取消' }
        )
      } catch {
        return
      }
    }

    try {
      const response = await rotateOpenApiToken()
      const data = response?.data || response || {}
      plainToken.value = data.token || ''
      tokenDialogVisible.value = true
      await loadConfig()
      ElMessage.success(response?.message || data.message || '统一 token 已生成')
    } catch (error) {
      ElMessage.error(requestErrorMessage(error, '统一 token 生成或轮换失败'))
    }
  })
}

const copyCallbackUrl = (row = selectedConfig.value) => copyText(row.callbackUrl, '接口地址已复制')
const copyPlainToken = () => copyText(plainToken.value, 'token 已复制')

const copyText = async (value, successMessage) => {
  if (!value) {
    ElMessage.warning('暂无可复制内容')
    return
  }
  try {
    await navigator.clipboard.writeText(value)
    ElMessage.success(successMessage)
  } catch {
    ElMessage.error('复制失败，请手动选中内容复制。')
  }
}

const clearPlainToken = () => {
  plainToken.value = ''
}

const supportsConfigMaintenance = (row) => row?.callbackCode === MES_MATERIAL_CALLBACK
const interfaceApiCode = (row) => interfaceApiPaths[row?.callbackCode] || row?.callbackCode

const callbackName = (row) => ({
  [MES_MATERIAL_CALLBACK]: 'MES 物料号回调',
  [SALES_ORDER_SYNC_QUERY]: '销售订单外部同步查询'
})[row?.callbackCode] || row?.callbackCode || '-'

const callbackUrl = (baseUrl, path) => {
  const normalizedPath = path || MES_MATERIAL_CALLBACK_PATH
  if (!baseUrl) return normalizedPath
  return `${String(baseUrl).replace(/\/+$/, '')}${normalizedPath.startsWith('/') ? normalizedPath : `/${normalizedPath}`}`
}

const validateStatusText = (row) => {
  if (row.lastValidateStatus === 'SUCCESS') return '校验成功'
  if (row.lastValidateStatus === 'FAILED') return '校验失败'
  return '未校验'
}

const validateMessageText = (row) => {
  const timestamp = formatDateTime(row.lastValidateAt)
  return [timestamp, row.lastValidateMessage].filter(Boolean).join(' / ') || '暂无校验记录'
}

const configSourceLabel = (value) => ({
  DB: '使用页面配置',
  ENV: '使用环境变量 fallback',
  MIXED: '页面配置 + 环境变量兜底',
  BUILT_IN: '内置元数据'
})[value] || value || '-'

const formatDateTime = (value) => {
  if (!value) return ''
  return String(value).replace('T', ' ').slice(0, 19)
}

const emptyText = (value) => value || '-'

onMounted(loadConfig)
</script>

<style scoped>
.system-inbound-callback-config-panel {
  display: grid;
  gap: var(--erp-space-4);
}

.system-inbound-callback-toolbar {
  align-items: center;
  display: flex;
  gap: var(--erp-space-4);
  justify-content: space-between;
  padding: var(--erp-space-4);
}

.system-inbound-title-block {
  min-width: 240px;
}

.system-inbound-callback-actions,
.system-inbound-row-actions,
.system-inbound-url-cell,
.system-inbound-drawer-footer {
  align-items: center;
  display: flex;
  flex-wrap: wrap;
  gap: var(--erp-space-2);
}

.system-inbound-open-api-token-card {
  background: var(--erp-surface-muted);
  border: 1px solid var(--erp-border);
  border-radius: var(--erp-radius-md);
  display: grid;
  gap: var(--erp-space-1);
  min-width: 320px;
  padding: var(--erp-space-2) var(--erp-space-3);
}

.system-inbound-token-heading {
  align-items: center;
  display: flex;
  gap: var(--erp-space-2);
  justify-content: space-between;
}

.system-inbound-table-note,
.system-inbound-open-api-token-card span,
.system-inbound-name-cell span,
.system-inbound-status-stack span,
.system-inbound-form-tip,
.system-inbound-url-cell span,
.system-inbound-drawer-summary span,
.system-inbound-readonly-meta span {
  color: var(--erp-text-secondary);
  font-size: var(--erp-font-size-sm);
}

.system-inbound-name-cell,
.system-inbound-status-stack,
.system-inbound-drawer-summary > div,
.system-inbound-readonly-meta > div {
  display: grid;
  gap: var(--erp-space-1);
}

.system-inbound-url-cell {
  justify-content: space-between;
}

.system-inbound-url-cell > div {
  display: grid;
  flex: 1 1 auto;
  gap: var(--erp-space-1);
  min-width: 0;
}

.system-inbound-url-cell strong,
.system-inbound-url-cell span {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.system-inbound-drawer-body {
  display: grid;
  gap: var(--erp-space-4);
}

.system-inbound-drawer-summary,
.system-inbound-readonly-meta {
  background: var(--erp-surface-muted);
  border: 1px solid var(--erp-border);
  border-radius: var(--erp-radius-md);
  display: grid;
  gap: var(--erp-space-3);
  grid-template-columns: repeat(2, minmax(0, 1fr));
  padding: var(--erp-space-3);
}

.system-inbound-drawer-summary strong,
.system-inbound-readonly-meta strong {
  color: var(--erp-text);
  overflow-wrap: anywhere;
}

.system-inbound-form {
  border-top: 1px solid var(--erp-border);
  padding-top: var(--erp-space-4);
}

.system-inbound-form :deep(.el-form-item:last-child) {
  margin-bottom: 0;
}

.system-inbound-form-tip {
  display: block;
  margin-top: var(--erp-space-1);
}

.system-inbound-drawer-footer {
  border-top: 1px solid var(--erp-border);
  justify-content: space-between;
  padding-top: var(--erp-space-3);
}

.system-inbound-token-box {
  margin-top: var(--erp-space-4);
}

@media (max-width: 920px) {
  .system-inbound-callback-toolbar {
    align-items: flex-start;
    flex-direction: column;
  }

  .system-inbound-callback-actions {
    width: 100%;
  }

  .system-inbound-open-api-token-card {
    min-width: 0;
    width: 100%;
  }
}

@media (max-width: 760px) {
  .system-inbound-drawer-footer {
    align-items: flex-start;
    flex-direction: column;
  }

  .system-inbound-drawer-summary,
  .system-inbound-readonly-meta {
    grid-template-columns: 1fr;
  }
}
</style>
