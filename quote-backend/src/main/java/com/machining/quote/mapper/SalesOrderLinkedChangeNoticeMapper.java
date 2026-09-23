package com.machining.quote.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.machining.quote.entity.SalesOrderLinkedChangeNotice;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface SalesOrderLinkedChangeNoticeMapper extends BaseMapper<SalesOrderLinkedChangeNotice> {
    @Insert("""
            <script>
            INSERT INTO sales_order_linked_change_notice (
                sales_order_id, sales_order_item_id, sales_order_no, line_no,
                customer_id, customer_code, customer_name, customer_order_no, internal_project_no,
                part_no, version, description, material_name,
                trigger_scope, linked_discard_event_key, linked_discarded_at, linked_discarded_by, linked_discard_reason,
                wms_submitted, wms_status, wms_request_id, wms_action_at,
                mes_production_submitted, mes_production_status, mes_production_request_id, mes_production_action_at,
                stock_picking_submitted, stock_picking_status, stock_picking_biz_id, stock_picking_action_at,
                downstream_impact_count, handling_status, handled_at, handled_by, handle_remark, created_at, updated_at
            ) VALUES
            <foreach collection="rows" item="row" separator=",">
                (
                    #{row.salesOrderId}, #{row.salesOrderItemId}, #{row.salesOrderNo}, #{row.lineNo},
                    #{row.customerId}, #{row.customerCode}, #{row.customerName}, #{row.customerOrderNo}, #{row.internalProjectNo},
                    #{row.partNo}, #{row.version}, #{row.description}, #{row.materialName},
                    #{row.triggerScope}, #{row.linkedDiscardEventKey}, #{row.linkedDiscardedAt}, #{row.linkedDiscardedBy}, #{row.linkedDiscardReason},
                    #{row.wmsSubmitted}, #{row.wmsStatus}, #{row.wmsRequestId}, #{row.wmsActionAt},
                    #{row.mesProductionSubmitted}, #{row.mesProductionStatus}, #{row.mesProductionRequestId}, #{row.mesProductionActionAt},
                    #{row.stockPickingSubmitted}, #{row.stockPickingStatus}, #{row.stockPickingBizId}, #{row.stockPickingActionAt},
                    #{row.downstreamImpactCount}, #{row.handlingStatus}, #{row.handledAt}, #{row.handledBy}, #{row.handleRemark}, #{row.createdAt}, #{row.updatedAt}
                )
            </foreach>
            ON CONFLICT (linked_discard_event_key, sales_order_item_id) DO NOTHING
            </script>
            """)
    int insertBatch(@Param("rows") List<SalesOrderLinkedChangeNotice> rows);
}
