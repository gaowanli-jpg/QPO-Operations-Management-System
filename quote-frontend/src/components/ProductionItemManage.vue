<template>
  <div class="erp-page production-item-page" v-permission="PRODUCTION_ITEM_VIEW_PERMISSION">
    <header class="erp-page-header">
      <div>
        <h1 class="erp-page-title">待产零件</h1>
        <p class="erp-page-description">处理 WMS 拆分后仍需下发 MES 生产的待产行。</p>
      </div>
    </header>

    <section class="erp-table-surface production-item-placeholder">
      <div class="erp-empty-state">
        <h3>待产零件工作台骨架已建立</h3>
        <p>后续阶段会把待生产 allocation 查询、筛选和下发 MES 生产操作迁移到这里。</p>
      </div>
    </section>
  </div>
</template>

<script setup>
const PRODUCTION_ITEM_VIEW_PERMISSION = 'market:production-item:view'
</script>

<style scoped>
.production-item-placeholder {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 280px;
}

.erp-empty-state {
  max-width: 520px;
  color: var(--erp-text-secondary);
  text-align: center;
}

.erp-empty-state h3 {
  margin: 0 0 var(--erp-space-2);
  color: var(--erp-text);
  font-size: var(--erp-font-size-lg);
}

.erp-empty-state p {
  margin: 0;
  line-height: var(--erp-line-height-relaxed);
}
</style>