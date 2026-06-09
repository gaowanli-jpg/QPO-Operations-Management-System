package com.machining.apssystem.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.machining.apssystem.entity.ApsPartInventoryLockDetail;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface ApsPartInventoryLockDetailMapper extends BaseMapper<ApsPartInventoryLockDetail> {
    @Select("""
            SELECT id, part_inventory_lock_id, work_item_id, part_inventory_id,
                   lock_type, pending_stock_id, pending_ref_no, locked_qty,
                   status, confirmed_at, released_at, created_at, updated_at
            FROM aps_part_inventory_lock_detail
            WHERE work_item_id = #{workItemId}
              AND status IN ('PENDING', 'CONFIRMED')
            ORDER BY id
            FOR UPDATE
            """)
    List<ApsPartInventoryLockDetail> selectByWorkItemIdForUpdate(@Param("workItemId") Long workItemId);

    @Select("""
            SELECT id, part_inventory_lock_id, work_item_id, part_inventory_id,
                   lock_type, pending_stock_id, pending_ref_no, locked_qty,
                   status, confirmed_at, released_at, created_at, updated_at
            FROM aps_part_inventory_lock_detail
            WHERE id = #{id}
            FOR UPDATE
            """)
    ApsPartInventoryLockDetail selectByIdForUpdate(@Param("id") Long id);

    @Update("""
            UPDATE aps_part_inventory_lock_detail
            SET status = 'CONFIRMED',
                confirmed_at = CURRENT_TIMESTAMP,
                updated_at = CURRENT_TIMESTAMP
            WHERE id = #{id}
            """)
    int markConfirmed(@Param("id") Long id);

    @Update("""
            UPDATE aps_part_inventory_lock_detail
            SET status = 'RELEASED',
                released_at = CURRENT_TIMESTAMP,
                updated_at = CURRENT_TIMESTAMP
            WHERE id = #{id}
            """)
    int markReleased(@Param("id") Long id);
}
