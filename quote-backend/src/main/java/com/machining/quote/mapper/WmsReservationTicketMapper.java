package com.machining.quote.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.machining.quote.entity.WmsReservationTicket;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface WmsReservationTicketMapper extends BaseMapper<WmsReservationTicket> {
    @Select("""
            SELECT * FROM wms_reservation_ticket
            WHERE ticket_id = #{ticketId}
              AND status IN ('RESERVED', 'SUBMITTING', 'RESULT_UNKNOWN', 'RESPONSE_RECEIVED', 'APPLY_FAILED')
            LIMIT 1
            """)
    WmsReservationTicket selectActiveByTicketId(@Param("ticketId") Integer ticketId);

    @Select("""
            SELECT * FROM wms_reservation_ticket
            WHERE allocation_id = #{allocationId}
              AND status IN ('RESERVED', 'SUBMITTING', 'RESULT_UNKNOWN', 'RESPONSE_RECEIVED', 'APPLY_FAILED')
            LIMIT 1
            """)
    WmsReservationTicket selectActiveByAllocationId(@Param("allocationId") Integer allocationId);

    @Select("""
            SELECT * FROM wms_reservation_ticket
            WHERE ticket_id = #{ticketId}
              AND allocation_id IS NULL
              AND status IN ('RESERVED', 'SUBMITTING', 'RESULT_UNKNOWN', 'RESPONSE_RECEIVED', 'APPLY_FAILED')
            LIMIT 1
            """)
    WmsReservationTicket selectActiveLegacyByTicketId(@Param("ticketId") Integer ticketId);

    @Select("""
            SELECT * FROM wms_reservation_ticket
            WHERE batch_id = #{batchId}
            ORDER BY sales_order_line_no ASC NULLS LAST, allocation_id ASC NULLS LAST, ticket_id ASC
            """)
    List<WmsReservationTicket> selectByBatchId(@Param("batchId") Long batchId);

    @Select("""
            <script>
            SELECT latest.id,
                   latest.batch_id,
                   latest.batch_item_id,
                   latest.ticket_id,
                   latest.allocation_id,
                   latest.sales_order_item_id,
                   latest.sales_order_line_no,
                   latest.product_code,
                   latest.reserve_count,
                   latest.stock_frozen_count,
                   latest.in_production_count,
                   latest.surplus_count,
                   latest.status,
                   latest.message,
                   latest.applied_at,
                   latest.created_at,
                   latest.updated_at
            FROM (
                SELECT reservation.*,
                       ROW_NUMBER() OVER (
                           PARTITION BY CASE
                               WHEN reservation.allocation_id IS NOT NULL
                                   THEN 'A:' || reservation.allocation_id::text
                               ELSE 'T:' || reservation.ticket_id::text
                           END
                           ORDER BY reservation.updated_at DESC NULLS LAST,
                                    reservation.created_at DESC NULLS LAST,
                                    reservation.id DESC
                       ) AS latest_rank
                FROM wms_reservation_ticket reservation
                WHERE (
                    <if test="allocationIds != null and allocationIds.size() > 0">
                        reservation.allocation_id IN
                        <foreach collection="allocationIds" item="allocationId" open="(" separator="," close=")">
                            #{allocationId}
                        </foreach>
                    </if>
                    <if test="legacyTicketIds != null and legacyTicketIds.size() > 0">
                        <if test="allocationIds != null and allocationIds.size() > 0">OR</if>
                        (reservation.allocation_id IS NULL AND reservation.ticket_id IN
                        <foreach collection="legacyTicketIds" item="ticketId" open="(" separator="," close=")">
                            #{ticketId}
                        </foreach>)
                    </if>
                )
            ) latest
            WHERE latest.latest_rank = 1
            </script>
            """)
    List<WmsReservationTicket> selectLatestForLinkedChange(
            @Param("allocationIds") List<Integer> allocationIds,
            @Param("legacyTicketIds") List<Integer> legacyTicketIds);

    @Select("""
            <script>
            SELECT * FROM wms_reservation_ticket
            WHERE id IN
            <foreach collection="reservationIds" item="reservationId" open="(" separator="," close=")">
                #{reservationId}
            </foreach>
            </script>
            """)
    List<WmsReservationTicket> selectLinkedChangeReservationsByIds(@Param("reservationIds") List<Long> reservationIds);
}
