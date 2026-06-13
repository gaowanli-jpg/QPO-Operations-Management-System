package com.machining.apssystem.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.machining.apssystem.entity.ApsUser;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface ApsUserMapper extends BaseMapper<ApsUser> {
    @Select("SELECT * FROM aps_user WHERE id = #{id} FOR UPDATE")
    ApsUser selectByIdForUpdate(@Param("id") Long id);

    @Select("SELECT * FROM aps_user WHERE username = #{username} LIMIT 1 FOR UPDATE")
    ApsUser selectByUsernameForUpdate(@Param("username") String username);
}
