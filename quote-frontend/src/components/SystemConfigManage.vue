<template>
  <div class="erp-page system-config-page" v-permission="'system:config:view'">
    <header class="erp-page-header system-config-page-header">
      <div>
        <h1 class="erp-page-title">系统配置</h1>
        <p class="erp-page-description">管理 QPO 文件存储、外部系统出站连接和入站回调入口配置。</p>
      </div>
    </header>

    <el-tabs v-model="activeTab" class="system-config-tabs">
      <el-tab-pane label="文件存储" name="storage">
        <SystemStorageConfigPanel />
      </el-tab-pane>
      <el-tab-pane label="出站接口" name="outbound">
        <SystemIntegrationConfigPanel />
      </el-tab-pane>
      <el-tab-pane label="入站接口" name="inbound">
        <SystemInboundCallbackConfigPanel />
      </el-tab-pane>
    </el-tabs>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import SystemStorageConfigPanel from './system-config/SystemStorageConfigPanel.vue'
import SystemIntegrationConfigPanel from './system-config/SystemIntegrationConfigPanel.vue'
import SystemInboundCallbackConfigPanel from './system-config/SystemInboundCallbackConfigPanel.vue'

const activeTab = ref('storage')
</script>

<style scoped>
.system-config-page {
  display: grid;
  gap: var(--erp-space-4);
}

.system-config-tabs :deep(.el-tabs__header) {
  background: var(--erp-surface);
  border-radius: var(--erp-radius-lg);
  margin: 0 0 var(--erp-space-4);
  padding: 0 var(--erp-space-4);
}
</style>
