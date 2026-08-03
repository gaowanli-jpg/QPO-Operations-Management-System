<template>
  <div class="system-inbound-callback-config-panel">
    <section class="erp-surface system-inbound-callback-toolbar">
      <div>
        <h2 class="erp-section-title">入站接口</h2>
        <p class="erp-page-description">管理外部系统调用 QPO 的回调入口、机器令牌和启用状态。</p>
      </div>
    </section>

    <section class="erp-surface system-inbound-callback-card">
      <div class="erp-section-header">
        <div>
          <h2 class="erp-section-title">MES 物料号回调</h2>
          <p class="system-inbound-callback-note">MES 后续异步回调 QPO 写回物料号。</p>
        </div>
        <el-tag type="info" effect="plain">后续阶段接入</el-tag>
      </div>

      <div class="system-inbound-callback-body">
        <dl class="system-inbound-callback-summary">
          <div>
            <dt>回调路径</dt>
            <dd>/api/integrations/mes/material-callback</dd>
          </div>
          <div>
            <dt>认证方式</dt>
            <dd>Authorization: Bearer token</dd>
          </div>
          <div>
            <dt>配置边界</dt>
            <dd>这里管理 MES 调 QPO 的入站回调，不管理 QPO 调 MES 的出站连接。</dd>
          </div>
        </dl>
        <p class="system-inbound-callback-note">
          后续阶段接入回调地址预览、token 生成 / 轮换、启停和配置校验。
        </p>
      </div>
    </section>
  </div>
</template>

<style scoped>
.system-inbound-callback-config-panel {
  display: grid;
  gap: var(--erp-space-4);
}

.system-inbound-callback-toolbar {
  padding: var(--erp-space-4);
}

.system-inbound-callback-card .erp-section-header {
  border-radius: calc(var(--erp-radius-lg) - 1px) calc(var(--erp-radius-lg) - 1px) 0 0;
}

.system-inbound-callback-body {
  display: grid;
  gap: var(--erp-space-3);
  padding: var(--erp-space-4);
}

.system-inbound-callback-summary {
  display: grid;
  gap: var(--erp-space-3);
  grid-template-columns: repeat(auto-fit, minmax(240px, 1fr));
  margin: 0;
}

.system-inbound-callback-summary div {
  border: 1px solid var(--erp-border-color);
  border-radius: var(--erp-radius-md);
  padding: var(--erp-space-3);
}

.system-inbound-callback-summary dt {
  color: var(--erp-text-secondary);
  font-size: var(--erp-font-size-sm);
  margin-bottom: var(--erp-space-2);
}

.system-inbound-callback-summary dd {
  color: var(--erp-text-primary);
  margin: 0;
}

.system-inbound-callback-note {
  color: var(--erp-text-secondary);
  font-size: var(--erp-font-size-sm);
  margin: 0;
}
</style>
