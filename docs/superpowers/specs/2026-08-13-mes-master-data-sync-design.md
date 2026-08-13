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
