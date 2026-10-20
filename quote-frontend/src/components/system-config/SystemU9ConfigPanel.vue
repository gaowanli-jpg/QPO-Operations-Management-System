<template>
  <div class="system-u9-config-panel">
    <section class="erp-surface system-u9-config-toolbar">
      <div>
        <h2 class="erp-section-title">U9配置</h2>
        <p class="erp-page-description">
          只维护 QPO 调用数据中心时使用的 U9 组织编码；接口地址、认证和密钥继续在数据中心出站接口中配置，默认 101。
        </p>
      </div>
      <div class="erp-page-actions">
        <el-button :loading="loading" @click="loadConfig">刷新</el-button>
      </div>
    </section>

    <section class="erp-surface system-u9-config-summary">
      <article>
        <span>当前组织编码</span>
        <strong>{{ current.orgCode || '101' }}</strong>
        <p>{{ sourceText }}</p>
      </article>
      <article>
        <span>影响范围</span>
        <strong>U9 主数据 / 销售订单同步</strong>
        <p>客户、Seller、SaleDepartment、同步前校验和 U9 核对都读取该组织编码。</p>
      </article>
    </section>

    <section class="erp-table-surface system-u9-config-form-surface">
      <div class="erp-table-header">
        <div>
          <h2 class="erp-table-title">组织编码</h2>
          <span class="system-u9-config-note">仅保存组织编码；留空不允许保存。</span>
        </div>
      </div>
      <el-form class="erp-dialog-form system-u9-config-form" label-width="110px" @submit.prevent>
        <el-form-item label="组织编码" required>
          <el-input v-model.trim="form.orgCode" maxlength="40" show-word-limit placeholder="默认 101" />
        </el-form-item>
        <el-form-item>
          <el-button
            v-permission="'system:config:integration:update'"
            type="primary"
            :loading="saving"
            :disabled="saving"
            @click="saveConfig"
          >
            保存
          </el-button>
        </el-form-item>
      </el-form>
    </section>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { getU9RuntimeConfig, saveU9RuntimeConfig } from '../../services/systemConfigApi'

const loading = ref(false)
const saving = ref(false)
const current = reactive({ orgCode: '101', source: 'DEFAULT', updatedAt: '', fallback: true })
const form = reactive({ orgCode: '101' })

const requestErrorMessage = (error, fallback) => error?.response?.data?.message || error?.payload?.message || error?.message || fallback

const sourceText = computed(() => {
  if (current.source === 'DB') return `来自系统配置${current.updatedAt ? `，更新于 ${formatDateTime(current.updatedAt)}` : ''}`
  if (current.source === 'ENV') return '来自 erp.env 的 ERP_U9_ORG_CODE'
  return '未配置时使用默认 101'
})

const applyConfig = (data = {}) => {
  current.orgCode = data.orgCode || '101'
  current.source = data.source || 'DEFAULT'
  current.updatedAt = data.updatedAt || ''
  current.fallback = data.fallback !== false
  form.orgCode = current.orgCode
}

const loadConfig = async () => {
  loading.value = true
  try {
    applyConfig(await getU9RuntimeConfig())
  } catch (error) {
    ElMessage.error(requestErrorMessage(error, 'U9组织编码加载失败'))
  } finally {
    loading.value = false
  }
}

const saveConfig = async () => {
  const orgCode = String(form.orgCode || '').trim()
  if (!orgCode) {
    ElMessage.warning('请填写U9组织编码')
    return
  }
  saving.value = true
  try {
    const response = await saveU9RuntimeConfig({ orgCode })
    applyConfig(response?.data || response)
    ElMessage.success(response?.message || 'U9组织编码已保存')
  } catch (error) {
    ElMessage.error(requestErrorMessage(error, 'U9组织编码保存失败'))
  } finally {
    saving.value = false
  }
}

const formatDateTime = (value) => {
  if (!value) return ''
  return String(value).replace('T', ' ').slice(0, 19)
}

onMounted(loadConfig)
</script>

<style scoped>
.system-u9-config-panel {
  display: grid;
  gap: var(--erp-space-4);
}

.system-u9-config-toolbar {
  align-items: center;
  display: flex;
  gap: var(--erp-space-4);
  justify-content: space-between;
  padding: var(--erp-space-4);
}

.system-u9-config-summary {
  display: grid;
  gap: var(--erp-space-3);
  grid-template-columns: repeat(auto-fit, minmax(260px, 1fr));
  padding: var(--erp-space-4);
}

.system-u9-config-summary article {
  background: var(--erp-surface-muted);
  border: 1px solid var(--erp-border-color);
  border-radius: var(--erp-radius-md);
  display: grid;
  gap: var(--erp-space-1);
  padding: var(--erp-space-3);
}

.system-u9-config-summary span,
.system-u9-config-note {
  color: var(--erp-text-secondary);
  font-size: var(--erp-font-size-sm);
}

.system-u9-config-summary strong {
  color: var(--erp-text-primary);
}

.system-u9-config-summary p,
.system-u9-config-note {
  margin: 0;
}

.system-u9-config-form-surface {
  padding-bottom: var(--erp-space-4);
}

.system-u9-config-form {
  max-width: 480px;
  padding: var(--erp-space-4);
}

@media (max-width: 760px) {
  .system-u9-config-toolbar {
    align-items: flex-start;
    flex-direction: column;
  }
}
</style>
