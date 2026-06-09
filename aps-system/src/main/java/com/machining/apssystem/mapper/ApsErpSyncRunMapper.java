package com.machining.apssystem.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.machining.apssystem.entity.ApsErpSyncRun;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface ApsErpSyncRunMapper extends BaseMapper<ApsErpSyncRun> {
    @Select("""
            INSERT INTO aps_erp_sync_run (
                source_system, sync_mode, request_after_ticket_id, request_limit, status
            ) VALUES (
                'ERP', 'PULL', #{afterTicketId}, #{pageSize}, 'RUNNING'
            )
            RETURNING id
            """)
    Long createRun(
            @Param("afterTicketId") Integer afterTicketId,
            @Param("pageSize") int pageSize);

    @Update("""
            UPDATE aps_erp_sync_run
            SET received_count = #{receivedCount},
                imported_count = #{importedCount},
                skipped_count = #{skippedCount},
                status = #{status},
                error_message = #{errorMessage},
                completed_at = CURRENT_TIMESTAMP
            WHERE id = #{id}
            """)
    int finishRun(
            @Param("id") Long id,
            @Param("receivedCount") int receivedCount,
            @Param("importedCount") int importedCount,
            @Param("skippedCount") int skippedCount,
            @Param("status") String status,
            @Param("errorMessage") String errorMessage);
}
