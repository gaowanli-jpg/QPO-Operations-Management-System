# 提供给数据中心的 QPO 统一入口 SSO 对接说明

## 1. 对接目标

数据中心作为统一入口，用户在数据中心登录后点击“进入 QPO”，浏览器跳转到 QPO 的 SSO 回调页。QPO 校验数据中心生成的签名登录参数后，使用 `username` 匹配 QPO 本地账号，并按 QPO 本地角色、权限、菜单和数据范围签发 QPO 登录态。

边界说明：

- 数据中心只负责证明 `username` 对应的用户已经在数据中心完成认证。
- 数据中心不向 QPO 传角色、权限、菜单、客户范围或组织范围。
- QPO 不接受裸 `username` 登录，必须校验 `timestamp`、`nonce`、`redirect` 和 `signature`。
- QPO 账号必须提前存在、启用并配置本地角色权限，否则拒绝进入 QPO。

## 2. 数据中心需要配置的 QPO 信息

| 配置项 | 示例 | 说明 |
| --- | --- | --- |
| QPO 前端地址 | `https://qpo.example.com` | 由部署环境提供。 |
| SSO 回调路径 | `/sso/datacenter/callback` | 数据中心按钮跳转到该前端路径。 |
| App Code | `QPO` | 必须与 QPO 系统配置中的 App Code 一致。 |
| Shared Secret | 线下交付 | HMAC-SHA256 共享密钥，不得写入普通文档或日志。 |
| 默认跳转 | `/dashboard` | `redirect` 为空时按该值参与签名。 |
| 时间窗口 | 默认 `120` 秒 | 可在 QPO 系统配置页调整。 |

QPO 管理员在 **系统 > 系统配置 > 数据中心登录** 中维护并激活同一套 App Code、Shared Secret、时间窗口和允许跳转前缀。

## 3. 数据中心跳转地址

用户点击数据中心里的“进入 QPO”后，数据中心让浏览器跳转：

```text
{QPO_FRONTEND_BASE_URL}/sso/datacenter/callback
  ?appCode=QPO
  &username=zhangsan
  &displayName=%E5%BC%A0%E4%B8%89
  &timestamp=1780000000000
  &nonce=dc-20260824-01KZZ08THK316WKM13FFENVJD0
  &redirect=%2Fdashboard
  &signature=base64url-signature
```

示例：

```text
https://qpo.example.com/sso/datacenter/callback?appCode=QPO&username=zhangsan&displayName=%E5%BC%A0%E4%B8%89&timestamp=1780000000000&nonce=dc-20260824-01KZZ08THK316WKM13FFENVJD0&redirect=%2Fdashboard&signature=base64url-signature
```

## 4. URL 参数定义

| 字段 | 必填 | 是否参与签名 | 示例 | 说明 |
| --- | --- | --- | --- | --- |
| `appCode` | 是 | 是 | `QPO` | 目标系统编码。 |
| `username` | 是 | 是 | `zhangsan` | 统一账号名，QPO 用它匹配本地账号。 |
| `displayName` | 否 | 否 | `张三` | 展示名，只作辅助展示和审计参考。 |
| `timestamp` | 是 | 是 | `1780000000000` | 毫秒级 Unix 时间戳。 |
| `nonce` | 是 | 是 | `dc-20260824-01K...` | 每次跳转唯一随机串，不能复用。 |
| `redirect` | 否 | 是 | `/dashboard` | 登录成功后进入 QPO 的内部相对路径。 |
| `signature` | 是 | 否 | `base64url...` | HMAC-SHA256 签名结果。 |

`redirect` 规则：

- 只能传 QPO 内部相对路径，必须以 `/` 开头。
- 不允许 `http://`、`https://` 或 `//` 开头的外部跳转。
- 为空时按 `/dashboard` 参与签名。
- 实际可用前缀以 QPO 系统配置页“允许跳转前缀”为准。

## 5. 签名规则

### 5.1 Canonical String

参与签名字段为：`appCode`、`nonce`、`redirect`、`timestamp`、`username`。

字段按如下固定顺序逐行拼接，行分隔符使用 `\n`，编码使用 UTF-8：

```text
appCode={appCode}
nonce={nonce}
redirect={redirectOrDefault}
timestamp={timestamp}
username={username}
```

示例：

```text
appCode=QPO
nonce=dc-20260824-01KZZ08THK316WKM13FFENVJD0
redirect=/dashboard
timestamp=1780000000000
username=zhangsan
```

注意：

- `displayName` 不参与签名。
- `redirect` 为空或未传时，canonical string 中必须使用 QPO 约定的默认值 `/dashboard`。
- 字段值按原始业务值去除首尾空格后参与签名。

### 5.2 Signature

```text
signature = BASE64URL_NO_PADDING(HMAC_SHA256(sharedSecret.trim(), canonicalStringUtf8))
```

Node.js 示例：

```javascript
import crypto from 'node:crypto'

function base64UrlNoPadding(buffer) {
  return buffer.toString('base64')
    .replace(/\+/g, '-')
    .replace(/\//g, '_')
    .replace(/=+$/g, '')
}

function signQpoSso({ appCode, username, timestamp, nonce, redirect }, sharedSecret) {
  const redirectForSign = String(redirect || '/dashboard').trim() || '/dashboard'
  const canonical = [
    `appCode=${String(appCode || '').trim()}`,
    `nonce=${String(nonce || '').trim()}`,
    `redirect=${redirectForSign}`,
    `timestamp=${timestamp ?? ''}`,
    `username=${String(username || '').trim()}`
  ].join('\n')

  const digest = crypto
    .createHmac('sha256', String(sharedSecret || '').trim())
    .update(canonical, 'utf8')
    .digest()

  return base64UrlNoPadding(digest)
}
```

## 6. QPO 后端 Exchange 接口

通常数据中心只需要做浏览器跳转，不需要直接调用该接口。QPO 前端回调页会把 URL 参数提交给 QPO 后端：

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

成功响应沿用 QPO 本地登录结构：

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

失败响应格式：

```json
{
  "success": false,
  "message": "SSO signature is invalid",
  "data": ""
}
```

常见失败：

| 场景 | HTTP | message |
| --- | --- | --- |
| 请求参数缺失或格式不正确 | `400` | `SSO request is invalid` |
| `appCode` 不匹配 | `400` | `SSO target system mismatch` |
| `redirect` 非法或不在白名单 | `400` | `SSO redirect is not allowed` |
| `timestamp` 超出时间窗口 | `401` | `SSO request expired` |
| `nonce` 已使用 | `401` | `SSO request already used` |
| 签名错误 | `401` | `SSO signature is invalid` |
| QPO 未启用或未配置 SSO | `503` | `Data center SSO is not configured` |
| QPO 无同名账号 | `403` | `QPO account is not configured` |
| QPO 同名账号已禁用 | `403` | `QPO account is disabled` |
| QPO 同名账号无权限 | `403` | `QPO account has no permission` |

## 7. 联调前置条件

QPO 侧：

- 已部署包含数据中心 SSO 的版本。
- 已在 **系统 > 系统配置 > 数据中心登录** 新增、测试并激活配置。
- 已与数据中心线下确认同一套 App Code 和 Shared Secret。
- 已创建与数据中心 `username` 一致的 QPO 本地账号。
- 已给 QPO 本地账号配置角色、权限、菜单和数据范围。

数据中心侧：

- 已配置 QPO 前端入口地址和 `/sso/datacenter/callback` 路径。
- 已按本文规则生成 `timestamp`、一次性 `nonce` 和 `signature`。
- 已保证数据中心服务器与 QPO 服务器时间同步。
- 已避免在日志、浏览器埋点、监控平台中记录完整 SSO URL、`signature` 或 Shared Secret。

## 8. 联调用例

| 用例 | 预期结果 |
| --- | --- |
| 正常账号、正确签名、未使用 nonce | 进入 QPO，并跳转到 `redirect`。 |
| QPO 不存在同名 `username` | QPO 拒绝登录，提示账号未配置。 |
| QPO 同名账号已禁用 | QPO 拒绝登录，提示账号已停用。 |
| QPO 同名账号无权限 | QPO 拒绝登录，提示权限未配置。 |
| 复制旧 URL 重放 | QPO 拒绝登录，提示请求已使用。 |
| `timestamp` 超过时间窗口 | QPO 拒绝登录，提示请求已过期。 |
| `signature` 被篡改 | QPO 拒绝登录，提示签名无效。 |
| `redirect=https://example.com` | QPO 拒绝登录，提示跳转地址不允许。 |

## 9. 安全要求

- Shared Secret 只能通过线下安全方式交付，不写入代码仓库、普通文档、聊天记录或工单评论。
- 数据中心不得记录完整 SSO URL，不得记录 `signature`、Shared Secret、数据中心 token 或 QPO token。
- `nonce` 必须全局唯一，建议至少 128 bit 随机性，并带业务前缀便于排查。
- `timestamp` 使用毫秒级 Unix 时间戳，双方服务器建议启用 NTP。
- 数据中心必须通过 HTTPS 访问 QPO。
- 数据中心不应把 QPO 登录 URL 分享给用户二次使用；旧 URL 会因为 `nonce` 重放或时间过期被拒绝。

## 10. 双方确认项

- QPO App Code：`QPO`
- 统一账号字段：`username`
- QPO 前端入口地址：由部署环境提供
- QPO SSO 回调路径：`/sso/datacenter/callback`
- 默认登录后路径：`/dashboard`
- 签名算法：`HMAC-SHA256 + Base64URL 无 padding`
- 时间戳单位：毫秒
- `displayName`：可传，但不参与权限判断，也不参与签名
- 权限来源：只使用 QPO 本地账号、角色、权限、菜单和数据范围
