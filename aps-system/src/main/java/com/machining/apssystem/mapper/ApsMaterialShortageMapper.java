package com.machining.apssystem.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.machining.apssystem.dto.ApsMaterialShortageDTO;
import com.machining.apssystem.entity.ApsMaterialShortage;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface ApsMaterialShortageMapper extends BaseMapper<ApsMaterialShortage> {
    @Select("""
            <script>
            SELECT s.id, s.work_item_id, s.internal_project_no,
                   w.customer_order_no, w.customer_project_no,
                   s.customer_name, s.part_no, s.version,
                   s.material_name, s.material_type, s.shortage_weight_kg, s.source_task, s.status,
                   s.remark, s.created_at, s.updated_at
            FROM aps_material_shortage s
            JOIN aps_work_item w ON w.id = s.work_item_id
            <where>
                <if test="status != null and status != ''">
                    s.status = #{status}
                </if>
            </where>
            ORDER BY s.id DESC
            LIMIT #{limit}
            </script>
            """)
    List<ApsMaterialShortageDTO> selectDtos(
            @Param("status") String status,
            @Param("limit") int limit);

    @Select("""
            SELECT s.id, s.work_item_id, s.internal_project_no,
                   w.customer_order_no, w.customer_project_no,
                   s.customer_name, s.part_no, s.version,
                   s.material_name, s.material_type, s.shortage_weight_kg, s.source_task, s.status,
                   s.remark, s.created_at, s.updated_at
            FROM aps_material_shortage s
            JOIN aps_work_item w ON w.id = s.work_item_id
            WHERE s.work_item_id = #{workItemId}
            ORDER BY s.id
            """)
    List<ApsMaterialShortageDTO> selectDtosByWorkItemId(@Param("workItemId") Long workItemId);

    @Delete("""
            DELETE FROM aps_material_shortage
            WHERE work_item_id = #{workItemId}
            """)
    int deleteByWorkItemId(@Param("workItemId") Long workItemId);

    @Select("""
            SELECT id, work_item_id, internal_project_no, customer_name, part_no, version,
                   material_name, material_type, shortage_weight_kg, source_task, status,
                   remark, created_at, updated_at
            FROM aps_material_shortage
            WHERE work_item_id = #{workItemId}
            ORDER BY id
            LIMIT 1
            FOR UPDATE
            """)
    ApsMaterialShortage selectByWorkItemIdForUpdate(@Param("workItemId") Long workItemId);

    @Update("""
            UPDATE aps_material_shortage
            SET shortage_weight_kg = shortage_weight_kg + #{shortageWeightKg},
                status = #{status},
                updated_at = CURRENT_TIMESTAMP
            WHERE id = #{id}
            """)
    int addShortageWeight(
            @Param("id") Long id,
            @Param("shortageWeightKg") java.math.BigDecimal shortageWeightKg,
            @Param("status") String status);
}
