package com.machining.quote.controller;

import com.machining.quote.dto.SalesOrderSyncQueryReq;
import com.machining.quote.dto.SalesOrderSyncResp;
import com.machining.quote.integration.InboundCallbackCode;
import com.machining.quote.service.SalesOrderIntegrationSyncService;
import com.machining.quote.service.SystemInboundCallbackConfigService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestController
@RequestMapping("/api/integrations/sales-orders")
public class SalesOrderIntegrationController {
    private static final Logger log = LoggerFactory.getLogger(SalesOrderIntegrationController.class);
    private static final String CALLER = "EXTERNAL_SALES_ORDER_SYNC";
    private static final int DEFAULT_PAGE_SIZE = 100;
    private static final String INVALID_TOKEN_MESSAGE = "销售订单同步接口 token 无效";
    private static final String INVALID_PAGE_SIZE_MESSAGE = "pageSize 只允许 20、50、100";

    @Autowired
    private SalesOrderIntegrationSyncService salesOrderIntegrationSyncService;
    @Autowired
    private SystemInboundCallbackConfigService inboundCallbackConfigService;

    @GetMapping
    public ResponseEntity<SalesOrderSyncResp> listSalesOrders(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @ModelAttribute SalesOrderSyncQueryReq req) {
        long startedAt = System.nanoTime();
        SalesOrderSyncQueryReq query = req == null ? new SalesOrderSyncQueryReq() : req;
        if (!inboundCallbackConfigService.hasValidOpenApiBearerToken(InboundCallbackCode.SALES_ORDER_SYNC_QUERY, authorization)) {
            log.warn(
                    "Sales order sync query failed caller={} pageSize={} reason={} durationMs={}",
                    CALLER,
                    pageSizeForLog(query),
                    "authFailed",
                    durationMs(startedAt));
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(SalesOrderSyncResp.error(HttpStatus.UNAUTHORIZED.value(), INVALID_TOKEN_MESSAGE));
        }
        Integer requestedPageSize = query.getPageSize();
        if (requestedPageSize == null) {
            query.setPageSize(DEFAULT_PAGE_SIZE);
        } else if (!isAllowedPageSize(requestedPageSize)) {
            log.warn(
                    "Sales order sync query failed caller={} pageSize={} reason={} durationMs={}",
                    CALLER,
                    requestedPageSize,
                    "invalidPageSize",
                    durationMs(startedAt));
            return ResponseEntity.badRequest()
                    .body(SalesOrderSyncResp.error(HttpStatus.BAD_REQUEST.value(), INVALID_PAGE_SIZE_MESSAGE));
        }
        try {
            return ResponseEntity.ok(salesOrderIntegrationSyncService.query(query));
        } catch (IllegalArgumentException error) {
            return ResponseEntity.badRequest()
                    .body(SalesOrderSyncResp.error(HttpStatus.BAD_REQUEST.value(), error.getMessage()));
        }
    }

    @ExceptionHandler({BindException.class, MethodArgumentTypeMismatchException.class})
    public ResponseEntity<SalesOrderSyncResp> invalidQueryParameter(Exception error) {
        log.warn(
                "Sales order sync query failed caller={} pageSize={} reason={} durationMs={}",
                CALLER,
                null,
                "invalidQueryParameter",
                0);
        return ResponseEntity.badRequest()
                .body(SalesOrderSyncResp.error(HttpStatus.BAD_REQUEST.value(), INVALID_PAGE_SIZE_MESSAGE));
    }

    private boolean isAllowedPageSize(Integer pageSize) {
        return pageSize == 20 || pageSize == 50 || pageSize == 100;
    }

    private Integer pageSizeForLog(SalesOrderSyncQueryReq req) {
        return req == null || req.getPageSize() == null ? DEFAULT_PAGE_SIZE : req.getPageSize();
    }

    private long durationMs(long startedAt) {
        return Math.max(0, (System.nanoTime() - startedAt) / 1_000_000);
    }
}
