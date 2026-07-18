package com.machining.quote.mapper;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface SystemRoleDataScopeCustomerMapper {
    @Select("SELECT customer_name FROM system_role_data_scope_customer WHERE role_id = #{roleId} ORDER BY customer_name ASC")
    List<String> selectCustomerNamesByRoleId(@Param("roleId") Long roleId);

    @Delete("DELETE FROM system_role_data_scope_customer WHERE role_id = #{roleId}")
    int deleteByRoleId(@Param("roleId") Long roleId);

    @Insert("""
            INSERT INTO system_role_data_scope_customer (role_id, customer_name)
            VALUES (#{roleId}, #{customerName})
            ON CONFLICT (role_id, customer_name) DO NOTHING
            """)
    int insertIgnore(@Param("roleId") Long roleId, @Param("customerName") String customerName);
}
