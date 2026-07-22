package com.machining.quote.mapper;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface SystemUserRoleMapper {
    @Select("""
            SELECT r.role_code
            FROM system_role r
            JOIN system_user_role ur ON ur.role_id = r.id
            WHERE ur.user_id = #{userId}
            ORDER BY r.sort_order ASC, r.id ASC
            """)
    List<String> selectRoleCodesByUserId(@Param("userId") Long userId);

    @Select("""
            SELECT r.role_name
            FROM system_role r
            JOIN system_user_role ur ON ur.role_id = r.id
            WHERE ur.user_id = #{userId}
            ORDER BY r.sort_order ASC, r.id ASC
            """)
    List<String> selectRoleNamesByUserId(@Param("userId") Long userId);

    @Delete("DELETE FROM system_user_role WHERE user_id = #{userId}")
    int deleteByUserId(@Param("userId") Long userId);

    @Insert("""
            INSERT INTO system_user_role (user_id, role_id)
            VALUES (#{userId}, #{roleId})
            ON CONFLICT (user_id, role_id) DO NOTHING
            """)
    int insertIgnore(@Param("userId") Long userId, @Param("roleId") Long roleId);
}
