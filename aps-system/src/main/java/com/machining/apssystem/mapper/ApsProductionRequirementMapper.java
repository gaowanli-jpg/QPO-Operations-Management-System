package com.machining.apssystem.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.machining.apssystem.dto.ApsPartProductionRequirementDTO;
import com.machining.apssystem.entity.ApsProductionRequirement;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface ApsProductionRequirementMapper extends BaseMapper<ApsProductionRequirement> {
    @Select("""
            <script>
            SELECT r.id, r.work_item_id, r.customer_name, r.part_no, r.version,
                   r.required_qty, r.status, r.remark, r.created_at, r.updated_at,
                   w.internal_project_no, w.customer_order_no, w.customer_project_no, w.delivery_date
            FROM aps_production_requirement r
            JOIN aps_work_item w ON w.id = r.work_item_id
            <where>
                <if test="status != null and status != ''">
                    r.status = #{status}
                </if>
            </where>
            ORDER BY r.id DESC
            LIMIT #{limit}
            </script>
            """)
    List<ApsPartProductionRequirementDTO> selectDtos(
            @Param("status") String status,
            @Param("limit") int limit);

    @Select("""
            SELECT id, work_item_id, customer_name, part_no, version,
                   required_qty, status, remark, created_at, updated_at
            FROM aps_production_requirement
            WHERE work_item_id = #{workItemId}
            ORDER BY id
            LIMIT 1
            FOR UPDATE
            """)
    ApsProductionRequirement selectByWorkItemIdForUpdate(@Param("workItemId") Long workItemId);

    @Update("""
            UPDATE aps_production_requirement
            SET required_qty = required_qty + #{qty},
                status = 'PENDING_PRODUCTION',
                updated_at = CURRENT_TIMESTAMP
            WHERE id = #{id}
            """)
    int addRequiredQty(@Param("id") Long id, @Param("qty") int qty);
}
