# MES 工艺/材料主数据同步说明

> 2026-08-13 MES 主数据同步阶段 9 收口：QPO 通过数据中心只读获取 MES 工艺类型和材料分页数据，同步到现有材料工艺字典。QPO 不删除材料费用、材料类型、工艺大类、具体工艺四个页签，也不删除 `dict_material`、`dict_material_type`、`dict_process_category`、`dict_process_detail` 四张字典表。

## 1. 总体口径

MES 主数据同步用于让 QPO 报价和后续 MES 下发使用 MES 编码体系，但 QPO 仍然保留本地核价治理能力：

- MES 工艺类型进入 QPO 工艺大类；MES `code` 写入 QPO `processCode`，MES `name` 写入 QPO `categoryName`，MES `hourPrice` 写入 QPO 小时单价。
- QPO 为 MES 工艺大类保留或创建默认具体工艺；下发 MES 生产时 `detailName` 转为 `processName`。
- MES 材料进入 QPO 材料字典；MES `code` 写入 QPO `materialCode` / 后续报价快照 `engineeringMaterialCode`。
- MES 无材料单价，QPO 继续维护材料单价。材料单价缺失时禁止完成核价，不能按 0 元/kg 静默计算。
- MES 来源字段只维护主数据身份、类型、密度和追踪信息；QPO 本地继续维护价格、必要例外、排序和备注。

## 2. 数据中心 endpoint

QPO 后端通过数据中心获取 MES 主数据，两个 endpoint 都是只读 GET，`targetSystem = MES`，timeout 不低于 `60000ms`。

| endpointCode | 方法 | 默认 path | 用途 |
| --- | --- | --- | --- |
| `MES_PROCESS_TYPE_LIST` | GET | `/api/v1/mes/openapi/process-type/list` | 获取 MES 工艺类型列表。 |
| `MES_MATERIAL_PAGE` | GET | `/api/v1/mes/openapi/material/page` | 分页获取 MES 材料主数据。 |

fallback 环境变量：

```env
ERP_DATA_CENTER_MES_PROCESS_TYPE_LIST_PATH=/api/v1/mes/openapi/process-type/list
ERP_DATA_CENTER_MES_MATERIAL_PAGE_PATH=/api/v1/mes/openapi/material/page
```

材料分页固定 `pageSize=200`，首轮请求为 `pageNo=1&pageSize=200`；同步不传 `materialType` 过滤，确保 `MATERIAL / PRODUCT / SEMI` 等 MES 全部材料类型进入 QPO 字典。

## 3. QPO 手动同步入口

用户入口位于 **工程 > 材料工艺**：

- 材料费用页签：按钮“从 MES 同步材料”。
- 工艺大类页签：按钮“从 MES 同步工艺”。
- 同步确认弹窗提示：MES 会更新基础信息，不覆盖 QPO 材料单价。

后端 API：

| API | 权限 | 审计动作 |
| --- | --- | --- |
| `POST /api/dict/process-categories/sync-mes` | `engineer:dict:process-manage` | `DICT_PROCESS_MES_SYNC` |
| `POST /api/dict/materials/sync-mes` | `engineer:dict:material-manage` | `DICT_MATERIAL_MES_SYNC` |

同步运行记录写入 `mes_master_data_sync_run`，记录 `syncRunId`、同步类型、状态、抓取数、有效数、无效数、重复数、upsert 数、停用数、执行人和脱敏消息。

## 4. 字段映射

### 4.1 工艺

| MES 字段 | QPO 字段 | 说明 |
| --- | --- | --- |
| `id` | `dict_process_category.mes_external_id` | MES 外部主键。 |
| `code` | `dict_process_category.process_code` | 后续 MES 下发 `processTypeCode`。 |
| `name` | `dict_process_category.category_name` | 工艺大类名称；默认具体工艺 `detailName` 也使用该名称。 |
| `hourPrice` | `dict_process_category.hourly_rate` | QPO 工时核价小时价。 |
| 当前时间 | `dict_process_category.mes_synced_at` | 最近同步时间。 |

### 4.2 材料

| MES 字段 | QPO 字段 | 说明 |
| --- | --- | --- |
| `id` | `dict_material.mes_external_id` | MES 外部主键。 |
| `code` | `dict_material.material_code` | 后续报价快照 `engineeringMaterialCode`，MES BOM `itemCode`。 |
| `name` | `dict_material.material_name` | 材料名称。 |
| `materialType` | `dict_material.mes_material_type` / `dict_material_type.type_code` | 自动确保材料类型存在。 |
| `standard` | `dict_material.mes_standard` | MES 规格。 |
| `docNum` | `dict_material.mes_doc_num` | MES 图号/资料号。 |
| `weight` | `dict_material.mes_weight` | MES 原始重量字段，仅作追踪。 |
| `density` | QPO 默认密度字段 + `dict_material.mes_density_raw` | QPO 可用于后续核价默认密度，保留 MES 原始密度。 |
| 当前时间 | `dict_material.mes_synced_at` | 最近同步时间。 |

## 5. MES 生产下发合同

主数据同步只影响 QPO 字典和报价快照，不改变 MES 生产下发 DTO 字段名称。下发 payload 仍满足：

- `processRoute[].processTypeCode = QPO processCode = MES code`
- `processRoute[].processName = QPO detailName`
- `bom[].itemCode = QPO engineeringMaterialCode = MES material code`
- `bom[].quantity = QPO materialGrossWeight kg`

因此，只要报价阶段选择的是 MES 同步后的材料编码和工艺编码，QPO 系统自己生成的提单、锁库和待产数据就会沿快照字段进入 MES 下发 payload。

## 6. 真实链路验收

普通回归可以执行真实数据中心读取 MES 工艺/材料并同步一轮；真实写 MES 仍使用独立显式开关，不在普通回归中自动执行。推荐顺序：

1. 检查数据中心连接配置和两个 GET endpoint 是否启用。
2. 手动触发“从 MES 同步工艺”和“从 MES 同步材料”。
3. 在材料工艺页面确认来源、MES 类型、同步时间和单价状态。
4. 对缺失材料单价的材料补齐 QPO 本地单价后再完成核价。
5. 如需真实写 MES，使用独立显式开关执行生产下发验收脚本。

安全要求：文档、操作日志和文件日志不记录完整请求、完整响应、token、Authorization、Cookie 或 client_secret。
