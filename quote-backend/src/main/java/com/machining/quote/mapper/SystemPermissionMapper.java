package com.machining.quote.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.machining.quote.entity.SystemPermission;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface SystemPermissionMapper extends BaseMapper<SystemPermission> {
    @Select("""
            SELECT DISTINCT p.*
            FROM system_permission p
            JOIN system_role_permission rp ON rp.permission_id = p.id
            JOIN system_role r ON r.id = rp.role_id
            JOIN system_user_role ur ON ur.role_id = r.id
            JOIN system_user u ON u.id = ur.user_id
            WHERE u.legacy_user_id = #{legacyUserId}
              AND COALESCE(u.deleted, FALSE) = FALSE
              AND COALESCE(r.deleted, FALSE) = FALSE
              AND COALESCE(r.enabled, TRUE) = TRUE
              AND COALESCE(p.enabled, TRUE) = TRUE
            ORDER BY p.sort_order ASC, p.id ASC
            """)
    List<SystemPermission> selectEnabledPermissionsByLegacyUserId(@Param("legacyUserId") Integer legacyUserId);

    @Select("""
            <script>
            SELECT *
            FROM system_permission
            WHERE permission_code IN
              <foreach collection="codes" item="code" open="(" separator="," close=")">
                #{code}
              </foreach>
            ORDER BY sort_order ASC, id ASC
            </script>
            """)
    List<SystemPermission> selectByPermissionCodes(@Param("codes") List<String> codes);
}
