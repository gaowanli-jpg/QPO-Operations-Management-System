package com.machining.quote.dto;

import lombok.Data;

@Data
public class SystemDeptSaveReq {
    private Long id;
    private Long parentId;
    private String deptCode;
    private String deptName;
    private Long leaderUserId;
    private Integer sortOrder;
    private Boolean enabled;
}
