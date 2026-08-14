# QPO 系统配置模块：出站 / 入站接口拆分与入站开放接口统一 token 配置开发文档

> 2026-08-14 最终补丁口径：入站接口鉴权从“MES 物料号回调专用 token”调整为 **QPO 入站开放接口统一 token（`QPO_OPEN_API_TOKEN`）**。统一 token 在 **系统配置 > 入站接口** 页面顶部、刷新按钮旁生成 / 轮换 / 展示状态，供所有入站开放接口共用；接口列表只维护路径、启停、来源系统和说明。轮换后旧 token 对 MES 物料号回调、销售订单外部同步和后续新增入站开放接口同时失效。`ERP_MES_CALLBACK_TOKEN` 仅作为老环境 deprecated fallback，不作为新联调交付口径。

## 1. 背景

当前 QPO 的“系统配置”页面已经承载两类能力：

- 文件存储配置：LOCAL / MinIO 的上传、读取、激活和切换。
- 系统对接配置：QPO 调用 U9、MES、WMS 时使用的地址、认证、路径和超时。

随着 MES 物料号回调接口上线，系统配置里又出现第三类配置：外部系统调用 QPO 的入站接口配置。它和现有 U9 / MES / WMS 出站配置不是同一类东西：

- 出站接口：QPO 主动调用外部系统，例如 QPO 调 MES 获取物料号、QPO 调 WMS 锁库、QPO 调 U9 同步销售订单。
- 入站接口：外部系统主动调用 QPO，例如 MES 后续回调 QPO 写回物料号。

如果继续把 MES 回调 URL、旧入站 token 和 token 开关塞进现有“MES 配置”弹窗，管理员会很容易混淆：

- `MES authToken` 是 QPO 调 MES 时使用的认证信息。
- `QPO_OPEN_API_TOKEN` 是外部系统调 QPO 时使用的统一入站开放接口令牌。

两者方向不同、权限边界不同、运维含义不同，必须在系统配置页面中明确拆开。

## 2. 目标

- 将系统配置页面拆成更清晰的信息架构：
  - `文件存储`
  - `出站接口`
  - `入站接口`
- 保留现有 U9 / MES / WMS 出站接口配置能力，不影响业务调用。
- 新增入站接口配置面板，展示 MES 物料号回调、销售订单外部同步等入站开放接口元数据。
- QPO 入站开放接口统一 token 支持在系统配置中生成、轮换和展示状态。
- 统一 token 可配置，但不明文存储、不明文回显、不进入日志。
- MES 回调接口运行时优先使用页面保存的入站配置，没有页面配置时继续兼容 `erp.env`。
- 系统配置页面从“各系统挤一块”调整为“按接口方向分组”，方便后续继续接入 WMS 回调、MES 生产状态回调等入站接口。
- 同步更新权限、日志、管理员说明、MES 对接文档和回归测试。

## 3. 非目标

- 不新增左侧菜单，继续使用现有 `/system-config`。
- 不改变 MES 物料号回调接口的业务路径：`POST /api/integrations/mes/material-callback`。
- 不改变 MES 回调请求体字段、幂等规则、同图号版本 fan-out 规则。
- 不把回调接口加入用户登录态；MES 回调仍通过 `QPO_OPEN_API_TOKEN` 鉴权。
- 不把统一 token 明文写入数据库、前端缓存、操作日志、文件日志、截图或接口文档正文。
- 不删除 `erp.env` 中的 `ERP_MES_CALLBACK_TOKEN`、`ERP_MES_CALLBACK_TOKEN_ENABLED`、`ERP_PUBLIC_BASE_URL`，它们继续作为 fallback。
- 不新增可分配的 `common:*` 权限。
- 不把“出站接口测试连接”和“入站回调校验配置”混成一个概念；入站接口第一版只做本地配置校验，不主动伪造业务回调。

## 4. 当前代码事实

### 4.1 当前页面结构

入口：

```text
quote-frontend/src/components/SystemConfigManage.vue
```

当前 tab：

```text
系统配置
  - 文件存储
  - 系统对接
```

其中“系统对接”实际承载 QPO 调外部系统的出站接口配置：

```text
quote-frontend/src/components/system-config/SystemIntegrationConfigPanel.vue
```

该面板目前把 U9 / MES / WMS 放在同一个概览、同一个列表、同一个弹窗中。

### 4.2 当前出站接口配置

现有出站接口配置表：

```text
system_external_integration_config
```

现有后端管理接口：

```text
GET    /api/system/integration-configs
GET    /api/system/integration-configs/active
POST   /api/system/integration-configs
PUT    /api/system/integration-configs/{id}
POST   /api/system/integration-configs/{id}/test
PUT    /api/system/integration-configs/{id}/activate
PUT    /api/system/integration-configs/{id}/disable
```

现有权限：

```text
system:config:view
system:config:integration:update
system:config:integration:test
system:config:integration:activate
```

这些权限继续用于出站接口配置，不承担入站开放接口统一 token 管理。

### 4.3 当前 MES 回调配置

MES 回调 Controller：

```text
quote-backend/src/main/java/com/machining/quote/controller/MesIntegrationCallbackController.java
```

当前配置来源：

```yaml
erp:
  mes:
    callback-token: ${ERP_MES_CALLBACK_TOKEN:}
    callback-token-enabled: ${ERP_MES_CALLBACK_TOKEN_ENABLED:true}
```

当前接口：

```text
POST /api/integrations/mes/material-callback
Authorization: Bearer {ERP_MES_CALLBACK_TOKEN}
```

当前问题：

- token 只能通过 `erp.env` 配置，页面无法查看是否已配置。
- 修改 token 需要改环境变量并重启后端。
- 页面没有合适位置展示回调 URL 和 token 状态。
- 若直接加到现有 MES 出站配置弹窗，会造成出站 token 和 `QPO_OPEN_API_TOKEN` 混淆。

## 5. 产品设计

### 5.1 页面入口

继续使用：

```text
系统 -> 系统配置 -> /system-config
```

页面改为三段式 tab：

```text
系统配置
  - 文件存储
  - 出站接口
  - 入站接口
```

页面标题说明同步调整：

```text
管理 QPO 文件存储、外部系统出站连接和入站回调入口配置。
```

### 5.2 文件存储 tab

保持现有 `SystemStorageConfigPanel.vue` 不变。

### 5.3 出站接口 tab

现有“系统对接”改名为“出站接口”。

显示对象：

- U9：QPO 同步销售订单到 U9。
- MES：QPO 获取物料号、下发待生产信息到 MES。
- WMS：QPO 获取库存锁库信息。
- 数据中心：继续只做预留展示。

建议组件命名：

```text
SystemOutboundIntegrationConfigPanel.vue
```

如果为了降低第一阶段改动，也可以暂时保留原组件文件名 `SystemIntegrationConfigPanel.vue`，但页面标题、tab 文案、测试快照和文档必须改成“出站接口”。

### 5.4 入站接口 tab

新增组件：

```text
quote-frontend/src/components/system-config/SystemInboundCallbackConfigPanel.vue
```

推荐布局：

```text
入站接口
  顶部工具条：
    - 刷新
    - QPO 入站开放接口统一 token 状态
    - 生成 token / 轮换 token
    - token 更新时间

  接口列表：
    - MES 物料号回调
      - 调用方：数据中心
      - 业务来源：MES
      - 接口路径：/api/integrations/mes/material-callback
      - 启用状态：已启用 / 已停用
      - 认证方式：QPO_OPEN_API_TOKEN
      - 操作：编辑配置、复制接口地址、校验配置
    - 销售订单外部同步查询
      - 调用方：外部同步系统 / 数据中心
      - 业务来源：销售订单
      - 接口路径：/api/integrations/sales-orders
      - 启用状态：已启用 / 已停用
      - 认证方式：QPO_OPEN_API_TOKEN
      - 操作：编辑配置、复制接口地址、校验配置
```

### 5.5 token 生成和展示交互

统一 token 允许写入系统配置，但必须按以下方式处理：

- 后端生成高强度随机 token。
- 后端只存 token hash，不存 token 明文。
- 前端只在生成 / 轮换成功后的弹窗中展示一次明文 token。
- 用户关闭弹窗后，页面无法再次查看明文 token。
- 页面仅展示脱敏状态，例如 `已配置，尾号 pLc` 或 `已配置`。
- 复制 token 必须由用户点击按钮触发，不能自动写剪贴板。
- token 不进入 `localStorage`、`sessionStorage`、Pinia 持久化、URL、日志或接口文档。
- 统一 token 轮换后，旧 token 对所有入站开放接口同时失效；页面必须提示“MES 回调、销售订单同步等外部调用都需要同步更换”。

生成算法建议：

```java
byte[] bytes = new byte[32];
SecureRandom.getInstanceStrong().nextBytes(bytes);
String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
```

保存时：

```text
token_hash = SHA-256(token)
token_mask = token 最后 3 到 4 位或固定“已配置”
```

校验时：

```text
SHA-256(Authorization Bearer token) 与 token_hash 做 constant-time compare
```

### 5.6 入站接口配置来源显示

页面需要让管理员知道当前接口元数据和统一 token 配置来自哪里：

| 来源 | 展示文案 | 含义 |
| --- | --- | --- |
| DB | 使用页面配置 | 已在系统配置页面保存入站接口配置或统一 token，运行时优先使用该配置。 |
| ENV | 使用环境变量 fallback | 页面尚未保存配置，运行时使用 `erp.env`。 |
| MIXED | 页面配置 + 环境变量兜底 | 页面只保存了部分字段，缺失字段回退到 `erp.env`。 |

推荐第一版实现规则：

- 如果 DB 中存在统一 token 配置，所有入站开放接口优先使用该 token 校验。
- 如果 DB 中存在接口元数据配置，优先使用 DB 字段。
- DB 中为空的字段允许回退环境变量。
- 如果 DB 中没有配置，则完全使用环境变量 fallback。
- 页面保存过配置后，建议尽量补齐 `publicBaseUrl`、接口启停状态和统一 token，减少 MIXED 状态。

## 6. 数据模型设计

### 6.1 新增表

新增 Flyway migration：

```text
quote-backend/src/main/resources/db/migration/V78__system_inbound_callback_config.sql
```

如果实施时 V78 已被占用，则使用实际下一迁移编号。

新增表：

```sql
CREATE TABLE IF NOT EXISTS system_inbound_callback_config (
    id BIGSERIAL PRIMARY KEY,
    callback_code VARCHAR(60) NOT NULL,
    source_system_code VARCHAR(30) NOT NULL,
    name VARCHAR(100) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    token_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    public_base_url VARCHAR(500),
    callback_path VARCHAR(300) NOT NULL,
    token_hash VARCHAR(128),
    token_mask VARCHAR(30),
    token_updated_at TIMESTAMP,
    last_validate_status VARCHAR(20),
    last_validate_message VARCHAR(500),
    last_validate_at TIMESTAMP,
    remark VARCHAR(500),
    created_by VARCHAR(100),
    updated_by VARCHAR(100),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_system_inbound_callback_code UNIQUE (callback_code),
    CONSTRAINT ck_system_inbound_callback_source CHECK (source_system_code IN ('MES')),
    CONSTRAINT ck_system_inbound_callback_validate_status CHECK (
        last_validate_status IS NULL OR last_validate_status IN ('SUCCESS', 'FAILED')
    )
);

CREATE INDEX IF NOT EXISTS idx_system_inbound_callback_source
    ON system_inbound_callback_config(source_system_code);

CREATE INDEX IF NOT EXISTS idx_system_inbound_callback_enabled
    ON system_inbound_callback_config(enabled);
```

### 6.2 callback_code 枚举

第一版只支持：

```text
MES_MATERIAL_CALLBACK
```

含义：

```text
MES 后续异步回调 QPO，写回 order_delivery_ticket.material_no，并同步销售订单来源零件的 material_no 确认字段。
```

### 6.3 为什么不复用 system_external_integration_config

不建议把入站回调配置塞进 `system_external_integration_config`，原因：

- 现有表语义是“QPO 调别人”，入站回调是“别人调 QPO”。
- 出站配置有 `baseUrl + path + authToken + test + activate`，入站配置有 `publicBaseUrl + callbackPath + enabled + authMode`，统一 token 单独维护 `tokenHash`。
- 出站接口允许多套配置并激活一套；入站回调第一版更适合一类回调一条配置。
- 出站 token 需要 QPO 发给对方；`QPO_OPEN_API_TOKEN` 是 QPO 校验对方调用入站开放接口，安全边界不同。

因此新增 `system_inbound_callback_config` 更清晰，后续可以承载更多入站接口。

## 7. 后端设计

### 7.1 新增后端文件

建议新增：

```text
quote-backend/src/main/java/com/machining/quote/entity/SystemInboundCallbackConfig.java
quote-backend/src/main/java/com/machining/quote/mapper/SystemInboundCallbackConfigMapper.java
quote-backend/src/main/java/com/machining/quote/dto/SystemInboundCallbackConfigDTO.java
quote-backend/src/main/java/com/machining/quote/dto/SystemInboundCallbackConfigSaveReq.java
quote-backend/src/main/java/com/machining/quote/dto/SystemInboundCallbackTokenResp.java
quote-backend/src/main/java/com/machining/quote/integration/InboundCallbackCode.java
quote-backend/src/main/java/com/machining/quote/integration/InboundCallbackRuntimeConfig.java
quote-backend/src/main/java/com/machining/quote/service/SystemInboundCallbackConfigService.java
quote-backend/src/main/java/com/machining/quote/controller/SystemInboundCallbackConfigController.java
```

### 7.2 Runtime 配置读取

新增 `SystemInboundCallbackConfigService.runtimeConfig(callbackCode)`：

读取优先级：

1. 查 `system_inbound_callback_config.callback_code = ?`。
2. 如果 DB 有值，优先使用 DB 字段。
3. DB 字段为空时，回退 `erp.env` 对应字段。
4. DB 不存在时，完全使用 `erp.env`。

MES 物料号回调 fallback：

| Runtime 字段 | DB 字段 | fallback 环境变量 |
| --- | --- | --- |
| `publicBaseUrl` | `public_base_url` | `ERP_PUBLIC_BASE_URL` |
| `callbackPath` | `callback_path` | 固定 `/api/integrations/mes/material-callback` |
| `authMode` | 固定 `QPO_OPEN_API_TOKEN` | 无环境变量交付口径 |
| `tokenHash` | `token_hash` | `SHA-256(ERP_MES_CALLBACK_TOKEN)` |
| `enabled` | `enabled` | 默认 `true` |

### 7.3 token 校验

新增方法：

```java
boolean hasValidBearerToken(InboundCallbackCode callbackCode, String authorization)
```

规则：

- `enabled = false`：接口拒绝，返回 403 或业务失败。
- 入站开放接口启用但统一 token 未配置：拒绝，并提示管理员先生成 `QPO_OPEN_API_TOKEN`。
- 入站开放接口启用且统一 token 错误：拒绝。
- 入站开放接口停用：拒绝业务调用，但允许管理员在系统配置页查看和编辑元数据。
- 比较时使用 constant-time compare。

### 7.4 MES 回调 Controller 改造

改造：

```text
MesIntegrationCallbackController
```

从当前：

```java
@Value("${erp.mes.callback-token:}")
private String callbackToken;

@Value("${erp.mes.callback-token-enabled:true}")
private boolean callbackTokenEnabled;
```

调整为：

```java
@Autowired
private SystemInboundCallbackConfigService inboundCallbackConfigService;
```

然后：

```text
inboundCallbackConfigService.hasValidBearerToken(MES_MATERIAL_CALLBACK, authorization)
```

验收要求：

- 页面配置存在时，回调校验使用页面配置。
- 页面配置不存在时，回调校验继续使用 `ERP_MES_CALLBACK_TOKEN`。
- token 错误不进入业务处理。
- token、Authorization header、完整回调体不进入日志。

### 7.5 管理接口

新增：

```text
GET  /api/system/inbound-callback-configs
GET  /api/system/inbound-callback-configs/open-api-token
POST /api/system/inbound-callback-configs/open-api-token
GET  /api/system/inbound-callback-configs/{interfaceCode}
PUT  /api/system/inbound-callback-configs/{interfaceCode}
POST /api/system/inbound-callback-configs/{interfaceCode}/validate
```

接口说明：

| API | 说明 | 权限 |
| --- | --- | --- |
| `GET /api/system/inbound-callback-configs` | 查询入站接口配置列表 | `system:config:view` |
| `GET /api/system/inbound-callback-configs/open-api-token` | 查询统一 token 状态、掩码和更新时间，不返回明文 | `system:config:view` |
| `POST /api/system/inbound-callback-configs/open-api-token` | 生成或轮换统一 token，返回明文一次 | `system:config:inbound:token-rotate` |
| `GET /api/system/inbound-callback-configs/{interfaceCode}` | 查询单个入站接口元数据 | `system:config:view` |
| `PUT /api/system/inbound-callback-configs/{interfaceCode}` | 保存接口启停、publicBaseUrl、remark 等元数据 | `system:config:inbound:update` |
| `POST /api/system/inbound-callback-configs/{interfaceCode}/validate` | 校验接口 URL、启停和统一 token 状态，不发起业务回调 | `system:config:inbound:update` |

### 7.6 DTO 脱敏

查询 DTO 返回：

```json
{
  "callbackCode": "MES_MATERIAL_CALLBACK",
  "name": "MES物料号回调",
  "enabled": true,
  "authMode": "QPO_OPEN_API_TOKEN",
  "publicBaseUrl": "http://192.168.70.21:8088",
  "callbackPath": "/api/integrations/mes/material-callback",
  "callbackUrl": "http://192.168.70.21:8088/api/integrations/mes/material-callback",
  "configSource": "DB",
  "lastValidateStatus": "SUCCESS",
  "lastValidateMessage": "配置完整",
  "lastValidateAt": "2026-08-03 10:00:00",
  "remark": ""
}
```

统一 token 状态 DTO 返回：

```json
{
  "tokenConfigured": true,
  "tokenMask": "***pLc",
  "updatedAt": "2026-08-14 10:00:00",
  "message": "统一 token 已配置；明文不可再次查看"
}
```

生成 / 轮换统一 token DTO 返回：

```json
{
  "success": true,
  "tokenType": "QPO_OPEN_API_TOKEN",
  "token": "真实token只返回一次",
  "tokenMask": "***pLc",
  "message": "QPO 入站开放接口统一 token 已生成，请立即复制并通过受控渠道交付外部调用方"
}
```

后续查询不得再返回 `token` 字段。

## 8. 权限设计

新增权限：

| 权限码 | 类型 | 父级 | 默认角色 | 说明 |
| --- | --- | --- | --- | --- |
| `system:config:inbound:update` | BUTTON | `system:config:view` | `SUPER_ADMIN` | 保存入站接口元数据、启停接口、配置 publicBaseUrl。 |
| `system:config:inbound:token-rotate` | BUTTON | `system:config:view` | `SUPER_ADMIN` | 生成或轮换 QPO 入站开放接口统一 token。 |

治理口径：

- 查看入站接口配置沿用 `system:config:view`。
- 修改入站接口配置必须由后端 `@RequirePermission("system:config:inbound:update")` 校验。
- 生成 / 轮换 token 必须由后端 `@RequirePermission("system:config:inbound:token-rotate")` 校验。
- 入站开放接口本身不走用户权限树，不要求登录态。
- 入站开放接口使用 QPO 入站开放接口统一 token，不新增可分配 `common:*`。

## 9. 审计和日志

### 9.1 操作日志动作

新增：

| 动作码 | 模块 | 目标表 | 说明 |
| --- | --- | --- | --- |
| `SYSTEM_INBOUND_CALLBACK_CONFIG_UPDATE` | SYSTEM | `system_inbound_callback_config` | 保存入站回调配置。 |
| `SYSTEM_INBOUND_CALLBACK_TOKEN_ROTATE` | SYSTEM | `system_inbound_callback_config` | 生成或轮换 QPO 入站开放接口统一 token。 |
| `SYSTEM_INBOUND_CALLBACK_CONFIG_VALIDATE` | SYSTEM | `system_inbound_callback_config` | 校验入站回调配置。 |

### 9.2 文件日志

推荐低噪声事件：

```text
Inbound callback config updated
Inbound open API token rotated
Inbound callback config validated
MES material callback auth rejected
MES material callback auth accepted
```

### 9.3 脱敏规则

不得记录：

- QPO 入站开放接口统一 token 明文
- token hash
- Authorization header
- 完整 MES 回调请求体
- 完整 MES 回调响应体
- 生成 token 接口响应明文

可以记录：

- callbackCode
- sourceSystemCode
- configSource
- enabled
- authMode
- tokenConfigured
- publicBaseUrl host
- callbackPath
- operator
- requestId
- validateStatus
- failure reason 摘要

## 10. 前端设计

### 10.1 文件调整

新增：

```text
quote-frontend/src/components/system-config/SystemInboundCallbackConfigPanel.vue
```

扩展：

```text
quote-frontend/src/components/SystemConfigManage.vue
quote-frontend/src/services/systemConfigApi.js
```

可选重命名：

```text
SystemIntegrationConfigPanel.vue -> SystemOutboundIntegrationConfigPanel.vue
```

如果重命名成本较高，第一版只改页面文案和测试，后续再重命名文件。

### 10.2 SystemConfigManage.vue

目标结构：

```vue
<el-tabs v-model="activeTab" class="system-config-tabs">
  <el-tab-pane label="文件存储" name="storage">
    <SystemStorageConfigPanel />
  </el-tab-pane>
  <el-tab-pane label="出站接口" name="outbound">
    <SystemOutboundIntegrationConfigPanel />
  </el-tab-pane>
  <el-tab-pane label="入站接口" name="inbound">
    <SystemInboundCallbackConfigPanel />
  </el-tab-pane>
</el-tabs>
```

默认 tab 建议仍为 `storage`，避免影响管理员已有使用习惯。

### 10.3 systemConfigApi.js

新增：

```js
export const listInboundCallbackConfigs = () =>
  request('/api/system/inbound-callback-configs')

export const getInboundOpenApiTokenStatus = () =>
  request('/api/system/inbound-callback-configs/open-api-token')

export const rotateInboundOpenApiToken = () =>
  request('/api/system/inbound-callback-configs/open-api-token', {
    method: 'POST'
  })

export const getInboundCallbackConfig = (interfaceCode) =>
  request(`/api/system/inbound-callback-configs/${interfaceCode}`)

export const saveInboundCallbackConfig = (interfaceCode, payload) =>
  request(`/api/system/inbound-callback-configs/${interfaceCode}`, {
    method: 'PUT',
    data: payload
  })

export const validateInboundCallbackConfig = (interfaceCode) =>
  request(`/api/system/inbound-callback-configs/${interfaceCode}/validate`, {
    method: 'POST'
  })
```

### 10.4 入站接口页面交互

顶部统一 token 区域：

| 字段 / 按钮 | 交互 |
| --- | --- |
| token 状态 | 展示已配置 / 未配置、掩码尾号、更新时间。 |
| 生成 token | 无 token 时显示，成功后明文只展示一次。 |
| 轮换 token | 已有 token 时显示，必须二次确认。 |

接口列表字段：

| 字段 | 交互 |
| --- | --- |
| 接口名称 | 例如 MES 物料号回调、销售订单外部同步查询。 |
| 接口地址 | 只读展示，支持复制。 |
| QPO 访问基址 | 可编辑，例如 `http://192.168.70.21:8088`。 |
| 接口路径 | 只读展示，例如 `/api/integrations/mes/material-callback`、`/api/integrations/sales-orders`。 |
| 接口启用 | switch，控制是否接受该类入站调用。 |
| 认证方式 | 固定展示 `QPO_OPEN_API_TOKEN`。 |
| 备注 | 文本框。 |

按钮：

| 按钮 | 权限 | 说明 |
| --- | --- | --- |
| 保存配置 | `system:config:inbound:update` | 保存 publicBaseUrl、enabled、remark 等接口元数据。 |
| 生成 token | `system:config:inbound:token-rotate` | 顶部统一 token 操作，无 token 时显示。 |
| 轮换 token | `system:config:inbound:token-rotate` | 顶部统一 token 操作，已有 token 时显示，必须二次确认。 |
| 复制接口地址 | `system:config:view` | 复制 callbackUrl。 |
| 校验配置 | `system:config:inbound:update` | 校验 URL、token 状态和启用状态，不调用业务回调。 |

轮换 token 确认文案：

```text
轮换后旧 token 将立即失效，MES 回调、销售订单同步等所有入站开放接口都会切换到新 token。外部系统未同步新 token 前，请求会被拒绝。确认继续？
```

生成成功弹窗：

```text
token 只展示一次，请立即复制并通过受控渠道交付外部调用方。
关闭后无法再次查看，只能重新轮换。
```

### 10.5 视觉口径

入站接口页面使用“顶部统一 token 管理 + 接口列表”。

原因：

- 统一 token 是全局安全边界，必须放在页面顶部，避免管理员误以为每个接口有独立 token。
- 接口列表负责展示路径、启停和配置摘要，不承载 token 明文或轮换按钮。
- 后续新增 WMS 回调、MES 生产状态回调、销售订单同步等接口时，只新增列表行，不新增 token 管理入口。

## 11. 安全设计

### 11.1 token 存储

推荐：

- 数据库存 `token_hash`。
- 后端内存中只在本次请求生命周期持有 token 明文。
- 前端只在生成成功响应中拿到 token 明文一次。

不推荐：

- 数据库存 token 明文。
- 页面每次查询都能看到 token。
- 把 token 写入 `erp_global_config` 这类泛用 key-value 并明文返回。

### 11.2 token 轮换

轮换后：

- 新 token 立即生效。
- 旧 token 立即失效。
- 操作日志记录轮换动作，但不记录新旧 token。
- 页面提示管理员必须同步通知 MES。

### 11.3 环境变量 fallback

保留：

```env
ERP_PUBLIC_BASE_URL=
ERP_MES_CALLBACK_TOKEN=
ERP_MES_CALLBACK_TOKEN_ENABLED=true
```

使用场景：

- 本地开发。
- 老环境升级后尚未在页面保存入站配置。
- 数据库初始化前的兜底。

一旦页面配置完整，建议生产运行以 DB 配置为准，后续改 token 不需要重启 backend。

## 12. 文档更新范围

需要更新：

```text
docs/QPO系统配置/03-系统对接配置开发文档.md
docs/QPO权限用户体系/02-权限点清单与现有角色映射.md
docs/QPO权限用户体系/03-权限体系部署与管理员使用说明.md
docs/QPO日志模块/03-日志运维说明.md
docs/QPO日志模块/04-主流程文件日志规范化开发文档.md
docs/QPO-MES-WMS对接/03-MES物料号同步开发文档.md
docs/QPO-MES-WMS对接/06-提供给MES的物料号接口对接说明.md
docs/QPO-MES-WMS对接/08-MES物料号回调接口对接说明.md
```

文档口径：

- `03-系统对接配置开发文档.md` 说明原“系统对接”更名为“出站接口”。
- MES 对接说明中只放回调接口文档链接，不写真实 token。
- MES 回调接口文档说明 token 由 QPO 系统配置页面生成并通过受控渠道交付。
- 日志文档强调 QPO 入站开放接口统一 token 和 Authorization header 严禁落日志。

## 13. 测试计划

### 13.1 后端测试

新增或扩展：

```text
SystemInboundCallbackConfigStage1Test
SystemInboundCallbackConfigServiceTest
SystemInboundCallbackConfigControllerTest
MesIntegrationCallbackAuthConfigTest
PermissionModuleTreeTest
AuditActionCoverageTests
```

覆盖：

- migration 创建 `system_inbound_callback_config`。
- `MES_MATERIAL_CALLBACK` 配置可保存。
- token 生成返回明文一次，数据库只保存 hash。
- DTO 查询不返回 token 明文。
- 接口启用且统一 token 正确时通过。
- 接口启用且统一 token 缺失 / 错误时拒绝。
- 接口停用时拒绝业务调用。
- DB 配置优先于 `erp.env`。
- DB 缺字段时可回退 `erp.env`。
- 页面配置不存在时继续使用 `ERP_MES_CALLBACK_TOKEN`。
- MES 回调 Controller 使用新服务校验 token。
- 轮换 token 后旧 token 失效。
- 操作日志和异常信息不包含 token、Authorization header、token hash。

### 13.2 前端测试

新增或扩展：

```text
systemConfigInboundCallback.test.js
systemConfigIntegration.test.js
permissionStage4.test.js
adminDuplicateClickGuard.test.js
```

覆盖：

- 系统配置页面展示 `文件存储 / 出站接口 / 入站接口`。
- 原 U9 / MES / WMS 出站接口配置仍可见。
- 入站接口页展示 MES 物料号回调卡片。
- token 明文不在普通查询结果中出现。
- 无 `system:config:inbound:update` 时保存和校验按钮隐藏。
- 无 `system:config:inbound:token-rotate` 时生成 / 轮换 token 按钮隐藏。
- 轮换 token 有二次确认。
- 生成 token 成功后只在弹窗中展示一次。
- 复制回调地址使用 callbackUrl。

### 13.3 验证命令

后端：

```powershell
cd D:\QPO-Operations-Management-System\quote-backend
.\mvnw.cmd "-Dtest=SystemInboundCallbackConfigStage1Test,SystemInboundCallbackConfigServiceTest,SystemInboundCallbackConfigControllerTest,MesIntegrationCallbackAuthConfigTest,PermissionModuleTreeTest,AuditActionCoverageTests" test
```

前端：

```powershell
cd D:\QPO-Operations-Management-System\quote-frontend
npm test -- systemConfigInboundCallback.test.js systemConfigIntegration.test.js permissionStage4.test.js adminDuplicateClickGuard.test.js
npm run build
```

全量回归：

```powershell
cd D:\QPO-Operations-Management-System\quote-backend
.\mvnw.cmd test

cd D:\QPO-Operations-Management-System\quote-frontend
npm test
npm run build
```

## 14. 分阶段实施

### 阶段 1：系统配置信息架构拆分

目标：先把页面结构从“系统对接一锅端”拆成清晰的三类配置。

修改：

- `SystemConfigManage.vue` tab 改为 `文件存储 / 出站接口 / 入站接口`。
- 原 `系统对接` 页面文案改为 `出站接口`。
- 新增空壳 `SystemInboundCallbackConfigPanel.vue`。
- 页面标题说明改成覆盖文件存储、出站连接和入站回调。
- 更新前端测试中系统配置 tab 文案。

验收：

- `/system-config` 仍可访问。
- 文件存储功能不受影响。
- U9 / MES / WMS 出站接口配置仍可使用。
- 页面能看到 `入站接口` tab。
- 页面不再把入站开放接口 token 语义塞进出站接口页面。

### 阶段 2：入站回调配置数据模型与权限

目标：建立 MES 回调配置可持久化、可授权治理的基础。

修改：

- 新增迁移 `V78__system_inbound_callback_config.sql`，或实际下一编号。
- 新增 `system_inbound_callback_config`。
- 新增 `SystemInboundCallbackConfig` 实体和 Mapper。
- 新增 `InboundCallbackCode` 枚举，第一版包含 `MES_MATERIAL_CALLBACK`。
- 新增权限：
  - `system:config:inbound:update`
  - `system:config:inbound:token-rotate`
- 更新 `PermissionService.defaultPermissions()`。
- 更新权限清单文档。

验收：

- 数据库存在 `system_inbound_callback_config`。
- `callback_code` 唯一。
- 权限树中系统配置下出现入站接口配置权限。
- 默认 `SUPER_ADMIN` 拥有入站接口配置权限。
- 不新增 `common:*` 权限。

### 阶段 3：入站接口配置后端管理接口

目标：管理员可以通过后端 API 查看和维护入站接口元数据，并在统一入口生成 / 轮换 QPO 入站开放接口统一 token。

修改：

- 新增 `SystemInboundCallbackConfigService`。
- 新增 `SystemInboundCallbackConfigController`。
- 新增 DTO：
  - `SystemInboundCallbackConfigDTO`
  - `SystemInboundCallbackConfigSaveReq`
  - `SystemInboundCallbackTokenResp`
- 新增接口：
  - `GET /api/system/inbound-callback-configs`
  - `GET /api/system/inbound-callback-configs/open-api-token`
  - `POST /api/system/inbound-callback-configs/open-api-token`
  - `GET /api/system/inbound-callback-configs/{interfaceCode}`
  - `PUT /api/system/inbound-callback-configs/{interfaceCode}`
  - `POST /api/system/inbound-callback-configs/{interfaceCode}/validate`
- token 生成后只返回一次明文。
- 数据库存 token hash，不存明文。
- 保存和轮换写操作日志。

验收：

- 查询接口不返回 token 明文。
- 生成 token 接口返回明文一次。
- 数据库存储 token hash。
- 轮换 token 后旧 token 不再可用。
- 保存配置和轮换 token 均有后端权限注解。
- 操作日志不包含 token、token hash 或 Authorization header。

### 阶段 4：入站开放接口接入统一 token

目标：MES 回调接口、销售订单外部同步接口等入站开放接口运行时统一使用系统配置中的 `QPO_OPEN_API_TOKEN`。

修改：

- 改造 `MesIntegrationCallbackController`。
- 移除直接使用 `@Value("${erp.mes.callback-token}")` 作为唯一校验来源。
- 注入 `SystemInboundCallbackConfigService`。
- 统一 token 配置存在时使用 DB hash 校验。
- 老环境未完成迁移时才继续 fallback 到 `ERP_MES_CALLBACK_TOKEN`。
- 补 token 正确、缺失、错误、DB 优先、env fallback 和轮换全局失效测试。

验收：

- MES 使用页面生成的统一 token 可正常回调。
- token 错误时回调被拒绝，且不进入物料号写回逻辑。
- 页面未配置时，老的 `ERP_MES_CALLBACK_TOKEN` 仍可用。
- MES 回调业务幂等、冲突拒绝、fan-out 逻辑不回退。
- 销售订单同步接口使用同一统一 token；轮换后旧 token 不能访问销售订单同步接口。

### 阶段 5：入站接口前端完整页面

目标：系统配置页面可清楚展示统一 token 状态和所有入站接口入口。

修改：

- 完成 `SystemInboundCallbackConfigPanel.vue`。
- `systemConfigApi.js` 新增入站接口配置方法。
- 展示顶部统一 token 状态、掩码和更新时间。
- 展示 MES 物料号回调、销售订单外部同步查询等入站接口列表。
- 展示 callbackUrl、configSource、enabled、authMode。
- 支持编辑 publicBaseUrl、enabled、remark。
- 支持顶部生成 / 轮换统一 token。
- 支持复制接口地址。
- 支持校验配置。
- 操作按钮接入权限。
- 补前端测试。

验收：

- 入站接口页不出现 U9 / WMS 出站配置表单。
- 出站接口页不出现入站开放接口 token 字段。
- token 明文只在生成成功弹窗中展示一次。
- 无 `system:config:inbound:update` 时不能保存或校验配置。
- 无 `system:config:inbound:token-rotate` 时不能生成或轮换 token。
- 轮换 token 有二次确认。
- 页面视觉上比当前系统对接页更清楚，不继续把多类系统挤进一个表单。

### 阶段 6：文档、日志与回归

目标：配置口径、运维口径、MES 联调口径完整收口。

修改：

- 更新 `docs/QPO系统配置/03-系统对接配置开发文档.md`，说明其更名为出站接口配置。
- 更新权限清单和管理员说明。
- 更新日志运维说明和主流程文件日志规范。
- 更新 MES 物料号同步开发文档。
- 更新给 MES 的物料号接口对接说明。
- 更新 MES 物料号回调接口对接说明，说明 `QPO_OPEN_API_TOKEN` 来自系统配置页面入站接口顶部，真实 token 单独交付。
- 补前后端回归测试。

验收：

```powershell
cd D:\QPO-Operations-Management-System\quote-backend
.\mvnw.cmd test

cd D:\QPO-Operations-Management-System\quote-frontend
npm test
npm run build
```

额外验收：

- 文档不包含真实 token。
- 操作日志不包含真实 token。
- 文件日志不包含真实 token。
- MES 物料号回调接口文档可直接给数据中心联调方使用，回调 URL 由接口列表提供，`QPO_OPEN_API_TOKEN` 由 QPO 管理员从系统配置页面提供给数据中心。
- 系统配置页面的信息架构为 `文件存储 / 出站接口 / 入站接口`，不再显得多个系统混在一起。

## 15. 完成定义

- 系统配置页面三类配置边界清晰。
- 出站接口继续管理 QPO 调 U9 / MES / WMS。
- 入站接口独立管理 MES 物料号回调、销售订单外部同步查询等外部调用 QPO 的接口元数据。
- QPO 入站开放接口统一 token 可在系统配置中生成和轮换。
- token 可配置但不可回显、不可落日志、不可进入前端缓存。
- MES 回调接口运行时优先使用 DB 配置，缺失时兼容 `erp.env`。
- 权限、操作日志、文件日志、管理员说明、MES 联调文档和测试全部收口。
