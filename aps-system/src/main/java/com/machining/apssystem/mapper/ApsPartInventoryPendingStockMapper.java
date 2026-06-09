package com.machining.apssystem.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.machining.apssystem.dto.ApsPartInventoryPendingStockDTO;
import com.machining.apssystem.entity.ApsPartInventoryPendingStock;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface ApsPartInventoryPendingStockMapper extends BaseMapper<ApsPartInventoryPendingStock> {
    @Select("""
            SELECT id, part_inventory_id, pending_ref_no, pending_qty, locked_qty,
                   available_qty, created_at, updated_at
            FROM aps_part_inventory_pending_stock
            WHERE part_inventory_id = #{inventoryId} AND pending_qty > 0
            ORDER BY id
            """)
    List<ApsPartInventoryPendingStockDTO> selectDtosByInventoryId(@Param("inventoryId") Long inventoryId);

    @Select("""
            SELECT id, part_inventory_id, pending_ref_no, pending_qty, locked_qty,
                   available_qty, created_at, updated_at
            FROM aps_part_inventory_pending_stock
            WHERE part_inventory_id = #{inventoryId} AND pending_ref_no = #{pendingRefNo}
            FOR UPDATE
            """)
    ApsPartInventoryPendingStock selectByInventoryAndRefForUpdate(
            @Param("inventoryId") Long inventoryId,
            @Param("pendingRefNo") String pendingRefNo);

    @Select("""
            SELECT id, part_inventory_id, pending_ref_no, pending_qty, locked_qty,
                   available_qty, created_at, updated_at
            FROM aps_part_inventory_pending_stock
            WHERE part_inventory_id = #{inventoryId} AND locked_qty > 0
            ORDER BY id
            FOR UPDATE
            """)
    List<ApsPartInventoryPendingStock> selectLockedRowsForUpdate(@Param("inventoryId") Long inventoryId);

    @Select("""
            SELECT id, part_inventory_id, pending_ref_no, pending_qty, locked_qty,
                   available_qty, created_at, updated_at
            FROM aps_part_inventory_pending_stock
            WHERE part_inventory_id = #{inventoryId} AND available_qty > 0
            ORDER BY id
            FOR UPDATE
            """)
    List<ApsPartInventoryPendingStock> selectAvailableRowsForUpdate(@Param("inventoryId") Long inventoryId);

    @Update("""
            UPDATE aps_part_inventory_pending_stock
            SET pending_qty = pending_qty + #{qty},
                updated_at = CURRENT_TIMESTAMP
            WHERE id = #{id}
            """)
    int addPendingQty(@Param("id") Long id, @Param("qty") int qty);

    @Update("""
            UPDATE aps_part_inventory_pending_stock
            SET pending_qty = #{qty},
                updated_at = CURRENT_TIMESTAMP
            WHERE id = #{id}
            """)
    int setPendingQty(@Param("id") Long id, @Param("qty") int qty);

    @Update("""
            UPDATE aps_part_inventory_pending_stock
            SET locked_qty = GREATEST(locked_qty - #{qty}, 0),
                updated_at = CURRENT_TIMESTAMP
            WHERE id = #{id}
            """)
    int releaseLockedQty(@Param("id") Long id, @Param("qty") int qty);

    @Update("""
            UPDATE aps_part_inventory_pending_stock
            SET locked_qty = locked_qty + #{qty},
                updated_at = CURRENT_TIMESTAMP
            WHERE id = #{id}
            """)
    int addLockedQty(@Param("id") Long id, @Param("qty") int qty);
}
