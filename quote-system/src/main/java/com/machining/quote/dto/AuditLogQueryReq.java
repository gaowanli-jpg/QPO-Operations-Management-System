package com.machining.quote.dto;

import lombok.Data;

@Data
public class AuditLogQueryReq {
    private Integer page;
    private Integer pageSize;
    private String startTime;
    private String endTime;
    private String username;
    private String role;
    private String module;
    private String action;
    private String targetType;
    private String targetId;
    private String result;
    private String keyword;
}
