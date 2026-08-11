package com.machining.quote.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.machining.quote.entity.CustomerMaster;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface CustomerMasterMapper extends BaseMapper<CustomerMaster> {
    @Select("SELECT * FROM customer_master WHERE customer_name = #{customerName} AND enabled = TRUE FOR UPDATE")
    CustomerMaster selectEnabledByNameForUpdate(@Param("customerName") String customerName);

    @Select("SELECT * FROM customer_master WHERE customer_code = #{customerCode} AND enabled = TRUE")
    CustomerMaster selectEnabledByCode(@Param("customerCode") String customerCode);
}
