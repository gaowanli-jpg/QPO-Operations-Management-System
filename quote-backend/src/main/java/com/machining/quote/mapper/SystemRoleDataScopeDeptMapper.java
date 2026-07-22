package com.machining.quote.mapper;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface SystemRoleDataScopeDeptMapper {
    @Select("SELECT dept_id FROM system_role_data_scope_dept WHERE role_id = #{roleId} ORDER BY dept_id ASC")
    List<Long> selectDeptIdsByRoleId(@Param("roleId") Long roleId);

    @Delete("DELETE FROM system_role_data_scope_dept WHERE role_id = #{roleId}")
    int deleteByRoleId(@Param("roleId") Long roleId);

    @Insert("""
            INSERT INTO system_role_data_scope_dept (role_id, dept_id)
            VALUES (#{roleId}, #{deptId})
            ON CONFLICT (role_id, dept_id) DO NOTHING
            """)
    int insertIgnore(@Param("roleId") Long roleId, @Param("deptId") Long deptId);
}
