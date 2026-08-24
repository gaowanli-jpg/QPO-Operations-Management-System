# QPO 数据中心统一入口 SSO 登录开发文档

## 1. 背景

团队希望把数据中心作为多个系统的统一入口。用户先登录数据中心，再点击数据中心里的“进入 QPO”按钮进入 QPO。当前约定不是 OIDC，也不是完整统一权限平台，而是由数据中心把统一账号名传给 QPO；QPO 能匹配到本地同名账号时，按 QPO 本地角色、权限和数据范围登录。

本方案目标是让这个“传账号名进入 QPO”的流程可安全落地。核心原则是：

```text
数据中心负责证明“这个账号名是谁”。
QPO 负责判断“这个账号在 QPO 能做什么、能看哪些数据”。
```

因此，数据中心不能裸传 `username`，必须通过短时、可验签、防篡改、防重放的 SSO 参数传给 QPO。

## 2. 总体结论

采用 **数据中心签名用户名 SSO**：

1. 用户已登录数据中心。
2. 用户点击数据中心的“进入 QPO”。
3. 数据中心生成带签名的 SSO 参数，跳转到 QPO 前端回调页。
4. QPO 前端把 SSO 参数提交给 QPO 后端 exchange 接口。
5. QPO 后端校验 `appCode`、签名、时间戳、`nonce` 和 `redirect`。
6. QPO 后端用 `username` 查询 QPO 本地账号。
7. 本地账号存在、启用、具备角色权限时，QPO 签发自己的 JWT。
8. QPO 前端复用现有 `authStore.setAuth()` 保存 `erp_token`、`erp_user`、`erp_permissions`、`erp_menus`。
9. 后续所有业务接口继续由 QPO 的 `@RequirePermission`、权限树和数据范围控制。

## 3. 非目标

- 不引入 OIDC / OAuth2 Authorization Code。
- 不让数据中心传 QPO 角色、权限、菜单或客户数据范围。
- 不把 QPO 权限体系迁移到数据中心。
- 不允许数据中心账号自动拥有 QPO 权限。
- 不自动给首次进入 QPO 的用户分配默认角色。
- 不修改 QPO 现有 `erp_token`、`erp_user`、`erp_permissions`、`erp_menus` 本地存储兼容键。
- 不修改 `V1__qpo_baseline_schema.sql`、`V2__qpo_baseline_seed_data.sql` 或任何已执行 Flyway migration。

## 4. 业务边界

### 4.1 数据中心职责

数据中心负责：

- 认证用户已经登录数据中心。
- 使用统一账号名生成 SSO 登录参数。
- 使用与 QPO 约定的密钥或私钥对参数签名。
- 跳转到 QPO SSO 回调地址。

数据中心不负责：

- 判断 QPO 权限。
- 判断 QPO 数据范围。
- 给 QPO 传角色或菜单。
- 给 QPO 创建账号。
- 给 QPO 修改账号状态。

### 4.2 QPO 职责

QPO 负责：

- 校验数据中心 SSO 参数可信。
- 使用 `username` 匹配 QPO 本地账号。
- 判断账号是否存在、启用、可登录。
- 加载 QPO 本地角色、权限、菜单和数据范围。
- 签发 QPO 自己的 JWT。
- 记录 SSO 登录成功 / 失败审计日志。

QPO 不负责：

- 验证数据中心密码。
- 接收裸 `username` 登录。
- 接收数据中心传来的权限作为 QPO 权限。

## 5. 推荐交互流程

```text
数据中心登录态已建立
        |
        v
用户点击“进入 QPO”
        |
        v
数据中心生成 username/timestamp/nonce/appCode/redirect/signature
        |
        v
浏览器跳转 QPO 前端 /sso/datacenter/callback?...参数...
        |
        v
QPO 前端调用 POST /api/auth/datacenter-sso/exchange
        |
        v
QPO 后端验签、验时间、验 nonce、验 redirect
        |
        v
QPO 后端按 username 查本地账号和权限
        |
        v
QPO 后端签发 QPO JWT 并返回 user/roles/permissions/menus
        |
        v
QPO 前端写入 authStore，进入 redirect 或首页
```

## 6. 数据中心跳转规范

### 6.1 跳转地址

数据中心点击“进入 QPO”后跳转：

```text
https://qpo.example.com/sso/datacenter/callback
  ?appCode=QPO
  &username=zhangsan
  &displayName=%E5%BC%A0%E4%B8%89
  &timestamp=1780000000000
  &nonce=dc-20260824-01K...
  &redirect=%2Fdashboard
  &signature=...
```

说明：

- `displayName` 可选，只用于展示或审计辅助，不参与权限判断。
- `redirect` 只能是 QPO 内部相对路径，推荐默认 `/dashboard`。
- URL 中不能出现数据中心登录 token、QPO token、密码、手机号验证码或任何长期凭据。

### 6.2 参数定义

| 字段 | 必填 | 示例 | 说明 |
| --- | --- | --- | --- |
| `appCode` | 是 | `QPO` | 目标系统编码。QPO 只接受 `QPO`。 |
| `username` | 是 | `zhangsan` | 统一账号名，是 QPO 本地账号匹配唯一依据。 |
| `displayName` | 否 | `张三` | 展示名，不参与权限判断。 |
| `timestamp` | 是 | `1780000000000` | 毫秒时间戳。QPO 默认只接受 120 秒内请求。 |
| `nonce` | 是 | `dc-20260824-01K...` | 每次跳转唯一随机串，防重放。 |
| `redirect` | 否 | `/dashboard` | 登录成功后的 QPO 内部跳转路径。 |
| `signature` | 是 | `base64url...` | 数据中心签名。 |

### 6.3 签名算法

第一阶段推荐 HMAC-SHA256，双方共享一个 SSO 密钥。

签名规范：

1. 取参与签名字段：`appCode`、`username`、`timestamp`、`nonce`、`redirect`。
2. 字段名按 ASCII 升序排序。
3. 使用 UTF-8 编码。
4. 空 `redirect` 统一按 `/dashboard` 参与签名。
5. 使用 `\n` 拼接 canonical string。
6. 使用 HMAC-SHA256 计算摘要。
7. 使用 Base64URL 无填充编码输出 `signature`。

canonical string 示例：

```text
appCode=QPO
nonce=dc-20260824-01KZZ08THK316WKM13FFENVJD0
redirect=/dashboard
timestamp=1780000000000
username=zhangsan
```

伪代码：

```text
signature = base64url_no_padding(
  HMAC_SHA256(sharedSecret, canonicalStringUtf8)
)
```

### 6.4 数据中心安全要求

- `timestamp` 有效期建议不超过 120 秒。
- `nonce` 必须全局唯一，至少 128 bit 随机性。
- 数据中心不能复用同一个 `nonce`。
- 数据中心与 QPO 服务器时间建议通过 NTP 同步。
- 数据中心必须使用 HTTPS 访问 QPO。
- 数据中心不得在日志中记录完整 SSO URL、`signature` 或共享密钥。
- 共享密钥只能通过线下安全方式交付，不写入代码仓库和普通文档。

## 7. QPO 后端接口设计

### 7.1 新增接口

```http
POST /api/auth/datacenter-sso/exchange
Content-Type: application/json
```

请求体：

```json
{
  "appCode": "QPO",
  "username": "zhangsan",
  "displayName": "张三",
  "timestamp": 1780000000000,
  "nonce": "dc-20260824-01KZZ08THK316WKM13FFENVJD0",
  "redirect": "/dashboard",
  "signature": "base64url-signature"
}
```

成功响应沿用本地登录返回结构：

```json
{
  "success": true,
  "message": "登录成功",
  "token": "qpo-jwt",
  "user": {
    "username": "zhangsan",
    "displayName": "张三",
    "role": "MARKET"
  },
  "roles": ["MARKET_USER"],
  "permissions": ["market:order:view"],
  "menus": []
}
```

失败响应：

```json
{
  "success": false,
  "message": "QPO账号未配置，请联系管理员"
}
```

建议 HTTP 状态：

| 场景 | HTTP | message |
| --- | --- | --- |
| 参数缺失 | 400 | 数据中心登录参数不完整 |
| `appCode` 错误 | 400 | 数据中心登录目标系统不匹配 |
| 时间戳过期 | 401 | 登录入口已过期，请从数据中心重新进入 |
| `nonce` 已使用 | 401 | 登录请求已使用，请重新进入 |
| 签名错误 | 401 | 数据中心登录票据无效 |
| 用户不存在 | 403 | QPO账号未配置，请联系管理员 |
| 用户禁用 | 403 | QPO账号已停用 |
| 无角色或无菜单权限 | 403 | QPO权限未配置，请联系管理员 |

### 7.2 后端校验顺序

QPO 后端必须按以下顺序校验：

1. SSO 功能是否启用。
2. 请求字段是否完整。
3. `appCode` 是否等于配置值 `QPO`。
4. `timestamp` 是否在允许时间窗口内。
5. `redirect` 是否为内部路径。
6. `signature` 是否正确。
7. `nonce` 是否未使用。
8. 原子化写入 `nonce` 使用记录。
9. 查询 QPO 本地账号。
10. 判断账号是否启用。
11. 判断账号是否有有效角色 / 权限。
12. 签发 QPO JWT。

`nonce` 检查和写入必须在同一个事务内完成，并依赖数据库唯一约束防止并发重放。

### 7.3 账号匹配规则

第一阶段只使用：

```text
erp_user.username = request.username.trim()
```

治理规则：

- QPO 管理员提前创建同名账号。
- QPO 管理员配置角色、权限和数据范围。
- SSO 不自动创建用户。
- SSO 不自动分配角色。
- SSO 不用 `displayName`、手机号、邮箱、部门名称做匹配。
- 用户禁用后，即使数据中心仍可登录，也不能进入 QPO。

### 7.4 本地密码策略

第一阶段建议：

- SSO 用户仍保留 QPO 本地账号记录。
- SSO 用户如果不使用 QPO 本地密码登录，管理员应设置随机初始密码。
- QPO 本地密码登录可保留为管理员和应急入口。
- SSO 登录成功后不修改用户本地密码。
- QPO 不再设置首次登录或重置密码后的改密阻断；账号启用且权限配置完成后即可使用。

因此，数据中心 SSO 不需要设计额外绕过逻辑；QPO 只校验账号是否存在、是否启用以及是否具备本地角色、权限和数据范围。

## 8. QPO 配置设计

第一版必须在现有 **系统 > 系统配置** 页面新增 **数据中心登录** 配置 tab，用于维护“数据中心进入 QPO”的 SSO 登录参数。`erp.env` / `application.yaml` 只作为首启 fallback 和部署兜底，不再作为第一版唯一配置入口。

页面信息架构调整为：

```text
系统配置
  - 文件存储
  - 出站接口
  - U9配置
  - 入站接口
  - 数据中心登录
```

`数据中心登录` tab 只配置数据中心作为统一入口登录 QPO 的参数，不混入数据中心出站调用配置，也不混入入站开放接口 `QPO_OPEN_API_TOKEN`。三类配置边界如下：

| 配置页 | 方向 | 用途 |
| --- | --- | --- |
| 出站接口 | QPO -> 数据中心 -> MES/WMS/U9 | QPO 主动调用外部业务接口。 |
| 入站接口 | 数据中心 / 外部系统 -> QPO 开放接口 | MES 回调、销售订单外部同步等接口 token 和入口元数据。 |
| 数据中心登录 | 数据中心页面 -> QPO 登录态 | 数据中心统一入口按钮进入 QPO 的 SSO 签名、开关和跳转策略。 |

### 8.1 运行时配置项

建议配置项：

| 配置项 | 示例 | 说明 |
| --- | --- | --- |
| `erp.auth.data-center-sso.enabled` | `true` | 是否启用数据中心 SSO。 |
| `erp.auth.data-center-sso.app-code` | `QPO` | QPO 接受的目标系统编码。 |
| `erp.auth.data-center-sso.shared-secret` | 空 | HMAC 共享密钥；页面配置优先，环境变量只作为 fallback。 |
| `erp.auth.data-center-sso.timestamp-skew-seconds` | `120` | 时间戳允许窗口。 |
| `erp.auth.data-center-sso.nonce-ttl-minutes` | `10` | `nonce` 留存时间。 |
| `erp.auth.data-center-sso.default-redirect` | `/dashboard` | 默认登录后跳转路径。 |
| `erp.auth.data-center-sso.allowed-redirect-prefixes` | `/,/dashboard,/orders` | 允许的内部跳转前缀。 |

运行时解析顺序：

```text
active DB 配置 -> erp.env / application.yaml fallback -> 安全默认值
```

如果页面存在 active 配置，QPO 必须优先使用页面配置。只有页面无 active 配置、字段为空或首次部署尚未配置时，才读取 `erp.env` / `application.yaml` fallback。真实共享密钥不得返回前端明文；列表和详情接口只能返回“已配置 / 未配置”、掩码、更新时间、测试状态和密钥是否变更。

### 8.2 系统配置页面字段

| 字段 | 示例 | 必填 | 说明 |
| --- | --- | --- | --- |
| 配置名称 | 数据中心测试环境 | 是 | 运维可读名称。 |
| 启用状态 | 启用 | 是 | 关闭后不允许被激活。 |
| 激活状态 | 当前使用 | 是 | 同一时间只允许一个数据中心登录配置 active。 |
| App Code | QPO | 是 | 必须与数据中心跳转参数 `appCode` 一致。 |
| Shared Secret | 已配置 | 是 | HMAC 共享密钥；保存时可录入新值，查询不回显明文。 |
| 时间戳窗口 | 120 秒 | 是 | 超过窗口拒绝登录。 |
| Nonce 留存 | 10 分钟 | 是 | 用于防重放记录保留。 |
| 默认跳转路径 | `/dashboard` | 是 | `redirect` 为空或不合法时使用。 |
| 允许跳转前缀 | `/,/dashboard,/orders` | 是 | 只允许 QPO 内部相对路径。 |
| 最后测试状态 | SUCCESS / FAILED | 否 | 页面“测试配置”结果。 |
| 备注 | 数据中心统一入口 | 否 | 运维说明，不参与签名。 |

页面操作：

- 新增 / 编辑配置：维护除激活状态外的配置字段；密钥留空表示沿用旧密钥。
- 测试配置：校验必填项、签名计算、redirect 白名单和密钥是否已配置，不生成真实登录态。
- 激活配置：仅测试通过且启用的配置可以激活；激活时自动取消其它 active 配置。
- 停用配置：停用后不能用于 SSO 登录；如果停用 active 配置，SSO 运行时回落到 fallback 或关闭。
- 轮换密钥：通过编辑配置录入新密钥，审计只记录 `secretChanged=true`，不记录密钥原文。

### 8.3 环境变量 fallback

环境变量建议：

```text
ERP_DATA_CENTER_SSO_ENABLED=true
ERP_DATA_CENTER_SSO_APP_CODE=QPO
ERP_DATA_CENTER_SSO_SHARED_SECRET=
ERP_DATA_CENTER_SSO_TIMESTAMP_SKEW_SECONDS=120
ERP_DATA_CENTER_SSO_NONCE_TTL_MINUTES=10
ERP_DATA_CENTER_SSO_DEFAULT_REDIRECT=/dashboard
```

`erp.env.example` 中 `ERP_DATA_CENTER_SSO_SHARED_SECRET` 必须为空；真实密钥不得提交。生产优先在系统配置页面维护 active 配置，`erp.env` 只用于首次部署、页面配置缺失或应急恢复。

## 9. 数据库设计

由于 AGENTS.md 明确要求 Flyway migration append-only，且 `V1__qpo_baseline_schema.sql`、`V2__qpo_baseline_seed_data.sql` 不可修改，本需求新增表必须使用下一个未使用版本。

截至本文更新时，当前最新 migration 为 `V21__remove_force_password_change.sql`，建议新增：

```text
quote-backend/src/main/resources/db/migration/V22__data_center_sso_login_config.sql
```

如果实施时仓库已有新的 V22，则顺延使用下一个未使用版本。

建议新增两张表：

1. `system_data_center_sso_config`：保存系统配置页的数据中心登录配置。
2. `auth_data_center_sso_nonce`：保存 SSO 登录 nonce 防重放记录。

### 9.1 数据中心登录配置表

```sql
CREATE TABLE system_data_center_sso_config (
    id bigserial PRIMARY KEY,
    config_name character varying(120) NOT NULL,
    enabled boolean DEFAULT true NOT NULL,
    active boolean DEFAULT false NOT NULL,
    app_code character varying(50) DEFAULT 'QPO' NOT NULL,
    shared_secret_cipher text,
    timestamp_skew_seconds integer DEFAULT 120 NOT NULL,
    nonce_ttl_minutes integer DEFAULT 10 NOT NULL,
    default_redirect character varying(255) DEFAULT '/dashboard' NOT NULL,
    allowed_redirect_prefixes text DEFAULT '/,/dashboard' NOT NULL,
    test_status character varying(20) DEFAULT 'NOT_TESTED' NOT NULL,
    last_test_at timestamp without time zone,
    last_test_message character varying(500),
    remark character varying(500),
    created_by bigint,
    updated_by bigint,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CONSTRAINT ck_system_data_center_sso_test_status
        CHECK (test_status IN ('NOT_TESTED', 'SUCCESS', 'FAILED')),
    CONSTRAINT ck_system_data_center_sso_timestamp_skew
        CHECK (timestamp_skew_seconds BETWEEN 30 AND 600),
    CONSTRAINT ck_system_data_center_sso_nonce_ttl
        CHECK (nonce_ttl_minutes BETWEEN 1 AND 1440)
);

CREATE UNIQUE INDEX uk_system_data_center_sso_active
    ON system_data_center_sso_config(active)
    WHERE active = true;

CREATE INDEX idx_system_data_center_sso_enabled
    ON system_data_center_sso_config(enabled);
```

说明：

- `shared_secret_cipher` 存储可供后端校验 HMAC 的密文字段或受控密钥值，不允许通过查询接口明文返回。
- 页面保存时如果密钥字段为空，表示沿用原密钥；如果传入非掩码新值，才更新密钥。
- active 配置全局唯一；没有 active 配置时才回退 `erp.env`。
- 审计只记录 `secretConfigured`、`secretChanged`、`appCode`、`enabled`、`active`、时间窗口和 redirect 配置摘要。

### 9.2 Nonce 防重放表

```sql
CREATE TABLE auth_data_center_sso_nonce (
    id bigserial PRIMARY KEY,
    nonce character varying(120) NOT NULL,
    username character varying(120) NOT NULL,
    app_code character varying(50) NOT NULL,
    request_timestamp bigint NOT NULL,
    nonce_hash character varying(64) NOT NULL,
    used_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    expires_at timestamp without time zone NOT NULL,
    result character varying(30) NOT NULL,
    failure_reason character varying(80),
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL
);

CREATE UNIQUE INDEX uk_auth_data_center_sso_nonce
    ON auth_data_center_sso_nonce(nonce_hash);

CREATE INDEX idx_auth_data_center_sso_nonce_expires
    ON auth_data_center_sso_nonce(expires_at);
```

说明：

- `nonce_hash = SHA-256(appCode + ':' + nonce)`，避免日志和表中直接暴露 nonce 原文。
- 成功和失败均可记录摘要，但不能记录 `signature`、完整 URL 或共享密钥。
- 可由定时清理任务删除过期记录；第一阶段也可仅保留最近若干天。

## 10. 后端实现设计

### 10.1 新增 DTO

建议新增：

```text
quote-backend/src/main/java/com/machining/quote/dto/auth/DataCenterSsoExchangeReq.java
```

字段：

```java
private String appCode;
private String username;
private String displayName;
private Long timestamp;
private String nonce;
private String redirect;
private String signature;
```

### 10.2 新增配置类

建议新增：

```text
quote-backend/src/main/java/com/machining/quote/config/DataCenterSsoProperties.java
```

绑定：

```text
erp.auth.data-center-sso.*
```

该配置类只承载 `erp.env` / `application.yaml` fallback。运行时必须通过 `DataCenterSsoConfigResolver` 优先读取系统配置页 active 配置，再回退到该配置类。

### 10.3 新增系统配置管理模型

建议新增：

```text
quote-backend/src/main/java/com/machining/quote/entity/SystemDataCenterSsoConfig.java
quote-backend/src/main/java/com/machining/quote/dto/system/SystemDataCenterSsoConfigDTO.java
quote-backend/src/main/java/com/machining/quote/dto/system/SystemDataCenterSsoConfigSaveReq.java
quote-backend/src/main/java/com/machining/quote/mapper/SystemDataCenterSsoConfigMapper.java
quote-backend/src/main/java/com/machining/quote/service/SystemDataCenterSsoConfigService.java
quote-backend/src/main/java/com/machining/quote/service/auth/DataCenterSsoConfigResolver.java
quote-backend/src/main/java/com/machining/quote/controller/SystemDataCenterSsoConfigController.java
```

Controller 建议路径：

```http
GET  /api/system/data-center-sso-configs
POST /api/system/data-center-sso-configs
PUT  /api/system/data-center-sso-configs/{id}
POST /api/system/data-center-sso-configs/{id}/test
PUT  /api/system/data-center-sso-configs/{id}/activate
PUT  /api/system/data-center-sso-configs/{id}/disable
```

权限要求：

```text
system:config:view
system:config:sso:update
system:config:sso:test
system:config:sso:activate
```

配置查询接口不能返回 `sharedSecret` 明文；保存接口支持“密钥留空沿用原值、传入新值则更新”。`DataCenterSsoConfigResolver` 返回运行时快照，字段包括 `source`、`enabled`、`appCode`、`sharedSecret`、`timestampSkewSeconds`、`nonceTtlMinutes`、`defaultRedirect` 和 `allowedRedirectPrefixes`。

### 10.4 新增 nonce entity 与 mapper

建议新增：

```text
quote-backend/src/main/java/com/machining/quote/entity/AuthDataCenterSsoNonce.java
quote-backend/src/main/java/com/machining/quote/mapper/AuthDataCenterSsoNonceMapper.java
```

Mapper 至少提供：

```java
int insertIgnore(AuthDataCenterSsoNonce row);
```

可使用 PostgreSQL：

```sql
INSERT INTO auth_data_center_sso_nonce (...) VALUES (...)
ON CONFLICT (nonce_hash) DO NOTHING
```

返回 0 时判定为重复登录请求。

### 10.5 新增签名服务

建议新增：

```text
quote-backend/src/main/java/com/machining/quote/service/auth/DataCenterSsoSignatureService.java
```

职责：

- 规范化 `redirect`。
- 构造 canonical string。
- 计算 HMAC-SHA256。
- 使用常量时间比较签名。
- 校验时间戳窗口。

### 10.6 扩展 AuthService

建议在 `AuthService` 中抽取复用方法：

```java
public Map<String, Object> issueLoginToken(ErpUser user, String auditAction, Map<String, Object> auditPayload)
```

本地密码登录和数据中心 SSO 登录都复用同一套 token/user/roles/permissions/menus 返回逻辑，避免两套登录响应漂移。

### 10.7 新增 SSO 服务

建议新增：

```text
quote-backend/src/main/java/com/machining/quote/service/auth/DataCenterSsoAuthService.java
```

职责：

- 校验功能启用。
- 通过 `DataCenterSsoConfigResolver` 获取 active DB 配置或 fallback 配置。
- 调用签名服务校验请求。
- 写入 nonce 防重放记录。
- 查询 QPO 本地用户。
- 判断用户启用状态。
- 加载权限信息，拒绝无权限账号。
- 调用 `AuthService.issueLoginToken(...)` 签发 QPO JWT。
- 写入 `AUTH_DATACENTER_SSO_SUCCESS` / `AUTH_DATACENTER_SSO_FAILED` 审计。

### 10.8 新增 Controller

建议修改：

```text
quote-backend/src/main/java/com/machining/quote/controller/AuthController.java
```

新增：

```java
@PostMapping("/datacenter-sso/exchange")
public Map<String, Object> exchangeDataCenterSso(@RequestBody DataCenterSsoExchangeReq req) {
    return dataCenterSsoAuthService.exchange(req);
}
```

### 10.9 AuthInterceptor 放行

修改：

```text
quote-backend/src/main/java/com/machining/quote/config/AuthInterceptor.java
```

`isPublicPath()` 放行：

```text
/api/auth/datacenter-sso/exchange
```

该接口自身必须完成签名校验，不能依赖登录态。

## 11. 前端实现设计

### 11.1 新增 API

修改：

```text
quote-frontend/src/services/authApi.js
```

新增：

```js
export const exchangeDataCenterSso = (payload) => request('/api/auth/datacenter-sso/exchange', {
  method: 'POST',
  data: payload
})
```

### 11.2 扩展 authStore

修改：

```text
quote-frontend/src/stores/authStore.js
```

新增 action：

```js
async loginWithDataCenterSso(payload) {
  const response = await exchangeDataCenterSso(payload)
  if (!response?.success) {
    throw new Error(response?.message || '数据中心登录失败')
  }
  this.setAuth(response.token, response.user, response)
  return response
}
```

### 11.3 新增回调页

建议新增：

```text
quote-frontend/src/views/DataCenterSsoCallback.vue
```

页面行为：

1. 从 query 读取 `appCode`、`username`、`displayName`、`timestamp`、`nonce`、`redirect`、`signature`。
2. 调用 `authStore.loginWithDataCenterSso(payload)`。
3. 成功后跳转到返回的 `redirect` 或 `/dashboard`。
4. 失败后跳转 `/login`，带错误提示。
5. 页面不展示完整 query，不打印参数到 console。

### 11.4 路由放行

修改：

```text
quote-frontend/src/router/index.js
```

新增路由：

```js
{ path: '/sso/datacenter/callback', component: DataCenterSsoCallback }
```

路由守卫中该路径应与 `/login` 一样免登录放行。

### 11.5 新增系统配置页数据中心登录 tab

修改：

```text
quote-frontend/src/components/SystemConfigManage.vue
quote-frontend/src/components/system-config/SystemDataCenterSsoConfigPanel.vue
quote-frontend/src/services/systemDataCenterSsoConfigApi.js
```

`SystemConfigManage.vue` 增加 tab：

```vue
<el-tab-pane label="数据中心登录" name="dataCenterSso">
  <SystemDataCenterSsoConfigPanel />
</el-tab-pane>
```

页面行为：

1. 列表展示配置名称、启用状态、激活状态、App Code、密钥配置状态、时间戳窗口、默认跳转、最后测试状态和更新时间。
2. 新增 / 编辑抽屉维护配置字段；密钥输入框为空时表示沿用原值。
3. 点击“测试配置”只做本地配置校验和签名样例校验，不生成真实登录态。
4. 点击“激活”前必须确认配置启用、密钥已配置、测试状态成功。
5. 页面不回显 `sharedSecret` 明文，不写入 localStorage / sessionStorage / Pinia 持久化。
6. 所有按钮沿用系统配置的加载态和重复点击防护模式。

## 12. 权限与数据范围

本需求不新增业务权限点，但第一版新增系统配置页，因此需要新增独立的系统配置按钮权限，继续挂在“系统配置”菜单下：

| 权限点 | 类型 | 默认角色 | 说明 |
| --- | --- | --- | --- |
| `system:config:sso:update` | BUTTON | `SUPER_ADMIN` | 新增、编辑、停用数据中心登录配置。 |
| `system:config:sso:test` | BUTTON | `SUPER_ADMIN` | 测试数据中心登录配置。 |
| `system:config:sso:activate` | BUTTON | `SUPER_ADMIN` | 激活数据中心登录配置。 |

`system:config:view` 继续控制系统配置页面入口和配置查询；不新增 `common:*` 权限。

治理口径：

- 数据中心 SSO 登录只是一种认证入口，不是业务能力。
- 数据中心登录配置属于系统治理能力，只建议授予超级管理员或系统运维账号；如创建系统运维账号，数据范围建议为 `NONE`。
- QPO 现有 `system_user` / `erp_user` / `system_role` / `system_permission` / 数据范围继续有效。
- 用户进入 QPO 后看到的菜单和按钮完全由 QPO 本地权限决定。
- 用户列表、角色授权、数据范围管理不搬到数据中心。
- 无 QPO 权限时，即使数据中心登录成功也拒绝进入 QPO。

## 13. 审计与日志

### 13.1 新增审计动作

建议新增：

| 动作 | 模块 | 中文名 |
| --- | --- | --- |
| `AUTH_DATACENTER_SSO_SUCCESS` | `AUTH` | 数据中心SSO登录成功 |
| `AUTH_DATACENTER_SSO_FAILED` | `AUTH` | 数据中心SSO登录失败 |
| `SYSTEM_DATACENTER_SSO_CONFIG_CREATE` | `SYSTEM_CONFIG` | 数据中心登录配置新增 |
| `SYSTEM_DATACENTER_SSO_CONFIG_UPDATE` | `SYSTEM_CONFIG` | 数据中心登录配置更新 |
| `SYSTEM_DATACENTER_SSO_CONFIG_TEST` | `SYSTEM_CONFIG` | 数据中心登录配置测试 |
| `SYSTEM_DATACENTER_SSO_CONFIG_ACTIVATE` | `SYSTEM_CONFIG` | 数据中心登录配置激活 |
| `SYSTEM_DATACENTER_SSO_CONFIG_DISABLE` | `SYSTEM_CONFIG` | 数据中心登录配置停用 |

审计摘要允许记录：

```text
username
displayName
appCode
configId
configName
enabled
active
source
secretConfigured
secretChanged
result
reason
timestamp
nonceHash
redirect
timestampSkewSeconds
nonceTtlMinutes
allowedRedirectPrefixes
```

禁止记录：

```text
signature
sharedSecret
完整 SSO URL
QPO JWT
Authorization header
Cookie
```

### 13.2 文件日志

文件日志只记录低噪声安全摘要：

```text
Data center SSO login succeeded username=... appCode=QPO nonceHash=... durationMs=...
Data center SSO login failed username=... appCode=QPO reason=... nonceHash=... durationMs=...
```

不得打印完整 query、完整请求体、`signature`、共享密钥或 token。

## 14. 安全清单

- [ ] 不接受裸 `username` 登录。
- [ ] `signature` 使用常量时间比较。
- [ ] `timestamp` 过期拒绝。
- [ ] `nonce` 重复拒绝。
- [ ] `nonce` 使用数据库唯一约束防并发重放。
- [ ] `appCode` 不等于 `QPO` 拒绝。
- [ ] `redirect` 只允许 QPO 内部路径，拒绝 `http://`、`https://`、`//evil.com`。
- [ ] 用户不存在拒绝。
- [ ] 用户禁用拒绝。
- [ ] 用户无权限拒绝。
- [ ] 数据中心登录配置查询不回显共享密钥明文。
- [ ] 数据中心登录配置保存密钥留空时沿用原值。
- [ ] 只有启用、测试成功、密钥已配置的 SSO 配置可以激活。
- [ ] 同一时间只有一个数据中心登录配置 active。
- [ ] 日志和审计不记录签名、token、共享密钥、完整 URL。
- [ ] `erp.env.example` 不写真实密钥。
- [ ] 不修改已执行 migration。

## 15. 数据中心联调示例

### 15.1 数据中心生成 canonical string

```text
appCode=QPO
nonce=dc-test-001
redirect=/dashboard
timestamp=1780000000000
username=zhangsan
```

### 15.2 数据中心跳转 URL

```text
https://qpo.example.com/sso/datacenter/callback?appCode=QPO&username=zhangsan&displayName=%E5%BC%A0%E4%B8%89&timestamp=1780000000000&nonce=dc-test-001&redirect=%2Fdashboard&signature=base64url-signature
```

### 15.3 QPO 联调前置数据

QPO 管理员需要提前确认：

```sql
SELECT id, username, enabled, role
FROM erp_user
WHERE username = 'zhangsan';
```

并确认该用户已经配置角色、权限和数据范围。

## 16. 测试设计

### 16.1 后端测试

建议新增 / 修改：

```text
quote-backend/src/test/java/com/machining/quote/DataCenterSsoSignatureServiceTest.java
quote-backend/src/test/java/com/machining/quote/DataCenterSsoAuthServiceTest.java
quote-backend/src/test/java/com/machining/quote/DataCenterSsoControllerTest.java
quote-backend/src/test/java/com/machining/quote/SystemDataCenterSsoConfigTest.java
quote-backend/src/test/java/com/machining/quote/AuditActionCatalogCharacterizationTest.java
quote-backend/src/test/java/com/machining/quote/FlywayMigrationPolicyTest.java
```

必须覆盖：

- 正确签名可以换取 QPO JWT。
- 错误签名失败。
- 过期时间戳失败。
- 未来时间戳超出窗口失败。
- 重复 `nonce` 失败。
- `appCode` 非 QPO 失败。
- 外部 `redirect` 失败。
- 用户不存在失败。
- 用户禁用失败。
- 无权限用户失败。
- 成功响应包含 `token/user/roles/permissions/menus`。
- 页面 active SSO 配置优先于 `erp.env` fallback。
- 配置管理接口受 `system:config:sso:*` 权限保护。
- 配置查询不返回共享密钥明文。
- 激活配置会取消其它 active 配置。
- 配置测试、更新、激活审计不包含共享密钥。
- 失败审计不包含 `signature` 或 token。
- `V1/V2` baseline 文件未被修改。

### 16.2 前端测试

建议新增 / 修改：

```text
quote-frontend/src/datacenterSsoCallback.test.js
quote-frontend/src/systemDataCenterSsoConfig.test.js
quote-frontend/src/services/authApi.test.js
quote-frontend/src/stores/authStore.test.js
quote-frontend/src/routerGuard.test.js
```

必须覆盖：

- `authApi.exchangeDataCenterSso()` 调用正确接口。
- `authStore.loginWithDataCenterSso()` 成功后写入 token、用户和权限。
- 回调页成功后跳转 `redirect`。
- 回调页失败后回到 `/login` 并展示错误。
- `/sso/datacenter/callback` 未登录时不会被路由守卫拦截到 `/login`。
- 回调页不在页面或 console 输出完整签名参数。
- 系统配置页出现“数据中心登录” tab。
- 数据中心登录配置页支持新增、编辑、测试、激活、停用。
- 密钥输入框不会回显明文，留空保存表示沿用原密钥。

## 17. 分阶段实施计划

### 阶段 0：已确认口径记录，不阻塞开发

**目标：** 记录已经确认的最小业务口径；本阶段不作为开发前置门槛，实际开发可以直接进入阶段 1。

- [x] 统一账号字段已确认使用 `username`，对应 QPO 当前已有登录账号名，例如 `admin`、`market`。
- [x] QPO 本地账号匹配规则已确认：`erp_user.username = request.username.trim()`。
- [x] 数据中心不传 QPO 角色、权限、菜单或数据范围；用户进入 QPO 后的能力仍完全由 QPO 本地账号、角色、权限和数据范围决定。
- [x] QPO 不按姓名、手机号、邮箱或显示名匹配账号。
- [ ] `appCode`、timestamp 窗口、nonce TTL、默认跳转路径、允许跳转前缀和共享密钥均进入阶段 1，按默认值实现并在系统配置页支持后续调整。
- [ ] 签名算法第一版按本文 HMAC-SHA256 实现；如数据中心后续调整协议，再在阶段 1/2 的实现和配置中同步变更。
- [ ] QPO 管理员可在联调前准备正常账号、禁用账号、无权限账号；这不是阶段 1 开发阻塞项。
- [ ] 真实共享密钥仍只能通过安全渠道交付，不进仓库、不进文档正文。

**阶段门槛：** 无阻塞门槛。`username` 口径已确认后，开发直接从阶段 1 开始；其它参数均作为可配置项在后续阶段实现和联调。

### 阶段 1：数据库、配置管理与签名校验基础

**目标：** 建立数据中心登录配置可持久化、可管理、可作为运行时优先配置，并让 QPO 能可靠判断数据中心 SSO 参数是否可信。

**涉及文件：**

```text
quote-backend/src/main/resources/db/migration/V22__data_center_sso_login_config.sql
quote-backend/src/main/java/com/machining/quote/config/DataCenterSsoProperties.java
quote-backend/src/main/java/com/machining/quote/dto/auth/DataCenterSsoExchangeReq.java
quote-backend/src/main/java/com/machining/quote/entity/SystemDataCenterSsoConfig.java
quote-backend/src/main/java/com/machining/quote/entity/AuthDataCenterSsoNonce.java
quote-backend/src/main/java/com/machining/quote/mapper/SystemDataCenterSsoConfigMapper.java
quote-backend/src/main/java/com/machining/quote/mapper/AuthDataCenterSsoNonceMapper.java
quote-backend/src/main/java/com/machining/quote/service/SystemDataCenterSsoConfigService.java
quote-backend/src/main/java/com/machining/quote/service/auth/DataCenterSsoConfigResolver.java
quote-backend/src/main/java/com/machining/quote/service/auth/DataCenterSsoSignatureService.java
quote-backend/src/main/java/com/machining/quote/controller/SystemDataCenterSsoConfigController.java
quote-backend/src/test/java/com/machining/quote/SystemDataCenterSsoConfigTest.java
quote-backend/src/test/java/com/machining/quote/DataCenterSsoSignatureServiceTest.java
quote-backend/src/test/java/com/machining/quote/DataCenterSsoAuthServiceTest.java
```

- [ ] 先写失败测试：正确 canonical string、正确签名、错误签名、过期 timestamp、外部 redirect、重复 nonce。
- [ ] 先写配置管理失败测试：配置表存在、active 唯一、查询不回显共享密钥、保存空密钥沿用原值、测试成功后才可激活。
- [ ] 新增 append-only Flyway migration；创建 `system_data_center_sso_config` 和 `auth_data_center_sso_nonce`，不得修改 `V1/V2` baseline。
- [ ] 新增 fallback 配置类、DTO、配置 entity / mapper / controller / service。
- [ ] 新增 `DataCenterSsoConfigResolver`，运行时优先 active DB 配置，再回退 `erp.env`。
- [ ] 新增 nonce entity / mapper，使用唯一索引防重放。
- [ ] 实现签名服务和 redirect 安全校验。
- [ ] 运行：

```powershell
cd quote-backend
.\mvnw.cmd "-Dtest=SystemDataCenterSsoConfigTest,DataCenterSsoSignatureServiceTest,DataCenterSsoAuthServiceTest" test
```

**阶段门槛：** 配置持久化、active 解析、签名、时间、nonce 和 redirect 安全测试全部通过。

### 阶段 2：QPO SSO 换取本地 JWT

**目标：** QPO 可以用可信 `username` 签发现有 QPO 登录 token。

**涉及文件：**

```text
quote-backend/src/main/java/com/machining/quote/service/AuthService.java
quote-backend/src/main/java/com/machining/quote/service/auth/DataCenterSsoAuthService.java
quote-backend/src/main/java/com/machining/quote/controller/AuthController.java
quote-backend/src/main/java/com/machining/quote/config/AuthInterceptor.java
quote-backend/src/main/java/com/machining/quote/service/AuditService.java
quote-backend/src/test/java/com/machining/quote/DataCenterSsoControllerTest.java
quote-backend/src/test/java/com/machining/quote/AuditActionCatalogCharacterizationTest.java
```

- [ ] 先写失败测试：用户不存在、用户禁用、无权限、签名错误、nonce 重复。
- [ ] 先写成功测试：正常用户返回 `token/user/roles/permissions/menus`。
- [ ] 从 `AuthService.login()` 抽取可复用 token 签发逻辑。
- [ ] 新增 `DataCenterSsoAuthService.exchange()`。
- [ ] `AuthController` 暴露 `/api/auth/datacenter-sso/exchange`。
- [ ] `AuthInterceptor` 放行该 exchange 接口。
- [ ] `AuditService` 增加 SSO 成功 / 失败动作中文名和模块归类。
- [ ] 运行：

```powershell
cd quote-backend
.\mvnw.cmd "-Dtest=DataCenterSsoControllerTest,AuditActionCatalogCharacterizationTest,PermissionAuthServiceTest,PermissionBootstrapServiceTest" test
```

**阶段门槛：** SSO 成功路径复用现有 QPO JWT 和权限载荷；所有失败路径都有明确错误且审计不泄密。

### 阶段 3：前端回调页、authStore 与系统配置页接入

**目标：** 用户从数据中心跳转到 QPO 后能自动完成登录并进入目标页；管理员能在系统配置页维护数据中心登录配置。

**涉及文件：**

```text
quote-frontend/src/services/authApi.js
quote-frontend/src/services/systemDataCenterSsoConfigApi.js
quote-frontend/src/stores/authStore.js
quote-frontend/src/router/index.js
quote-frontend/src/views/DataCenterSsoCallback.vue
quote-frontend/src/components/SystemConfigManage.vue
quote-frontend/src/components/system-config/SystemDataCenterSsoConfigPanel.vue
quote-frontend/src/datacenterSsoCallback.test.js
quote-frontend/src/systemDataCenterSsoConfig.test.js
quote-frontend/src/services/authApi.test.js
quote-frontend/src/stores/authStore.test.js
```

- [ ] 先写失败测试：未登录访问 `/sso/datacenter/callback` 不被重定向到 `/login`。
- [ ] 先写成功测试：回调页调用 exchange，成功后写入 authStore 并跳转 redirect。
- [ ] 先写失败测试：exchange 失败后跳转登录页并显示错误。
- [ ] 新增 `exchangeDataCenterSso()` API。
- [ ] 新增 `authStore.loginWithDataCenterSso()`。
- [ ] 新增回调页组件。
- [ ] 修改路由守卫白名单。
- [ ] 在 `SystemConfigManage.vue` 新增“数据中心登录” tab。
- [ ] 新增 `SystemDataCenterSsoConfigPanel.vue`，支持查询、新增、编辑、测试、激活、停用。
- [ ] 前端不回显共享密钥明文，不把密钥写入本地缓存。
- [ ] 运行：

```powershell
cd quote-frontend
npm.cmd test -- datacenterSsoCallback.test.js systemDataCenterSsoConfig.test.js authApi.test.js authStore.test.js
```

**阶段门槛：** 前端可完成数据中心跳转登录，失败路径清晰返回登录页，系统配置页可维护数据中心登录配置。

### 阶段 4：配置、部署文档与安全日志收口

**目标：** fallback 配置、权限文档、部署文档和日志脱敏口径全部收口。

**涉及文件：**

```text
quote-backend/src/main/resources/application.yaml
erp.env.example
docker-compose.yml
docs/QPO权限用户体系/02-权限点清单与现有角色映射.md
docs/QPO权限用户体系/03-权限体系部署与管理员使用说明.md
docs/QPO日志模块/01-操作日志模块开发文档.md
docs/QPO日志模块/03-日志运维说明.md
docs/QPO日志模块/04-主流程文件日志规范化开发文档.md
```

- [ ] 新增 SSO fallback 配置项，真实密钥优先由系统配置页维护，`erp.env` 只作 fallback。
- [ ] `erp.env.example` 保留空密钥。
- [ ] Compose 注入 SSO 环境变量但不输出真实密钥。
- [ ] 权限文档新增 `system:config:sso:update`、`system:config:sso:test`、`system:config:sso:activate`，并明确 SSO 不新增业务权限点。
- [ ] 日志文档明确：不得记录完整 SSO URL、signature、共享密钥、token。
- [ ] 运行文档字符化测试和日志脱敏测试。
- [ ] 运行：

```powershell
cd quote-backend
.\mvnw.cmd "-Dtest=AuditActionCatalogCharacterizationTest,LoggingConventionCharacterizationTest" test
```

**阶段门槛：** 配置可部署、文档口径一致、日志脱敏测试通过。

### 阶段 5：数据中心联调

**目标：** 使用数据中心真实按钮完成端到端登录。

- [ ] QPO 管理员在 **系统配置 > 数据中心登录** 新增并激活 SSO 配置。
- [ ] 如页面未配置，确认 `erp.env` fallback 可作为应急方案；正式联调优先使用页面 active 配置。
- [ ] 数据中心配置 QPO 入口地址。
- [ ] 双方配置同一套共享密钥。
- [ ] 数据中心生成测试用户 `username`，QPO 创建同名账号并配置角色权限。
- [ ] 测试正常账号进入 QPO。
- [ ] 测试禁用账号拒绝。
- [ ] 测试无权限账号拒绝。
- [ ] 测试复制旧 URL 重放失败。
- [ ] 测试 timestamp 过期失败。
- [ ] 测试外部 redirect 被拒绝。
- [ ] 检查 QPO 操作日志和文件日志均无 `signature`、token、共享密钥、完整 URL。

**阶段门槛：** 数据中心入口可真实进入 QPO，失败场景明确，日志无敏感信息。

### 阶段 6：发布前回归

**目标：** 确认 SSO 不破坏本地登录、权限、数据范围和现有业务入口。

- [ ] 后端全量测试：

```powershell
cd quote-backend
.\mvnw.cmd test
```

- [ ] 前端全量测试：

```powershell
cd quote-frontend
npm.cmd test
```

- [ ] 前端构建：

```powershell
cd quote-frontend
npm.cmd run build
```

- [ ] 后端打包：

```powershell
cd quote-backend
.\mvnw.cmd -DskipTests package
```

- [ ] Flyway policy 验证：确认 `V1/V2` 未变更，启动无 checksum mismatch。

**阶段门槛：** 自动化测试、构建、Flyway 校验和数据中心真实联调全部通过。

## 18. 最终验收清单

- [ ] 数据中心只传统一账号名，不传 QPO 权限。
- [ ] QPO 不接受未签名 username。
- [ ] QPO 使用本地账号、角色、权限和数据范围决定登录后能力。
- [ ] 不存在 QPO 账号时拒绝。
- [ ] QPO 账号禁用时拒绝。
- [ ] QPO 账号无权限时拒绝。
- [ ] 签名错误拒绝。
- [ ] 过期 timestamp 拒绝。
- [ ] 重复 nonce 拒绝。
- [ ] 外部 redirect 拒绝。
- [ ] SSO 成功后返回现有 `token/user/roles/permissions/menus` 结构。
- [ ] 前端写入现有 authStore，不新增第二套登录态。
- [ ] 系统配置页存在“数据中心登录” tab，支持配置、测试、激活和停用。
- [ ] 数据中心登录配置查询不回显共享密钥明文，日志和审计只记录密钥状态。
- [ ] active DB 配置优先于 `erp.env` fallback。
- [ ] 本地账号密码登录仍可用作管理员 / 应急入口。
- [ ] 审计可查询 SSO 成功和失败。
- [ ] 日志不包含完整 URL、signature、共享密钥、token、Authorization、Cookie。
- [ ] Flyway migration 只新增 append-only 文件，不修改 V1/V2。
