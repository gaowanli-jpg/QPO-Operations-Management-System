# 具体工艺小时价格设计文档

日期：2026-08-21

## 1. 背景

QPO 当前通过 MES 同步工时工艺主数据。现有模型中，`dict_process_category.hourly_rate` 表示工艺大类小时价格，`dict_process_detail` 具体工艺没有独立价格字段。具体工艺列表返回的 `hourlyRate` 由后端从所属大类价格拼装，零件核价保存也使用大类小时价格计算工艺金额。

新需求是：QPO 核价时需要支持针对“具体工艺”单独维护小时价格，核价、实时预估、保存快照和报价导出都应按具体工艺价格计算。MES 仍负责同步工艺大类和具体工艺身份，QPO 本地负责维护具体工艺核价价格。

## 2. 目标

1. 具体工艺支持单独维护小时价格。
2. 核价使用价格规则固定为：具体工艺价格优先，空值才兜底工艺大类价格。
3. `0` 是有效具体工艺价格，不得被当成空值兜底。
4. MES 同步不得覆盖 QPO 本地维护的具体工艺价格。
5. 前端实时价格预估、后端保存核价、工艺快照、报价导出使用同一个有效小时价格口径。
6. 不新增用户权限，继续复用材料工艺模块现有权限和日志口径。

## 3. 非目标

1. 不做价格版本管理或按生效日期取价。
2. 不做客户级、订单级或项目级工艺价格差异。
3. 不改变 MES 工艺主数据同步接口契约。
4. 不改变非工时费用、百分比费用、材料费用的计算规则。
5. 不批量重算历史已核价订单、已提单快照或已导出的报价单。

## 4. 推荐方案

采用“具体工艺可空覆盖价”方案：

- `dict_process_detail` 新增可空字段 `hourly_rate`，含义为“具体小时价格”。
- 有效小时价格计算规则：`effectiveHourlyRate = detail.hourlyRate != null ? detail.hourlyRate : category.hourlyRate`。
- `detail.hourlyRate = null` 表示未单独维护，继承大类小时价格。
- `detail.hourlyRate = 0` 表示明确维护为 0 元/小时，是有效价格。
- 前端下拉和实时预估继续消费 DTO 的 `hourlyRate`，但该字段改为有效小时价格。
- DTO 额外返回 `detailHourlyRate`、`categoryHourlyRate` 和 `effectiveHourlyRate`，用于页面展示和测试追溯。

这个方案保留 MES 大类价格作为默认价，同时让 QPO 可以对具体工艺做本地核价覆盖，兼容老数据且语义清晰。

## 5. 数据模型

### 5.1 Flyway 迁移

新增下一版迁移，当前仓库最高已到 `V15__data_center_readonly_role.sql`，因此后续实现使用：

```sql
V16__process_detail_hourly_rate.sql
```

建议内容：

```sql
ALTER TABLE dict_process_detail
    ADD COLUMN IF NOT EXISTS hourly_rate NUMERIC(18, 6);

COMMENT ON COLUMN dict_process_detail.hourly_rate IS '具体工艺小时价格；为空时继承工艺大类小时价格，0 表示明确为 0 元/小时';
```

不得修改 `V1__qpo_baseline_schema.sql`、`V2__qpo_baseline_seed_data.sql` 或任何已经执行过的历史迁移。

### 5.2 实体与 DTO

- `DictProcessDetail` 新增 `BigDecimal hourlyRate`。
- `ProcessDetailSaveReq` 新增可空 `BigDecimal hourlyRate`。
- `ProcessDetailDTO` 保留 `hourlyRate` 作为有效小时价格，新增：
  - `detailHourlyRate`：具体工艺本地维护价，可为空，可为 0。
  - `categoryHourlyRate`：所属大类小时价格。
  - `effectiveHourlyRate`：最终核价使用价格，等于 `hourlyRate`。

保留 `hourlyRate` 作为有效价，是为了让现有前端字典缓存、下拉选项和实时预估尽量少改。

## 6. 后端规则

### 6.1 统一取价

新增或集中一个明确方法，用于所有后端取价：

```java
BigDecimal effectiveHourlyRate(DictProcessDetail detail, DictProcessCategory category) {
    return detail.getHourlyRate() != null ? detail.getHourlyRate() : category.getHourlyRate();
}
```

注意：不能使用 `BigDecimal.ZERO`、`0.0`、空字符串判断来决定兜底。只有 `null` 表示缺失。

### 6.2 具体工艺保存

- `hourlyRate` 可为空。
- 非空时必须大于等于 0。
- MES 来源的具体工艺身份字段仍不可手工改，包括所属大类和具体工艺名称。
- MES 来源的具体工艺允许维护 QPO 本地 `hourlyRate`，因为它是本地核价覆盖价，不是 MES 身份字段。
- 审计动作继续使用 `DICT_PROCESS_DETAIL_UPDATE`，摘要中增加 `detailHourlyRate`、`categoryHourlyRate`、`effectiveHourlyRate`。

### 6.3 MES 同步

- MES 同步工艺大类时，继续同步大类小时价格到 `dict_process_category.hourly_rate`。
- MES 同步具体工艺时，不覆盖 `dict_process_detail.hourly_rate`。
- 如果 MES 同步新增具体工艺，新增行的 `hourly_rate` 默认为 `null`，表示继承大类价。
- 如果 MES 调整大类小时价格，只影响未维护具体价的具体工艺；已维护具体价的具体工艺不受影响。

### 6.4 核价保存

`ProcessRoutePricingService` 保存工艺路线时，按有效小时价格计算：

```text
工艺行金额 = 单件工时 × 有效小时价格
```

快照建议保留：

- `hourlyRate`：有效小时价格，兼容现有导出和展示。
- `unitRate`：有效小时价格，兼容旧字段。
- `detailHourlyRate`：具体工艺维护价，可为 `null` 或 `0`。
- `categoryHourlyRate`：大类小时价格。
- `hourlyRateSource`：`DETAIL` 或 `CATEGORY`。
- `lineCost`：按有效小时价格计算后的金额。

这样历史快照可以解释当时为什么用了某个价格，也不会破坏现有使用 `hourlyRate` 的展示和导出。

## 7. 前端规则

### 7.1 材料工艺管理页

具体工艺表格建议展示三列价格信息：

| 字段 | 展示 | 编辑 |
| --- | --- | --- |
| 具体小时价格 | `detailHourlyRate`，空时显示“继承大类” | 可编辑，可清空，可填 0 |
| 大类小时价格 | `categoryHourlyRate` | 只读 |
| 核价使用价格 | `effectiveHourlyRate` 或 `hourlyRate` | 只读 |

编辑弹窗中新增“具体小时价格”输入框：

- 可留空，表示继承大类。
- 可填 0，表示具体工艺 0 元/小时。
- 不允许负数。
- 建议增加提示：“留空继承大类小时价格；填 0 表示该具体工艺按 0 元/小时核价。”

### 7.2 字典缓存与核价弹窗

- `dictionaryStore.processDetails[].hourlyRate` 继续作为有效小时价格。
- 下拉标签继续显示核价使用价格，可追加“继承大类”或“具体价”标识。
- 普通件实时价格预估继续按 `inputVal × hourlyRate`，由于 `hourlyRate` 已是有效价，不需要在前端重新查大类兜底。
- 打开同一订单内下一个零件时继续复用 Pinia 字典缓存；具体工艺价格变更后，材料工艺管理保存成功调用现有强制刷新能力。

## 8. 价格链路一致性

需要统一以下链路的有效小时价格口径：

1. 材料工艺管理具体工艺列表和编辑弹窗。
2. `dictionaryStore` 字典缓存。
3. 全局工艺模板和逐件核价工艺选择。
4. 普通件实时价格预估。
5. 后端普通件保存核价。
6. 工艺路线快照复制、已提零件展示和提单表据展示。
7. 报价模板导出和报价单导出中显示的小时价格和工艺金额。

后端保存核价是最终权威计算，前端实时预估只是当前缓存下的预览，但两者应在字典未变化时保持一致。

## 9. 权限与日志

本需求不新增权限点：

- 查看继续复用 `engineer:dict:view`。
- 维护具体工艺价格继续复用 `engineer:dict:process-manage`。
- 不新增可分配 `common:*` 权限。

日志口径：

- 具体工艺价格新增、修改、清空走现有 `DICT_PROCESS_DETAIL_CREATE` / `DICT_PROCESS_DETAIL_UPDATE`。
- 审计摘要只记录价格字段的简要值，不记录批量快照、完整核价 JSON 或高频读取日志。
- 核价保存本身继续沿用现有核价保存日志，不因读取有效工艺价格新增噪声日志。

## 10. 历史数据与兼容

- 迁移后所有历史具体工艺 `hourly_rate` 为 `null`，自动继承大类价，历史行为不变。
- 历史核价快照中的 `hourlyRate` 仍按快照值展示，不回溯重算。
- 重复件复用、销售订单导入自动补全、提单和已提零件快照继续复制历史快照，不因为当前字典价格变化重写历史快照。
- 只有新保存核价或重新保存的工艺路线才使用新的有效小时价格规则。

## 11. 测试计划

后续实现至少补充以下测试：

### 后端

- Flyway 迁移包含 `dict_process_detail.hourly_rate`，且不修改 V1/V2 baseline。
- `ProcessMasterService` 返回具体工艺 DTO 时：
  - 具体价为空，`hourlyRate/effectiveHourlyRate` 等于大类价。
  - 具体价为 0，`hourlyRate/effectiveHourlyRate` 等于 0。
  - 具体价为正数，优先使用具体价。
- 保存具体工艺允许空和 0，拒绝负数。
- MES 同步更新具体工艺时不覆盖已有 `hourly_rate`。
- `ProcessRoutePricingService` 使用有效小时价格计算 `lineCost`，并在快照写入 `detailHourlyRate/categoryHourlyRate/hourlyRateSource`。

### 前端

- 材料工艺管理页展示具体小时价格、大类小时价格、核价使用价格。
- 编辑具体工艺时允许清空价格、允许 0、拒绝负数。
- `dictionaryStore` 下拉仍使用有效 `hourlyRate`。
- 普通件实时预估对具体价、0 价、继承大类价都计算正确。

### 回归

- 已有工艺模板、重复件复用、报价导出、提单表据展示继续读取旧 `hourlyRate` 快照。
- MES 主数据同步测试继续通过，证明具体工艺本地价不被同步覆盖。

## 12. 具体实施计划

> 执行约束：实施时按 TDD 推进，每个阶段先补失败测试，再写实现，再运行该阶段验证命令。所有迁移只追加新版本，禁止修改 V1/V2 baseline 和任何已经执行过的历史迁移。实现过程不新增权限点，不新增高频读取日志。

### 阶段 1：数据模型与后端有效价

**目标：** 让后端具备具体工艺小时价格字段和统一有效价口径，但先不改前端页面。

**修改文件：**

- 新增：`quote-backend/src/main/resources/db/migration/V16__process_detail_hourly_rate.sql`
- 修改：`quote-backend/src/main/java/com/machining/quote/entity/DictProcessDetail.java`
- 修改：`quote-backend/src/main/java/com/machining/quote/dto/ProcessDetailDTO.java`
- 修改：`quote-backend/src/main/java/com/machining/quote/dto/ProcessDetailSaveReq.java`
- 修改：`quote-backend/src/main/java/com/machining/quote/service/ProcessMasterService.java`
- 测试：`quote-backend/src/test/java/com/machining/quote/ProcessMasterBackendServiceTest.java`
- 测试：`quote-backend/src/test/java/com/machining/quote/ProcessMasterMigrationTest.java`
- 测试：`quote-backend/src/test/java/com/machining/quote/ProcessRouteBaselineStage1CharacterizationTest.java`

**接口约定：**

- `DictProcessDetail.hourlyRate`：具体工艺本地维护价，可为 `null`，可为 `0`。
- `ProcessDetailDTO.hourlyRate`：对前端兼容的有效小时价格。
- `ProcessDetailDTO.detailHourlyRate`：具体工艺本地维护价。
- `ProcessDetailDTO.categoryHourlyRate`：大类小时价格。
- `ProcessDetailDTO.effectiveHourlyRate`：最终核价使用价格，等于 `hourlyRate`。
- `ProcessDetailSaveReq.hourlyRate`：具体工艺本地维护价，空值表示继承大类。

**实施步骤：**

- [ ] 在 `ProcessMasterBackendServiceTest.entitiesMappersDtosAndAuditLabelsExistForTwoLevelProcessMaster` 中先增加断言，要求 `DictProcessDetail`、`ProcessDetailDTO`、`ProcessDetailSaveReq` 出现具体工艺价格字段。

```java
assertThat(read("entity/DictProcessDetail.java"))
        .contains("private BigDecimal hourlyRate;");
assertThat(read("dto/ProcessDetailSaveReq.java"))
        .contains("private BigDecimal hourlyRate;");
assertThat(read("dto/ProcessDetailDTO.java"))
        .contains(
                "private BigDecimal hourlyRate;",
                "private BigDecimal detailHourlyRate;",
                "private BigDecimal categoryHourlyRate;",
                "private BigDecimal effectiveHourlyRate;");
```

- [ ] 新增后端服务测试，覆盖空值继承、`0` 有效、正数覆盖。

```java
@Test
void detailHourlyRateOverridesCategoryRateOnlyWhenNonNullAndZeroIsValid() {
    DictProcessCategoryMapper categoryMapper = mock(DictProcessCategoryMapper.class);
    DictProcessDetailMapper detailMapper = mock(DictProcessDetailMapper.class);
    ProcessMasterService service = service(categoryMapper, detailMapper, mock(AuditService.class));
    DictProcessCategory category = category(7L, true);
    category.setHourlyRate(new BigDecimal("80.00"));
    when(categoryMapper.selectById(7L)).thenReturn(category);

    DictProcessDetail inherited = detail(1L, 7L, null);
    when(detailMapper.selectList(any(QueryWrapper.class))).thenReturn(List.of(inherited));
    assertThat(service.listDetails(null, null).get(0).getHourlyRate()).isEqualByComparingTo("80.00");
    assertThat(service.listDetails(null, null).get(0).getEffectiveHourlyRate()).isEqualByComparingTo("80.00");
    assertThat(service.listDetails(null, null).get(0).getCategoryHourlyRate()).isEqualByComparingTo("80.00");
    assertThat(service.listDetails(null, null).get(0).getDetailHourlyRate()).isNull();

    DictProcessDetail zero = detail(2L, 7L, new BigDecimal("0.00"));
    when(detailMapper.selectList(any(QueryWrapper.class))).thenReturn(List.of(zero));
    assertThat(service.listDetails(null, null).get(0).getHourlyRate()).isEqualByComparingTo("0.00");

    DictProcessDetail custom = detail(3L, 7L, new BigDecimal("125.50"));
    when(detailMapper.selectList(any(QueryWrapper.class))).thenReturn(List.of(custom));
    assertThat(service.listDetails(null, null).get(0).getHourlyRate()).isEqualByComparingTo("125.50");
}
```

- [ ] 新增保存校验测试，允许空值和 `0`，拒绝负数；MES 来源具体工艺允许维护本地 `hourlyRate`，但仍禁止改身份字段。

```java
@Test
void saveDetailAllowsNullAndZeroHourlyRateButRejectsNegativeRate() {
    DictProcessCategoryMapper categoryMapper = mock(DictProcessCategoryMapper.class);
    DictProcessDetailMapper detailMapper = mock(DictProcessDetailMapper.class);
    ProcessMasterService service = service(categoryMapper, detailMapper, mock(AuditService.class));
    when(categoryMapper.selectById(7L)).thenReturn(category(7L, true));
    when(detailMapper.selectCount(any(QueryWrapper.class))).thenReturn(0L);

    ProcessDetailSaveReq inherited = validDetailReq();
    inherited.setHourlyRate(null);
    assertThat(service.saveDetail(null, inherited).getDetailHourlyRate()).isNull();

    ProcessDetailSaveReq zero = validDetailReq();
    zero.setHourlyRate(BigDecimal.ZERO);
    assertThat(service.saveDetail(null, zero).getHourlyRate()).isEqualByComparingTo("0");

    ProcessDetailSaveReq negative = validDetailReq();
    negative.setHourlyRate(new BigDecimal("-0.01"));
    assertThatThrownBy(() -> service.saveDetail(null, negative))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("具体小时价格");
}
```

- [ ] 新增 `V16__process_detail_hourly_rate.sql`，只追加 `dict_process_detail.hourly_rate NUMERIC(18, 6)` 和字段注释。

```sql
ALTER TABLE dict_process_detail
    ADD COLUMN IF NOT EXISTS hourly_rate NUMERIC(18, 6);

COMMENT ON COLUMN dict_process_detail.hourly_rate IS '具体工艺小时价格；为空时继承工艺大类小时价格，0 表示明确为 0 元/小时';
```

- [ ] 更新实体和 DTO，所有新增 `BigDecimal` 字段使用 Java 驼峰名 `hourlyRate/detailHourlyRate/categoryHourlyRate/effectiveHourlyRate`。
- [ ] 在 `ProcessMasterService` 中集中有效价方法，且只用 `null` 判断缺失。

```java
private BigDecimal effectiveHourlyRate(DictProcessDetail detail, DictProcessCategory category) {
    BigDecimal detailRate = detail == null ? null : detail.getHourlyRate();
    if (detailRate != null) {
        return detailRate;
    }
    return category == null ? BigDecimal.ZERO : Objects.requireNonNullElse(category.getHourlyRate(), BigDecimal.ZERO);
}
```

- [ ] 在 `normalizeDetail(...)` 中读取 `req.getHourlyRate()`，非空且 `< 0` 时抛出 `IllegalArgumentException("具体小时价格不能小于 0")`，空值直接保存为 `null`。
- [ ] 在 `toDetailDTO(...)` 中设置 `detailHourlyRate/categoryHourlyRate/effectiveHourlyRate/hourlyRate`，其中 `hourlyRate` 必须等于有效小时价格。
- [ ] 在 `detailAuditSummary(...)` 中加入 `detailHourlyRate/categoryHourlyRate/effectiveHourlyRate`，不加入完整备注或快照 JSON。

**验证命令：**

```powershell
cd quote-backend
.\mvnw.cmd -q "-Dtest=ProcessMasterBackendServiceTest,ProcessMasterMigrationTest,ProcessRouteBaselineStage1CharacterizationTest" test
```

**提交建议：**

```powershell
git add quote-backend/src/main/resources/db/migration/V16__process_detail_hourly_rate.sql quote-backend/src/main/java/com/machining/quote/entity/DictProcessDetail.java quote-backend/src/main/java/com/machining/quote/dto/ProcessDetailDTO.java quote-backend/src/main/java/com/machining/quote/dto/ProcessDetailSaveReq.java quote-backend/src/main/java/com/machining/quote/service/ProcessMasterService.java quote-backend/src/test/java/com/machining/quote/ProcessMasterBackendServiceTest.java quote-backend/src/test/java/com/machining/quote/ProcessMasterMigrationTest.java quote-backend/src/test/java/com/machining/quote/ProcessRouteBaselineStage1CharacterizationTest.java
git commit -m "feat: 增加具体工艺小时价格字段"
```

### 阶段 2：MES 同步不覆盖具体价

**目标：** MES 同步继续维护工艺大类小时价格和具体工艺身份，但不覆盖 QPO 本地维护的具体工艺价格。

**修改文件：**

- 修改：`quote-backend/src/main/java/com/machining/quote/service/mes/MesMasterDataSyncService.java`
- 测试：`quote-backend/src/test/java/com/machining/quote/MesProcessTypeSyncServiceTest.java`
- 测试：`quote-backend/src/test/java/com/machining/quote/ProcessMasterBackendServiceTest.java`

**实施步骤：**

- [ ] 先写 MES 同步回归测试：已有 `DictProcessDetail.hourlyRate = 120.00`，MES 返回同一个具体工艺后，同步结果仍保留 `120.00`。

```java
assertThat(detailMapper.selectById(existingDetailId).getHourlyRate())
        .isEqualByComparingTo("120.00");
```

- [ ] 检查 `MesMasterDataSyncService` 中创建或更新 `DictProcessDetail` 的逻辑。新增具体工艺时不设置 `hourlyRate`，保持 `null`；更新已有具体工艺时不调用 `setHourlyRate(...)`。
- [ ] 保留大类价格同步逻辑：`category.setHourlyRate(source.hourlyRate())` 不变。
- [ ] 保留 MES 来源具体工艺身份不可手工修改规则；该规则不拦截本地 `hourlyRate` 保存。

**验证命令：**

```powershell
cd quote-backend
.\mvnw.cmd -q "-Dtest=MesMasterDataSync*Test,ProcessMasterBackendServiceTest" test
```

**提交建议：**

```powershell
git add quote-backend/src/main/java/com/machining/quote/service/mes/MesMasterDataSyncService.java quote-backend/src/test/java/com/machining/quote/*MesMasterDataSync*Test.java quote-backend/src/test/java/com/machining/quote/ProcessMasterBackendServiceTest.java
git commit -m "fix: MES工艺同步保留具体工艺本地价格"
```

### 阶段 3：核价保存和快照使用有效价

**目标：** 后端普通件保存核价时使用具体工艺有效小时价格，并把价格来源写入工艺快照。

**修改文件：**

- 修改：`quote-backend/src/main/java/com/machining/quote/service/ProcessRoutePricingService.java`
- 测试：`quote-backend/src/test/java/com/machining/quote/ProcessRoutePricingStage6Test.java`
- 测试：`quote-backend/src/test/java/com/machining/quote/EngineerServiceProcessSortTests.java`
- 回归：`QuoteTemplateExportServiceTests` 和 `QuoteExportProcessMergeTests` 必须继续通过；如果新增快照字段改变断言对象，更新断言以确认导出仍读取 `hourlyRate/unitRate` 快照值，不实时查当前字典价。

**快照字段：**

```java
snapshot.put("hourlyRate", hourlyRate);
snapshot.put("unitRate", hourlyRate);
snapshot.put("detailHourlyRate", detail.getHourlyRate());
snapshot.put("categoryHourlyRate", number(category.getHourlyRate()));
snapshot.put("hourlyRateSource", detail.getHourlyRate() == null ? "CATEGORY" : "DETAIL");
snapshot.put("lineCost", lineCost);
```

**实施步骤：**

- [ ] 将 `ProcessRoutePricingStage6Test.pricesProcessRowsFromCategoryHourlyRateAndIgnoresSubmittedPriceSnapshots` 改名为 `pricesProcessRowsFromEffectiveHourlyRateAndIgnoresSubmittedPriceSnapshots`，新增三段断言：空具体价继承大类价、`0` 具体价覆盖大类价、正数具体价覆盖大类价。
- [ ] 在测试中断言快照包含 `detailHourlyRate/categoryHourlyRate/hourlyRateSource`。

```java
assertThat(route).containsEntry("hourlyRateSource", "DETAIL");
assertThat((Double) route.get("hourlyRate")).isEqualTo(0.0);
assertThat((Double) route.get("lineCost")).isEqualTo(0.0);
```

- [ ] 在 `ProcessRoutePricingService` 中新增私有有效价 helper，避免复制 `ProcessMasterService` 的 DTO 逻辑。

```java
private BigDecimal effectiveHourlyRate(DictProcessDetail detail, DictProcessCategory category) {
    return detail.getHourlyRate() != null
            ? detail.getHourlyRate()
            : Objects.requireNonNullElse(category.getHourlyRate(), BigDecimal.ZERO);
}
```

- [ ] 将现有 `double hourlyRate = number(category.getHourlyRate());` 替换为有效价。
- [ ] 保留“提交参数里的 `unitRate/lineCost` 不可信，后端重新查字典计算”的安全边界。
- [ ] 确认 `QuoteTemplateExportService`、`QuoteProcessExportSupport`、`ExcelExportService` 继续读取 `hourlyRate/unitRate`，无需改成实时查字典。

**验证命令：**

```powershell
cd quote-backend
.\mvnw.cmd -q "-Dtest=ProcessRoutePricingStage6Test,EngineerServiceProcessSortTests,QuoteTemplateExportServiceTests,QuoteExportProcessMergeTests" test
```

**提交建议：**

```powershell
git add quote-backend/src/main/java/com/machining/quote/service/ProcessRoutePricingService.java quote-backend/src/test/java/com/machining/quote/ProcessRoutePricingStage6Test.java quote-backend/src/test/java/com/machining/quote/EngineerServiceProcessSortTests.java quote-backend/src/test/java/com/machining/quote/QuoteTemplateExportServiceTests.java quote-backend/src/test/java/com/machining/quote/QuoteExportProcessMergeTests.java
git commit -m "feat: 核价工艺按具体工艺有效价格计算"
```

### 阶段 4：前端具体工艺维护页和字典缓存

**目标：** 用户能在材料工艺模块维护具体小时价格，核价下拉和实时预估继续使用有效小时价格。

**修改文件：**

- 修改：`quote-frontend/src/components/MaterialProcessManage.vue`
- 修改：`quote-frontend/src/stores/dictionaryStore.js`
- 测试：`quote-frontend/src/process-master-management.workflow.test.js`
- 测试：`quote-frontend/src/stores/dictionaryStore.test.js`
- 测试：`quote-frontend/src/itemPricingRealtimePricePreview.test.js`

**展示口径：**

- 具体小时价格：`detailHourlyRate`，空时显示“继承大类”。
- 大类小时价格：`categoryHourlyRate`，只读。
- 核价使用价格：`effectiveHourlyRate` 或 `hourlyRate`，只读。

**实施步骤：**

- [ ] 先改 `process-master-management.workflow.test.js`，要求页面源码包含 `具体小时价格`、`大类小时价格`、`核价使用价格`、`detailHourlyRate`、`categoryHourlyRate`、`effectiveHourlyRate`。
- [ ] 修改 `dictionaryStore.test.js`，模拟后端返回如下具体工艺，并断言下拉 label 使用有效价 `0.00元/小时`，不是大类价。

```javascript
listProcessDetails.mockResolvedValue([
  { id: 2, categoryId: 10, processCode: 'CNC', categoryName: '数控', detailName: 'CNC1', detailHourlyRate: 0, categoryHourlyRate: 80, effectiveHourlyRate: 0, hourlyRate: 0 }
])
```

- [ ] 在 `MaterialProcessManage.vue` 具体工艺表格中，把现有“小时价格”列调整为“核价使用价格”，并新增“具体小时价格”“大类小时价格”列。
- [ ] 在编辑具体工艺弹窗中新增 `el-input-number` 或可清空数值输入，绑定 `processDetailForm.hourlyRate`，配置 `:min="0"`，并提供留空继承提示。
- [ ] 打开编辑弹窗时设置：

```javascript
processDetailForm.hourlyRate = row.detailHourlyRate ?? null
```

- [ ] 保存时发送 `hourlyRate`；用户清空输入时传 `null`，用户填 `0` 时传数字 `0`。
- [ ] `dictionaryStore.combinedProcessOptions` 继续使用 `item.hourlyRate` 作为有效价；可在 label 中追加来源：`item.detailHourlyRate == null ? '继承大类' : '具体价'`。
- [ ] `ItemPricing.vue` 的普通件实时预估继续使用 `option?.hourlyRate ?? proc.hourlyRate ?? proc.unitRate`，不在前端重新查大类兜底。

**验证命令：**

```powershell
cd quote-frontend
npm test -- process-master-management.workflow.test.js stores/dictionaryStore.test.js itemPricingRealtimePricePreview.test.js processRouteStage5Frontend.test.js
```

**提交建议：**

```powershell
git add quote-frontend/src/components/MaterialProcessManage.vue quote-frontend/src/stores/dictionaryStore.js quote-frontend/src/process-master-management.workflow.test.js quote-frontend/src/stores/dictionaryStore.test.js quote-frontend/src/itemPricingRealtimePricePreview.test.js quote-frontend/src/processRouteStage5Frontend.test.js
git commit -m "feat: 前端支持维护具体工艺小时价格"
```

### 阶段 5：端到端回归和文档收口

**目标：** 确认具体价、`0`、继承大类价在前后端和导出链路一致，且权限和日志口径没有偏移。

**修改文件：**

- 修改：`docs/QPO权限用户体系/02-权限点清单与现有角色映射.md`
- 修改：`docs/QPO日志模块/03-日志运维说明.md`
- 测试：`quote-backend/src/test/java/com/machining/quote/ProcessMasterDocumentationCharacterizationTest.java`
- 测试：`quote-frontend/src/processRouteStage15Documentation.test.js`

**实施步骤：**

- [ ] 权限文档补充一句：具体工艺小时价格维护继续复用 `engineer:dict:process-manage`，不新增权限点。
- [ ] 日志文档补充一句：具体工艺价格保存走 `DICT_PROCESS_DETAIL_UPDATE`，读取和实时预估不记高频日志。
- [ ] 文档 characterization 测试断言上述两句存在。
- [ ] 运行后端工艺、核价、MES 主数据同步相关测试组合。
- [ ] 运行前端工艺字典、核价实时预估相关测试组合。
- [ ] 检查 `git diff --check`，确认没有空白错误。

**整体验证命令：**

```powershell
cd quote-backend
.\mvnw.cmd -q "-Dtest=ProcessMasterBackendServiceTest,ProcessMasterMigrationTest,ProcessRoutePricingStage6Test,EngineerServiceProcessSortTests,MesMasterDataSync*Test,QuoteTemplateExportServiceTests,QuoteExportProcessMergeTests,ProcessMasterDocumentationCharacterizationTest" test

cd ..\quote-frontend
npm test -- process-master-management.workflow.test.js stores/dictionaryStore.test.js itemPricingRealtimePricePreview.test.js processRouteStage5Frontend.test.js processRouteStage15Documentation.test.js

cd ..
git diff --check
```

**提交建议：**

```powershell
git add docs/QPO权限用户体系/02-权限点清单与现有角色映射.md docs/QPO日志模块/03-日志运维说明.md quote-backend/src/test/java/com/machining/quote/ProcessMasterDocumentationCharacterizationTest.java quote-frontend/src/processRouteStage15Documentation.test.js
git commit -m "docs: 收口具体工艺价格权限日志口径"
```

## 13. 验收标准

1. 具体工艺未维护价格时，核价仍按大类小时价格计算。
2. 具体工艺维护为 0 时，核价按 0 元/小时计算，不兜底大类价。
3. 具体工艺维护为正数时，核价按具体工艺价格计算。
4. MES 同步后，已维护的具体工艺价格不被覆盖。
5. 前端实时预估和后端保存结果在同一字典版本下保持一致。
6. 历史快照不被重算，已提单和报价导出继续稳定展示历史价格。
7. 不新增权限点，不新增高频读取日志。
