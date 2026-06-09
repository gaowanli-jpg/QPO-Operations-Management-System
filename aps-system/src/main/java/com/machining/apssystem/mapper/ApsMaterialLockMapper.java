package com.machining.apssystem.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.machining.apssystem.dto.ApsMaterialLockDTO;
import com.machining.apssystem.entity.ApsMaterialLock;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface ApsMaterialLockMapper extends BaseMapper<ApsMaterialLock> {
    @Select("""
            SELECT id, work_item_id, material_inventory_id, rfq_no, internal_project_no,
                   customer_name, part_no, version, material_name, material_type,
                   demand_weight_kg, locked_weight_kg, shortage_weight_kg, status,
                   locked_at, released_at
            FROM aps_material_lock
            WHERE work_item_id = #{workItemId}
            """)
    ApsMaterialLockDTO selectDtoByWorkItemId(@Param("workItemId") Long workItemId);

    @Select("""
            SELECT id, work_item_id, material_inventory_id, rfq_no, internal_project_no,
                   customer_name, part_no, version, material_name, material_type,
                   demand_weight_kg, locked_weight_kg, shortage_weight_kg, status,
                   locked_at, released_at
            FROM aps_material_lock
            ORDER BY id DESC
            LIMIT #{limit}
            """)
    List<ApsMaterialLockDTO> selectDtos(@Param("limit") int limit);

    @Select("""
            SELECT id, work_item_id, material_inventory_id, rfq_no, internal_project_no,
                   customer_name, part_no, version, material_name, material_type,
                   demand_weight_kg, locked_weight_kg, shortage_weight_kg, status,
                   locked_at, released_at, created_at, updated_at
            FROM aps_material_lock
            WHERE work_item_id = #{workItemId}
            FOR UPDATE
            """)
    ApsMaterialLock selectByWorkItemIdForUpdate(@Param("workItemId") Long workItemId);
}
