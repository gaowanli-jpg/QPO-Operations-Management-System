package com.machining.apssystem.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.machining.apssystem.dto.ApsPartInventoryLockDTO;
import com.machining.apssystem.entity.ApsPartInventoryLock;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface ApsPartInventoryLockMapper extends BaseMapper<ApsPartInventoryLock> {
    @Select("""
            SELECT l.id, l.work_item_id, l.part_inventory_id, l.id AS part_inventory_lock_id,
                   l.customer_name, l.part_no, l.version,
                   l.demand_qty, l.locked_in_stock_qty, l.locked_in_production_qty,
                   l.production_required_qty, l.status, l.locked_at, l.released_at,
                   w.internal_project_no, w.customer_order_no, w.customer_project_no, w.delivery_date
            FROM aps_part_inventory_lock l
            JOIN aps_work_item w ON w.id = l.work_item_id
            WHERE l.work_item_id = #{workItemId}
            """)
    ApsPartInventoryLockDTO selectDtoByWorkItemId(@Param("workItemId") Long workItemId);

    @Select("""
            <script>
            SELECT d.id, d.part_inventory_lock_id, d.work_item_id, d.part_inventory_id,
                   l.customer_name, l.part_no, l.version,
                   l.demand_qty,
                   CASE WHEN d.lock_type = 'IN_STOCK' THEN d.locked_qty ELSE 0 END AS locked_in_stock_qty,
                   CASE WHEN d.lock_type = 'IN_PRODUCTION' THEN d.locked_qty ELSE 0 END AS locked_in_production_qty,
                   l.production_required_qty, l.status, l.locked_at, l.released_at,
                   d.lock_type,
                   CASE d.lock_type
                       WHEN 'IN_STOCK' THEN '锁定已入库库存'
                       WHEN 'IN_PRODUCTION' THEN '锁定在产库存'
                       ELSE d.lock_type
                   END AS lock_type_text,
                   d.pending_stock_id, d.pending_ref_no, d.locked_qty,
                   w.internal_project_no, w.customer_order_no, w.customer_project_no, w.delivery_date
            FROM aps_part_inventory_lock_detail d
            JOIN aps_part_inventory_lock l ON l.id = d.part_inventory_lock_id
            JOIN aps_work_item w ON w.id = d.work_item_id
            WHERE d.locked_qty > 0
              AND d.status = 'PENDING'
              <if test="customerName != null and customerName != ''">
                  AND l.customer_name = #{customerName}
              </if>
            ORDER BY d.id DESC
            LIMIT #{limit}
            </script>
            """)
    List<ApsPartInventoryLockDTO> selectDtos(
            @Param("customerName") String customerName,
            @Param("limit") int limit);

    @Select("""
            SELECT id, work_item_id, part_inventory_id, customer_name, part_no, version,
                   demand_qty, locked_in_stock_qty, locked_in_production_qty,
                   production_required_qty, status, locked_at, released_at,
                   created_at, updated_at
            FROM aps_part_inventory_lock
            WHERE work_item_id = #{workItemId}
            FOR UPDATE
            """)
    ApsPartInventoryLock selectByWorkItemIdForUpdate(@Param("workItemId") Long workItemId);

    @Select("""
            SELECT id, work_item_id, part_inventory_id, customer_name, part_no, version,
                   demand_qty, locked_in_stock_qty, locked_in_production_qty,
                   production_required_qty, status, locked_at, released_at,
                   created_at, updated_at
            FROM aps_part_inventory_lock
            WHERE id = #{id}
            FOR UPDATE
            """)
    ApsPartInventoryLock selectByIdForUpdate(@Param("id") Long id);

    @Update("""
            UPDATE aps_part_inventory_lock
            SET locked_in_stock_qty = #{lockedInStockQty},
                locked_in_production_qty = #{lockedInProductionQty},
                production_required_qty = #{productionRequiredQty},
                status = #{status},
                released_at = CASE
                    WHEN #{lockedInStockQty} + #{lockedInProductionQty} = 0 THEN CURRENT_TIMESTAMP
                    ELSE released_at
                END,
                updated_at = CURRENT_TIMESTAMP
            WHERE id = #{id}
            """)
    int updateLockQuantities(
            @Param("id") Long id,
            @Param("lockedInStockQty") int lockedInStockQty,
            @Param("lockedInProductionQty") int lockedInProductionQty,
            @Param("productionRequiredQty") int productionRequiredQty,
            @Param("status") String status);
}
