package com.machining.quote.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.machining.quote.entity.SalesOrderItem;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface SalesOrderItemMapper extends BaseMapper<SalesOrderItem> {
    @Select("SELECT * FROM sales_order_item WHERE id = #{id} FOR UPDATE")
    SalesOrderItem selectByIdForUpdate(@Param("id") Long id);

    @Select("SELECT * FROM sales_order_item WHERE source_ticket_id = #{ticketId} FOR UPDATE")
    SalesOrderItem selectBySourceTicketIdForUpdate(@Param("ticketId") Integer ticketId);

    @Select("SELECT * FROM sales_order_item WHERE source_ticket_id = #{ticketId} ORDER BY line_no ASC, id ASC LIMIT 1")
    SalesOrderItem selectBySourceTicketId(@Param("ticketId") Integer ticketId);

    @Select("SELECT * FROM sales_order_item WHERE allocation_id = #{allocationId} FOR UPDATE")
    SalesOrderItem selectByAllocationIdForUpdate(@Param("allocationId") Long allocationId);

    @Select("SELECT * FROM sales_order_item WHERE allocation_id = #{allocationId} LIMIT 1")
    SalesOrderItem selectByAllocationId(@Param("allocationId") Long allocationId);

    @Select("SELECT * FROM sales_order_item WHERE sales_order_id = #{salesOrderId} ORDER BY line_no ASC, id ASC FOR UPDATE")
    List<SalesOrderItem> selectBySalesOrderIdForUpdate(@Param("salesOrderId") Long salesOrderId);

    @Update("""
            UPDATE sales_order_item
            SET status = #{targetStatus}, updated_at = #{updatedAt}
            WHERE sales_order_id = #{salesOrderId}
              AND status IS DISTINCT FROM #{targetStatus}
            """)
    int markOrderItemsStatus(@Param("salesOrderId") Long salesOrderId,
                             @Param("targetStatus") String targetStatus,
                             @Param("updatedAt") LocalDateTime updatedAt);

    @Update("""
            UPDATE sales_order_item
            SET status = #{targetStatus}, updated_at = #{updatedAt}
            WHERE sales_order_id = #{salesOrderId}
              AND status = #{fromStatus}
            """)
    int restoreOrderItemsStatus(@Param("salesOrderId") Long salesOrderId,
                                @Param("fromStatus") String fromStatus,
                                @Param("targetStatus") String targetStatus,
                                @Param("updatedAt") LocalDateTime updatedAt);

    @Update("""
            <script>
            UPDATE sales_order_item
            SET u9_change_excluded = TRUE,
                u9_change_excluded_at = #{updatedAt},
                u9_change_excluded_by = #{operatorId},
                u9_change_excluded_reason = #{reason},
                u9_change_restored_at = NULL,
                u9_change_restored_by = NULL,
                u9_change_restore_reason = NULL,
                updated_at = #{updatedAt}
            WHERE sales_order_id = #{salesOrderId}
              AND COALESCE(u9_change_excluded, FALSE) = FALSE
              AND id IN
              <foreach collection="itemIds" item="itemId" open="(" separator="," close=")">
                #{itemId}
              </foreach>
            </script>
            """)
    int markItemsU9ChangeExcluded(@Param("salesOrderId") Long salesOrderId,
                                  @Param("itemIds") List<Long> itemIds,
                                  @Param("reason") String reason,
                                  @Param("operatorId") Long operatorId,
                                  @Param("updatedAt") LocalDateTime updatedAt);

    @Update("""
            <script>
            UPDATE sales_order_item
            SET linked_discarded = TRUE,
                linked_u9_change_excluded =
                <choose>
                    <when test="u9OwnedItemIds != null and u9OwnedItemIds.size() &gt; 0">
                        CASE WHEN id IN
                        <foreach collection="u9OwnedItemIds" item="itemId" open="(" separator="," close=")">
                            #{itemId}
                        </foreach>
                        THEN TRUE ELSE FALSE END
                    </when>
                    <otherwise>FALSE</otherwise>
                </choose>,
                linked_discarded_at = #{updatedAt},
                linked_discarded_by = #{operatorId},
                linked_discard_reason = #{reason},
                linked_restored_at = NULL,
                linked_restored_by = NULL,
                linked_restore_reason = NULL,
                updated_at = #{updatedAt}
            WHERE sales_order_id = #{salesOrderId}
              AND COALESCE(linked_discarded, FALSE) = FALSE
              AND id IN
              <foreach collection="itemIds" item="itemId" open="(" separator="," close=")">
                #{itemId}
              </foreach>
            </script>
            """)
    int markItemsLinkedDiscarded(@Param("salesOrderId") Long salesOrderId,
                                 @Param("itemIds") List<Long> itemIds,
                                 @Param("u9OwnedItemIds") List<Long> u9OwnedItemIds,
                                 @Param("reason") String reason,
                                 @Param("operatorId") Long operatorId,
                                 @Param("updatedAt") LocalDateTime updatedAt);

    @Update("""
            <script>
            UPDATE sales_order_item
            SET u9_change_excluded = FALSE,
                u9_change_restored_at = #{updatedAt},
                u9_change_restored_by = #{operatorId},
                u9_change_restore_reason = #{reason},
                updated_at = #{updatedAt}
            WHERE sales_order_id = #{salesOrderId}
              AND COALESCE(u9_change_excluded, FALSE) = TRUE
              AND id IN
              <foreach collection="itemIds" item="itemId" open="(" separator="," close=")">
                #{itemId}
              </foreach>
            </script>
            """)
    int restoreItemsU9ChangeExcluded(@Param("salesOrderId") Long salesOrderId,
                                     @Param("itemIds") List<Long> itemIds,
                                     @Param("reason") String reason,
                                     @Param("operatorId") Long operatorId,
                                     @Param("updatedAt") LocalDateTime updatedAt);

    @Update("""
            <script>
            UPDATE sales_order_item
            SET linked_discarded = FALSE,
                linked_u9_change_excluded = FALSE,
                linked_restored_at = #{updatedAt},
                linked_restored_by = #{operatorId},
                linked_restore_reason = #{reason},
                updated_at = #{updatedAt}
            WHERE sales_order_id = #{salesOrderId}
              AND linked_discarded = TRUE
              AND id IN
              <foreach collection="itemIds" item="itemId" open="(" separator="," close=")">
                #{itemId}
              </foreach>
            </script>
            """)
    int restoreItemsLinkedDiscarded(@Param("salesOrderId") Long salesOrderId,
                                    @Param("itemIds") List<Long> itemIds,
                                    @Param("reason") String reason,
                                    @Param("operatorId") Long operatorId,
                                    @Param("updatedAt") LocalDateTime updatedAt);
}
