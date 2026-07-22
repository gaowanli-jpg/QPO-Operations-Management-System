package com.machining.quote.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.machining.quote.entity.ErpUser;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface ErpUserMapper extends BaseMapper<ErpUser> {
    @Select("SELECT * FROM erp_user WHERE id = #{id} FOR UPDATE")
    ErpUser selectByIdForUpdate(@Param("id") Integer id);

    @Select("SELECT * FROM erp_user WHERE username = #{username} LIMIT 1 FOR UPDATE")
    ErpUser selectByUsernameForUpdate(@Param("username") String username);
}
