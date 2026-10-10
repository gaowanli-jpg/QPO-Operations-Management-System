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
}
