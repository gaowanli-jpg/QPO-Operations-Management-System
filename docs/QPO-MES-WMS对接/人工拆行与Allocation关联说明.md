# 销售订单人工拆行与 Allocation 关联说明

销售订单详情的“锁库拆行”页面支持两种互斥的初次拆分来源：WMS 返回库存结果，或操作员根据人工核查结果输入锁已入库数量和锁在产数量。两者都以有效、数量大于零、物料号已确认且尚未开始 MES 生产的 `PRODUCTION_REQUIRED` Allocation 为操作对象。

人工拆行接口为：

```text
POST /api/market/sales-order-management/orders/{salesOrderId}/allocation-splits/manual
```

```json
{
  "allocationId": 123,
  "inStockQty": 3,
  "inProductionQty": 2
}
```

锁已入库数量与锁在产数量必须是非负整数，合计不能超过当前待产数量。原待产 Allocation 保留为剩余待产行；正数量的锁库结果追加 Allocation，并同步追加销售订单零件行。数量为 `0 + 0` 时不生成锁库行，待产数量保持不变，但记录人工拆分来源，后续不能再次进行初次 WMS 或人工拆分。

WMS 处理中、结果未知、响应待应用或本地应用失败时禁止人工接管；WMS 明确失败且没有形成初次拆分结果时允许人工接管。人工拆分不写入 `wms_reservation_ticket`，也不改变 `order_delivery_ticket` 和历史 WMS 请求快照。后续的 Allocation 数量维护仍按既有权限和审计规则执行。

审计动作 `SALES_ORDER_MANUAL_ALLOCATION_SPLIT` 记录订单、源 Allocation、两类库存数量、剩余待产数量、生成的 Allocation 和销售订单零件行 ID，以及耗时；不记录外部请求、响应或凭据。
