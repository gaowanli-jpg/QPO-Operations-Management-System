<template>
  <div class="system-inbound-callback-config-panel">
    <section class="erp-surface system-inbound-callback-toolbar">
      <div>
        <h2 class="erp-section-title">入站接口</h2>
        <p class="erp-page-description">管理数据中心调用 QPO 的入站回调入口、机器令牌和启用状态。</p>
      </div>
      <div class="erp-page-actions system-inbound-callback-actions">
        <el-button :loading="loading" @click="loadConfig">刷新</el-button>
      </div>
    </section>

    <section class="erp-table-surface system-inbound-callback-table-surface">
      <div class="erp-table-header">
        <div>
          <h2 class="erp-table-title">入站接口配置</h2>
          <span class="system-inbound-table-note">调用方：数据中心；业务来源按接口区分，当前 MES 物料号回调的业务来源：MES。数据中心后续异步调用 QPO 写回 MES 物料号，回调 URL 和 token 由 QPO 管理员在入站接口页提供给数据中心，真实 token 需单独通过受控渠道交付数据中心。</span>
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
          <el-table-column label="调用方" width="120">
            <template #default>
              <el-tag effect="plain">数据中心</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="业务来源" width="110">
            <template #default="{ row }">
              <el-tag type="info" effect="plain">{{ row.sourceSystemCode || 'MES' }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="callbackUrl" label="回调地址" min-width="310" show-overflow-tooltip>
            <template #default="{ row }">
              <div class="system-inbound-url-cell">
                <span>{{ emptyText(row.callbackUrl) }}</span>
                <el-button size="small" @click="copyCallbackUrl(row)">复制</el-button>
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
          <el-table-column label="token 校验" min-width="170">
            <template #default="{ row }">
              <div class="system-inbound-status-stack">
                <el-tag :type="tokenStatusMeta(row).type" effect="plain">{{ tokenStatusMeta(row).label }}</el-tag>
                <span>{{ tokenMaskText(row) }}</span>
              </div>
            </template>
          </el-table-column>
          <el-table-column prop="configSource" label="配置来源" width="170">
            <template #default="{ row }">
              <el-tag :type="row.configSource === 'DB' ? 'success' : 'warning'" effect="plain">
                {{ configSourceLabel(row.configSource) }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="最后校验" min-width="230" show-overflow-tooltip>
            <template #default="{ row }">
              <div class="system-inbound-status-stack">
                <strong>{{ validateStatusText(row) }}</strong>
                <span>{{ validateMessageText(row) }}</span>
              </div>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="360" fixed="right">
            <template #default="{ row }">
              <div class="erp-table-actions system-inbound-row-actions">
                <el-button size="small" @click="openEditDrawer(row)">编辑</el-button>
                <el-button size="small" @click="copyCallbackUrl(row)">复制回调地址</el-button>
                <el-button
                  v-permission="'system:config:inbound:update'"
                  size="small"
                  :loading="isPending(inboundActionKeys.validateConfig)"
                  :disabled="!supportsMesMaterialCallback(row) || isPending(inboundActionKeys.validateConfig)"
                  @click="validateConfig(row)"
                >
                  校验配置
                </el-button>
                <el-button
                  v-permission="'system:config:inbound:token-rotate'"
                  size="small"
                  type="warning"
                  plain
                  :loading="isPending(inboundActionKeys.rotateToken)"
                  :disabled="!supportsMesMaterialCallback(row) || isPending(inboundActionKeys.rotateToken)"
                  @click="rotateToken(row)"
                >
                  {{ row.tokenConfigured ? '轮换 token' : '生成 token' }}
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
            <span>业务来源</span>
            <strong>{{ selectedConfig.sourceSystemCode || 'MES' }}</strong>
          </div>
          <div>
            <span>回调路径</span>
            <strong>{{ selectedConfig.callbackPath }}</strong>
          </div>
        </section>

        <el-alert
          v-if="selectedConfig.tokenEnabled === false"
          title="当前已关闭 token 校验，数据中心调用 QPO 会跳过机器令牌校验，请仅在联调或受控网络内短期开启。"
          type="warning"
          show-icon
          :closable="false"
        />

        <el-form :model="form" label-width="132px" class="system-inbound-form">
          <el-form-item label="QPO 访问基础地址">
            <el-input v-model.trim="form.publicBaseUrl" placeholder="例如 http://192.168.70.21:8088" />
            <span class="system-inbound-form-tip">用于拼接给数据中心调用的回调地址；留空时后端会按环境变量 fallback。</span>
          </el-form-item>
          <el-form-item label="接口启用">
            <el-switch v-model="form.enabled" active-text="接受回调" inactive-text="停用回调" />
          </el-form-item>
          <el-form-item label="token 校验">
            <el-switch v-model="form.tokenEnabled" active-text="开启" inactive-text="关闭" />
            <span class="system-inbound-form-tip">生产环境建议保持开启；关闭时后端会写入低噪声风险提示日志。</span>
          </el-form-item>
          <el-form-item label="备注">
            <el-input v-model.trim="form.remark" type="textarea" :rows="4" maxlength="500" show-word-limit />
          </el-form-item>
        </el-form>

        <section class="system-inbound-readonly-meta">
          <div>
            <span>完整回调地址</span>
            <strong>{{ emptyText(selectedConfig.callbackUrl) }}</strong>
          </div>
          <div>
            <span>配置来源</span>
            <strong>{{ configSourceLabel(selectedConfig.configSource) }}</strong>
          </div>
          <div>
            <span>token 更新时间</span>
            <strong>{{ emptyText(formatDateTime(selectedConfig.tokenUpdatedAt)) }}</strong>
          </div>
          <div>
            <span>最后校验</span>
            <strong>{{ validateStatusText(selectedConfig) }} / {{ validateMessageText(selectedConfig) }}</strong>
          </div>
        </section>
      </div>

      <template #footer>
        <div class="system-inbound-drawer-footer">
          <el-button
            v-permission="'system:config:inbound:update'"
            :loading="isPending(inboundActionKeys.validateConfig)"
            :disabled="!supportsMesMaterialCallback(selectedConfig) || isPending(inboundActionKeys.validateConfig)"
            @click="validateConfig(selectedConfig)"
          >
            校验配置
          </el-button>
          <div>
            <el-button
              v-permission="'system:config:inbound:token-rotate'"
              type="warning"
              plain
              :loading="isPending(inboundActionKeys.rotateToken)"
              :disabled="!supportsMesMaterialCallback(selectedConfig) || isPending(inboundActionKeys.rotateToken)"
              @click="rotateToken(selectedConfig)"
            >
              {{ selectedConfig.tokenConfigured ? '轮换 token' : '生成 token' }}
            </el-button>
            <el-button
              v-permission="'system:config:inbound:update'"
              type="primary"
              :loading="isPending(inboundActionKeys.saveConfig)"
              :disabled="!supportsMesMaterialCallback(selectedConfig) || isPending(inboundActionKeys.saveConfig)"
              @click="saveConfig"
            >
              保存配置
            </el-button>
          </div>
        </div>
      </template>
    </el-drawer>

    <el-dialog
      v-model="tokenDialogVisible"
      title="QPO 入站回调 token 已生成"
      width="620px"
      @closed="clearPlainToken"
    >
      <el-alert
        title="token 只展示一次，关闭后无法再次查看，请立即复制并通过受控渠道交付数据中心。"
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
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  listInboundCallbackConfigs,
  rotateMesMaterialCallbackToken,
  saveMesMaterialCallbackConfig,
  validateMesMaterialCallbackConfig
} from '../../services/systemConfigApi'
import { usePendingAction } from '../../composables/usePendingAction'

const DEFAULT_CALLBACK_PATH = '/api/integrations/mes/material-callback'
const MES_MATERIAL_CALLBACK = 'MES_MATERIAL_CALLBACK'

const loading = ref(false)
const editDrawerVisible = ref(false)
const tokenDialogVisible = ref(false)
const plainToken = ref('')
const inboundConfigs = ref([])
const selectedConfig = ref(defaultConfig())
const form = reactive({
  publicBaseUrl: '',
  enabled: true,
  tokenEnabled: true,
  remark: ''
})
const { isPending, runAction } = usePendingAction()

const inboundActionKeys = {
  saveConfig: 'system:config:inbound:save-config',
  validateConfig: 'system:config:inbound:validate-config',
  rotateToken: 'system:config:inbound:rotate-token'
}

const requestErrorMessage = (error, fallback) => error?.response?.data?.message || error?.payload?.message || error?.message || fallback

function defaultConfig() {
  return {
    callbackCode: MES_MATERIAL_CALLBACK,
    sourceSystemCode: 'MES',
    name: 'MES 物料号回调',
    enabled: true,
    tokenEnabled: true,
    tokenConfigured: false,
    tokenMask: '',
    publicBaseUrl: '',
    callbackPath: DEFAULT_CALLBACK_PATH,
    callbackUrl: DEFAULT_CALLBACK_PATH,
    configSource: 'ENV',
    lastValidateStatus: '',
    lastValidateMessage: '',
    lastValidateAt: '',
    tokenUpdatedAt: '',
    remark: ''
  }
}

const normalizeConfig = (row = {}) => ({
  ...defaultConfig(),
  ...row,
  enabled: row.enabled !== false,
  tokenEnabled: row.tokenEnabled !== false,
  tokenConfigured: row.tokenConfigured === true,
  publicBaseUrl: row.publicBaseUrl || '',
  callbackPath: row.callbackPath || DEFAULT_CALLBACK_PATH,
  callbackUrl: row.callbackUrl || callbackUrl(row.publicBaseUrl, row.callbackPath || DEFAULT_CALLBACK_PATH),
  configSource: row.configSource || 'ENV',
  tokenUpdatedAt: row.tokenUpdatedAt || row.updatedAt || '',
  remark: row.remark || ''
})

const responseRows = (response) => {
  const data = response?.data ?? response
  if (Array.isArray(data)) return data
  if (Array.isArray(data?.records)) return data.records
  return data ? [data] : []
}

const syncForm = (row) => {
  form.publicBaseUrl = row.publicBaseUrl || ''
  form.enabled = row.enabled !== false
  form.tokenEnabled = row.tokenEnabled !== false
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

const loadConfig = async () => {
  loading.value = true
  try {
    const response = await listInboundCallbackConfigs()
    const rows = responseRows(response).map(normalizeConfig)
    inboundConfigs.value = rows.length ? rows : [defaultConfig()]
    const current = inboundConfigs.value.find((row) => row.callbackCode === selectedConfig.value.callbackCode) || inboundConfigs.value[0]
    selectedConfig.value = current
    syncForm(current)
  } catch (error) {
    inboundConfigs.value = [defaultConfig()]
    selectedConfig.value = inboundConfigs.value[0]
    syncForm(selectedConfig.value)
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
  tokenEnabled: form.tokenEnabled !== false,
  remark: form.remark || null
})

const saveConfig = async () => {
  return runAction(inboundActionKeys.saveConfig, async () => {
    if (!supportsMesMaterialCallback(selectedConfig.value)) {
      ElMessage.warning('该入站接口的保存接口待接入')
      return
    }
    try {
      const response = await saveMesMaterialCallbackConfig(buildPayload())
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
    if (!supportsMesMaterialCallback(row)) {
      ElMessage.warning('该入站接口的校验接口待接入')
      return
    }
    try {
      const response = await validateMesMaterialCallbackConfig()
      const updated = upsertConfig(response?.data || response)
      selectedConfig.value = updated
      ElMessage.success(response?.message || '配置校验完成')
    } catch (error) {
      ElMessage.error(requestErrorMessage(error, '配置校验失败'))
    }
  })
}

const rotateToken = async (row = selectedConfig.value) => {
  return runAction(inboundActionKeys.rotateToken, async () => {
    if (!supportsMesMaterialCallback(row)) {
      ElMessage.warning('该入站接口的 token 轮换接口待接入')
      return
    }
    if (row.tokenConfigured) {
      try {
        await ElMessageBox.confirm(
          '轮换后旧 token 将立即失效。数据中心未同步新 token 前，回调会被拒绝。确认继续？',
          '轮换入站回调 token',
          { type: 'warning', confirmButtonText: '确认轮换', cancelButtonText: '取消' }
        )
      } catch {
        return
      }
    }

    try {
      const response = await rotateMesMaterialCallbackToken()
      const data = response?.data || response || {}
      plainToken.value = data.token || ''
      tokenDialogVisible.value = true
      await loadConfig()
      ElMessage.success(response?.message || data.message || 'token 已生成')
    } catch (error) {
      ElMessage.error(requestErrorMessage(error, 'token 生成或轮换失败'))
    }
  })
}

const copyCallbackUrl = (row = selectedConfig.value) => copyText(row.callbackUrl, '回调地址已复制')
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

const supportsMesMaterialCallback = (row) => row?.callbackCode === MES_MATERIAL_CALLBACK

const callbackName = (row) => ({
  MES_MATERIAL_CALLBACK: 'MES 物料号回调'
})[row?.callbackCode] || row?.callbackCode || '-'

const callbackUrl = (baseUrl, path) => {
  const normalizedPath = path || DEFAULT_CALLBACK_PATH
  if (!baseUrl) return normalizedPath
  return `${String(baseUrl).replace(/\/+$/, '')}${normalizedPath.startsWith('/') ? normalizedPath : `/${normalizedPath}`}`
}

const tokenStatusMeta = (row) => {
  if (row.tokenEnabled === false) return { label: '校验关闭', type: 'warning' }
  return row.tokenConfigured ? { label: '已配置', type: 'success' } : { label: '未配置', type: 'danger' }
}

const tokenMaskText = (row) => {
  if (row.tokenEnabled === false) return '生产环境建议开启校验'
  return row.tokenConfigured ? `脱敏标识：${row.tokenMask || '已配置'}` : '请生成 token 后交付数据中心'
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
  MIXED: '页面配置 + 环境变量兜底'
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

.system-inbound-callback-actions,
.system-inbound-row-actions,
.system-inbound-url-cell,
.system-inbound-drawer-footer,
.system-inbound-drawer-footer > div {
  align-items: center;
  display: flex;
  flex-wrap: wrap;
  gap: var(--erp-space-2);
}

.system-inbound-table-note,
.system-inbound-name-cell span,
.system-inbound-status-stack span,
.system-inbound-form-tip,
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

.system-inbound-url-cell span {
  flex: 1 1 auto;
  min-width: 0;
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

@media (max-width: 760px) {
  .system-inbound-callback-toolbar,
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
