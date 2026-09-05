<template>
  <div class="erp-page material-process-page">
    <header class="erp-page-header">
      <div>
        <h1 class="erp-page-title">材料工艺</h1>
        <p class="erp-page-description">维护材料、两级工艺主数据和费用字典，工程核价会直接读取这里的数据。</p>
      </div>
    </header>

    <section class="erp-toolbar">
      <div class="erp-toolbar-group material-toolbar">
        <el-button @click="loadAll" :loading="loading">刷新</el-button>
        <el-button v-permission="'engineer:dict:material-manage'" v-if="activeTab === 'materials'" type="warning" plain :loading="isMesSyncRunning('MES_MATERIAL') || isPending(dictActionKeys.syncMesMaterials)" :disabled="isMesSyncRunning('MES_MATERIAL') || isPending(dictActionKeys.syncMesMaterials)" @click="syncMesMaterials">从 MES 同步材料</el-button>
        <el-button v-permission="'engineer:dict:material-manage'" v-if="activeTab === 'materials'" plain :loading="scheduleLoading && scheduleTarget === 'MES_MATERIAL'" :disabled="scheduleLoading || isPending(dictActionKeys.saveMesSchedule)" @click="openMesMaterialScheduleDialog">定时同步策略</el-button>
        <el-button v-permission="'engineer:dict:process-manage'" v-if="activeTab === 'processCategories'" type="warning" plain :loading="isMesSyncRunning('MES_PROCESS_TYPE') || isPending(dictActionKeys.syncMesProcessTypes)" :disabled="isMesSyncRunning('MES_PROCESS_TYPE') || isPending(dictActionKeys.syncMesProcessTypes)" @click="syncMesProcessTypes">从 MES 同步工艺</el-button>
        <el-button v-permission="'engineer:dict:process-manage'" v-if="activeTab === 'processCategories'" plain :loading="scheduleLoading && scheduleTarget === 'MES_PROCESS_TYPE'" :disabled="scheduleLoading || isPending(dictActionKeys.saveMesSchedule)" @click="openMesProcessScheduleDialog">定时同步策略</el-button>
        <el-button v-permission="'engineer:dict:process-manage'" v-else-if="activeTab === 'processDetails'" type="primary" @click="openProcessDetailDialog()">新增具体工艺</el-button>
        <el-button v-permission="'engineer:dict:fee-manage'" v-else-if="activeTab === 'pricingMarkupFees' && pricingMarkupFeeCategory === 'BASIC_PERCENT'" type="primary" @click="openPricingMarkupFeeDialog()">新增基础百分比费用</el-button>
        <el-button v-permission="'engineer:dict:fee-manage'" v-else-if="activeTab === 'extraFees'" type="primary" @click="openExtraFeeDialog()">新增非工时费用工艺</el-button>
        <el-button v-permission="'engineer:dict:process-manage'" v-if="activeTab === 'processArrangement'" type="primary" @click="openArrangementNodeDialog()">新增根节点</el-button>
        <el-button v-permission="'engineer:dict:process-manage'" v-if="activeTab === 'processArrangement'" :disabled="!selectedArrangementNode" @click="openArrangementNodeDialog(selectedArrangementNode, 'child')">新增子节点</el-button>
      </div>
    </section>

    <section class="erp-table-surface material-table-surface">
      <div class="erp-table-body">
        <el-tabs v-model="activeTab">
          <el-tab-pane label="材料费用" name="materials">
            <div class="material-filter-bar">
              <el-input v-model="materialKeyword" clearable placeholder="搜索材料编码、名称、材料类型或U9类别" />
            </div>
            <el-table :data="materials" border stripe height="520" v-loading="loading">
              <el-table-column prop="materialCode" label="材料编码" width="150" fixed show-overflow-tooltip />
              <el-table-column prop="materialName" label="材料名称" width="140" fixed show-overflow-tooltip />
              <el-table-column label="材料类型" width="170" show-overflow-tooltip>
                <template #default="scope">
                  <div class="material-type-cell">
                    <span>{{ scope.row.materialTypeName || '-' }}</span>
                    <small v-if="scope.row.materialTypeCode">{{ scope.row.materialTypeCode }}</small>
                  </div>
                </template>
              </el-table-column>
              <el-table-column prop="categoryName" label="U9类别" width="120" show-overflow-tooltip />
              <el-table-column label="来源" width="110" align="center">
                <template #default="scope">
                  <el-tag :type="scope.row.sourceType === 'MES' ? 'success' : 'info'" size="small">{{ sourceTypeText(scope.row.sourceType) }}</el-tag>
                </template>
              </el-table-column>
              <el-table-column label="MES 类型" width="120" align="center">
                <template #default="scope">{{ scope.row.mesMaterialType || '-' }}</template>
              </el-table-column>
              <el-table-column label="MES 密度" width="130" align="right">
                <template #default="scope">{{ formatDensity(scope.row.mesDensityRaw) }}</template>
              </el-table-column>
              <el-table-column label="同步时间" width="170" align="center">
                <template #default="scope">{{ formatSyncTime(scope.row.mesSyncedAt) }}</template>
              </el-table-column>
              <el-table-column label="单价状态" width="120" align="center">
                <template #default="scope">
                  <el-tag :type="materialPriceStatusType(scope.row)" size="small">{{ materialPriceStatusText(scope.row) }}</el-tag>
                </template>
              </el-table-column>
              <el-table-column label="具体密度" width="130" align="right">
                <template #default="scope">{{ formatDensity(scope.row.density) }}</template>
              </el-table-column>
              <el-table-column
                v-for="definition in MATERIAL_PRICE_SPEC_DEFINITIONS"
                :key="definition.specCode"
                :label="definition.specName"
                width="160"
                align="right"
              >
                <template #default="scope">
                  <div class="material-spec-cell">
                    <span>{{ formatSpecUnifiedPrice(scope.row, definition.specCode) }}</span>
                    <el-button v-permission="'engineer:dict:material-manage'" size="small" link type="primary" @click="openRowMaterialSpecDialog(scope.row, definition.specCode)">
                      详情
                    </el-button>
                  </div>
                </template>
              </el-table-column>
              <el-table-column label="状态" width="90" align="center">
                <template #default="scope">
                  <el-tag :type="scope.row.enabled !== false ? 'success' : 'info'" size="small">{{ scope.row.enabled !== false ? '启用' : '停用' }}</el-tag>
                </template>
              </el-table-column>
              <el-table-column prop="sortNo" label="排序" width="90" align="center" />
              <el-table-column prop="remarks" label="备注" min-width="150" show-overflow-tooltip />
              <el-table-column label="操作" width="90" align="center" fixed="right">
                <template #default="scope">
                  <div class="erp-table-actions">
                    <el-button v-permission="'engineer:dict:material-manage'" type="primary" size="small" @click="openMaterialDialog(scope.row)">编辑</el-button>
                  </div>
                </template>
              </el-table-column>
            </el-table>
            <div v-if="materialPagination.total" class="erp-pagination material-dict-pagination">
              <el-pagination
                v-model:current-page="materialPagination.page"
                v-model:page-size="materialPagination.pageSize"
                :page-sizes="CLIENT_PAGE_SIZES"
                :pager-count="7"
                :total="materialPagination.total"
                layout="total, sizes, prev, pager, next, jumper"
                background
                @size-change="resetMaterialPage"
              />
            </div>
          </el-tab-pane>

          <el-tab-pane label="材料类型" name="materialTypes">
            <el-table :data="materialTypes" border stripe height="560" v-loading="loading">
              <el-table-column prop="sortNo" label="排序" width="80" align="center" fixed />
              <el-table-column prop="typeCode" label="类型编码" width="180" fixed show-overflow-tooltip />
              <el-table-column prop="typeName" label="类型名称" min-width="160" show-overflow-tooltip />
              <el-table-column label="状态" width="90" align="center">
                <template #default="scope">
                  <el-tag :type="scope.row.enabled !== false ? 'success' : 'info'" size="small">{{ scope.row.enabled !== false ? '启用' : '停用' }}</el-tag>
                </template>
              </el-table-column>
              <el-table-column prop="remarks" label="备注" min-width="180" show-overflow-tooltip />
            </el-table>
          </el-tab-pane>

          <el-tab-pane label="工艺大类" name="processCategories">
            <div class="process-master-grid">
              <div class="material-filter-bar">
                <el-input v-model="processCategoryKeyword" clearable placeholder="搜索工艺编码、工艺名称、来源或备注" />
              </div>
              <el-table :data="processCategories" border stripe height="520" v-loading="loading">
                <el-table-column prop="sortNo" label="序号" width="80" align="center" fixed />
                <el-table-column prop="processCode" label="工艺编码" width="150" fixed show-overflow-tooltip />
                <el-table-column prop="categoryName" label="工艺名称" min-width="160" show-overflow-tooltip />
                <el-table-column label="小时价格" width="140" align="right">
                  <template #default="scope">{{ formatHourlyRate(scope.row.hourlyRate) }}</template>
                </el-table-column>
                <el-table-column label="来源" width="110" align="center">
                  <template #default="scope">
                    <el-tag :type="scope.row.sourceType === 'MES' ? 'success' : 'info'" size="small">{{ sourceTypeText(scope.row.sourceType) }}</el-tag>
                  </template>
                </el-table-column>
                <el-table-column label="同步时间" width="170" align="center">
                  <template #default="scope">{{ formatSyncTime(scope.row.mesSyncedAt) }}</template>
                </el-table-column>
                <el-table-column label="状态" width="90" align="center">
                  <template #default="scope">
                    <el-tag :type="scope.row.enabled ? 'success' : 'info'" size="small">{{ scope.row.enabled ? '启用' : '停用' }}</el-tag>
                  </template>
                </el-table-column>
                <el-table-column prop="remarks" label="备注" min-width="180" show-overflow-tooltip />
              </el-table>
              <div v-if="processCategoryPagination.total" class="erp-pagination material-dict-pagination">
                <el-pagination
                  v-model:current-page="processCategoryPagination.page"
                  v-model:page-size="processCategoryPagination.pageSize"
                  :page-sizes="CLIENT_PAGE_SIZES"
                  :pager-count="7"
                  :total="processCategoryPagination.total"
                  layout="total, sizes, prev, pager, next, jumper"
                  background
                  @size-change="resetProcessCategoryPage"
                />
              </div>
            </div>
          </el-tab-pane>

          <el-tab-pane label="具体工艺" name="processDetails">
            <div class="process-master-grid">
              <div class="material-filter-bar">
                <el-input v-model="processDetailKeyword" clearable placeholder="搜索工艺编码、具体工艺或备注" />
              </div>
              <el-table :data="processDetails" border stripe height="520" v-loading="loading">
                <el-table-column prop="sortNo" label="序号" width="80" align="center" fixed />
                <el-table-column prop="processCode" label="工艺编码" width="150" fixed show-overflow-tooltip />
                <el-table-column prop="detailName" label="具体工艺" min-width="180" fixed show-overflow-tooltip />
                <el-table-column label="状态" width="100" align="center">
                  <template #default="scope">
                    <el-tag :type="(scope.row.detailEnabled ?? scope.row.enabled) !== false ? 'success' : 'info'" size="small">{{ (scope.row.detailEnabled ?? scope.row.enabled) !== false ? '启用' : '停用' }}</el-tag>
                  </template>
                </el-table-column>
                <el-table-column label="小时价格" width="150" align="right">
                  <template #default="scope">{{ formatHourlyRate(scope.row.effectiveHourlyRate ?? scope.row.hourlyRate) }}</template>
                </el-table-column>
                <el-table-column prop="detailRemarks" label="具体备注" min-width="200" show-overflow-tooltip />
                <el-table-column label="操作" width="180" align="center" fixed="right">
                  <template #default="scope">
                    <div class="erp-table-actions">
                      <el-button v-permission="'engineer:dict:process-manage'" type="primary" size="small" :disabled="isProcessDetailDeletePending(scope.row)" @click="openProcessDetailDialog(scope.row)">编辑</el-button>
                      <el-button v-permission="'engineer:dict:process-manage'" type="danger" size="small" plain class="erp-danger-action" :loading="isProcessDetailDeletePending(scope.row)" :disabled="isProcessDetailDeletePending(scope.row)" @click="deleteProcessDetail(scope.row)">删除</el-button>
                    </div>
                  </template>
                </el-table-column>
              </el-table>
              <div v-if="processDetailPagination.total" class="erp-pagination material-dict-pagination">
                <el-pagination
                  v-model:current-page="processDetailPagination.page"
                  v-model:page-size="processDetailPagination.pageSize"
                  :page-sizes="CLIENT_PAGE_SIZES"
                  :pager-count="7"
                  :total="processDetailPagination.total"
                  layout="total, sizes, prev, pager, next, jumper"
                  background
                  @size-change="resetProcessDetailPage"
                />
              </div>
            </div>
          </el-tab-pane>

          <el-tab-pane label="工艺编排" name="processArrangement">
            <div class="process-arrangement-workbench">
              <section class="process-arrangement-tree-panel">
                <div class="process-arrangement-panel-header">
                  <div>
                    <strong>编排层级</strong>
                    <span>当前放入位置：{{ arrangementTargetName }}</span>
                  </div>
                  <el-button size="small" @click="selectArrangementRoot">选择根层级</el-button>
                </div>
                <el-empty v-if="!processArrangementTree.length" description="暂无编排节点，可先新增根节点或直接把工艺放到根层级。" :image-size="80" />
                <el-tree
                  v-else
                  ref="processArrangementTreeRef"
                  :data="processArrangementTree"
                  node-key="treeKey"
                  :props="processArrangementTreeProps"
                  :default-expanded-keys="processArrangementExpandedKeys"
                  highlight-current
                  @node-click="handleArrangementTreeClick"
                  @node-expand="handleArrangementNodeExpand"
                  @node-collapse="handleArrangementNodeCollapse"
                >
                  <template #default="{ data }">
                    <div class="process-arrangement-tree-row" :class="{ 'is-selected': isArrangementTarget(data) }">
                      <div class="process-arrangement-node-main">
                        <el-tag size="small" :type="data.nodeType === 'node' ? 'info' : arrangementSourceTagType(data.sourceType)">
                          {{ data.nodeType === 'node' ? '分组' : arrangementSourceText(data.sourceType) }}
                        </el-tag>
                        <span class="process-arrangement-node-label">{{ data.label }}</span>
                        <el-tag v-if="data.nodeType === 'item' && data.enabled === false" size="small" type="danger">停用</el-tag>
                      </div>
                      <div class="process-arrangement-node-actions">
                        <template v-if="data.nodeType === 'node'">
                          <el-button v-permission="'engineer:dict:process-manage'" size="small" link type="primary" @click.stop="openArrangementNodeDialog(data, 'child')">子级</el-button>
                          <el-button v-permission="'engineer:dict:process-manage'" size="small" link type="primary" @click.stop="openArrangementNodeDialog(data, 'edit')">改名</el-button>
                          <el-button v-permission="'engineer:dict:process-manage'" size="small" link @click.stop="moveArrangementNode(data, -1)">上移</el-button>
                          <el-button v-permission="'engineer:dict:process-manage'" size="small" link @click.stop="moveArrangementNode(data, 1)">下移</el-button>
                          <el-button v-permission="'engineer:dict:process-manage'" size="small" link type="danger" @click.stop="deleteArrangementNode(data)">删除</el-button>
                        </template>
                        <template v-else>
                          <el-button v-permission="'engineer:dict:process-manage'" size="small" link @click.stop="moveArrangementItem(data, -1)">上移</el-button>
                          <el-button v-permission="'engineer:dict:process-manage'" size="small" link @click.stop="moveArrangementItem(data, 1)">下移</el-button>
                          <el-button v-permission="'engineer:dict:process-manage'" size="small" link type="danger" @click.stop="removeArrangementItem(data)">移除</el-button>
                        </template>
                      </div>
                    </div>
                  </template>
                </el-tree>
              </section>

              <section class="process-arrangement-library-panel">
                <div class="process-arrangement-panel-header library-header">
                  <div>
                    <strong>未编排工艺</strong>
                    <span>同一个工艺只能放入一处，已放入后会从这里消失。</span>
                  </div>
                  <el-button
                    v-permission="'engineer:dict:process-manage'"
                    type="primary"
                    :disabled="!selectedArrangementOptions.length"
                    :loading="isPending(dictActionKeys.placeArrangementItems)"
                    @click="placeSelectedArrangementItems"
                  >放入选中位置</el-button>
                </div>
                <div class="process-arrangement-library-filters">
                  <el-input v-model="arrangementKeyword" clearable placeholder="搜索工艺名称、编码、大类或备注" />
                  <el-radio-group v-model="arrangementSourceType" size="small">
                    <el-radio-button v-for="option in arrangementSourceTypeOptions" :key="option.value" :label="option.value">{{ option.label }}</el-radio-button>
                  </el-radio-group>
                </div>
                <el-table
                  ref="arrangementOptionTableRef"
                  :data="unplacedArrangementOptions"
                  border
                  stripe
                  height="470"
                  row-key="optionKey"
                  v-loading="arrangementLoading"
                  @selection-change="handleArrangementOptionSelection"
                >
                  <el-table-column type="selection" width="44" />
                  <el-table-column label="类型" width="100" align="center">
                    <template #default="{ row }">
                      <el-tag :type="arrangementSourceTagType(row.sourceType)" size="small">{{ arrangementSourceText(row.sourceType) }}</el-tag>
                    </template>
                  </el-table-column>
                  <el-table-column prop="label" label="工艺" min-width="220" show-overflow-tooltip />
                  <el-table-column label="状态" width="90" align="center">
                    <template #default="{ row }">
                      <el-tag :type="row.enabled !== false ? 'success' : 'danger'" size="small">{{ row.enabled !== false ? '启用' : '停用' }}</el-tag>
                    </template>
                  </el-table-column>
                </el-table>
              </section>
            </div>
          </el-tab-pane>

          <el-tab-pane label="百分比费用" name="pricingMarkupFees">
            <div class="pricing-markup-panel">
              <div class="pricing-markup-controls">
                <div class="pricing-markup-control-group">
                  <span class="pricing-markup-control-label">适用范围</span>
                  <el-radio-group v-model="pricingMarkupScope" size="small">
                    <el-radio-button v-for="option in pricingMarkupScopeOptions" :key="option.value" :label="option.value">{{ option.label }}</el-radio-button>
                  </el-radio-group>
                </div>
                <div class="pricing-markup-control-group">
                  <span class="pricing-markup-control-label">费用分类</span>
                  <el-radio-group v-model="pricingMarkupFeeCategory" size="small">
                    <el-radio-button v-for="option in pricingMarkupFeeCategoryOptions" :key="option.value" :label="option.value">{{ option.label }}</el-radio-button>
                  </el-radio-group>
                </div>
                <el-input v-model="pricingMarkupKeyword" clearable placeholder="搜索费用名称、适用范围、分类或状态" />
              </div>
              <el-table :data="pagedPricingMarkupFees" border stripe height="520" v-loading="loading">
                <el-table-column prop="sortNo" label="序号" width="80" align="center" fixed />
                <el-table-column prop="feeName" label="费用名称" min-width="150" fixed show-overflow-tooltip />
                <el-table-column label="费率" width="120" align="right">
                  <template #default="{ row }">{{ Number(row.ratePercent || 0).toFixed(2) }}%</template>
                </el-table-column>
                <el-table-column prop="pricingScope" label="适用范围" width="120" align="center">
                  <template #default="{ row }">{{ pricingScopeText(row.pricingScope) }}</template>
                </el-table-column>
                <el-table-column prop="feeCategory" label="分类" width="140" align="center">
                  <template #default="{ row }">{{ pricingMarkupFeeCategoryText(row.feeCategory) }}</template>
                </el-table-column>
                <el-table-column label="启用" width="90" align="center">
                  <template #default="{ row }">
                    <el-tag :type="row.enabled ? 'success' : 'info'" size="small">{{ row.enabled ? '启用' : '停用' }}</el-tag>
                  </template>
                </el-table-column>
                <el-table-column prop="systemRequired" label="系统项" width="100" align="center">
                  <template #default="{ row }">
                    <el-tag :type="row.systemRequired ? 'warning' : 'info'" size="small">{{ row.systemRequired ? '系统项' : '普通项' }}</el-tag>
                  </template>
                </el-table-column>
                <el-table-column label="操作" width="180" align="center" fixed="right">
                  <template #default="{ row }">
                    <div class="erp-table-actions">
                      <el-button v-permission="'engineer:dict:fee-manage'" type="primary" size="small" :disabled="isPricingMarkupFeeDeletePending(row)" @click="openPricingMarkupFeeDialog(row)">编辑</el-button>
                      <el-button v-permission="'engineer:dict:fee-manage'" type="danger" size="small" plain class="erp-danger-action" :loading="isPricingMarkupFeeDeletePending(row)" :disabled="row.systemRequired || isPricingMarkupFeeDeletePending(row)" @click="deletePricingMarkupFee(row)">删除</el-button>
                    </div>
                  </template>
                </el-table-column>
              </el-table>
              <div v-if="filteredPricingMarkupFees.length" class="erp-pagination material-dict-pagination">
                <el-pagination
                  v-model:current-page="pricingMarkupPagination.page"
                  v-model:page-size="pricingMarkupPagination.pageSize"
                  :page-sizes="CLIENT_PAGE_SIZES"
                  :pager-count="7"
                  :total="filteredPricingMarkupFees.length"
                  layout="total, sizes, prev, pager, next, jumper"
                  background
                  @size-change="resetPricingMarkupFeePage"
                />
              </div>
            </div>
          </el-tab-pane>

          <el-tab-pane label="非工时费用工艺" name="extraFees">
            <div class="process-master-grid">
              <div class="material-filter-bar">
                <el-input v-model="extraFeeKeyword" clearable placeholder="搜索工艺编码、工艺名称或备注" />
              </div>
              <el-table :data="extraFeeItems" border stripe height="520" v-loading="loading">
                <el-table-column prop="sortNo" label="序号" width="80" align="center" fixed />
                <el-table-column prop="feeCode" label="工艺编码" width="150" fixed show-overflow-tooltip />
                <el-table-column prop="feeName" label="工艺名称" min-width="180" show-overflow-tooltip />
                <el-table-column prop="remarks" label="备注" min-width="180" show-overflow-tooltip />
                <el-table-column label="启用" width="90" align="center">
                  <template #default="scope">
                    <el-tag :type="scope.row.enabled ? 'success' : 'info'" size="small">{{ scope.row.enabled ? '启用' : '停用' }}</el-tag>
                  </template>
                </el-table-column>
                <el-table-column label="操作" width="160" align="center" fixed="right">
                  <template #default="scope">
                    <div class="erp-table-actions">
                      <el-button v-permission="'engineer:dict:fee-manage'" type="primary" size="small" :disabled="isExtraFeeDeletePending(scope.row)" @click="openExtraFeeDialog(scope.row)">编辑</el-button>
                      <el-button v-permission="'engineer:dict:fee-manage'" type="danger" size="small" plain class="erp-danger-action" :loading="isExtraFeeDeletePending(scope.row)" :disabled="isExtraFeeDeletePending(scope.row)" @click="deleteExtraFee(scope.row)">删除</el-button>
                    </div>
                  </template>
                </el-table-column>
              </el-table>
              <div v-if="extraFeePagination.total" class="erp-pagination material-dict-pagination">
                <el-pagination
                  v-model:current-page="extraFeePagination.page"
                  v-model:page-size="extraFeePagination.pageSize"
                  :page-sizes="CLIENT_PAGE_SIZES"
                  :pager-count="7"
                  :total="extraFeePagination.total"
                  layout="total, sizes, prev, pager, next, jumper"
                  background
                  @size-change="resetExtraFeePage"
                />
              </div>
            </div>
          </el-tab-pane>
        </el-tabs>
      </div>
    </section>

    <el-dialog v-model="materialDialogVisible" :title="materialForm.id ? '编辑材料' : '新增材料'" width="820px">
      <el-form :model="materialForm" label-width="120px" class="erp-dialog-form process-dialog-grid material-dialog-grid">
        <el-form-item label="材料编码" required>
          <el-input v-model="materialForm.materialCode" disabled placeholder="例如：MAT-000001" />
        </el-form-item>
        <el-form-item label="材料名称" required>
          <el-input v-model="materialForm.materialName" disabled placeholder="例如：45#" />
        </el-form-item>
        <el-form-item label="材料类型" required>
          <el-select v-model="materialForm.materialTypeId" filterable disabled class="dialog-select" placeholder="选择材料类型">
            <el-option v-for="type in materialTypes" :key="type.id" :label="materialTypeLabel(type)" :value="type.id" :disabled="type.enabled === false" />
          </el-select>
        </el-form-item>
        <el-form-item label="材质分类">
          <el-input v-model="materialForm.materialCategory" disabled placeholder="例如：钢铁、铝、塑料" />
        </el-form-item>
        <el-form-item label="排序">
          <el-input-number v-model="materialForm.sortNo" :min="0" controls-position="right" class="dialog-number-input" />
        </el-form-item>
        <el-form-item label="启用">
          <el-switch v-model="materialForm.enabled" />
        </el-form-item>
        <el-form-item label="具体密度">
          <div class="unit-input">
            <el-input-number v-model="materialForm.density" :precision="4" :min="0" controls-position="right" class="unit-input-control" />
            <span class="unit-label">kg/m³</span>
          </div>
        </el-form-item>
        <el-form-item label="规格价格" class="process-dialog-wide">
          <div class="material-spec-grid">
            <div v-for="definition in MATERIAL_PRICE_SPEC_DEFINITIONS" :key="definition.specCode" class="material-spec-card">
              <span>{{ definition.specName }}</span>
              <strong>{{ formatSpecUnifiedPrice(materialForm, definition.specCode) }}</strong>
              <el-button type="primary" plain size="small" @click="openMaterialFormSpecDialog(definition.specCode)">
                详情
              </el-button>
            </div>
          </div>
        </el-form-item>
        <el-form-item label="备注" class="process-dialog-wide">
          <el-input v-model="materialForm.remarks" type="textarea" :rows="3" />
        </el-form-item>
      </el-form>
      <template #footer>
        <div class="erp-dialog-footer">
          <el-button @click="materialDialogVisible = false">取消</el-button>
          <el-button v-permission="'engineer:dict:material-manage'" type="primary" :loading="isPending(dictActionKeys.saveMaterialDialog)" :disabled="isPending(dictActionKeys.saveMaterialDialog)" @click="saveMaterial">保存</el-button>
        </div>
      </template>
    </el-dialog>

    <MaterialPriceSpecDetailDialog
      v-model="materialSpecDialogVisible"
      :spec="activeMaterialSpecDraft"
      @update:spec="updateActiveMaterialSpecDraft"
    />

    <el-dialog v-model="processDetailDialogVisible" :title="processDetailForm.id ? '编辑具体工艺' : '新增具体工艺'" width="720px">
      <el-form :model="processDetailForm" label-width="120px" class="erp-dialog-form process-dialog-grid">
        <el-form-item label="序号">
          <el-input-number v-model="processDetailForm.sortNo" :min="0" controls-position="right" class="dialog-number-input" />
        </el-form-item>
        <el-form-item label="工艺编码" required>
          <el-input v-model="processDetailForm.processCode" :readonly="!!processDetailForm.id" placeholder="例如：CNC_FINE" />
        </el-form-item>
        <el-form-item label="具体工艺" required>
          <el-input v-model="processDetailForm.detailName" placeholder="例如：精细包装" />
        </el-form-item>
        <el-form-item label="启用">
          <el-switch v-model="processDetailForm.enabled" />
        </el-form-item>
        <el-form-item label="小时价格" required>
          <div class="unit-input">
            <el-input-number v-model="processDetailForm.hourlyRate" :min="0" :precision="2" controls-position="right" class="unit-input-control" />
            <span class="unit-label wide">元/小时</span>
          </div>
          <div class="form-helper-text">具体工艺按这里维护的小时价格核价；填 0 表示按 0 元/小时核价。</div>
        </el-form-item>
        <el-form-item label="具体备注" class="process-dialog-wide">
          <el-input v-model="processDetailForm.detailRemarks" type="textarea" :rows="3" placeholder="例如：包装选择精细包装" />
        </el-form-item>
      </el-form>
      <template #footer>
        <div class="erp-dialog-footer">
          <el-button @click="processDetailDialogVisible = false">取消</el-button>
          <el-button v-permission="'engineer:dict:process-manage'" type="primary" :loading="isPending(dictActionKeys.saveProcessDetailDialog)" :disabled="isPending(dictActionKeys.saveProcessDetailDialog)" @click="saveProcessDetail">保存</el-button>
        </div>
      </template>
    </el-dialog>

    <el-dialog v-model="pricingMarkupFeeDialogVisible" :title="pricingMarkupFeeForm.id ? '编辑百分比费用' : '新增基础百分比费用'" width="620px">
      <el-form :model="pricingMarkupFeeForm" label-width="110px" class="erp-dialog-form process-dialog-grid">
        <el-form-item label="适用范围" required>
          <el-select v-model="pricingMarkupFeeForm.pricingScope" disabled class="dialog-select">
            <el-option v-for="option in pricingMarkupScopeOptions" :key="option.value" :label="option.label" :value="option.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="费用分类" required>
          <el-select v-model="pricingMarkupFeeForm.feeCategory" disabled class="dialog-select">
            <el-option v-for="option in pricingMarkupFeeCategoryOptions" :key="option.value" :label="option.label" :value="option.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="序号">
          <el-input-number v-model="pricingMarkupFeeForm.sortNo" :min="0" :disabled="!pricingMarkupIdentityEditable" controls-position="right" class="dialog-number-input" />
        </el-form-item>
        <el-form-item label="费用名称" required>
          <el-input v-model="pricingMarkupFeeForm.feeName" :disabled="!pricingMarkupIdentityEditable" placeholder="例如：运输费、管理费、利润" />
        </el-form-item>
        <el-form-item label="费率(%)" required>
          <el-input-number v-model="pricingMarkupFeeForm.ratePercent" :precision="2" :min="0" controls-position="right" class="dialog-number-input" />
        </el-form-item>
        <el-form-item label="启用">
          <el-switch v-model="pricingMarkupFeeForm.enabled" />
        </el-form-item>
        <el-form-item label="系统项">
          <el-tag :type="pricingMarkupFeeForm.systemRequired ? 'warning' : 'info'">{{ pricingMarkupFeeForm.systemRequired ? '不可删除' : '可删除' }}</el-tag>
        </el-form-item>
      </el-form>
      <template #footer>
        <div class="erp-dialog-footer">
          <el-button @click="pricingMarkupFeeDialogVisible = false">取消</el-button>
          <el-button v-permission="'engineer:dict:fee-manage'" type="primary" :loading="isPending(dictActionKeys.savePricingMarkupFeeDialog)" :disabled="isPending(dictActionKeys.savePricingMarkupFeeDialog)" @click="savePricingMarkupFee">保存</el-button>
        </div>
      </template>
    </el-dialog>

    <el-dialog v-model="extraFeeDialogVisible" :title="extraFeeForm.id ? '编辑非工时费用工艺' : '新增非工时费用工艺'" width="560px">
      <el-form :model="extraFeeForm" label-width="120px" class="erp-dialog-form">
        <el-form-item label="序号">
          <el-input-number v-model="extraFeeForm.sortNo" :min="0" controls-position="right" style="width: 100%;" />
        </el-form-item>
        <el-form-item label="工艺编码" required>
          <el-input v-model="extraFeeForm.feeCode" :readonly="!!extraFeeForm.id && !!extraFeeOriginalCode" placeholder="例如：SURFACE_TREATMENT" />
          <div class="form-helper-text">编码是使用次数排序的稳定身份；删除后可用旧编码重建并继承热度。</div>
        </el-form-item>
        <el-form-item label="工艺名称" required>
          <el-input v-model="extraFeeForm.feeName" placeholder="例如：EDM、热处理、表面处理、QA" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="extraFeeForm.remarks" />
        </el-form-item>
        <el-form-item label="启用">
          <el-switch v-model="extraFeeForm.enabled" />
        </el-form-item>
      </el-form>
      <template #footer>
        <div class="erp-dialog-footer">
          <el-button @click="extraFeeDialogVisible = false">取消</el-button>
          <el-button v-permission="'engineer:dict:fee-manage'" type="primary" :loading="isPending(dictActionKeys.saveExtraFeeDialog)" :disabled="isPending(dictActionKeys.saveExtraFeeDialog)" @click="saveExtraFee">保存</el-button>
        </div>
      </template>
    </el-dialog>

    <el-dialog v-model="arrangementNodeDialogVisible" :title="arrangementNodeDialogTitle" width="520px">
      <el-form :model="arrangementNodeForm" label-width="110px" class="erp-dialog-form">
        <el-form-item label="父级位置">
          <el-input :model-value="arrangementNodeParentName" disabled />
        </el-form-item>
        <el-form-item label="节点名称" required>
          <el-input v-model="arrangementNodeForm.nodeName" maxlength="120" show-word-limit placeholder="例如：铣、粗铣、精铣" />
        </el-form-item>
        <el-form-item label="排序">
          <el-input-number v-model="arrangementNodeForm.sortNo" controls-position="right" class="dialog-number-input" />
        </el-form-item>
      </el-form>
      <template #footer>
        <div class="erp-dialog-footer">
          <el-button @click="arrangementNodeDialogVisible = false">取消</el-button>
          <el-button v-permission="'engineer:dict:process-manage'" type="primary" :loading="isPending(dictActionKeys.saveArrangementNode)" :disabled="isPending(dictActionKeys.saveArrangementNode)" @click="saveArrangementNode">保存</el-button>
        </div>
      </template>
    </el-dialog>

    <el-dialog
      v-model="scheduleDialogVisible"
      :title="scheduleDialogTitle"
      width="min(560px, 92vw)"
      class="mes-schedule-dialog"
      destroy-on-close
    >
      <div v-loading="scheduleLoading" class="mes-schedule-panel">
        <el-alert
          class="mes-schedule-alert"
          type="info"
          :closable="false"
          show-icon
          :title="scheduleDialogHelpText"
        />
        <el-form label-width="150px" class="erp-dialog-form" :class="'mes-schedule-form'">
          <el-form-item label="开启定时同步">
            <el-switch v-model="scheduleForm.enabled" active-text="开启" inactive-text="关闭" />
          </el-form-item>
          <el-form-item label="同步策略">
            <el-radio-group v-model="scheduleForm.scheduleMode">
              <el-radio-button
                v-for="option in scheduleModeOptions"
                :key="option.scheduleMode"
                :label="option.scheduleMode"
              >{{ option.label }}</el-radio-button>
            </el-radio-group>
          </el-form-item>
          <el-form-item v-if="scheduleForm.scheduleMode === 'FIXED_DELAY'" label="启动后首次执行">
            <el-input-number
              v-model="scheduleForm.startupDelaySeconds"
              :min="0"
              :max="86400"
              :step="30"
              controls-position="right"
            />
            <span class="mes-schedule-unit">秒</span>
          </el-form-item>
          <el-form-item v-if="scheduleForm.scheduleMode === 'FIXED_DELAY'" label="完成后再次执行">
            <el-input-number
              v-model="scheduleForm.fixedDelayMinutes"
              :min="1"
              :max="10080"
              :step="5"
              controls-position="right"
            />
            <span class="mes-schedule-unit">分钟</span>
          </el-form-item>
          <el-form-item v-if="scheduleForm.scheduleMode === 'DAILY_TIME'" label="每天执行时间">
            <el-time-picker
              v-model="scheduleForm.dailyRunTime"
              value-format="HH:mm:ss"
              format="HH:mm"
              placeholder="选择时间"
            />
          </el-form-item>
        </el-form>
        <el-descriptions :column="1" size="small" border class="mes-schedule-status">
          <el-descriptions-item label="同步对象">{{ scheduleTargetLabel }}</el-descriptions-item>
          <el-descriptions-item label="配置来源">{{ scheduleForm.fallback ? '环境兜底' : '数据库策略' }}</el-descriptions-item>
          <el-descriptions-item label="上次定时状态">{{ scheduleStatusLabel(scheduleForm.lastScheduledStatus) }}</el-descriptions-item>
          <el-descriptions-item label="上次定时时间">{{ formatSyncTime(scheduleForm.lastScheduledRunAt) }}</el-descriptions-item>
          <el-descriptions-item label="同步批次">{{ scheduleForm.lastScheduledSyncRunId || '-' }}</el-descriptions-item>
          <el-descriptions-item label="说明">{{ scheduleForm.lastScheduledMessage || '-' }}</el-descriptions-item>
        </el-descriptions>
      </div>
      <template #footer>
        <div class="mes-schedule-footer">
          <el-button :disabled="isPending(dictActionKeys.saveMesSchedule)" @click="scheduleDialogVisible = false">取消</el-button>
          <el-button type="primary" :loading="isPending(dictActionKeys.saveMesSchedule)" :disabled="scheduleLoading || isPending(dictActionKeys.saveMesSchedule)" @click="saveMesScheduleSetting">保存策略</el-button>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, nextTick, reactive, ref, onBeforeUnmount, onMounted, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import MaterialPriceSpecDetailDialog from './material/MaterialPriceSpecDetailDialog.vue'
import {
  createProcessArrangementNode,
  deleteExtraFeeItem,
  deletePricingMarkupFee as deletePricingMarkupFeeRequest,
  deleteProcessArrangementNode,
  deleteProcessDetail as deleteProcessDetailRequest,
  getLatestMesMaterialSyncRun,
  getLatestMesProcessSyncRun,
  getMesMaterialSyncRun,
  getMesMaterialSyncSchedule,
  getMesProcessSyncRun,
  getMesProcessSyncSchedule,
  listMaterialTypes,
  listProcessArrangementTree,
  listPricingMarkupFees,
  listUnplacedProcessArrangementOptions,
  moveProcessArrangementItem,
  pageExtraFeeItems,
  pageMaterials,
  pageProcessCategories,
  pageProcessDetails,
  placeProcessArrangementItems,
  removeProcessArrangementItem,
  createPricingMarkupFee,
  saveExtraFeeItem,
  saveMaterial as saveMaterialRequest,
  saveMesMaterialSyncSchedule,
  saveMesProcessSyncSchedule,
  saveProcessDetail as saveProcessDetailRequest,
  syncMesMaterials as syncMesMaterialsRequest,
  syncMesProcessTypes as syncMesProcessTypesRequest,
  updateProcessArrangementNode,
  updatePricingMarkupFee
} from '../services/dictApi'
import { useDictionaryStore } from '../stores/dictionaryStore'
import { usePendingAction } from '../composables/usePendingAction'
import { CLIENT_PAGE_SIZES, DEFAULT_CLIENT_PAGE_SIZE, paginateRows } from '../utils/clientPagination'
import {
  collectProcessArrangementNodeKeys,
  nextProcessArrangementExpandedKeys
} from '../utils/processArrangementTreeExpansion'
import {
  MATERIAL_PRICE_SPEC_DEFINITIONS,
  findPriceSpec,
  formatMaterialSpecPrice,
  hasAnyMaterialSpecPrice,
  normalizePriceSpecs
} from '../utils/materialPriceSpecs'

const dictionaryStore = useDictionaryStore()
const pricingMarkupScopeOptions = [
  { value: 'NORMAL', label: '普通件' },
  { value: 'ASSEMBLY', label: '组装件' }
]
const pricingMarkupFeeCategoryOptions = [
  { value: 'BASIC_PERCENT', label: '基础百分比费用' },
  { value: 'TAX', label: '税率' },
  { value: 'CUSTOMER_WEIGHT', label: '客户权重' }
]
const activeTab = ref('materials')
const materialTypes = ref([])
const materials = ref([])
const processCategories = ref([])
const processDetails = ref([])
const processArrangementTree = ref([])
const processArrangementTreeRef = ref(null)
const processArrangementExpandedKeys = ref([])
const unplacedArrangementOptions = ref([])
const pricingMarkupFees = ref([])
const extraFeeItems = ref([])
const materialKeyword = ref('')
const processCategoryKeyword = ref('')
const processDetailKeyword = ref('')
const extraFeeKeyword = ref('')
const arrangementKeyword = ref('')
const arrangementSourceType = ref('')
const pricingMarkupKeyword = ref('')
const pricingMarkupScope = ref('NORMAL')
const pricingMarkupFeeCategory = ref('BASIC_PERCENT')
const createPagination = () => reactive({ page: 1, pageSize: DEFAULT_CLIENT_PAGE_SIZE, total: 0 })
const materialPagination = createPagination()
const processCategoryPagination = createPagination()
const processDetailPagination = createPagination()
const extraFeePagination = createPagination()
const pricingMarkupPagination = reactive({ page: 1, pageSize: DEFAULT_CLIENT_PAGE_SIZE })
const processArrangementTreeProps = { children: 'children', label: 'label' }
const arrangementSourceTypeOptions = [
  { value: '', label: '全部' },
  { value: 'process', label: '工时工艺' },
  { value: 'extra', label: '非工时工艺' }
]
const arrangementOptionTableRef = ref(null)
const selectedArrangementNode = ref(null)
const selectedArrangementOptions = ref([])
const arrangementLoading = ref(false)
const arrangementNodeDialogVisible = ref(false)
const arrangementNodeDialogMode = ref('root')
const arrangementNodeParentName = ref('根层级')
const arrangementNodeForm = reactive(createEmptyArrangementNode())
const deleteSummary = reactive({ removedNodeCount: 0, removedItemCount: 0 })
const resetMaterialPage = () => { materialPagination.page = 1 }
const resetProcessCategoryPage = () => { processCategoryPagination.page = 1 }
const resetProcessDetailPage = () => { processDetailPagination.page = 1 }
const resetExtraFeePage = () => { extraFeePagination.page = 1 }
const resetPricingMarkupFeePage = () => { pricingMarkupPagination.page = 1 }
const resetPageAndLoad = (pagination, loader) => {
  if (pagination.page === 1) {
    loadPageWithLoading(loader)
    return
  }
  pagination.page = 1
}
const loading = ref(false)
const { isPending, runAction } = usePendingAction()

const dictActionKeys = {
  saveMaterialDialog: 'engineer:dict:material-save-dialog',
  syncMesMaterials: 'engineer:dict:material-sync-mes',
  syncMesProcessTypes: 'engineer:dict:process-sync-mes',
  saveMesSchedule: 'engineer:dict:mes-master-data-schedule-save',
  saveProcessDetailDialog: 'engineer:dict:process-detail-save-dialog',
  deleteProcessDetail: (detailId) => `engineer:dict:process-detail-delete:${detailId || 'none'}`,
  saveArrangementNode: 'engineer:dict:process-arrangement-node-save',
  placeArrangementItems: 'engineer:dict:process-arrangement-items-place',
  moveArrangementNode: (nodeId) => `engineer:dict:process-arrangement-node-move:${nodeId || 'none'}`,
  moveArrangementItem: (itemId) => `engineer:dict:process-arrangement-item-move:${itemId || 'none'}`,
  deleteArrangementNode: (nodeId) => `engineer:dict:process-arrangement-node-delete:${nodeId || 'none'}`,
  removeArrangementItem: (itemId) => `engineer:dict:process-arrangement-item-remove:${itemId || 'none'}`,
  savePricingMarkupFeeDialog: 'engineer:dict:pricing-markup-fee-save-dialog',
  saveExtraFeeDialog: 'engineer:dict:extra-fee-save-dialog',
  deletePricingMarkupFee: (feeId) => `engineer:dict:pricing-markup-fee-delete:${feeId || 'none'}`,
  deleteExtraFee: (feeId) => `engineer:dict:extra-fee-delete:${feeId || 'none'}`
}

const isProcessDetailDeletePending = (row) => isPending(dictActionKeys.deleteProcessDetail(row?.id))
const isPricingMarkupFeeDeletePending = (row) => isPending(dictActionKeys.deletePricingMarkupFee(row?.id))
const isExtraFeeDeletePending = (row) => isPending(dictActionKeys.deleteExtraFee(row?.id))

const materialDialogVisible = ref(false)
const materialSpecDialogVisible = ref(false)
const materialSpecDialogCode = ref('SPEC_1')
const processDetailDialogVisible = ref(false)
const pricingMarkupFeeDialogVisible = ref(false)
const extraFeeDialogVisible = ref(false)
const extraFeeOriginalCode = ref('')
const scheduleDialogVisible = ref(false)
const scheduleLoading = ref(false)
const scheduleTarget = ref('MES_MATERIAL')
const MES_SYNC_POLL_INTERVAL_MS = 3000
const activeMesSyncRuns = reactive({
  MES_MATERIAL: null,
  MES_PROCESS_TYPE: null
})
const mesSyncPollingTimers = new Map()

const materialForm = reactive(createEmptyMaterial())
const processDetailForm = reactive(createEmptyProcessDetail())
const pricingMarkupFeeForm = reactive(createEmptyPricingMarkupFee())
const extraFeeForm = reactive(createEmptyExtraFee())
const scheduleForm = reactive({
  enabled: false,
  scheduleMode: 'FIXED_DELAY',
  dailyRunTime: '02:00:00',
  startupDelaySeconds: 30,
  fixedDelayMinutes: 30,
  lastScheduledRunAt: '',
  lastScheduledSyncRunId: null,
  lastScheduledStatus: 'NEVER',
  lastScheduledMessage: '',
  fallback: false
})

const scheduleModeOptions = [
  { scheduleMode: 'FIXED_DELAY', label: '间隔时间' },
  { scheduleMode: 'DAILY_TIME', label: '每天固定时间' }
]

const scheduleConfigs = {
  MES_MATERIAL: {
    title: 'MES 材料定时同步策略',
    label: 'MES 材料',
    load: getMesMaterialSyncSchedule,
    save: saveMesMaterialSyncSchedule,
    loadError: 'MES 材料定时同步策略加载失败',
    saveSuccess: 'MES 材料定时同步策略已保存',
    saveError: 'MES 材料定时同步策略保存失败'
  },
  MES_PROCESS_TYPE: {
    title: 'MES 工艺定时同步策略',
    label: 'MES 工艺',
    load: getMesProcessSyncSchedule,
    save: saveMesProcessSyncSchedule,
    loadError: 'MES 工艺定时同步策略加载失败',
    saveSuccess: 'MES 工艺定时同步策略已保存',
    saveError: 'MES 工艺定时同步策略保存失败'
  }
}

const syncConfigByType = {
  MES_MATERIAL: {
    label: 'MES 材料',
    getLatestRun: getLatestMesMaterialSyncRun,
    getRun: getMesMaterialSyncRun,
    fallbackSubmitMessage: 'MES 材料同步已提交',
    fallbackSuccessMessage: 'MES 材料同步完成',
    fallbackErrorMessage: 'MES 材料同步失败'
  },
  MES_PROCESS_TYPE: {
    label: 'MES 工艺',
    getLatestRun: getLatestMesProcessSyncRun,
    getRun: getMesProcessSyncRun,
    fallbackSubmitMessage: 'MES 工艺同步已提交',
    fallbackSuccessMessage: 'MES 工艺同步完成',
    fallbackErrorMessage: 'MES 工艺同步失败'
  }
}

const activeScheduleConfig = computed(() => scheduleConfigs[scheduleTarget.value] || scheduleConfigs.MES_MATERIAL)
const scheduleDialogTitle = computed(() => activeScheduleConfig.value.title)
const scheduleTargetLabel = computed(() => activeScheduleConfig.value.label)
const mesMaterialSyncOwnershipMessage = 'MES 会更新基础信息和 MES 密度，不覆盖 QPO 具体密度和材料规格价格'
const mesProcessSyncOwnershipMessage = 'MES 同步只更新工艺大类页，不创建、删除或覆盖 QPO 具体工艺'
const scheduleDialogHelpText = computed(() => `${scheduleTarget.value === 'MES_PROCESS_TYPE' ? mesProcessSyncOwnershipMessage : mesMaterialSyncOwnershipMessage}；策略默认关闭，保存后作为运行时配置。`)

const formatNumber = (value, precision) => {
  const numericValue = Number(value)
  return Number.isFinite(numericValue) ? numericValue.toFixed(precision) : '-'
}
const formatDensity = (value) => {
  const text = formatNumber(value, 4)
  return text === '-' ? '-' : `${text} kg/m³`
}
const formatPrice = (value) => {
  const text = formatNumber(value, 2)
  return text === '-' ? '-' : `${text} 元/kg`
}
const formatHourlyRate = (value) => {
  const text = formatNumber(value, 2)
  return text === '-' ? '-' : `${text} 元/小时`
}
const sourceTypeText = (value) => (value === 'MES' ? 'MES同步' : '本地维护')
const materialPriceStatusText = (row = {}) => (
  hasAnyMaterialSpecPrice(row.priceSpecs)
    ? '单价已维护'
    : '单价未维护'
)
const materialPriceStatusType = (row = {}) => (materialPriceStatusText(row) === '单价已维护' ? 'success' : 'danger')
const formatSpecUnifiedPrice = (row, specCode) => formatMaterialSpecPrice(findPriceSpec(row?.priceSpecs, specCode)?.unifiedPrice)
const formatSyncTime = (value) => (value ? String(value).replace('T', ' ').slice(0, 19) : '-')
const materialTypeLabel = (type) => `${type.typeCode || '-'} / ${type.typeName || '-'}`
const pricingScopeText = (value) => pricingMarkupScopeOptions.find((item) => item.value === value)?.label || value || '-'
const pricingMarkupFeeCategoryText = (value) => pricingMarkupFeeCategoryOptions.find((item) => item.value === value)?.label || value || '-'
const requestErrorMessage = (error, fallback) => error?.response?.data?.message || error?.payload?.message || error?.message || fallback
const normalizeDailyRunTime = (value) => {
  if (!value) return '02:00:00'
  if (value instanceof Date) {
    const hours = String(value.getHours()).padStart(2, '0')
    const minutes = String(value.getMinutes()).padStart(2, '0')
    const seconds = String(value.getSeconds()).padStart(2, '0')
    return `${hours}:${minutes}:${seconds}`
  }
  const text = String(value).trim()
  if (/^\d{2}:\d{2}$/.test(text)) return `${text}:00`
  if (/^\d{2}:\d{2}:\d{2}$/.test(text)) return text
  return '02:00:00'
}
const normalizeMesSyncRun = (syncType, run = {}) => ({
  ...run,
  syncType: run.syncType || syncType,
  syncRunId: run.syncRunId ?? run.id ?? null,
  status: run.status || 'NONE'
})
const isMesSyncRunning = (syncType) => activeMesSyncRuns[syncType]?.status === 'RUNNING'
const formatMesSyncResultMessage = (run = {}) => {
  const baseMessage = run.message || syncConfigByType[run.syncType]?.fallbackSuccessMessage || 'MES 同步完成'
  const countParts = [
    ['抓取', run.fetchedCount],
    ['有效', run.validCount],
    ['无效', run.invalidCount],
    ['重复', run.duplicateCount],
    ['更新', run.upsertedCount],
    ['停用', run.disabledCount],
    ['删除', run.deletedCount]
  ]
    .filter(([, value]) => value !== undefined && value !== null)
    .map(([label, value]) => `${label}${value}`)
  return countParts.length ? `${baseMessage}（${countParts.join('，')}）` : baseMessage
}
const scheduleStatusLabel = (status) => ({
  SUCCESS: '成功',
  FAILED: '失败',
  NEVER: '未执行'
}[status] || status || '-')

function createEmptyMaterial() {
  return {
    id: null,
    materialCode: '',
    materialName: '',
    materialTypeId: null,
    materialCategory: '',
    categoryName: '',
    remarks: '',
    enabled: true,
    sortNo: 0,
    density: null,
    priceSpecs: normalizePriceSpecs([])
  }
}

function createEmptyProcessDetail() {
  return {
    id: null,
    sortNo: 0,
    categoryId: null,
    processCode: '',
    detailName: '',
    hourlyRate: null,
    detailRemarks: '',
    enabled: true
  }
}

function createEmptyArrangementNode() {
  return {
    id: null,
    parentId: null,
    nodeName: '',
    sortNo: 0
  }
}

function createEmptyPricingMarkupFee() {
  return {
    id: null,
    sortNo: 0,
    pricingScope: pricingMarkupScope.value,
    feeCategory: pricingMarkupFeeCategory.value,
    feeName: '',
    ratePercent: 0,
    enabled: true,
    systemRequired: false
  }
}

function createEmptyExtraFee() {
  return {
    id: null,
    sortNo: 0,
    feeCode: '',
    feeName: '',
    enabled: true,
    remarks: ''
  }
}

const pageParams = (pagination, keyword) => ({
  keyword: keyword.value.trim(),
  page: pagination.page,
  pageSize: pagination.pageSize
})

const applyPageResult = (pagination, response = {}) => {
  pagination.total = Number(response.total || 0)
  pagination.page = Number(response.page || pagination.page)
  pagination.pageSize = Number(response.pageSize || pagination.pageSize)
  return response.records || []
}

const normalizePricingMarkupFee = (row = {}, pricingScope, feeCategory) => ({
  ...row,
  pricingScope: row.pricingScope || pricingScope,
  feeCategory: row.feeCategory || feeCategory,
  enabled: row.enabled !== false,
  systemRequired: row.systemRequired === true
})

const loadPricingMarkupFeeRows = async () => {
  const groups = await Promise.all(pricingMarkupScopeOptions.flatMap((scope) => (
    pricingMarkupFeeCategoryOptions.map((category) => (
      listPricingMarkupFees(scope.value, category.value)
        .then((rows) => (rows || []).map((row) => normalizePricingMarkupFee(row, scope.value, category.value)))
    ))
  )))
  return groups.flat()
}

const filteredPricingMarkupFees = computed(() => {
  const keyword = pricingMarkupKeyword.value.trim().toLowerCase()
  return pricingMarkupFees.value.filter((row) => {
    if (row.pricingScope !== pricingMarkupScope.value || row.feeCategory !== pricingMarkupFeeCategory.value) {
      return false
    }
    if (!keyword) return true
    return [
      row.feeName,
      pricingScopeText(row.pricingScope),
      pricingMarkupFeeCategoryText(row.feeCategory),
      row.enabled ? '启用' : '停用',
      row.systemRequired ? '系统项' : '普通项'
    ].some((value) => String(value || '').toLowerCase().includes(keyword))
  })
})
const pagedPricingMarkupFees = computed(() => paginateRows(filteredPricingMarkupFees.value, pricingMarkupPagination))
const pricingMarkupIdentityEditable = computed(() => (
  pricingMarkupFeeForm.feeCategory === 'BASIC_PERCENT' && !pricingMarkupFeeForm.systemRequired
))
const arrangementTargetName = computed(() => selectedArrangementNode.value?.label || '根层级')
const arrangementNodeDialogTitle = computed(() => {
  if (arrangementNodeDialogMode.value === 'edit') return '编辑工艺编排节点'
  return arrangementNodeForm.parentId ? '新增子节点' : '新增根节点'
})

const arrangementSourceText = (sourceType) => (sourceType === 'extra' ? '非工时工艺' : '工时工艺')
const arrangementSourceTagType = (sourceType) => (sourceType === 'extra' ? 'warning' : 'success')
const isArrangementTarget = (row) => row?.nodeType === 'node' && selectedArrangementNode.value?.nodeId === row.nodeId
const findArrangementNode = (nodeId, rows = processArrangementTree.value) => {
  if (nodeId == null) return null
  for (const row of rows || []) {
    if (row.nodeType === 'node' && String(row.nodeId) === String(nodeId)) return row
    const child = findArrangementNode(nodeId, row.children || [])
    if (child) return child
  }
  return null
}
const findArrangementSiblings = (row, rows = processArrangementTree.value) => {
  if (!row) return []
  if (row.parentNodeId == null) return rows || []
  const parent = findArrangementNode(row.parentNodeId)
  return parent?.children || []
}
const handleArrangementNodeExpand = (row) => {
  if (row?.nodeType !== 'node' || !row.treeKey) return
  if (!processArrangementExpandedKeys.value.includes(row.treeKey)) {
    processArrangementExpandedKeys.value = [...processArrangementExpandedKeys.value, row.treeKey]
  }
}
const handleArrangementNodeCollapse = (row) => {
  if (row?.nodeType !== 'node' || !row.treeKey) return
  const collapsedKeys = new Set([row.treeKey, ...collectProcessArrangementNodeKeys(row.children || [])])
  processArrangementExpandedKeys.value = processArrangementExpandedKeys.value.filter(key => !collapsedKeys.has(key))
}
const summarizeArrangementDeletion = (row) => {
  const summary = { removedNodeCount: 0, removedItemCount: 0 }
  const visit = (node) => {
    if (!node) return
    if (node.nodeType === 'node') summary.removedNodeCount += 1
    if (node.nodeType === 'item') summary.removedItemCount += 1
    ;(node.children || []).forEach(visit)
  }
  visit(row)
  return summary
}

const invalidateDictionaryCache = () => {
  dictionaryStore.$patch({
    loadedAt: null,
    lastIncludePricingMarkupFees: false,
    lastIncludeAllExtraFees: false
  })
}

const loadLookupDictionaries = async () => {
  const [types, pricingFees] = await Promise.all([
    listMaterialTypes(),
    loadPricingMarkupFeeRows()
  ])
  materialTypes.value = types || []
  pricingMarkupFees.value = pricingFees || []
}

const loadMaterialPage = async () => {
  const response = await pageMaterials({
    ...pageParams(materialPagination, materialKeyword)
  })
  materials.value = applyPageResult(materialPagination, response)
}

const loadProcessCategoryPage = async () => {
  const response = await pageProcessCategories({
    ...pageParams(processCategoryPagination, processCategoryKeyword)
  })
  processCategories.value = applyPageResult(processCategoryPagination, response)
}

const loadProcessDetailPage = async () => {
  const response = await pageProcessDetails({
    ...pageParams(processDetailPagination, processDetailKeyword)
  })
  processDetails.value = applyPageResult(processDetailPagination, response)
}

const loadExtraFeePage = async () => {
  const response = await pageExtraFeeItems({
    includeDisabled: true,
    ...pageParams(extraFeePagination, extraFeeKeyword)
  })
  extraFeeItems.value = applyPageResult(extraFeePagination, response)
}

const loadProcessArrangementOptions = async () => {
  unplacedArrangementOptions.value = await listUnplacedProcessArrangementOptions({
    keyword: arrangementKeyword.value.trim(),
    sourceType: arrangementSourceType.value
  }) || []
}

const loadProcessArrangement = async ({ revealNodeId = null } = {}) => {
  arrangementLoading.value = true
  try {
    const [tree] = await Promise.all([
      listProcessArrangementTree(),
      loadProcessArrangementOptions()
    ])
    processArrangementTree.value = tree || []
    processArrangementExpandedKeys.value = nextProcessArrangementExpandedKeys({
      tree: processArrangementTree.value,
      currentExpandedKeys: processArrangementExpandedKeys.value,
      revealNodeId
    })
    if (selectedArrangementNode.value) {
      selectedArrangementNode.value = findArrangementNode(selectedArrangementNode.value.nodeId)
    }
    selectedArrangementOptions.value = []
    arrangementOptionTableRef.value?.clearSelection?.()
    await nextTick()
    processArrangementTreeRef.value?.setCurrentKey?.(selectedArrangementNode.value?.treeKey ?? null, false)
  } finally {
    arrangementLoading.value = false
  }
}

const loadPageWithLoading = async (loader) => {
  loading.value = true
  try {
    await loader()
  } catch (error) {
    console.error(error)
    ElMessage.error('材料或工艺数据加载失败，请检查后端服务。')
  } finally {
    loading.value = false
  }
}

const loadAll = async () => loadPageWithLoading(async () => {
  await Promise.all([
    loadLookupDictionaries(),
    loadMaterialPage(),
    loadProcessCategoryPage(),
    loadProcessDetailPage(),
    loadExtraFeePage(),
    loadProcessArrangement()
  ])
})

const reloadAfterMutation = async () => {
  invalidateDictionaryCache()
  await loadAll()
}

const applyMesSyncRun = (syncType, run = {}) => {
  const normalized = normalizeMesSyncRun(syncType, run)
  activeMesSyncRuns[syncType] = normalized.status === 'RUNNING' ? normalized : null
  return normalized
}

const stopMesSyncPolling = (syncType) => {
  const timer = mesSyncPollingTimers.get(syncType)
  if (timer) {
    window.clearInterval(timer)
    mesSyncPollingTimers.delete(syncType)
  }
}

const pollMesSyncRun = async (syncType, syncRunId) => {
  const config = syncConfigByType[syncType]
  if (!config || !syncRunId) return
  try {
    const run = applyMesSyncRun(syncType, await config.getRun(syncRunId))
    if (run.status === 'RUNNING') return
    stopMesSyncPolling(syncType)
    if (run.status === 'SUCCESS') {
      ElMessage.success(formatMesSyncResultMessage(run))
      await reloadAfterMutation()
      return
    }
    if (run.status === 'FAILED') {
      ElMessage.error(formatMesSyncResultMessage(run) || config.fallbackErrorMessage)
    }
  } catch (error) {
    console.error(error)
    stopMesSyncPolling(syncType)
    activeMesSyncRuns[syncType] = null
    ElMessage.error(syncConfigByType[syncType]?.fallbackErrorMessage || 'MES 同步状态查询失败')
  }
}

const startMesSyncPolling = (syncType, syncRunId) => {
  if (!syncRunId) return
  stopMesSyncPolling(syncType)
  mesSyncPollingTimers.set(syncType, window.setInterval(() => {
    pollMesSyncRun(syncType, syncRunId)
  }, MES_SYNC_POLL_INTERVAL_MS))
  pollMesSyncRun(syncType, syncRunId)
}

const stopAllMesSyncPolling = () => {
  Object.keys(activeMesSyncRuns).forEach((syncType) => stopMesSyncPolling(syncType))
}

const loadLatestMesSyncRun = async (syncType) => {
  const config = syncConfigByType[syncType]
  if (!config) return
  try {
    const run = normalizeMesSyncRun(syncType, await config.getLatestRun())
    if (run.status === 'RUNNING' && run.syncRunId) {
      activeMesSyncRuns[syncType] = run
      startMesSyncPolling(syncType, run.syncRunId)
      return
    }
    activeMesSyncRuns[syncType] = null
    stopMesSyncPolling(syncType)
  } catch (error) {
    console.error(error)
  }
}

const loadLatestMesSyncRuns = () => Promise.all([
  loadLatestMesSyncRun('MES_MATERIAL'),
  loadLatestMesSyncRun('MES_PROCESS_TYPE')
])

const handleMesSyncSubmitResult = async (syncType, response = {}) => {
  const config = syncConfigByType[syncType]
  const run = applyMesSyncRun(syncType, {
    ...response,
    status: response.status || (response.syncRunId ? 'RUNNING' : 'NONE')
  })
  const message = response.message || config?.fallbackSubmitMessage || 'MES 同步已提交'
  if (response.alreadyRunning) {
    ElMessage.warning(message)
  } else if (response.success === false) {
    ElMessage.error(message)
  } else if (response.submitted) {
    ElMessage.success(message)
  } else {
    ElMessage.success(message)
  }
  if (run.status === 'RUNNING' && run.syncRunId) {
    if (syncType === 'MES_MATERIAL') {
      startMesSyncPolling('MES_MATERIAL', run.syncRunId)
    } else if (syncType === 'MES_PROCESS_TYPE') {
      startMesSyncPolling('MES_PROCESS_TYPE', run.syncRunId)
    } else {
      startMesSyncPolling(syncType, run.syncRunId)
    }
    return
  }
  if (run.status === 'SUCCESS' || !run.syncRunId) {
    await reloadAfterMutation()
  }
}

const openMaterialDialog = (row) => {
  Object.assign(materialForm, createEmptyMaterial(), row || {})
  materialForm.priceSpecs = normalizePriceSpecs(row?.priceSpecs)
  materialForm.enabled = materialForm.enabled !== false
  materialDialogVisible.value = true
}

const activeMaterialSpecDraft = computed(() => findPriceSpec(materialForm.priceSpecs, materialSpecDialogCode.value))

const openMaterialFormSpecDialog = (specCode) => {
  materialSpecDialogCode.value = specCode
  materialSpecDialogVisible.value = true
}

const openRowMaterialSpecDialog = (row, specCode) => {
  openMaterialDialog(row)
  openMaterialFormSpecDialog(specCode)
}

const updateActiveMaterialSpecDraft = (updatedSpec) => {
  materialForm.priceSpecs = normalizePriceSpecs(materialForm.priceSpecs).map(spec => (
    spec.specCode === updatedSpec.specCode ? updatedSpec : spec
  ))
}

const openProcessDetailDialog = (row) => {
  Object.assign(processDetailForm, createEmptyProcessDetail(), row || {})
  processDetailForm.processCode = row?.processCode || ''
  processDetailForm.hourlyRate = row?.detailHourlyRate ?? row?.hourlyRate ?? row?.effectiveHourlyRate ?? null
  processDetailForm.enabled = processDetailForm.enabled !== false
  processDetailDialogVisible.value = true
}

const selectArrangementRoot = () => {
  selectedArrangementNode.value = null
}

const handleArrangementTreeClick = (row) => {
  if (row?.nodeType === 'node') {
    selectedArrangementNode.value = row
  }
}

const handleArrangementOptionSelection = (rows) => {
  selectedArrangementOptions.value = rows || []
}

const nextArrangementSortNo = (rows = []) => rows.reduce((max, row) => Math.max(max, Number(row?.sortNo || 0)), 0) + 1

const openArrangementNodeDialog = (row, mode = 'root') => {
  Object.assign(arrangementNodeForm, createEmptyArrangementNode())
  arrangementNodeDialogMode.value = mode
  if (mode === 'edit' && row?.nodeType === 'node') {
    arrangementNodeForm.id = row.nodeId
    arrangementNodeForm.parentId = row.parentNodeId ?? null
    arrangementNodeForm.nodeName = row.nodeName || row.label || ''
    arrangementNodeForm.sortNo = Number(row.sortNo || 0)
    arrangementNodeParentName.value = findArrangementNode(row.parentNodeId)?.label || '根层级'
  } else if (mode === 'child' && row?.nodeType === 'node') {
    arrangementNodeForm.parentId = row.nodeId
    arrangementNodeForm.sortNo = nextArrangementSortNo(row.children || [])
    arrangementNodeParentName.value = row.label || '根层级'
  } else {
    arrangementNodeDialogMode.value = 'root'
    arrangementNodeForm.parentId = null
    arrangementNodeForm.sortNo = nextArrangementSortNo(processArrangementTree.value)
    arrangementNodeParentName.value = '根层级'
  }
  arrangementNodeDialogVisible.value = true
}

const saveArrangementNode = async () => {
  if (!arrangementNodeForm.nodeName?.trim()) return ElMessage.warning('请填写节点名称。')
  return runAction(dictActionKeys.saveArrangementNode, async () => {
    try {
      const payload = {
        parentId: arrangementNodeForm.parentId,
        nodeName: arrangementNodeForm.nodeName.trim(),
        sortNo: Number(arrangementNodeForm.sortNo || 0)
      }
      const revealNodeId = arrangementNodeForm.id ? null : arrangementNodeForm.parentId
      if (arrangementNodeForm.id) {
        await updateProcessArrangementNode(arrangementNodeForm.id, payload)
        ElMessage.success('工艺编排节点已更新')
      } else {
        await createProcessArrangementNode(payload)
        ElMessage.success('工艺编排节点已新增')
      }
      arrangementNodeDialogVisible.value = false
      await loadProcessArrangement({ revealNodeId })
    } catch (error) {
      console.error(error)
      ElMessage.error(requestErrorMessage(error, '工艺编排节点保存失败。'))
    }
  })
}

const moveArrangementNode = async (row, direction) => {
  if (!row?.nodeId) return
  const siblings = findArrangementSiblings(row)
  const index = siblings.findIndex(item => item.treeKey === row.treeKey)
  const adjacent = siblings[index + direction]
  if (!adjacent) return ElMessage.info(direction < 0 ? '已经在当前层级最上方。' : '已经在当前层级最下方。')
  const nextSortNo = Number(adjacent.sortNo || 0) + (direction < 0 ? -1 : 1)
  return runAction(dictActionKeys.moveArrangementNode(row.nodeId), async () => {
    try {
      await updateProcessArrangementNode(row.nodeId, {
        parentId: row.parentNodeId ?? null,
        nodeName: row.nodeName || row.label,
        sortNo: nextSortNo
      })
      await loadProcessArrangement()
    } catch (error) {
      console.error(error)
      ElMessage.error(requestErrorMessage(error, '工艺编排节点移动失败。'))
    }
  })
}

const moveArrangementItem = async (row, direction) => {
  if (!row?.itemId) return
  const siblings = findArrangementSiblings(row)
  const index = siblings.findIndex(item => item.treeKey === row.treeKey)
  const adjacent = siblings[index + direction]
  if (!adjacent) return ElMessage.info(direction < 0 ? '已经在当前层级最上方。' : '已经在当前层级最下方。')
  const nextSortNo = Number(adjacent.sortNo || 0) + (direction < 0 ? -1 : 1)
  return runAction(dictActionKeys.moveArrangementItem(row.itemId), async () => {
    try {
      await moveProcessArrangementItem(row.itemId, {
        parentNodeId: row.parentNodeId ?? null,
        sortNo: nextSortNo
      })
      await loadProcessArrangement()
    } catch (error) {
      console.error(error)
      ElMessage.error(requestErrorMessage(error, '工艺编排工艺移动失败。'))
    }
  })
}

const placeSelectedArrangementItems = async () => {
  if (!selectedArrangementOptions.value.length) return ElMessage.warning('请选择要放入的工艺。')
  return runAction(dictActionKeys.placeArrangementItems, async () => {
    try {
      await placeProcessArrangementItems({
        parentNodeId: selectedArrangementNode.value?.nodeId ?? null,
        items: selectedArrangementOptions.value.map(row => ({
          sourceType: row.sourceType,
          processDetailId: row.processDetailId,
          feeItemId: row.feeItemId
        }))
      })
      ElMessage.success('已放入工艺编排')
      await loadProcessArrangement({ revealNodeId: selectedArrangementNode.value?.nodeId ?? null })
    } catch (error) {
      console.error(error)
      ElMessage.error(requestErrorMessage(error, '工艺放入失败。'))
    }
  })
}

const deleteArrangementNode = async (row) => {
  if (!row?.nodeId) return
  Object.assign(deleteSummary, summarizeArrangementDeletion(row))
  return runAction(dictActionKeys.deleteArrangementNode(row.nodeId), async () => {
    await ElMessageBox.confirm(
      `确认级联移除工艺编排节点「${row.label}」？将移除 ${deleteSummary.removedNodeCount} 个节点、${deleteSummary.removedItemCount} 个工艺挂载，基础工艺不会被删除。`,
      '级联移除确认',
      { type: 'warning' }
    )
    try {
      const response = await deleteProcessArrangementNode(row.nodeId)
      deleteSummary.removedNodeCount = response?.removedNodeCount ?? deleteSummary.removedNodeCount
      deleteSummary.removedItemCount = response?.removedItemCount ?? deleteSummary.removedItemCount
      if (selectedArrangementNode.value?.nodeId === row.nodeId) selectedArrangementNode.value = null
      ElMessage.success('工艺编排节点已级联移除')
      await loadProcessArrangement()
    } catch (error) {
      console.error(error)
      ElMessage.error(requestErrorMessage(error, '工艺编排节点删除失败。'))
    }
  })
}

const removeArrangementItem = async (row) => {
  if (!row?.itemId) return
  return runAction(dictActionKeys.removeArrangementItem(row.itemId), async () => {
    await ElMessageBox.confirm(`确认从工艺编排中移除「${row.label}」？基础工艺不会被删除。`, '移除确认', { type: 'warning' })
    try {
      await removeProcessArrangementItem(row.itemId)
      ElMessage.success('工艺挂载已移除')
      await loadProcessArrangement()
    } catch (error) {
      console.error(error)
      ElMessage.error(requestErrorMessage(error, '工艺挂载移除失败。'))
    }
  })
}

const openPricingMarkupFeeDialog = (row) => {
  Object.assign(pricingMarkupFeeForm, createEmptyPricingMarkupFee(), row || {})
  pricingMarkupFeeForm.pricingScope = row?.pricingScope || pricingMarkupScope.value
  pricingMarkupFeeForm.feeCategory = row?.feeCategory || pricingMarkupFeeCategory.value
  pricingMarkupFeeForm.enabled = pricingMarkupFeeForm.enabled !== false
  pricingMarkupFeeForm.systemRequired = pricingMarkupFeeForm.systemRequired === true
  pricingMarkupFeeDialogVisible.value = true
}

const openExtraFeeDialog = (row) => {
  Object.assign(extraFeeForm, createEmptyExtraFee(), row || {})
  extraFeeOriginalCode.value = row?.feeCode || ''
  extraFeeDialogVisible.value = true
}

const syncMesMaterials = async () => {
  return runAction(dictActionKeys.syncMesMaterials, async () => {
    await ElMessageBox.confirm(`${mesMaterialSyncOwnershipMessage}。确认从 MES 同步材料主数据？`, '同步确认', { type: 'warning' })
    try {
      const response = await syncMesMaterialsRequest()
      await handleMesSyncSubmitResult('MES_MATERIAL', response)
    } catch (error) {
      console.error(error)
      ElMessage.error('MES 材料同步失败。')
    }
  })
}

const syncMesProcessTypes = async () => {
  return runAction(dictActionKeys.syncMesProcessTypes, async () => {
    await ElMessageBox.confirm(`${mesProcessSyncOwnershipMessage}。确认从 MES 同步工艺主数据？`, '同步确认', { type: 'warning' })
    try {
      const response = await syncMesProcessTypesRequest()
      await handleMesSyncSubmitResult('MES_PROCESS_TYPE', response)
    } catch (error) {
      console.error(error)
      ElMessage.error('MES 工艺同步失败。')
    }
  })
}

const openMesMaterialScheduleDialog = () => openMesScheduleDialog('MES_MATERIAL')
const openMesProcessScheduleDialog = () => openMesScheduleDialog('MES_PROCESS_TYPE')

const openMesScheduleDialog = async (syncType) => {
  if (scheduleLoading.value) return
  scheduleTarget.value = syncType
  scheduleDialogVisible.value = true
  scheduleLoading.value = true
  try {
    const response = await activeScheduleConfig.value.load()
    applyMesScheduleSetting(response)
  } catch (error) {
    console.error(error)
    ElMessage.error(requestErrorMessage(error, activeScheduleConfig.value.loadError))
  } finally {
    scheduleLoading.value = false
  }
}

const saveMesScheduleSetting = async () => {
  return runAction(dictActionKeys.saveMesSchedule, async () => {
    try {
      const payload = {
        enabled: !!scheduleForm.enabled,
        scheduleMode: scheduleForm.scheduleMode,
        dailyRunTime: normalizeDailyRunTime(scheduleForm.dailyRunTime),
        ...(scheduleForm.scheduleMode === 'FIXED_DELAY'
          ? {
              startupDelayMs: Number(scheduleForm.startupDelaySeconds || 0) * 1000,
              fixedDelayMs: Number(scheduleForm.fixedDelayMinutes || 1) * 60 * 1000
            }
          : {})
      }
      const response = await activeScheduleConfig.value.save(payload)
      applyMesScheduleSetting(response)
      ElMessage.success(activeScheduleConfig.value.saveSuccess)
      scheduleDialogVisible.value = false
    } catch (error) {
      console.error(error)
      ElMessage.error(requestErrorMessage(error, activeScheduleConfig.value.saveError))
    }
  })
}

const applyMesScheduleSetting = (setting = {}) => {
  scheduleForm.enabled = !!setting.enabled
  scheduleForm.scheduleMode = setting.scheduleMode || 'FIXED_DELAY'
  scheduleForm.dailyRunTime = normalizeDailyRunTime(setting.dailyRunTime || '02:00:00')
  scheduleForm.startupDelaySeconds = Math.round((setting.startupDelayMs ?? 30000) / 1000)
  scheduleForm.fixedDelayMinutes = Math.max(1, Math.round((setting.fixedDelayMs ?? 1800000) / 60000))
  scheduleForm.lastScheduledRunAt = setting.lastScheduledRunAt || ''
  scheduleForm.lastScheduledSyncRunId = setting.lastScheduledSyncRunId || null
  scheduleForm.lastScheduledStatus = setting.lastScheduledStatus || 'NEVER'
  scheduleForm.lastScheduledMessage = setting.lastScheduledMessage || ''
  scheduleForm.fallback = !!setting.fallback
}

const saveMaterial = async () => {
  if (!materialForm.materialCode) return ElMessage.warning('请填写材料编码。')
  if (!materialForm.materialName) return ElMessage.warning('请填写材料名称。')
  if (!materialForm.materialTypeId) return ElMessage.warning('请选择材料类型。')
  return runAction(dictActionKeys.saveMaterialDialog, async () => {
    try {
      const payload = { ...materialForm, priceSpecs: normalizePriceSpecs(materialForm.priceSpecs) }
      const response = await saveMaterialRequest(payload)
      ElMessage.success(response.message || '保存成功')
      materialDialogVisible.value = false
      await reloadAfterMutation()
    } catch (error) {
      console.error(error)
      ElMessage.error('材料保存失败。')
    }
  })
}

const saveProcessDetail = async () => {
  processDetailForm.processCode = String(processDetailForm.processCode || '').trim().toUpperCase()
  if (!processDetailForm.processCode) return ElMessage.warning('请填写工艺编码。')
  if (!processDetailForm.detailName) return ElMessage.warning('请填写具体工艺名称。')
  if (processDetailForm.hourlyRate === '' || processDetailForm.hourlyRate == null) return ElMessage.warning('请填写小时价格。')
  return runAction(dictActionKeys.saveProcessDetailDialog, async () => {
    try {
      const hourlyRate = Number(processDetailForm.hourlyRate)
      const response = await saveProcessDetailRequest({ ...processDetailForm, hourlyRate })
      ElMessage.success(response.message || '保存成功')
      processDetailDialogVisible.value = false
      await reloadAfterMutation()
    } catch (error) {
      console.error(error)
      ElMessage.error('具体工艺保存失败。')
    }
  })
}

const savePricingMarkupFee = async () => {
  if (!pricingMarkupFeeForm.feeName) return ElMessage.warning('请填写费用名称。')
  return runAction(dictActionKeys.savePricingMarkupFeeDialog, async () => {
    try {
      const payload = {
        pricingScope: pricingMarkupFeeForm.pricingScope,
        feeCategory: pricingMarkupFeeForm.feeCategory,
        feeName: pricingMarkupFeeForm.feeName,
        ratePercent: Number(pricingMarkupFeeForm.ratePercent || 0),
        sortNo: Number(pricingMarkupFeeForm.sortNo || 0),
        enabled: pricingMarkupFeeForm.enabled !== false
      }
      const response = pricingMarkupFeeForm.id
        ? await updatePricingMarkupFee(pricingMarkupFeeForm.id, payload)
        : await createPricingMarkupFee(payload)
      ElMessage.success(response.message || '保存成功')
      pricingMarkupFeeDialogVisible.value = false
      await reloadAfterMutation()
    } catch (error) {
      console.error(error)
      ElMessage.error(requestErrorMessage(error, '百分比费用保存失败。'))
    }
  })
}

const saveExtraFee = async () => {
  if (!extraFeeForm.feeCode) return ElMessage.warning('请填写工艺编码。')
  if (!extraFeeForm.feeName) return ElMessage.warning('请填写工艺名称。')
  return runAction(dictActionKeys.saveExtraFeeDialog, async () => {
    try {
      const response = await saveExtraFeeItem(extraFeeForm)
      ElMessage.success(response.message || '保存成功')
      extraFeeDialogVisible.value = false
      await reloadAfterMutation()
    } catch (error) {
      console.error(error)
      ElMessage.error('非工时费用工艺保存失败。')
    }
  })
}

const deleteProcessDetail = async (row) => {
  return runAction(dictActionKeys.deleteProcessDetail(row.id), async () => {
    await ElMessageBox.confirm(`确认删除具体工艺「${row.detailName}」？删除后推荐次数也会跟随 QPO 工艺编码重新累计。`, '删除确认', { type: 'warning' })
    await deleteProcessDetailRequest(row.id)
    ElMessage.success('具体工艺已删除')
    await reloadAfterMutation()
  })
}

const deletePricingMarkupFee = async (row) => {
  if (row.systemRequired) return ElMessage.warning('系统项不可删除，只能修改百分率或启停。')
  return runAction(dictActionKeys.deletePricingMarkupFee(row.id), async () => {
    await ElMessageBox.confirm(`确认删除百分比费用「${row.feeName}」？`, '删除确认', { type: 'warning' })
    await deletePricingMarkupFeeRequest(row.id)
    ElMessage.success('百分比费用已删除')
    await reloadAfterMutation()
  })
}

const deleteExtraFee = async (row) => {
  return runAction(dictActionKeys.deleteExtraFee(row.id), async () => {
    await ElMessageBox.confirm(`确认删除非工时费用工艺「${row.feeName}」？`, '删除确认', { type: 'warning' })
    await deleteExtraFeeItem(row.id)
    ElMessage.success('非工时费用工艺已删除')
    await reloadAfterMutation()
  })
}

watch(materialKeyword, () => {
  resetPageAndLoad(materialPagination, loadMaterialPage)
})
watch(processCategoryKeyword, () => {
  resetPageAndLoad(processCategoryPagination, loadProcessCategoryPage)
})
watch(processDetailKeyword, () => {
  resetPageAndLoad(processDetailPagination, loadProcessDetailPage)
})
watch(extraFeeKeyword, () => {
  resetPageAndLoad(extraFeePagination, loadExtraFeePage)
})
watch([arrangementKeyword, arrangementSourceType], async () => {
  arrangementLoading.value = true
  try {
    await loadProcessArrangementOptions()
    selectedArrangementOptions.value = []
    arrangementOptionTableRef.value?.clearSelection?.()
  } catch (error) {
    console.error(error)
    ElMessage.error('未编排工艺加载失败。')
  } finally {
    arrangementLoading.value = false
  }
})
watch(pricingMarkupKeyword, resetPricingMarkupFeePage)
watch(pricingMarkupScope, resetPricingMarkupFeePage)
watch(pricingMarkupFeeCategory, resetPricingMarkupFeePage)
watch(
  () => [materialPagination.page, materialPagination.pageSize],
  () => loadPageWithLoading(loadMaterialPage)
)
watch(
  () => [processCategoryPagination.page, processCategoryPagination.pageSize],
  () => loadPageWithLoading(loadProcessCategoryPage)
)
watch(
  () => [processDetailPagination.page, processDetailPagination.pageSize],
  () => loadPageWithLoading(loadProcessDetailPage)
)
watch(
  () => [extraFeePagination.page, extraFeePagination.pageSize],
  () => loadPageWithLoading(loadExtraFeePage)
)
watch(activeTab, (tab) => {
  if (tab === 'materials') loadLatestMesSyncRun('MES_MATERIAL')
  if (tab === 'processCategories') loadLatestMesSyncRun('MES_PROCESS_TYPE')
  if (tab === 'processArrangement') loadProcessArrangement()
})

onMounted(loadAll)
onMounted(loadLatestMesSyncRuns)
onBeforeUnmount(stopAllMesSyncPolling)
</script>

<style scoped>
.material-toolbar {
  justify-content: flex-end;
  flex-wrap: wrap;
}

.material-table-surface {
  overflow: hidden;
}

.material-filter-bar {
  display: flex;
  justify-content: flex-end;
  margin-bottom: 12px;
}

.material-filter-bar .el-input {
  max-width: 360px;
  width: 100%;
}

.pricing-markup-panel {
  display: grid;
  min-width: 0;
  gap: 12px;
}

.pricing-markup-controls {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  flex-wrap: wrap;
}

.pricing-markup-control-group {
  display: flex;
  align-items: center;
  gap: 8px;
}

.pricing-markup-control-label {
  color: #606266;
  font-size: 13px;
  white-space: nowrap;
}

.pricing-markup-controls .el-input {
  max-width: 340px;
  width: 100%;
}

.material-type-cell {
  display: flex;
  min-width: 0;
  flex-direction: column;
  gap: 2px;
}

.material-type-cell small {
  color: #909399;
  font-size: 12px;
  line-height: 1.2;
}

.material-spec-cell {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 8px;
  min-width: 0;
}

.material-spec-cell span {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.material-spec-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 10px;
  width: 100%;
}

.material-spec-card {
  display: grid;
  gap: 6px;
  min-width: 0;
  padding: 10px;
  border: 1px solid var(--erp-border-soft);
  border-radius: 8px;
  background: var(--erp-surface-muted);
}

.material-spec-card span {
  color: var(--erp-text-secondary);
  font-size: 12px;
}

.material-spec-card strong {
  color: var(--erp-text);
  font-size: 14px;
}

.process-master-grid {
  display: grid;
  min-width: 0;
  gap: 12px;
}

.process-arrangement-workbench {
  display: grid;
  grid-template-columns: minmax(360px, 1fr) minmax(420px, 1.2fr);
  gap: 12px;
  min-width: 0;
}

.process-arrangement-tree-panel,
.process-arrangement-library-panel {
  min-width: 0;
  padding: 12px;
  border: 1px solid var(--erp-border-soft);
  border-radius: 8px;
  background: var(--erp-surface);
}

.process-arrangement-panel-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 12px;
}

.process-arrangement-panel-header > div {
  display: grid;
  gap: 4px;
  min-width: 0;
}

.process-arrangement-panel-header strong {
  color: var(--erp-text);
  font-size: 14px;
}

.process-arrangement-panel-header span {
  color: var(--erp-text-secondary);
  font-size: 12px;
}

.process-arrangement-tree-panel :deep(.el-tree-node__content) {
  height: auto;
  min-height: 36px;
  padding: 4px 0;
}

.process-arrangement-tree-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  width: 100%;
  min-width: 0;
  padding: 4px 8px;
  border-radius: 6px;
}

.process-arrangement-tree-row.is-selected {
  background: var(--erp-surface-soft);
}

.process-arrangement-node-main {
  display: flex;
  align-items: center;
  gap: 6px;
  min-width: 0;
}

.process-arrangement-node-label {
  min-width: 0;
  overflow: hidden;
  color: var(--erp-text);
  text-overflow: ellipsis;
  white-space: nowrap;
}

.process-arrangement-node-actions {
  display: flex;
  flex: 0 0 auto;
  align-items: center;
  gap: 2px;
}

.process-arrangement-library-filters {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 12px;
}

.process-arrangement-library-filters .el-input {
  max-width: 320px;
}

.process-dialog-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  column-gap: 16px;
}

.process-dialog-wide {
  grid-column: 1 / -1;
}

.unit-input {
  display: flex;
  align-items: center;
  width: 100%;
  gap: 8px;
}

.unit-input-control {
  flex: 1;
  min-width: 0;
}

.unit-label {
  flex: 0 0 52px;
  color: #606266;
  font-size: 13px;
  text-align: left;
  white-space: nowrap;
}

.unit-label.wide {
  flex-basis: 64px;
}

.form-helper-text {
  grid-column: 1 / -1;
  margin-top: 4px;
  color: #909399;
  font-size: 12px;
  line-height: 1.4;
}

.dialog-number-input,
.dialog-select {
  width: 100%;
}

.mes-schedule-panel {
  display: flex;
  flex-direction: column;
  gap: var(--erp-space-4);
}

.mes-schedule-alert {
  margin: 0;
}

.mes-schedule-form {
  max-width: 100%;
}

.mes-schedule-form :deep(.el-input-number) {
  width: 180px;
  max-width: calc(100vw - 220px);
}

.mes-schedule-unit {
  margin-left: var(--erp-space-2);
  color: var(--erp-text-secondary);
}

.mes-schedule-status {
  width: 100%;
}

.mes-schedule-footer {
  display: flex;
  justify-content: flex-end;
  gap: var(--erp-space-2);
}

@media (max-width: 900px) {
  .material-toolbar {
    justify-content: flex-start;
  }

  .material-filter-bar {
    justify-content: flex-start;
  }

  .pricing-markup-controls,
  .pricing-markup-control-group {
    align-items: flex-start;
    flex-direction: column;
  }

  .pricing-markup-controls .el-input {
    max-width: 100%;
  }

  .process-arrangement-workbench {
    grid-template-columns: minmax(0, 1fr);
  }

  .process-arrangement-panel-header,
  .process-arrangement-library-filters {
    align-items: stretch;
    flex-direction: column;
  }

  .process-arrangement-library-filters .el-input {
    max-width: 100%;
  }

  .process-dialog-grid {
    display: block;
  }

  .mes-schedule-form :deep(.el-form-item) {
    display: block;
  }

  .mes-schedule-form :deep(.el-form-item__label) {
    width: auto !important;
    justify-content: flex-start;
  }

  .mes-schedule-form :deep(.el-form-item__content) {
    margin-left: 0 !important;
  }

  .mes-schedule-form :deep(.el-input-number) {
    width: calc(100vw - 120px);
    max-width: 100%;
  }

  .mes-schedule-footer {
    flex-direction: column-reverse;
  }

  .mes-schedule-footer .el-button {
    width: 100%;
    margin-left: 0;
  }
}
</style>
