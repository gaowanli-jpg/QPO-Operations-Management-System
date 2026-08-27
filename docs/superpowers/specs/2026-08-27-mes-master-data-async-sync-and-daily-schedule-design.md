# MES 主数据异步同步与每日固定时间调度设计

## 背景

2026-08-27 的线上验证说明，MES 材料主数据同步本身可以成功完成，但材料全量数据约 10 万级时，当前手动接口会一直阻塞到 MES 分页拉取、归一化、落库、删除对账和审计全部完成。Nginx 默认 60 秒上游读取超时会先返回 504，用户看到“同步失败”，刷新后又看到后台实际已经成功。

根因不是材料映射或删除逻辑再次失败，而是“长任务绑定在一次 HTTP 请求上”。工艺同步当前数据量较小，所以更容易在超时前完成，但架构上存在同样风险。

本设计将材料和工艺大类手动同步统一改为异步任务：原路径保留，点击后立即返回“已提交”和 `syncRunId`，前端根据 `syncRunId` 查询任务状态并在完成后刷新列表。同时扩展现有定时策略，在保留“按间隔执行”的基础上，支持“每天固定时间执行一次”，例如每天 02:00。

## 目标

- 保留现有手动同步路径：`POST /api/dict/materials/sync-mes` 和 `POST /api/dict/process-categories/sync-mes`。
- 手动同步接口不再阻塞等待 MES 拉取和落库完成，立即返回提交结果。
- 后台异步 worker 复用现有 MES 拉取、归一化、upsert、停用和硬删除口径。
- 删除计数继续使用独立 `deletedCount`，不混入 `disabledCount`。
- 工艺大类在 MES 全量成功后缺失时，继续先删除其下 `dict_process_detail`，再删除 `dict_process_category`。
- 定时策略继续支持现有间隔模式，并新增每天固定时间模式。
- 同步提交、开始、完成、失败均可运维追踪；失败日志包含 `taskId`/`syncRunId` 和异常堆栈，但不记录 MES 完整响应、token、Authorization、Cookie 或 client secret。

## 非目标

- 不改变 MES 主数据字段映射口径，尤其不恢复把 `standard` 兜底写入 `materialCategory` 的旧行为。
- 不改变 MES 全量缺失删除规则，不删除 `LOCAL` 来源材料或工艺大类。
- 不引入新的前端 UI 库。
- 不把该主数据同步强行拆进 `system_task` 表，除非实现阶段发现现有线程池无法安全复用。
- 不通过调大 Nginx 超时时间作为主要解决方案。

## 现状确认

当前 `DictController.syncMesMaterials()` 直接调用 `MesMasterDataSyncService.syncMaterialsManual(currentUserId())`，`ProcessMasterController.syncMesProcessTypes()` 直接调用 `syncProcessTypesManual(currentUserId())`，因此接口返回必须等待同步完成。

当前 `MesMasterDataSyncService` 已具备核心同步能力：

- `syncMaterialsManual` 和 `syncProcessTypesManual` 创建 `mes_master_data_sync_run` 运行记录。
- MES 拉取和数据持久化失败时，运行记录通过独立事务更新为 `FAILED`。
- 成功时写入 `fetchedCount`、`validCount`、`invalidCount`、`duplicateCount`、`upsertedCount`、`disabledCount`、`deletedCount`。
- MES 返回但状态停用计入 `disabledCount`。
- MES 全量成功但缺失的 MES 来源材料或工艺大类会硬删除并计入 `deletedCount`。
- 工艺大类删除前先删除其下具体工艺。

当前定时策略保存在 `mes_master_data_sync_schedule_config`，只支持：

- `enabled`
- `startupDelayMs`
- `fixedDelayMs`
- `lastScheduledRunAt`
- `lastScheduledSyncRunId`
- `lastScheduledStatus`
- `lastScheduledMessage`

## 方案比较

### 方案 A：只调大网关和 HTTP 超时

优点是改动少，但它继续把用户请求、网关连接、后端线程和 MES 全量同步绑在一起。材料数据量继续增长后仍会遇到超时、重复点击、用户误判失败等问题。该方案不推荐。

### 方案 B：复用 `system_task` 作为主任务表

优点是已有后台任务模式和任务查询接口。缺点是 MES 主数据同步已经有 `mes_master_data_sync_run`，其中包含同步类型、计数、脱敏消息和审计目标；再写一份 `system_task` 会引入双表状态一致性和前端展示转换成本。

### 方案 C：以 `mes_master_data_sync_run` 作为主任务表，复用现有线程池执行 worker

这是推荐方案。`mes_master_data_sync_run` 继续作为唯一同步运行记录和审计目标，后台执行只复用现有 `systemTaskExecutor` 线程池或新增同配置的轻量 executor。这样能保留现有计数、日志、文档和删除口径，同时避免双表状态漂移。

## 推荐设计

### 1. 后端接口契约

保留现有 POST 路径，但响应含义从“同步已完成”改为“同步任务已提交”。

材料：

```http
POST /api/dict/materials/sync-mes
```

工艺大类：

```http
POST /api/dict/process-categories/sync-mes
```

首次提交成功响应：

```json
{
  "success": true,
  "submitted": true,
  "alreadyRunning": false,
  "syncRunId": 123,
  "syncType": "MES_MATERIAL",
  "status": "RUNNING",
  "message": "MES材料主数据同步已提交"
}
```

已有同类型同步正在执行时，不再启动第二个任务，返回已有运行记录：

```json
{
  "success": true,
  "submitted": false,
  "alreadyRunning": true,
  "syncRunId": 123,
  "syncType": "MES_MATERIAL",
  "status": "RUNNING",
  "message": "已有MES材料主数据同步正在执行"
}
```

保留权限不变：

- 材料同步：`engineer:dict:material-manage`
- 工艺同步：`engineer:dict:process-manage`

新增状态查询接口，用于前端轮询和刷新后恢复状态：

```http
GET /api/dict/materials/sync-runs/latest
GET /api/dict/materials/sync-runs/{syncRunId}
GET /api/dict/process-categories/sync-runs/latest
GET /api/dict/process-categories/sync-runs/{syncRunId}
```

查询响应复用当前同步摘要字段：

```json
{
  "success": true,
  "syncRunId": 123,
  "syncType": "MES_MATERIAL",
  "status": "SUCCESS",
  "fetchedCount": 104654,
  "validCount": 104654,
  "invalidCount": 0,
  "duplicateCount": 0,
  "upsertedCount": 104654,
  "disabledCount": 0,
  "deletedCount": 14,
  "message": "OK",
  "startedAt": "2026-08-27T14:22:10",
  "finishedAt": "2026-08-27T14:25:42"
}
```

`GET` 轮询接口只读，不写操作日志。

### 2. 后端服务拆分

将当前阻塞式公共方法拆成提交入口和 worker 执行入口：

- `submitMaterialsManual(Long createdBy)`：创建或复用同类型 active run，提交后台 worker，立即返回 `SyncSubmitResult`。
- `submitProcessTypesManual(Long createdBy)`：同上。
- `submitScheduled(String syncType)`：由定时巡检调用，`createdBy=null`，提交后台 worker。
- `runMaterialsWorker(MesMasterDataSyncRun run, TriggerSource triggerSource)`：执行原材料同步核心逻辑。
- `runProcessTypesWorker(MesMasterDataSyncRun run, TriggerSource triggerSource)`：执行原工艺同步核心逻辑。
- `getRun(String syncType, Long syncRunId)` / `getLatestRun(String syncType)`：给 controller 查询状态。

旧的 `syncMaterialsManual` / `syncProcessTypesManual` 不再作为 controller 调用路径保留。实现阶段可以删除旧 public blocking 方法，或先改名为 private worker 辅助方法，避免继续暴露同步阻塞入口。

核心同步逻辑继续保留：

- MES 工艺列表拉取、归一化、默认具体工艺创建或启用。
- MES 材料分页拉取、归一化、材料类型确保存在。
- MES 状态停用 -> QPO `enabled=false`，计入 `disabledCount`。
- MES 全量缺失 -> 删除 MES 来源记录，计入 `deletedCount`。
- 空有效数据 -> 判定失败，不执行缺失删除。

事务边界继续沿用当前加固后的原则：

- 创建运行记录使用独立事务，保证失败时也能追踪 `syncRunId`。
- 数据 upsert/delete 在业务事务中执行。
- 完成或失败更新运行记录和审计摘要使用独立事务。

### 3. 并发与重复点击

每个 `syncType` 同一时间只允许一个 active run。

推荐增加 Flyway 迁移，建立数据库级约束：

```sql
CREATE UNIQUE INDEX IF NOT EXISTS uk_mes_master_data_sync_run_active_type
    ON mes_master_data_sync_run (sync_type)
    WHERE status = 'RUNNING';
```

提交流程：

1. 查询同类型 `RUNNING` 运行记录。
2. 如果存在，返回 `alreadyRunning=true` 和已有 `syncRunId`。
3. 如果不存在，插入新的 `RUNNING` 运行记录。
4. 如果并发插入触发唯一索引冲突，重新查询 active run 并返回 `alreadyRunning=true`。
5. 提交后台 worker。
6. 如果后台任务提交被拒绝或 executor 不可用，立即把该 run 更新为 `FAILED`，返回失败提交响应或抛出安全错误。

为避免进程中断后 `RUNNING` 永久卡住，设计一个保守的 stale 处理口径：

- 默认不自动把短时间运行中的任务判失败。
- 新增配置 `erp.mes.master-data-sync.running-stale-minutes`，默认 240 分钟。
- 提交或定时巡检发现同类型 `RUNNING` 且 `startedAt` 早于当前时间减去该阈值时，将其标记为 `FAILED`，消息为 `MES master data sync interrupted or timed out`，随后允许新任务提交。
- 失败文件日志必须包含 `taskId=<syncRunId>`、`syncType` 和异常堆栈；stale 标记日志包含 `taskId=<syncRunId>`，不伪造异常堆栈。

### 4. 定时同步扩展

`mes_master_data_sync_schedule_config` 新增字段：

- `schedule_mode`：`FIXED_DELAY` 或 `DAILY_TIME`，默认 `FIXED_DELAY`。
- `daily_run_time`：每天执行时间，`TIME` 类型，例如 `02:00:00`。

DTO/请求对象新增：

- `scheduleMode`
- `dailyRunTime`

兼容规则：

- 旧数据没有 `schedule_mode` 时按 `FIXED_DELAY` 处理。
- `startupDelayMs` 和 `fixedDelayMs` 继续保留，只在 `FIXED_DELAY` 模式生效。
- `dailyRunTime` 只在 `DAILY_TIME` 模式生效；如果用户选择 DAILY_TIME 但未传时间，默认 `02:00:00`。
- 环境变量 `erp.mes.master-data-sync.enabled/startup-delay-ms/fixed-delay-ms/check-ms` 仍只作为数据库策略缺失时的兜底默认值。

`FIXED_DELAY` 模式保持当前语义：

- 服务启动后首次运行受 `startupDelayMs` 控制。
- 上次定时同步完成后，等待 `fixedDelayMs` 再触发下一次。

`DAILY_TIME` 模式采用 Asia/Shanghai 本地日期时间：

- `enabled=false` 时不运行。
- 当前时间早于 `dailyRunTime` 时不运行。
- 当前时间达到或晚于 `dailyRunTime`，且当天还没有定时同步结果时，提交一次后台同步。
- 当天无论成功或失败，定时任务都不自动重复；需要重试时由用户手动点击同步。
- 如果服务在 `dailyRunTime` 之后启动，只要当天没有定时同步结果，则启动后的巡检会补跑一次。
- 如果同类型手动或定时同步正在运行，定时巡检不启动重复任务，也不写噪声操作日志。

后台 worker 完成定时触发的 run 后，再调用 `updateScheduledResult(syncType, result)` 更新：

- `lastScheduledRunAt`
- `lastScheduledSyncRunId`
- `lastScheduledStatus`
- `lastScheduledMessage`

### 5. 前端交互

材料工艺模块保持当前按钮和权限展示位置不变。

点击“从 MES 同步材料”或“从 MES 同步工艺”后：

1. 保留现有确认弹窗。
2. POST 原路径。
3. 成功提交后立即提示“MES材料主数据同步已提交”或“MES工艺主数据同步已提交”。
4. 按 `syncRunId` 启动轮询。
5. 当前 tab 对应按钮在 active run 结束前保持 disabled/loading。
6. `SUCCESS` 后提示抓取、更新、停用、删除计数，并刷新材料/工艺列表。
7. `FAILED` 后提示脱敏失败原因，不刷新列表，不让 loading 永久停留。
8. 页面加载或切换到材料/工艺 tab 时，查询 latest run；如果 latest 是 `RUNNING`，自动恢复轮询。

定时策略弹窗增加模式选择：

- “按间隔执行”：展示启动延迟秒数、执行间隔分钟数。
- “每天固定时间”：展示时间选择器，默认 `02:00`。

保存 payload 示例：

```json
{
  "enabled": true,
  "scheduleMode": "DAILY_TIME",
  "dailyRunTime": "02:00:00"
}
```

间隔模式 payload 示例：

```json
{
  "enabled": true,
  "scheduleMode": "FIXED_DELAY",
  "startupDelayMs": 30000,
  "fixedDelayMs": 1800000
}
```

### 6. 日志与审计

操作日志动作继续使用现有编码：

- 材料：`DICT_MATERIAL_MES_SYNC`
- 工艺：`DICT_PROCESS_MES_SYNC`
- 策略保存：`DICT_MES_MASTER_DATA_SYNC_SCHEDULE_UPDATE`

手动异步同步建议记录两类业务审计摘要：

- 提交 accepted：`targetType=mes_master_data_sync_run`，摘要包含 `syncRunId`、`syncType`、`status=RUNNING`、`submitted=true`、`alreadyRunning=false`。
- 完成或失败：摘要包含 `syncRunId`、`syncType`、`status`、所有计数和脱敏消息。

如果重复点击只返回已有 active run，不新增业务审计记录，避免噪声；可写低噪声文件日志 `already running`。

后端文件日志固定低敏摘要：

- `MES master data sync submitted taskId={} syncRunId={} syncType={} triggerSource={}`
- `MES master data sync started taskId={} syncRunId={} syncType={} triggerSource={}`
- `MES master data sync completed taskId={} syncRunId={} syncType={} fetchedCount={} validCount={} invalidCount={} duplicateCount={} upsertedCount={} disabledCount={} deletedCount={} durationMs={}`
- `MES master data sync failed taskId={} syncRunId={} syncType={} durationMs={}`，并带异常堆栈。

不得记录完整 MES 主数据响应体、完整请求、完整响应、token、Authorization、Cookie 或 client secret。

### 7. 运维文档

需要更新：

- `docs/QPO-MES-WMS对接/10-MES工艺材料主数据同步说明.md`
- `docs/QPO日志模块/03-日志运维说明.md`
- `docs/QPO日志模块/04-主流程文件日志规范化开发文档.md`

文档新增说明：

- 手动同步接口现在是异步提交，返回 `syncRunId` 后需要通过状态查询接口看结果。
- 前端“失败后刷新又成功”的旧现象来自 HTTP 超时，不代表后台同步必然失败。
- `disabledCount` 只代表 MES 返回但状态停用。
- `deletedCount` 只代表 MES 全量成功后缺失的 MES 来源记录硬删除。
- 定时策略支持 `FIXED_DELAY` 和 `DAILY_TIME`。
- `DAILY_TIME` 按 Asia/Shanghai 每天最多执行一次，服务器错过时间后当天补跑一次。

## 测试口径

### 后端 controller 测试

- `POST /api/dict/materials/sync-mes` 仍使用原路径和 `engineer:dict:material-manage` 权限。
- `POST /api/dict/process-categories/sync-mes` 仍使用原路径和 `engineer:dict:process-manage` 权限。
- POST 响应包含 `success=true`、`submitted=true`、`alreadyRunning=false`、`syncRunId`、`syncType`、`status=RUNNING`、中文“已提交”消息。
- controller 不再调用旧阻塞式 `syncMaterialsManual` / `syncProcessTypesManual`。
- active run 已存在时，POST 返回 `alreadyRunning=true` 和已有 `syncRunId`。
- 新增 GET latest / by id 接口按同步类型隔离，不能用材料权限查询工艺状态，反之亦然。

### 后端 service 测试

- submit 创建 `RUNNING` run 并把 worker 投递到 executor，方法立即返回提交结果。
- worker 成功时更新 `SUCCESS`、`finishedAt`、所有计数和 `message=OK`。
- worker 失败时更新 `FAILED` 和脱敏 message，运行记录不丢失。
- executor 投递失败时 run 变为 `FAILED`，不会留下不可追踪的 active run。
- 同类型 active run 已存在时，不启动第二个 worker。
- 数据库唯一 active 索引冲突时，submit 能重新查询已有 active run 并返回 `alreadyRunning=true`。
- stale `RUNNING` 超过配置阈值时会先标记失败，再允许新任务提交。
- 材料同步继续验证：MES 状态停用计入 `disabledCount`，MES 全量缺失硬删除计入 `deletedCount`，`LOCAL` 来源不删除。
- 工艺同步继续验证：MES 状态停用计入 `disabledCount`，MES 全量缺失硬删除工艺大类，并先删除其下具体工艺，计入 `deletedCount`。
- 空有效数据仍失败，且不执行缺失删除。
- 审计摘要不包含完整 MES 响应、token、Authorization、Cookie 或 client secret。

### 调度测试

- `FIXED_DELAY` 默认兼容现有行为：首次运行受 `startupDelayMs` 控制，后续按 `fixedDelayMs` 间隔。
- `DAILY_TIME=02:00:00` 在 01:59 不运行。
- `DAILY_TIME=02:00:00` 在 02:00 或之后，且当天没有定时结果时提交一次。
- 当天已有 `lastScheduledRunAt` 时不再自动提交，即使上一轮失败也不重复。
- 服务 03:00 启动且当天没有定时结果时补跑一次。
- `enabled=false` 时两种模式都不运行。
- 同类型 active run 存在时，定时巡检不重复投递 worker。
- worker 完成定时 run 后，更新 `lastScheduledSyncRunId`、`lastScheduledStatus` 和安全 `lastScheduledMessage`。

### Flyway / 迁移测试

- 新迁移使用下一个未占用版本，当前应从 `V25__...` 开始。
- 不修改 `V1__qpo_baseline_schema.sql`、`V2__qpo_baseline_seed_data.sql` 或既有已执行迁移。
- 新增 `schedule_mode` 默认 `FIXED_DELAY`。
- 新增 `daily_run_time` 默认 `02:00:00` 或允许为空但服务归一化为 `02:00:00`。
- 新增 active run 唯一索引，限定 `status='RUNNING'`。
- 迁移测试确认 `V24__mes_master_data_sync_deleted_count.sql` 保持 `deleted_count` 独立存在。

### 前端测试

- `dictApi.syncMesMaterials()` 继续 POST `/api/dict/materials/sync-mes`。
- `dictApi.syncMesProcessTypes()` 继续 POST `/api/dict/process-categories/sync-mes`。
- 新增 latest / by id 查询 API。
- 点击同步后成功提示“已提交”，不等待最终计数才解除 POST 请求 pending。
- active run 为 `RUNNING` 时，材料或工艺同步按钮保持 disabled/loading。
- 轮询到 `SUCCESS` 后显示抓取、更新、停用、删除计数，并刷新列表。
- 轮询到 `FAILED` 后显示脱敏失败消息，并停止 loading。
- 页面加载时如果 latest run 为 `RUNNING`，自动恢复轮询。
- 定时策略弹窗包含 `FIXED_DELAY` / `DAILY_TIME` 模式选择。
- `FIXED_DELAY` 保存 `scheduleMode/startupDelayMs/fixedDelayMs`。
- `DAILY_TIME` 保存 `scheduleMode/dailyRunTime`。

### 文档 characterization 测试

- MES 主数据同步说明包含“异步提交”“syncRunId”“状态查询”“deletedCount”“disabledCount”。
- 日志运维说明包含 submitted/started/completed/failed 四类文件日志口径。
- 日志规范文档明确不得记录 MES 完整响应、token、Authorization、Cookie、client secret。
- 文档说明 `DAILY_TIME` 每天最多执行一次，并采用 Asia/Shanghai。

### 回归验证命令

实现完成后至少运行：

```powershell
cd quote-backend
./mvnw test -Dtest=MesMasterDataSyncControllerTest,MesMasterDataSyncScheduleConfigTest,MesMaterialMasterSyncServiceTest,MesProcessTypeSyncServiceTest,MesMasterDataMigrationTest,MesMasterDataDocumentationCharacterizationTest
```

```powershell
cd quote-frontend
npm test -- mes-master-data-sync.workflow.test.js adminDuplicateClickGuard.test.js
```

最后运行完整后端测试和前端相关测试，确认本次改动没有扩大回归面。

## 待用户确认的实现口径

当前设计已替你先定三个默认口径，除非你要求调整，后续实现按以下执行：

- 每天固定时间模式按 Asia/Shanghai 判断日期和时间。
- 当天定时同步失败后不自动重试，避免 MES 或数据中心异常时反复打接口；需要重试时走手动同步。
- `mes_master_data_sync_run` 作为唯一主任务表，不额外写 `system_task` 任务记录。

