package com.machining.apssystem.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.machining.apssystem.dto.ApsPartProductionRequirementDTO;
import com.machining.apssystem.entity.ApsProductionRequirement;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface ApsProductionRequirementMapper extends BaseMapper<ApsProductionRequirement> {
    @Select("""
            <script>
            SELECT r.id, r.work_item_id, r.customer_name, r.part_no, r.version,
                   r.required_qty, r.status, r.remark, r.created_at, r.updated_at,
                   w.rfq_no, w.internal_project_no, w.delivery_date
            FROM aps_production_requirement r
            JOIN aps_work_item w ON w.id = r.work_item_id
            <where>
                <if test="status != null and status != ''">
                    r.status = #{status}
                </if>
            </where>
            ORDER BY r.id DESC
            LIMIT #{limit}
            </script>
            """)
    List<ApsPartProductionRequirementDTO> selectDtos(
            @Param("status") String status,
            @Param("limit") int limit);
}
