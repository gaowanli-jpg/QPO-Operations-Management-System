package com.machining.apssystem.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.machining.apssystem.dto.ApsDepartmentAssignmentDTO;
import com.machining.apssystem.entity.ApsDepartmentAssignment;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface ApsDepartmentAssignmentMapper extends BaseMapper<ApsDepartmentAssignment> {
    @Select("""
            SELECT id, work_item_id, department_id, department_name, required_hours,
                   status, assigned_by, assigned_at, released_at, remark, capacity_counted
            FROM aps_department_assignment
            WHERE work_item_id = #{workItemId}
            ORDER BY id DESC
            """)
    List<ApsDepartmentAssignmentDTO> selectDtosByWorkItemId(@Param("workItemId") Long workItemId);

    @Select("""
            SELECT id, work_item_id, department_id, department_name, required_hours,
                   status, assigned_by, assigned_at, released_at, remark, capacity_counted
            FROM aps_department_assignment
            WHERE work_item_id = #{workItemId} AND status = 'ASSIGNED'
            ORDER BY id DESC
            LIMIT 1
            FOR UPDATE
            """)
    ApsDepartmentAssignment selectActiveByWorkItemIdForUpdate(@Param("workItemId") Long workItemId);

    @Update("""
            UPDATE aps_department_assignment
            SET status = 'RELEASED',
                released_at = CURRENT_TIMESTAMP,
                capacity_counted = FALSE
            WHERE id = #{id}
            """)
    int releaseById(@Param("id") Long id);
}
