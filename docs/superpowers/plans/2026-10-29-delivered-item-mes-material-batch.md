# 已提零件批量获取 MES 物料号：QPO 后端修复计划

## 目标与范围

修复 QPO「已提零件 > 批量获取物料号」现有后端在物料去重、批次回调匹配、重启恢复和结果覆盖上的问题。保留现有按钮、权限、批量接口、异步任务结构及 MES 请求/回调字段。

本计划只改 QPO 仓库。不得改造 MES 或数据中心，不得新增 QPO 直连 MES 的路径，也不新增删除代理、恢复 API、普通用户按钮、定时反查或物料分页全表扫描。MES 删除/任务状态核实继续由现有运维流程人工处理；QPO 对无法确认的请求保留原请求并安全等待。

仅当现有结果区无法表达待回调/待核对状态时，才考虑最小的前端文案调整；不改页面布局或交互入口。

## 已核实的 QPO 现状

- `MesMaterialDedupService.buildDedupKey` 目前只使用图号和版本，并对两者执行 `trim().toUpperCase(Locale.ROOT)`；MES 出站值保留 trim 后的原大小写。
- MES 物料请求包含 `material`；提单实体有 `materialName`。活动请求、提单和 waiter 的去重字段均为 `varchar(220)`。
- V70 的唯一索引覆盖 `PROCESSING`、`CALLBACK_PENDING`、`RESULT_UNKNOWN`。现有活动请求已经存有两字段键，切换代码而不迁移会使在途请求查找失效。
- `MesMaterialResolveResp.Item` 有 `taskNo`、图号、版本、状态和物料号，没有材质。批次行已保存 `mes_task_no`；批次 `item_key` 为 220 字符且在批内唯一。
- 批次 callback 当前以图号+版本拒绝重复行，并按同一键建立行索引；相同图号版本的不同材质会被折叠或覆盖。
- `ProductionItemMaterialNoRefreshService` 也写入 MES 请求台账，但当前请求没有去重键；批量获取不能看到这种活动请求。
- `MesMaterialProcessingRecoveryService` 在本地任务停止且台账仍为 `PROCESSING` 时会置为 `FAILED`，之后可能再次提交；这无法区分外部 POST 前后崩溃。
- callback 目前会在校验请求/taskNo 前跳过已确认物料号，并在批次行物料号不同时返回冲突；这与本次“有效结果允许覆盖并审计”的要求不符。

以上仅描述 QPO 当前代码；外部语义以用户确认的现有接口契约为准。

## 生产数据核验结果（2026-10-29，正式库只读）

用 `docs/ops/mes-material-identity-preflight.sql` 在正式库跑过一轮，结论是**迁移可以安全执行**：

| 查询 | 结果 | 含义 |
| --- | --- | --- |
| Q1 活动台账 | 15 行，全部 `CALLBACK_PENDING` / `MASTER` / 来自批次 / 已有 dedup_key | 无 `PROCESSING`、无 `RESULT_UNKNOWN`，没有卡死行 |
| Q2 重复身份 | **0 行** | 回填三字段键不会撞 `uk_delivery_mes_request_active_dedup` |
| Q3 材质可证明性 | **13 个批次全部 PROVABLE**（stored == computed，共 45 行，含 8 行、11 行、16 行的大批次） | 在途行全部能证明出站材质未变，可直接回填三字段键 |
| Q4 waiter/master 不一致 | 0 行 | follower 关系没有挂错，无需拆关系 |
| Q5 刷新料号在途行 | 0 行 | 当前没有刷新料号在途请求；`request_role` 过滤缺陷是潜在问题而非现存数据问题 |

这次核验同时证实了两个实现假设，不必再当待确认项：

- **批次行顺序即请求 items 顺序**：13 个批次（含 16 行的 5578、11 行的 5575）指纹全部一致，说明按 `mes_material_batch_item.id` 升序重建 items 与当初出站顺序完全吻合。
- **SQL 重建与 Java 序列化逐字节等价**：真实数据里覆盖了中文、`/`、`,`、空格、`R/F-L/T` 这类符号、两行 `customerRequirements`、空备注，哈希全部对上。

因此第 2 步的「二分回填」在当前数据下退化为「全量回填」；保留旧键的分支降级为**应急路径**，只在部署前复跑 Q3 出现 `UNPROVABLE` 时启用。

## 保持不变的边界

- 保持现有权限 `market:delivered-item:mes-sync` 和后端数据范围检查，不新增权限。
- 不更名或删除 `erp.*` 等兼容字段，不改变 QPO、数据中心、MES 的 API 形状。
- 不把客户或项目加入物料身份；现有客户/项目请求分组继续保留。
- 不查询历史成功提单来推断物料号。
- 不自动超时重提，不把 `PUSHED` 当作 QPO 写回成功，也不调用 MES 删除。
- 图号和版本继续沿用现有大小写归一化，避免本次顺便改变既有匹配行为。材质先按出站规则 trim，空值归一；在确认 MES 材质大小写语义前，不对材质做大小写折叠。
- 不要求即时成功行一定返回 `taskNo`；仅对需要后续 callback 的响应，`taskNo` 必须非空、长度合法且在该 `bizId` 批次内唯一。
- 前端默认不改：`DeliveredItemManage.vue` 已内置 `RESULT_UNKNOWN` 的「结果待核对」文案、标签色与筛选，本次状态口径变化落在既有表达范围内；仅在实现中发现确实无法表达时才按第 9 行做最小文案调整。
- 不改 `MesMaterialSyncService.syncMaterialNo` 单条入口：`src/main` 中无调用方（仅测试引用），统一其口径属扩大改动面，若确需处理另开任务。

## 实施前需确认的现有接口事实

- MES 是否把材质大小写视为不同身份；确认前 QPO 保留材质原大小写，不自行折叠。
- `taskNo` 是否在同一 `bizId` 内唯一稳定，且 callback 重推沿用同一值。若无法确认，QPO 不按有歧义的行写回，保留 `RESULT_UNKNOWN`；不要求 MES 改契约。
- 数据中心现有 endpoint 是否把 `1_030_900_002` / `1_030_900_003` 原样传到 QPO。只解析实际可见的现有响应；不可见时按未知结果保留原请求，不要求数据中心新增能力。

## 实施步骤

### 1. 先加针对性特征测试并冻结现有契约

检查当前 QPO controller、批量请求组装、MES 响应校验、请求预留和 callback 入口。用测试固定现有路由、权限、请求 JSON、异步任务形状及 callback JSON。

先补能复现本次问题的测试，不先重写状态机：

- 不同材质的同图号版本不能共用活动主请求或互相 fan-out。
- callback 中重复 `taskNo` 必须显式拒绝，不能静默覆盖批次索引。
- 已有确认值可被当前有效 MES 结果更新；失效旧请求不能覆盖另一个请求已经确认的结果。
- abandoned 本地任务对应的 `PROCESSING` 请求不能变成可直接重提的普通失败。
- 活动刷新料号请求与批量获取不能对同一物料身份并发创建。

### 2. 升级 QPO 去重身份并安全迁移存量

将 QPO 活动请求身份改为“图号 + 版本 + 材质”。材质使用提单的 `material_name`，按出站规则 trim，空值一致；图号和版本沿用当前 uppercase 规则。

- 使用无歧义的长度前缀编码表示三个字段，不直接拼接可含分隔符的原值。
- 在 V71 中将 `order_delivery_ticket.mes_dedup_key`、`order_delivery_ticket_mes_request.dedup_key`、`order_delivery_ticket_mes_waiter.dedup_key` 扩为 512（长度依据见本节末尾），并按下方策略回填活动主请求键及对应 ticket/waiter 键。V70 及更早 migration 不得修改。
- 回填需覆盖 V70 活动状态中的 `RESULT_UNKNOWN`，也要纳入仍活动的刷新料号台账行；不能只迁移新代码写入的数据。
- 回填 waiter 时先比较 waiter 与 master 的三字段身份。生产基线 Q4 = 0 行，即当前所有 waiter 与 master 身份一致，正常情况下直接按 master 身份回填即可；下列判断只作为异常兜底：身份相同才保留关系并更新键，身份不同且能证明 waiter 没有独立 MES 行级请求时，解除错误的 follower 关系、将其恢复为可单独获取状态；若无法证明没有外部请求，保留待核对状态，不释放锁、不自动重提。
- V6 批次行没有材质快照，`mes_integration_batch.request_fingerprint` 是唯一可用的旁证：它由物料流程真实写入（`MesMaterialSyncService` 组装出站请求后调用 `batchFingerprintService.fingerprint(req)`），覆盖 `material`。指纹是 Jackson（属性名排序 + NON_NULL）序列化后的 SHA-256，SQL 迁移里不能算，但**打包前的核对可以**：`docs/ops/mes-material-identity-preflight.sql` 的 Q3 按同一键序逐字节重建请求 JSON（`to_jsonb` 保证转义一致），用 PostgreSQL 11+ 内置 `sha256()` 重算并与库中值比对，输出 `PROVABLE` / `UNPROVABLE`。该实现先在本地两组构造数据上与 Java 侧 `MesMaterialIdentityBackfillPreflightTest` 交叉验证（哈希逐字节一致，含引号、换行、空版本号、客户编码、两行 `customerRequirements`），再在正式库 13 个批次 45 行上全部命中。
- 回填策略：**默认全量回填**。2026-10-29 的生产核验是 Q2 = 0 行、Q3 = 13/13 PROVABLE，所以 V71 直接按「图号 + 版本 + 材质」覆盖活动行的键即可，不需要在迁移里嵌指纹判断，也不需要为在途行保留旧键。
- 「保留旧两字段键」降级为应急路径，只在**部署前复跑 Q3 出现 `UNPROVABLE`** 时启用：该批次行不猜材质、保留旧键、列入人工核对。复跑方法见本节末尾的部署前检查。
- 不要为了凑齐回填而在 SQL 里猜材质或直接覆盖无法证明的行。核验通过不等于永远通过——从核验到部署之间仍可能有新的在途请求，所以部署前必须再跑一次。
- 刷新料号台账行：当前生产数据里没有在途行（Q5 = 0 行），所以回填时不存在这类行。若部署前复跑出现，则用当前提单的三字段身份回填并标注为推定值——留空等于仲裁失效，比推定更危险。推定错误的代价有限：真正的旧身份若未被锁住，新提交会被 MES 以 `1_030_900_002`/`1_030_900_003` 拒绝，而这两个码已按「安全等待」处理，不会产生第二个任务。
- 活动请求查询仍应同时接受两种键：`dedup_key IN (新三字段键, 原两字段键)`。这不是为预期的 `UNPROVABLE` 行准备，而是兜底「漏回填的行」和「应急路径保留旧键的行」，成本一行 SQL，不要省。
- 指纹同时覆盖 `remark`、`description`、`customerRequirements` 等字段，因此这些字段被编辑也会导致「无法证明」；这是可接受的保守偏差，不要为了减少人工核对项而放宽比对，也不要为此改造指纹算法（会破坏历史值可比性）。
- 部署前检查（打包后、上传前）：在目标库复跑一次 `docs/ops/mes-material-identity-preflight.sql`。Q2 必须仍为 0 行；Q3 若出现 `UNPROVABLE`，该批次行走应急路径（保留旧键 + 人工核对），其余照常。2026-10-29 的核验结果是 Q2 = 0、Q3 = 13/13 PROVABLE、Q4 = 0、Q5 = 0，可作为基线对比；若复跑结果与基线不同，说明期间有新在途请求或提单被改，先看清楚再发。
- 继续以数据库唯一索引作为并发最终保护。唯一冲突后只重新查获胜活动请求并等待/关联，绝不能再次盲发外部 POST。
- 对 V67 刷新入口只做共享身份锁所需的最小 QPO 改动：写入同一三字段键并检查相同身份的活动请求。批量入口遇到刷新请求时只返回在途/待核对，不创建 follower；刷新入口遇到批量请求时不发 POST。唯一索引并发冲突后按同一规则重新查请求，不改刷新用户流程。
- 共享身份查询必须同步放开 `MesMaterialDedupService.findActiveMaster` 的 `request_role = 'MASTER'` 过滤：刷新料号写入的台账行 `request_role` 为 NULL，若只回填 `dedup_key` 而保留该过滤条件，批量入口仍然看不见刷新请求，上述仲裁等于没做。二者择一并写明：去掉角色过滤、改按 `dedup_key + 活动状态` 查询后再按角色分支（批量主请求转 follower，刷新请求只返回在途）；或给刷新行写入独立角色值并在查询中列入。推荐前者，改动面更小；不要复用 `MASTER` 语义给刷新行。

键编码长度已按现有列核实：`order_delivery_ticket.part_no` 为 `varchar(100)`、`version` 为 `varchar(20)`、`material_name` 为 `varchar(255)`，长度前缀编码最坏约 390 字符，512 有余量，不需要摘要降级。PostgreSQL 增加 `varchar` 长度属元数据变更、不重写表，`uk_delivery_mes_request_active_dedup` 会随之适配，迁移中不需要额外的索引重建或 drop 步骤；但仍应在迁移末尾校验回填后的键长度不超过新列宽。

### 3. 修复批次归并与创建响应匹配

- 批次内以三字段身份归并相同零件并建立 follower；不同材质不能被合并。
- 现有创建响应不带材质，因此一个 MES 请求内必须保证每个“图号+版本”最多只有一种材质。只对存在冲突的计划组拆分请求，不更改数据中心/MES 契约；每个响应按图号+版本匹配前，显式校验该键在本次请求中唯一。
- 该不变式由既有 `uk_mes_material_batch_key (batch_id, item_key)` 在数据库层兜底：拆分漏了会在预留阶段撞唯一键而不是静默合并，这是好事，但错误信息对用户不可读。拆分逻辑要在预留前自检，命中冲突时返回明确的业务原因（哪个图号版本有多种材质、被拆成几个请求），不要让唯一键异常直接冒到前端。
- `mes_material_batch_item.item_key` 继续保存图号+版本键，不将三字段长键塞进 220 字符列。callback 行匹配改用已持久化的 `mes_task_no`。
- 异步响应将 `taskNo` 写入现有 `mes_material_batch_item.mes_task_no` 及现有可追溯请求字段。异步响应缺失/重复 taskNo、响应缺行或身份不匹配时，保留原 `bizId` 并进入 `RESULT_UNKNOWN`；不生成新请求掩盖结果。
- 注意 `applyBatchItem` 已把「无物料号且非 pending 状态」判为 `RESULT_UNKNOWN`，本步只需补「有 pending 状态但缺 `taskNo`」这半段，不要重写整条分支；`RESULT_UNKNOWN` 对 `mes_material_batch_item` 与提单状态都已有合法枚举值，不需要新增状态。
- 对数据中心实际透传的 MES 错误 `1_030_900_002` / `1_030_900_003` 增加 QPO 定向处理和测试：分别识别已存在/处理中，保持原请求关联和安全等待，不显示成允许盲目重试的普通失败。若错误码未被现有代理响应透传，按结果不确定处理并报告，不要求修改对端契约。

### 4. callback 用 bizId + taskNo 定位并允许安全覆盖

- 先按 callback 根级 `bizId` 定位批次，再按 `taskNo` 找唯一批次行；以图号、版本交叉校验。重复输入 taskNo、批内重复 taskNo、未知 taskNo、跨 bizId 或图号/版本不符均拒绝写回。
- 为没有历史 `mes_task_no` 的旧批次保留兼容路径：仅当该旧批次中图号+版本候选行唯一时才匹配；有歧义就安全拒绝，不猜测。该唯一性由既有 `uk_mes_material_batch_key (batch_id, item_key)` 保证（`item_key` 继续存两字段键），所以不存在歧义分支，实现不需要为旧批次设计额外状态或人工介入路径；若发现该约束被改动，先停下来报告，不要自行放宽匹配。
- 更新当前先跳过已确认 ticket 的判断顺序：必须先验证 bizId、taskNo 和行归属。相同有效请求的重复同值回调幂等；同一有效请求回不同物料号时允许覆盖提单行及其 RFQ/销售订单来源记录，并用现有操作日志记录旧值、新值和请求标识。
- 若已成功值属于另一个较新的有效请求，旧请求回调不得覆盖。保留现有安全恢复口径：ticket 尚未取得有效成功值时，不能只因其 request id 被一次失败重试更新，就拒绝一个已由本批次 taskNo 证明真实的迟到成功回调。
- 逐项处理当前实现中的三个覆盖阻断点：已确认 ticket 的提前跳过、批次行产品号冲突 409、已确认请求行冲突 409。只放开通过上述身份与请求校验的覆盖；错误身份、旧有效值和跨批次冲突仍拒绝。
- 保持现有 callback 部分成功/事务语义、失败恢复和来源表边界；不重写 callback controller/API。

### 5. 修正进程恢复状态并保留安全等待

- 当本地任务已不活跃而其 MES request 台账仍为 `PROCESSING`，改为将 request、ticket 和相关 waiter 保留/转为 `RESULT_UNKNOWN`，记录原 `bizId`；不能关闭为 `FAILED`、释放活动身份或提示用户直接重提。
- 只有能证明 MES POST 尚未发生的本地预留失败，才允许进入可重试失败状态。
- `CALLBACK_PENDING` / `RESULT_UNKNOWN` 再次点击现有按钮时只返回/关联原请求状态，不做物料分页扫描，不生成新 `bizId`。
- 不新增删除、反查或恢复 API。待核对的人工处理沿用 QPO 现有运维方式，并在结果中明确提示保留原 `bizId`。

### 6. 收口 QPO 测试、文档与验证

重点更新/新增以下测试中的受影响断言：

- `MesMaterialDedupServiceTest`、`MesMaterialDedupBatchWorkflowTest`、`MesMaterialBatchCallbackWorkflowTest`、`MesMaterialSyncServiceTest`、`MesMaterialProcessingRecoveryServiceTest`。
- `ProductionItemMaterialNoRefreshServiceTest`：验证共享活动身份冲突不会触发第二个外部请求。
- 去重查询的混合键兼容：三字段键与原两字段键都能命中同一条活动请求，旧行不会因键格式变化被漏判为新请求。
- 指纹核对是一次性离线步骤（依赖真实库数据），不写成 CI 断言；产出物是「PROVABLE / UNPROVABLE」两类批次，与只读冲突预演一起作为打包前的检查项。两个工具已交付并互相验证：`docs/ops/mes-material-identity-preflight.sql`（Q1–Q5，纯只读，**正式库可直接跑**）与 `MesMaterialIdentityBackfillPreflightTest`（`-Dqpo.mes.preflight=true` 才运行，默认 skipped，作为交叉验证）。若后续调整身份键、状态口径或请求字段，必须同步改这两处与去重服务，否则检查结果会与代码脱节。
- `MesMaterialDedupMigrationCharacterizationTest`：改为断言 V71 在新增 migration 中完成键扩展和存量回填，保留 V1/V2/V70 不变。
- `SalesOrderLinkedChangeNoticeMigrationTest`：最高 migration 版本断言从 70 更新到 71。
- `MesMaterialDedupDocumentationCharacterizationTest`：同步更新文档断言，避免旧的两字段去重口径被特征测试继续固定。

只更新 QPO 内直接描述本流程的 18/19 号文档、MES 物料号同步/回调说明及相关测试断言。权限继续复用现有点；日志沿用现有同步/回调审计事件，只补必要的旧/新物料号摘要，不新增高频读取日志。若新增 DTO，必须加 `@JsonIgnoreProperties(ignoreUnknown = true)`。

验证顺序：先运行受影响的后端测试，再运行 Flyway policy/migration characterization 和后端要求的启动/校验。完整后端基线命令沿用项目配置，包含 `-DargLine="-Djdk.attach.allowAttachSelf=true"`，`DB_URL` 由环境变量提供，并排除 `DatacenterReadonlyDirectConnectConfigTest`；后端与前端测试不要并行。若没有前端改动，不跑无关前端套件；如果执行中确需前端改动，除受影响 Vitest 外必须跑 Vite build。

实施结束检查 `git diff --check` 和 `git status --short --branch`，只保留本功能的 QPO 代码、V71、测试及必要 QPO 文档。不得提交或推送。

## 验收标准

- 普通操作员仍只有现有一个批量按钮，API、权限及外部 JSON 契约不变。
- 活动去重身份为图号、版本、材质；不同材质不互相归并，存量活动 request/waiter 安全升级（生产基线：15 行活动请求全部可回填，Q2 无重复身份）。
- 同一 MES 请求内图号+版本唯一，响应可以在不改对端契约时准确对应。
- callback 在 bizId 内按 taskNo 唯一定位，保留旧批次无 taskNo 的无歧义兼容路径。
- 当前有效 MES 结果可覆盖旧物料号并审计；无效/过期请求不能覆盖新结果。
- 已存在/处理中错误、进程中断和网络结果不明不会变成可盲重试状态。
- 批量获取和刷新料号共享 QPO 活动身份保护，不会并发提交同一身份。
- 刷新料号在途请求对批量入口真实可见，不因 `request_role` 过滤被漏判。
- QPO 不直连 MES、不调用删除、不做分页反查、不新增恢复入口；未知状态保留并由现有运维方式处理。
