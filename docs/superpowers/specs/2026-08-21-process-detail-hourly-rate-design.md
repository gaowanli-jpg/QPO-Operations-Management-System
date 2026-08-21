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

## 12. 分阶段实施建议

### 阶段 1：数据模型与后端有效价

新增 V16 迁移、实体字段、DTO 字段、有效价 helper，补充后端单元测试。

### 阶段 2：具体工艺维护页

前端具体工艺列表和编辑弹窗支持具体小时价格维护，保存后刷新字典缓存。

### 阶段 3：核价与快照

后端核价保存改用有效小时价格，快照补充来源字段；前端实时预估验证与后端保存一致。

### 阶段 4：同步与回归收口

确认 MES 同步不覆盖具体价，跑工艺字典、零件核价、报价导出、MES 主数据同步相关回归。

## 13. 验收标准

1. 具体工艺未维护价格时，核价仍按大类小时价格计算。
2. 具体工艺维护为 0 时，核价按 0 元/小时计算，不兜底大类价。
3. 具体工艺维护为正数时，核价按具体工艺价格计算。
4. MES 同步后，已维护的具体工艺价格不被覆盖。
5. 前端实时预估和后端保存结果在同一字典版本下保持一致。
6. 历史快照不被重算，已提单和报价导出继续稳定展示历史价格。
7. 不新增权限点，不新增高频读取日志。
