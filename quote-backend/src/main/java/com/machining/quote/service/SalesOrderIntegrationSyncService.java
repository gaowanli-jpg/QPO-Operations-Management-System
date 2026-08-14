package com.machining.quote.service;

import com.machining.quote.dto.SalesOrderSyncQueryReq;
import com.machining.quote.dto.SalesOrderSyncResp;
import org.springframework.stereotype.Service;

@Service
public class SalesOrderIntegrationSyncService {
    public SalesOrderSyncResp query(SalesOrderSyncQueryReq req) {
        int pageSize = req == null || req.getPageSize() == null ? 100 : req.getPageSize();
        return SalesOrderSyncResp.empty(pageSize);
    }
}
