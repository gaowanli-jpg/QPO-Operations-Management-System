package com.machining.quote.mapper;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface SystemRolePermissionMapper {
    @Select("""
            SELECT p.permission_code
            FROM system_permission p
            JOIN system_role_permission rp ON rp.permission_id = p.id
            WHERE rp.role_id = #{roleId}
            ORDER BY p.sort_order ASC, p.id ASC
            """)
    List<String> selectPermissionCodesByRoleId(@Param("roleId") Long roleId);

    @Delete("DELETE FROM system_role_permission WHERE role_id = #{roleId}")
    int deleteByRoleId(@Param("roleId") Long roleId);

    @Insert("""
            INSERT INTO system_role_permission (role_id, permission_id)
            VALUES (#{roleId}, #{permissionId})
            ON CONFLICT (role_id, permission_id) DO NOTHING
            """)
    int insertIgnore(@Param("roleId") Long roleId, @Param("permissionId") Long permissionId);
}
