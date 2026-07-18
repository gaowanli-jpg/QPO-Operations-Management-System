package com.machining.quote.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.machining.quote.entity.SystemRole;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface SystemRoleMapper extends BaseMapper<SystemRole> {
    @Select("""
            SELECT r.*
            FROM system_role r
            JOIN system_user_role ur ON ur.role_id = r.id
            JOIN system_user u ON u.id = ur.user_id
            WHERE u.legacy_user_id = #{legacyUserId}
              AND COALESCE(u.deleted, FALSE) = FALSE
              AND COALESCE(r.deleted, FALSE) = FALSE
              AND COALESCE(r.enabled, TRUE) = TRUE
            ORDER BY r.sort_order ASC, r.id ASC
            """)
    List<SystemRole> selectActiveRolesByLegacyUserId(@Param("legacyUserId") Integer legacyUserId);
}
