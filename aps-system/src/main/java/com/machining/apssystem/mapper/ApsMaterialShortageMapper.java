package com.machining.apssystem.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.machining.apssystem.dto.ApsMaterialShortageDTO;
import com.machining.apssystem.entity.ApsMaterialShortage;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface ApsMaterialShortageMapper extends BaseMapper<ApsMaterialShortage> {
    @Select("""
            <script>
            SELECT id, work_item_id, rfq_no, internal_project_no, customer_name, part_no, version,
                   material_name, material_type, shortage_weight_kg, source_task, status,
                   remark, created_at, updated_at
            FROM aps_material_shortage
            <where>
                <if test="status != null and status != ''">
                    status = #{status}
                </if>
            </where>
            ORDER BY id DESC
            LIMIT #{limit}
            </script>
            """)
    List<ApsMaterialShortageDTO> selectDtos(
            @Param("status") String status,
            @Param("limit") int limit);

    @Select("""
            SELECT id, work_item_id, rfq_no, internal_project_no, customer_name, part_no, version,
                   material_name, material_type, shortage_weight_kg, source_task, status,
                   remark, created_at, updated_at
            FROM aps_material_shortage
            WHERE work_item_id = #{workItemId}
            ORDER BY id
            """)
    List<ApsMaterialShortageDTO> selectDtosByWorkItemId(@Param("workItemId") Long workItemId);

    @Delete("""
            DELETE FROM aps_material_shortage
            WHERE work_item_id = #{workItemId}
            """)
    int deleteByWorkItemId(@Param("workItemId") Long workItemId);
}
