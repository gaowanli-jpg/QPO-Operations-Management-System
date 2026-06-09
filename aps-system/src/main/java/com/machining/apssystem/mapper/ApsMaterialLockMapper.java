package com.machining.apssystem.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.machining.apssystem.dto.ApsMaterialLockDTO;
import com.machining.apssystem.entity.ApsMaterialLock;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.math.BigDecimal;
import java.util.List;

@Mapper
public interface ApsMaterialLockMapper extends BaseMapper<ApsMaterialLock> {
    @Select("""
            SELECT l.id, l.work_item_id, l.material_inventory_id, l.internal_project_no,
                   w.customer_order_no, w.customer_project_no,
                   l.customer_name, l.part_no, l.version, l.material_name, l.material_type,
                   l.demand_weight_kg, l.locked_weight_kg, l.shortage_weight_kg, l.status,
                   l.warehouse_status, l.locked_at, l.confirmed_at, l.released_at
            FROM aps_material_lock l
            JOIN aps_work_item w ON w.id = l.work_item_id
            WHERE l.work_item_id = #{workItemId}
            """)
    ApsMaterialLockDTO selectDtoByWorkItemId(@Param("workItemId") Long workItemId);

    @Select("""
            <script>
            SELECT l.id, l.work_item_id, l.material_inventory_id, l.internal_project_no,
                   w.customer_order_no, w.customer_project_no,
                   l.customer_name, l.part_no, l.version, l.material_name, l.material_type,
                   l.demand_weight_kg, l.locked_weight_kg, l.shortage_weight_kg, l.status,
                   l.warehouse_status, l.locked_at, l.confirmed_at, l.released_at
            FROM aps_material_lock l
            JOIN aps_work_item w ON w.id = l.work_item_id
            WHERE l.locked_weight_kg > 0
              AND l.warehouse_status = 'PENDING'
              <if test="customerName != null and customerName != ''">
                  AND l.customer_name = #{customerName}
              </if>
            ORDER BY l.id DESC
            LIMIT #{limit}
            </script>
            """)
    List<ApsMaterialLockDTO> selectDtos(
            @Param("customerName") String customerName,
            @Param("limit") int limit);

    @Select("""
            SELECT id, work_item_id, material_inventory_id, internal_project_no,
                   customer_name, part_no, version, material_name, material_type,
                   demand_weight_kg, locked_weight_kg, shortage_weight_kg, status,
                   warehouse_status, locked_at, confirmed_at, released_at, created_at, updated_at
            FROM aps_material_lock
            WHERE work_item_id = #{workItemId}
            FOR UPDATE
            """)
    ApsMaterialLock selectByWorkItemIdForUpdate(@Param("workItemId") Long workItemId);

    @Select("""
            SELECT id, work_item_id, material_inventory_id, internal_project_no,
                   customer_name, part_no, version, material_name, material_type,
                   demand_weight_kg, locked_weight_kg, shortage_weight_kg, status,
                   warehouse_status, locked_at, confirmed_at, released_at, created_at, updated_at
            FROM aps_material_lock
            WHERE id = #{id}
            FOR UPDATE
            """)
    ApsMaterialLock selectByIdForUpdate(@Param("id") Long id);

    @Select("""
            SELECT DISTINCT customer_name
            FROM aps_material_lock
            WHERE customer_name IS NOT NULL AND customer_name <> ''
              AND locked_weight_kg > 0
              AND warehouse_status = 'PENDING'
            ORDER BY customer_name
            """)
    List<String> selectCustomerNames();

    @Update("""
            UPDATE aps_material_lock
            SET warehouse_status = 'CONFIRMED',
                confirmed_at = CURRENT_TIMESTAMP,
                updated_at = CURRENT_TIMESTAMP
            WHERE id = #{id}
            """)
    int markConfirmed(@Param("id") Long id);

    @Update("""
            UPDATE aps_material_lock
            SET locked_weight_kg = 0,
                shortage_weight_kg = #{shortageWeightKg},
                status = #{status},
                warehouse_status = 'RELEASED',
                released_at = CURRENT_TIMESTAMP,
                updated_at = CURRENT_TIMESTAMP
            WHERE id = #{id}
            """)
    int markReleased(
            @Param("id") Long id,
            @Param("shortageWeightKg") BigDecimal shortageWeightKg,
            @Param("status") String status);
}
