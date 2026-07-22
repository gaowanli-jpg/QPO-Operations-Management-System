package com.machining.quote.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.machining.quote.entity.SystemUser;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface SystemUserMapper extends BaseMapper<SystemUser> {
    @Select("SELECT * FROM system_user WHERE legacy_user_id = #{legacyUserId} AND deleted = FALSE LIMIT 1")
    SystemUser selectByLegacyUserId(@Param("legacyUserId") Integer legacyUserId);
}
