package com.machining.quote.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.machining.quote.entity.SalesOrderItem;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface SalesOrderItemMapper extends BaseMapper<SalesOrderItem> {
    @Select("SELECT * FROM sales_order_item WHERE id = #{id} FOR UPDATE")
    SalesOrderItem selectByIdForUpdate(@Param("id") Long id);
}
