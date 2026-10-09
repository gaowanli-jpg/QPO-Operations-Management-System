package com.machining.quote.mapper;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.toolkit.Constants;
import com.machining.quote.config.JsonbTypeHandler;
import com.machining.quote.dto.LockedItemDTO;
import com.machining.quote.dto.SalesOrderLinkedDiscardRelation;
import com.machining.quote.entity.OrderDeliveryTicketAllocation;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Result;
import org.apache.ibatis.annotations.Results;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;
import java.time.LocalDateTime;

@Mapper
public interface OrderDeliveryTicketAllocationMapper extends BaseMapper<OrderDeliveryTicketAllocation> {
    @Select("SELECT * FROM order_delivery_ticket_allocation WHERE ticket_id = #{ticketId} ORDER BY id ASC")
    List<OrderDeliveryTicketAllocation> selectByTicketId(@Param("ticketId") Integer ticketId);

    @Select("SELECT * FROM order_delivery_ticket_allocation WHERE id = #{id} FOR UPDATE")
    OrderDeliveryTicketAllocation selectByIdForUpdate(@Param("id") Integer id);

    @Select("SELECT * FROM order_delivery_ticket_allocation WHERE ticket_id = #{ticketId} ORDER BY id ASC FOR UPDATE")
    List<OrderDeliveryTicketAllocation> selectByTicketIdForUpdate(@Param("ticketId") Integer ticketId);

    @Update("""
            <script>
            UPDATE order_delivery_ticket_allocation AS allocation
            SET discarded = TRUE,
                discarded_at = #{updatedAt},
                discarded_by = #{operatorId},
                discard_reason = #{reason},
                discard_source = #{discardSource},
                discard_source_sales_order_item_id = target.source_item_id,
                updated_at = #{updatedAt}
            FROM (VALUES
                <foreach collection="relations" item="relation" separator=",">
                    (#{relation.recordId}, #{relation.salesOrderItemId})
                </foreach>
            ) AS target(id, source_item_id)
            WHERE allocation.id = target.id
              AND COALESCE(allocation.discarded, FALSE) = FALSE
            </script>
            """)
    int markLinkedDiscarded(@Param("relations") List<SalesOrderLinkedDiscardRelation> relations,
                            @Param("discardSource") String discardSource,
                            @Param("reason") String reason,
                            @Param("operatorId") Long operatorId,
                            @Param("updatedAt") LocalDateTime updatedAt);

    @Update("""
            <script>
            UPDATE order_delivery_ticket_allocation AS allocation
            SET discarded = FALSE,
                restored_at = #{updatedAt},
                restored_by = #{operatorId},
                restore_reason = #{reason},
                discard_source = NULL,
                discard_source_sales_order_item_id = NULL,
                updated_at = #{updatedAt}
            FROM (VALUES
                <foreach collection="relations" item="relation" separator=",">
                    (#{relation.recordId}, #{relation.salesOrderItemId})
                </foreach>
            ) AS target(id, source_item_id)
            WHERE allocation.id = target.id
              AND COALESCE(allocation.discarded, FALSE) = TRUE
              AND allocation.discard_source = #{discardSource}
              AND allocation.discard_source_sales_order_item_id = target.source_item_id
            </script>
            """)
    int restoreLinkedDiscarded(@Param("relations") List<SalesOrderLinkedDiscardRelation> relations,
                               @Param("discardSource") String discardSource,
                               @Param("reason") String reason,
                               @Param("operatorId") Long operatorId,
                               @Param("updatedAt") LocalDateTime updatedAt);

    @Update("""
            UPDATE order_delivery_ticket_allocation
            SET mes_production_request_id = #{lineRequestId},
                mes_production_status = 'PROCESSING',
                mes_production_message = NULL,
                updated_at = CURRENT_TIMESTAMP
            WHERE id = #{allocationId}
              AND allocation_type = 'PRODUCTION_REQUIRED'
              AND COALESCE(status, 'ACTIVE') = 'ACTIVE'
              AND COALESCE(mes_production_status, 'PENDING') IN ('PENDING', 'FAILED')
            """)
    int markMesProductionLineReserved(@Param("allocationId") Integer allocationId,
                                      @Param("lineRequestId") String lineRequestId);

    @Select("""
            <script>
            SELECT
                alloc.id,
                alloc.ticket_id,
                alloc.rfq_id,
                alloc.rfq_no,
                alloc.customer_name,
                alloc.item_id,
                alloc.source_type,
                alloc.source_order_id,
                alloc.source_item_id,
                alloc.allocation_type,
                alloc.wms_reservation_ticket_id,
                alloc.qty,
                alloc.ticket_qty,
                alloc.material_no,
                alloc.order_type AS order_type,
                alloc.item_type,
                alloc.part_no,
                alloc.version,
                alloc.internal_project_no,
                alloc.customer_order_no,
                alloc.customer_project_no,
                alloc.material_name,
                alloc.engineering_material_code,
                alloc.engineering_material_name,
                alloc.engineering_material_type_code,
                alloc.engineering_material_type_name,
                alloc.engineering_material_category,
                alloc.material_shape,
                alloc.surface_treatment,
                alloc.remark,
                alloc.description,
                alloc.delivery_date,
                alloc.process_route,
                alloc.material_data,
                alloc.material_gross_weight,
                alloc.part_net_weight,
                alloc.source_payload,
                alloc.mes_production_status,
                alloc.mes_production_request_id,
                alloc.mes_production_external_id,
                alloc.mes_production_synced_at,
                alloc.mes_production_message,
                alloc.stock_picking_status,
                alloc.stock_picking_biz_id,
                alloc.stock_picking_external_id,
                alloc.stock_picking_synced_at,
                alloc.stock_picking_message,
                alloc.status,
                alloc.created_at,
                alloc.updated_at,
                alloc.discarded,
                alloc.discarded_at,
                alloc.discarded_by,
                alloc.discard_reason,
                alloc.restored_at,
                alloc.restored_by,
                alloc.restore_reason
            FROM order_delivery_ticket_allocation alloc
            LEFT JOIN order_rfq rfq ON rfq.id = alloc.rfq_id
            LEFT JOIN order_delivery_ticket ticket ON ticket.id = alloc.ticket_id
            ${ew.customSqlSegment}
            ORDER BY alloc.id DESC
            LIMIT #{limit} OFFSET #{offset}
            </script>
            """)
    @Results(id = "lockedItemResultMap", value = {
            @Result(column = "id", property = "id", id = true),
            @Result(column = "engineering_material_code", property = "engineeringMaterialCode"),
            @Result(column = "engineering_material_type_code", property = "engineeringMaterialTypeCode"),
            @Result(column = "engineering_material_type_name", property = "engineeringMaterialTypeName"),
            @Result(column = "engineering_material_category", property = "engineeringMaterialCategory"),
            @Result(column = "process_route", property = "processRoute", typeHandler = JsonbTypeHandler.class),
            @Result(column = "material_data", property = "materialData", typeHandler = JsonbTypeHandler.class),
            @Result(column = "source_payload", property = "sourcePayload", typeHandler = JsonbTypeHandler.class)
    })
    List<LockedItemDTO> selectLockedItems(
            @Param(Constants.WRAPPER) Wrapper<LockedItemDTO> query,
            @Param("limit") int limit,
            @Param("offset") long offset);

    @Select("""
            <script>
            SELECT COUNT(*)
            FROM order_delivery_ticket_allocation alloc
            LEFT JOIN order_rfq rfq ON rfq.id = alloc.rfq_id
            LEFT JOIN order_delivery_ticket ticket ON ticket.id = alloc.ticket_id
            ${ew.customSqlSegment}
            </script>
            """)
    long countLockedItems(@Param(Constants.WRAPPER) Wrapper<LockedItemDTO> query);

    @Select("""
            SELECT * FROM order_delivery_ticket_allocation
            WHERE wms_reservation_ticket_id = #{reservationTicketId}
            ORDER BY id ASC
            """)
    List<OrderDeliveryTicketAllocation> selectByWmsReservationTicketId(@Param("reservationTicketId") Long reservationTicketId);

    @Select("""
            <script>
            SELECT DISTINCT COALESCE(alloc.customer_name, ticket.customer_name, rfq.customer_name) AS customer_name
            FROM order_delivery_ticket_allocation alloc
            LEFT JOIN order_rfq rfq ON rfq.id = alloc.rfq_id
            LEFT JOIN order_delivery_ticket ticket ON ticket.id = alloc.ticket_id
            ${ew.customSqlSegment}
            ORDER BY customer_name ASC
            </script>
            """)
    List<String> selectLockedItemCustomers(@Param(Constants.WRAPPER) Wrapper<LockedItemDTO> query);
}
