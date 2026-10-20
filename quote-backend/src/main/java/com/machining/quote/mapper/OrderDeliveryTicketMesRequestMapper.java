package com.machining.quote.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.machining.quote.entity.OrderDeliveryTicketMesRequest;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface OrderDeliveryTicketMesRequestMapper extends BaseMapper<OrderDeliveryTicketMesRequest> {
    @Update("""
            UPDATE order_delivery_ticket_mes_request
            SET batch_id = #{batchId},
                batch_item_id = #{batchItemId},
                updated_at = CURRENT_TIMESTAMP
            WHERE id = #{requestRowId}
            """)
    int linkMesBatchItem(@Param("requestRowId") Long requestRowId,
                         @Param("batchId") Long batchId,
                         @Param("batchItemId") Long batchItemId);
}
