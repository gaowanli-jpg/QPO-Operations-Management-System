# QPO 系统配置模块：文件存储配置与 MinIO 切换开发文档

## 1. 背景与结论

当前 QPO 的图纸文件以本地目录为核心：后端通过 `erp.upload-dir` 保存文件，通过 `/uploads/**` 预览或下载，`item_quote_version.drawing_url` 保存稳定的业务访问路径。这个方式适合单机或容器卷，但后续如果要让导入图纸长期保存、跨容器重建后不丢失、并给 MES 提供可下载图纸，就应该把“文件存储方式”抽成系统配置，而不是把 MinIO 地址写死在代码里。

结论：

- 新增左侧“系统配置”模块，第一版只承载“文件存储配置”，后续可扩展为其他系统配置 tab。
- 文件存储采用策略模式：`LOCAL` 保持现状，`MINIO` 走对象存储；运行时由激活配置决定新文件写入哪里。
- 借鉴 MES 的“数据库保存多套文件配置 + 设置主配置 + 测试上传”思路，但 QPO 第一版不照搬 DB/FTP/SFTP 等全量能力，只做 `LOCAL` 与 `MINIO`。
- `drawingUrl` 继续保存稳定的 `/uploads/...` 路径，不把业务字段改成 MinIO 原始 URL；MinIO 的长期访问地址由文件元数据和存储服务统一维护。
- 新增文件对象元数据，记录每个文件实际存储在哪种策略下，避免切换到 MinIO 后老的本地图纸失效，也避免将来切回本地后 MinIO 文件失效。
- MinIO 只用于业务文件，尤其是导入/补传/替换的图纸；后端运行日志、错误日志、数据库备份不放进 MinIO。

## 2. MES 文件配置参考

已查看 MES 测试服务器实现，MES 使用若依/Yudao 风格的 infra 文件模块管理 MinIO：

- 后端表：`infra_file_config`
- 关键字段：`id`、`name`、`storage`、`master`、`config`、`remark`、创建/更新时间、删除标记
- 存储类型枚举：
  - `DB = 1`
  - `LOCAL = 10`
  - `FTP = 11`
  - `SFTP = 12`
  - `S3 = 20`
- MinIO 走 S3 兼容配置，`config` JSON 中保存：
  - `endpoint`
  - `bucket`
  - `accessKey`
  - `accessSecret`
  - `enablePathStyleAccess`
  - `enablePublicAccess`
  - `region`
  - 可选 `domain`
- 管理接口包括创建、更新、删除、分页查询、设置主配置、测试上传。
- 前端页面支持列表查询、创建/编辑、设置主配置、测试上传；测试动作会上传临时文件并返回可访问 URL。

MES 当前可借鉴的核心不是具体框架，而是治理方式：

1. 存储配置放数据库，可在后台页面管理。
2. 多套配置中只有一套主配置负责新文件写入。
3. 配置保存前/激活前支持测试连接和测试上传。
4. 密钥属于敏感信息，日志和页面都不能明文回显。

QPO 不建议照搬 MES 的动态 `@class` JSON 结构。QPO 代码更轻，第一版使用明确的 `storage_type + config_json` 更容易测试、审计和维护。

## 3. QPO 当前状态

当前相关代码入口：

- `quote-backend/src/main/resources/application.yaml`
  - `erp.upload-dir`
  - `erp.backup-dir`
  - `erp.public-base-url`
- `quote-backend/src/main/java/com/machining/quote/service/OrderFilePathService.java`
  - 根据订单和文件名生成本地目录与 `/uploads/...` 图纸路径。
- `quote-backend/src/main/java/com/machining/quote/controller/UploadFileController.java`
  - `GET /uploads/**` 从本地 `erp.upload-dir` 下读取文件并返回。
- `quote-backend/src/main/java/com/machining/quote/config/WebConfig.java`
  - `/uploads/**` 当前受登录鉴权拦截。
- `quote-backend/src/main/java/com/machining/quote/controller/MarketController.java`
  - RFQ 导入图纸时直接写本地文件。
- `quote-backend/src/main/java/com/machining/quote/service/OrderManagementService.java`
  - 补传图纸、删除图纸时按 `/uploads/...` 反推本地路径。
- `quote-backend/src/main/java/com/machining/quote/service/InternalTargetLeadTimeService.java`
  - 议价提单相关图纸替换仍依赖 `OrderFilePathService`。
- `quote-backend/src/main/java/com/machining/quote/service/MesMaterialSyncService.java`
  - 当前把 `drawingUrl` 拼成 `erp.public-base-url + /uploads/...` 发送给 MES。
- `quote-frontend/src/api.js`
  - 前端预览会带登录态下载 `/uploads/**`，再转成 blob URL。

关键约束：

- `drawingUrl` 已经在订单、历史订单、已提零件、零件核价、MES 对接等多处使用，不能随意改成临时 URL。
- `/uploads/**` 当前需要登录 token，MES 如果没有 QPO 登录态，不能直接下载这个地址。
- Docker 卷和本地目录仍要保留，用作默认模式、历史图纸读取和本地开发兜底。

## 4. 方案取舍

| 方案 | 做法 | 优点 | 问题 | 结论 |
| --- | --- | --- | --- | --- |
| A. 只加环境变量 | 在 `erp.env` 写 MinIO endpoint/bucket/key，代码启动时固定使用 | 快，改动少 | 不能后台切换；无法审计谁改了配置；测试/生产切换要改部署文件 | 不推荐作为最终方案 |
| B. 数据库多配置 + 激活策略 | 像 MES 一样保存多套配置，后台选择激活配置，新文件按激活配置写入 | 可后台切换；可测试；可审计；能保留历史文件兼容 | 需要新增配置表、文件元数据、存储服务抽象 | 推荐 |
| C. 全量迁移到 MinIO | 所有历史图纸先迁移，之后只认 MinIO | 存储形态最统一 | 上线风险大；历史路径和缺失文件处理复杂；回滚困难 | 后续可选迁移，不作为第一版 |

推荐采用 B。第一版不强制迁移历史文件：老文件继续从本地读，新上传的图纸按当前激活策略写入。这样可以先把能力跑通，再决定是否做历史迁移工具。

## 5. 模块定位

### 5.1 左侧菜单

新增系统分组下的菜单：

- 菜单名称：`系统配置`
- 路由：`/system-config`
- 组件：`SystemConfigManage.vue`
- 第一版页面内容：`文件存储配置`

第一版页面不要拆成多个复杂配置模块，只做文件存储；但组件结构保留 tab 扩展点：

```text
系统配置
  文件存储
    当前激活策略
    配置列表
    新增/编辑配置
    测试连接/测试上传
    激活配置
```

### 5.2 页面能力

页面展示：

- 当前激活配置：名称、类型、endpoint 或本地根目录、bucket、是否可用、最后测试时间。
- 配置列表：名称、类型、状态、是否激活、更新时间、备注。
- 新增/编辑弹窗：
  - 通用字段：配置名称、存储类型、备注。
  - `LOCAL` 字段：本地根目录，默认读取 `ERP_UPLOAD_DIR`。
  - `MINIO` 字段：endpoint、bucket、accessKey、accessSecret、region、pathStyle、外部访问域名或公开访问地址。
- 测试按钮：
  - `LOCAL`：测试目录可创建、可写入、可读取、可删除临时文件。
  - `MINIO`：测试 bucket 可访问、可上传临时文件、可读取、可删除临时文件。
- 激活按钮：
  - 只能激活最近一次测试成功或保存时校验通过的配置。
  - 激活只影响之后上传的新文件，不改变历史文件实际存储位置。

密钥展示规则：

- 新增时必须输入 `accessSecret`。
- 编辑时不回显完整密钥；页面显示 `******`。
- 编辑时密钥留空代表沿用原密钥；输入新密钥才覆盖。
- 后端响应永远不返回明文 `accessSecret`。

## 6. 数据模型

### 6.1 文件存储配置表

新增表：`system_file_storage_config`

建议字段：

```sql
id BIGSERIAL PRIMARY KEY,
name VARCHAR(100) NOT NULL,
storage_type VARCHAR(20) NOT NULL,
active BOOLEAN NOT NULL DEFAULT FALSE,
enabled BOOLEAN NOT NULL DEFAULT TRUE,
config_json JSONB NOT NULL,
remark VARCHAR(500),
last_test_status VARCHAR(20),
last_test_message VARCHAR(500),
last_test_at TIMESTAMP,
created_by VARCHAR(100),
updated_by VARCHAR(100),
created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
```

约束：

- `storage_type` 只允许 `LOCAL`、`MINIO`。
- 同一时间只能有一条 `active = true AND enabled = true`。
- `config_json` 中允许保存敏感字段，但操作日志、文件日志、接口响应必须脱敏。
- 不做物理删除；停用配置使用 `enabled = false`，已激活配置不能停用。

`LOCAL` 的 `config_json` 示例：

```json
{
  "rootDir": "/data/uploads"
}
```

`MINIO` 的 `config_json` 示例：

```json
{
  "endpoint": "http://10.2.9.152:9050",
  "bucket": "qpo-drawings",
  "accessKey": "qpo-service",
  "accessSecret": "encrypted-or-raw-in-db",
  "region": "us-east-1",
  "pathStyle": true,
  "publicReadEnabled": true,
  "publicBaseUrl": "http://minio-internal.example.com/qpo-drawings"
}
```

密钥存储建议：

- 第一版可以先放在 `config_json`，但必须接口脱敏、日志脱敏。
- 如果要更强安全性，可在同阶段增加 AES 加密，密钥来自环境变量 `ERP_CONFIG_SECRET_KEY`。
- 不允许把 MinIO secret 写入前端缓存、操作日志、文件日志或异常 message。

### 6.2 文件对象元数据表

新增表：`system_file_object`

目的：记录每个业务文件真正保存在哪个存储策略下，解决“切换策略后历史文件怎么读”的问题。

建议字段：

```sql
id BIGSERIAL PRIMARY KEY,
public_path VARCHAR(800) NOT NULL,
storage_type VARCHAR(20) NOT NULL,
storage_config_id BIGINT,
object_key VARCHAR(800) NOT NULL,
original_filename VARCHAR(255),
content_type VARCHAR(100),
size_bytes BIGINT,
sha256 VARCHAR(64),
external_url VARCHAR(1000),
biz_type VARCHAR(50),
biz_id VARCHAR(100),
created_by VARCHAR(100),
created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
```

约束：

- `public_path` 唯一，例如 `/uploads/客户/RFQ-001/A.pdf`。
- `object_key` 是存储内部 key，例如 `客户/RFQ-001/A.pdf`。
- `storage_type` 记录文件创建时实际写入的策略，不随当前激活配置改变。
- `external_url` 可选，用于保存给 MES 等外部系统长期访问的文件地址；如果可由 `publicBaseUrl + objectKey` 稳定推导，也可以不落库，只在响应时生成。
- 老数据没有元数据时，`UploadFileController` 先按 `system_file_object.public_path` 查询；查不到再走历史本地目录兜底。

第一版不要求给所有历史图纸补齐 `system_file_object`。只要保留本地兜底读取，老图纸仍可预览。

## 7. 后端设计

### 7.1 存储服务抽象

新增存储接口：

```java
public interface FileStorageProvider {
    StorageType storageType();
    StoredFile save(FileSaveCommand command);
    InputStreamResource open(String objectKey);
    boolean exists(String objectKey);
    void delete(String objectKey);
    StorageTestResult test(FileStorageConfig config);
    Optional<String> externalReadUrl(String objectKey);
}
```

核心实现：

- `LocalFileStorageProvider`
  - 使用本地根目录保存和读取。
  - `externalReadUrl` 返回 QPO 后端代理的长期文件地址，不能暴露服务器真实本地路径。
- `MinioFileStorageProvider`
  - 使用 MinIO Java SDK 或 AWS S3 SDK。
  - `save` 上传对象到 bucket。
  - `open` 从 bucket 读取对象流。
  - `externalReadUrl` 返回长期可访问 URL，前提是 bucket/object 策略或反向代理允许读取。
- `FileStorageRouter`
  - 读取激活配置，决定新文件写入哪个 provider。
  - 按 `system_file_object` 的 `storage_type + storage_config_id` 读取历史文件。
  - 激活配置变更后刷新缓存。

### 7.2 文件保存流程

目标是把各业务模块的“文件写入”统一收口到一个服务，而不是每个 controller 自己 `transferTo(...)`。

新增服务建议：

```java
public class BusinessFileService {
    StoredBusinessFile saveDrawing(OrderRfq order, MultipartFile file, String bizType, String bizId);
    FileReadResult openByPublicPath(String publicPath);
    String externalReadableUrl(String publicPath);
    void deleteByPublicPath(String publicPath);
}
```

保存图纸时：

1. 使用 `OrderFilePathService` 继续生成稳定 `publicPath`，例如 `/uploads/客户/RFQ-001/P-001.pdf`。
2. 从 `publicPath` 推导 `objectKey`，例如 `客户/RFQ-001/P-001.pdf`。
3. `FileStorageRouter` 使用当前激活配置保存文件。
4. 写入 `system_file_object`，记录 `publicPath`、`objectKey`、`storage_type`、`storage_config_id`。
5. 业务表仍保存 `drawingUrl = publicPath`。

这样前端、历史数据和现有业务字段都不用理解 MinIO。

### 7.3 文件读取与预览

保留 `GET /uploads/**` 作为唯一业务预览入口：

1. 解析请求 path 得到 `publicPath`。
2. 查询 `system_file_object`。
3. 查到元数据时，按元数据中的实际存储策略读取。
4. 查不到元数据时，按历史逻辑从 `erp.upload-dir` 读取本地文件。
5. 正常下载不打 INFO；非法路径、文件不存在打 WARN；真实 IO 异常打 ERROR。

前端 `createAuthenticatedObjectUrl('/uploads/...')` 逻辑保持不变。

### 7.4 MES 图纸 URL

当前 `MesMaterialSyncService` 会把 `drawingUrl` 拼成 `erp.public-base-url + /uploads/...` 发给 MES。切到 MinIO 后不建议把业务表里的 `drawingUrl` 直接改成 MinIO 原始地址，因为这会让订单、历史订单、核价、提单等页面都和某一种存储实现强绑定。更稳妥的做法是：业务表保存 QPO 稳定路径，文件元数据保存 MinIO 对象信息，调用 MES 时由存储服务返回长期外部访问地址。

推荐改为：

```java
String drawingUrl = businessFileService.externalReadableUrl(item.getDrawingUrl());
req.setDrawingUrl(drawingUrl);
```

`externalReadableUrl` 的行为：

- MinIO 文件：返回长期 MinIO 访问 URL，通常由 `publicBaseUrl + objectKey` 生成，或者使用元数据表保存的 `external_url`。
- 本地文件：返回 QPO 后端代理的长期外部文件地址，例如 `/external-files/{fileId或publicKey}`。
- 外部文件地址只暴露对应图纸对象，不能访问 QPO 其他接口；如果走 QPO 代理，应使用不可猜测的 `publicKey` 或文件 ID 映射，避免任意遍历。

这能解决“MES 没有 QPO 登录 token，但需要下载图纸”的问题。

WMS 当前锁库逻辑不需要图纸文件，文件存储切换不影响 WMS 请求体。

### 7.5 系统配置 API

新增后端 controller：`SystemConfigController` 或 `FileStorageConfigController`。

接口建议：

| 方法 | 路径 | 权限 | 说明 |
| --- | --- | --- | --- |
| `GET` | `/api/system/storage-configs` | `system:config:view` | 查询配置列表，密钥脱敏 |
| `GET` | `/api/system/storage-configs/active` | `system:config:view` | 查询当前激活配置 |
| `POST` | `/api/system/storage-configs` | `system:config:storage:update` | 新增配置 |
| `PUT` | `/api/system/storage-configs/{id}` | `system:config:storage:update` | 编辑配置，secret 留空代表不变 |
| `POST` | `/api/system/storage-configs/{id}/test` | `system:config:storage:test` | 测试连接/上传/读取/删除临时文件 |
| `PUT` | `/api/system/storage-configs/{id}/activate` | `system:config:storage:activate` | 激活配置 |
| `PUT` | `/api/system/storage-configs/{id}/disable` | `system:config:storage:update` | 停用非激活配置 |

所有写接口必须有后端 `@RequirePermission`，不能只靠前端隐藏按钮。

## 8. 前端设计

新增文件：

- `quote-frontend/src/components/SystemConfigManage.vue`
- `quote-frontend/src/services/systemConfigApi.js`
- 对应单元测试，例如 `quote-frontend/src/systemConfigStorage.test.js`

修改文件：

- `quote-frontend/src/router/index.js`
  - 增加 `/system-config` 路由，`meta.permission = 'system:config:view'`
- `quote-frontend/src/App.vue`
  - 系统分组增加“系统配置”菜单元数据。
- 权限快照测试
  - 增加新路由和菜单权限断言。

页面样式遵循现有 QPO 页面模式：

```html
<section class="erp-page">
  <header class="erp-page-header">
    <div>
      <h1>系统配置</h1>
      <p>管理文件存储策略和 MinIO 连接。</p>
    </div>
  </header>

  <section class="erp-surface">
    文件存储配置内容
  </section>
</section>
```

交互要求：

- 测试、保存、激活按钮都接入重复点击保护。
- 无权限用户看不到保存、测试、激活按钮。
- `accessSecret` 使用密码框；编辑时不回显明文。
- 测试失败显示明确原因，但不显示 secret、token、完整异常堆栈。
- 激活配置前弹确认框，提示“只影响新上传文件，历史文件保持原存储位置”。

## 9. 权限设计

新增权限：

| 权限编码 | 类型 | 默认角色 | 说明 |
| --- | --- | --- | --- |
| `system:config:view` | `MENU` | `SUPER_ADMIN` | 查看系统配置页和当前存储配置 |
| `system:config:storage:update` | `BUTTON` | `SUPER_ADMIN` | 新增、编辑、停用文件存储配置 |
| `system:config:storage:test` | `BUTTON` | `SUPER_ADMIN` | 测试本地目录或 MinIO 上传读取 |
| `system:config:storage:activate` | `BUTTON` | `SUPER_ADMIN` | 激活某套存储配置 |

治理要求：

- 不新增 `common:*`。
- 系统配置属于系统治理能力，不受订单/客户数据范围控制。
- 后端权限目录、前端路由、`App.vue` 菜单、角色授权树、权限文档、权限测试必须同步更新。
- 如果管理员只授予 `system:config:view`，用户只能查看脱敏后的当前配置，不能保存、测试或激活。

## 10. 操作日志与文件日志

### 10.1 操作日志

配置变更属于系统治理动作，必须写 `audit_log`。

建议操作日志 action：

| action | module | targetType | 说明 |
| --- | --- | --- | --- |
| `SYSTEM_STORAGE_CONFIG_CREATE` | `SYSTEM` | `system_file_storage_config` | 新增文件存储配置 |
| `SYSTEM_STORAGE_CONFIG_UPDATE` | `SYSTEM` | `system_file_storage_config` | 编辑文件存储配置 |
| `SYSTEM_STORAGE_CONFIG_TEST` | `SYSTEM` | `system_file_storage_config` | 测试配置成功 |
| `SYSTEM_STORAGE_CONFIG_TEST_FAILED` | `SYSTEM` | `system_file_storage_config` | 测试配置失败 |
| `SYSTEM_STORAGE_CONFIG_ACTIVATE` | `SYSTEM` | `system_file_storage_config` | 激活配置 |
| `SYSTEM_STORAGE_CONFIG_DISABLE` | `SYSTEM` | `system_file_storage_config` | 停用非激活配置 |

操作日志 afterData 只能记录脱敏摘要：

```json
{
  "id": 12,
  "name": "测试 MinIO",
  "storageType": "MINIO",
  "endpoint": "http://10.2.9.152:9050",
  "bucket": "qpo-drawings",
  "active": true,
  "secretChanged": true
}
```

不得记录 `accessSecret`、JWT、Authorization header、完整请求 JSON、上传文件内容。

### 10.2 后端文件日志

建议事件：

- `Storage config created`
- `Storage config updated`
- `Storage config test completed`
- `Storage config test failed`
- `Storage config activated`
- `Business file save completed`
- `Business file save failed`
- `Business file access rejected`
- `Business file missing`
- `Business file read failed`

日志原则：

- 配置保存、测试、激活打 INFO 摘要。
- 测试失败、非法路径、文件不存在打 WARN。
- MinIO 连接异常、上传失败、读取失败、数据库写入失败打 ERROR，并带异常对象。
- 正常文件预览/下载不打 INFO，避免高频噪声。
- 不在循环里对每个零件/每个文件重复打大量 INFO；批量上传只打批次汇总。

## 11. 部署与环境变量

### 11.1 本地开发

本地开发不强制安装 MinIO。默认 `LOCAL` 模式可以继续使用 `ERP_UPLOAD_DIR`。如果要验证 MinIO 逻辑，可以在本机或开发服务器启动 MinIO Docker 容器。

建议在 `docker-compose.yml` 增加可选 MinIO 服务：

```yaml
minio:
  image: minio/minio:latest
  container_name: quote-minio
  command: server /data --console-address ":9001"
  environment:
    MINIO_ROOT_USER: ${MINIO_ROOT_USER:-qpo_minio_admin}
    MINIO_ROOT_PASSWORD: ${MINIO_ROOT_PASSWORD:-change-this-minio-password}
  ports:
    - "9000:9000"
    - "9001:9001"
  volumes:
    - minio_data:/data
```

生产或测试服务器如果已有统一 MinIO，可以不启动 QPO 自带 MinIO 容器，只在系统配置页里配置管理员提供的 endpoint、bucket 和账号。

### 11.2 环境变量

环境变量负责启动默认值和兜底，不是唯一配置来源。

建议新增：

```env
ERP_FILE_STORAGE_TYPE=LOCAL
ERP_MINIO_ENDPOINT=
ERP_MINIO_BUCKET=
ERP_MINIO_ACCESS_KEY=
ERP_MINIO_SECRET_KEY=
ERP_MINIO_REGION=us-east-1
ERP_MINIO_PATH_STYLE=true
ERP_CONFIG_SECRET_KEY=
```

启动逻辑：

1. 如果数据库没有任何 `system_file_storage_config`，启动时根据环境变量创建一条默认配置。
2. `ERP_FILE_STORAGE_TYPE=LOCAL` 时创建本地默认配置。
3. `ERP_FILE_STORAGE_TYPE=MINIO` 且 MinIO 必填项完整时创建 MinIO 默认配置。
4. 数据库已有配置时，以数据库激活配置为准，环境变量只作为紧急回退和初始化来源。

## 12. 迁移与兼容

第一版上线策略：

1. 上线后默认仍为 `LOCAL`，现有上传和预览行为不变。
2. 管理员在“系统配置 > 文件存储”新增 MinIO 配置。
3. 点击测试，确认 MinIO 可上传、读取、删除临时文件。
4. 激活 MinIO。
5. 激活后新导入、补传、替换的图纸写入 MinIO。
6. 历史本地图纸继续通过本地兜底读取。

历史图纸迁移不是第一版必需能力。后续如需迁移，可单独增加迁移任务：

- 扫描本地 `erp.upload-dir`。
- 根据现有 `drawingUrl` 生成 `objectKey`。
- 上传到 MinIO。
- 写入或更新 `system_file_object`。
- 校验数量、大小、hash。
- 迁移完成后再决定是否清理本地历史文件。

回滚策略：

- 如果 MinIO 配置有问题，管理员可重新激活 `LOCAL` 配置。
- 已写入 MinIO 的文件仍由 `system_file_object` 指向 MinIO 读取，不会因为当前激活策略变回 `LOCAL` 而丢失。
- 如果 MinIO 完全不可用，MinIO 历史文件预览会失败并记录 WARN/ERROR；不影响本地历史文件。

## 13. MES/WMS 对接影响

### 13.1 MES

MES 获取物料号时需要图纸，当前 QPO 发送 `drawingUrl`。改造后：

- QPO 仍保存稳定 `drawingUrl = /uploads/...`。
- 调 MES 前，QPO 临时生成外部可访问 URL。
- MinIO 模式优先返回长期 MinIO 访问 URL，要求 MES 所在网络能访问该 endpoint 或反向代理地址。
- 本地模式返回 QPO 后端代理的长期外部文件地址。
- 如果公司安全策略不允许公开 bucket，可以使用 QPO 代理地址做长期只读下载入口，而不是把 bucket 完全公开到所有内网用户。

这比直接把文件 multipart 传给 MES 更轻，也更符合 MES 当前“根据 URL 下载图纸”的接入方式。如果 MES 明确要求 QPO 直接上传图纸文件，再另开接口适配，不和本次存储配置混在一起。

### 13.2 WMS

WMS 库存/锁库不需要图纸文件。文件存储切换对 WMS 请求、并发、结果解析没有直接影响。

## 14. 开发分阶段

### 阶段 1：配置表与权限目录

- 新增 Flyway migration：
  - `system_file_storage_config`
  - `system_file_object`
  - 新增系统配置权限点
  - 初始化默认 `LOCAL` 配置
- 更新后端权限 fallback catalog。
- 更新权限文档和权限测试。

### 阶段 2：存储服务抽象

- 新增 `StorageType` 枚举。
- 新增 `FileStorageProvider`、`LocalFileStorageProvider`、`MinioFileStorageProvider`、`FileStorageRouter`。
- 新增配置读取、脱敏、测试服务。
- 引入 MinIO SDK 或 S3 SDK。

### 阶段 3：统一文件保存/读取

- 新增 `BusinessFileService`。
- 改造 RFQ 导入图纸、订单补传图纸、议价提单替换图纸。
- 保留 `OrderFilePathService` 负责生成稳定 `publicPath`。
- 改造 `UploadFileController` 从 `BusinessFileService` 读取。
- 改造删除图纸逻辑，不再直接按本地路径删除。

### 阶段 4：MES 外部图纸 URL

- 新增长期外部下载地址或 MinIO 公开/代理 URL 生成能力。
- 改造 `MesMaterialSyncService`，调用 `externalReadableUrl(...)`。
- 保持 MES 请求体字段名不变。

### 阶段 5：前端系统配置页

- 新增 `/system-config` 路由和左侧菜单。
- 新增文件存储配置页面、API service、弹窗表单、测试/激活按钮。
- 接入权限指令和重复点击保护。
- 更新前端权限快照测试。

### 阶段 6：部署文档与验证

- 更新 `erp.env.example`。
- 更新 `docker-compose.yml` 可选 MinIO 服务。
- 补充部署说明：本地开发默认 LOCAL，验证 MinIO 时启动 MinIO 容器；测试/生产可以接公司统一 MinIO。
- 跑后端单元/集成测试和前端测试。

## 15. 测试清单

后端测试：

- `LOCAL` 默认配置初始化成功。
- MinIO 配置保存时必填项校验正确。
- 编辑 MinIO 配置时 secret 留空会沿用旧值。
- 配置响应、操作日志、文件日志均不包含明文 secret。
- 同一时间只能激活一条配置。
- 激活配置后新文件写入对应 provider。
- `system_file_object` 能保证 MinIO 文件在切回 LOCAL 后仍可读取。
- 老的 `/uploads/...` 本地文件没有元数据时仍能兜底读取。
- 非法路径、越权路径、缺失文件返回正确状态并写 WARN。
- MES 调用时拿到的是外部可访问 URL，不是数据库里永久保存的 `drawingUrl` 原值。

前端测试：

- `/system-config` 使用 `system:config:view` 保护。
- 左侧菜单显示受权限控制。
- 无保存权限时不显示新增/编辑/停用按钮。
- 无测试权限时不显示测试按钮。
- 无激活权限时不显示激活按钮。
- 密钥字段编辑时不回显明文，留空提交代表保留。
- 测试失败只展示安全摘要。

集成验收：

- 默认 LOCAL 模式下，现有 RFQ 导入、补传图纸、图纸预览不回归。
- 启动 MinIO 后，在系统配置页新增并测试 MinIO 成功。
- 激活 MinIO 后，新上传图纸进入 MinIO bucket。
- 前端仍通过原 `/uploads/...` 预览新图纸。
- 已有本地图纸仍可预览。
- MES 获取物料号请求中的图纸 URL 可被 MES 长期下载，前提是 MES 网络可达且存储只读策略允许。
- WMS 锁库流程不受影响。
- Docker 重建 QPO 后，只要数据库和 MinIO 数据保留，新图纸不丢失。

## 16. 需要业务确认的默认决策

文档按以下默认决策设计，后续如果业务选择不同，可以再调整实现计划：

1. 第一版“系统配置”只做文件存储配置，不同时做 U9、MES、WMS token 等其他配置页。
2. 第一版不批量迁移历史本地图纸，只做本地兜底读取和新文件写 MinIO。
3. MinIO 图纸地址按长期可访问设计；可以使用 bucket 内网只读策略，也可以使用 QPO 后端代理的长期只读地址。
4. 日志、数据库备份、导出临时文件不进入 MinIO；如后续要做归档，再单独设计。
5. 第一版不提供物理删除存储配置，只提供停用非激活配置，降低误删风险。
