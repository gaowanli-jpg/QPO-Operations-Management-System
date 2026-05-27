package com.machining.quote.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.machining.quote.entity.InventoryMaster;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface InventoryMasterMapper extends BaseMapper<InventoryMaster> {
    @Select("SELECT * FROM inventory_master WHERE id = #{id} FOR UPDATE")
    InventoryMaster selectByIdForUpdate(@Param("id") Integer id);

    @Select("SELECT * FROM inventory_master WHERE customer_name = #{customerName} AND part_no = #{partNo} AND version = #{version} LIMIT 1 FOR UPDATE")
    InventoryMaster selectByCustomerPartAndVersionForUpdate(@Param("customerName") String customerName,
                                                            @Param("partNo") String partNo,
                                                            @Param("version") String version);
}
