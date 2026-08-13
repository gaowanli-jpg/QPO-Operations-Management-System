# MES 工艺与材料主数据同步开发设计

日期：2026-08-13

## 1. 背景

QPO 生产下发 MES 已经打通生产任务接口，并且当前下发链路已经明确使用以下字段：

- QPO `processCode` 转为 MES `processTypeCode`。
- QPO `detailName` 转为 MES `processName`。
- QPO `engineeringMaterialCode` 转为 MES BOM `itemCode`。
- QPO `materialGrossWeight` 转为 MES BOM `quantity`，两边口径均为 kg 毛重。

目前遗留问题是：QPO 的工时工艺和材料仍主要依赖本地字典维护。如果本地编码与 MES 编码不一致，即使脚本用伪造匹配数据能跑通，也不能证明 QPO 系统自己生成的数据一定能被 MES 接收。因此需要通过数据中心从 MES 拉取工艺、材料主数据，并同步到 QPO 本地字典，保证报价、锁定、下发使用同一套 MES 编码基础。

本设计只覆盖 MES 工艺与材料主数据同步、价格缺失拦截、UI/权限/日志配套，不修改生产任务下发字段口径。

## 2. 资料来源

- `D:/桌面/获取工艺接口.txt`
- `D:/桌面/获取材料接口.txt`
- 当前代码：
  - `quote-backend/src/main/java/com/machining/quote/service/DataCenterEndpointRegistry.java`
  - `quote-backend/src/main/java/com/machining/quote/service/DataCenterIntegrationClient.java`
  - `quote-backend/src/main/java/com/machining/quote/service/u9/U9MasterDataSyncService.java`
  - `quote-backend/src/main/java/com/machining/quote/service/EngineerService.java`
  - `quote-backend/src/main/java/com/machining/quote/service/mes/MesProductionDispatchService.java`
  - `quote-backend/src/main/java/com/machining/quote/entity/DictProcessCategory.java`
  - `quote-backend/src/main/java/com/machining/quote/entity/DictMaterial.java`

## 3. 目标

1. QPO 可以通过数据中心调用 MES 工艺类型列表接口，并把 MES 工艺类型同步到 QPO 工艺大类。
2. QPO 可以通过数据中心调用 MES 材料分页接口，并把 MES `MATERIAL / PRODUCT / SEMI` 全量同步到 QPO 材料字典。
3. MES 编码进入 QPO 字典后，报价生成的工艺、材料字段天然符合 MES 下发口径。
4. MES 不提供材料单价，QPO 继续维护核价单价；同步过程不得覆盖已有单价。
5. 材料单价缺失时，后端禁止完成核价，不允许按 0 元/kg 通过。
6. 同步动作可审计、可追踪、可失败恢复，且不记录 token、完整请求体或敏感响应体。

## 4. 非目标

- 不引入 APS 相关设计或文件。
- 不实时依赖 MES 查询来渲染报价页选择器。
- 不让 MES 材料接口承担价格主数据职责。
- 不改变 MES 生产任务下发接口字段定义。
- 不做材料价格自动推断或自动补价。
- 不删除本地已有材料或工艺；远端消失或禁用时只停用 MES 来源记录。

## 5. 方案选择

### 5.1 推荐方案：MES 主数据同步到 QPO 字典

同步 MES 工艺和材料到 QPO 已有字典表。QPO 前端选择器、报价计算、交付锁定、MES 下发继续读取本地字典和报价快照。

优点：

- 与现有 U9 客户主数据同步模式一致，可靠、可审计。
- 报价历史不受 MES 临时网络或数据中心波动影响。
- 下发 MES 时使用的 `processCode`、`materialCode` 已经来自 MES，避免伪造脚本数据通过但真实系统数据不通。
- 可以保留 QPO 本地价格管理，不把 MES 缺失单价误当成 0。

代价：

- 需要新增同步端点、字段和同步服务。
- 需要处理 MES 状态、分页、重复编码、远端停用等边界。

### 5.2 备选方案：MES 独立缓存 + QPO 字典映射

新增独立 MES 缓存表，QPO 字典通过映射关系引用 MES 编码。

优点是远端数据与本地业务配置隔离更强；缺点是映射成本高，用户仍要维护一层对应关系。当前目标是让 QPO 自己生成的数据尽快使用 MES 编码，本方案过重，不作为第一版。

### 5.3 不推荐方案：报价时实时查 MES

每次材料/工艺选择时实时调用 MES。

该方案会让报价页、核价完成、历史快照依赖外部实时可用性，且不能稳定解释历史报价依据，不符合 QPO 当前业务链路。

## 6. 总体架构

```text
MES
  -> 数据中心 token / endpoint
  -> DataCenterIntegrationClient
  -> MesMasterDataClient
  -> MesMasterDataSyncService
  -> QPO dict_process_category / dict_process_detail / dict_material / dict_material_type
  -> 报价选择、核价计算、MES 生产下发
```

新增后端模块：

- `DataCenterEndpointRegistry`：新增 MES 主数据 endpoint code。
- `DataCenterIntegrationClient`：增加 GET query 参数支持。
- `dto.mes.master`：新增 MES 工艺、材料接口响应 DTO。
- `service.mes.MesMasterDataClient`：封装数据中心调用和响应校验。
- `service.mes.MesMasterDataSyncService`：负责同步、落库、统计、审计。
- `controller`：在字典模块下暴露手动同步接口。

前端继续使用 `MaterialProcessManage.vue` 的材料工艺管理页，新增同步按钮与同步结果提示。

## 7. 数据中心端点设计

### 7.1 工艺类型列表

新增 endpoint code：`MES_PROCESS_TYPE_LIST`

- 目标系统：`MES`
- 方法：`GET`
- 默认路径：`/api/v1/mes/openapi/process-type/list`
- 请求适配器：`MES_PROCESS_TYPE_LIST_V1`
- 响应适配器：`MES_PROCESS_TYPE_LIST_RESPONSE_V1`
- 最小超时：`60000ms`
- 兼容环境变量：`ERP_DATA_CENTER_MES_PROCESS_TYPE_LIST_PATH`

MES 响应字段：`id`、`code`、`name`、`hourPrice`、`deptId`、`deptName`、`autoDispatch`、`autoReport`、`status`、`remark`、`createTime`、`updateTime`。

### 7.2 材料分页查询

新增 endpoint code：`MES_MATERIAL_PAGE`

- 目标系统：`MES`
- 方法：`GET`
- 默认路径：`/api/v1/mes/openapi/material/page`
- 请求适配器：`MES_MATERIAL_PAGE_V1`
- 响应适配器：`MES_MATERIAL_PAGE_RESPONSE_V1`
- 最小超时：`60000ms`
- 兼容环境变量：`ERP_DATA_CENTER_MES_MATERIAL_PAGE_PATH`

查询参数：

- `pageNo`：必填，从 1 开始。
- `pageSize`：必填，固定使用 200。
- `materialType`：第一版不传，拉取全部 `MATERIAL / PRODUCT / SEMI`。
- `productCode`、`purchaseType`、`docNum`、`createTime`、`updateTime`：第一版同步全量时不传，后续可用于增量或检索。

MES 响应字段：`id`、`name`、`code`、`categoryId`、`unitId`、`status`、`standard`、`docNum`、`materialType`、`attribute`、`purchaseType`、`productionType`、`version`、`drawingUrls`、`remark`、`weight`、`density`、`createTime`、`updateTime`。

## 8. 数据中心客户端变更

`DataCenterIntegrationClient` 当前 `GET` 调用只拼接 endpoint path，不支持 query 参数。材料分页接口必须新增显式 query 参数能力：

- 新增方法：`callGet(String endpointCode, Map<String, ?> queryParams, Class<T> responseType)`。
- 参数值按 UTF-8 URL 编码。
- 忽略 null 或空字符串参数。
- 日志只记录 endpointCode、targetSystemCode、host、path、status，不记录 token 和完整 query 值。
- 复用现有 token、timeout、endpoint 启用校验、错误脱敏逻辑。

工艺列表可用 `callGet(endpointCode, Map.of(), responseType)` 调用；材料分页通过 `pageNo/pageSize` 调用。

## 9. 工艺同步设计

### 9.1 落库策略

MES 工艺类型同步到 `dict_process_category`，同步键为 MES `code`。

字段映射：

| MES 字段 | QPO 字段 | 规则 |
| --- | --- | --- |
| `code` | `process_code` | trim 后原样保存，避免破坏 MES 大小写。 |
| `name` | `category_name` | 工艺大类名称。 |
| `hourPrice` | `hourly_rate` | 转 BigDecimal；缺失或非法时使用 0 并记录同步警告，不阻塞工艺同步。 |
| `remark` | `remarks` | 可附加部门、MES 状态等简要信息。 |
| `id` | `mes_external_id` | 远端主键。 |
| 固定值 | `source_type` | `MES`。 |
| 当前时间 | `mes_synced_at` | 同步时间。 |
| `status` | `enabled` | 采用明确启用映射；无法识别时默认启用但记录原始状态。 |

### 9.2 具体工艺策略

当前 MES 接口提供的是工艺类型，不提供更细的具体工艺列表；QPO 核价和下发又需要 `detailName -> processName`。

第一版策略：

- 每个 MES 工艺类型至少保证存在一个 `dict_process_detail`。
- 默认 `detail_name = MES name`。
- `category_id` 关联同步后的工艺大类。
- 若同类下已存在同名具体工艺，则保持并启用。
- 若用户已在该 MES 大类下手动维护更多具体工艺，不删除、不覆盖。

这样报价选择后：

- QPO `processCode = MES code`。
- QPO `detailName = MES name 或本地维护的具体工艺名`。
- MES 下发 `processTypeCode = processCode`，`processName = detailName`。

### 9.3 远端缺失或停用

- 本次同步未返回的 `source_type = MES` 工艺大类，默认不删除，标记为 disabled。
- 本次同步返回但状态表示停用的，标记 disabled。
- 非 MES 来源的本地工艺不受影响。
- 已被历史报价快照引用的工艺保持历史数据不变。

## 10. 材料同步设计

### 10.1 落库策略

MES 材料同步到 `dict_material`，同步键为 MES `code`。

字段映射：

| MES 字段 | QPO 字段 | 规则 |
| --- | --- | --- |
| `code` | `material_code` | trim 后原样保存，作为 MES BOM `itemCode` 来源。 |
| `name` | `material_name` | 材料名称。 |
| `materialType` | `dict_material_type.type_code` | 全量同步 `MATERIAL / PRODUCT / SEMI`，缺少类型时归入 `MES_UNKNOWN`。 |
| `materialType` | `dict_material_type.type_name` | `MATERIAL=原材料`，`PRODUCT=成品`，`SEMI=半成品`，未知值保存原值。 |
| `categoryId` / `standard` / `attribute` | `material_category` | 第一版优先 `standard`，其次 `attribute`，再其次 `categoryId`。 |
| `density` | `density` | 转 Double；单位按 QPO 当前 `kg/m3` 处理，同时保留 MES 原始值字段。 |
| `density` | `plate_density/bar_density/tube_density` | 仅当对应形态密度为空时初始化为 MES density；已有人工维护值不覆盖。 |
| `id` | `mes_external_id` | 新增字段，保存 MES 主键。 |
| `materialType` | `mes_material_type` | 新增字段，保存 MES 原始材料类型。 |
| `purchaseType` | `mes_purchase_type` | 新增字段。 |
| `productionType` | `mes_production_type` | 新增字段。 |
| `docNum` | `mes_doc_num` | 新增字段。 |
| `standard` | `mes_standard` | 新增字段。 |
| `weight` | `mes_weight` | 新增字段，保存 MES 原始重量字段。 |
| `density` | `mes_density_raw` | 新增字段，保存 MES 原始密度字符串。 |
| 固定值 | `source_type` | `MES`。 |
| 当前时间 | `mes_synced_at` | 同步时间。 |
| `status` | `enabled` | 采用明确启用映射；无法识别时默认启用但记录原始状态。 |

### 10.2 单价策略

MES 材料接口不提供单价。QPO 当前材料核价依赖：

- `plate_price`
- `bar_price`
- `tube_price`

同步规则：

- 已存在材料：绝不覆盖 `plate_price/bar_price/tube_price`。
- 新增材料：单价字段保持 null，不写 0。
- 前端展示“单价未维护”状态，提醒工程维护价格。
- 后端核价时强制校验所选形态价格，缺失则禁止完成核价。

### 10.3 密度策略

MES 材料接口存在 `density` 字段，但文档未明确单位。第一版处理：

- 按 QPO 当前密度单位 `kg/m3` 存入 `density`。
- 同时保存 `mes_density_raw`，用于追溯和后续单位换算。
- 如果 MES 返回空密度，不阻塞同步；报价时若需要体积转重量且密度缺失，应沿用现有缺失数据提示机制。
- 如果 QPO 已人工维护板/棒/管密度，不被 MES 同步覆盖。

## 11. 核价完成校验

当前 `EngineerService.resolveMaterialPrice` 在单价为空时返回 `0.0`，这会导致 MES 同步新增材料在未维护价格时仍能完成核价。

必须调整为：

- 对普通材料核价，选中材料后根据 `shape` 解析对应价格字段。
- 板材缺 `platePrice`、棒材缺 `barPrice`、管材缺 `tubePrice` 时，抛出业务异常。
- 错误信息包含材料编码、材料名称、材料形态，便于用户维护价格。
- 价格为 0 也视为未维护，不允许完成核价。
- 非材料行、非工时费用、已有独立费用逻辑不受影响。

建议错误文案：

```text
材料单价未维护：材料编码 {materialCode}，材料名称 {materialName}，形态 {shape}，请先维护材料单价后再完成核价。
```

该规则只影响完成核价/保存核价计算结果，不阻止 MES 材料同步入库。

## 12. 同步服务设计

新增 `MesMasterDataSyncService`，提供：

- `syncProcessTypesManual(Long userId)`
- `syncMaterialsManual(Long userId)`

同步流程：

1. 创建同步运行记录，状态 `RUNNING`。
2. 通过 `MesMasterDataClient` 调用数据中心。
3. 过滤无效行：缺少 `code` 或 `name` 的行跳过并计入 invalidCount。
4. 按 `code` 去重；重复 code 取第一条，后续重复计入 duplicateCount。
5. Upsert 本地字典。
6. 对 MES 来源但本次未返回的记录执行停用。
7. 更新同步运行记录为 `SUCCESS` 或 `FAILED`。
8. 写操作日志和后端文件日志摘要。

同步结果字段：

- `syncRunId`
- `syncType`：`MES_PROCESS_TYPE` 或 `MES_MATERIAL`
- `status`
- `fetchedCount`
- `validCount`
- `invalidCount`
- `duplicateCount`
- `upsertedCount`
- `disabledCount`
- `message`
- `startedAt`
- `finishedAt`

## 13. 同步运行记录表

新增表：`mes_master_data_sync_run`

字段建议：

```text
id bigint primary key auto increment
sync_type varchar(40) not null
status varchar(20) not null
fetched_count int not null default 0
valid_count int not null default 0
invalid_count int not null default 0
duplicate_count int not null default 0
upserted_count int not null default 0
disabled_count int not null default 0
message varchar(1000)
started_at datetime not null
finished_at datetime
created_by bigint
created_at datetime not null
```

不复用 `U9MasterDataSyncRun`，避免 MES 主数据长期挂在 U9 命名下。

## 14. 字典表结构变更

### 14.1 工艺表

`dict_process_category` 已具备以下字段，可直接复用：

- `source_type`
- `mes_external_id`
- `mes_synced_at`

无需新增工艺字段。

### 14.2 材料表

`dict_material` 当前缺少 MES 来源和同步追踪字段。新增字段：

```text
source_type varchar(30) default 'LOCAL'
mes_external_id varchar(120)
mes_material_type varchar(40)
mes_purchase_type varchar(60)
mes_production_type varchar(60)
mes_doc_num varchar(120)
mes_standard varchar(255)
mes_weight varchar(80)
mes_density_raw varchar(80)
mes_synced_at datetime
```

索引建议：

- `idx_dict_material_source_type`
- `idx_dict_material_mes_external_id`
- 保持现有 `material_code` 唯一/查重逻辑。

## 15. 后端接口设计

在现有字典模块下新增手动同步接口：

### 15.1 同步 MES 工艺

```text
POST /api/dict/process-categories/sync-mes
Permission: engineer:dict:process-manage
Audit action: DICT_PROCESS_MES_SYNC
```

返回：

```json
{
  "success": true,
  "message": "MES工艺同步完成",
  "data": {
    "syncRunId": 1,
    "fetchedCount": 20,
    "validCount": 20,
    "upsertedCount": 18,
    "disabledCount": 2,
    "invalidCount": 0,
    "duplicateCount": 0
  }
}
```

### 15.2 同步 MES 材料

```text
POST /api/dict/materials/sync-mes
Permission: engineer:dict:material-manage
Audit action: DICT_MATERIAL_MES_SYNC
```

返回结构与工艺同步一致。

### 15.3 同步记录查询

第一版可不新增独立页面；如前端需要展示最近一次同步结果，可新增：

```text
GET /api/dict/mes-sync-runs/latest?syncType=MES_MATERIAL
Permission: engineer:dict:view
```

如果只在同步完成后 toast 返回统计，本接口可延后。

## 16. 前端设计

在 `quote-frontend/src/components/MaterialProcessManage.vue`：

- 材料页签新增按钮：`从 MES 同步材料`。
- 工艺大类页签新增按钮：`从 MES 同步工艺`。
- 按钮权限：
  - 材料：`engineer:dict:material-manage`
  - 工艺：`engineer:dict:process-manage`
- 点击后展示确认弹窗，说明：MES 会更新基础信息，不覆盖 QPO 材料单价。
- 成功后刷新对应列表，并展示 fetched/upserted/disabled/invalid/duplicate 统计。
- 材料列表增加来源、MES 类型、同步时间、单价状态展示。
- 工艺大类列表已有来源和同步时间字段时直接展示；若前端未展示，则补列。

单价状态：

- 任一形态价格缺失时显示“单价未维护”。
- 价格完整时显示“已维护”。
- 展示只做提示，真正拦截在后端核价完成逻辑。

## 17. 权限与审计

权限复用现有工程字典权限，不新增 `common:*`：

- `engineer:dict:process-manage`：同步 MES 工艺。
- `engineer:dict:material-manage`：同步 MES 材料。
- `engineer:dict:view`：查看同步后的字典。

新增审计动作：

- `DICT_PROCESS_MES_SYNC`：同步 MES 工艺主数据。
- `DICT_MATERIAL_MES_SYNC`：同步 MES 材料主数据。

审计摘要只记录：

- `syncRunId`
- `syncType`
- `fetchedCount`
- `validCount`
- `invalidCount`
- `duplicateCount`
- `upsertedCount`
- `disabledCount`
- `success`

不记录：

- access token
- client secret
- 完整 MES 响应体
- 完整数据中心请求体

## 18. 错误处理

### 18.1 数据中心错误

- token 获取失败：同步失败，记录脱敏错误。
- endpoint 未配置或禁用：同步失败，提示配置问题。
- HTTP 非 2xx：同步失败，记录 status 和 endpointCode。
- 网络超时：同步失败，提示数据中心或 MES 网络异常。

### 18.2 MES 业务错误

- `code` 非成功值：同步失败，记录 MES `msg` 的脱敏摘要。
- 工艺返回空列表：同步失败，避免误停用全部工艺。
- 材料第一页返回空且 total 为空或 0：同步失败，避免误停用全部材料。
- 单页数据异常：跳过非法行并继续，最终统计 invalidCount。

### 18.3 本地落库错误

- 同步操作整体事务控制；任一批次落库失败时回滚该次同步。
- 同步运行记录写入失败时记录后端错误日志，不继续伪装成功。
- 重复 code 只处理第一条，避免同一次同步内互相覆盖。

## 19. 状态映射

MES `status` 文档未列枚举。第一版使用兼容映射：

- 启用：`1`、`true`、`TRUE`、`ENABLE`、`ENABLED`、`ACTIVE`、`NORMAL`、`正常`、`启用`。
- 停用：`0`、`false`、`FALSE`、`DISABLE`、`DISABLED`、`INACTIVE`、`停用`、`禁用`。
- 未识别：默认启用，同时把原始状态写入 remarks 或 MES 原始字段摘要，便于后续修正。

## 20. 测试策略

### 20.1 单元测试

- `DataCenterEndpointRegistry` 包含 `MES_PROCESS_TYPE_LIST`、`MES_MATERIAL_PAGE`。
- `DataCenterIntegrationClient.callGet` 正确拼 query 参数，忽略空参数，保留脱敏日志。
- `MesMasterDataClient` 正确解析工艺列表和材料分页响应。
- `MesMasterDataSyncService` 覆盖工艺 upsert、默认具体工艺、材料全类型同步、已有价格不覆盖、新增价格为空、远端缺失停用、空返回保护。
- `EngineerService` 覆盖材料单价缺失或为 0 时禁止完成核价。

### 20.2 集成测试

- 模拟数据中心返回 MES 工艺，完成同步后报价路线生成 `processCode`，MES 下发 DTO 输出 `processTypeCode`。
- 模拟数据中心返回 MES 材料，完成同步后报价材料选择使用 `materialCode`，MES BOM 输出 `itemCode`。
- 新增 MES 材料未维护价格时，完成核价失败。
- 维护价格后，完成核价成功并保留材料编码和毛重。

### 20.3 前端测试

- 同步按钮受正确权限控制。
- 同步成功后刷新列表。
- 材料单价状态展示正确。
- 同步 loading 防重复点击。

### 20.4 文档测试

- 数据中心配置文档包含两个新增 endpoint code 和环境变量。
- MES/WMS 对接文档补充工艺、材料同步口径。
- 权限和日志文档补充审计动作。

## 21. 上线与回滚

上线步骤：

1. 执行数据库迁移，新增材料 MES 字段和同步运行记录表。
2. 部署后端，配置两个数据中心 endpoint path。
3. 部署前端，开放同步按钮。
4. 管理员先执行 MES 工艺同步，确认 `processCode` 为 MES code。
5. 管理员执行 MES 材料同步，确认全部类型进入字典。
6. 工程维护缺失材料单价。
7. 使用真实或编造但字段匹配的数据完成一笔报价与 MES 生产下发验证。

回滚策略：

- 如同步接口异常，禁用新增数据中心 endpoint，不影响已有本地字典。
- 如同步数据有误，可通过 `source_type = MES` 和 `mes_synced_at` 定位本次同步数据，批量停用或恢复备份。
- 生产下发字段逻辑不变，因此回滚同步功能不会破坏已存在下发链路。

## 22. 验收标准

1. 数据中心配置页或数据库中存在 `MES_PROCESS_TYPE_LIST` 和 `MES_MATERIAL_PAGE`。
2. QPO 能通过数据中心获取 MES 工艺列表，并同步到工艺大类。
3. QPO 能通过数据中心分页获取 MES 全部材料类型，并同步到材料字典。
4. 同步后的工艺报价快照中 `processCode` 等于 MES 工艺 code。
5. 同步后的材料报价快照中 `materialCode` 等于 MES 材料 code。
6. MES 生产下发中：
   - `processRoute[].processTypeCode = QPO processCode`
   - `processRoute[].processName = QPO detailName`
   - `bom[].itemCode = QPO engineeringMaterialCode`
   - `bom[].quantity = QPO materialGrossWeight kg`
7. MES 材料无单价时，QPO 允许同步但禁止完成核价。
8. 已有 QPO 材料单价不会被 MES 同步覆盖。
9. 同步操作产生审计日志和同步运行记录，日志不包含 token 或敏感密钥。
10. 测试覆盖数据中心 endpoint、同步服务、核价价格拦截、前端权限按钮。

## 23. 实现顺序建议

1. 后端数据中心 endpoint 与 GET query 参数能力。
2. MES 工艺/材料 DTO 与 `MesMasterDataClient`。
3. 数据库迁移：材料 MES 字段、同步运行记录表、endpoint seed。
4. `MesMasterDataSyncService` 工艺同步。
5. `MesMasterDataSyncService` 材料同步。
6. 核价材料单价缺失拦截。
7. 后端手动同步 API、权限、审计动作。
8. 前端材料工艺页面同步按钮和状态展示。
9. 文档和测试补齐。

## 24. 明确决策

- 材料同步范围：全部 MES 材料类型，不过滤 `materialType`。
- 材料价格来源：QPO 本地维护，MES 不提供、不覆盖。
- 材料单价缺失：禁止完成核价。
- MES density：接口存在，第一版按 QPO `kg/m3` 写入，同时保留原始值。
- 工艺 `processTypeCode` 来源：MES `code` 同步为 QPO `processCode`。
- 工艺 `processName` 来源：QPO `detailName`，默认同步生成 `detailName = MES name`。

## 25. 推荐方案确认

已确认采用推荐方案：**MES 主数据同步到 QPO 现有工艺/材料字典，QPO 继续维护材料核价单价，生产下发沿用现有字段链路。**

该方案的实现边界如下：

- MES 工艺类型进入 `dict_process_category`，并保证至少有一个可选 `dict_process_detail`。
- MES 全部材料类型进入 `dict_material` 和 `dict_material_type`，不按 `materialType` 过滤。
- MES 材料接口的 `density` 用于 QPO 密度字段初始化和追溯，但 MES 不提供材料单价。
- QPO 材料单价仍由工程字典维护，MES 同步不得覆盖已有单价。
- 材料单价缺失或为 0 时，后端禁止完成核价。
- MES 生产下发字段口径不重新设计，只通过同步后的本地字典保证编码来源正确。

## 26. 分阶段实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:subagent-driven-development` 或 `superpowers:executing-plans` to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 通过数据中心同步 MES 工艺与材料主数据到 QPO 本地字典，并确保未维护材料单价时不能完成核价。

**Architecture:** 后端先补齐数据中心 endpoint 和 GET query 能力，再新增 MES 主数据客户端、同步服务、数据库迁移和字典 API；前端在材料工艺页添加同步入口和单价状态提示。生产下发继续读取报价快照中的 `processCode/detailName/engineeringMaterialCode/materialGrossWeight`，不改 MES 下发合同字段。

**Tech Stack:** Spring Boot 3.2.5、Java 17、MyBatis-Plus、Flyway、JUnit/Spring Boot Test、Vue 3、Element Plus、Pinia、Vitest。

### 26.1 全局约束

- 不引入 APS 代码、配置或设计目标。
- 保留 QPO 兼容命名：`erp.env`、`ERP_*`、`erp.*`、`ErpUser` 等不得因本次 MES 同步改名。
- 后端权限是最终安全边界；新增同步接口必须使用 `@RequirePermission`。
- 不新增 assignable `common:*` 权限。
- 同步、价格拦截、配置变更必须写操作日志或后端摘要日志；不能记录 token、client secret、完整 MES 响应体。
- 代码实现遵循 TDD：每阶段先加失败测试，再实现最小代码，再跑对应测试。
- 每个阶段完成后建议单独提交，便于回滚和审查。

### 26.2 阶段 1：数据中心 endpoint 与 GET query 能力

**目标：** QPO 能配置并通过数据中心调用两个 MES 主数据接口，材料分页支持 query 参数。

**文件：**

- Modify: `quote-backend/src/main/java/com/machining/quote/service/DataCenterEndpointRegistry.java`
- Modify: `quote-backend/src/main/java/com/machining/quote/service/DataCenterIntegrationClient.java`
- Modify: `quote-backend/src/main/java/com/machining/quote/config/DataCenterProperties.java`
- Modify: `quote-backend/src/main/resources/application.yaml`
- Modify: `erp.env`
- Create: `quote-backend/src/main/resources/db/migration/V8__mes_master_data_sync.sql`
- Modify/Test: `quote-backend/src/test/java/com/machining/quote/DataCenterConfigStage4Test.java`
- Modify/Test: `quote-backend/src/test/java/com/machining/quote/DataCenterConfigStage8DocumentationTest.java`
- Create/Test: `quote-backend/src/test/java/com/machining/quote/MesMasterDataEndpointStage1Test.java`

**Interfaces produced:**

- `DataCenterEndpointRegistry.MES_PROCESS_TYPE_LIST`
- `DataCenterEndpointRegistry.MES_MATERIAL_PAGE`
- `DataCenterIntegrationClient.callGet(String endpointCode, Map<String, ?> queryParams, Class<T> responseType)`
- `DataCenterProperties.getMesProcessTypeListPath()`
- `DataCenterProperties.getMesMaterialPagePath()`

**Steps:**

- [ ] Add failing tests asserting registry definitions contain `MES_PROCESS_TYPE_LIST` and `MES_MATERIAL_PAGE` with method `GET`, target system `MES`, min timeout `60000`.
- [ ] Add failing test for `callGet` using query params `pageNo=1&pageSize=200` and a value containing spaces or Chinese text; expected URL encodes params and omits null/blank params.
- [ ] Add registry constants and endpoint definitions.
- [ ] Extend fallback path resolution in `DataCenterEndpointRegistry` and properties binding in `DataCenterProperties`.
- [ ] Add `application.yaml` defaults and `erp.env` entries:

```properties
ERP_DATA_CENTER_MES_PROCESS_TYPE_LIST_PATH=/api/v1/mes/openapi/process-type/list
ERP_DATA_CENTER_MES_MATERIAL_PAGE_PATH=/api/v1/mes/openapi/material/page
```

- [ ] Implement `DataCenterIntegrationClient.callGet(...)` using the existing token, timeout, endpoint enablement, and sanitization logic.
- [ ] Add Flyway seed rows for `MES_PROCESS_TYPE_LIST` and `MES_MATERIAL_PAGE` in `V8__mes_master_data_sync.sql`; do not mutate historical migrations except tests that assert baseline docs.
- [ ] Run backend focused tests:

```powershell
cd quote-backend
mvn -Dtest=DataCenterConfigStage4Test,DataCenterConfigStage8DocumentationTest,MesMasterDataEndpointStage1Test test
```

Expected: all selected tests pass.

### 26.3 阶段 2：MES 主数据 DTO 与客户端

**目标：** 后端能解析 MES 工艺列表和材料分页响应，并把数据中心异常转换为安全业务错误。

**文件：**

- Create: `quote-backend/src/main/java/com/machining/quote/dto/mes/master/MesProcessTypeListResp.java`
- Create: `quote-backend/src/main/java/com/machining/quote/dto/mes/master/MesMaterialPageResp.java`
- Create: `quote-backend/src/main/java/com/machining/quote/service/mes/MesMasterDataClient.java`
- Create/Test: `quote-backend/src/test/java/com/machining/quote/MesMasterDataClientTest.java`

**Interfaces produced:**

- `List<MesProcessTypeListResp.Row> MesMasterDataClient.listProcessTypes()`
- `MesMaterialPageResp.Data MesMasterDataClient.pageMaterials(int pageNo, int pageSize)`

**Steps:**

- [ ] Add failing client tests for successful process list parsing: `code/name/hourPrice/status/id` are preserved.
- [ ] Add failing client tests for successful material page parsing: `total/list/code/name/materialType/density/weight` are preserved.
- [ ] Add failing tests for MES business error response where `code` is not success; expected exception message contains endpoint purpose and safe MES `msg`, not token or full response.
- [ ] Implement DTOs with Jackson-friendly fields matching MES docs.
- [ ] Implement `MesMasterDataClient` using `DataCenterIntegrationClient.callGet`.
- [ ] Define success code handling conservatively: accept blank/`0`/`200`/`SUCCESS` only if current MES responses confirm it; otherwise keep parser centralized so mapping can be adjusted in one method.
- [ ] Run:

```powershell
cd quote-backend
mvn -Dtest=MesMasterDataClientTest,MesMasterDataEndpointStage1Test test
```

Expected: all selected tests pass.

### 26.4 阶段 3：数据库迁移与实体映射

**目标：** 本地材料字典具备 MES 来源追踪字段，同步运行有独立记录表。

**文件：**

- Modify: `quote-backend/src/main/resources/db/migration/V8__mes_master_data_sync.sql`
- Modify: `quote-backend/src/main/java/com/machining/quote/entity/DictMaterial.java`
- Modify: `quote-backend/src/main/java/com/machining/quote/dto/MaterialMasterDTO.java`
- Modify: `quote-backend/src/main/java/com/machining/quote/service/MaterialMasterService.java`
- Create: `quote-backend/src/main/java/com/machining/quote/entity/MesMasterDataSyncRun.java`
- Create: `quote-backend/src/main/java/com/machining/quote/mapper/MesMasterDataSyncRunMapper.java`
- Create/Test: `quote-backend/src/test/java/com/machining/quote/MesMasterDataSchemaStage2Test.java`
- Modify/Test: `quote-backend/src/test/java/com/machining/quote/MaterialMasterStage1CharacterizationTest.java`

**Interfaces produced:**

- `DictMaterial.sourceType`
- `DictMaterial.mesExternalId`
- `DictMaterial.mesMaterialType`
- `DictMaterial.mesPurchaseType`
- `DictMaterial.mesProductionType`
- `DictMaterial.mesDocNum`
- `DictMaterial.mesStandard`
- `DictMaterial.mesWeight`
- `DictMaterial.mesDensityRaw`
- `DictMaterial.mesSyncedAt`
- `MesMasterDataSyncRunMapper`

**Steps:**

- [ ] Add failing schema test asserting migration contains `mes_master_data_sync_run` and all new `dict_material` MES fields.
- [ ] Add failing DTO/entity characterization tests asserting `MaterialMasterDTO` exposes source/MES fields for frontend display.
- [ ] Extend Flyway migration with `ALTER TABLE dict_material ADD COLUMN IF NOT EXISTS ...` and indexes.
- [ ] Add `MesMasterDataSyncRun` entity and mapper.
- [ ] Extend `DictMaterial` and `MaterialMasterDTO` fields.
- [ ] Extend `MaterialMasterService.toMaterialDTO` to copy MES fields.
- [ ] Ensure manual material save defaults `sourceType` to `LOCAL` and does not require users to provide MES fields.
- [ ] Run:

```powershell
cd quote-backend
mvn -Dtest=MesMasterDataSchemaStage2Test,MaterialMasterStage1CharacterizationTest,MaterialMasterStage3BackendTest test
```

Expected: all selected tests pass.

### 26.5 阶段 4：MES 工艺同步服务

**目标：** MES 工艺类型能同步到 QPO 工艺大类，并自动保证可选具体工艺。

**文件：**

- Create: `quote-backend/src/main/java/com/machining/quote/service/mes/MesMasterDataSyncService.java`
- Modify: `quote-backend/src/main/java/com/machining/quote/service/ProcessMasterService.java` only if helper extraction is needed; avoid large refactor.
- Modify: `quote-backend/src/main/java/com/machining/quote/service/AuditService.java`
- Create/Test: `quote-backend/src/test/java/com/machining/quote/MesProcessTypeSyncServiceTest.java`
- Modify/Test: `quote-backend/src/test/java/com/machining/quote/ProcessMasterStage3BackendTest.java`

**Interfaces produced:**

- `MesMasterDataSyncService.SyncResult syncProcessTypesManual(Long createdBy)`
- Sync type: `MES_PROCESS_TYPE`
- Audit action: `DICT_PROCESS_MES_SYNC`

**Steps:**

- [ ] Add failing test: MES row `code=P001,name=车削,hourPrice=80,id=mes-1,status=ENABLE` creates/updates `dict_process_category` with `processCode=P001`, `categoryName=车削`, `hourlyRate=80`, `sourceType=MES`, `mesExternalId=mes-1`.
- [ ] Add failing test: sync creates default `dict_process_detail.detailName=车削` for the category.
- [ ] Add failing test: local `sourceType=LOCAL` process category is not disabled when absent from MES response.
- [ ] Add failing test: existing `sourceType=MES` category absent from MES response is disabled, not deleted.
- [ ] Add failing test: empty MES process response fails and does not disable existing MES categories.
- [ ] Implement sync run lifecycle: `RUNNING -> SUCCESS/FAILED`, counters, safe message.
- [ ] Implement process upsert and default detail creation in a transaction.
- [ ] Add audit label/module mapping for `DICT_PROCESS_MES_SYNC`.
- [ ] Run:

```powershell
cd quote-backend
mvn -Dtest=MesProcessTypeSyncServiceTest,ProcessMasterStage3BackendTest,AuditServiceTests test
```

Expected: all selected tests pass.

### 26.6 阶段 5：MES 材料同步服务

**目标：** MES 全部材料类型分页同步到 QPO 材料字典，保留本地价格，写入密度和 MES 原始元数据。

**文件：**

- Modify: `quote-backend/src/main/java/com/machining/quote/service/mes/MesMasterDataSyncService.java`
- Modify: `quote-backend/src/main/java/com/machining/quote/service/MaterialMasterService.java` only if shared material type helpers are needed.
- Modify: `quote-backend/src/main/java/com/machining/quote/service/AuditService.java`
- Create/Test: `quote-backend/src/test/java/com/machining/quote/MesMaterialMasterSyncServiceTest.java`
- Modify/Test: `quote-backend/src/test/java/com/machining/quote/MaterialMasterStage3BackendTest.java`

**Interfaces produced:**

- `MesMasterDataSyncService.SyncResult syncMaterialsManual(Long createdBy)`
- Sync type: `MES_MATERIAL`
- Audit action: `DICT_MATERIAL_MES_SYNC`

**Steps:**

- [ ] Add failing test: material sync calls page 1 and continues until returned list size is less than 200 or fetched count reaches `total`.
- [ ] Add failing test: `MATERIAL / PRODUCT / SEMI` rows all create enabled material records and material types.
- [ ] Add failing test: existing material with `platePrice=12,barPrice=13,tubePrice=14` keeps those values after MES sync.
- [ ] Add failing test: new MES material has price fields null, not 0.
- [ ] Add failing test: MES `density="7850"` populates `density`, and initializes empty `plateDensity/barDensity/tubeDensity` without overwriting existing values.
- [ ] Add failing test: MES `weight` and raw `density` are preserved in `mesWeight` and `mesDensityRaw`.
- [ ] Add failing test: duplicate MES material `code` increments duplicateCount and only first row is applied.
- [ ] Add failing test: empty first page fails and does not disable existing MES materials.
- [ ] Implement material type ensure/upsert for `MATERIAL=原材料`、`PRODUCT=成品`、`SEMI=半成品`、unknown raw value.
- [ ] Implement material upsert by `material_code`, only updating MES identity fields, name/category/density/status, never price fields.
- [ ] Add audit label/module mapping for `DICT_MATERIAL_MES_SYNC`.
- [ ] Run:

```powershell
cd quote-backend
mvn -Dtest=MesMaterialMasterSyncServiceTest,MaterialMasterStage3BackendTest,AuditServiceTests test
```

Expected: all selected tests pass.

### 26.7 阶段 6：核价材料单价缺失拦截

**目标：** 同步进来的 MES 材料如果没有 QPO 单价，不能完成核价。

**文件：**

- Modify: `quote-backend/src/main/java/com/machining/quote/service/EngineerService.java`
- Modify/Test: `quote-backend/src/test/java/com/machining/quote/EngineerServiceMaterialCalculationTests.java`

**Interfaces changed:**

- `EngineerService.resolveMaterialPrice(DictMaterial material, String shape)` should reject missing or non-positive prices instead of returning `0.0`.

**Steps:**

- [ ] Add failing test: plate material with null `platePrice` throws business exception and quote completion fails.
- [ ] Add failing test: bar material with `barPrice=0` throws business exception.
- [ ] Add failing test: tube material with positive `tubePrice` still calculates normally.
- [ ] Add failing test: error message contains material code, material name, and selected shape.
- [ ] Implement price validation using selected shape; default shape follows current behavior as plate material.
- [ ] Ensure non-material fee rows and process rows are unaffected.
- [ ] Run:

```powershell
cd quote-backend
mvn -Dtest=EngineerServiceMaterialCalculationTests test
```

Expected: selected tests pass and missing price paths fail before implementation, pass after implementation.

### 26.8 阶段 7：后端同步 API、权限与日志闭环

**目标：** 工程字典页面可以通过后端接口触发 MES 工艺/材料同步，权限和审计完整。

**文件：**

- Modify: `quote-backend/src/main/java/com/machining/quote/controller/ProcessMasterController.java`
- Modify: `quote-backend/src/main/java/com/machining/quote/controller/DictController.java`
- Modify: `quote-backend/src/main/java/com/machining/quote/service/PermissionService.java` only if tests require catalog labeling; reuse existing permissions.
- Modify: `quote-backend/src/main/java/com/machining/quote/service/AuditService.java`
- Create/Test: `quote-backend/src/test/java/com/machining/quote/MesMasterDataSyncControllerTest.java`
- Modify/Test: `quote-backend/src/test/java/com/machining/quote/PermissionSystemStage5Test.java`
- Modify/Test: `quote-backend/src/test/java/com/machining/quote/AuditActionCoverageTests.java`

**Interfaces produced:**

- `POST /api/dict/process-categories/sync-mes`
- `POST /api/dict/materials/sync-mes`

**Steps:**

- [ ] Add failing controller tests asserting process sync endpoint has `@RequirePermission("engineer:dict:process-manage")`.
- [ ] Add failing controller tests asserting material sync endpoint has `@RequirePermission("engineer:dict:material-manage")`.
- [ ] Add failing tests asserting audit actions infer module `DICT` and labels are user-readable.
- [ ] Implement controller endpoints returning `{success,message,data}` with sync counters.
- [ ] Record audit summaries with only syncRunId and counters.
- [ ] Ensure endpoint failures return safe messages without token or full MES response.
- [ ] Run:

```powershell
cd quote-backend
mvn -Dtest=MesMasterDataSyncControllerTest,PermissionSystemStage5Test,AuditActionCoverageTests test
```

Expected: all selected tests pass.

### 26.9 阶段 8：前端同步入口与展示

**目标：** 材料工艺管理页支持手动同步，并展示来源、MES 类型、同步时间和单价状态。

**文件：**

- Modify: `quote-frontend/src/services/dictApi.js`
- Modify: `quote-frontend/src/components/MaterialProcessManage.vue`
- Modify/Test: `quote-frontend/src/materialMasterStage10Frontend.test.js`
- Modify/Test: `quote-frontend/src/processMasterStage4Frontend.test.js`
- Create/Test: `quote-frontend/src/mesMasterDataSyncFrontend.test.js`
- Modify/Test: `quote-frontend/src/adminDuplicateClickGuard.test.js`

**Interfaces consumed:**

- `dictApi.syncMesProcessTypes()` -> `POST /api/dict/process-categories/sync-mes`
- `dictApi.syncMesMaterials()` -> `POST /api/dict/materials/sync-mes`

**Steps:**

- [ ] Add failing API tests for both new `dictApi` methods.
- [ ] Add failing component tests asserting sync buttons render under correct tab and permission string.
- [ ] Add failing duplicate-click guard test keys: `syncMesMaterials` and `syncMesProcessTypes`.
- [ ] Add failing display test for material source/MES type/sync time/unit price status.
- [ ] Implement `dictApi` methods.
- [ ] Add buttons in `MaterialProcessManage.vue` with confirmation dialog text: “MES 会更新基础信息，不覆盖 QPO 材料单价”。
- [ ] Use existing pending action pattern to prevent duplicate clicks.
- [ ] Refresh current list after sync success and show fetched/upserted/disabled/invalid/duplicate counters.
- [ ] Add material list columns for source, MES type, sync time, and price status.
- [ ] Run:

```powershell
cd quote-frontend
npm test -- --run src/mesMasterDataSyncFrontend.test.js src/materialMasterStage10Frontend.test.js src/processMasterStage4Frontend.test.js src/adminDuplicateClickGuard.test.js
```

Expected: all selected Vitest tests pass.

### 26.10 阶段 9：文档、合同验收与真实链路回归

**目标：** 文档、测试和真实数据验证口径闭环，证明 QPO 系统生成的数据可以按 MES 编码体系下发。

**文件：**

- Modify: `docs/QPO-MES-WMS对接/` 下相关 MES 对接文档
- Modify: `docs/QPO系统配置/` 下数据中心配置文档
- Modify: `docs/QPO日志模块/` 下操作日志文档
- Modify: `docs/QPO权限用户体系/` 下权限说明
- Create/Test: `quote-backend/src/test/java/com/machining/quote/MesMasterDataSyncDocumentationTest.java`
- Modify/Test: `quote-backend/src/test/java/com/machining/quote/LockedItemMesProductionDispatchTest.java`
- Modify/Test: `quote-backend/src/test/java/com/machining/quote/MesRealBatchStage8AcceptanceTest.java`

**Acceptance checks:**

- `processRoute[].processTypeCode` comes from MES `code` synced to QPO `processCode`.
- `processRoute[].processName` comes from QPO `detailName`.
- `bom[].itemCode` comes from QPO `engineeringMaterialCode` synced from MES material `code`.
- `bom[].quantity` remains QPO material gross weight in kg.
- Missing material unit price blocks quote completion.

**Steps:**

- [ ] Add documentation tests asserting endpoint codes, env vars, permissions, and audit actions appear in docs.
- [ ] Update docs with MES 工艺/材料主数据同步口径 and “MES 无单价，QPO 维护单价”。
- [ ] Extend dispatch tests to build data from synced dictionary records rather than hardcoded mismatched local codes.
- [ ] Run focused backend tests:

```powershell
cd quote-backend
mvn -Dtest=MesMasterDataSyncDocumentationTest,LockedItemMesProductionDispatchTest,MesRealBatchStage8AcceptanceTest test
```

Expected: all selected tests pass.

- [ ] Run focused frontend tests from 阶段 8 again.
- [ ] Run final smoke suite:

```powershell
cd quote-backend
mvn test
```

```powershell
cd quote-frontend
npm test
```

Expected: backend and frontend test suites pass, or any unrelated pre-existing failures are documented with exact failing test names and reasons.

### 26.11 建议提交拆分

每个阶段建议独立提交，提交信息如下：

1. `feat: add MES master data endpoints`
2. `feat: add MES master data client`
3. `feat: add MES material metadata schema`
4. `feat: sync MES process types into process dictionary`
5. `feat: sync MES materials into material dictionary`
6. `fix: block quote completion when material price is missing`
7. `feat: expose MES master data sync APIs`
8. `feat: add MES sync controls to material process page`
9. `docs: close MES master data sync acceptance`

### 26.12 实施检查清单

- [ ] 新增 endpoint 不影响既有 `MES_MATERIAL_RESOLVE` 和 `MES_PRODUCTION_DISPATCH`。
- [ ] GET query 参数支持不会改变现有 POST 调用行为。
- [ ] MES 同步不会覆盖已有材料单价。
- [ ] 新增 MES 材料单价保持 null，不写 0。
- [ ] 材料价格缺失或为 0 时禁止完成核价。
- [ ] 同步空返回不会误停用全部 MES 来源数据。
- [ ] 本地 `LOCAL` 来源工艺/材料不被 MES 同步停用。
- [ ] 审计日志不包含 token、secret、完整响应体。
- [ ] 前端按钮权限与后端 `@RequirePermission` 一致。
- [ ] MES 下发 payload 字段口径保持既有合同：工艺编码、工艺名称、材料编码、毛重 kg。

## 27. 页面与字典表保留策略

推荐方案实现后，材料工艺模块的四个核心页签和对应表都继续保留，不删除、不合并。变化的是维护主体和字段边界：MES 负责主数据身份，QPO 负责核价业务字段和本地例外维护。

| 页面 | 对应表 | 是否保留 | 同步后职责 | 人工维护边界 |
| --- | --- | --- | --- | --- |
| 材料费用 | `dict_material` | 保留 | 保存 MES 材料编码、名称、类型、密度等基础信息，并继续保存 QPO 板/棒/管单价。报价和 MES BOM 下发都依赖该表。 | MES 来源材料的编码、名称、MES 类型由同步维护；QPO 人工维护单价、必要的本地密度修正、排序和备注。 |
| 材料类型 | `dict_material_type` | 保留 | 作为 `dict_material.material_type_id` 外键、列表分组和筛选依据；同步时自动确保 `MATERIAL/PRODUCT/SEMI` 等 MES 类型存在。 | MES 自动生成的类型不建议删除；可维护显示名称、排序和本地补充类型。若类型已被材料引用，仍按现有逻辑禁止硬删除或转停用。 |
| 工艺大类 | `dict_process_category` | 保留 | 保存 MES 工艺类型编码、名称、小时单价和来源信息；报价快照里的 `processCode` 来自这里，并下发为 MES `processTypeCode`。 | MES 来源大类的 `processCode/categoryName/hourlyRate` 默认由同步维护；本地可保留 `LOCAL` 来源工艺或少量例外配置。 |
| 具体工艺 | `dict_process_detail` | 保留 | QPO 仍需要具体工艺作为报价路线明细；下发 MES 时 `detailName` 转为 `processName`。MES 当前只提供工艺类型接口，未提供具体工艺接口。 | 同步会为每个 MES 工艺大类生成一个默认具体工艺；用户仍可在 MES 大类下维护更细的具体工艺，不被同步删除。 |

因此，后续实现不应把“从 MES 获取主数据”理解为删掉材料类型表或工艺大类表。正确理解是：

- `dict_material_type` 和 `dict_process_category` 仍是 QPO 核价与选择器的结构表。
- MES 来源记录应增加来源和同步时间展示，避免用户误以为全部字段都是本地自由维护。
- 对 MES 来源材料，前端编辑时应优先开放价格字段，基础身份字段可只读或提示“下次 MES 同步会覆盖”。
- 对 MES 来源工艺大类，前端编辑时应提示 `processCode` 必须保持 MES code；如允许编辑名称或小时价，也要明确下次同步可能覆盖。
- `LOCAL` 来源记录继续作为本地例外或历史兼容数据存在，但生产下发前应尽量使用 MES 来源编码。

## 28. 按 QPO 开发文档口径的分阶段计划

本节采用现有 QPO 开发文档常用写法：每个阶段明确目标、修改范围和验收口径。第 26 节保留给后续 agentic worker 执行时逐任务拆解；本节作为项目评审和排期视角的分阶段开发计划。

### 阶段 0：口径冻结与测试护栏

状态：已完成。

目标：先冻结 MES 工艺/材料主数据同步的业务边界，防止实现阶段误删页面、误覆盖价格或误改 MES 下发契约。

修改：

- 保留材料工艺模块四个核心页签：材料费用、材料类型、工艺大类、具体工艺。
- 保留四张字典表：`dict_material`、`dict_material_type`、`dict_process_category`、`dict_process_detail`。
- 明确 MES 来源字段只维护主数据身份，QPO 本地继续维护材料单价和必要例外。
- 新增后端测试文件骨架：`MesMasterDataEndpointStage1Test`、`MesMasterDataClientTest`、`MesProcessTypeSyncServiceTest`、`MesMaterialMasterSyncServiceTest`。
- 新增前端测试文件骨架：`mesMasterDataSyncFrontend.test.js`。

验收：

- 文档中明确“页面与字典表保留策略”。
- 测试名覆盖 endpoint、client、工艺同步、材料同步、前端入口和价格拦截。
- 不修改生产下发 DTO 字段口径。

### 阶段 1：数据中心 endpoint 与 GET query 能力

状态：已完成。

目标：系统配置里能维护 MES 工艺列表和材料分页两个只读 endpoint，QPO 后端能通过数据中心带 query 参数调用材料分页。

修改：

- 修改 `DataCenterEndpointRegistry`，新增：
  - `MES_PROCESS_TYPE_LIST`
  - `MES_MATERIAL_PAGE`
- 修改 `DataCenterIntegrationClient`，新增 `callGet(endpointCode, queryParams, responseType)`。
- 修改 `DataCenterProperties`、`application.yaml`、`erp.env`，新增：
  - `ERP_DATA_CENTER_MES_PROCESS_TYPE_LIST_PATH`
  - `ERP_DATA_CENTER_MES_MATERIAL_PAGE_PATH`
- 新增 Flyway 迁移 `V8__mes_master_data_sync.sql`，seed 两个 endpoint。

验收：

- 数据中心配置中存在两个 endpoint，方法均为 `GET`，targetSystem 为 `MES`，最低 timeout 为 `60000ms`。
- `callGet` 能拼接 `pageNo=1&pageSize=200`，能跳过空 query 参数。
- 现有 `MES_MATERIAL_RESOLVE`、`MES_PRODUCTION_DISPATCH`、`U9_COMMON_ENTITY_QUERY` 行为不变。

验收命令：

```powershell
cd D:\QPO-Operations-Management-System\quote-backend
.\mvnw.cmd test -Dtest=DataCenterConfigStage4Test,DataCenterConfigStage8DocumentationTest,MesMasterDataEndpointStage1Test
```

### 阶段 2：MES 主数据 DTO 与只读 Client

状态：已完成。

目标：封装 MES 工艺和材料主数据只读调用，让同步服务不直接依赖数据中心客户端细节。

修改：

- 新增 `MesProcessTypeListResp`。
- 新增 `MesMaterialPageResp`。
- 新增 `MesMasterDataClient`。
- 解析工艺字段：`id/code/name/hourPrice/deptId/deptName/status/remark/createTime/updateTime`。
- 解析材料字段：`id/code/name/materialType/purchaseType/productionType/standard/docNum/weight/density/status`。
- 统一成功码和错误码处理，错误信息脱敏。

验收：

- 工艺列表响应能转成稳定行对象。
- 材料分页响应能读取 `total` 和 `list`。
- MES 返回业务失败时抛出安全错误，不包含 token、Authorization、完整响应体。
- Client 测试不依赖真实 MES。

验收命令：

```powershell
cd D:\QPO-Operations-Management-System\quote-backend
.\mvnw.cmd test -Dtest=MesMasterDataClientTest,MesMasterDataEndpointStage1Test
```

### 阶段 3：数据库迁移与本地字典字段扩展

状态：已完成。

目标：材料字典具备 MES 来源追踪字段，主数据同步有独立批次记录；工艺表复用已有 MES 字段。

修改：

- 扩展 `dict_material`：新增 `source_type`、`mes_external_id`、`mes_material_type`、`mes_purchase_type`、`mes_production_type`、`mes_doc_num`、`mes_standard`、`mes_weight`、`mes_density_raw`、`mes_synced_at`。
- 新增 `mes_master_data_sync_run`，记录同步类型、状态、抓取数、有效数、无效数、重复数、upsert 数、停用数、脱敏消息和执行人。
- 修改 `DictMaterial`、`MaterialMasterDTO`、`MaterialMasterService.toMaterialDTO`。
- 新增 `MesMasterDataSyncRun` 和 `MesMasterDataSyncRunMapper`。

验收：

- Flyway 空库迁移通过。
- 材料列表 DTO 可返回来源、MES 类型、MES 同步时间和 MES 原始密度。
- 本地手工新增材料默认 `sourceType=LOCAL`。
- 工艺大类继续使用已有 `sourceType/mesExternalId/mesSyncedAt`。

验收命令：

```powershell
cd D:\QPO-Operations-Management-System\quote-backend
.\mvnw.cmd test -Dtest=MesMasterDataSchemaStage2Test,MaterialMasterStage1CharacterizationTest,MaterialMasterStage3BackendTest
```

### 阶段 4：MES 工艺类型同步到工艺大类

目标：MES 工艺类型进入 QPO 工艺大类，并保证报价仍能选择具体工艺。

修改：

- 新增或扩展 `MesMasterDataSyncService.syncProcessTypesManual(createdBy)`。
- 按 MES `code` upsert `dict_process_category`。
- 字段映射：`code -> process_code`，`name -> category_name`，`hourPrice -> hourly_rate`，`id -> mes_external_id`。
- 为每个 MES 工艺大类创建或保留默认 `dict_process_detail`，默认 `detailName = MES name`。
- MES 返回缺失的 MES 来源工艺大类标记停用，不删除。
- 本地 `LOCAL` 来源工艺不受 MES 同步影响。

验收：

- 同步后 `processCode` 等于 MES `code`。
- 默认具体工艺存在，且 `detailName` 可转为 MES 下发 `processName`。
- 空返回不会误停用全部 MES 工艺。
- 同步运行记录和审计摘要有计数，不保存完整响应。

验收命令：

```powershell
cd D:\QPO-Operations-Management-System\quote-backend
.\mvnw.cmd test -Dtest=MesProcessTypeSyncServiceTest,ProcessMasterStage3BackendTest,AuditServiceTests
```

### 阶段 5：MES 全量材料同步到材料字典

目标：MES `MATERIAL / PRODUCT / SEMI` 全量进入 QPO 材料字典，同时保留 QPO 本地价格字段。

修改：

- 扩展 `MesMasterDataSyncService.syncMaterialsManual(createdBy)`。
- 分页调用 `MES_MATERIAL_PAGE`，pageSize 固定 200，不传 `materialType` 过滤。
- 自动确保 `dict_material_type` 中存在：
  - `MATERIAL = 原材料`
  - `PRODUCT = 成品`
  - `SEMI = 半成品`
  - 未知类型按 MES 原值创建或归入 `MES_UNKNOWN`
- 按 MES `code` upsert `dict_material`。
- 同步 `materialName/materialType/density/standard/docNum/weight/status` 等基础信息。
- 不覆盖 `platePrice/barPrice/tubePrice`。
- 新增材料价格保持 null，不写 0。

验收：

- 三类 MES 材料都能进入 QPO 材料字典。
- 已有材料价格同步后保持不变。
- 新增 MES 材料单价为空。
- MES `density` 写入 QPO 默认密度，并保留 `mesDensityRaw`。
- 空第一页不会误停用全部 MES 材料。

验收命令：

```powershell
cd D:\QPO-Operations-Management-System\quote-backend
.\mvnw.cmd test -Dtest=MesMaterialMasterSyncServiceTest,MaterialMasterStage3BackendTest,AuditServiceTests
```

### 阶段 6：材料单价缺失禁止完成核价

目标：MES 同步新增材料如果未维护 QPO 单价，不能按 0 元/kg 完成核价。

修改：

- 修改 `EngineerService.resolveMaterialPrice`。
- 板材读取 `platePrice`，棒材读取 `barPrice`，管材读取 `tubePrice`。
- 对应单价为 null 或 `<= 0` 时抛出业务异常。
- 错误消息包含材料编码、材料名称和形态。
- 不影响非工时费用、工艺费用和已有正价材料。

验收：

- 新增 MES 材料未维护单价时，报价完成失败。
- 单价为 0 时，报价完成失败。
- 维护正数单价后，报价可完成。
- 后端不能再默默把缺失单价当 0 计算。

验收命令：

```powershell
cd D:\QPO-Operations-Management-System\quote-backend
.\mvnw.cmd test -Dtest=EngineerServiceMaterialCalculationTests
```

### 阶段 7：后端同步 API、权限和审计

目标：材料工艺模块可以手动触发 MES 工艺/材料同步，权限和日志符合 QPO 治理要求。

修改：

- 修改 `ProcessMasterController`，新增 `POST /api/dict/process-categories/sync-mes`。
- 修改 `DictController`，新增 `POST /api/dict/materials/sync-mes`。
- 工艺同步复用 `engineer:dict:process-manage`。
- 材料同步复用 `engineer:dict:material-manage`。
- 新增审计动作：`DICT_PROCESS_MES_SYNC`、`DICT_MATERIAL_MES_SYNC`。
- `AuditService` 增加中文标签和模块归类。

验收：

- 无权限用户不能调用同步接口。
- 同步成功/失败均有同步运行记录。
- 操作日志只保存 `syncRunId` 和计数摘要。
- 不新增 `common:*` 权限。

验收命令：

```powershell
cd D:\QPO-Operations-Management-System\quote-backend
.\mvnw.cmd test -Dtest=MesMasterDataSyncControllerTest,PermissionSystemStage5Test,AuditActionCoverageTests
```

### 阶段 8：前端材料工艺页同步入口与字段展示

目标：用户能在材料工艺模块手动同步 MES 工艺和材料，并能看出哪些字段来自 MES、哪些价格仍需 QPO 维护。

修改：

- 修改 `quote-frontend/src/services/dictApi.js`，新增：
  - `syncMesProcessTypes()`
  - `syncMesMaterials()`
- 修改 `MaterialProcessManage.vue`：
  - 材料费用页签新增“从 MES 同步材料”。
  - 工艺大类页签新增“从 MES 同步工艺”。
  - 材料列表展示来源、MES 类型、同步时间、单价状态。
  - 工艺大类列表展示来源和同步时间。
  - 同步确认弹窗提示“MES 会更新基础信息，不覆盖 QPO 材料单价”。
- 使用现有 pending action 防重复点击。

验收：

- 无权限时同步按钮不可见。
- 同步过程中按钮 loading，不能重复提交。
- 同步成功后刷新对应列表。
- 材料价格缺失显示“单价未维护”。
- 页面仍保留材料费用、材料类型、工艺大类、具体工艺四个页签。

验收命令：

```powershell
cd D:\QPO-Operations-Management-System\quote-frontend
npm test -- --run src/mesMasterDataSyncFrontend.test.js src/materialMasterStage10Frontend.test.js src/processMasterStage4Frontend.test.js src/adminDuplicateClickGuard.test.js
```

### 阶段 9：文档、回归和真实链路验收

目标：完成开发文档、权限日志文档、MES/WMS 对接文档和真实数据验证闭环。

修改：

- 更新 `docs/QPO-MES-WMS对接/` 中 MES 主数据同步说明。
- 更新 `docs/QPO系统配置/` 中数据中心 endpoint 配置说明。
- 更新 `docs/QPO权限用户体系/` 中字典同步权限说明。
- 更新 `docs/QPO日志模块/` 中同步操作日志说明。
- 扩展 `LockedItemMesProductionDispatchTest` 和 `MesRealBatchStage8AcceptanceTest`，证明同步后的字典字段进入 MES 下发 payload。
- 如具备测试环境权限，执行真实数据中心读取 MES 工艺/材料并同步一轮；真实写 MES 仍使用独立显式开关，不在普通回归中自动执行。

验收：

- 文档列出两个新增 endpoint、环境变量、权限、审计动作。
- 生产下发 payload 仍满足：
  - `processRoute[].processTypeCode = QPO processCode = MES code`
  - `processRoute[].processName = QPO detailName`
  - `bom[].itemCode = QPO engineeringMaterialCode = MES material code`
  - `bom[].quantity = QPO materialGrossWeight kg`
- 全量后端测试通过。
- 全量前端测试通过。
- 前端 build 通过。

验收命令：

```powershell
cd D:\QPO-Operations-Management-System\quote-backend
.\mvnw.cmd test

cd D:\QPO-Operations-Management-System\quote-frontend
npm test
npm run build
```

最终提交应按阶段拆分，至少包含 endpoint/client/schema/sync/price guard/API/frontend/docs 几类提交；不要把真实环境凭据、完整请求响应或 token 写入仓库。
