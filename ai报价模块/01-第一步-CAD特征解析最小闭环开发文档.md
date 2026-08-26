# 第一步开发文档：CAD 特征解析最小闭环

日期：2026-08-26
状态：开发前定稿
适用范围：QPO AI 报价模块第一阶段
关联文档：`ai报价模块/技术方案文档.md`

## 1. 第一步目标

第一步只完成 **CAD 特征解析最小闭环**。

目标是让 QPO 能把一个零件的 STEP/STP 三维图纸解析成系统可用的结构化几何属性，并保存、复用、展示这些属性。

```text
MinIO 中的 STEP/STP 图纸
  -> QPO Java 后端发起解析任务
  -> FastAPI AI Quote Service 编排解析
  -> OCCT Parser Worker 执行 CAD 几何解析
  -> FastAPI 返回结构化 CAD 特征 JSON
  -> Java 后端保存 cad_feature_snapshot
  -> 前端或接口可查看 CAD 特征摘要
```

这一步完成后，系统还不能自动报价，也不做相似件检索，但已经具备“理解三维零件几何信息”的基础能力。

## 2. 第一步不做什么

为了代码文件清晰、边界稳定，第一步明确不做以下内容：

- 不做 Qdrant 向量数据库接入。
- 不做 embedding 或 CAD 几何向量生成。
- 不做 AI 工艺推荐。
- 不做 RuleOnlyProvider / ExternalAiProvider / PrivateModelProvider。
- 不做 OCR。
- 不做相似件检索。
- 不做工艺预测、工时预测、自动报价。
- 不做复杂孔、槽、薄壁、深腔算法的最终版本。
- 不改现有最终核价计算链路。

第一步只处理一件事：**CAD 文件解析为结构化几何特征，并入库可查**。

## 3. 技术架构定调

第一步仍按整体技术方案的双后端架构落地：

```text
QPO Java Core
  负责：权限、数据权限、文件元数据查询、任务创建、操作日志、结果落库、对前端暴露接口

FastAPI AI Quote Service
  负责：接收 Java 内部请求、读取 CAD 文件、调用 OCCT Worker、整理解析结果

OCCT Parser Worker
  负责：STEP/STP 文件读取、OCCT 几何计算、输出结构化 JSON

PostgreSQL
  负责：保存文件元数据、cad_feature_snapshot、操作结果

MinIO
  负责：保存 STEP/STP 原始文件
```

关键原则：

- 前端只调用 Java API，不直接调用 FastAPI。
- Java 不直接依赖 OCCT，不引入 C++ CAD 内核编译环境。
- FastAPI 不绕过 Java 写最终报价字段。
- OCCT Worker 不接触用户权限、报价主流程、核价计算。
- CAD 解析失败不能影响人工核价。

## 4. 代码目录规划

### 4.1 Java 后端目录

Java 侧放在现有 `quote-backend`，新增轻量 `aiquote` 包。

```text
quote-backend/src/main/java/com/machining/quote/aiquote/
  controller/AiQuoteController.java
  service/AiQuoteTaskService.java
  service/AiQuoteResultService.java
  service/client/AiQuoteServiceClient.java
  dto/CadFeatureExtractReq.java
  dto/CadFeatureExtractResp.java
  dto/CadFeatureSnapshotDTO.java
  entity/CadFeatureSnapshot.java
  mapper/CadFeatureSnapshotMapper.java
```

Java 第一阶段职责：

- `AiQuoteController`：暴露 CAD 解析接口。
- `AiQuoteTaskService`：校验 item、查文件元数据、判断 hash 是否已有解析结果。
- `AiQuoteServiceClient`：调用 FastAPI 内部解析接口。
- `AiQuoteResultService`：保存或读取 `cad_feature_snapshot`。
- `CadFeatureSnapshotMapper`：读写 CAD 特征快照表。

Java 第一阶段不放：

- OCCT 解析代码。
- Python 模型调用代码。
- Qdrant 客户端。
- AI Provider 路由。

### 4.2 FastAPI 服务目录

建议在仓库根目录新增独立服务目录：

```text
ai-quote-service/
  app/main.py
  app/api/cad_feature.py
  app/services/cad_feature_service.py
  app/clients/minio_client.py
  app/clients/occt_parser_client.py
  app/schemas/cad_feature_request.py
  app/schemas/cad_feature_response.py
  app/core/config.py
  app/core/logging.py
  tests/
```

FastAPI 第一阶段职责：

- 接收 Java 内部接口请求。
- 根据文件引用从 MinIO 读取 STEP/STP 文件，或接收 Java 提供的受控文件下载地址。
- 调用 OCCT Parser Worker。
- 整理单位、字段名、解析状态和错误信息。
- 返回结构化 CAD 特征 JSON。

FastAPI 第一阶段不做：

- Qdrant 写入。
- embedding。
- AI 推荐。
- OCR。
- 最终报价保存。

### 4.3 OCCT Parser Worker 目录

建议新增独立 worker 目录：

```text
cad-parser-worker/
  src/occt_step_reader.*
  src/geometry_property_calculator.*
  src/topology_feature_extractor.*
  src/cad_parser_api.*
  tests/
```

OCCT Worker 第一阶段职责：

- 支持 STEP/STP 文件读取。
- 构建 OCCT shape。
- 计算基础几何属性。
- 统计基础拓扑信息。
- 返回 JSON 给 FastAPI。

第一阶段不要求 OCCT Worker 直接连 PostgreSQL、MinIO、Qdrant。

## 5. 第一阶段 CAD 特征范围

第一阶段只做稳定、基础、可验证的字段。

### 5.1 必做字段

```text
cad_format
parser_name
parser_version
bbox_length
bbox_width
bbox_height
volume
surface_area
face_count
edge_count
curved_face_count
cylindrical_face_count
status
error_message
feature_summary_json
```

### 5.2 可选字段

如果 OCCT Worker 实现顺手，可以补充：

```text
solid_count
shell_count
compound_count
shape_valid
unit_hint
parse_duration_ms
```

### 5.3 后续再做字段

以下字段暂不作为第一步验收要求：

```text
hole_count
min_hole_diameter
max_hole_diameter
thin_wall_risk
deep_cavity_risk
slot_count
chamfer_count
thread_candidate_count
complexity_score
```

这些字段更依赖特征识别算法，需要等基础解析稳定后再做。

## 6. 数据库设计

后续实现必须遵守 Flyway 追加迁移规则。不能修改已执行的 `V1__qpo_baseline_schema.sql`、`V2__qpo_baseline_seed_data.sql` 等 baseline 文件。

第一阶段建议新增表：`cad_feature_snapshot`。

建议字段：

```text
id                       bigint primary key
item_id                  bigint not null
file_id                  bigint
file_hash                varchar(128) not null
cad_format               varchar(32) not null
parser_name              varchar(64) not null
parser_version           varchar(64)
bbox_length              numeric(18,6)
bbox_width               numeric(18,6)
bbox_height              numeric(18,6)
volume                   numeric(24,6)
surface_area             numeric(24,6)
face_count               integer
edge_count               integer
curved_face_count        integer
cylindrical_face_count   integer
feature_summary_json     jsonb
status                   varchar(32) not null
error_message            text
created_by               bigint
created_at               timestamp not null
updated_at               timestamp not null
```

建议约束和索引：

```text
unique(file_hash, parser_name, parser_version)
index(item_id)
index(file_id)
index(status)
```

复用规则：

- 同一个 `file_hash + parser_name + parser_version` 已成功解析时，直接复用历史快照。
- 文件 hash 变化时重新解析。
- parser 版本变化时允许重新解析，避免旧算法结果污染新算法。

## 7. Java 对外接口

### 7.1 触发 CAD 解析

```http
POST /api/engineer/ai-quote/items/{itemId}/cad-feature/extract
Permission: engineer:pricing:ai-suggest
```

请求体建议：

```json
{
  "fileId": 123,
  "forceRefresh": false
}
```

返回体建议：

```json
{
  "success": true,
  "reused": false,
  "snapshotId": 10001,
  "status": "SUCCESS",
  "cadFeature": {
    "cadFormat": "STEP",
    "bboxLength": 120.0,
    "bboxWidth": 60.0,
    "bboxHeight": 20.0,
    "volume": 85000.0,
    "surfaceArea": 42000.0,
    "faceCount": 96,
    "edgeCount": 240,
    "curvedFaceCount": 14,
    "cylindricalFaceCount": 8
  },
  "errorMessage": null
}
```

### 7.2 查询 CAD 特征快照

```http
GET /api/engineer/ai-quote/items/{itemId}/cad-feature
Permission: engineer:pricing:view
```

用途：前端展示当前零件已解析的 CAD 几何特征。

## 8. FastAPI 内部接口

FastAPI 只接受 Java 后端调用，不直接暴露给前端。

### 8.1 内部解析接口

```http
POST /internal/cad-feature/extract
```

请求体建议：

```json
{
  "itemId": 456,
  "fileId": 123,
  "fileHash": "sha256:...",
  "cadFormat": "STEP",
  "minioObjectKey": "rfq/2026/xxx/part.step",
  "parserOptions": {
    "extractTopology": true,
    "unit": "mm"
  }
}
```

返回体建议：

```json
{
  "success": true,
  "parserName": "occt-parser",
  "parserVersion": "0.1.0",
  "cadFormat": "STEP",
  "bboxLength": 120.0,
  "bboxWidth": 60.0,
  "bboxHeight": 20.0,
  "volume": 85000.0,
  "surfaceArea": 42000.0,
  "faceCount": 96,
  "edgeCount": 240,
  "curvedFaceCount": 14,
  "cylindricalFaceCount": 8,
  "featureSummary": {
    "unit": "mm",
    "parseDurationMs": 820
  },
  "errorMessage": null
}
```

失败返回也必须结构化：

```json
{
  "success": false,
  "parserName": "occt-parser",
  "parserVersion": "0.1.0",
  "cadFormat": "STEP",
  "errorCode": "CAD_PARSE_FAILED",
  "errorMessage": "STEP file cannot be read by OCCT"
}
```

## 9. OCCT Worker 契约

OCCT Worker 可以先做成 FastAPI 调用的本地命令行程序，也可以做成独立 HTTP/gRPC 服务。第一阶段建议先追求边界清晰，不追求复杂分布式。

输入：

```text
local_file_path
cad_format
unit
extract_topology
```

输出：

```text
success
parser_name
parser_version
bbox_length / bbox_width / bbox_height
volume
surface_area
face_count
edge_count
curved_face_count
cylindrical_face_count
feature_summary
error_code
error_message
```

Worker 约束：

- 不读取 PostgreSQL。
- 不读取 QPO 用户信息。
- 不写业务日志。
- 不参与报价计算。
- 只处理 CAD 文件和几何结果。

## 10. 权限和日志

### 10.1 权限

第一阶段建议新增或复用以下权限：

- 触发解析：`engineer:pricing:ai-suggest`
- 查看结果：`engineer:pricing:view`

如果后续认为 CAD 解析需要独立按钮权限，可新增：

- `engineer:pricing:cad-feature-extract`

但第一阶段为了减少权限复杂度，可以先复用 `engineer:pricing:ai-suggest`。

### 10.2 操作日志

建议记录事件：

```text
AI_QUOTE_CAD_FEATURE_EXTRACT_SUBMIT
AI_QUOTE_CAD_FEATURE_EXTRACT_SUCCESS
AI_QUOTE_CAD_FEATURE_EXTRACT_FAILED
AI_QUOTE_CAD_FEATURE_EXTRACT_REUSED
```

操作日志记录摘要：

```text
itemId
fileId
fileHash
snapshotId
parserName
parserVersion
status
durationMs
```

禁止记录：

- 完整 CAD 文件内容。
- MinIO 密钥。
- FastAPI 内部 token。
- 完整异常堆栈到操作日志。

异常堆栈只能进后端文件日志。

## 11. 错误处理

第一阶段必须保证“解析失败不影响人工核价”。

常见错误和处理：

| 场景 | 处理 |
| --- | --- |
| 文件不存在 | Java 返回失败，记录操作日志，不影响核价 |
| 文件格式不是 STEP/STP | 返回 `UNSUPPORTED_CAD_FORMAT` |
| MinIO 读取失败 | 返回 `CAD_FILE_READ_FAILED` |
| OCCT 解析失败 | 返回 `CAD_PARSE_FAILED`，保存失败快照或失败日志 |
| FastAPI 超时 | Java 返回可重试失败，记录 durationMs |
| 同 hash 已解析 | 直接复用成功快照 |

## 12. 前端最小展示

第一阶段前端只做轻量展示，不做复杂 AI 推荐交互。

建议在逐件核价页面或 AI 入口附近展示：

```text
CAD 解析状态
解析时间
长 / 宽 / 高
体积
表面积
面数 / 边数
曲面数 / 圆柱面数
失败原因
重新解析按钮
```

前端交互原则：

- 解析失败不阻断手工核价。
- 解析中要有 loading 状态。
- 解析成功后展示摘要，不自动生成工艺。
- 同文件复用时提示“已复用历史解析结果”。

## 13. 开发顺序

建议按以下顺序开发，避免文件和职责混乱：

1. 新增 Flyway 迁移，创建 `cad_feature_snapshot`。
2. Java 新增 entity、mapper、DTO 和查询/保存服务。
3. Java 新增 `AiQuoteController` 的 CAD 解析接口骨架。
4. FastAPI 新增服务骨架和 `/internal/cad-feature/extract`。
5. OCCT Worker 新增 STEP/STP 基础解析能力。
6. FastAPI 串联 MinIO 读取和 OCCT Worker 调用。
7. Java 串联 FastAPI 调用和快照保存。
8. Java 增加 hash 复用逻辑。
9. 前端增加 CAD 特征摘要展示和触发按钮。
10. 补充权限、操作日志和失败日志。
11. 补充测试和验收样例。

## 14. 验收标准

第一步完成必须满足以下标准：

1. 可以对一个已上传 STEP/STP 文件触发 CAD 解析。
2. Java 后端完成权限校验和数据权限校验。
3. Java 能调用 FastAPI 内部接口。
4. FastAPI 能调用 OCCT Worker。
5. OCCT Worker 能返回基础几何特征。
6. Java 能保存 `cad_feature_snapshot`。
7. 相同 `file_hash + parser_version` 能复用解析结果。
8. 前端或接口能查看 CAD 特征摘要。
9. 解析失败时不影响手工核价。
10. 操作日志有提交、成功、失败、复用记录。
11. 文件日志记录异常摘要和 task/item/file 信息。
12. 不引入 Qdrant、embedding、OCR、AI 工艺推荐代码。

## 15. 测试建议

### 15.1 Java 测试

- CAD 解析接口权限测试。
- 文件不存在时失败测试。
- hash 命中复用测试。
- FastAPI 调用成功后保存快照测试。
- FastAPI 失败时不写成功快照测试。
- Flyway policy 测试，确认 baseline 未改。

### 15.2 FastAPI 测试

- 请求参数校验测试。
- MinIO 文件读取 mock 测试。
- OCCT Worker 成功返回解析结果测试。
- OCCT Worker 失败返回结构化错误测试。
- 超时处理测试。

### 15.3 OCCT Worker 测试

- 标准 STEP 文件解析成功。
- 损坏 STEP 文件解析失败。
- 空文件失败。
- 输出字段类型稳定。
- parser version 正确返回。

## 16. 第一阶段完成后的下一步

第一步完成后，第二步再进入：

```text
结构化业务属性 + CAD 几何特征
  -> 特征工程
  -> Qdrant collection
  -> 相似件 Top N 召回
  -> 带出历史相似件工艺和工时作为参考
```

也就是说，Qdrant 和相似件检索放在第二步，不能提前混入第一步，防止 CAD 基础能力还没稳定就扩大范围。
