package com.machining.quote.dto;

import lombok.Data;

import java.time.LocalDate;

@Data
public class DeliveredItemQueryReq {
    private Integer page;
    private Integer pageSize;
    private String flowScope;
    private String flowStage;
    private String customerCode;
    private String customerName;
    private String rfqNo;
    private String partNo;
    private String version;
    private String materialNo;
    private String internalProjectNo;
    private String customerOrderNo;
    private String customerProjectNo;
    private String mesSyncStatus;
    private String wmsAllocateStatus;
    private LocalDate dateFrom;
    private LocalDate dateTo;
    private Boolean discarded;
    /** 排序列，只接受服务端白名单值（deliveryDate / createdAt），其它值按默认顺序处理。 */
    private String sortBy;
    /** 排序方向，ASC 正序，其余（含空）按 DESC 倒序。 */
    private String sortOrder;
}
