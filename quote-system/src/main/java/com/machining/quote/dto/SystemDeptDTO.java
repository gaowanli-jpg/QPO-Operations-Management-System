package com.machining.quote.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class SystemDeptDTO {
    private Long id;
    private Long parentId;
    private String deptCode;
    private String deptName;
    private Long leaderUserId;
    private Integer sortOrder;
    private Boolean enabled;
    private List<SystemDeptDTO> children = new ArrayList<>();
}
