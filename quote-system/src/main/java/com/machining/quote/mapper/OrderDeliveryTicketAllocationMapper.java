package com.machining.quote.mapper;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.toolkit.Constants;
import com.machining.quote.config.JsonbTypeHandler;
import com.machining.quote.dto.LockedItemDTO;
import com.machining.quote.entity.OrderDeliveryTicketAllocation;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Result;
import org.apache.ibatis.annotations.Results;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface OrderDeliveryTicketAllocationMapper extends BaseMapper<OrderDeliveryTicketAllocation> {
    @Select("""
            <script>
            SELECT
                alloc.id,
                alloc.ticket_id,
                alloc.rfq_id,
                alloc.rfq_no,
                alloc.customer_name,
                alloc.item_id,
                alloc.allocation_type,
                alloc.qty,
                alloc.material_no,
                alloc.part_no,
                alloc.version,
                alloc.internal_project_no,
                alloc.customer_order_no,
                alloc.customer_project_no,
                alloc.material_name,
                alloc.engineering_material_name,
                alloc.surface_treatment,
                alloc.description,
                alloc.delivery_date,
                alloc.process_route,
                alloc.material_data,
                alloc.material_gross_weight,
                alloc.part_net_weight,
                alloc.wms_production_order_no,
                alloc.source_payload,
                alloc.status,
                alloc.created_at,
                alloc.updated_at
            FROM order_delivery_ticket_allocation alloc
            LEFT JOIN order_rfq rfq ON rfq.id = alloc.rfq_id
            ${ew.customSqlSegment}
            ORDER BY alloc.id DESC
            LIMIT #{limit} OFFSET #{offset}
            </script>
            """)
    @Results(id = "lockedItemResultMap", value = {
            @Result(column = "id", property = "id", id = true),
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
            ${ew.customSqlSegment}
            </script>
            """)
    long countLockedItems(@Param(Constants.WRAPPER) Wrapper<LockedItemDTO> query);
}
