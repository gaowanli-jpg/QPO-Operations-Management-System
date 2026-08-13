# MES 需求任务行级回写补丁开发文档

## 1. 背景

QPO 当前通过 MES 需求任务接口批量下发待生产零件：

```text
POST /api/v1/mes/openapi/demand/create
```

原 MES 响应行级字段主要包含：

```json
{
  "id": "string",
  "planNo": "string",
  "productCode": "string",
  "status": "string"
}
```

仅依赖 `productCode` 回写时，如果同一批次中存在相同物料号，QPO 无法稳定判断 MES 响应行对应哪一条待生产零件。

本次补丁按 QPO 与 MES 最终沟通口径执行：

- QPO 请求需求任务时，`items[]` 每个零件行新增 `orderType`。
- MES 响应需求任务时，`items[]` 每个返回行新增 `saleOrderNo` 和 `saleOrderItemNo`。
- QPO 以 `bizId + saleOrderNo + saleOrderItemNo` 作为本轮需求任务响应的行级匹配依据。
- 如果未来同一销售订单行拆成多条生产任务，QPO 在请求 MES 前做批次拆分，保证单个 `bizId` 内 `saleOrderNo + saleOrderItemNo` 唯一。

## 2. 最终接口口径

### 2.1 QPO 请求 MES：需求任务新增订单类型

请求体保持原结构，只在 `items[]` 行内增加 `orderType`。

```json
{
  "bizId": "QPO-DEMAND-20260813-000001",
  "customerCode": "C001",
  "customerName": "示例客户",
  "internalProjectNo": "IP-20260813-001",
  "items": [
    {
      "saleOrderNo": "SO-20260813-001",
      "saleOrderItemNo": "10",
      "productCode": "MAT-000123",
      "orderType": "正式订单",
      "demandQty": 10,
      "makeQty": 10,
      "buyQty": 0,
      "stockQty": 0,
      "requiredDate": "2026-08-20T23:59:59",
      "remark": "QPO待生产下发",
      "customerRequirements": "客户特殊要求",
      "bom": [
        {
          "itemCode": "AL6061",
          "quantity": "1.25"
        }
      ],
      "processRoute": [
        {
          "programNum": "P001",
          "processName": "精加工",
          "processTypeCode": "CNC",
          "workHour": "1.5",
          "processDescription": "按图加工"
        }
      ]
    }
  ]
}
```

### 2.2 MES 响应 QPO：需求任务返回销售订单行身份

MES 在 `data.items[]` 中原样返回 QPO 请求中的：

```json
{
  "saleOrderNo": "string",
  "saleOrderItemNo": "string"
}
```

推荐响应体：

```json
{
  "code": "0",
  "msg": "success",
  "data": {
    "bizId": "QPO-DEMAND-20260813-000001",
    "items": [
      {
        "id": "MES-TASK-ID-001",
        "planNo": "PLAN-20260813-001",
        "saleOrderNo": "SO-20260813-001",
        "saleOrderItemNo": "10",
        "productCode": "MAT-000123",
        "status": "SUCCESS"
      }
    ]
  }
}
```

## 3. 字段说明

| 字段 | 位置 | 方向 | 必填 | 说明 |
| --- | --- | --- | --- | --- |
| `bizId` | 根节点 | QPO -> MES，MES -> QPO | 是 | 本次批量需求任务请求号，也是 QPO 侧批次身份。 |
| `items[].saleOrderNo` | 行 | QPO -> MES，MES -> QPO | 是 | 销售订单号，MES 必须原样返回。 |
| `items[].saleOrderItemNo` | 行 | QPO -> MES，MES -> QPO | 是 | 销售订单行号，MES 必须原样返回。 |
| `items[].orderType` | 行 | QPO -> MES | 是 | 订单类型，例如 `备货订单`、`正式订单`、`预测订单`。 |
| `items[].productCode` | 行 | QPO -> MES，MES -> QPO | 是 | MES 物料号 / 产品编码。 |
| `items[].planNo` | 行 | MES -> QPO | 成功时是 | MES 生成的生产计划号。 |
| `items[].id` | 行 | MES -> QPO | 成功时建议返回 | MES 需求任务 ID 或任务标识。 |
| `items[].status` | 行 | MES -> QPO | 是 | MES 行级处理状态。 |

## 4. QPO 行级匹配规则

QPO 收到 MES 需求任务响应后，使用以下组合定位本次被处理的待生产行：

```text
bizId + saleOrderNo + saleOrderItemNo
```

处理规则：

1. 先按 `data.bizId` 找到本次 QPO 下发批次。
2. 再按 `items[].saleOrderNo + items[].saleOrderItemNo` 匹配本批次中的请求行。
3. 匹配成功后，回写该 QPO 待生产行的 MES 生产状态、MES 计划号、MES 任务 ID 等信息。
4. 如果响应中缺少 `saleOrderNo` 或 `saleOrderItemNo`，该响应行视为无法安全匹配，不应误写到任意 QPO 行。
5. 如果响应中出现重复 `saleOrderNo + saleOrderItemNo`，该批次响应应进入异常处理，避免错写。

## 5. 批次拆分规则

本轮不要求 MES 支持 `lineRequestId`。

为了保证 MES 只返回 `saleOrderNo + saleOrderItemNo` 也能被 QPO 准确识别，QPO 侧需要保证：

```text
同一个 bizId 内，saleOrderNo + saleOrderItemNo 必须唯一
```

如果未来出现同一个销售订单行拆成多条生产任务，例如：

- 同一销售订单行分批投产；
- 同一销售订单行多次补投产；
- 同一销售订单行被拆成多个 allocation；
- 同一销售订单行在同一次操作中产生多条待生产记录；

QPO 不把这些重复行放在同一个 `bizId` 批次内，而是在下发前自动拆成多个批次。

示例：

```text
原始待下发行：
1. SO-001 / 10 / allocationId=1001
2. SO-001 / 10 / allocationId=1002
3. SO-001 / 20 / allocationId=1003

拆分后：
bizId=A:
- SO-001 / 10 / allocationId=1001
- SO-001 / 20 / allocationId=1003

bizId=B:
- SO-001 / 10 / allocationId=1002
```

这样 MES 即使只返回：

```json
{
  "saleOrderNo": "SO-001",
  "saleOrderItemNo": "10"
}
```

QPO 也能在单个 `bizId` 内唯一定位对应行。

## 6. 后端补丁范围

### 6.1 请求 DTO

文件：

```text
quote-backend/src/main/java/com/machining/quote/dto/mes/MesProductionDispatchReq.java
```

修改点：在 `MesProductionDispatchReq.Item` 中新增：

```java
private String orderType;
```

注意：

- 当前 `MesProductionDispatchReq` 根对象上已有兼容用的 `orderType`，但该字段是 `@JsonIgnore`，不会进入 MES 请求体。
- 本次需要把 `orderType` 放到 `items[]` 行内，作为单个零件的属性传给 MES。

### 6.2 请求组装逻辑

文件：

```text
quote-backend/src/main/java/com/machining/quote/service/mes/MesProductionDispatchService.java
```

修改点：在构造需求任务 `Item` 时补充：

```java
item.setOrderType(...);
```

字段来源建议：

```text
order_delivery_ticket_allocation.order_type
```

如果 allocation 行为空，可按现有业务来源降级：

```text
allocation.orderType -> ticket.orderType -> salesOrderItem.orderType -> salesOrderHeader.orderType
```

最终发送给 MES 的值应为业务可读订单类型：

```text
备货订单 / 正式订单 / 预测订单
```

### 6.3 响应 DTO

文件：

```text
quote-backend/src/main/java/com/machining/quote/dto/mes/MesProductionDispatchResp.java
```

修改点：在 `MesProductionDispatchResp.Item` 中新增：

```java
private String saleOrderNo;
private String saleOrderItemNo;
```

可选增强：

```java
@JsonAlias("msg")
private String message;
```

如果 MES 后续支持行级失败原因，可以用 `items[].message` 或 `items[].msg` 承接。

### 6.4 响应匹配逻辑

文件：

```text
quote-backend/src/main/java/com/machining/quote/service/mes/MesProductionDispatchService.java
```

现状：当前主要按 `productCode` 匹配响应行。

补丁后优先级建议：

```text
第一优先级：bizId + saleOrderNo + saleOrderItemNo
第二优先级：productCode，仅作为兼容旧 MES 响应的兜底逻辑
```

处理建议：

1. 如果 MES 响应行包含 `saleOrderNo`、`saleOrderItemNo`，优先使用销售订单行匹配。
2. 如果 MES 响应行不包含这两个字段，则继续使用当前 `productCode` 匹配逻辑，保持旧接口兼容。
3. 如果同一 `bizId` 内发现重复 `saleOrderNo + saleOrderItemNo`，QPO 应在发起前拆批，不应把重复行放在同一批次。
4. 如果仍发生响应重复或无法匹配，不做错误回写，只记录失败摘要和异常原因。

### 6.5 下发前拆批逻辑

文件：

```text
quote-backend/src/main/java/com/machining/quote/service/mes/MesProductionDispatchService.java
```

现有逻辑已经有按重复 `productCode` 拆批的保护。本次补丁建议增加一个同类拆分规则：

```text
同一批次内 saleOrderNo + saleOrderItemNo 不允许重复。
```

拆分时机：在生成 MES 请求批次之前，或在现有 group 拆分逻辑中追加判断。

目标结果：

```text
每个最终发送给 MES 的 bizId 批次内：
1. productCode 不重复，兼容旧 MES 响应；
2. saleOrderNo + saleOrderItemNo 不重复，支持新 MES 响应；
3. 每个 items[] 行都有 orderType。
```

## 7. 兼容策略

本次补丁应保持向后兼容。

| MES 响应情况 | QPO 处理方式 |
| --- | --- |
| 返回 `saleOrderNo + saleOrderItemNo` | 优先使用销售订单行身份匹配。 |
| 未返回 `saleOrderNo + saleOrderItemNo` | 继续使用现有 `productCode` 兜底匹配。 |
| 返回重复销售订单行 | 判定为异常响应，不做不确定回写。 |
| 返回未知销售订单行 | 判定为无法匹配，不写入其他行。 |
| 行级 `status` 失败 | 回写失败状态和失败摘要。 |
| 整体 `code` 非成功 | 整批按失败处理。 |

## 8. 状态回写规则

QPO 匹配到响应行后，按 MES 返回结果回写待生产行：

- MES 成功：回写 `mes_production_status = SUCCESS`。
- MES 处理中或已接收：按现有状态映射规则回写处理中状态。
- MES 失败：回写 `mes_production_status = FAILED`，并记录失败摘要。
- MES 返回 `planNo`：保存 MES 生产计划号。
- MES 返回 `id`：保存 MES 任务 ID 或外部任务标识。

注意：

```text
不能因为响应缺字段或匹配不唯一，就把状态写到第一条、任意一条或按 productCode 猜测的行。
```

## 9. 日志与排查

本次补丁不新增权限点。

需要保留或补充的日志摘要字段：

```text
bizId
saleOrderNo
saleOrderItemNo
productCode
allocationId
planNo
mesTaskId
status
message
durationMs
```

日志注意事项：

- 不记录完整请求体。
- 不记录完整响应体。
- 不记录 token、Authorization、Cookie。
- 异常日志需要包含 `bizId` 和无法匹配的响应行摘要，便于排查。

## 10. 测试建议

### 10.1 请求体测试

验证 QPO 发给 MES 的 `items[]` 包含：

```json
{
  "orderType": "正式订单"
}
```

并确认 `orderType` 不在根节点暴露为业务字段。

### 10.2 新响应匹配测试

MES 返回：

```json
{
  "data": {
    "bizId": "BIZ-001",
    "items": [
      {
        "saleOrderNo": "SO-001",
        "saleOrderItemNo": "10",
        "productCode": "MAT-001",
        "planNo": "PLAN-001",
        "status": "SUCCESS"
      }
    ]
  }
}
```

预期：

```text
QPO 精准回写 BIZ-001 批次内 SO-001 / 10 对应的待生产行。
```

### 10.3 旧响应兼容测试

MES 仍只返回：

```json
{
  "productCode": "MAT-001",
  "planNo": "PLAN-001",
  "status": "SUCCESS"
}
```

预期：

```text
QPO 继续使用现有 productCode 兜底逻辑，不影响当前已联通链路。
```

### 10.4 重复销售订单行拆批测试

输入：

```text
SO-001 / 10 / allocationId=1001
SO-001 / 10 / allocationId=1002
SO-001 / 20 / allocationId=1003
```

预期：

```text
QPO 生成至少两个 bizId 批次；
每个 bizId 内 saleOrderNo + saleOrderItemNo 唯一。
```

### 10.5 异常响应测试

MES 在同一 `bizId` 内返回两条相同：

```text
SO-001 / 10
```

预期：

```text
QPO 不做不确定回写；
记录异常摘要；
相关行保持可排查状态。
```

## 11. 不做事项

本补丁不做以下事项：

- 不要求 MES 本轮支持 `lineRequestId`。
- 不改 MES 物料准备接口。
- 不调整 QPO 原有物料号幂等逻辑。
- 不改变 WMS 锁库分配逻辑。
- 不改变权限体系。
- 不新增用户操作入口。

## 12. 最终结论

本补丁采用最小改造方案：

```text
QPO 请求新增 items[].orderType
MES 响应新增 items[].saleOrderNo + items[].saleOrderItemNo
QPO 按 bizId + saleOrderNo + saleOrderItemNo 回写
QPO 对重复销售订单行做批次拆分
```

该方案可以满足当前 MES 接口改造意愿，同时解决 QPO 需求任务响应无法准确识别行的问题。

后续如果 MES 支持更强的行级唯一标识，例如 `lineRequestId`，QPO 可以再升级为：

```text
bizId + lineRequestId
```

作为更稳定的行级匹配方式。
