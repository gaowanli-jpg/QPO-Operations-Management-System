package com.machining.quote.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.machining.quote.entity.InventoryPendingStock;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface InventoryPendingStockMapper extends BaseMapper<InventoryPendingStock> {
    @Select("SELECT * FROM inventory_pending_stock WHERE inventory_id = #{inventoryId} AND pending_ref_no = #{pendingRefNo} LIMIT 1 FOR UPDATE")
    InventoryPendingStock selectByInventoryAndRefForUpdate(@Param("inventoryId") Integer inventoryId,
                                                           @Param("pendingRefNo") String pendingRefNo);

    @Select("SELECT * FROM inventory_pending_stock WHERE inventory_id = #{inventoryId} AND pending_qty > 0 ORDER BY id FOR UPDATE")
    List<InventoryPendingStock> listByInventoryForUpdate(@Param("inventoryId") Integer inventoryId);
}
