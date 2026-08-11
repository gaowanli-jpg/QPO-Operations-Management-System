package com.machining.quote.mapper;

import com.machining.quote.dto.CustomerOptionDTO;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface SystemRoleDataScopeCustomerMapper {
    @Select("""
            SELECT customer_code
            FROM system_role_data_scope_customer
            WHERE role_id = #{roleId}
              AND customer_code IS NOT NULL
              AND customer_code <> ''
            ORDER BY customer_code ASC
            """)
    List<String> selectCustomerCodesByRoleId(@Param("roleId") Long roleId);

    @Select("""
            SELECT
                customer_code AS customerCode,
                COALESCE(NULLIF(customer_name_snapshot, ''), customer_name, customer_code) AS customerShortName,
                COALESCE(NULLIF(customer_name_snapshot, ''), customer_name, customer_code) AS customerName
            FROM system_role_data_scope_customer
            WHERE role_id = #{roleId}
              AND customer_code IS NOT NULL
              AND customer_code <> ''
            ORDER BY customer_code ASC
            """)
    List<CustomerOptionDTO> selectCustomerOptionsByRoleId(@Param("roleId") Long roleId);

    @Select("""
            SELECT COALESCE(NULLIF(customer_name_snapshot, ''), customer_name, customer_code) AS customer_name
            FROM system_role_data_scope_customer
            WHERE role_id = #{roleId}
            ORDER BY COALESCE(NULLIF(customer_name_snapshot, ''), customer_name, customer_code) ASC
            """)
    List<String> selectCustomerNamesByRoleId(@Param("roleId") Long roleId);

    @Delete("DELETE FROM system_role_data_scope_customer WHERE role_id = #{roleId}")
    int deleteByRoleId(@Param("roleId") Long roleId);

    @Insert("""
            INSERT INTO system_role_data_scope_customer (role_id, customer_code, customer_name, customer_name_snapshot)
            VALUES (#{roleId}, #{customerCode}, #{customerNameSnapshot}, #{customerNameSnapshot})
            ON CONFLICT (role_id, customer_code) DO NOTHING
            """)
    int insertIgnore(
            @Param("roleId") Long roleId,
            @Param("customerCode") String customerCode,
            @Param("customerNameSnapshot") String customerNameSnapshot);
}
