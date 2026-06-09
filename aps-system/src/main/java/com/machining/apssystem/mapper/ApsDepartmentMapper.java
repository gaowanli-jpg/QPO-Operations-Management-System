package com.machining.apssystem.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.machining.apssystem.entity.ApsDepartment;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.math.BigDecimal;

@Mapper
public interface ApsDepartmentMapper extends BaseMapper<ApsDepartment> {
    @Select("""
            SELECT id, department_name, department_type, department_status,
                   monthly_available_hours, occupied_hours, remaining_hours,
                   remark, created_at, updated_at
            FROM aps_department
            WHERE id = #{id}
            FOR UPDATE
            """)
    ApsDepartment selectByIdForUpdate(@Param("id") Long id);

    @Select("""
            SELECT COUNT(*)
            FROM aps_department_assignment
            WHERE department_id = #{departmentId} AND status = 'ASSIGNED'
            """)
    int countActiveAssignments(@Param("departmentId") Long departmentId);

    @Update("""
            UPDATE aps_department_assignment
            SET capacity_counted = FALSE
            WHERE department_id = #{departmentId} AND status = 'ASSIGNED' AND capacity_counted = TRUE
            """)
    int resetActiveAssignmentCapacityCounted(@Param("departmentId") Long departmentId);

    @Update("""
            UPDATE aps_department_assignment
            SET department_id = NULL
            WHERE department_id = #{departmentId} AND status <> 'ASSIGNED'
            """)
    int clearInactiveAssignmentDepartment(@Param("departmentId") Long departmentId);

    @Update("""
            UPDATE aps_department
            SET occupied_hours = occupied_hours + #{hours},
                updated_at = CURRENT_TIMESTAMP
            WHERE id = #{id}
            """)
    int addOccupiedHours(@Param("id") Long id, @Param("hours") BigDecimal hours);

    @Update("""
            UPDATE aps_department
            SET occupied_hours = GREATEST(occupied_hours - #{hours}, 0),
                updated_at = CURRENT_TIMESTAMP
            WHERE id = #{id}
            """)
    int subtractOccupiedHours(@Param("id") Long id, @Param("hours") BigDecimal hours);
}
