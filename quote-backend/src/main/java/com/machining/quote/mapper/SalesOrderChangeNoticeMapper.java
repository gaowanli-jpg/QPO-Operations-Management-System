package com.machining.quote.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.machining.quote.entity.SalesOrderChangeNotice;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface SalesOrderChangeNoticeMapper extends BaseMapper<SalesOrderChangeNotice> {
    @Select("SELECT * FROM sales_order_change_notice WHERE notice_id = #{noticeId}")
    SalesOrderChangeNotice selectByNoticeId(@Param("noticeId") String noticeId);

    @Select("""
            <script>
            SELECT * FROM sales_order_change_notice
            WHERE sales_order_id = #{salesOrderId}
              AND sales_order_item_id IN
              <foreach collection="salesOrderItemIds" item="itemId" open="(" separator="," close=")">
                  #{itemId}
              </foreach>
              AND allocation_id IN
              <foreach collection="allocationIds" item="allocationId" open="(" separator="," close=")">
                  #{allocationId}
              </foreach>
              AND change_type IN ('DISCARD', 'RESTORE')
            ORDER BY created_at ASC, id ASC
            </script>
            """)
    List<SalesOrderChangeNotice> selectLinkedLifecycleEvents(
            @Param("salesOrderId") Long salesOrderId,
            @Param("salesOrderItemIds") List<Long> salesOrderItemIds,
            @Param("allocationIds") List<Long> allocationIds);

    @Select("""
            SELECT * FROM sales_order_change_notice
            WHERE status = 'PENDING'
              AND is_hidden = FALSE
              AND auto_dispatch_epoch = #{epoch}
            ORDER BY created_at ASC, id ASC
            LIMIT #{limit}
            FOR UPDATE SKIP LOCKED
            """)
    List<SalesOrderChangeNotice> selectAutoPendingForUpdate(@Param("epoch") Long epoch, @Param("limit") int limit);

    @Select("""
            <script>
            SELECT * FROM sales_order_change_notice
            WHERE notice_id IN
            <foreach collection="noticeIds" item="noticeId" open="(" separator="," close=")">
                #{noticeId}
            </foreach>
            ORDER BY id ASC
            FOR UPDATE
            </script>
            """)
    List<SalesOrderChangeNotice> selectByNoticeIdsForUpdate(@Param("noticeIds") List<String> noticeIds);

    @Update("""
            <script>
            UPDATE sales_order_change_notice
            SET status = 'SENDING', attempt_count = attempt_count + 1,
                last_attempt_at = CURRENT_TIMESTAMP, last_error = NULL, updated_at = CURRENT_TIMESTAMP
            WHERE notice_id IN
            <foreach collection="noticeIds" item="noticeId" open="(" separator="," close=")">
                #{noticeId}
            </foreach>
              AND status IN ('PENDING', 'FAILED', 'RESULT_UNKNOWN')
              AND is_hidden = FALSE
            </script>
            """)
    int claimManual(@Param("noticeIds") List<String> noticeIds);

    @Update("""
            <script>
            UPDATE sales_order_change_notice
            SET status = 'SENDING', attempt_count = attempt_count + 1,
                last_attempt_at = CURRENT_TIMESTAMP, last_error = NULL, updated_at = CURRENT_TIMESTAMP
            WHERE id IN
            <foreach collection="ids" item="id" open="(" separator="," close=")">
                #{id}
            </foreach>
              AND status = 'PENDING'
              AND is_hidden = FALSE
              AND auto_dispatch_epoch = #{epoch}
            </script>
            """)
    int claimAuto(@Param("ids") List<Long> ids, @Param("epoch") Long epoch);
}
