# 待产零件每日定时自动下发 MES 开发规划

> 目标：待产零件页面增加一个系统级定时任务，每天 17:00（Asia/Shanghai）自动把"待下发的待产行"批量下发 MES 生产，与现有手动"下发MES生产"共用同一套组包、校验、幂等和状态回写链路。
> 本文是规划稿，未动代码。文中标注"待确认"的条目需要业务侧拍板后，才进入实施。
> 最后更新：2026-10-27

## 1. 现状事实（已核对代码）

### 1.1 页面与接口

| 项 | 现状 |
| --- | --- |
| 页面 | `quote-frontend/src/components/ProductionItemManage.vue`，路由 `/production-items` |
| 列表接口 | `GET /api/order-delivery/production-items`，权限 `market:production-item:view` |
| 列表口径 | `ProductionItemService.buildQuery`：`alloc.allocation_type = 'PRODUCTION_REQUIRED'` + 数量 > 0 + `COALESCE(alloc.status,'ACTIVE') = 'ACTIVE'` + `alloc.discarded = false`，默认排除 `mes_production_status = 'SUCCESS'` |
| 手动下发接口 | `POST /api/order-delivery/production-items/mes-production`，权限 `market:production-item:mes-production-submit` |
| 单次上限 | `MAX_PRODUCTION_DISPATCH_BATCH_SIZE = 1000`，前端同样限制 |

### 1.2 下发链路

```text
用户勾选 -> OrderDeliveryController.submitMesProduction
        -> SystemTaskService.submitMesProductionBatchTask(allocationIds)
             normalizeAllocationIds（去重、>0、<=1000）
             assertCanAccessAllocations（数据权限）
             filterDiscardedAllocationIds
             createTask("MES_PRODUCTION_DISPATCH") + 操作日志 PRODUCTION_ITEM_MES_PRODUCTION_SUBMIT
             systemTaskExecutor.execute(...)
        -> MesProductionDispatchService.dispatchBatch(allocationIds, taskId)
             selectBatchIdsForUpdate（行锁）
             SalesOrderAllocationContextLoader 加载销售订单上下文
             buildSalesOrderAssemblyRelationsBatch（组合件关系批量装配）
             逐行 skipReason 判定
             按 groupKey 分组 -> 每组一次数据中心请求 MES_PRODUCTION_DISPATCH
             逐行回写 mes_production_status
             返回 {total/success/failed/skipped/rows}
```

### 1.3 已有的幂等与防重（不需要重造）

- `mes_production_status` 状态机：`PENDING / PROCESSING / SUCCESS / FAILED / RESULT_UNKNOWN / SKIPPED`。
- `MesProductionDispatchService.markProcessing` 是 CAS 更新（仅当状态为空或不在 `PROCESSING/SUCCESS` 时才置为 `PROCESSING`）。
- `order_delivery_ticket_allocation.mes_production_request_id` 有唯一索引。
- `MesBatchStateService.reserveProductionBatch` 用请求指纹做批次级幂等。
- `skipReason` 已经会跳过：已废弃、非 `PRODUCTION_REQUIRED`、`SUCCESS`、`PROCESSING`、`RESULT_UNKNOWN`、未直连销售订单行、数量 <= 0、物料号为空。

**结论：定时任务不需要重新实现下发逻辑，只需要"挑行 + 分批 + 调度 + 记录"。**

### 1.4 已有的定时任务范式（直接照抄即可）

`@EnableScheduling` 已开启（`QuoteSystemApplication`）。现有 4 个 `@Scheduled`：

| 类 | 模式 |
| --- | --- |
| `MesMasterDataSyncService` | `fixedDelay` 轮询 + `MesMasterDataSyncScheduleConfigService.shouldRun()` 判断"今天这个点是否该跑" |
| `SalesOrderChangeNoticeAutoDispatchScheduler` | `fixedDelay` 轮询 + `claimAutoPending()` 认领 |
| `U9MasterDataSyncService` / `AuditLogRetentionService` | `fixedDelay` 轮询 |

其中 `MesMasterDataSyncScheduleConfigService` 是**最贴合本需求的模板**：数据库配置表（`enabled / schedule_mode / daily_run_time / startup_delay_ms / fixed_delay_ms / last_scheduled_run_at / last_scheduled_status / last_scheduled_message`）+ `DAILY_TIME` 模式 + 前端"定时同步策略"弹窗 + 配置变更审计。本次照这个结构做，运维习惯一致。

### 1.5 数据权限在定时场景的行为（重要）

`DataScopeService.currentSnapshot()` 在 `AuthContext.get() == null` 时返回 `withoutContext()`，所有 scope 校验直接放行。

所以定时任务天然是**系统级全量范围**，不受某个销售员的数据权限限制。这一点必须写进需求口径，否则业务侧会误以为"只下发我负责的客户"。

## 2. 需求拆解

用户原话："每天下午五点自动下发一次"。真正要定的是 5 件事：

| # | 问题 | 已定口径 | 状态 |
| --- | --- | --- | --- |
| 1 | 触发时间 | 每天 17:00，时区 Asia/Shanghai，可配置 | 已确认 |
| 2 | 下发哪些行 | 只发 `PENDING`（含状态为空），不含 `FAILED / PROCESSING / RESULT_UNKNOWN / SUCCESS / SKIPPED` | 已确认 |
| 3 | 是否补发历史积压 | **是。所有待下发行，含历史积压**，按交期升序；昨天没发成功的今天继续发 | 已确认 |
| 4 | 是否自动重试 `FAILED` | **不重发**。`include_failed` 固定 `FALSE`，`FAILED` 由人工在页面看原因后手动重发 | 已确认 |
| 5 | 单批行数 | 200 行/批，只控瞬时负载，**不设每日总量上限** | 已确认 |
| 6 | 配置入口 | 待产零件页顶部按钮 + 弹窗（见 7.1） | 已确认 |

补充建议：**不做"按客户/按人"的差异化定时**，一期只做一条全局策略。多策略会把配置、并发、优先级全部放大一个量级。

## 3. 目标与非目标

### 3.1 目标

- 每天 17:00 自动挑选待下发的待产行，按批次调用现有 `dispatchBatch` 完成 MES 生产下发。
- 定时下发与手动下发共用同一套组包、校验、幂等、状态回写和审计，**不新增第二套下发实现**。
- 每次定时执行落一条可追溯的系统任务记录（`system_task`），页面可按"生产下发状态"筛选查看结果。
- 定时策略可开关、可改时间、可改批大小，改完不重启。
- 执行失败、部分失败、被限流跳过，都能在配置表里看到 `last_status / last_message`。

### 3.2 非目标

- 不做 MES 生产进度回调、撤销、回滚。
- 不改 `dispatchBatch` 内部组包逻辑（groupKey 分组、组合件关系、BOM 口径全部复用）。
- 不做多策略（多客户、多时间点、优先级队列）。
- 不做"失败自动无限重试"。
- 不做定时任务的独立运维页面，只做待产零件页上的一个策略弹窗。
- 不新增 `common:*` 权限。

## 4. 核心口径设计

### 4.1 候选行 SQL（新增 Mapper 方法）

```sql
SELECT alloc.id
FROM order_delivery_ticket_allocation alloc
WHERE alloc.allocation_type = 'PRODUCTION_REQUIRED'
  AND alloc.source_type = 'SALES_ORDER'
  AND COALESCE(alloc.status, 'ACTIVE') = 'ACTIVE'
  AND COALESCE(alloc.discarded, FALSE) = FALSE
  AND COALESCE(alloc.qty, 0) > 0
  AND COALESCE(alloc.ticket_qty, 0) > 0
  AND alloc.material_no IS NOT NULL AND TRIM(alloc.material_no) <> ''
  AND COALESCE(NULLIF(TRIM(alloc.mes_production_status), ''), 'PENDING') = 'PENDING'
ORDER BY alloc.source_order_id ASC, alloc.delivery_date ASC NULLS LAST, alloc.id ASC
LIMIT #{limit}
```

要点：

- **必须带 `LIMIT`**，不能"取全表再在内存里切批"，否则积压多时会 OOM。
- **`ORDER BY alloc.source_order_id`**：`dispatchBatch` 的 `groupKey` 含销售订单号，同一销售订单的行连续排列后，按行数切批时**同一订单几乎不会跨批**，避免同一订单被拆成两次请求。这一条零成本，效果比调 batch_size 更直接。
- 加 `source_type = 'SALES_ORDER'`：`SalesOrderAllocationContextLoader.isDirectSalesOrder()` 只认 `SALES_ORDER`，RFQ 来源的行在 `dispatchBatch` 里必然被 `skipReason` 跳过。提前排除，避免每轮都把注定跳过的历史行捞出来刷一遍日志。
- `delivery_date ASC` 让交期早的先发，符合生产排产直觉。
- 数量、物料号、`ticket_qty` 的过滤是把 `skipReason` 里能提前判定的部分下沉到 SQL，减少无效行进入批次（`dispatchBatch` 内部仍会再判一次，双保险，不改原逻辑）。
- `RESULT_UNKNOWN` 一定排除：这类行 MES 侧可能已建单，自动重发会造成重复生产任务，只能人工核对。

### 4.2 分批策略（回答"是否批量处理"）

#### 设计原则

> **限制的是"单批瞬时负载"，不是"当天总量"。当天 17:00 存在的待下发行必须全部处理完，不允许堆到第二天。**

这两件事必须解耦：

- 担心"单批行数过多拖慢系统、影响其他用户" → 用**小批次 + 批间隔**解决，让负载摊平，而不是减少总量。
- 担心"当天发不完" → 用**循环跑到空**解决，而不是设上限。

所以最终口径：**批要小，但必须跑完。**

#### 三层结构

```text
第 1 层：一次定时执行 = 1 个 system_task（type = MES_PRODUCTION_AUTO_DISPATCH）
第 2 层：循环取行，每批 batch_size 行 -> dispatchBatch(ids, taskId)，批间 sleep
第 3 层：dispatchBatch 内部按 groupKey 再分组，每组一次数据中心请求（既有逻辑，不改）
```

#### 关键事实：请求数是按"销售订单"算的，不是按"行数"算的

`MesProductionDispatchService.buildProductionPlan` 里：

```java
String groupKey = String.join("|", customerCode, customerName, internalProjectNo, saleOrderNo);
```

`dispatchPlanGroup` 每个 groupKey 发一次 HTTP 请求。也就是说：**同一个销售订单下的所有待产行，会合并成一次请求**。

这解释了为什么"单批行数"和"系统负载"不是线性关系：

- 200 行如果来自 15 个销售订单 → 15 次请求；500 行如果也来自 15 个订单 → 还是 15 次请求。
- 把 `batch_size` 从 500 压到 100，**不会减少请求数**，反而因为同一订单被切到两批而多发请求。
- 所以 `batch_size` 的作用不是"降低 MES 压力"，而是**控制单次 DB 事务规模、单次请求报文大小、行锁持有时间**——这才是"不影响其他用户"的关键。

#### 降载三件套

| 手段 | 建议值 | 真正解决的问题 |
| --- | --- | --- |
| **串行执行**（独立单线程，不并发） | 固定 | 任一时刻只有 1 个请求在飞，其他用户不会被并发压垮 |
| **单批行数** `batch_size` | **200**（可调 100~1000） | 单次 `selectBatchIdsForUpdate` 的行锁范围、单次请求报文、单次事务耗时 |
| **批间隔** `batch_interval_ms` | **3000ms** | 批与批之间释放 DB 连接、行锁、网络；这是"让其他用户感觉不到"的核心 |
| 挑行排序 | 按 `source_order_id` | 同一订单连续，切批时不跨订单，避免同一订单发两次请求 |

三者叠加后，其他用户感受到的只是"每 3 秒有一次 200 行的小事务"，而不是"一次几千行的大事务"。

#### 必须跑完的保障

```text
loop:
  ids = selectAutoDispatchCandidateIds(batch_size)   # 只取 PENDING
  if ids 为空: break
  dispatchBatch(ids, taskId)
  sleep(batch_interval_ms)
  if 连续失败批数 >= failure_breaker: 熔断 break
  if 轮次 >= max_rounds: 记 WARN break
```

- **不设每日行数上限**：当天有多少 PENDING 就发多少。
- `max_rounds`（默认 50，即 50×200 = 10000 行）只是**防死循环兜底**，不是业务上限。正常业务量远达不到，触发了说明异常（比如 17:00 后仍在持续大量新增），记 WARN 并在配置表标 `PARTIAL`。
- **天然支持断点续跑**：`dispatchBatch` 是逐行回写 `SUCCESS / FAILED` 的。如果执行中途服务重启，重新触发时 `SUCCESS` 的行已被状态机排除，只会捞到剩下的 `PENDING`，自动从断点继续，不需要额外记录游标。
- **不设时长截断**：跑 2 小时也让它跑完（跑在独立线程上，不占用户线程）。但超过 `warn_duration_ms`（默认 60 分钟）记 WARN，方便发现"是不是 MES 变慢了"。

#### 预期耗时（正常情况）

假设单请求 1~3 秒返回，200 行/批 ≈ 10~20 个订单：

| 当天待发行数 | 批数 | 预计耗时 |
| --- | --- | --- |
| 200 | 1 | 30 秒内 |
| 1000 | 5 | 3~5 分钟 |
| 3000 | 15 | 10~15 分钟 |
| 10000 | 50 | 35~50 分钟 |

最坏情况（MES 全部 60s 超时）：由 `failure_breaker=3` 熔断，不会真的跑 50 批 × 10 分钟。

#### 线程池隔离

`systemTaskExecutor` 只有 core 2 / max 4 / queue 50，定时任务可能跑几十分钟，会挤占手动下发。新增独立 `productionAutoDispatchExecutor`（**单线程**，`ThreadPoolTaskExecutor`），定时任务只跑在这条线程上，与手动任务互不影响。

#### 防并发

- 进程内：`AtomicBoolean running` 守卫，上一轮没跑完直接跳过（与 `MesMasterDataSyncService` 一致）。
- 跨实例（将来多副本）：配置表 `last_run_date` 的 CAS 更新认领（`UPDATE ... SET last_run_at=now(), last_run_date=today WHERE last_run_date < today`），影响行数为 1 才继续。现在单实例也用同一套，避免以后改。
- 与手动下发互斥：靠 `markProcessing` 的 CAS + `reserveProductionBatch` 指纹，同一行不会被两个任务同时发出去。

### 4.3 失败处理

| 场景 | 处理 |
| --- | --- |
| 单行 `FAILED` | 回写 `FAILED` + 摘要，**不自动重发**（`include_failed` 固定关闭），由人工在页面筛选 `下发失败` 后手动重发 |
| 单行 `RESULT_UNKNOWN` | 回写 `RESULT_UNKNOWN`，**永不由定时任务自动重发**，必须人工"核对结果" |
| 整批 HTTP 失败 | 该批全部 `FAILED`，继续下一批（不因一批失败终止整轮） |
| 连续 N 批全失败 | 熔断：终止本轮，`last_status=FAILED`，避免把 MES 打挂。**这是唯一会提前结束整轮的情况** |
| 达到 `max_rounds` 仍有剩余 | 记 WARN，`last_status=PARTIAL`，剩余次日继续（正常业务量不会触发） |
| 执行时长超过 `warn_duration_ms` | **不截断**，只记 WARN 并继续，用于发现 MES 变慢 |
| 服务重启中断 | 无需处理：逐行回写状态，重新触发只捞剩下的 `PENDING`，自动续跑 |
| 配置未开启 | 直接 return，不产生任何记录（避免日志噪声） |

### 4.4 结果可见性

- `system_task`：新增任务类型 `MES_PRODUCTION_AUTO_DISPATCH`，`created_by = 'SYSTEM'`，`request_json` 记 `{trigger:'SCHEDULE', batchSize, intervalMs}`，`result_json` 复用 `dispatchBatch` 的 summary 结构（total/success/failed/skipped/rows），额外加 `trigger / rounds / durationMs / remainingCount`。
- 待产零件页：现有"生产下发状态"筛选已能查到 `FAILED`，无需新页面。建议补一个"最近一次定时下发"的轻提示（可选，见 7.3）。
- 配置表：`last_run_at / last_task_id / last_status / last_message / last_total / last_success / last_failed / last_skipped / last_rounds / last_duration_ms`。

## 5. 数据模型

新增迁移 `V66__production_item_auto_dispatch_config.sql`（当前最高为 V65，**不改历史迁移**）。

```sql
CREATE TABLE IF NOT EXISTS production_item_auto_dispatch_config (
    id                    VARCHAR(60) PRIMARY KEY,
    enabled               BOOLEAN      NOT NULL DEFAULT FALSE,
    schedule_mode         VARCHAR(20)  NOT NULL DEFAULT 'DAILY_TIME',
    daily_run_time        TIME         NOT NULL DEFAULT '17:00:00',
    batch_size            INTEGER      NOT NULL DEFAULT 200,
    batch_interval_ms     INTEGER      NOT NULL DEFAULT 3000,
    max_rounds            INTEGER      NOT NULL DEFAULT 50,
    warn_duration_ms      INTEGER      NOT NULL DEFAULT 3600000,
    failure_breaker       INTEGER      NOT NULL DEFAULT 3,
    include_failed        BOOLEAN      NOT NULL DEFAULT FALSE,
    last_run_at           TIMESTAMP,
    last_run_date         DATE,
    last_task_id          BIGINT,
    last_status           VARCHAR(20)  NOT NULL DEFAULT 'NEVER',
    last_message          VARCHAR(500),
    last_total            INTEGER,
    last_success          INTEGER,
    last_failed           INTEGER,
    last_skipped          INTEGER,
    last_rounds           INTEGER,
    last_duration_ms      BIGINT,
    updated_by            VARCHAR(60),
    updated_at            TIMESTAMP    NOT NULL DEFAULT NOW()
);

INSERT INTO production_item_auto_dispatch_config (id) VALUES ('MES_PRODUCTION_AUTO_DISPATCH')
ON CONFLICT (id) DO NOTHING;
```

字段说明：

| 字段 | 默认 | 说明 |
| --- | --- | --- |
| `batch_size` | 200 | 单批行数，控制单次事务/请求规模 |
| `batch_interval_ms` | 3000 | 批间隔，核心降载手段 |
| `max_rounds` | 50 | **仅防死循环兜底**，不是业务上限（50×200=10000 行） |
| `warn_duration_ms` | 3600000 | 超 60 分钟只记 WARN，**不截断执行** |
| `failure_breaker` | 3 | 连续 3 批全失败熔断，唯一会提前结束整轮的条件 |
| `include_failed` | FALSE | 固定关闭，`FAILED` 不自动重发 |
| `last_rounds` | — | 上次执行实际跑了多少轮 |
| `last_duration_ms` | — | 上次执行总耗时，用于观察是否变慢 |

**没有 `daily_max_rows` 字段**：当天有多少待下发行就发多少，不设总量上限。

说明：

- 单行配置表（固定 id），一期不做多策略。
- `enabled` 默认 `FALSE`：**上线后必须由管理员显式开启**，避免迁移一执行就自动往 MES 发数据。
- 索引复用现有 `idx_delivery_allocation_mes_production_status(mes_production_status, allocation_type, id DESC)`，候选行查询走这个索引，不需要新建索引。

## 6. 后端设计

### 6.1 新增类

| 类 | 职责 |
| --- | --- |
| `ProductionItemAutoDispatchScheduler` | `@Scheduled(fixedDelayString="${erp.production-auto-dispatch.check-ms:60000}")`，轮询判断"今天 17:00 是否已跑" |
| `ProductionItemAutoDispatchConfigService` | 配置读写、`shouldRun()`、`claimRun()`（CAS）、`updateResult()`、配置变更审计；照 `MesMasterDataSyncScheduleConfigService` 写 |
| `ProductionItemAutoDispatchService` | 挑行 → 分批 → 调 `dispatchBatch` → 汇总 → 落 `system_task` |
| `ProductionItemAutoDispatchConfigMapper` / `Entity` / `DTO` / `Req` | 常规 CRUD |

### 6.2 改动点

| 文件 | 改动 |
| --- | --- |
| `OrderDeliveryTicketAllocationMapper` | 新增 `selectAutoDispatchCandidateIds(limit, includeFailed)` |
| `SystemTaskService` | 新增 `submitAutoMesProductionDispatchTask()`：**不做数据权限校验**（无用户上下文），`created_by='SYSTEM'`，审计 `trigger=SCHEDULED` |
| `MesProductionDispatchService` | 不改。仅在 `summary()` 里可选追加 `trigger`（或由 AutoDispatchService 自己包一层 summary，推荐后者，零侵入） |
| `application.yaml` | 新增 `erp.production-auto-dispatch.check-ms: 60000`、`executor` 线程配置 |
| `erp.env.example` | 同步新增 `ERP_PRODUCTION_AUTO_DISPATCH_CHECK_MS` |

### 6.3 接口

```java
@GetMapping("/production-items/auto-dispatch/config")
@RequirePermission("market:production-item:mes-production-schedule")
public ProductionItemAutoDispatchConfigDTO getAutoDispatchConfig();

@PutMapping("/production-items/auto-dispatch/config")
@RequirePermission("market:production-item:mes-production-schedule")
public ProductionItemAutoDispatchConfigDTO updateAutoDispatchConfig(@RequestBody ProductionItemAutoDispatchConfigReq req);

@PostMapping("/production-items/auto-dispatch/run-now")
@RequirePermission("market:production-item:mes-production-schedule")
public SystemTask runAutoDispatchNow();   // 运维兜底：立即跑一轮，便于上线验证
```

`run-now` 是必须的：上线当天不能等到 17:00 才知道有没有问题。

### 6.4 权限

| 权限编码 | 类型 | 父级 | 中文 | 默认授权 |
| --- | --- | --- | --- | --- |
| `market:production-item:mes-production-schedule` | BUTTON/API | `market:production-item:view` | 待产零件定时下发策略 | SUPER_ADMIN、MARKET_MANAGER |

普通 `MARKET_USER` 不授予：定时任务是系统级全量下发，属于管理动作。

## 7. 前端设计

### 7.1 入口位置：待产零件页顶部工具栏（已确认）

只在 `ProductionItemManage.vue` 顶部批量操作区加一个按钮，**不新增菜单、不新增路由、不改系统配置页**。

理由：

- 这是待产零件模块自己的行为，"为什么今天没自动发"的答案应该就在看这个页面的地方。
- 与现有 `MaterialProcessManage.vue` 的"定时同步策略"按钮位置完全一致（业务模块自带策略入口），运维心智统一。
- `SystemConfigManage.vue`（`/system-config`）目前只放"文件存储 / 出站接口 / U9配置 / 入站接口 / 数据中心登录"这类**跨模块的连接配置**，把业务定时策略塞进去会破坏它的定位；且它的权限是 `system:config:view`，而本功能应该由市场部管理员控制。

按钮位置：

```text
[客户] [图号] [版本] [生产下发状态] [搜索] [重置] [高级筛选]   |   [定时下发策略] [下发MES生产] [批量废弃]
                                                                    ^^^^^^^^^^^^^^ 新增（仅 ACTIVE 视图显示）
```

### 7.2 弹窗内容与操作逻辑

照 `MaterialProcessManage.vue` 的"定时同步策略"弹窗版式（`el-dialog` + `el-form` + `el-descriptions`）。

常用参数直接展示，技术兜底参数收进"高级参数"折叠区，避免运维被吓到。

```text
┌─ 待产零件定时下发策略 ────────────────────────────┐
│ 开启每日自动下发            [ 开关 ]              │
│ 每天执行时间                [ 17:00:00 ]          │
│ 单批行数                    [ 200 ]  (100~1000)   │
│ 批间隔                      [ 3000 ] ms           │
│ 连续失败熔断                [ 3 ] 批              │
│                                                   │
│ ▸ 高级参数                                        │
│   最大轮次                  [ 50 ]  （防死循环）   │
│   慢执行告警                [ 60 ] 分钟（不中断）  │
│                                                   │
│ ──────────────────────────────────────────────── │
│ 上次执行    2026-10-27 17:00:12                   │
│ 执行状态    成功（部分失败）                       │
│ 执行结果    总 128 行 / 成功 120 / 失败 6 / 跳过 2 │
│             共 1 轮，耗时 48 秒，剩余 0 行         │
│ 任务ID      10231                                 │
│ 提示：当天待下发行会全部处理完，不做每日数量限制。  │
│ 提示：系统级全量下发，不受个人数据权限限制。        │
│ 提示：FAILED 行不会自动重发，请在列表中筛选后手动下发。│
│                                                   │
│        [ 立即执行一次 ]      [ 取消 ] [ 保存 ]     │
└───────────────────────────────────────────────────┘
```

操作逻辑：

| 动作 | 行为 |
| --- | --- |
| 打开弹窗 | `GET /production-items/auto-dispatch/config` 拉当前配置 + 上次执行结果 |
| 保存 | `PUT .../config`，写审计 `PRODUCTION_ITEM_AUTO_DISPATCH_CONFIG_UPDATE`，**即时生效**（调度器每分钟轮询配置，不需要重启） |
| 关闭开关 | 保存后调度器直接 `return`，不产生任何任务和日志 |
| 立即执行一次 | `POST .../auto-dispatch/run-now`，二次确认后提交，返回 taskId，弹窗内展示"任务已提交"，前端轮询一次任务结果（或提示到任务ID查看） |
| 参数校验 | 时间必须 `HH:mm:ss`；`batch_size` 100~1000；`batch_interval_ms` 1000~60000；`failure_breaker` 1~10；`max_rounds` 1~500；`warn_duration_ms` 60s~8h |
| 权限 | 整个按钮 `v-permission="'market:production-item:mes-production-schedule'"`，无权限完全不渲染 |

### 7.3 页面辅助信息（可选，二期）

顶部加一行轻提示，降低"为什么没发"的沟通成本：

```text
定时下发：已开启 · 每天 17:00 · 上次 10-27 17:00 成功 120 条，失败 6 条（点击查看）
```

一期可以先不做，等业务跑一段时间看是否真有需求。

### 7.4 不改动的地方

- 不新增左侧菜单，不改 `router/index.js`。
- 不改列表表格列（"生产下发状态"已能筛 `PENDING/FAILED/SUCCESS`）。
- 不改"下发MES生产"手动按钮的交互与权限。

## 8. 日志与审计

| 动作 | 说明 |
| --- | --- |
| `PRODUCTION_ITEM_MES_PRODUCTION_AUTO_SUBMIT` | 定时轮提交（`trigger=SCHEDULED`，含 taskId、批数、行数） |
| `PRODUCTION_ITEM_MES_PRODUCTION_SUCCESS / FAILED` | 复用现有行级动作，summary 增加 `trigger=SCHEDULED` |
| `PRODUCTION_ITEM_AUTO_DISPATCH_CONFIG_UPDATE` | 策略变更（before/after 摘要） |

文件日志（低噪声）：

```text
Production auto dispatch skipped: not due yet
Production auto dispatch started taskId={} batchSize={} intervalMs={}
Production auto dispatch round started taskId={} round={} size={}
Production auto dispatch round finished taskId={} round={} success={} failed={} skipped={} durationMs={}
Production auto dispatch finished taskId={} rounds={} total={} success={} failed={} skipped={} remaining={} durationMs={}
Production auto dispatch slow run taskId={} durationMs={} thresholdMs={}
Production auto dispatch aborted taskId={} reason={} roundsWithoutSuccess={} lastRoundSize={} lastRoundSkipped={}
```

禁止：token、Authorization、完整请求体、完整响应体。

## 9. 测试方案

后端（新增）：

- `ProductionItemAutoDispatchConfigServiceTest`：默认 17:00、开关关闭不跑、同一天只跑一次、改时间生效、参数越界拒绝。
- `ProductionItemAutoDispatchSchedulerTest`：未到点跳过、到点执行、执行中重复触发跳过、跨天可再次执行。
- `ProductionItemAutoDispatchServiceTest`：只挑 `PENDING`、排除 `FAILED`（默认）/ 包含 `FAILED`（开启）、排除 `RESULT_UNKNOWN`、排除非 `SALES_ORDER` 来源、**循环跑到候选为空**、达到 `max_rounds` 记 WARN 并标 `PARTIAL`、一批失败不终止后续批次、连续失败熔断、**执行超过 `warn_duration_ms` 只记 WARN 不中断**、中断后重跑只捞剩余 `PENDING`。
- `ProductionItemAutoDispatchMigrationTest`：V66 存在、历史迁移未被修改（沿用现有 Flyway 策略测试写法）。
- `PermissionCatalogCharacterizationTest` / `AuditActionCoverageTests`：新权限点与新动作纳入清单。
- 契约：候选行 SQL 不得包含 `SUCCESS / PROCESSING / RESULT_UNKNOWN`，且必须带 `LIMIT` 和 `ORDER BY source_order_id`。

前端（扩展 `productionItemManage.test.js`）：

- 无权限时"定时下发策略"按钮隐藏。
- 弹窗默认值 17:00 / 200 / 3000 / 3。
- 高级参数默认收起，展开后可见最大轮次与慢执行告警。
- 保存后回显上次执行状态。

## 10. 分阶段实施

| 阶段 | 内容 | 验收 |
| --- | --- | --- |
| 1 | V66 迁移 + 配置表 + Entity/Mapper/DTO + 权限点 | 迁移可跑、默认关闭、权限清单更新 |
| 2 | 候选行 Mapper + `ProductionItemAutoDispatchService`（挑行、循环分批、跑到空、汇总、落 system_task） | 单测覆盖挑行、跑到空、熔断、`max_rounds` 兜底 |
| 3 | Scheduler + 配置服务（shouldRun / claimRun / updateResult） | 未到点不跑、一天一次、执行中不重入 |
| 4 | Controller 三个接口 + 前端策略弹窗 | 页面可开关、可改时间、可"立即执行" |
| 5 | 审计 + 日志 + 文档（更新 `docs/QPO-MES-WMS对接/09`、权限清单、日志运维说明） | 审计动作齐全、日志无敏感信息 |
| 6 | 联调与回归 | `mvnw test` 全绿（DB 环境）、`npm test`、`npm run build` 通过 |

## 11. 风险与处理

| 风险 | 影响 | 处理 |
| --- | --- | --- |
| 定时任务重复下发 | MES 出现重复生产工单 | 三重保险：候选 SQL 只取 `PENDING`、`markProcessing` CAS、`reserveProductionBatch` 指纹 + `request_id` 唯一索引 |
| 把 `RESULT_UNKNOWN` 自动重发 | 重复建单 | 候选 SQL 硬排除，代码里再加断言 |
| 单批过重拖慢系统 | 其他用户查询/操作变慢 | 单批 200 行 + 批间隔 3 秒 + 串行单线程 + 独立 executor；批间释放行锁与 DB 连接 |
| 积压量大导致跑很久 | 执行时间长 | 不截断，跑完为止（跑在独立线程）；`warn_duration_ms` 只告警；`last_duration_ms` 可观察趋势 |
| 17:00 后持续新增导致循环不结束 | 死循环 | `max_rounds=50` 兜底，触发记 WARN 并标 `PARTIAL` |
| 执行中途服务重启 | 部分行未发 | 逐行回写状态，重跑只捞剩余 `PENDING`，天然续跑 |
| 定时任务抢占手动下发线程 | 用户点"下发MES生产"没反应 | 独立单线程 executor |
| 多副本部署重复触发 | 同一行被两个实例抢 | 配置表 `last_run_date` CAS 认领 |
| 无用户上下文导致审计 `created_by` 为空 | 追溯困难 | `created_by='SYSTEM'`，审计 summary 带 `trigger=SCHEDULED` |
| 业务误以为"只发自己的客户" | 期望落差 | 页面弹窗文案明确写"系统级全量下发，不受个人数据权限限制" |
| 上线首日直接发生产数据 | 数据事故 | `enabled` 默认 `FALSE`，必须先"立即执行"验证一轮再开启 |

## 12. 完成定义

- 配置表存在、默认关闭、可在页面开关与改时间，改完不重启生效。
- 每天 17:00 自动执行一次，同日不重复执行。
- 只下发 `PENDING`，绝不自动重发 `FAILED / RESULT_UNKNOWN / SUCCESS / PROCESSING`。
- **当天所有待下发行全部处理完，不设每日数量上限，不堆到第二天。**
- 分批执行（默认 200 行/批、批间隔 3 秒），串行且跑在独立线程，不挤占手动下发。
- 每次执行落一条 `MES_PRODUCTION_AUTO_DISPATCH` 系统任务（`total=1`，跑完置 `progress=1`），轮次汇总写进任务结果和配置表，结果可查。
- 权限、审计、日志、文档、前后端测试齐全。

## 13. 待确认清单

已确认：

- 范围：所有待下发行（含历史积压），按交期升序。
- `FAILED`：不自动重发，人工处理。
- 时间：每天 17:00。
- 入口：待产零件页顶部按钮 + 弹窗。
- **不设每日下发总量上限**，当天必须跑完；只限制单批规模以避免瞬时负载影响其他用户。
- **单批行数 200 / 批间隔 3000ms** 已确认，无需再降到 `100 / 5000ms`。

## 14. 实施落地对照（2026-10-27 完成）

参数口径：`17:00` 每日一次、单批 `200` 行、批间隔 `3000ms`、最大轮次 `50`（仅防死循环）、慢执行告警 `60` 分钟（只告警不中断）、连续失败熔断 `3` 批、`include_failed=false`、默认关闭。

后端新增：

| 文件 | 作用 |
| --- | --- |
| `db/migration/V66__production_item_auto_dispatch_config.sql` | 配置表 + 默认行（`enabled=false`）+ 权限点 239 + 角色授权 |
| `entity/ProductionItemAutoDispatchConfig.java` | 配置表实体，主键固定 `MES_PRODUCTION_AUTO_DISPATCH` |
| `mapper/ProductionItemAutoDispatchConfigMapper.java` | 含 `claimDailyRun` / `resetDailyRunClaim` 两条 CAS 更新 |
| `dto/ProductionItemAutoDispatchConfigDTO.java`、`ProductionItemAutoDispatchConfigReq.java` | 读写契约 |
| `service/ProductionItemAutoDispatchConfigService.java` | 默认值、范围校验、`shouldRun`、跨实例认领、结果回写、审计 |
| `service/ProductionItemAutoDispatchResult.java` | 执行结果 record |
| `service/ProductionItemAutoDispatchService.java` | 分批循环、熔断、状态判定、异步提交、任务落库 |
| `service/ProductionItemAutoDispatchScheduler.java` | `@Scheduled(fixedDelayString)` 轮询 + 认领 + 提交 |
| `config/ProductionAutoDispatchExecutorConfig.java` | 独立单线程池 `productionAutoDispatchExecutor` |

后端改动：`OrderDeliveryTicketAllocationMapper`（新增 `selectAutoDispatchCandidateIds` / `countAutoDispatchCandidates`）、`OrderDeliveryController`（三个接口）、`PermissionService`（权限点 + 角色映射）、`AuditService`（两个动作标签）、`application.yaml`、`erp.env.example`。

前端改动：`services/deliveredItemApi.js`（三个方法）、`components/ProductionItemManage.vue`（顶部“定时下发策略”按钮 + 640px 策略弹窗 + 高级参数折叠 + 上次执行状态）。

测试：后端 `ProductionItemAutoDispatchConfigTest`(10)、`ProductionItemAutoDispatchServiceTest`(12)、`ProductionItemAutoDispatchSchedulerTest`(5) 共 27 例全绿；前端 `productionItemManage.test.js` 新增 6 例，全量 161 文件 / 847 例全绿；`vite build` 通过。

与规划的四处偏差（已按实现修正本文件）：

1. **一个执行只落一条系统任务**，不是每轮一条——轮次信息写进 `resultJson` 和配置表 `last_rounds`，避免 50 轮刷 50 条任务。
2. 候选 SQL 用 `EXISTS` 子查询校验 `sales_order_item.qty > 0 AND material_no` 非空，对齐 `MesProductionDispatchService.skipReason` 的真实口径；不校验 `ticket_qty`（该条件只在 legacy `dispatchOne` 路径生效）。
3. **熔断判定从“连续 N 批全失败”改为“连续 N 批没有任何一行成功”**（2026-10-27 部署前核验修正）。原判定要求 `成功数=0 且 跳过数=0`，但 `dispatchBatch` 对被跳过的行**不回写 `mes_production_status`**，候选 SQL 下一轮仍会返回它们；加上候选排序固定，恒跳过行会永远排在前面，导致每轮 `成功=0 / 跳过=N` 却永不熔断，一路空转到 `max_rounds=50`，并把排在后面的可下发行全部挡住。修正后纯跳过批次也计入无进展，最坏 3 轮（约 10 秒）收手并标 `FAILED`。
4. 候选 SQL 的 `EXISTS` 追加 `(alloc.source_order_id IS NULL OR item.sales_order_id = alloc.source_order_id)`，把“关联到别的销售订单”的错位行在 SQL 层就排除——这类行会被 `SalesOrderAllocationContextLoader` 判为 error 并整批跳过，属于第 3 条里最容易出现的恒跳过来源。`source_order_id` 为空时不加该限制，避免误伤通过 `allocation_id` 直连的合法行。
