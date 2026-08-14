package com.machining.quote.controller;

import com.machining.quote.dto.SalesOrderSyncQueryReq;
import com.machining.quote.dto.SalesOrderSyncResp;
import com.machining.quote.service.SalesOrderIntegrationSyncService;
import com.machining.quote.service.SystemInboundCallbackConfigService;
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
        if (!inboundCallbackConfigService.hasValidOpenApiBearerToken(authorization)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(SalesOrderSyncResp.error(HttpStatus.UNAUTHORIZED.value(), INVALID_TOKEN_MESSAGE));
        }
        SalesOrderSyncQueryReq query = req == null ? new SalesOrderSyncQueryReq() : req;
        Integer requestedPageSize = query.getPageSize();
        if (requestedPageSize == null) {
            query.setPageSize(DEFAULT_PAGE_SIZE);
        } else if (!isAllowedPageSize(requestedPageSize)) {
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
        return ResponseEntity.badRequest()
                .body(SalesOrderSyncResp.error(HttpStatus.BAD_REQUEST.value(), INVALID_PAGE_SIZE_MESSAGE));
    }

    private boolean isAllowedPageSize(Integer pageSize) {
        return pageSize == 20 || pageSize == 50 || pageSize == 100;
    }
}
