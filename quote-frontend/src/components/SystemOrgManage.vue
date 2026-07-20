<template>
  <div class="erp-page system-org-page">
    <header class="erp-page-header">
      <div>
        <h1 class="erp-page-title">组织档案</h1>
        <p class="erp-page-description">维护部门和岗位这两类用户身份属性，不参与订单数据范围隔离。</p>
      </div>
    </header>

    <section class="erp-surface system-org-placeholder">
      <el-tabs model-value="departments">
        <el-tab-pane label="部门管理" name="departments">
          <el-empty description="部门管理将在组织身份体系阶段 1 接入" />
        </el-tab-pane>
        <el-tab-pane label="岗位管理" name="posts">
          <el-empty description="岗位管理将在组织身份体系阶段 1 接入" />
        </el-tab-pane>
      </el-tabs>
    </section>
  </div>
</template>

<style scoped>
.system-org-placeholder {
  padding: var(--erp-space-4);
}
</style>
