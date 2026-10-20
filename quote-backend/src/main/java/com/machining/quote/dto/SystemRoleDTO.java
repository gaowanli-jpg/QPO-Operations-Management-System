package com.machining.quote.dto;

import lombok.Data;

@Data
public class SystemRoleDTO {
    private Long id;
    private String roleCode;
    private String roleName;
    private String description;
    private Boolean superAdmin;
    private String dataScope;
    private Boolean enabled;
    private Integer sortOrder;
}
