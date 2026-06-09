package com.machining.apssystem.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.machining.apssystem.dto.ApsWorkItemDTO;
import com.machining.apssystem.dto.WorkItemSummaryDTO;
import com.machining.apssystem.entity.ApsWorkItem;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface ApsWorkItemMapper extends BaseMapper<ApsWorkItem> {
    @Select("""
            <script>
            SELECT w.id, w.erp_ticket_id, w.internal_project_no, w.customer_order_no, w.customer_project_no,
                   w.customer_name, w.part_no, w.version,
                   COALESCE(l.production_required_qty, w.qty) AS qty,
                   w.delivery_date, w.material_name, w.material_type, w.material_demand_weight_kg,
                   w.total_work_hours, w.part_stock_status, w.material_stock_status,
                   w.assignment_status, w.status, w.assigned_department_id, w.assigned_department_name, w.assigned_at
            FROM aps_work_item w
            LEFT JOIN aps_part_inventory_lock l ON l.work_item_id = w.id
            <where>
                <if test="assignmentStatus != null and assignmentStatus != ''">
                    w.assignment_status = #{assignmentStatus}
                </if>
            </where>
            ORDER BY w.id DESC
            LIMIT #{limit}
            </script>
            """)
    List<ApsWorkItemDTO> selectPmcTaskDtos(
            @Param("assignmentStatus") String assignmentStatus,
            @Param("limit") int limit);

    @Select("""
            <script>
            SELECT COUNT(*)
            FROM aps_work_item w
            <where>
                <if test="assignmentStatus != null and assignmentStatus != ''">
                    w.assignment_status = #{assignmentStatus}
                </if>
            </where>
            </script>
            """)
    long countPmcTaskPage(@Param("assignmentStatus") String assignmentStatus);

    @Select("""
            <script>
            SELECT w.id, w.erp_ticket_id, w.internal_project_no, w.customer_order_no, w.customer_project_no,
                   w.customer_name, w.part_no, w.version,
                   COALESCE(l.production_required_qty, w.qty) AS qty,
                   w.delivery_date, w.material_name, w.material_type, w.material_demand_weight_kg,
                   w.total_work_hours, w.part_stock_status, w.material_stock_status,
                   w.assignment_status, w.status, w.assigned_department_id, w.assigned_department_name, w.assigned_at
            FROM aps_work_item w
            LEFT JOIN aps_part_inventory_lock l ON l.work_item_id = w.id
            <where>
                <if test="assignmentStatus != null and assignmentStatus != ''">
                    w.assignment_status = #{assignmentStatus}
                </if>
            </where>
            ORDER BY w.id DESC
            LIMIT #{limit} OFFSET #{offset}
            </script>
            """)
    List<ApsWorkItemDTO> selectPmcTaskPageDtos(
            @Param("assignmentStatus") String assignmentStatus,
            @Param("limit") int limit,
            @Param("offset") int offset);

    @Select("""
            SELECT id, erp_ticket_id, internal_project_no, customer_order_no, customer_project_no,
                   customer_name, part_no, version,
                   qty, delivery_date, material_name, material_type, material_demand_weight_kg,
                   total_work_hours, part_stock_status, material_stock_status, assignment_status, status
            FROM aps_work_item
            ORDER BY id DESC
            LIMIT #{limit}
            """)
    List<WorkItemSummaryDTO> selectRecentSummaryDtos(@Param("limit") int limit);

    @Select("""
            SELECT id, erp_ticket_id, internal_project_no, customer_order_no, customer_project_no,
                   customer_name, part_no, version,
                   qty, delivery_date, material_name, material_type, material_demand_weight_kg,
                   total_work_hours, part_stock_status, material_stock_status, assignment_status, status
            FROM aps_work_item
            WHERE id = #{id}
            """)
    WorkItemSummaryDTO selectSummaryDtoById(@Param("id") Long id);

    @Select("SELECT COUNT(*) FROM aps_work_item WHERE erp_ticket_id = #{erpTicketId}")
    int countByErpTicketId(@Param("erpTicketId") Integer erpTicketId);

    @Select("SELECT MAX(erp_ticket_id) FROM aps_work_item")
    Integer maxSyncedTicketId();

    @Select("""
            INSERT INTO aps_work_item (
                erp_sync_run_id, erp_ticket_id, erp_rfq_id, erp_item_id, erp_process_sheet_id,
                process_snapshot_source, customer_name, order_status, internal_project_no,
                customer_order_no, customer_project_no, project_no, order_type, item_type, part_no, version, description, qty, source_item_qty,
                unit, material_name, material_type, material_gross_weight_kg, material_demand_weight_kg,
                part_net_weight_kg, surface_treatment, lead_time, quote_lead_time, internal_target_lead_time,
                start_date, delivery_date, remark, arrange_type, drawing_url, drawing_view_url,
                material_data, source_payload, total_work_hours
            ) VALUES (
                #{erpSyncRunId}, #{erpTicketId}, #{erpRfqId}, #{erpItemId}, #{erpProcessSheetId},
                #{processSnapshotSource}, #{customerName}, #{orderStatus}, #{internalProjectNo},
                #{customerOrderNo}, #{customerProjectNo}, #{projectNo}, #{orderType}, #{itemType}, #{partNo}, #{version}, #{description}, #{qty}, #{sourceItemQty},
                #{unit}, #{materialName}, #{materialType}, #{materialGrossWeightKg}, #{materialDemandWeightKg},
                #{partNetWeightKg}, #{surfaceTreatment}, #{leadTime}, #{quoteLeadTime}, #{internalTargetLeadTime},
                #{startDate}, #{deliveryDate}, #{remark}, #{arrangeType}, #{drawingUrl}, #{drawingViewUrl},
                CAST(#{materialData,jdbcType=VARCHAR} AS jsonb), CAST(#{sourcePayload,jdbcType=VARCHAR} AS jsonb),
                #{totalWorkHours}
            )
            ON CONFLICT (erp_ticket_id) DO NOTHING
            RETURNING id
            """)
    Long insertFromErp(ApsWorkItem item);

    @Select("""
            SELECT w.id, w.erp_ticket_id, w.internal_project_no, w.customer_order_no, w.customer_project_no,
                   w.customer_name, w.part_no, w.version,
                   w.description, COALESCE(l.production_required_qty, w.qty) AS qty, w.unit,
                   w.delivery_date, w.drawing_url, w.drawing_view_url,
                   w.material_name, w.material_type, w.material_demand_weight_kg, w.total_work_hours,
                   w.part_stock_status, w.material_stock_status, w.assignment_status, w.status,
                   w.assigned_department_id, w.assigned_department_name, w.assigned_at,
                   w.created_at, w.updated_at
            FROM aps_work_item w
            LEFT JOIN aps_part_inventory_lock l ON l.work_item_id = w.id
            WHERE w.id = #{id}
            """)
    ApsWorkItemDTO selectDetailDtoById(@Param("id") Long id);

    @Select("""
            SELECT w.id, w.erp_ticket_id, w.internal_project_no, w.customer_order_no, w.customer_project_no,
                   w.customer_name, w.part_no, w.version,
                   w.description, COALESCE(l.production_required_qty, w.qty) AS qty, w.unit,
                   w.delivery_date, w.drawing_url, w.drawing_view_url,
                   w.material_name, w.material_type, w.material_demand_weight_kg, w.total_work_hours,
                   w.part_stock_status, w.material_stock_status, w.assignment_status, w.status,
                   w.assigned_department_id, w.assigned_department_name, w.assigned_at,
                   w.created_at, w.updated_at
            FROM aps_work_item w
            LEFT JOIN aps_part_inventory_lock l ON l.work_item_id = w.id
            WHERE w.id = #{id}
            FOR UPDATE OF w
            """)
    ApsWorkItemDTO selectDetailDtoByIdForUpdate(@Param("id") Long id);

    @Update("""
            UPDATE aps_work_item
            SET part_stock_status = #{partStockStatus},
                material_stock_status = #{materialStockStatus},
                updated_at = CURRENT_TIMESTAMP
            WHERE id = #{id}
            """)
    int updateStockStatuses(
            @Param("id") Long id,
            @Param("partStockStatus") String partStockStatus,
            @Param("materialStockStatus") String materialStockStatus);

    @Update("""
            UPDATE aps_work_item
            SET part_stock_status = #{partStockStatus},
                updated_at = CURRENT_TIMESTAMP
            WHERE id = #{id}
            """)
    int updatePartStockStatus(
            @Param("id") Long id,
            @Param("partStockStatus") String partStockStatus);

    @Update("""
            UPDATE aps_work_item
            SET material_stock_status = #{materialStockStatus},
                updated_at = CURRENT_TIMESTAMP
            WHERE id = #{id}
            """)
    int updateMaterialStockStatus(
            @Param("id") Long id,
            @Param("materialStockStatus") String materialStockStatus);

    @Update("""
            UPDATE aps_work_item
            SET assigned_department_id = #{departmentId},
                assigned_department_name = #{departmentName},
                assigned_at = CURRENT_TIMESTAMP,
                assignment_status = 'ASSIGNED',
                status = 'ASSIGNED',
                updated_at = CURRENT_TIMESTAMP
            WHERE id = #{id}
            """)
    int markAssigned(
            @Param("id") Long id,
            @Param("departmentId") Long departmentId,
            @Param("departmentName") String departmentName);

    @Update("""
            UPDATE aps_work_item
            SET assigned_department_id = NULL,
                assigned_department_name = NULL,
                assigned_at = NULL,
                assignment_status = 'PENDING',
                status = 'PENDING_PMC_ASSIGNMENT',
                updated_at = CURRENT_TIMESTAMP
            WHERE id = #{id}
            """)
    int markPendingAssignment(@Param("id") Long id);
}
