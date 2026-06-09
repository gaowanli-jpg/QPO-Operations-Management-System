package com.machining.apssystem.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.machining.apssystem.dto.ApsWorkItemProcessDTO;
import com.machining.apssystem.entity.ApsWorkItemProcess;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.math.BigDecimal;
import java.util.List;

@Mapper
public interface ApsWorkItemProcessMapper extends BaseMapper<ApsWorkItemProcess> {
    @Insert("""
            INSERT INTO aps_work_item_process (
                work_item_id, sort_no, process_name, calc_type, source_type,
                input_val, work_hours, unit_rate, line_cost, raw_row
            ) VALUES (
                #{workItemId}, #{sortNo}, #{processName}, #{calcType}, #{sourceType},
                #{inputVal}, #{workHours}, #{unitRate}, #{lineCost}, CAST(#{rawRow,jdbcType=VARCHAR} AS jsonb)
            )
            """)
    int insertFromErp(ApsWorkItemProcess process);

    @Select("""
            SELECT id, work_item_id, sort_no, process_name, calc_type, source_type,
                   input_val, work_hours, unit_rate, line_cost, raw_row::text AS raw_row, created_at
            FROM aps_work_item_process
            WHERE work_item_id = #{workItemId}
            ORDER BY sort_no NULLS LAST, id
            """)
    List<ApsWorkItemProcessDTO> selectDtosByWorkItemId(@Param("workItemId") Long workItemId);

    @Select("""
            SELECT COALESCE(SUM(work_hours), 0)
            FROM aps_work_item_process
            WHERE work_item_id = #{workItemId}
            """)
    BigDecimal sumWorkHoursByWorkItemId(@Param("workItemId") Long workItemId);
}
