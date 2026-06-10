package com.machining.apssystem.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.machining.apssystem.dto.ApsWorkItemDTO;
import com.machining.apssystem.entity.ApsDepartmentTask;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface ApsDepartmentTaskMapper extends BaseMapper<ApsDepartmentTask> {
    @Update("""
            UPDATE aps_department_task
            SET task_status = 'RELEASED',
                released_at = CURRENT_TIMESTAMP,
                updated_at = CURRENT_TIMESTAMP
            WHERE work_item_id = #{workItemId} AND task_status = 'ACTIVE'
            """)
    int releaseActiveByWorkItemId(@Param("workItemId") Long workItemId);

    @Select("""
            <script>
            SELECT COUNT(*)
            FROM aps_department_task t
            JOIN aps_work_item w ON w.id = t.work_item_id
            <where>
                <if test="taskStatus != null and taskStatus != ''">
                    t.task_status = #{taskStatus}
                </if>
                <if test="departmentId != null">
                    AND t.department_id = #{departmentId}
                </if>
            </where>
            </script>
            """)
    long countDepartmentTaskPage(
            @Param("departmentId") Long departmentId,
            @Param("taskStatus") String taskStatus);

    @Select("""
            <script>
            SELECT w.id, w.erp_ticket_id, w.internal_project_no, w.customer_order_no, w.customer_project_no,
                   w.customer_name, w.part_no, w.version,
                   COALESCE(l.production_required_qty, w.qty) AS qty,
                   w.delivery_date, w.material_name, w.material_type, w.material_demand_weight_kg,
                   w.total_work_hours, w.process_route_text, w.process_route_signature,
                   w.part_stock_status, w.material_stock_status,
                   w.assignment_status, w.status,
                   t.department_id AS assigned_department_id,
                   t.department_name AS assigned_department_name,
                   t.assigned_at AS assigned_at,
                   t.task_status AS department_task_status
            FROM aps_department_task t
            JOIN aps_work_item w ON w.id = t.work_item_id
            LEFT JOIN aps_part_inventory_lock l ON l.work_item_id = w.id
            <where>
                <if test="taskStatus != null and taskStatus != ''">
                    t.task_status = #{taskStatus}
                </if>
                <if test="departmentId != null">
                    AND t.department_id = #{departmentId}
                </if>
            </where>
            ORDER BY t.assigned_at DESC, t.id DESC
            LIMIT #{limit} OFFSET #{offset}
            </script>
            """)
    List<ApsWorkItemDTO> selectDepartmentTaskPageDtos(
            @Param("departmentId") Long departmentId,
            @Param("taskStatus") String taskStatus,
            @Param("limit") int limit,
            @Param("offset") int offset);
}
