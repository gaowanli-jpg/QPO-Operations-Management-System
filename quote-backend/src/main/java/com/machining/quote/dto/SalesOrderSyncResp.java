package com.machining.quote.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

@Data
@JsonInclude(JsonInclude.Include.ALWAYS)
public class SalesOrderSyncResp {
    private boolean success;
    private int code;
    private String message;
    private SalesOrderSyncPageData data;

    public static SalesOrderSyncResp empty(int pageSize) {
        SalesOrderSyncPageData data = new SalesOrderSyncPageData();
        data.setPageSize(pageSize);
        data.setHasMore(false);
        data.setNextCursor(null);

        SalesOrderSyncResp resp = new SalesOrderSyncResp();
        resp.setSuccess(true);
        resp.setCode(0);
        resp.setMessage("OK");
        resp.setData(data);
        return resp;
    }

    public static SalesOrderSyncResp error(int code, String message) {
        SalesOrderSyncResp resp = new SalesOrderSyncResp();
        resp.setSuccess(false);
        resp.setCode(code);
        resp.setMessage(message);
        return resp;
    }
}
