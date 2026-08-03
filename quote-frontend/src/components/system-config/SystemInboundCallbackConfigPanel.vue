<template>
  <div class="system-inbound-callback-config-panel">
    <section class="erp-surface system-inbound-callback-toolbar">
      <div>
        <h2 class="erp-section-title">入站接口</h2>
        <p class="erp-page-description">管理外部系统调用 QPO 的回调入口、机器令牌和启用状态。</p>
      </div>
      <div class="erp-page-actions system-inbound-callback-actions">
        <el-button :loading="loading" @click="loadConfig">刷新</el-button>
      </div>
    </section>

    <section class="erp-surface system-inbound-callback-card" v-loading="loading">
      <div class="erp-section-header system-inbound-card-header">
        <div>
          <h2 class="erp-section-title">MES 物料号回调</h2>
          <p class="system-inbound-callback-note">MES 后续异步回调 QPO 写回物料号，和 QPO 主动调用 MES 的出站连接分开管理。回调 URL 和 token 由 QPO 管理员在入站接口页提供。</p>
        </div>
        <div class="system-inbound-status-tags">
          <el-tag :type="config.enabled ? 'success' : 'info'" effect="plain">
            {{ config.enabled ? '接口已启用' : '接口已停用' }}
          </el-tag>
          <el-tag :type="config.tokenEnabled ? tokenStatusType : 'warning'" effect="plain">
            {{ tokenStatusText }}
          </el-tag>
          <el-tag type="info" effect="plain">{{ configSourceLabel }}</el-tag>
        </div>
      </div>

      <div class="system-inbound-callback-body">
        <el-alert
          v-if="config.tokenEnabled === false"
          class="system-inbound-warning"
          title="当前已关闭 token 校验，MES 回调会跳过机器令牌校验，请仅在联调或受控网络内短期开启。"
          type="warning"
          show-icon
          :closable="false"
        />

        <div class="system-inbound-summary-grid">
          <article class="system-inbound-summary-card system-inbound-summary-card-wide">
            <span>回调地址</span>
            <strong>{{ emptyText(config.callbackUrl) }}</strong>
            <small>提供给 MES 的完整 URL，真实 token 需单独通过受控渠道交付。</small>
          </article>
          <article class="system-inbound-summary-card">
            <span>配置来源</span>
            <strong>{{ configSourceLabel }}</strong>
            <small>{{ configSourceDescription }}</small>
          </article>
          <article class="system-inbound-summary-card">
            <span>token 状态</span>
            <strong>{{ tokenStatusText }}</strong>
            <small>{{ tokenMaskText }}</small>
          </article>
          <article class="system-inbound-summary-card">
            <span>最后校验</span>
            <strong>{{ validateStatusText }}</strong>
            <small>{{ validateMessageText }}</small>
          </article>
        </div>

        <el-descriptions class="system-inbound-detail" :column="2" border>
          <el-descriptions-item label="callbackUrl">{{ emptyText(config.callbackUrl) }}</el-descriptions-item>
          <el-descriptions-item label="configSource">{{ emptyText(config.configSource) }}</el-descriptions-item>
          <el-descriptions-item label="enabled">{{ config.enabled ? '启用' : '停用' }}</el-descriptions-item>
          <el-descriptions-item label="tokenEnabled">{{ config.tokenEnabled ? '开启校验' : '关闭校验' }}</el-descriptions-item>
          <el-descriptions-item label="tokenConfigured">{{ config.tokenConfigured ? '已配置' : '未配置' }}</el-descriptions-item>
          <el-descriptions-item label="tokenUpdatedAt">{{ emptyText(formatDateTime(config.tokenUpdatedAt)) }}</el-descriptions-item>
          <el-descriptions-item label="callbackPath">{{ emptyText(config.callbackPath) }}</el-descriptions-item>
          <el-descriptions-item label="lastValidateAt">{{ emptyText(formatDateTime(config.lastValidateAt)) }}</el-descriptions-item>
        </el-descriptions>

        <el-form :model="form" label-width="132px" class="system-inbound-form">
          <el-form-item label="QPO 访问基础地址">
            <el-input v-model.trim="form.publicBaseUrl" placeholder="例如 http://192.168.70.21:8088" />
            <span class="system-inbound-form-tip">用于拼接给 MES 的回调地址；留空时后端会按环境变量 fallback。</span>
          </el-form-item>
          <el-form-item label="接口启用">
            <el-switch v-model="form.enabled" active-text="接受回调" inactive-text="停用回调" />
          </el-form-item>
          <el-form-item label="token 校验">
            <el-switch v-model="form.tokenEnabled" active-text="开启" inactive-text="关闭" />
            <span class="system-inbound-form-tip">生产环境建议保持开启；关闭时后端会写入低噪声风险提示日志。</span>
          </el-form-item>
          <el-form-item label="备注">
            <el-input v-model.trim="form.remark" type="textarea" :rows="3" maxlength="500" show-word-limit />
          </el-form-item>
        </el-form>

        <div class="system-inbound-operation-bar">
          <div class="system-inbound-secondary-actions">
            <el-button @click="copyCallbackUrl">复制回调地址</el-button>
            <el-button
              v-permission="'system:config:inbound:update'"
              :loading="isPending(inboundActionKeys.validateConfig)"
              :disabled="isPending(inboundActionKeys.validateConfig)"
              @click="validateConfig"
            >
              校验配置
            </el-button>
          </div>
          <div class="system-inbound-primary-actions">
            <el-button
              v-permission="'system:config:inbound:token-rotate'"
              type="warning"
              plain
              :loading="isPending(inboundActionKeys.rotateToken)"
              :disabled="isPending(inboundActionKeys.rotateToken)"
              @click="rotateToken"
            >
              {{ config.tokenConfigured ? '轮换 token' : '生成 token' }}
            </el-button>
            <el-button
              v-permission="'system:config:inbound:update'"
              type="primary"
              :loading="isPending(inboundActionKeys.saveConfig)"
              :disabled="isPending(inboundActionKeys.saveConfig)"
              @click="saveConfig"
            >
              保存配置
            </el-button>
          </div>
        </div>
      </div>
    </section>

    <el-dialog
      v-model="tokenDialogVisible"
      title="MES 回调 token 已生成"
      width="620px"
      @closed="clearPlainToken"
    >
      <el-alert
        title="token 只展示一次，关闭后无法再次查看，请立即复制并通过受控渠道交付 MES。"
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
  getMesMaterialCallbackConfig,
  rotateMesMaterialCallbackToken,
  saveMesMaterialCallbackConfig,
  validateMesMaterialCallbackConfig
} from '../../services/systemConfigApi'
import { usePendingAction } from '../../composables/usePendingAction'

const DEFAULT_CALLBACK_PATH = '/api/integrations/mes/material-callback'

const loading = ref(false)
const tokenDialogVisible = ref(false)
const plainToken = ref('')
const config = reactive(defaultConfig())
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

const tokenStatusType = computed(() => config.tokenConfigured ? 'success' : 'danger')
const tokenStatusText = computed(() => {
  if (!config.tokenEnabled) return 'token 校验关闭'
  return config.tokenConfigured ? 'token 已配置' : 'token 未配置'
})
const tokenMaskText = computed(() => config.tokenConfigured ? `脱敏标识：${config.tokenMask || '已配置'}` : '请生成 token 后交付 MES')
const validateStatusText = computed(() => {
  if (config.lastValidateStatus === 'SUCCESS') return '校验成功'
  if (config.lastValidateStatus === 'FAILED') return '校验失败'
  return '未校验'
})
const validateMessageText = computed(() => {
  const timestamp = formatDateTime(config.lastValidateAt)
  return [timestamp, config.lastValidateMessage].filter(Boolean).join(' / ') || '暂无校验记录'
})
const configSourceLabel = computed(() => ({
  DB: '使用页面配置',
  ENV: '使用环境变量 fallback',
  MIXED: '页面配置 + 环境变量兜底'
})[config.configSource] || config.configSource || '-')
const configSourceDescription = computed(() => ({
  DB: '运行时优先读取系统配置表。',
  ENV: '页面尚未保存配置，继续兼容 erp.env。',
  MIXED: '页面配置缺少字段时由 erp.env 兜底。'
})[config.configSource] || '暂无来源说明')

function defaultConfig() {
  return {
    callbackCode: 'MES_MATERIAL_CALLBACK',
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

const applyConfig = (row = {}) => {
  Object.assign(config, normalizeConfig(row))
  form.publicBaseUrl = config.publicBaseUrl || ''
  form.enabled = config.enabled !== false
  form.tokenEnabled = config.tokenEnabled !== false
  form.remark = config.remark || ''
}

const loadConfig = async () => {
  loading.value = true
  try {
    const response = await getMesMaterialCallbackConfig()
    applyConfig(response?.data || response)
  } catch (error) {
    applyConfig(defaultConfig())
    ElMessage.error(requestErrorMessage(error, '入站接口配置加载失败'))
  } finally {
    loading.value = false
  }
}

const buildPayload = () => ({
  publicBaseUrl: form.publicBaseUrl || null,
  enabled: form.enabled !== false,
  tokenEnabled: form.tokenEnabled !== false,
  remark: form.remark || null
})

const saveConfig = async () => {
  return runAction(inboundActionKeys.saveConfig, async () => {
    try {
      const response = await saveMesMaterialCallbackConfig(buildPayload())
      applyConfig(response?.data || response)
      ElMessage.success(response?.message || '入站接口配置已保存')
    } catch (error) {
      ElMessage.error(requestErrorMessage(error, '入站接口配置保存失败'))
    }
  })
}

const validateConfig = async () => {
  return runAction(inboundActionKeys.validateConfig, async () => {
    try {
      const response = await validateMesMaterialCallbackConfig()
      applyConfig(response?.data || response)
      ElMessage.success(response?.message || '配置校验完成')
    } catch (error) {
      ElMessage.error(requestErrorMessage(error, '配置校验失败'))
    }
  })
}

const rotateToken = async () => {
  return runAction(inboundActionKeys.rotateToken, async () => {
    if (config.tokenConfigured) {
      try {
        await ElMessageBox.confirm(
          '轮换后旧 token 将立即失效。MES 未同步新 token 前，回调会被拒绝。确认继续？',
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

const copyCallbackUrl = () => copyText(config.callbackUrl, '回调地址已复制')
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

const callbackUrl = (baseUrl, path) => {
  const normalizedPath = path || DEFAULT_CALLBACK_PATH
  if (!baseUrl) return normalizedPath
  return `${String(baseUrl).replace(/\/+$/, '')}${normalizedPath.startsWith('/') ? normalizedPath : `/${normalizedPath}`}`
}

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
.system-inbound-status-tags,
.system-inbound-operation-bar,
.system-inbound-secondary-actions,
.system-inbound-primary-actions {
  align-items: center;
  display: flex;
  flex-wrap: wrap;
  gap: var(--erp-space-2);
}

.system-inbound-card-header {
  border-radius: calc(var(--erp-radius-lg) - 1px) calc(var(--erp-radius-lg) - 1px) 0 0;
}

.system-inbound-callback-body {
  display: grid;
  gap: var(--erp-space-4);
  padding: var(--erp-space-4);
}

.system-inbound-warning {
  margin-bottom: 0;
}

.system-inbound-summary-grid {
  display: grid;
  gap: var(--erp-space-3);
  grid-template-columns: repeat(auto-fit, minmax(220px, 1fr));
}

.system-inbound-summary-card {
  background: var(--erp-surface-muted);
  border: 1px solid var(--erp-border);
  border-radius: var(--erp-radius-md);
  display: grid;
  gap: var(--erp-space-1);
  padding: var(--erp-space-3);
}

.system-inbound-summary-card-wide {
  grid-column: span 2;
}

.system-inbound-summary-card span,
.system-inbound-summary-card small,
.system-inbound-callback-note,
.system-inbound-form-tip {
  color: var(--erp-text-secondary);
  font-size: var(--erp-font-size-sm);
}

.system-inbound-summary-card strong {
  color: var(--erp-text);
  font-size: var(--erp-font-size-md);
  overflow-wrap: anywhere;
}

.system-inbound-detail :deep(.el-descriptions__cell) {
  word-break: break-word;
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

.system-inbound-operation-bar {
  border-top: 1px solid var(--erp-border);
  justify-content: space-between;
  padding-top: var(--erp-space-4);
}

.system-inbound-token-box {
  margin-top: var(--erp-space-4);
}

@media (max-width: 760px) {
  .system-inbound-callback-toolbar,
  .system-inbound-card-header,
  .system-inbound-operation-bar {
    align-items: flex-start;
    flex-direction: column;
  }

  .system-inbound-summary-card-wide {
    grid-column: span 1;
  }
}
</style>
