package com.machining.quote.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.machining.quote.entity.SystemDataCenterSsoConfig;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface SystemDataCenterSsoConfigMapper extends BaseMapper<SystemDataCenterSsoConfig> {
    @Select("""
            SELECT *
            FROM system_data_center_sso_config
            WHERE active = true
              AND enabled = true
            ORDER BY id
            LIMIT 1
            """)
    SystemDataCenterSsoConfig selectActive();
}
