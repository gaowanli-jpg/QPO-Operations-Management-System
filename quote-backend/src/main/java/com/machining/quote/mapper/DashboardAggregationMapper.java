package com.machining.quote.mapper;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.toolkit.Constants;
import com.machining.quote.dto.dashboard.DashboardCustomerCountRow;
import com.machining.quote.dto.dashboard.DashboardStatusCountRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface DashboardAggregationMapper {
    @Select("""
            <script>
            SELECT
                rfq.status AS status,
                COUNT(*) AS count
            FROM order_rfq rfq
            ${ew.customSqlSegment}
            GROUP BY rfq.status
            </script>
            """)
    List<DashboardStatusCountRow> countRfqByStatus(@Param(Constants.WRAPPER) Wrapper<?> query);

    @Select("""
            <script>
            SELECT
                cm.id AS customer_id,
                COALESCE(NULLIF(cm.customer_code, ''), NULLIF(rfq.customer_code, '')) AS customer_code,
                COALESCE(NULLIF(cm.customer_name, ''), NULLIF(rfq.customer_name, ''), '未识别客户') AS customer_name,
                COALESCE(NULLIF(cm.customer_short_name, ''), NULLIF(rfq.customer_short_name, ''), NULLIF(rfq.customer_name, ''), '未识别客户') AS customer_short_name,
                rfq.status AS status,
                COUNT(*) AS count
            FROM order_rfq rfq
            LEFT JOIN customer_master cm ON cm.customer_name = rfq.customer_name
            ${ew.customSqlSegment}
            GROUP BY
                cm.id,
                COALESCE(NULLIF(cm.customer_code, ''), NULLIF(rfq.customer_code, '')),
                COALESCE(NULLIF(cm.customer_name, ''), NULLIF(rfq.customer_name, ''), '未识别客户'),
                COALESCE(NULLIF(cm.customer_short_name, ''), NULLIF(rfq.customer_short_name, ''), NULLIF(rfq.customer_name, ''), '未识别客户'),
                rfq.status
            </script>
            """)
    List<DashboardCustomerCountRow> countRfqByCustomerAndStatus(@Param(Constants.WRAPPER) Wrapper<?> query);

    @Select("""
            <script>
            SELECT
                COALESCE(so.customer_id, cm.id) AS customer_id,
                COALESCE(NULLIF(cm.customer_code, ''), NULLIF(so.customer_code, '')) AS customer_code,
                COALESCE(NULLIF(cm.customer_name, ''), NULLIF(so.customer_name, ''), '未识别客户') AS customer_name,
                COALESCE(NULLIF(cm.customer_short_name, ''), NULLIF(so.customer_short_name, ''), NULLIF(so.customer_name, ''), '未识别客户') AS customer_short_name,
                so.status AS status,
                COUNT(*) AS count
            FROM sales_order_header so
            LEFT JOIN customer_master cm ON cm.id = so.customer_id OR (so.customer_id IS NULL AND cm.customer_name = so.customer_name)
            ${ew.customSqlSegment}
            GROUP BY
                COALESCE(so.customer_id, cm.id),
                COALESCE(NULLIF(cm.customer_code, ''), NULLIF(so.customer_code, '')),
                COALESCE(NULLIF(cm.customer_name, ''), NULLIF(so.customer_name, ''), '未识别客户'),
                COALESCE(NULLIF(cm.customer_short_name, ''), NULLIF(so.customer_short_name, ''), NULLIF(so.customer_name, ''), '未识别客户'),
                so.status
            ORDER BY COUNT(*) DESC
            </script>
            """)
    List<DashboardCustomerCountRow> countSalesQuotedByCustomer(@Param(Constants.WRAPPER) Wrapper<?> query);

    @Select("""
            <script>
            SELECT COUNT(*)
            FROM order_delivery_ticket ticket
            ${ew.customSqlSegment}
            </script>
            """)
    long countDeliveredActive(@Param(Constants.WRAPPER) Wrapper<?> query);

    @Select("""
            <script>
            SELECT COUNT(*)
            FROM order_delivery_ticket ticket
            ${ew.customSqlSegment}
            </script>
            """)
    long countDeliveredNeedMaterial(@Param(Constants.WRAPPER) Wrapper<?> query);

    @Select("""
            <script>
            SELECT COUNT(*)
            FROM order_delivery_ticket ticket
            ${ew.customSqlSegment}
            </script>
            """)
    long countDeliveredNeedWms(@Param(Constants.WRAPPER) Wrapper<?> query);

    @Select("""
            <script>
            SELECT COUNT(*)
            FROM order_delivery_ticket ticket
            ${ew.customSqlSegment}
            </script>
            """)
    long countDeliveredMesCallbackPending(@Param(Constants.WRAPPER) Wrapper<?> query);

    @Select("""
            <script>
            SELECT COUNT(*)
            FROM order_delivery_ticket ticket
            ${ew.customSqlSegment}
            </script>
            """)
    long countDeliveredMesFailed(@Param(Constants.WRAPPER) Wrapper<?> query);

    @Select("""
            <script>
            SELECT COUNT(*)
            FROM order_delivery_ticket ticket
            ${ew.customSqlSegment}
            </script>
            """)
    long countDeliveredWmsFailed(@Param(Constants.WRAPPER) Wrapper<?> query);

    @Select("""
            <script>
            SELECT COUNT(*)
            FROM order_delivery_ticket_allocation alloc
            LEFT JOIN order_delivery_ticket ticket ON ticket.id = alloc.ticket_id
            ${ew.customSqlSegment}
            </script>
            """)
    long countLockedProductionTodo(@Param(Constants.WRAPPER) Wrapper<?> query);

    @Select("""
            <script>
            SELECT COUNT(*)
            FROM order_delivery_ticket_allocation alloc
            LEFT JOIN order_delivery_ticket ticket ON ticket.id = alloc.ticket_id
            ${ew.customSqlSegment}
            </script>
            """)
    long countLockedMesProductionFailed(@Param(Constants.WRAPPER) Wrapper<?> query);

    @Select("""
            <script>
            SELECT COUNT(*)
            FROM sales_order_header so
            ${ew.customSqlSegment}
            </script>
            """)
    long countSalesMatchTodo(@Param(Constants.WRAPPER) Wrapper<?> query);

    @Select("""
            <script>
            SELECT COUNT(*)
            FROM sales_order_header so
            ${ew.customSqlSegment}
            </script>
            """)
    long countU9Ready(@Param(Constants.WRAPPER) Wrapper<?> query);

    @Select("""
            <script>
            SELECT COUNT(*)
            FROM sales_order_header so
            ${ew.customSqlSegment}
            </script>
            """)
    long countU9Failed(@Param(Constants.WRAPPER) Wrapper<?> query);
}
