package com.machining.quote.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.machining.quote.entity.OrderRfq;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.ResultMap;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface OrderRfqMapper extends BaseMapper<OrderRfq> {
    @ResultMap("mybatis-plus_OrderRfq")
    @Select("SELECT * FROM order_rfq WHERE id = #{id} FOR UPDATE")
    OrderRfq selectByIdForUpdate(@Param("id") Integer id);
}
