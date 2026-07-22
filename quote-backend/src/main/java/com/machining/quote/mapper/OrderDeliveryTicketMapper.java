package com.machining.quote.mapper;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.toolkit.Constants;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.machining.quote.config.JsonbTypeHandler;
import com.machining.quote.dto.DeliveredItemDTO;
import com.machining.quote.entity.OrderDeliveryTicket;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Result;
import org.apache.ibatis.annotations.ResultMap;
import org.apache.ibatis.annotations.Results;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface OrderDeliveryTicketMapper extends BaseMapper<OrderDeliveryTicket> {
    @Select("""
            <script>
            SELECT
                ticket.id,
                ticket.rfq_id,
                COALESCE(ticket.rfq_no, rfq.rfq_no) AS rfq_no,
                COALESCE(ticket.customer_name, rfq.customer_name) AS customer_name,
                ticket.item_id,
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
                COALESCE(ticket.material_name, item.material_name) AS material_name,
                COALESCE(ticket.engineering_material_name, item.engineering_material_name) AS engineering_material_name,
                COALESCE(ticket.surface_treatment, item.surface_treatment) AS surface_treatment,
                item.drawing_url AS drawing_url,
                ticket.process_route,
                ticket.material_data,
                ticket.material_gross_weight,
                ticket.part_net_weight,
                ticket.material_no,
                ticket.mes_sync_status,
                ticket.mes_sync_message,
                ticket.mes_synced_at,
                ticket.wms_allocate_status,
                ticket.wms_allocate_message,
                ticket.wms_allocated_at,
                ticket.delivery_date
            FROM order_delivery_ticket ticket
            LEFT JOIN order_rfq rfq ON rfq.id = ticket.rfq_id
            LEFT JOIN item_quote_version item ON item.id = ticket.item_id
            ${ew.customSqlSegment}
            ORDER BY ticket.id DESC
            LIMIT #{limit} OFFSET #{offset}
            </script>
            """)
    @Results(id = "deliveredItemResultMap", value = {
            @Result(column = "id", property = "id", id = true),
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
                COALESCE(ticket.material_name, item.material_name) AS material_name,
                COALESCE(ticket.engineering_material_name, item.engineering_material_name) AS engineering_material_name,
                COALESCE(ticket.surface_treatment, item.surface_treatment) AS surface_treatment,
                item.drawing_url AS drawing_url,
                ticket.process_route,
                ticket.material_data,
                ticket.material_gross_weight,
                ticket.part_net_weight,
                ticket.material_no,
                ticket.mes_sync_status,
                ticket.mes_sync_message,
                ticket.mes_synced_at,
                ticket.wms_allocate_status,
                ticket.wms_allocate_message,
                ticket.wms_allocated_at,
                ticket.delivery_date
            FROM order_delivery_ticket ticket
            LEFT JOIN order_rfq rfq ON rfq.id = ticket.rfq_id
            LEFT JOIN item_quote_version item ON item.id = ticket.item_id
            ${ew.customSqlSegment}
            LIMIT 1
            </script>
            """)
    @ResultMap("deliveredItemResultMap")
    DeliveredItemDTO selectDeliveredItem(@Param(Constants.WRAPPER) Wrapper<DeliveredItemDTO> query);
}
