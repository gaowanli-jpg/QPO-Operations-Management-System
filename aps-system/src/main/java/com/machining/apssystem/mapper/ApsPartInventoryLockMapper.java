package com.machining.apssystem.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.machining.apssystem.dto.ApsPartInventoryLockDTO;
import com.machining.apssystem.entity.ApsPartInventoryLock;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface ApsPartInventoryLockMapper extends BaseMapper<ApsPartInventoryLock> {
    @Select("""
            SELECT id, work_item_id, part_inventory_id, customer_name, part_no, version,
                   demand_qty, locked_in_stock_qty, locked_in_production_qty,
                   production_required_qty, status, locked_at, released_at
            FROM aps_part_inventory_lock
            WHERE work_item_id = #{workItemId}
            """)
    ApsPartInventoryLockDTO selectDtoByWorkItemId(@Param("workItemId") Long workItemId);

    @Select("""
            SELECT l.id, l.work_item_id, l.part_inventory_id, l.customer_name, l.part_no, l.version,
                   l.demand_qty, l.locked_in_stock_qty, l.locked_in_production_qty,
                   l.production_required_qty, l.status, l.locked_at, l.released_at,
                   w.rfq_no, w.internal_project_no, w.delivery_date
            FROM aps_part_inventory_lock l
            JOIN aps_work_item w ON w.id = l.work_item_id
            ORDER BY l.id DESC
            LIMIT #{limit}
            """)
    List<ApsPartInventoryLockDTO> selectDtos(@Param("limit") int limit);

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
}
