<template>
  <div class="erp-page pricing-workbench-page">
    <header class="erp-page-header pricing-page-header">
      <div>
        <h1 class="erp-page-title">逐件工艺路线与核价中心</h1>
        <p class="erp-page-description">
          按零件逐项核算材料、工艺、费用与报价，完成后提交市场报价。
        </p>
      </div>
      <div class="erp-page-actions pricing-page-actions">
        <el-tag type="success" size="large" v-if="rfqId">询价单 ID: {{ rfqId }}</el-tag>
        <el-tag size="large" v-if="quoteData?.orderInfo?.status">订单状态：{{ quoteData.orderInfo.status }}</el-tag>
      </div>
    </header>

    <section class="erp-surface pricing-context-card">
      <div class="pricing-context-copy">
        <span class="pricing-context-kicker">当前核价任务</span>
        <strong>RFQ {{ rfqId || '-' }}</strong>
        <p>继承工程全局配置后，在这里完成每个零件的材料与工艺核价。</p>
      </div>
      <div class="pricing-metric-grid">
        <div class="pricing-metric">
          <span class="pricing-metric-label">零件总数</span>
          <strong class="pricing-metric-value">{{ quoteData?.itemList?.length || 0 }}</strong>
        </div>
        <div class="pricing-metric">
          <span class="pricing-metric-label">已核价</span>
          <strong class="pricing-metric-value">
            {{ quoteData?.itemList?.filter(item => item.finalUnitPrice != null).length || 0 }}
          </strong>
        </div>
        <div class="pricing-metric">
          <span class="pricing-metric-label">重复件</span>
          <strong class="pricing-metric-value">
            {{ quoteData?.itemList?.filter(item => repeatItemMap.has(itemIdentityKey(item))).length || 0 }}
          </strong>
        </div>
      </div>
    </section>

    <section class="erp-toolbar pricing-toolbar">
      <div class="erp-toolbar-group pricing-toolbar-copy">
        <span class="pricing-toolbar-title">逐件核价清单</span>
        <small>先处理有图纸零件，再生成报价单并提交。</small>
      </div>
      <div class="erp-toolbar-group pricing-toolbar-actions">
        <el-button @click="returnToGlobalSetup">返回修改全局设定</el-button>
        <el-button type="success" plain :disabled="!canExportQuote" @click="exportQuoteSheet">
          生成报价单
        </el-button>
        <el-button v-permission="'engineer:pricing:refresh'" type="warning" plain :disabled="!canRefreshQuotes" :loading="refreshLoading" @click="refreshOrderQuotes">
          一键刷新核价
        </el-button>
        <el-button v-permission="'engineer:pricing:complete-order'" type="primary" :disabled="!canCompleteQuote" :loading="completeLoading" @click="completeOrderQuote">
          完成报价提交
        </el-button>
      </div>
    </section>

    <section class="erp-table-surface pricing-table-surface">
      <div v-if="!quoteData" class="erp-empty-state pricing-empty-state" v-loading="loading">
        <el-empty description="正在拉取市场部数据..." />
      </div>

      <!-- 数据大表格 -->
      <div v-else class="erp-table-body pricing-table-body">
        <el-table
          ref="pricingTableRef"
          :data="pagedPricingItems"
          row-key="id"
          border
          stripe
          class="pricing-table"
          height="450"
        >
          <el-table-column prop="partNo" label="图号" width="140" fixed show-overflow-tooltip />
          <el-table-column prop="version" label="版本" width="80" align="center" />
          <el-table-column prop="description" label="名称/描述" min-width="150" show-overflow-tooltip />
          <el-table-column prop="qty" label="数量" width="70" align="center" />
          <el-table-column label="类型" width="90" align="center">
            <template #default="scope">
              <el-tag :type="isAssemblyItem(scope.row) ? 'warning' : 'info'" size="small">
                {{ isAssemblyItem(scope.row) ? '组合件' : '普通件' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="材料毛重(kg)" width="120" align="right">
            <template #default="scope">{{ formatWeight(scope.row.materialGrossWeight) }}</template>
          </el-table-column>
          <el-table-column label="零件净重(kg)" width="120" align="right">
            <template #default="scope">{{ formatWeight(scope.row.partNetWeight) }}</template>
          </el-table-column>
          <el-table-column label="重复" width="90" align="center">
            <template #default="scope">
              <el-tag v-if="repeatItemMap.has(itemIdentityKey(scope.row))" type="success" size="small">重复</el-tag>
              <el-tag v-else type="info" size="small">新件</el-tag>
            </template>
          </el-table-column>

          <el-table-column label="图纸" width="90" align="center">
            <template #default="scope">
              <div v-if="scope.row.drawingUrl" class="erp-table-actions pricing-row-actions">
                <el-button
                  type="success"
                  size="small"
                  plain
                  @click="openPdfPreview(scope.row.drawingUrl, scope.row.partNo)"
                >
                  图纸
                </el-button>
              </div>
              <el-tag v-else type="info" size="small">无图</el-tag>
            </template>
          </el-table-column>

          <el-table-column label="核算总成本" width="110" align="right">
            <template #default="scope">
              <span v-if="scope.row.baseCost">¥ {{ scope.row.baseCost.toFixed(2) }}</span><span v-else class="muted-price">待核算</span>
            </template>
          </el-table-column>
          <el-table-column label="含税单价" width="110" align="right">
            <template #default="scope"><span v-if="scope.row.finalUnitPrice != null" class="success-price">¥ {{ scope.row.finalUnitPrice.toFixed(2) }}</span></template>
          </el-table-column>
          <el-table-column label="单项合计" width="110" align="right">
            <template #default="scope"><span v-if="scope.row.finalTotalPrice != null" class="danger-price">¥ {{ scope.row.finalTotalPrice.toFixed(2) }}</span></template>
          </el-table-column>
          <el-table-column prop="status" label="状态" width="100" align="center" />
          <el-table-column label="操作" width="120" align="center" fixed="right">
            <template #default="scope">
              <div class="erp-table-actions pricing-row-actions">
                <el-button v-permission="'engineer:pricing:save-item'"
                  v-if="scope.row.drawingUrl"
                  type="primary"
                  size="small"
                  @click="openQuoteDialog(scope.row)"
                >
                  去核价
                </el-button>
                <el-tag v-else type="warning" size="small">无图不核价</el-tag>
              </div>
            </template>
          </el-table-column>
        </el-table>
        <div v-if="quoteData.itemList?.length" class="erp-pagination">
          <el-pagination
            v-model:current-page="pricingListPagination.page"
            v-model:page-size="pricingListPagination.pageSize"
            :page-sizes="CLIENT_PAGE_SIZES"
            :pager-count="7"
            :total="quoteData.itemList.length"
            layout="total, sizes, prev, pager, next, jumper"
            background
            @size-change="pricingListPagination.page = 1"
          />
        </div>
      </div>
    </section>

    <!-- 工程师核价控制台 -->
    <el-dialog
      v-model="dialogVisible"
      title="工程师精准核价控制台"
      width="95vw"
      top="3vh"
      class="pricing-workbench-dialog"
      :close-on-click-modal="false"
    >
      <div class="pricing-workbench">
        <div class="pricing-form-pane">
          <!-- 弹窗顶部状态条：左边是信息，右边是图纸操作 -->
          <div class="dialog-header-bar">
            <el-alert
              :title="`当前核价：${currentRow?.partNo} / ${currentRow?.version || '-'} - ${currentRow?.description} (需生产: ${currentRow?.qty} 件)`"
              type="success"
              show-icon
              :closable="false"
              class="current-item-alert"
            />
            <div v-if="currentRow?.drawingUrl" class="erp-table-actions dialog-drawing-actions">
              <el-button type="warning" plain @click="openPdfPreview(currentRow.drawingUrl, currentRow.partNo)">
                弹窗预览
              </el-button>
              <el-button type="primary" plain @click="openDrawingInNewWindow(currentRow.drawingUrl)">
                新窗口打开图纸
              </el-button>
            </div>
          </div>

          <section class="erp-dialog-section pricing-workbench-section pricing-mode-section">
            <div class="form-section-title pricing-section-title">核价模式</div>
            <el-form label-width="100px" class="pricing-section-form quote-mode-form">
              <el-form-item label="核价类型">
                <el-radio-group v-model="quoteMode">
                  <el-radio-button label="NORMAL">普通零件</el-radio-button>
                  <el-radio-button label="ASSEMBLY">组合件</el-radio-button>
                </el-radio-group>
              </el-form-item>
            </el-form>
          </section>

          <template v-if="quoteMode === 'NORMAL'">
          <section class="erp-dialog-section pricing-workbench-section pricing-material-section">
            <!-- 模块A：材料 -->
            <div class="form-section-title pricing-section-title">模块 A：材料成本核算（已继承全局设定）</div>
            <el-form label-width="100px" class="pricing-section-form">
              <el-row :gutter="20">
                <el-col :span="12">
                  <el-form-item label="材质选择">
                    <el-button class="picker-trigger full-width" :title="selectedMaterialLabel" @click="materialPickerVisible = true">
                      <span class="picker-trigger-text" :title="selectedMaterialLabel">{{ selectedMaterialLabel }}</span>
                      <span class="picker-trigger-action">选择</span>
                    </el-button>
                  </el-form-item>
                </el-col>
                <el-col :span="12">
                  <el-form-item label="毛坯形态">
                    <el-radio-group v-model="matForm.shape">
                      <el-radio label="板材">板/块</el-radio><el-radio label="棒材">棒材</el-radio><el-radio label="管材">管材</el-radio>
                    </el-radio-group>
                  </el-form-item>
                </el-col>
              </el-row>

              <!-- 尺寸填写 -->
              <el-row :gutter="20" class="dimension-row">
                <el-col :span="12" v-if="matForm.shape === '板材'"><el-form-item label="长(m)"><el-input-number v-model="matForm.L" :min="0" :step="0.01" :precision="4" placeholder="请输入" controls-position="right" class="full-width" /></el-form-item></el-col>
                <el-col :span="12" v-if="matForm.shape === '板材'"><el-form-item label="宽(m)"><el-input-number v-model="matForm.W" :min="0" :step="0.01" :precision="4" placeholder="请输入" controls-position="right" class="full-width" /></el-form-item></el-col>
                <el-col :span="12" v-if="matForm.shape === '板材'"><el-form-item label="厚(m)"><el-input-number v-model="matForm.T" :min="0" :step="0.01" :precision="4" placeholder="请输入" controls-position="right" class="full-width" /></el-form-item></el-col>
                <el-col :span="12" v-if="matForm.shape !== '板材'"><el-form-item label="长度(m)"><el-input-number v-model="matForm.L" :min="0" :step="0.01" :precision="4" placeholder="请输入" controls-position="right" class="full-width" /></el-form-item></el-col>
                <el-col :span="12" v-if="matForm.shape !== '板材'"><el-form-item label="外径(m)"><el-input-number v-model="matForm.outR" :min="0" :step="0.01" :precision="4" placeholder="请输入" controls-position="right" class="full-width" /></el-form-item></el-col>
                <el-col :span="12" v-if="matForm.shape === '管材'"><el-form-item label="内径(m)"><el-input-number v-model="matForm.inR" :min="0" :step="0.01" :precision="4" placeholder="请输入" controls-position="right" class="full-width" /></el-form-item></el-col>
              </el-row>
              <el-row :gutter="20" class="weight-row">
                <el-col :span="12">
                  <el-form-item label="材料毛重(kg)">
                    <el-input :model-value="calculatedWeight.toFixed(3)" disabled class="full-width" />
                  </el-form-item>
                </el-col>
                <el-col :span="12">
                  <el-form-item label="零件净重(kg)">
                    <el-input-number
                      v-model="matForm.netWeight"
                      :min="0"
                      :step="0.01"
                      :precision="3"
                      placeholder="请输入"
                      controls-position="right"
                      class="full-width"
                    />
                  </el-form-item>
                </el-col>
              </el-row>
              <div class="pricing-workbench-summary material-summary">
                <table class="material-summary-table" aria-label="材料成本核算汇总">
                  <tbody>
                    <tr>
                      <th scope="row" title="毛坯体积">毛坯体积</th>
                      <td :title="`${calculatedVolume.toFixed(6)} m³`">{{ calculatedVolume.toFixed(6) }} m³</td>
                      <th scope="row" title="毛坯重量">毛坯重量</th>
                      <td :title="`${calculatedWeight.toFixed(3)} kg`">{{ calculatedWeight.toFixed(3) }} kg</td>
                    </tr>
                    <tr>
                      <th scope="row" title="材料单价">材料单价</th>
                      <td :title="`${currentUnitPrice.toFixed(2)} 元/kg`">{{ currentUnitPrice.toFixed(2) }} 元/kg</td>
                      <th scope="row" title="预测本件材料费">预测本件材料费</th>
                      <td :title="`¥ ${calculatedMaterialCost.toFixed(2)}`">¥ {{ calculatedMaterialCost.toFixed(2) }}</td>
                    </tr>
                  </tbody>
                </table>
              </div>
            </el-form>
          </section>

          <!-- 模块B：工艺 -->
          <section class="erp-dialog-section pricing-workbench-section pricing-process-section">
            <div class="process-title-row">
              <div class="form-section-title process-title pricing-section-title">模块 B：工艺路线排程（已继承全局设定）</div>
              <el-button type="primary" plain size="small" :icon="Plus" @click="addProcess">
                新增工序
              </el-button>
            </div>
            <div class="process-box">
              <el-empty v-if="processRoute.length === 0" description="暂无工艺路线" :image-size="60">
                <el-button type="primary" plain size="small" :icon="Plus" @click="addProcess">新增工序</el-button>
              </el-empty>
              <el-row :gutter="10" v-for="(proc, index) in processRoute" :key="proc.rowKey || index" class="process-row">
                <el-col :span="7">
                  <el-button class="picker-trigger full-width" :title="formatProcessLabel(proc, '选择工序')" @click="openProcessPicker(index)">
                    <span :class="proc.processName ? 'picker-trigger-text' : 'picker-trigger-placeholder'" :title="formatProcessLabel(proc, '选择工序')">
                      {{ formatProcessLabel(proc, '选择工序') }}
                    </span>
                    <span class="picker-trigger-action">选择</span>
                  </el-button>
                </el-col>
                <el-col :span="3">
                  <el-tag :type="proc.calcType === '非工时费用' ? 'warning' : 'info'">{{ proc.calcType || '工时' }}</el-tag>
                </el-col>
                <el-col :span="8">
                  <el-input-number
                    v-model="proc.inputVal"
                    :min="0"
                    :step="proc.calcType === '非工时费用' ? 10 : 0.5"
                    placeholder="请输入"
                    class="full-width"
                  />
                </el-col>
                <el-col :span="6">
                  <div class="erp-table-actions process-row-actions">
                    <el-tooltip content="在当前行前插入工序" placement="top">
                      <el-button size="small" plain :icon="Plus" @click="insertProcess(index)">插入</el-button>
                    </el-tooltip>
                    <el-tooltip content="删除当前工序" placement="top">
                      <el-button size="small" type="danger" plain :icon="Delete" @click="removeProcess(index)">删除</el-button>
                    </el-tooltip>
                  </div>
                </el-col>
              </el-row>
            </div>
          </section>
          </template>

          <section v-else class="erp-dialog-section pricing-workbench-section pricing-assembly-section assembly-box">
            <div class="form-section-title pricing-section-title">模块 A：组合件子件汇总</div>
            <el-alert
              type="info"
              :closable="false"
              title="组合件父件价格 = 子件含税单价汇总 + 组装费 + 组装费对应的管理费/利润/税费；组装费不收运输费。"
              class="assembly-tip"
            />
            <div class="erp-table-actions assembly-actions">
              <el-button type="primary" plain size="small" :icon="Plus" @click="openAssemblyChildPicker">选择子件</el-button>
            </div>
            <el-empty v-if="assemblyChildren.length === 0" description="暂无子件关系" :image-size="60">
              <el-button type="primary" plain size="small" :icon="Plus" @click="openAssemblyChildPicker">选择子件</el-button>
            </el-empty>
            <el-row
              v-for="(child, index) in assemblyChildren"
              :key="child.rowKey || index"
              :gutter="10"
              class="assembly-row"
            >
              <el-col :span="10">
                <div class="assembly-child-info">
                  <div class="assembly-child-title" :title="formatAssemblyChildTitle(child)">{{ formatAssemblyChildTitle(child) }}</div>
                  <div class="assembly-child-desc" :title="child.description || '-'">{{ child.description || '-' }}</div>
                </div>
              </el-col>
              <el-col :span="4">
                <el-input-number
                  v-model="child.qtyPerParent"
                  :min="0.0001"
                  :step="1"
                  :precision="4"
                  controls-position="right"
                  class="full-width"
                />
              </el-col>
              <el-col :span="6">
                <div class="assembly-line-price">
                  {{ child.finalUnitPrice != null ? `¥ ${(Number(child.finalUnitPrice) * Number(child.qtyPerParent || 0)).toFixed(2)}` : '子件未核价' }}
                </div>
              </el-col>
              <el-col :span="4">
                <div class="erp-table-actions assembly-child-actions">
                  <el-button size="small" type="danger" plain :icon="Delete" @click="removeAssemblyChild(index)">删除</el-button>
                </div>
              </el-col>
            </el-row>
            <el-form label-width="100px" class="pricing-section-form assembly-fee-form">
              <el-form-item label="组装费">
                <el-input-number
                  v-model="assemblyFee"
                  :min="0"
                  :step="10"
                  :precision="2"
                  placeholder="请输入"
                  controls-position="right"
                  class="full-width"
                />
              </el-form-item>
            </el-form>
            <div class="pricing-workbench-summary assembly-summary">
              子件含税汇总：¥ {{ assemblyChildrenTotal.toFixed(2) }}，
              组装费：¥ {{ Number(assemblyFee || 0).toFixed(2) }}，
              组装费附加费用：¥ {{ assemblyFeeMarkupTotal.toFixed(2) }}，
              父件含税单价：¥ {{ assemblyFinalUnitPrice.toFixed(2) }}
            </div>
            <div v-if="assemblyFeeMarkupRows.length" class="assembly-fee-breakdown">
              <el-tag
                v-for="row in assemblyFeeMarkupRows"
                :key="row.feeName"
                type="info"
                size="small"
              >
                {{ row.feeName }} {{ row.ratePercent }}%：¥ {{ row.cost.toFixed(2) }}
              </el-tag>
            </div>
          </section>
        </div>

        <aside class="drawing-pane">
          <div class="drawing-pane-header">
            <div>
              <div class="drawing-pane-title">当前零件图纸</div>
              <div class="drawing-pane-subtitle">{{ currentRow ? `${currentRow.partNo || '-'} / ${currentRow.version || '-'}` : '-' }}</div>
            </div>
            <el-button
              v-if="currentRow?.drawingUrl"
              type="primary"
              plain
              size="small"
              @click="openDrawingInNewWindow(currentRow.drawingUrl)"
            >
              新窗口打开
            </el-button>
          </div>
          <div v-if="currentRow?.drawingUrl" v-loading="currentDrawingLoading" class="drawing-frame">
            <iframe
              :key="currentRow?.id"
              :src="currentDrawingSrc"
              class="drawing-frame-iframe"
              frameborder="0"
            ></iframe>
          </div>
          <el-empty v-else description="当前零件没有绑定图纸" :image-size="90" class="drawing-empty" />
        </aside>
      </div>

      <template #footer>
        <div class="erp-dialog-footer pricing-dialog-footer">
          <el-button @click="dialogVisible = false">取消</el-button>
          <el-button v-permission="'engineer:pricing:save-item'" type="success" @click="submitCurrentQuote" :loading="submitLoading">确认提交</el-button>
        </div>
      </template>
    </el-dialog>

    <el-dialog
      v-model="assemblyPickerVisible"
      title="选择组合件子件"
      width="86vw"
      top="6vh"
      class="assembly-picker-dialog"
      destroy-on-close
    >
      <div class="assembly-picker-toolbar">
        <div>
          <div class="assembly-picker-title">当前 RFQ 零件表</div>
          <div class="assembly-picker-subtitle">可一次选择多个子件，已加入的子件不可重复选择。</div>
        </div>
        <el-tag type="info">已选 {{ assemblyPickerSelection.length }} 项</el-tag>
      </div>
      <el-table
        ref="assemblyPickerTableRef"
        :data="pagedAssemblyChildOptions"
        row-key="id"
        border
        stripe
        height="560"
        @selection-change="handleAssemblyPickerSelection"
      >
        <el-table-column type="selection" width="48" :selectable="isAssemblyPickerRowSelectable" :reserve-selection="true" />
        <el-table-column prop="partNo" label="图号" width="150" fixed show-overflow-tooltip />
        <el-table-column prop="version" label="版本" width="80" align="center" />
        <el-table-column prop="description" label="名称/描述" min-width="180" show-overflow-tooltip />
        <el-table-column prop="qty" label="数量" width="80" align="center" />
        <el-table-column label="类型" width="90" align="center">
          <template #default="{ row }">
            <el-tag :type="isAssemblyItem(row) ? 'warning' : 'info'" size="small">
              {{ isAssemblyItem(row) ? '组合件' : '普通件' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="100" align="center">
          <template #default="{ row }">{{ row.status || '-' }}</template>
        </el-table-column>
        <el-table-column label="含税单价" width="110" align="right">
          <template #default="{ row }">
            <span v-if="row.finalUnitPrice != null">¥ {{ Number(row.finalUnitPrice).toFixed(2) }}</span>
            <el-tag v-else type="warning" size="small">未核价</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="图纸" width="190" align="center" fixed="right">
          <template #default="{ row }">
            <div v-if="row.drawingUrl" class="erp-table-actions picker-drawing-actions">
              <el-button type="warning" plain size="small" @click.stop="openPdfPreview(row.drawingUrl, row.partNo)">
                弹窗预览
              </el-button>
              <el-button type="primary" plain size="small" @click.stop="openDrawingInNewWindow(row.drawingUrl)">
                新窗口
              </el-button>
            </div>
            <el-tag v-else type="info" size="small">无图</el-tag>
          </template>
        </el-table-column>
      </el-table>
      <div v-if="assemblyChildOptions.length" class="erp-pagination assembly-picker-pagination">
        <el-pagination
          v-model:current-page="assemblyPickerPagination.page"
          v-model:page-size="assemblyPickerPagination.pageSize"
          :page-sizes="CLIENT_PAGE_SIZES"
          :pager-count="7"
          :total="assemblyChildOptions.length"
          layout="total, sizes, prev, pager, next, jumper"
          background
          @size-change="assemblyPickerPagination.page = 1"
        />
      </div>
      <template #footer>
        <div class="erp-dialog-footer pricing-dialog-footer assembly-picker-footer">
          <el-button @click="assemblyPickerVisible = false">取消</el-button>
          <el-button type="primary" :disabled="assemblyPickerSelection.length === 0" @click="confirmAssemblyChildPicker">
            加入子件
          </el-button>
        </div>
      </template>
    </el-dialog>

    <EngineeringOptionPickerDialog
      v-model="materialPickerVisible"
      title="选择材料"
      type="material"
      :items="materialOptions"
      :selected-key="matForm.materialName"
      empty-text="暂无材料字典数据"
      @confirm="selectMaterial"
    />

    <EngineeringOptionPickerDialog
      v-model="processPickerVisible"
      title="选择工艺"
      type="process"
      :items="combinedProcessOptions"
      :selected-key="selectedProcessOptionKey"
      empty-text="暂无工艺字典数据"
      @confirm="selectProcess"
    />

    <!-- 图纸预览 -->
    <el-dialog
      v-model="pdfVisible"
      :title="`图纸预览：${pdfTitle}`"
      width="75%"
      top="5vh"
      destroy-on-close
    >
      <div v-loading="!pdfLoaded" class="pdf-frame-shell">
        <iframe
          :src="currentPdfSrc"
          width="100%"
          height="100%"
          frameborder="0"
          @load="pdfLoaded = true"
        ></iframe>
      </div>
    </el-dialog>

  </div>
</template>

<script setup>
import { ref, reactive, computed, nextTick, onMounted, onBeforeUnmount, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Delete, Plus } from '@element-plus/icons-vue'
import { useRoute, useRouter } from 'vue-router'
import EngineeringOptionPickerDialog from './EngineeringOptionPickerDialog.vue'
import {
  completeOrderQuote as completeOrderQuoteRequest,
  getRepeatAnalysis,
  listAssemblyRelations,
  saveOrderConfig,
  submitQuote
} from '../services/engineerApi'
import { downloadCustomerLeadTimeQuote, getMarketSummary } from '../services/marketApi'
import { getTask, refreshEngineerQuotes } from '../services/taskApi'
import { useDictionaryStore } from '../stores/dictionaryStore'
import { useGlobalConfigStore } from '../stores/globalConfigStore'
import {
  createAuthenticatedObjectUrl,
  openAuthenticatedFileInNewWindow,
  readFileErrorMessage,
  revokeObjectUrl
} from '../api'
import {
  CLIENT_PAGE_SIZES,
  DEFAULT_CLIENT_PAGE_SIZE,
  clampPage,
  paginateRows
} from '../utils/clientPagination'

const route = useRoute()
const router = useRouter()
const dictionaryStore = useDictionaryStore()
const globalConfigStore = useGlobalConfigStore()
const rfqId = ref('')
const globalConfig = ref({ materialName: '45#', shape: '板材', processRoute: [] }) // 默认保底配置

const quoteData = ref(null)
const loading = ref(false)
const completeLoading = ref(false)
const refreshLoading = ref(false)
const pricingTableRef = ref(null)
const pricingListPagination = reactive({ page: 1, pageSize: DEFAULT_CLIENT_PAGE_SIZE })
const materialOptions = computed(() => dictionaryStore.materials)
const processOptions = computed(() => dictionaryStore.processes)
const extraFeeOptions = computed(() => dictionaryStore.extraFeeItems)
const markupFeeOptions = computed(() => dictionaryStore.markupFees)
const materialPickerVisible = ref(false)
const processPickerVisible = ref(false)
const editingProcessIndex = ref(null)
const repeatAnalysis = ref(null)

const combinedProcessOptions = computed(() => dictionaryStore.combinedProcessOptions)

const defaultGlobalConfig = () => ({ materialName: '45#', shape: '板材', processRoute: [] })
const normalizePartNo = (value) => String(value || '').trim().toUpperCase()
const normalizeVersion = (value) => {
  const text = String(value || '').trim().toUpperCase()
  return text
}
const itemIdentityKey = (item) => `${normalizePartNo(item?.partNo)}||${normalizeVersion(item?.version)}`

const normalizeGlobalConfig = (config) => {
  if (!config || typeof config !== 'object') return defaultGlobalConfig()
  return {
    materialName: config.materialName || defaultGlobalConfig().materialName,
    shape: config.shape || defaultGlobalConfig().shape,
    processRoute: normalizeRouteList(config.processRoute)
  }
}

const numberOrZero = (value) => {
  const numericValue = Number(value || 0)
  return Number.isFinite(numericValue) ? numericValue : 0
}
const numberOrNullForInput = (value) => {
  if (value === null || value === undefined || value === '') return null
  const numericValue = Number(value)
  if (!Number.isFinite(numericValue) || numericValue === 0) return null
  return numericValue
}
const formatWeight = (value) => {
  const numericValue = Number(value)
  return Number.isFinite(numericValue) ? numericValue.toFixed(3) : '-'
}
const formatAssemblyChildTitle = (child) => `${child?.partNo || '-'} / ${child?.version || '-'}`
const toMetricDimension = (value, unit = 'm') => {
  if (value === null || value === undefined || value === '') return null
  const numericValue = numberOrZero(value)
  return unit === 'm' ? numericValue : numericValue / 1000
}

const repeatItemMap = computed(() => {
  const map = new Map()
  ;(repeatAnalysis.value?.repeatItems || []).forEach(item => {
    map.set(itemIdentityKey(item), item)
  })
  return map
})

const pagedPricingItems = computed(() => paginateRows(quoteData.value?.itemList || [], pricingListPagination))

const canExportQuote = computed(() => {
  const items = quoteData.value?.itemList || []
  const drawableItems = items.filter(item => item.drawingUrl)
  return !!rfqId.value
    && items.length > 0
    && drawableItems.every(item => item.finalUnitPrice != null)
})

const canCompleteQuote = computed(() => {
  const items = quoteData.value?.itemList || []
  const drawableItems = items.filter(item => item.drawingUrl)
  return !!rfqId.value
    && quoteData.value?.orderInfo?.status !== '已报价'
    && items.length > 0
    && drawableItems.every(item => item.finalUnitPrice != null)
})

const canRefreshQuotes = computed(() => {
  const items = quoteData.value?.itemList || []
  return !!rfqId.value
    && !!quoteData.value
    && items.some(item => item.finalUnitPrice != null || isAssemblyItem(item))
})

const returnToGlobalSetup = () => {
  if (!rfqId.value) {
    router.push({ path: '/engineering' })
    return
  }
  router.push({
    path: '/engineering',
    query: { rfqId: rfqId.value }
  })
}

watch(
  () => [quoteData.value?.itemList?.length || 0, pricingListPagination.page, pricingListPagination.pageSize],
  () => {
    pricingListPagination.page = clampPage(pricingListPagination, quoteData.value?.itemList?.length || 0)
  }
)

// 页面加载时自动拉取数据
onMounted(async () => {
  rfqId.value = route.query.rfqId
  await fetchDicts()

  // 1. 读取全局缓存
  globalConfig.value = globalConfigStore.loadLocalConfig(rfqId.value)

  // 2. 自动根据路由 ID 获取数据
  if (rfqId.value) {
    fetchQuoteData()
  } else {
    ElMessage.error('缺失订单 ID 参数，请返回上一页重新进入！')
  }
})

const fetchDicts = async () => {
  try {
    await dictionaryStore.loadBaseDicts({ includeMarkupFees: true })
  } catch (error) {
    console.error(error)
    dictionaryStore.applyPricingFallbackData()
    ElMessage.warning('基础字典加载失败，已启用本地兜底数据。')
  }
}

// 拉取订单核心数据
const getPricingTableScrollTop = () => {
  const wrapper = pricingTableRef.value?.$el?.querySelector('.el-scrollbar__wrap')
  return wrapper?.scrollTop || 0
}

const restorePricingTableScrollTop = (scrollTop) => {
  requestAnimationFrame(() => {
    const wrapper = pricingTableRef.value?.$el?.querySelector('.el-scrollbar__wrap')
    if (wrapper) {
      wrapper.scrollTop = scrollTop
    }
  })
}

const fetchQuoteData = async (options = {}) => {
  const scrollTop = options.keepScroll ? getPricingTableScrollTop() : 0
  loading.value = true
  try {
    // 动态加载真实的 rfqId
    quoteData.value = await getMarketSummary(rfqId.value)
    if (!options.keepScroll) {
      pricingListPagination.page = 1
    }
    if (quoteData.value?.orderInfo?.engineerConfig) {
      globalConfig.value = globalConfigStore.applyEngineerConfig(rfqId.value, quoteData.value.orderInfo.engineerConfig)
    }
    await loadRepeatAnalysis()
    if (options.keepScroll) {
      restorePricingTableScrollTop(scrollTop)
    }
  } catch (error) {
    ElMessage.error('获取失败，请检查后端状态！')
  } finally {
    loading.value = false
  }
}

const loadRepeatAnalysis = async () => {
  if (!rfqId.value) return
  try {
    repeatAnalysis.value = await getRepeatAnalysis(rfqId.value)
  } catch (error) {
    console.error(error)
    repeatAnalysis.value = null
  }
}

const exportQuoteSheet = async () => {
  if (!rfqId.value) return ElMessage.warning('缺失订单 ID。')
  const result = await downloadCustomerLeadTimeQuote(rfqId.value)
  if (!result.success) {
    ElMessage.error(result.message || '报价单导出失败。')
  }
}

const completeOrderQuote = async () => {
  if (!canCompleteQuote.value) {
    return ElMessage.warning('所有有图零件完成核价后才能提交订单报价。')
  }
  completeLoading.value = true
  try {
    const response = await completeOrderQuoteRequest(rfqId.value)
    ElMessage.success(response.message || '订单报价已完成。')
    await fetchQuoteData()
  } catch (error) {
    console.error(error)
    ElMessage.error(error.response?.data?.message || '完成报价提交失败。')
  } finally {
    completeLoading.value = false
  }
}

const refreshOrderQuotes = async () => {
  if (!canRefreshQuotes.value) {
    return ElMessage.warning('当前订单没有可刷新的核价数据。')
  }
  try {
    await ElMessageBox.confirm(
      '将使用当前材料单价、工艺单价、费率字典，对本 RFQ 已有核价参数的零件重新计算价格；未核价且没有参数的零件不会被自动报价。',
      '确认一键刷新核价',
      {
        confirmButtonText: '确认刷新',
        cancelButtonText: '取消',
        type: 'warning'
      }
    )
  } catch {
    return
  }

  refreshLoading.value = true
  try {
    const submitResponse = await refreshEngineerQuotes(rfqId.value)
    const taskId = submitResponse?.task?.id
    if (!taskId) {
      throw new Error('任务提交失败')
    }
    ElMessage.info('一键刷新核价任务已提交，系统正在后台处理。')
    const task = await waitSystemTask(taskId)
    const data = task.resultJson || {}
    await fetchDicts()
    await fetchQuoteData({ keepScroll: true })
    const skippedCount = Number(data.skippedCount || 0)
    if (skippedCount > 0) {
      ElMessage.warning(`${data.message || '一键刷新核价完成'} 有 ${skippedCount} 个零件未刷新，请查看控制台或逐件核价。`)
    } else {
      ElMessage.success(data.message || '一键刷新核价完成。')
    }
  } catch (error) {
    console.error(error)
    ElMessage.error(error.response?.data?.message || error.message || '一键刷新核价失败，请检查后端服务。')
  } finally {
    refreshLoading.value = false
  }
}

const waitSystemTask = async (taskId) => {
  for (let i = 0; i < 120; i++) {
    const task = await getTask(taskId) || {}
    if (task.status === 'SUCCESS') {
      return task
    }
    if (task.status === 'FAILED') {
      throw new Error(task.errorMessage || '后台任务执行失败')
    }
    await new Promise(resolve => setTimeout(resolve, 1500))
  }
  throw new Error('后台任务执行超时，请稍后查看结果。')
}

// 图纸预览核心逻辑
const pdfVisible = ref(false)
const currentPdfUrl = ref('')
const pdfTitle = ref('')
const pdfLoaded = ref(false)
const currentPdfSrc = ref('')

const cleanupPdfSrc = () => {
  revokeObjectUrl(currentPdfSrc.value)
  currentPdfSrc.value = ''
}

const openPdfPreview = async (url, partNo) => {
  if (!url) {
    return ElMessage.warning('当前零件没有绑定图纸。')
  }
  pdfTitle.value = partNo
  currentPdfUrl.value = url
  pdfLoaded.value = false
  cleanupPdfSrc()
  pdfVisible.value = true
  try {
    currentPdfSrc.value = await createAuthenticatedObjectUrl(url)
  } catch (error) {
    pdfVisible.value = false
    ElMessage.error(await readFileErrorMessage(error) || '图纸预览失败。')
  }
}

const openDrawingInNewWindow = async (url) => {
  if (!url) {
    return ElMessage.warning('当前零件没有绑定图纸。')
  }
  const result = await openAuthenticatedFileInNewWindow(url)
  if (!result.success) {
    ElMessage.warning(result.blocked ? '浏览器拦截了新窗口，请允许弹窗后重试。' : (result.message || '图纸打开失败。'))
  }
}

// 核价弹窗核心逻辑
const dialogVisible = ref(false)
const currentRow = ref(null)
const currentDrawingSrc = ref('')
const currentDrawingLoading = ref(false)
const submitLoading = ref(false)
const quoteMode = ref('NORMAL')
const assemblyFee = ref(null)
const assemblyChildren = ref([])
const assemblyPickerVisible = ref(false)
const assemblyPickerSelection = ref([])
const assemblyPickerTableRef = ref(null)
const assemblyPickerPagination = reactive({ page: 1, pageSize: DEFAULT_CLIENT_PAGE_SIZE })
let assemblyChildSeq = 0
let currentDrawingRequestSeq = 0
let syncingAssemblyPickerSelection = false

const cleanupCurrentDrawingSrc = () => {
  revokeObjectUrl(currentDrawingSrc.value)
  currentDrawingSrc.value = ''
}

const loadCurrentDrawing = async (url) => {
  currentDrawingRequestSeq += 1
  const requestSeq = currentDrawingRequestSeq
  cleanupCurrentDrawingSrc()
  currentDrawingLoading.value = false
  if (!url) return

  currentDrawingLoading.value = true
  try {
    const objectUrl = await createAuthenticatedObjectUrl(url)
    if (requestSeq === currentDrawingRequestSeq) {
      currentDrawingSrc.value = objectUrl
    } else {
      revokeObjectUrl(objectUrl)
    }
  } catch (error) {
    if (requestSeq === currentDrawingRequestSeq) {
      ElMessage.warning(await readFileErrorMessage(error) || '当前图纸加载失败。')
    }
  } finally {
    if (requestSeq === currentDrawingRequestSeq) {
      currentDrawingLoading.value = false
    }
  }
}

watch(dialogVisible, (visible) => {
  if (visible) {
    loadCurrentDrawing(currentRow.value?.drawingUrl)
  } else {
    currentDrawingRequestSeq += 1
    cleanupCurrentDrawingSrc()
    currentDrawingLoading.value = false
    materialPickerVisible.value = false
    processPickerVisible.value = false
    editingProcessIndex.value = null
  }
})

watch(processPickerVisible, (visible) => {
  if (!visible) {
    editingProcessIndex.value = null
  }
})

watch(pdfVisible, (visible) => {
  if (!visible) {
    cleanupPdfSrc()
    pdfLoaded.value = false
  }
})

onBeforeUnmount(() => {
  currentDrawingRequestSeq += 1
  cleanupCurrentDrawingSrc()
  cleanupPdfSrc()
})

const matForm = reactive({ materialName: '', shape: '', L: null, W: null, T: null, outR: null, inR: null, netWeight: null })
const processRoute = ref([])
let processRouteSeq = 0
const PROCESS_SORT_STEP = 10

const formatMaterialLabel = (material) => {
  if (!material) return ''
  return material.remarks ? `${material.materialName} ${material.remarks}` : material.materialName
}

const selectedMaterialLabel = computed(() => {
  const material = materialOptions.value.find(item => item.materialName === matForm.materialName)
  return formatMaterialLabel(material) || matForm.materialName || '请选择材质'
})

const findProcessOption = (proc) => {
  if (!proc) return null
  return combinedProcessOptions.value.find(item => item.optionKey === proc.optionKey)
    || combinedProcessOptions.value.find(item => item.processName === proc.processName && item.sourceType === (proc.sourceType || 'process'))
    || combinedProcessOptions.value.find(item => item.processName === proc.processName)
    || null
}

const selectedProcessOptionKey = computed(() => {
  const proc = processRoute.value[editingProcessIndex.value]
  return findProcessOption(proc)?.optionKey || proc?.optionKey || ''
})

const formatProcessLabel = (proc, fallback = '请选择工序') => {
  const option = findProcessOption(proc)
  if (option?.label) return option.label
  if (proc?.processName) return `${proc.processName}（${proc.calcType || '工时'}）`
  return fallback
}

const applyProcessSelection = (proc, selected) => {
  if (!proc || !selected) return
  proc.optionKey = selected.optionKey
  proc.processName = selected.processName
  proc.calcType = selected.calcType
  proc.sourceType = selected.sourceType
}

const selectMaterial = (material) => {
  if (!material?.materialName) return
  matForm.materialName = material.materialName
}

const openProcessPicker = (index) => {
  editingProcessIndex.value = index
  processPickerVisible.value = true
}

const selectProcess = (selected) => {
  const proc = processRoute.value[editingProcessIndex.value]
  applyProcessSelection(proc, selected)
  editingProcessIndex.value = null
}

const isAssemblyItem = (item) => String(item?.quoteMode || '').toUpperCase() === 'ASSEMBLY'

const assemblyChildOptions = computed(() => {
  const currentId = currentRow.value?.id
  return (quoteData.value?.itemList || []).filter(item => item.id !== currentId)
})

const pagedAssemblyChildOptions = computed(() => paginateRows(assemblyChildOptions.value, assemblyPickerPagination))

const selectedAssemblyChildIds = computed(() => new Set(
  assemblyChildren.value
    .map(child => child.childItemId)
    .filter(Boolean)
))

const selectedAssemblyPickerIds = computed(() => new Set(assemblyPickerSelection.value.map(row => row.id)))

const syncAssemblyPickerTableSelection = async () => {
  if (!assemblyPickerVisible.value) return
  syncingAssemblyPickerSelection = true
  await nextTick()
  assemblyPickerTableRef.value?.clearSelection()
  pagedAssemblyChildOptions.value.forEach(row => {
    if (selectedAssemblyPickerIds.value.has(row.id)) {
      assemblyPickerTableRef.value?.toggleRowSelection(row, true)
    }
  })
  await nextTick()
  syncingAssemblyPickerSelection = false
}

watch(
  () => [assemblyChildOptions.value.length, assemblyPickerPagination.page, assemblyPickerPagination.pageSize, assemblyPickerVisible.value],
  () => {
    assemblyPickerPagination.page = clampPage(assemblyPickerPagination, assemblyChildOptions.value.length)
    syncAssemblyPickerTableSelection()
  }
)

const assemblyChildrenTotal = computed(() => assemblyChildren.value.reduce((sum, child) => {
  const unitPrice = Number(child.finalUnitPrice)
  const qty = Number(child.qtyPerParent || 0)
  return Number.isFinite(unitPrice) ? sum + unitPrice * qty : sum
}, 0))

const isTransportMarkupFee = (feeName) => {
  const text = String(feeName || '')
  return text.includes('运输') || text.includes('运费')
}

const assemblyFeeMarkupRows = computed(() => markupFeeOptions.value
  .filter(fee => fee.enabled !== false && !isTransportMarkupFee(fee.feeName))
  .map(fee => {
    const ratePercent = Number(fee.ratePercent || 0)
    const cost = numberOrZero(assemblyFee.value) * ratePercent / 100
    return {
      feeName: fee.feeName,
      ratePercent,
      cost
    }
  }))

const assemblyFeeMarkupTotal = computed(() => assemblyFeeMarkupRows.value.reduce((sum, row) => sum + numberOrZero(row.cost), 0))

const assemblyFinalUnitPrice = computed(() => assemblyChildrenTotal.value + numberOrZero(assemblyFee.value) + assemblyFeeMarkupTotal.value)

const numericSortNo = (value) => {
  const numericValue = Number(value)
  return Number.isFinite(numericValue) ? numericValue : null
}

const createProcessRow = (source = {}) => ({
  rowKey: `process-${Date.now()}-${processRouteSeq++}`,
  processName: source.processName || '',
  optionKey: source.optionKey || '',
  calcType: source.calcType || '工时',
  sourceType: source.sourceType || (source.calcType === '非工时费用' ? 'extra' : 'process'),
  inputVal: numberOrNullForInput(source.inputVal),
  sortNo: numericSortNo(source.sortNo)
})

const createAssemblyChildRow = (source = {}) => {
  const childId = source.childItemId ?? source.id ?? null
  const option = assemblyChildOptions.value.find(item => item.id === childId)
  return {
    rowKey: `assembly-child-${Date.now()}-${assemblyChildSeq++}`,
    childItemId: childId,
    qtyPerParent: Number(source.qtyPerParent ?? source.childQtyPerParent ?? 1),
    sortNo: numericSortNo(source.sortNo),
    partNo: source.partNo || option?.partNo || '',
    version: source.version || option?.version || '',
    description: source.description || option?.description || '',
    finalUnitPrice: source.finalUnitPrice ?? option?.finalUnitPrice ?? null,
    status: source.status || option?.status || ''
  }
}

const removeAssemblyChild = (index) => {
  assemblyChildren.value.splice(index, 1)
}

const openAssemblyChildPicker = async () => {
  assemblyPickerSelection.value = []
  assemblyPickerPagination.page = 1
  assemblyPickerVisible.value = true
  await nextTick()
  assemblyPickerTableRef.value?.clearSelection()
}

const handleAssemblyPickerSelection = (rows) => {
  if (syncingAssemblyPickerSelection) return
  const currentPageIds = new Set(pagedAssemblyChildOptions.value.map(row => row.id))
  const preservedRows = assemblyPickerSelection.value.filter(row => !currentPageIds.has(row.id))
  assemblyPickerSelection.value = [...preservedRows, ...rows]
}

const isAssemblyPickerRowSelectable = (row) => !selectedAssemblyChildIds.value.has(row.id)

const confirmAssemblyChildPicker = () => {
  const existingIds = selectedAssemblyChildIds.value
  const rowsToAdd = assemblyPickerSelection.value.filter(row => !existingIds.has(row.id))
  rowsToAdd.forEach(row => {
    assemblyChildren.value.push(createAssemblyChildRow({
      childItemId: row.id,
      qtyPerParent: 1,
      partNo: row.partNo,
      version: row.version,
      description: row.description,
      finalUnitPrice: row.finalUnitPrice,
      status: row.status
    }))
  })
  assemblyPickerVisible.value = false
  assemblyPickerSelection.value = []
  if (rowsToAdd.length > 0) {
    ElMessage.success(`已加入 ${rowsToAdd.length} 个子件。`)
  }
}

const assignProcessSortNos = (rows = processRoute.value) => {
  rows.forEach((row, index) => {
    row.sortNo = (index + 1) * PROCESS_SORT_STEP
  })
  return rows
}

const addProcess = () => {
  processRoute.value.push(createProcessRow())
  assignProcessSortNos()
}

const insertProcess = (index) => {
  processRoute.value.splice(index, 0, createProcessRow())
  assignProcessSortNos()
}

const removeProcess = (index) => {
  processRoute.value.splice(index, 1)
  assignProcessSortNos()
}

const syncProcessMeta = (proc) => {
  const selected = combinedProcessOptions.value.find(item => item.optionKey === proc.optionKey)
  if (!selected) return
  applyProcessSelection(proc, selected)
}

const hydrateRouteItem = (proc) => {
  if (proc.optionKey) {
    syncProcessMeta(proc)
    return proc
  }
  const selected = combinedProcessOptions.value.find(item => item.processName === proc.processName && item.sourceType === (proc.sourceType || 'process'))
    || combinedProcessOptions.value.find(item => item.processName === proc.processName)
  if (selected) {
    proc.optionKey = selected.optionKey
    proc.processName = selected.processName
    proc.calcType = selected.calcType
    proc.sourceType = selected.sourceType
  }
  return proc
}

const normalizeRouteList = (route) => {
  let list = []
  if (!route) return list
  if (Array.isArray(route)) {
    list = JSON.parse(JSON.stringify(route))
  } else if (typeof route === 'string') {
    try {
      list = JSON.parse(route)
    } catch {
      return list
    }
  } else {
    return list
  }
  return sortRouteList(Array.isArray(list) ? list : [])
}

const sortRouteList = (routes) => {
  const withIndex = routes.map((route, index) => ({ route, index }))
  const hasSortNo = withIndex.some(item => numericSortNo(item.route?.sortNo) != null)
  if (!hasSortNo) return routes
  return withIndex
    .sort((a, b) => {
      const aSort = numericSortNo(a.route?.sortNo)
      const bSort = numericSortNo(b.route?.sortNo)
      if (aSort != null && bSort != null && aSort !== bSort) return aSort - bSort
      if (aSort != null && bSort == null) return -1
      if (aSort == null && bSort != null) return 1
      return a.index - b.index
    })
    .map(item => item.route)
}

const processRouteKey = (route) => {
  const sourceType = route.sourceType || (route.calcType === '非工时费用' ? 'extra' : 'process')
  return `${sourceType}::${route.processName || ''}`
}

const mergeRoutesByGlobalOrder = (savedRoutes, defaultRoutes) => {
  const savedRows = normalizeRouteList(savedRoutes).map(route => hydrateRouteItem(createProcessRow(route)))
  const defaultRows = normalizeRouteList(defaultRoutes).map(route => hydrateRouteItem(createProcessRow(route)))
  const savedBuckets = new Map()
  savedRows.forEach(route => {
    const key = processRouteKey(route)
    if (!savedBuckets.has(key)) savedBuckets.set(key, [])
    savedBuckets.get(key).push(route)
  })

  return assignProcessSortNos(defaultRows
    .filter(defaultRoute => defaultRoute.processName)
    .map(defaultRoute => {
      const savedRoute = savedBuckets.get(processRouteKey(defaultRoute))?.shift()
      return {
        ...defaultRoute,
        inputVal: savedRoute ? savedRoute.inputVal : defaultRoute.inputVal
      }
    }))
}

const buildQuoteProcessRoute = () => processRoute.value
  .map((p, index) => ({ ...p, sortNo: (index + 1) * PROCESS_SORT_STEP }))
  .filter(p => p.processName && p.inputVal > 0)
  .map(p => ({
    sortNo: p.sortNo,
    processName: p.processName,
    inputVal: p.inputVal,
    calcType: p.calcType || '工时'
  }))

const buildGlobalProcessRoute = () => processRoute.value
  .map((p, index) => ({ ...p, sortNo: (index + 1) * PROCESS_SORT_STEP }))
  .filter(p => p.processName)
  .map(p => ({
    sortNo: p.sortNo,
    processName: p.processName,
    optionKey: p.optionKey || '',
    calcType: p.calcType || '工时',
    sourceType: p.sourceType || (p.calcType === '非工时费用' ? 'extra' : 'process')
  }))

const comparableProcessRoute = (route) => normalizeRouteList(route)
  .filter(p => p.processName)
  .map((p, index) => ({
    sortNo: (index + 1) * PROCESS_SORT_STEP,
    processName: p.processName || '',
    calcType: p.calcType || '工时',
    sourceType: p.sourceType || (p.calcType === '非工时费用' ? 'extra' : 'process')
  }))

const syncCurrentRouteToGlobalConfig = async () => {
  const nextProcessRoute = buildGlobalProcessRoute()
  if (JSON.stringify(comparableProcessRoute(globalConfig.value.processRoute)) === JSON.stringify(comparableProcessRoute(nextProcessRoute))) {
    return { updated: false }
  }

  const nextConfig = {
    ...normalizeGlobalConfig(globalConfig.value),
    processRoute: nextProcessRoute
  }
  globalConfig.value = globalConfigStore.setConfig(rfqId.value, nextConfig)
  await saveOrderConfig(rfqId.value, JSON.parse(JSON.stringify(nextConfig)))
  return { updated: true }
}

const loadAssemblyRelations = async (row) => {
  assemblyChildren.value = []
  if (!row?.id) return
  try {
    const response = await listAssemblyRelations(row.id)
    assemblyChildren.value = (response || []).map(item => createAssemblyChildRow(item))
  } catch (error) {
    console.error(error)
    ElMessage.error('组合件子件关系加载失败。')
  }
}

// 打开弹窗，继承全局配置
const openQuoteDialog = async (row) => {
  currentRow.value = row
  dialogVisible.value = true
  quoteMode.value = isAssemblyItem(row) ? 'ASSEMBLY' : 'NORMAL'
  assemblyFee.value = numberOrNullForInput(row.assemblyFee)
  assemblyChildren.value = []

  // 继承阶段 1 的材料设置
  const savedMaterialData = row.materialData || {}
  const savedDimensions = savedMaterialData.dimensions || {}
  matForm.materialName = savedMaterialData.name || globalConfig.value.materialName || '45#'
  matForm.shape = savedMaterialData.shape || globalConfig.value.shape || '板材'
  const savedDimensionUnit = savedMaterialData.dimensionUnit || 'mm'
  matForm.L = toMetricDimension(savedDimensions.L, savedDimensionUnit)
  matForm.W = toMetricDimension(savedDimensions.W, savedDimensionUnit)
  matForm.T = toMetricDimension(savedDimensions.T, savedDimensionUnit)
  matForm.outR = toMetricDimension(savedDimensions.outR, savedDimensionUnit)
  matForm.inR = toMetricDimension(savedDimensions.inR, savedDimensionUnit)
  matForm.netWeight = numberOrNullForInput(row.partNetWeight ?? savedMaterialData.netWeight)

  if (globalConfig.value.processRoute && globalConfig.value.processRoute.length > 0) {
    processRoute.value = mergeRoutesByGlobalOrder(row.processRoute, globalConfig.value.processRoute)
  } else if (row.processRoute) {
    processRoute.value = assignProcessSortNos(normalizeRouteList(row.processRoute).map(route => hydrateRouteItem(createProcessRow(route))))
  } else {
    processRoute.value = assignProcessSortNos(normalizeRouteList(globalConfig.value.processRoute)
      .map(p => hydrateRouteItem(createProcessRow(p))))
  }

  if (quoteMode.value === 'ASSEMBLY') {
    await loadAssemblyRelations(row)
  }
}

// 智能计算材料费
const currentMatInfo = computed(() => {
  return materialOptions.value.find(item => item.materialName === matForm.materialName) || {
    density: 0,
    platePrice: 0,
    barPrice: 0,
    tubePrice: 0
  }
})
const currentUnitPrice = computed(() => {
  if (matForm.shape === '棒材') return currentMatInfo.value.barPrice || 0
  if (matForm.shape === '管材') return currentMatInfo.value.tubePrice || 0
  return currentMatInfo.value.platePrice || 0
})
const currentDensity = computed(() => {
  if (matForm.shape === '棒材') return currentMatInfo.value.barDensity || currentMatInfo.value.density || 0
  if (matForm.shape === '管材') return currentMatInfo.value.tubeDensity || currentMatInfo.value.density || 0
  return currentMatInfo.value.plateDensity || currentMatInfo.value.density || 0
})
const calculatedVolume = computed(() => {
  const length = numberOrZero(matForm.L)
  const width = numberOrZero(matForm.W)
  const thickness = numberOrZero(matForm.T)
  const outerDiameter = numberOrZero(matForm.outR)
  const innerDiameter = numberOrZero(matForm.inR)

  if (matForm.shape === '板材') {
    return length * width * thickness
  }
  if (matForm.shape === '棒材') {
    return Math.PI * Math.pow(outerDiameter / 2, 2) * length
  }
  if (matForm.shape === '管材') {
    const ringArea = Math.PI * (Math.pow(outerDiameter / 2, 2) - Math.pow(innerDiameter / 2, 2))
    return Math.max(ringArea, 0) * length
  }
  return 0
})
const calculatedWeight = computed(() => calculatedVolume.value * currentDensity.value)
const calculatedMaterialCost = computed(() => calculatedWeight.value * currentUnitPrice.value)

// 提交逻辑
const submitCurrentQuote = async () => {
  submitLoading.value = true
  let payload

  if (quoteMode.value === 'ASSEMBLY') {
    const validChildren = assemblyChildren.value
      .filter(child => child.childItemId && Number(child.qtyPerParent) > 0)
      .map((child, index) => ({
        childItemId: child.childItemId,
        qtyPerParent: Number(child.qtyPerParent),
        sortNo: (index + 1) * PROCESS_SORT_STEP
      }))
    if (validChildren.length === 0) {
      submitLoading.value = false
      return ElMessage.warning('组合件至少需要选择一个子件。')
    }
    const unquotedChildren = assemblyChildren.value.filter(child => child.childItemId && child.finalUnitPrice == null)
    if (unquotedChildren.length > 0) {
      submitLoading.value = false
      return ElMessage.warning('组合件存在未核价子件，请先完成子件核价。')
    }
    payload = {
      itemId: currentRow.value.id,
      quoteMode: 'ASSEMBLY',
      assemblyFee: numberOrZero(assemblyFee.value),
      assemblyChildren: validChildren,
      materialData: {},
      partNetWeight: 0,
      processRoute: []
    }
  } else {
    const validProcess = buildQuoteProcessRoute()
    payload = {
      itemId: currentRow.value.id,
      quoteMode: 'NORMAL',
      partNetWeight: numberOrZero(matForm.netWeight),
      materialData: {
        name: matForm.materialName,
        shape: matForm.shape,
        volume: calculatedVolume.value,
        weight: calculatedWeight.value,
        grossWeight: calculatedWeight.value,
        grossWeightUnit: 'kg',
        netWeight: numberOrZero(matForm.netWeight),
        netWeightUnit: 'kg',
        density: currentDensity.value,
        densityUnit: 'kg/m3',
        unitPrice: currentUnitPrice.value,
        unitPriceUnit: '元/kg',
        cost: calculatedMaterialCost.value,
        dimensionUnit: 'm',
        volumeUnit: 'm3',
        weightUnit: 'kg',
        dimensions: {
          L: matForm.L,
          W: matForm.W,
          T: matForm.T,
          outR: matForm.outR,
          inR: matForm.inR
        }
      },
      processRoute: validProcess
    }
  }

  try {
    await submitQuote(payload)
    let globalSynced = true
    let globalUpdated = false
    if (quoteMode.value === 'NORMAL') {
      try {
        const syncResult = await syncCurrentRouteToGlobalConfig()
        globalUpdated = !!syncResult.updated
      } catch (syncError) {
        console.error(syncError)
        globalSynced = false
      }
    }
    ElMessage[globalSynced ? 'success' : 'warning'](
      quoteMode.value === 'ASSEMBLY'
        ? '组合件核价成功。'
        : globalSynced
        ? (globalUpdated ? '核价成功，当前工艺路线已同步为订单全局设定。' : '核价成功，工艺路线未变化。')
        : '核价成功，但订单全局工艺同步失败，请返回全局设定页检查。'
    )
    dialogVisible.value = false
    await fetchQuoteData({ keepScroll: true }) // 刷新大表格，保留当前滚动位置
  } catch (error) {
    console.error(error)
    ElMessage.error(error.response?.data?.message || '核价提交失败，请检查后端服务。')
  } finally {
    submitLoading.value = false
  }
}
</script>

<style scoped>
.pricing-workbench-page {
  gap: var(--erp-space-4);
}

.pricing-page-header {
  align-items: center;
}

.pricing-page-actions {
  min-width: 260px;
}

.pricing-context-card {
  display: flex;
  align-items: stretch;
  justify-content: space-between;
  gap: var(--erp-space-5);
  padding: var(--erp-space-5);
}

.pricing-context-copy {
  display: flex;
  min-width: 240px;
  flex: 1;
  flex-direction: column;
  justify-content: center;
  gap: var(--erp-space-2);
}

.pricing-context-kicker {
  color: var(--erp-primary);
  font-size: var(--erp-font-size-xs);
  font-weight: var(--erp-font-weight-bold);
  letter-spacing: 0;
}

.pricing-context-copy strong {
  color: var(--erp-text);
  font-size: var(--erp-font-size-xl);
  line-height: var(--erp-line-height-tight);
}

.pricing-context-copy p {
  max-width: 520px;
  margin: 0;
  color: var(--erp-text-secondary);
  font-size: var(--erp-font-size-sm);
}

.pricing-metric-grid {
  display: grid;
  min-width: 360px;
  grid-template-columns: repeat(3, minmax(96px, 1fr));
  gap: var(--erp-space-3);
}

.pricing-metric {
  display: flex;
  flex-direction: column;
  justify-content: center;
  gap: var(--erp-space-2);
  min-height: 84px;
  padding: var(--erp-space-4);
  border: 1px solid var(--erp-border-soft);
  border-radius: var(--erp-radius-lg);
  background: var(--erp-surface-muted);
}

.pricing-metric-label {
  color: var(--erp-text-secondary);
  font-size: var(--erp-font-size-xs);
}

.pricing-metric-value {
  color: var(--erp-text);
  font-size: 24px;
  line-height: 1;
}

.pricing-toolbar {
  align-items: center;
}

.pricing-toolbar-copy {
  flex-direction: column;
  align-items: flex-start;
  gap: 2px;
}

.pricing-toolbar-title {
  color: var(--erp-text);
  font-weight: var(--erp-font-weight-bold);
}

.pricing-toolbar-copy small {
  color: var(--erp-text-secondary);
  font-size: var(--erp-font-size-xs);
}

.pricing-toolbar-actions {
  justify-content: flex-end;
}

.pricing-table-body {
  padding: var(--erp-space-3);
}

.pricing-empty-state {
  margin: var(--erp-space-4);
}

.pricing-table,
.full-width {
  width: 100%;
}

.pricing-row-actions {
  width: 100%;
}

.picker-trigger {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  min-height: 40px;
  padding: 0 12px;
  border-color: var(--erp-border);
  background: var(--erp-surface);
  color: var(--erp-text);
}

.picker-trigger :deep(> span) {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  width: 100%;
  min-width: 0;
}

.picker-trigger:hover,
.picker-trigger:focus {
  border-color: var(--erp-primary);
  color: var(--erp-primary);
}

.picker-trigger-text,
.picker-trigger-placeholder {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-align: left;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.picker-trigger-placeholder {
  color: var(--erp-text-secondary);
}

.picker-trigger-action {
  flex: none;
  color: var(--erp-primary);
  font-size: 12px;
  font-weight: 700;
}

.muted-price {
  color: var(--erp-text-secondary);
}

.success-price {
  color: var(--erp-success);
  font-weight: 700;
}

.danger-price {
  color: var(--erp-danger);
  font-weight: 700;
}

.pricing-workbench-section {
  display: flex;
  flex-direction: column;
  gap: var(--erp-space-3);
  padding: var(--erp-space-4);
  background: var(--erp-surface);
}

.form-section-title {
  margin: 0;
  padding-left: 10px;
  border-left: 3px solid var(--erp-primary);
  color: var(--erp-text);
  font-size: 14px;
  font-weight: 700;
  line-height: 1.2;
}

.pricing-section-form {
  padding: 0;
  border: 0;
  background: transparent;
}

.quote-mode-form {
  margin-top: 0;
}

.dimension-row,
.weight-row {
  margin-top: 4px;
  padding: 10px;
  border: 1px solid var(--erp-border-soft);
  border-radius: 6px;
  background: var(--erp-surface-muted);
}

.weight-row {
  margin-top: 10px;
}

.pricing-workbench-summary {
  margin-top: 10px;
  padding: 12px 14px;
  border: 1px solid var(--erp-border-soft);
  border-radius: 8px;
  background: var(--erp-surface-muted);
  color: var(--erp-danger);
  font-weight: 700;
  text-align: right;
}

.material-summary {
  margin-right: -10px;
  margin-left: -10px;
  padding: 0;
  overflow: hidden;
  color: var(--erp-text);
  text-align: left;
}

.material-summary-table {
  width: 100%;
  border-collapse: collapse;
  table-layout: fixed;
}

.material-summary-table th,
.material-summary-table td {
  min-width: 0;
  max-width: 0;
  overflow: hidden;
  padding: 10px 12px;
  border-bottom: 1px solid var(--erp-border-soft);
  text-overflow: ellipsis;
  vertical-align: middle;
  white-space: nowrap;
}

.material-summary-table tr:last-child th,
.material-summary-table tr:last-child td {
  border-bottom: 0;
}

.material-summary-table th {
  width: 24%;
  color: var(--erp-text-secondary);
  font-size: var(--erp-font-size-xs);
  font-weight: 600;
  text-align: left;
  background: var(--erp-surface-soft);
}

.material-summary-table td {
  width: 26%;
  color: var(--erp-danger);
  font-weight: 700;
  text-align: right;
  font-variant-numeric: tabular-nums;
}

.process-title-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.process-box {
  padding: 14px;
  border: 1px dashed var(--erp-border);
  border-radius: 8px;
  background: var(--erp-surface-muted);
}

.process-row {
  margin-bottom: 10px;
  align-items: center;
}

.process-row-actions {
  width: 100%;
  justify-content: flex-end;
}

.assembly-box {
  margin-top: 0;
}

.assembly-tip {
  margin-bottom: 0;
}

.assembly-actions {
  justify-content: flex-end;
}

.assembly-row {
  margin-bottom: 10px;
  align-items: center;
  padding: 10px;
  border: 1px solid var(--erp-border-soft);
  border-radius: 8px;
  background: var(--erp-surface-muted);
}

.assembly-child-info {
  min-width: 0;
}

.assembly-child-title {
  color: var(--erp-text);
  font-weight: 700;
  line-height: 1.4;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.assembly-child-desc {
  margin-top: 2px;
  color: var(--erp-text-secondary);
  font-size: 12px;
  line-height: 1.4;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.assembly-line-price {
  min-height: 32px;
  display: flex;
  align-items: center;
  justify-content: flex-end;
  color: var(--erp-text);
  font-weight: 700;
}

.assembly-child-actions {
  width: 100%;
  justify-content: flex-end;
}

.assembly-fee-form {
  margin-top: 0;
}

.assembly-fee-breakdown {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  flex-wrap: wrap;
  margin-top: 8px;
}

.assembly-picker-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 14px;
  margin-bottom: 12px;
}

.assembly-picker-title {
  color: var(--erp-text);
  font-size: 15px;
  font-weight: 700;
  line-height: 1.3;
}

.assembly-picker-subtitle {
  margin-top: 4px;
  color: var(--erp-text-secondary);
  font-size: 13px;
}

.picker-drawing-actions {
  width: 100%;
}

.picker-drawing-actions :deep(.el-button + .el-button) {
  margin-left: 0;
}

.pricing-dialog-footer {
  width: 100%;
}

:deep(.pricing-workbench-dialog .el-dialog__body) {
  max-height: 82vh;
  overflow: hidden;
  padding-top: 12px;
}

.pricing-workbench {
  display: grid;
  grid-template-columns: minmax(520px, 0.95fr) minmax(460px, 1.05fr);
  gap: 16px;
  height: 74vh;
  min-height: 560px;
}

.pricing-form-pane,
.drawing-pane {
  min-width: 0;
  min-height: 0;
}

.pricing-form-pane {
  display: flex;
  flex-direction: column;
  gap: var(--erp-space-3);
  overflow-y: auto;
  padding-right: 4px;
}

.drawing-pane {
  display: flex;
  flex-direction: column;
  border: 1px solid var(--erp-border-soft);
  border-radius: 8px;
  background: var(--erp-surface);
  overflow: hidden;
}

.drawing-pane-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 10px 12px;
  border-bottom: 1px solid var(--erp-border-soft);
  background: var(--erp-surface-soft);
}

.drawing-pane-title {
  color: var(--erp-text);
  font-weight: 700;
  line-height: 1.3;
}

.drawing-pane-subtitle {
  color: var(--erp-text-secondary);
  font-size: 12px;
  line-height: 1.4;
}

.drawing-frame {
  flex: 1;
  min-height: 0;
  background: var(--erp-surface-soft);
}

.drawing-frame-iframe {
  display: block;
  width: 100%;
  height: 100%;
}

.drawing-empty {
  flex: 1;
  display: flex;
  justify-content: center;
}

.dialog-header-bar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
  padding: 12px;
  border: 1px solid var(--erp-border-soft);
  border-radius: 8px;
  background: var(--erp-surface-muted);
}

.current-item-alert {
  flex: 1;
}

.dialog-drawing-actions {
  flex-shrink: 0;
}

.pdf-frame-shell {
  width: 100%;
  height: 70vh;
}

@media (max-width: 1180px) {
  :deep(.pricing-workbench-dialog .el-dialog__body) {
    max-height: none;
    overflow: visible;
  }

  .pricing-context-card,
  .pricing-page-header,
  .pricing-toolbar {
    align-items: stretch;
    flex-direction: column;
  }

  .pricing-metric-grid,
  .pricing-page-actions {
    width: 100%;
    min-width: 0;
  }

  .pricing-workbench {
    grid-template-columns: 1fr;
    height: auto;
    min-height: 0;
  }

  .pricing-form-pane {
    overflow: visible;
    padding-right: 0;
  }

  .drawing-pane {
    min-height: 520px;
  }
}

@media (max-width: 720px) {
  .pricing-metric-grid {
    grid-template-columns: 1fr;
  }

  .dialog-header-bar {
    align-items: stretch;
    flex-direction: column;
  }
}
</style>
