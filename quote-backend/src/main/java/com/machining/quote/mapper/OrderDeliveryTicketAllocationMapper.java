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
import org.apache.ibatis.annotations.ResultMap;
import org.apache.ibatis.annotations.Results;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;
import java.time.LocalDateTime;

@Mapper
public interface OrderDeliveryTicketAllocationMapper extends BaseMapper<OrderDeliveryTicketAllocation> {
    @Select("""
            <script>
            SELECT *
            FROM order_delivery_ticket_allocation
            WHERE source_type = 'SALES_ORDER'
              AND source_order_id IN
              <foreach collection="salesOrderIds" item="salesOrderId" open="(" separator="," close=")">
                  #{salesOrderId}
              </foreach>
              AND source_item_id IN
              <foreach collection="salesOrderItemIds" item="salesOrderItemId" open="(" separator="," close=")">
                  #{salesOrderItemId}
              </foreach>
            ORDER BY id ASC
            </script>
            """)
    List<OrderDeliveryTicketAllocation> selectBySalesOrderItemIds(
            @Param("salesOrderIds") List<Long> salesOrderIds,
            @Param("salesOrderItemIds") List<Long> salesOrderItemIds);

    @Select("""
            <script>
            SELECT * FROM order_delivery_ticket_allocation
            WHERE id IN
            <foreach collection="allocationIds" item="allocationId" open="(" separator="," close=")">
                #{allocationId}
            </foreach>
            ORDER BY id ASC
            FOR UPDATE
            </script>
            """)
    List<OrderDeliveryTicketAllocation> selectBatchIdsForUpdate(@Param("allocationIds") List<Integer> allocationIds);

    @Select("""
            SELECT allocation.*
            FROM order_delivery_ticket_allocation allocation
            JOIN sales_order_item sales_item ON sales_item.allocation_id = allocation.id
            WHERE sales_item.sales_order_id = #{salesOrderId}
              AND allocation.allocation_type = 'PRODUCTION_REQUIRED'
              AND sales_item.qty > 0
            ORDER BY sales_item.line_no ASC, allocation.id ASC
            """)
    List<OrderDeliveryTicketAllocation> selectSalesOrderProductionAllocations(@Param("salesOrderId") Long salesOrderId);

    @Select("""
            <script>
            SELECT allocation.*
            FROM order_delivery_ticket_allocation allocation
            WHERE allocation.ticket_id IN
            <foreach collection="ticketIds" item="ticketId" open="(" separator="," close=")">
                #{ticketId}
            </foreach>
              AND allocation.allocation_type = 'PRODUCTION_REQUIRED'
              AND COALESCE(allocation.status, 'ACTIVE') = 'ACTIVE'
              AND COALESCE(allocation.discarded, FALSE) = FALSE
            ORDER BY allocation.id ASC
            </script>
            """)
    List<OrderDeliveryTicketAllocation> selectProductionAllocationsByTicketIds(@Param("ticketIds") List<Integer> ticketIds);

    @Select("""
            SELECT allocation.*
            FROM order_delivery_ticket_allocation allocation
            WHERE allocation.ticket_id = #{ticketId}
              AND allocation.allocation_type = 'PRODUCTION_REQUIRED'
              AND COALESCE(allocation.status, 'ACTIVE') = 'ACTIVE'
              AND COALESCE(allocation.discarded, FALSE) = FALSE
            ORDER BY allocation.id ASC
            """)
    List<OrderDeliveryTicketAllocation> selectActiveProductionAllocationsByTicketId(@Param("ticketId") Integer ticketId);

    @Select("SELECT * FROM order_delivery_ticket_allocation WHERE ticket_id = #{ticketId} ORDER BY id ASC")
    List<OrderDeliveryTicketAllocation> selectByTicketId(@Param("ticketId") Integer ticketId);

    @Select("SELECT * FROM order_delivery_ticket_allocation WHERE split_parent_allocation_id = #{parentAllocationId} ORDER BY id ASC")
    List<OrderDeliveryTicketAllocation> selectBySplitParentAllocationId(@Param("parentAllocationId") Integer parentAllocationId);

    @Select("""
            <script>
            SELECT alloc.*,
                   alloc.id AS allocation_id,
                   sales_item.id AS sales_order_item_id,
                   sales_item.line_no AS sales_order_item_line_no,
                   sales_item.sales_order_date AS sales_order_date,
                   CASE WHEN sales_item.id IS NULL THEN alloc.qty ELSE sales_item.qty END AS linked_qty,
                   CASE WHEN sales_item.id IS NULL THEN alloc.material_no ELSE sales_item.material_no END AS linked_material_no,
                   CASE WHEN sales_item.id IS NULL THEN alloc.order_type ELSE sales_item.order_type END AS linked_order_type,
                   CASE WHEN sales_item.id IS NULL THEN alloc.item_type ELSE sales_item.item_type END AS linked_item_type,
                   CASE WHEN sales_item.id IS NULL THEN alloc.part_no ELSE sales_item.part_no END AS linked_part_no,
                   CASE WHEN sales_item.id IS NULL THEN alloc.version ELSE sales_item.version END AS linked_version,
                   CASE WHEN sales_item.id IS NULL THEN alloc.internal_project_no ELSE sales_item.internal_project_no END AS linked_internal_project_no,
                   CASE WHEN sales_item.id IS NULL THEN alloc.customer_order_no ELSE sales_item.customer_order_no END AS linked_customer_order_no,
                   CASE WHEN sales_item.id IS NULL THEN alloc.customer_project_no ELSE sales_item.customer_project_no END AS linked_customer_project_no,
                   CASE WHEN sales_item.id IS NULL THEN alloc.material_name ELSE sales_item.material_name END AS linked_material_name,
                   CASE WHEN sales_item.id IS NULL THEN alloc.engineering_material_code ELSE sales_item.engineering_material_code END AS linked_engineering_material_code,
                   CASE WHEN sales_item.id IS NULL THEN alloc.engineering_material_name ELSE sales_item.engineering_material_name END AS linked_engineering_material_name,
                   CASE WHEN sales_item.id IS NULL THEN alloc.engineering_material_type_code ELSE sales_item.engineering_material_type_code END AS linked_engineering_material_type_code,
                   CASE WHEN sales_item.id IS NULL THEN alloc.engineering_material_type_name ELSE sales_item.engineering_material_type_name END AS linked_engineering_material_type_name,
                   CASE WHEN sales_item.id IS NULL THEN alloc.engineering_material_category ELSE sales_item.engineering_material_category END AS linked_engineering_material_category,
                   CASE WHEN sales_item.id IS NULL THEN alloc.material_shape ELSE sales_item.material_shape END AS linked_material_shape,
                   CASE WHEN sales_item.id IS NULL THEN alloc.surface_treatment ELSE sales_item.surface_treatment END AS linked_surface_treatment,
                   CASE WHEN sales_item.id IS NULL THEN alloc.remark ELSE sales_item.remark END AS linked_remark,
                   CASE WHEN sales_item.id IS NULL THEN alloc.description ELSE sales_item.description END AS linked_description,
                   CASE WHEN sales_item.id IS NULL THEN alloc.delivery_date ELSE COALESCE(sales_item.internal_delivery_date, sales_item.sales_order_date) END AS linked_delivery_date,
                   CASE WHEN sales_item.id IS NULL THEN alloc.process_route ELSE sales_item.process_route END AS linked_process_route,
                   CASE WHEN sales_item.id IS NULL THEN alloc.material_data ELSE sales_item.material_data END AS linked_material_data,
                   CASE WHEN sales_item.id IS NULL THEN alloc.material_gross_weight ELSE sales_item.material_gross_weight END AS linked_material_gross_weight,
                   CASE WHEN sales_item.id IS NULL THEN alloc.part_net_weight ELSE sales_item.part_net_weight END AS linked_part_net_weight,
                   COALESCE(sales_header.customer_name, alloc.customer_name) AS linked_customer_name
            FROM order_delivery_ticket_allocation alloc
            LEFT JOIN LATERAL (
                SELECT item.*
                FROM sales_order_item item
                WHERE item.allocation_id = alloc.id
                   OR (item.allocation_id IS NULL AND item.source_ticket_id = alloc.ticket_id)
                   OR (item.allocation_id IS NULL
                       AND alloc.source_type = 'SALES_ORDER'
                       AND item.id = alloc.source_item_id)
                ORDER BY CASE
                    WHEN item.allocation_id = alloc.id THEN 1
                    WHEN alloc.source_type = 'SALES_ORDER' AND item.id = alloc.source_item_id THEN 2
                    ELSE 3
                END, item.line_no ASC, item.id ASC
                LIMIT 1
            ) sales_item ON TRUE
            LEFT JOIN sales_order_header sales_header ON sales_header.id = sales_item.sales_order_id
            WHERE (sales_item.sales_order_id = #{salesOrderId}
                   OR (sales_item.id IS NULL
                       AND alloc.source_type = 'SALES_ORDER'
                       AND alloc.source_order_id = #{salesOrderId}))
              AND alloc.allocation_type IN ('IN_STOCK_LOCK', 'IN_PRODUCTION_LOCK', 'PRODUCTION_REQUIRED')
              AND COALESCE(alloc.status, 'ACTIVE') = 'ACTIVE'
              AND COALESCE(alloc.discarded, FALSE) = FALSE
            <if test="partNo != null and partNo != ''">
              AND LOWER(COALESCE(sales_item.part_no, alloc.part_no, '')) LIKE CONCAT('%', LOWER(#{partNo}), '%')
            </if>
            <if test="version != null and version != ''">
              AND LOWER(COALESCE(sales_item.version, alloc.version, '')) LIKE CONCAT('%', LOWER(#{version}), '%')
            </if>
            ORDER BY alloc.ticket_id ASC, alloc.id ASC
            LIMIT #{limit} OFFSET #{offset}
            </script>
            """)
    @Results(id = "salesOrderAllocationSplitResultMap", value = {
            @Result(column = "id", property = "id", id = true),
            @Result(column = "allocation_id", property = "allocationId"),
            @Result(column = "sales_order_item_id", property = "salesOrderItemId"),
            @Result(column = "sales_order_item_line_no", property = "salesOrderItemLineNo"),
            @Result(column = "sales_order_date", property = "salesOrderDate"),
            @Result(column = "linked_qty", property = "qty"),
            @Result(column = "linked_material_no", property = "materialNo"),
            @Result(column = "linked_order_type", property = "orderType"),
            @Result(column = "linked_item_type", property = "itemType"),
            @Result(column = "linked_part_no", property = "partNo"),
            @Result(column = "linked_version", property = "version"),
            @Result(column = "linked_internal_project_no", property = "internalProjectNo"),
            @Result(column = "linked_customer_order_no", property = "customerOrderNo"),
            @Result(column = "linked_customer_project_no", property = "customerProjectNo"),
            @Result(column = "linked_material_name", property = "materialName"),
            @Result(column = "linked_engineering_material_code", property = "engineeringMaterialCode"),
            @Result(column = "linked_engineering_material_name", property = "engineeringMaterialName"),
            @Result(column = "linked_engineering_material_type_code", property = "engineeringMaterialTypeCode"),
            @Result(column = "linked_engineering_material_type_name", property = "engineeringMaterialTypeName"),
            @Result(column = "linked_engineering_material_category", property = "engineeringMaterialCategory"),
            @Result(column = "linked_material_shape", property = "materialShape"),
            @Result(column = "linked_surface_treatment", property = "surfaceTreatment"),
            @Result(column = "linked_remark", property = "remark"),
            @Result(column = "linked_description", property = "description"),
            @Result(column = "linked_delivery_date", property = "deliveryDate"),
            @Result(column = "linked_process_route", property = "processRoute", typeHandler = JsonbTypeHandler.class),
            @Result(column = "linked_material_data", property = "materialData", typeHandler = JsonbTypeHandler.class),
            @Result(column = "linked_material_gross_weight", property = "materialGrossWeight"),
            @Result(column = "linked_part_net_weight", property = "partNetWeight"),
            @Result(column = "linked_customer_name", property = "customerName"),
            @Result(column = "source_payload", property = "sourcePayload", typeHandler = JsonbTypeHandler.class)
    })
    List<LockedItemDTO> selectSalesOrderAllocationSplits(
            @Param("salesOrderId") Long salesOrderId,
            @Param("limit") int limit,
            @Param("offset") long offset,
            @Param("partNo") String partNo,
            @Param("version") String version);

    @Select("""
            <script>
            SELECT COUNT(*)
            FROM order_delivery_ticket_allocation alloc
            LEFT JOIN LATERAL (
                SELECT item.*
                FROM sales_order_item item
                WHERE item.allocation_id = alloc.id
                   OR (item.allocation_id IS NULL AND item.source_ticket_id = alloc.ticket_id)
                   OR (item.allocation_id IS NULL
                       AND alloc.source_type = 'SALES_ORDER'
                       AND item.id = alloc.source_item_id)
                ORDER BY CASE
                    WHEN item.allocation_id = alloc.id THEN 1
                    WHEN alloc.source_type = 'SALES_ORDER' AND item.id = alloc.source_item_id THEN 2
                    ELSE 3
                END, item.line_no ASC, item.id ASC
                LIMIT 1
            ) sales_item ON TRUE
            LEFT JOIN sales_order_header sales_header ON sales_header.id = sales_item.sales_order_id
            WHERE (sales_item.sales_order_id = #{salesOrderId}
                   OR (sales_item.id IS NULL
                       AND alloc.source_type = 'SALES_ORDER'
                       AND alloc.source_order_id = #{salesOrderId}))
              AND alloc.allocation_type IN ('IN_STOCK_LOCK', 'IN_PRODUCTION_LOCK', 'PRODUCTION_REQUIRED')
              AND COALESCE(alloc.status, 'ACTIVE') = 'ACTIVE'
              AND COALESCE(alloc.discarded, FALSE) = FALSE
            <if test="partNo != null and partNo != ''">
              AND LOWER(COALESCE(sales_item.part_no, alloc.part_no, '')) LIKE CONCAT('%', LOWER(#{partNo}), '%')
            </if>
            <if test="version != null and version != ''">
              AND LOWER(COALESCE(sales_item.version, alloc.version, '')) LIKE CONCAT('%', LOWER(#{version}), '%')
            </if>
            </script>
            """)
    long countSalesOrderAllocationSplits(
            @Param("salesOrderId") Long salesOrderId,
            @Param("partNo") String partNo,
            @Param("version") String version);

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
              AND NOT EXISTS (
                  SELECT 1
                  FROM wms_reservation_ticket reservation
                  WHERE reservation.allocation_id = order_delivery_ticket_allocation.id
                    AND reservation.status IN ('RESERVED', 'SUBMITTING', 'RESULT_UNKNOWN', 'RESPONSE_RECEIVED', 'APPLY_FAILED')
              )
            """)
    int markMesProductionLineReserved(@Param("allocationId") Integer allocationId,
                                      @Param("lineRequestId") String lineRequestId);

    @Select("""
            <script>
            SELECT
                alloc.id,
                alloc.id AS allocation_id,
                sales_item.id AS sales_order_item_id,
                sales_item.line_no AS sales_order_item_line_no,
                sales_item.sales_order_date AS sales_order_date,
                alloc.ticket_id,
                alloc.rfq_id,
                alloc.rfq_no,
                CASE WHEN sales_item.id IS NULL THEN COALESCE(alloc.customer_name, ticket.customer_name, rfq.customer_name)
                     ELSE COALESCE(sales_header.customer_name, alloc.customer_name, ticket.customer_name, rfq.customer_name) END AS customer_name,
                alloc.item_id,
                alloc.source_type,
                alloc.source_order_id,
                alloc.source_item_id,
                alloc.allocation_source,
                alloc.allocation_split_source,
                alloc.split_parent_allocation_id,
                   (SELECT reservation.status
                      FROM wms_reservation_ticket reservation
                     WHERE reservation.allocation_id = alloc.id
                     ORDER BY reservation.updated_at DESC NULLS LAST, reservation.id DESC
                     LIMIT 1) AS wms_reservation_status,
                alloc.allocation_type,
                alloc.wms_reservation_ticket_id,
                CASE WHEN sales_item.id IS NULL THEN alloc.qty ELSE sales_item.qty END AS qty,
                alloc.ticket_qty,
                CASE WHEN sales_item.id IS NULL THEN alloc.material_no ELSE sales_item.material_no END AS material_no,
                CASE WHEN sales_item.id IS NULL THEN alloc.order_type ELSE sales_item.order_type END AS order_type,
                CASE WHEN sales_item.id IS NULL THEN alloc.item_type ELSE sales_item.item_type END AS item_type,
                CASE WHEN sales_item.id IS NULL THEN alloc.part_no ELSE sales_item.part_no END AS part_no,
                CASE WHEN sales_item.id IS NULL THEN alloc.version ELSE sales_item.version END AS version,
                CASE WHEN sales_item.id IS NULL THEN alloc.internal_project_no ELSE sales_item.internal_project_no END AS internal_project_no,
                CASE WHEN sales_item.id IS NULL THEN alloc.customer_order_no ELSE sales_item.customer_order_no END AS customer_order_no,
                CASE WHEN sales_item.id IS NULL THEN alloc.customer_project_no ELSE sales_item.customer_project_no END AS customer_project_no,
                CASE WHEN sales_item.id IS NULL THEN alloc.material_name ELSE sales_item.material_name END AS material_name,
                CASE WHEN sales_item.id IS NULL THEN alloc.engineering_material_code ELSE sales_item.engineering_material_code END AS engineering_material_code,
                CASE WHEN sales_item.id IS NULL THEN alloc.engineering_material_name ELSE sales_item.engineering_material_name END AS engineering_material_name,
                CASE WHEN sales_item.id IS NULL THEN alloc.engineering_material_type_code ELSE sales_item.engineering_material_type_code END AS engineering_material_type_code,
                CASE WHEN sales_item.id IS NULL THEN alloc.engineering_material_type_name ELSE sales_item.engineering_material_type_name END AS engineering_material_type_name,
                CASE WHEN sales_item.id IS NULL THEN alloc.engineering_material_category ELSE sales_item.engineering_material_category END AS engineering_material_category,
                CASE WHEN sales_item.id IS NULL THEN alloc.material_shape ELSE sales_item.material_shape END AS material_shape,
                CASE WHEN sales_item.id IS NULL THEN alloc.surface_treatment ELSE sales_item.surface_treatment END AS surface_treatment,
                CASE WHEN sales_item.id IS NULL THEN alloc.remark ELSE sales_item.remark END AS remark,
                CASE WHEN sales_item.id IS NULL THEN alloc.description ELSE sales_item.description END AS description,
                CASE WHEN sales_item.id IS NULL THEN alloc.delivery_date ELSE COALESCE(sales_item.internal_delivery_date, sales_item.sales_order_date) END AS delivery_date,
                CASE WHEN sales_item.id IS NULL THEN alloc.process_route ELSE sales_item.process_route END AS process_route,
                CASE WHEN sales_item.id IS NULL THEN alloc.material_data ELSE sales_item.material_data END AS material_data,
                CASE WHEN sales_item.id IS NULL THEN alloc.material_gross_weight ELSE sales_item.material_gross_weight END AS material_gross_weight,
                CASE WHEN sales_item.id IS NULL THEN alloc.part_net_weight ELSE sales_item.part_net_weight END AS part_net_weight,
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
            LEFT JOIN LATERAL (
                SELECT item.*
                FROM sales_order_item item
                WHERE item.allocation_id = alloc.id
                   OR (item.allocation_id IS NULL AND item.source_ticket_id = alloc.ticket_id)
                   OR (item.allocation_id IS NULL
                       AND alloc.source_type = 'SALES_ORDER'
                       AND item.id = alloc.source_item_id)
                ORDER BY CASE
                    WHEN item.allocation_id = alloc.id THEN 1
                    WHEN alloc.source_type = 'SALES_ORDER' AND item.id = alloc.source_item_id THEN 2
                    ELSE 3
                END, item.line_no ASC, item.id ASC
                LIMIT 1
            ) sales_item ON TRUE
            LEFT JOIN sales_order_header sales_header ON sales_header.id = sales_item.sales_order_id
            ${ew.customSqlSegment}
            ORDER BY alloc.id DESC
            LIMIT #{limit} OFFSET #{offset}
            </script>
            """)
    @Results(id = "lockedItemResultMap", value = {
            @Result(column = "id", property = "id", id = true),
            @Result(column = "allocation_id", property = "allocationId"),
            @Result(column = "sales_order_item_id", property = "salesOrderItemId"),
            @Result(column = "sales_order_item_line_no", property = "salesOrderItemLineNo"),
            @Result(column = "sales_order_date", property = "salesOrderDate"),
            @Result(column = "allocation_source", property = "allocationSource"),
            @Result(column = "allocation_split_source", property = "allocationSplitSource"),
            @Result(column = "split_parent_allocation_id", property = "splitParentAllocationId"),
            @Result(column = "wms_reservation_status", property = "wmsReservationStatus"),
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
            LEFT JOIN LATERAL (
                SELECT item.*
                FROM sales_order_item item
                WHERE item.allocation_id = alloc.id
                   OR (item.allocation_id IS NULL AND item.source_ticket_id = alloc.ticket_id)
                   OR (item.allocation_id IS NULL
                       AND alloc.source_type = 'SALES_ORDER'
                       AND item.id = alloc.source_item_id)
                ORDER BY CASE
                    WHEN item.allocation_id = alloc.id THEN 1
                    WHEN alloc.source_type = 'SALES_ORDER' AND item.id = alloc.source_item_id THEN 2
                    ELSE 3
                END, item.line_no ASC, item.id ASC
                LIMIT 1
            ) sales_item ON TRUE
            LEFT JOIN sales_order_header sales_header ON sales_header.id = sales_item.sales_order_id
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
            SELECT DISTINCT COALESCE(sales_header.customer_name, alloc.customer_name, ticket.customer_name, rfq.customer_name) AS customer_name
            FROM order_delivery_ticket_allocation alloc
            LEFT JOIN order_rfq rfq ON rfq.id = alloc.rfq_id
            LEFT JOIN order_delivery_ticket ticket ON ticket.id = alloc.ticket_id
            LEFT JOIN LATERAL (
                SELECT item.*
                FROM sales_order_item item
                WHERE item.allocation_id = alloc.id
                   OR (item.allocation_id IS NULL AND item.source_ticket_id = alloc.ticket_id)
                   OR (item.allocation_id IS NULL
                       AND alloc.source_type = 'SALES_ORDER'
                       AND item.id = alloc.source_item_id)
                ORDER BY CASE
                    WHEN item.allocation_id = alloc.id THEN 1
                    WHEN alloc.source_type = 'SALES_ORDER' AND item.id = alloc.source_item_id THEN 2
                    ELSE 3
                END, item.line_no ASC, item.id ASC
                LIMIT 1
            ) sales_item ON TRUE
            LEFT JOIN sales_order_header sales_header ON sales_header.id = sales_item.sales_order_id
            ${ew.customSqlSegment}
            ORDER BY customer_name ASC
            </script>
            """)
    List<String> selectLockedItemCustomers(@Param(Constants.WRAPPER) Wrapper<LockedItemDTO> query);
}
