package com.machining.quote.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.machining.quote.config.JsonbTypeHandler;
import com.machining.quote.entity.SalesOrderAppendBatch;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Result;
import org.apache.ibatis.annotations.Results;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface SalesOrderAppendBatchMapper extends BaseMapper<SalesOrderAppendBatch> {
    @Select("SELECT * FROM sales_order_append_batch WHERE append_batch_key = #{appendBatchKey} FOR UPDATE")
    @Results(id = "salesOrderAppendBatchResultMap", value = {
            @Result(column = "id", property = "id", id = true),
            @Result(column = "append_batch_key", property = "appendBatchKey"),
            @Result(column = "target_sales_order_id", property = "targetSalesOrderId"),
            @Result(column = "request_hash", property = "requestHash"),
            @Result(column = "source_rfq_ids", property = "sourceRfqIds", typeHandler = JsonbTypeHandler.class),
            @Result(column = "ticket_ids", property = "ticketIds", typeHandler = JsonbTypeHandler.class),
            @Result(column = "sales_order_item_ids", property = "salesOrderItemIds", typeHandler = JsonbTypeHandler.class),
            @Result(column = "item_count", property = "itemCount"),
            @Result(column = "created_by", property = "createdBy"),
            @Result(column = "created_at", property = "createdAt")
    })
    SalesOrderAppendBatch selectByAppendBatchKeyForUpdate(@Param("appendBatchKey") String appendBatchKey);
}
