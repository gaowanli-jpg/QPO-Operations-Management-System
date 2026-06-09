package com.machining.apssystem.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.machining.apssystem.dto.ApsMaterialInventoryDTO;
import com.machining.apssystem.entity.ApsMaterialInventory;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.math.BigDecimal;
import java.util.List;

@Mapper
public interface ApsMaterialInventoryMapper extends BaseMapper<ApsMaterialInventory> {
    @Select("""
            <script>
            SELECT id, material_name, material_type, stock_weight_kg, locked_weight_kg,
                   available_weight_kg, safety_stock_weight_kg, unit, remark, created_at, updated_at
            FROM aps_material_inventory
            <where>
              <if test="keyword != null and keyword != ''">
                UPPER(material_name) LIKE #{keyword}
                OR UPPER(material_type) LIKE #{keyword}
              </if>
            </where>
            ORDER BY material_name, material_type, id
            LIMIT #{limit}
            </script>
            """)
    List<ApsMaterialInventoryDTO> selectDtos(
            @Param("keyword") String keyword,
            @Param("limit") int limit);

    @Select("""
            INSERT INTO aps_material_inventory (
                material_name, material_type, stock_weight_kg, safety_stock_weight_kg, unit, remark
            ) VALUES (
                #{materialName}, #{materialType}, #{stockWeightKg}, #{safetyStockWeightKg}, #{unit}, #{remark}
            )
            ON CONFLICT (material_name, material_type) DO NOTHING
            RETURNING id
            """)
    Long insertReturningIdIfAbsent(ApsMaterialInventory inventory);

    @Select("""
            SELECT id, material_name, material_type, stock_weight_kg, locked_weight_kg,
                   available_weight_kg, safety_stock_weight_kg, unit, remark, created_at, updated_at
            FROM aps_material_inventory
            WHERE id = #{id}
            FOR UPDATE
            """)
    ApsMaterialInventory selectByIdForUpdate(@Param("id") Long id);

    @Select("""
            SELECT id, material_name, material_type, stock_weight_kg, locked_weight_kg,
                   available_weight_kg, safety_stock_weight_kg, unit, remark, created_at, updated_at
            FROM aps_material_inventory
            WHERE material_name = #{materialName} AND material_type = #{materialType}
            FOR UPDATE
            """)
    ApsMaterialInventory selectByNameTypeForUpdate(
            @Param("materialName") String materialName,
            @Param("materialType") String materialType);

    @Update("""
            UPDATE aps_material_inventory
            SET locked_weight_kg = locked_weight_kg + #{weight},
                updated_at = CURRENT_TIMESTAMP
            WHERE id = #{id}
            """)
    int addLockedWeight(@Param("id") Long id, @Param("weight") BigDecimal weight);

    @Update("""
            UPDATE aps_material_inventory
            SET material_name = #{materialName},
                material_type = #{materialType},
                stock_weight_kg = #{stockWeightKg},
                safety_stock_weight_kg = #{safetyStockWeightKg},
                unit = #{unit},
                remark = #{remark},
                updated_at = CURRENT_TIMESTAMP
            WHERE id = #{id}
            """)
    int updateDetails(
            @Param("id") Long id,
            @Param("materialName") String materialName,
            @Param("materialType") String materialType,
            @Param("stockWeightKg") BigDecimal stockWeightKg,
            @Param("safetyStockWeightKg") BigDecimal safetyStockWeightKg,
            @Param("unit") String unit,
            @Param("remark") String remark);

    @Update("""
            UPDATE aps_material_inventory
            SET locked_weight_kg = GREATEST(locked_weight_kg - #{weight}, 0),
                updated_at = CURRENT_TIMESTAMP
            WHERE id = #{id}
            """)
    int releaseLockedWeight(@Param("id") Long id, @Param("weight") BigDecimal weight);
}
