package com.machining.quote.mapper;

import com.machining.quote.dto.SalesOrderSyncItemRow;
import com.machining.quote.dto.SalesOrderSyncOrderRow;
import com.machining.quote.dto.SalesOrderSyncQueryReq;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.SelectProvider;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface SalesOrderIntegrationMapper {
    @SelectProvider(type = SalesOrderIntegrationSqlProvider.class, method = "selectOrderPage")
    List<SalesOrderSyncOrderRow> selectOrderPage(
            @Param("req") SalesOrderSyncQueryReq req,
            @Param("cursorChangedAt") LocalDateTime cursorChangedAt,
            @Param("cursorSalesOrderId") Long cursorSalesOrderId,
            @Param("limit") int limit);

    @SelectProvider(type = SalesOrderIntegrationSqlProvider.class, method = "selectItemsByOrderIds")
    List<SalesOrderSyncItemRow> selectItemsByOrderIds(
            @Param("orderIds") List<Long> orderIds,
            @Param("req") SalesOrderSyncQueryReq req);
}
