package com.machining.apssystem.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.machining.apssystem.dto.ApsPartInventoryDTO;
import com.machining.apssystem.entity.ApsPartInventory;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface ApsPartInventoryMapper extends BaseMapper<ApsPartInventory> {
    @Select("""
            SELECT customer_name
            FROM (
                SELECT customer_name
                FROM aps_work_item
                WHERE customer_name IS NOT NULL AND customer_name <> ''
                UNION
                SELECT customer_name
                FROM aps_part_inventory
                WHERE customer_name IS NOT NULL AND customer_name <> ''
            ) t
            ORDER BY customer_name
            """)
    List<String> selectCustomerNames();

    @Select("""
            <script>
            SELECT COUNT(*)
            FROM aps_part_inventory
            WHERE customer_name = #{customerName}
            <if test="keyword != null and keyword != ''">
              AND (UPPER(part_no) LIKE #{keyword} OR UPPER(version) LIKE #{keyword})
            </if>
            </script>
            """)
    long countPage(
            @Param("customerName") String customerName,
            @Param("keyword") String keyword);

    @Select("""
            <script>
            SELECT id, customer_name, part_no, version, in_stock_qty, in_production_qty,
                   locked_in_stock_qty, locked_in_production_qty,
                   available_in_stock_qty, available_in_production_qty,
                   remark, created_at, updated_at
            FROM aps_part_inventory
            WHERE customer_name = #{customerName}
            <if test="keyword != null and keyword != ''">
              AND (UPPER(part_no) LIKE #{keyword} OR UPPER(version) LIKE #{keyword})
            </if>
            ORDER BY part_no, version, id
            LIMIT #{limit} OFFSET #{offset}
            </script>
            """)
    List<ApsPartInventoryDTO> selectPageDtos(
            @Param("customerName") String customerName,
            @Param("keyword") String keyword,
            @Param("limit") int limit,
            @Param("offset") int offset);

    @Select("""
            <script>
            SELECT id, customer_name, part_no, version, in_stock_qty, in_production_qty,
                   locked_in_stock_qty, locked_in_production_qty,
                   available_in_stock_qty, available_in_production_qty,
                   remark, created_at, updated_at
            FROM aps_part_inventory
            <where>
              <if test="keyword != null and keyword != ''">
                UPPER(customer_name) LIKE #{keyword}
                OR UPPER(part_no) LIKE #{keyword}
                OR UPPER(version) LIKE #{keyword}
              </if>
            </where>
            ORDER BY customer_name, part_no, version, id
            LIMIT #{limit}
            </script>
            """)
    List<ApsPartInventoryDTO> selectListDtos(
            @Param("keyword") String keyword,
            @Param("limit") int limit);

    @Select("""
            INSERT INTO aps_part_inventory (
                customer_name, part_no, version, in_stock_qty, in_production_qty, remark
            ) VALUES (
                #{customerName}, #{partNo}, #{version}, #{inStockQty}, #{inProductionQty}, #{remark}
            )
            ON CONFLICT (customer_name, part_no, version) DO NOTHING
            RETURNING id
            """)
    Long insertReturningIdIfAbsent(ApsPartInventory inventory);

    @Select("""
            SELECT id, customer_name, part_no, version, in_stock_qty, in_production_qty,
                   locked_in_stock_qty, locked_in_production_qty,
                   available_in_stock_qty, available_in_production_qty,
                   remark, created_at, updated_at
            FROM aps_part_inventory
            WHERE id = #{id}
            FOR UPDATE
            """)
    ApsPartInventory selectByIdForUpdate(@Param("id") Long id);

    @Select("""
            SELECT id, customer_name, part_no, version, in_stock_qty, in_production_qty,
                   locked_in_stock_qty, locked_in_production_qty,
                   available_in_stock_qty, available_in_production_qty,
                   remark, created_at, updated_at
            FROM aps_part_inventory
            WHERE customer_name = #{customerName} AND part_no = #{partNo} AND version = #{version}
            FOR UPDATE
            """)
    ApsPartInventory selectByCustomerPartVersionForUpdate(
            @Param("customerName") String customerName,
            @Param("partNo") String partNo,
            @Param("version") String version);

    @Update("""
            UPDATE aps_part_inventory
            SET locked_in_stock_qty = locked_in_stock_qty + #{stockQty},
                locked_in_production_qty = locked_in_production_qty + #{productionQty},
                updated_at = CURRENT_TIMESTAMP
            WHERE id = #{id}
            """)
    int addLockedQuantities(
            @Param("id") Long id,
            @Param("stockQty") int stockQty,
            @Param("productionQty") int productionQty);

    @Update("""
            UPDATE aps_part_inventory
            SET customer_name = #{customerName},
                part_no = #{partNo},
                version = #{version},
                in_stock_qty = #{inStockQty},
                in_production_qty = #{inProductionQty},
                remark = #{remark},
                updated_at = CURRENT_TIMESTAMP
            WHERE id = #{id}
            """)
    int updateDetails(
            @Param("id") Long id,
            @Param("customerName") String customerName,
            @Param("partNo") String partNo,
            @Param("version") String version,
            @Param("inStockQty") int inStockQty,
            @Param("inProductionQty") int inProductionQty,
            @Param("remark") String remark);

    @Update("""
            UPDATE aps_part_inventory
            SET in_stock_qty = in_stock_qty + #{qty},
                updated_at = CURRENT_TIMESTAMP
            WHERE id = #{id}
            """)
    int addInStockQty(@Param("id") Long id, @Param("qty") int qty);

    @Update("""
            UPDATE aps_part_inventory
            SET in_stock_qty = #{qty},
                updated_at = CURRENT_TIMESTAMP
            WHERE id = #{id}
            """)
    int setInStockQty(@Param("id") Long id, @Param("qty") int qty);

    @Update("""
            UPDATE aps_part_inventory
            SET in_production_qty = (
                    SELECT COALESCE(SUM(pending_qty), 0)
                    FROM aps_part_inventory_pending_stock
                    WHERE part_inventory_id = #{id}
                ),
                updated_at = CURRENT_TIMESTAMP
            WHERE id = #{id}
            """)
    int refreshPendingSummary(@Param("id") Long id);

    @Update("""
            UPDATE aps_part_inventory
            SET locked_in_stock_qty = GREATEST(locked_in_stock_qty - #{stockQty}, 0),
                locked_in_production_qty = GREATEST(locked_in_production_qty - #{productionQty}, 0),
                updated_at = CURRENT_TIMESTAMP
            WHERE id = #{id}
            """)
    int releaseLockedQuantities(
            @Param("id") Long id,
            @Param("stockQty") int stockQty,
            @Param("productionQty") int productionQty);
}
