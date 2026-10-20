<template>
  <div class="system-data-center-sso-config-panel">
    <section class="erp-surface system-data-center-sso-toolbar">
      <div>
        <h2 class="erp-section-title">数据中心登录</h2>
        <p class="erp-page-description">维护数据中心统一入口进入 QPO 的 SSO 签名、跳转和启停策略。</p>
      </div>
      <div class="erp-page-actions system-data-center-sso-actions">
        <el-button v-permission="'system:config:sso:update'" type="primary" @click="openCreate">新增登录配置</el-button>
        <el-button :loading="loading" @click="loadConfigs">刷新</el-button>
      </div>
    </section>

    <section class="erp-table-surface system-data-center-sso-table-surface">
      <div class="erp-table-header">
        <div>
          <h2 class="erp-table-title">数据中心登录配置</h2>
          <span class="system-data-center-sso-note">同一时间只允许一个 active 配置；sharedSecret 查询只展示是否已配置，编辑时留空或 ****** 沿用原 SSO 密钥。</span>
        </div>
      </div>
      <div class="erp-table-body">
        <el-table :data="configs" border v-loading="loading" empty-text="暂无数据中心登录配置">
          <el-table-column prop="configName" label="配置名称" min-width="180" show-overflow-tooltip />
          <el-table-column prop="appCode" label="App Code" width="120" />
          <el-table-column label="状态" width="150">
            <template #default="{ row }">
              <el-tag v-if="row.active" type="success">当前使用</el-tag>
              <el-tag v-else-if="row.enabled === false" type="info">已停用</el-tag>
              <el-tag v-else type="warning">待激活</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="共享密钥" width="130">
            <template #default="{ row }">
              <el-tag :type="row.sharedSecretConfigured ? 'success' : 'danger'" effect="plain">
                {{ row.sharedSecretConfigured ? '已配置' : '未配置' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="时间窗口" width="120">
            <template #default="{ row }">{{ row.timestampSkewSeconds }} 秒</template>
          </el-table-column>
          <el-table-column label="Nonce 留存" width="120">
            <template #default="{ row }">{{ row.nonceTtlMinutes }} 分钟</template>
          </el-table-column>
          <el-table-column prop="defaultRedirect" label="默认跳转" min-width="150" show-overflow-tooltip />
          <el-table-column prop="allowedRedirectPrefixes" label="允许跳转前缀" min-width="220" show-overflow-tooltip />
          <el-table-column label="最近测试" min-width="240" show-overflow-tooltip>
            <template #default="{ row }">{{ formatTestSummary(row) }}</template>
          </el-table-column>
          <el-table-column prop="remark" label="备注" min-width="180" show-overflow-tooltip />
          <el-table-column label="操作" width="330" fixed="right">
            <template #default="{ row }">
              <div class="erp-table-actions system-data-center-sso-row-actions">
                <el-button v-permission="'system:config:sso:update'" size="small" :disabled="isRowPending(row)" @click="openEdit(row)">编辑</el-button>
                <el-button
                  v-permission="'system:config:sso:test'"
                  size="small"
                  :loading="isPending(dataCenterSsoActionKeys.test(row.id))"
                  :disabled="isRowPending(row)"
                  @click="testConfig(row)"
                >
                  测试配置
                </el-button>
                <el-button
                  v-permission="'system:config:sso:activate'"
                  size="small"
                  type="success"
                  plain
                  :loading="isPending(dataCenterSsoActionKeys.activate(row.id))"
                  :disabled="row.active || row.enabled === false || row.lastTestStatus !== 'SUCCESS' || isRowPending(row)"
                  @click="activateConfig(row)"
                >
                  激活
                </el-button>
                <el-button
                  v-permission="'system:config:sso:update'"
                  size="small"
                  type="warning"
                  plain
                  :loading="isPending(dataCenterSsoActionKeys.disable(row.id))"
                  :disabled="row.enabled === false || isRowPending(row)"
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

    <el-dialog v-model="dialogVisible" :title="form.id ? '编辑数据中心登录配置' : '新增数据中心登录配置'" width="720px">
      <el-form ref="formRef" :model="form" label-width="152px" class="erp-dialog-form system-data-center-sso-form">
        <el-form-item label="配置名称" required>
          <el-input v-model.trim="form.configName" maxlength="120" show-word-limit placeholder="例如 数据中心测试入口" />
        </el-form-item>
        <el-form-item label="启用状态">
          <el-switch v-model="form.enabled" active-text="启用" inactive-text="停用" />
        </el-form-item>
        <el-form-item label="App Code" required>
          <el-input v-model.trim="form.appCode" placeholder="QPO" />
        </el-form-item>
        <el-form-item :label="form.id ? 'Shared Secret' : 'Shared Secret'" :required="!form.id">
          <el-input
            v-model="form.sharedSecret"
            type="password"
            show-password
            autocomplete="new-password"
            :placeholder="form.id ? '留空或 ****** 沿用原 SSO 密钥' : '请输入数据中心 SSO 共享密钥'"
          />
          <span class="system-data-center-sso-tip">密钥明文只在本弹窗输入；列表、缓存和日志只展示是否已配置。</span>
        </el-form-item>
        <el-form-item label="时间戳窗口">
          <el-input-number v-model="form.timestampSkewSeconds" :min="30" :max="600" :step="10" class="system-data-center-sso-number" />
          <span class="system-data-center-sso-inline-tip">秒</span>
        </el-form-item>
        <el-form-item label="Nonce 留存">
          <el-input-number v-model="form.nonceTtlMinutes" :min="1" :max="1440" :step="1" class="system-data-center-sso-number" />
          <span class="system-data-center-sso-inline-tip">分钟</span>
        </el-form-item>
        <el-form-item label="默认跳转" required>
          <el-input v-model.trim="form.defaultRedirect" placeholder="/dashboard" />
        </el-form-item>
        <el-form-item label="允许跳转前缀" required>
          <el-input v-model.trim="form.allowedRedirectPrefixes" placeholder="/,/dashboard,/orders" />
          <span class="system-data-center-sso-tip">多个内部路径前缀用英文逗号分隔，只允许 QPO 内部相对路径。</span>
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model.trim="form.remark" type="textarea" :rows="3" maxlength="500" show-word-limit />
        </el-form-item>
      </el-form>
      <template #footer>
        <div class="erp-dialog-footer">
          <el-button @click="dialogVisible = false">取消</el-button>
          <el-button
            v-permission="'system:config:sso:update'"
            type="primary"
            :loading="isPending(dataCenterSsoActionKeys.saveDialog)"
            :disabled="isPending(dataCenterSsoActionKeys.saveDialog)"
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
  activateDataCenterSsoConfig,
  disableDataCenterSsoConfig,
  listDataCenterSsoConfigs,
  saveDataCenterSsoConfig,
  testDataCenterSsoConfig
} from '../../services/systemDataCenterSsoConfigApi'
import { usePendingAction } from '../../composables/usePendingAction'

const MASKED_SECRET = '******'
const loading = ref(false)
const configs = ref([])
const dialogVisible = ref(false)
const formRef = ref()
const { isPending, runAction } = usePendingAction()

const dataCenterSsoActionKeys = {
  saveDialog: 'system:config:sso:save-dialog',
  test: (configId) => `system:config:sso:test:${configId || 'none'}`,
  activate: (configId) => `system:config:sso:activate:${configId || 'none'}`,
  disable: (configId) => `system:config:sso:disable:${configId || 'none'}`
}

const form = reactive({
  id: null,
  configName: '',
  enabled: true,
  appCode: 'QPO',
  sharedSecret: '',
  sharedSecretConfigured: false,
  timestampSkewSeconds: 120,
  nonceTtlMinutes: 10,
  defaultRedirect: '/dashboard',
  allowedRedirectPrefixes: '/,/dashboard,/orders',
  remark: ''
})

const requestErrorMessage = (error, fallback) => error?.response?.data?.message || error?.payload?.message || error?.message || fallback

const normalizeRows = (response) => {
  const rows = Array.isArray(response) ? response : (response?.records || response?.data || [])
  return (Array.isArray(rows) ? rows : []).map(normalizeConfig)
}

const normalizeConfig = (row = {}) => ({
  ...row,
  active: row.active === true,
  enabled: row.enabled !== false,
  appCode: row.appCode || 'QPO',
  sharedSecret: '',
  sharedSecretConfigured: row.sharedSecretConfigured === true || row.sharedSecret === MASKED_SECRET,
  timestampSkewSeconds: row.timestampSkewSeconds || 120,
  nonceTtlMinutes: row.nonceTtlMinutes || 10,
  defaultRedirect: row.defaultRedirect || '/dashboard',
  allowedRedirectPrefixes: row.allowedRedirectPrefixes || '/,/dashboard,/orders',
  lastTestStatus: row.lastTestStatus || '',
  lastTestMessage: row.lastTestMessage || '',
  lastTestAt: row.lastTestAt || '',
  remark: row.remark || ''
})

const loadConfigs = async () => {
  loading.value = true
  try {
    const response = await listDataCenterSsoConfigs()
    configs.value = normalizeRows(response)
  } catch (error) {
    configs.value = []
    ElMessage.error(requestErrorMessage(error, '数据中心登录配置加载失败'))
  } finally {
    loading.value = false
  }
}

const resetForm = () => {
  form.id = null
  form.configName = ''
  form.enabled = true
  form.appCode = 'QPO'
  form.sharedSecret = ''
  form.sharedSecretConfigured = false
  form.timestampSkewSeconds = 120
  form.nonceTtlMinutes = 10
  form.defaultRedirect = '/dashboard'
  form.allowedRedirectPrefixes = '/,/dashboard,/orders'
  form.remark = ''
  formRef.value?.clearValidate?.()
}

const openCreate = () => {
  resetForm()
  dialogVisible.value = true
}

const openEdit = (row) => {
  const current = normalizeConfig(row)
  Object.assign(form, {
    ...current,
    sharedSecret: current.sharedSecretConfigured ? MASKED_SECRET : ''
  })
  formRef.value?.clearValidate?.()
  dialogVisible.value = true
}

const validateForm = () => {
  if (!form.configName || !form.appCode || !form.defaultRedirect || !form.allowedRedirectPrefixes) {
    ElMessage.warning('请填写配置名称、App Code、默认跳转和允许跳转前缀')
    return false
  }
  if (!form.id && !form.sharedSecret) {
    ElMessage.warning('新增数据中心登录配置必须填写 Shared Secret')
    return false
  }
  if (!safeInternalPath(form.defaultRedirect)) {
    ElMessage.warning('默认跳转必须是 QPO 内部相对路径')
    return false
  }
  return true
}

const safeInternalPath = (value) => {
  const text = String(value || '').trim()
  return text.startsWith('/') && !text.startsWith('//') && !text.startsWith('http://') && !text.startsWith('https://')
}

const secretOrUndefined = (value) => (!value || value === MASKED_SECRET) ? undefined : value

const buildPayload = () => ({
  id: form.id,
  configName: form.configName,
  enabled: form.enabled !== false,
  appCode: form.appCode || 'QPO',
  sharedSecret: secretOrUndefined(form.sharedSecret),
  timestampSkewSeconds: form.timestampSkewSeconds,
  nonceTtlMinutes: form.nonceTtlMinutes,
  defaultRedirect: form.defaultRedirect || '/dashboard',
  allowedRedirectPrefixes: form.allowedRedirectPrefixes || '/,/dashboard,/orders',
  remark: form.remark
})

const saveConfig = async () => {
  if (!validateForm()) return
  return runAction(dataCenterSsoActionKeys.saveDialog, async () => {
    try {
      const response = await saveDataCenterSsoConfig(buildPayload())
      ElMessage.success(response?.message || '数据中心登录配置已保存')
      dialogVisible.value = false
      await loadConfigs()
    } catch (error) {
      ElMessage.error(requestErrorMessage(error, '数据中心登录配置保存失败'))
    }
  })
}

const testConfig = async (row) => {
  return runAction(dataCenterSsoActionKeys.test(row.id), async () => {
    try {
      const response = await testDataCenterSsoConfig(row.id)
      ElMessage.success(response?.message || '数据中心登录配置测试完成')
    } catch (error) {
      ElMessage.error(requestErrorMessage(error, '数据中心登录配置测试失败'))
    } finally {
      await loadConfigs()
    }
  })
}

const activateConfig = async (row) => {
  return runAction(dataCenterSsoActionKeys.activate(row.id), async () => {
    try {
      await ElMessageBox.confirm(`确认激活数据中心登录配置 ${row.configName || row.id}？同一时间只允许一个配置 active。`, '激活数据中心登录配置', {
        type: 'warning',
        confirmButtonText: '激活',
        cancelButtonText: '取消'
      })
    } catch {
      return
    }
    try {
      const response = await activateDataCenterSsoConfig(row.id)
      ElMessage.success(response?.message || '数据中心登录配置已激活')
      await loadConfigs()
    } catch (error) {
      ElMessage.error(requestErrorMessage(error, '激活数据中心登录配置失败'))
    }
  })
}

const disableConfig = async (row) => {
  return runAction(dataCenterSsoActionKeys.disable(row.id), async () => {
    try {
      await ElMessageBox.confirm(`确认停用数据中心登录配置 ${row.configName || row.id}？停用后该配置不能用于 SSO 登录。`, '停用数据中心登录配置', {
        type: 'warning',
        confirmButtonText: '停用',
        cancelButtonText: '取消'
      })
    } catch {
      return
    }
    try {
      const response = await disableDataCenterSsoConfig(row.id)
      ElMessage.success(response?.message || '数据中心登录配置已停用')
      await loadConfigs()
    } catch (error) {
      ElMessage.error(requestErrorMessage(error, '停用数据中心登录配置失败'))
    }
  })
}

const isRowPending = (row) => (
  isPending(dataCenterSsoActionKeys.test(row?.id))
  || isPending(dataCenterSsoActionKeys.activate(row?.id))
  || isPending(dataCenterSsoActionKeys.disable(row?.id))
)

const formatDateTime = (value) => {
  if (!value) return ''
  return String(value).replace('T', ' ').slice(0, 19)
}

const formatTestSummary = (row) => {
  if (!row?.lastTestStatus && !row?.lastTestAt) return '未测试'
  const status = row.lastTestStatus === 'SUCCESS' ? '成功' : row.lastTestStatus === 'FAILED' ? '失败' : row.lastTestStatus
  return [status, formatDateTime(row.lastTestAt), row.lastTestMessage].filter(Boolean).join(' / ')
}

onMounted(loadConfigs)
</script>

<style scoped>
.system-data-center-sso-config-panel {
  display: grid;
  gap: var(--erp-space-4);
}

.system-data-center-sso-toolbar {
  align-items: center;
  display: flex;
  gap: var(--erp-space-4);
  justify-content: space-between;
  padding: var(--erp-space-4);
}

.system-data-center-sso-actions,
.system-data-center-sso-row-actions {
  flex-wrap: wrap;
  justify-content: flex-start;
}

.system-data-center-sso-table-surface .erp-table-header {
  border-radius: calc(var(--erp-radius-lg) - 1px) calc(var(--erp-radius-lg) - 1px) 0 0;
}

.system-data-center-sso-table-surface .erp-table-body {
  padding: var(--erp-space-3);
}

.system-data-center-sso-note,
.system-data-center-sso-tip,
.system-data-center-sso-inline-tip {
  color: var(--erp-text-secondary);
  font-size: var(--erp-font-size-sm);
}

.system-data-center-sso-form {
  max-width: 660px;
}

.system-data-center-sso-number {
  width: 220px;
}

.system-data-center-sso-inline-tip {
  margin-left: var(--erp-space-2);
}

@media (max-width: 760px) {
  .system-data-center-sso-toolbar {
    align-items: flex-start;
    flex-direction: column;
  }

  .system-data-center-sso-form,
  .system-data-center-sso-number {
    max-width: none;
    width: 100%;
  }
}
</style>
