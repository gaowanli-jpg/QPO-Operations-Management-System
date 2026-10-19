package com.machining.quote.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.machining.quote.entity.SalesOrderChangeDispatchSetting;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface SalesOrderChangeDispatchSettingMapper extends BaseMapper<SalesOrderChangeDispatchSetting> {
    @Select("SELECT * FROM sales_order_change_dispatch_setting WHERE id = 1 FOR UPDATE")
    SalesOrderChangeDispatchSetting selectSingletonForUpdate();

    @Select("SELECT * FROM sales_order_change_dispatch_setting WHERE id = 1 FOR SHARE")
    SalesOrderChangeDispatchSetting selectSingletonForShare();
}
