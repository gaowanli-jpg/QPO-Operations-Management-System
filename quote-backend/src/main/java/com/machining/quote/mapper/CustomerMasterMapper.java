package com.machining.quote.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.machining.quote.entity.CustomerMaster;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface CustomerMasterMapper extends BaseMapper<CustomerMaster> {
    @Select("SELECT COUNT(*) FROM order_rfq WHERE customer_name = #{customerName}")
    long countOrderReferences(@Param("customerName") String customerName);

    @Select("SELECT COUNT(*) FROM system_role_data_scope_customer WHERE customer_name = #{customerName}")
    long countDataScopeReferences(@Param("customerName") String customerName);
}
