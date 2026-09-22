package com.machining.quote.mapper;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.toolkit.Constants;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.machining.quote.config.JsonbTypeHandler;
import com.machining.quote.dto.DeliveredItemDTO;
import com.machining.quote.dto.SalesOrderLinkedDiscardRelation;
import com.machining.quote.entity.OrderDeliveryTicket;
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
public interface OrderDeliveryTicketMapper extends BaseMapper<OrderDeliveryTicket> {
    @Select("SELECT * FROM order_delivery_ticket WHERE id = #{id} FOR UPDATE")
    OrderDeliveryTicket selectByIdForUpdate(@Param("id") Integer id);

    @Update("""
            <script>
            UPDATE order_delivery_ticket AS ticket
            SET discarded = TRUE,
                discarded_at = #{updatedAt},
                discarded_by = #{operatorId},
                discard_reason = #{reason},
                discard_source = #{discardSource},
                discard_source_sales_order_item_id = target.source_item_id
            FROM (VALUES
                <foreach collection="relations" item="relation" separator=",">
                    (#{relation.recordId}, #{relation.salesOrderItemId})
                </foreach>
            ) AS target(id, source_item_id)
            WHERE ticket.id = target.id
              AND COALESCE(ticket.discarded, FALSE) = FALSE
            </script>
            """)
    int markLinkedDiscarded(@Param("relations") List<SalesOrderLinkedDiscardRelation> relations,
                            @Param("discardSource") String discardSource,
                            @Param("reason") String reason,
                            @Param("operatorId") Long operatorId,
                            @Param("updatedAt") LocalDateTime updatedAt);

    @Update("""
            <script>
            UPDATE order_delivery_ticket AS ticket
            SET discarded = FALSE,
                restored_at = #{updatedAt},
                restored_by = #{operatorId},
                restore_reason = #{reason},
                discard_source = NULL,
                discard_source_sales_order_item_id = NULL
            FROM (VALUES
                <foreach collection="relations" item="relation" separator=",">
                    (#{relation.recordId}, #{relation.salesOrderItemId})
                </foreach>
            ) AS target(id, source_item_id)
            WHERE ticket.id = target.id
              AND COALESCE(ticket.discarded, FALSE) = TRUE
              AND ticket.discard_source = #{discardSource}
              AND ticket.discard_source_sales_order_item_id = target.source_item_id
            </script>
            """)
    int restoreLinkedDiscarded(@Param("relations") List<SalesOrderLinkedDiscardRelation> relations,
                               @Param("discardSource") String discardSource,
                               @Param("reason") String reason,
                               @Param("operatorId") Long operatorId,
                               @Param("updatedAt") LocalDateTime updatedAt);

    @Update("""
            UPDATE order_delivery_ticket
            SET mes_request_id = #{lineRequestId},
                mes_sync_status = 'PROCESSING',
                mes_sync_message = NULL
            WHERE id = #{ticketId}
            """)
    int markMesMaterialLineReserved(@Param("ticketId") Integer ticketId,
                                    @Param("lineRequestId") String lineRequestId);

    @Select("""
            <script>
            SELECT
                ticket.id,
                ticket.rfq_id,
                COALESCE(ticket.rfq_no, rfq.rfq_no) AS rfq_no,
                COALESCE(ticket.customer_name, rfq.customer_name) AS customer_name,
                ticket.item_id,
                ticket.source_type,
                ticket.source_order_id,
                ticket.source_item_id,
                COALESCE(direct_sales_order.id, rfq_sales_order.id) AS sales_order_id,
                COALESCE(direct_sales_order.sales_order_no, rfq_sales_order.sales_order_no) AS sales_order_no,
                COALESCE(direct_sales_order.customer_code, rfq_sales_order.customer_code) AS sales_order_customer_code,
                COALESCE(direct_sales_order.customer_name, rfq_sales_order.customer_name) AS sales_order_customer_name,
                ticket.part_no,
                ticket.version,
                ticket.description,
                ticket.qty,
                ticket.order_type AS order_type,
                ticket.remark,
                ticket.internal_project_no,
                ticket.customer_order_no,
                ticket.customer_project_no,
                ticket.project_no,
                ticket.item_type,
                COALESCE(ticket.material_name, item.material_name, direct_sales_item.material_name, rfq_sales_item.material_name) AS material_name,
                COALESCE(ticket.engineering_material_code, item.engineering_material_code, direct_sales_item.engineering_material_code, rfq_sales_item.engineering_material_code) AS engineering_material_code,
                COALESCE(ticket.engineering_material_name, item.engineering_material_name, direct_sales_item.engineering_material_name, rfq_sales_item.engineering_material_name) AS engineering_material_name,
                COALESCE(ticket.engineering_material_type_code, item.engineering_material_type_code, direct_sales_item.engineering_material_type_code, rfq_sales_item.engineering_material_type_code) AS engineering_material_type_code,
                COALESCE(ticket.engineering_material_type_name, item.engineering_material_type_name, direct_sales_item.engineering_material_type_name, rfq_sales_item.engineering_material_type_name) AS engineering_material_type_name,
                COALESCE(ticket.engineering_material_category, item.engineering_material_category, direct_sales_item.engineering_material_category, rfq_sales_item.engineering_material_category) AS engineering_material_category,
                COALESCE(ticket.material_shape, item.material_shape, direct_sales_item.material_shape, rfq_sales_item.material_shape) AS material_shape,
                COALESCE(ticket.surface_treatment, item.surface_treatment, direct_sales_item.surface_treatment, rfq_sales_item.surface_treatment) AS surface_treatment,
                COALESCE(ticket.drawing_url, direct_sales_item.drawing_url, rfq_sales_item.drawing_url, item.drawing_url) AS drawing_url,
                ticket.process_route,
                ticket.material_data,
                ticket.material_gross_weight,
                ticket.part_net_weight,
                ticket.material_no,
                ticket.mes_sync_status,
                ticket.mes_sync_message,
                ticket.mes_synced_at,
                ticket.mes_request_id,
                ticket.mes_external_task_id,
                ticket.mes_callback_received_at,
                ticket.mes_callback_message,
                ticket.mes_dedup_key,
                ticket.mes_request_role,
                ticket.mes_master_ticket_id,
                ticket.mes_master_request_id,
                ticket.wms_allocate_status,
                ticket.wms_allocate_message,
                ticket.wms_allocated_at,
                ticket.discarded,
                ticket.discarded_at,
                ticket.discarded_by,
                ticket.discard_reason,
                ticket.restored_at,
                ticket.restored_by,
                ticket.restore_reason,
                ticket.delivery_date
            FROM order_delivery_ticket ticket
            LEFT JOIN order_rfq rfq ON rfq.id = ticket.rfq_id
            LEFT JOIN item_quote_version item ON item.id = ticket.item_id
            LEFT JOIN sales_order_item direct_sales_item
                ON ticket.source_type = 'SALES_ORDER' AND direct_sales_item.id = ticket.source_item_id
            LEFT JOIN sales_order_header direct_sales_order
                ON ticket.source_type = 'SALES_ORDER' AND direct_sales_order.id = ticket.source_order_id
            LEFT JOIN sales_order_item rfq_sales_item ON rfq_sales_item.source_ticket_id = ticket.id
            LEFT JOIN sales_order_header rfq_sales_order ON rfq_sales_order.id = rfq_sales_item.sales_order_id
            ${ew.customSqlSegment}
            ORDER BY ticket.id DESC
            LIMIT #{limit} OFFSET #{offset}
            </script>
            """)
    @Results(id = "deliveredItemResultMap", value = {
            @Result(column = "id", property = "id", id = true),
            @Result(column = "engineering_material_code", property = "engineeringMaterialCode"),
            @Result(column = "engineering_material_type_code", property = "engineeringMaterialTypeCode"),
            @Result(column = "engineering_material_type_name", property = "engineeringMaterialTypeName"),
            @Result(column = "engineering_material_category", property = "engineeringMaterialCategory"),
            @Result(column = "process_route", property = "processRoute", typeHandler = JsonbTypeHandler.class),
            @Result(column = "material_data", property = "materialData", typeHandler = JsonbTypeHandler.class)
    })
    List<DeliveredItemDTO> selectDeliveredItems(
            @Param(Constants.WRAPPER) Wrapper<DeliveredItemDTO> query,
            @Param("limit") int limit,
            @Param("offset") long offset);

    @Select("""
            <script>
            SELECT COUNT(*)
            FROM order_delivery_ticket ticket
            LEFT JOIN order_rfq rfq ON rfq.id = ticket.rfq_id
            LEFT JOIN item_quote_version item ON item.id = ticket.item_id
            LEFT JOIN sales_order_item direct_sales_item
                ON ticket.source_type = 'SALES_ORDER' AND direct_sales_item.id = ticket.source_item_id
            LEFT JOIN sales_order_header direct_sales_order
                ON ticket.source_type = 'SALES_ORDER' AND direct_sales_order.id = ticket.source_order_id
            LEFT JOIN sales_order_item rfq_sales_item ON rfq_sales_item.source_ticket_id = ticket.id
            LEFT JOIN sales_order_header rfq_sales_order ON rfq_sales_order.id = rfq_sales_item.sales_order_id
            ${ew.customSqlSegment}
            </script>
            """)
    long countDeliveredItems(@Param(Constants.WRAPPER) Wrapper<DeliveredItemDTO> query);

    @Select("""
            <script>
            SELECT DISTINCT COALESCE(ticket.customer_name, rfq.customer_name) AS customer_name
            FROM order_delivery_ticket ticket
            LEFT JOIN order_rfq rfq ON rfq.id = ticket.rfq_id
            ${ew.customSqlSegment}
            ORDER BY customer_name ASC
            </script>
            """)
    List<String> selectDeliveredItemCustomers(@Param(Constants.WRAPPER) Wrapper<DeliveredItemDTO> query);

    @Select("""
            <script>
            SELECT
                ticket.id,
                ticket.rfq_id,
                COALESCE(ticket.rfq_no, rfq.rfq_no) AS rfq_no,
                COALESCE(ticket.customer_name, rfq.customer_name) AS customer_name,
                ticket.item_id,
                ticket.source_type,
                ticket.source_order_id,
                ticket.source_item_id,
                COALESCE(direct_sales_order.id, rfq_sales_order.id) AS sales_order_id,
                COALESCE(direct_sales_order.sales_order_no, rfq_sales_order.sales_order_no) AS sales_order_no,
                COALESCE(direct_sales_order.customer_code, rfq_sales_order.customer_code) AS sales_order_customer_code,
                COALESCE(direct_sales_order.customer_name, rfq_sales_order.customer_name) AS sales_order_customer_name,
                ticket.part_no,
                ticket.version,
                ticket.description,
                ticket.qty,
                ticket.order_type AS order_type,
                ticket.remark,
                ticket.internal_project_no,
                ticket.customer_order_no,
                ticket.customer_project_no,
                ticket.project_no,
                ticket.item_type,
                COALESCE(ticket.material_name, item.material_name, direct_sales_item.material_name, rfq_sales_item.material_name) AS material_name,
                COALESCE(ticket.engineering_material_code, item.engineering_material_code, direct_sales_item.engineering_material_code, rfq_sales_item.engineering_material_code) AS engineering_material_code,
                COALESCE(ticket.engineering_material_name, item.engineering_material_name, direct_sales_item.engineering_material_name, rfq_sales_item.engineering_material_name) AS engineering_material_name,
                COALESCE(ticket.engineering_material_type_code, item.engineering_material_type_code, direct_sales_item.engineering_material_type_code, rfq_sales_item.engineering_material_type_code) AS engineering_material_type_code,
                COALESCE(ticket.engineering_material_type_name, item.engineering_material_type_name, direct_sales_item.engineering_material_type_name, rfq_sales_item.engineering_material_type_name) AS engineering_material_type_name,
                COALESCE(ticket.engineering_material_category, item.engineering_material_category, direct_sales_item.engineering_material_category, rfq_sales_item.engineering_material_category) AS engineering_material_category,
                COALESCE(ticket.material_shape, item.material_shape, direct_sales_item.material_shape, rfq_sales_item.material_shape) AS material_shape,
                COALESCE(ticket.surface_treatment, item.surface_treatment, direct_sales_item.surface_treatment, rfq_sales_item.surface_treatment) AS surface_treatment,
                COALESCE(ticket.drawing_url, direct_sales_item.drawing_url, rfq_sales_item.drawing_url, item.drawing_url) AS drawing_url,
                ticket.process_route,
                ticket.material_data,
                ticket.material_gross_weight,
                ticket.part_net_weight,
                ticket.material_no,
                ticket.mes_sync_status,
                ticket.mes_sync_message,
                ticket.mes_synced_at,
                ticket.mes_request_id,
                ticket.mes_external_task_id,
                ticket.mes_callback_received_at,
                ticket.mes_callback_message,
                ticket.mes_dedup_key,
                ticket.mes_request_role,
                ticket.mes_master_ticket_id,
                ticket.mes_master_request_id,
                ticket.wms_allocate_status,
                ticket.wms_allocate_message,
                ticket.wms_allocated_at,
                ticket.discarded,
                ticket.discarded_at,
                ticket.discarded_by,
                ticket.discard_reason,
                ticket.restored_at,
                ticket.restored_by,
                ticket.restore_reason,
                ticket.delivery_date
            FROM order_delivery_ticket ticket
            LEFT JOIN order_rfq rfq ON rfq.id = ticket.rfq_id
            LEFT JOIN item_quote_version item ON item.id = ticket.item_id
            LEFT JOIN sales_order_item direct_sales_item
                ON ticket.source_type = 'SALES_ORDER' AND direct_sales_item.id = ticket.source_item_id
            LEFT JOIN sales_order_header direct_sales_order
                ON ticket.source_type = 'SALES_ORDER' AND direct_sales_order.id = ticket.source_order_id
            LEFT JOIN sales_order_item rfq_sales_item ON rfq_sales_item.source_ticket_id = ticket.id
            LEFT JOIN sales_order_header rfq_sales_order ON rfq_sales_order.id = rfq_sales_item.sales_order_id
            ${ew.customSqlSegment}
            LIMIT 1
            </script>
            """)
    @ResultMap("deliveredItemResultMap")
    DeliveredItemDTO selectDeliveredItem(@Param(Constants.WRAPPER) Wrapper<DeliveredItemDTO> query);
}
