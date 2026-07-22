package com.machining.quote.dto;

import lombok.Data;

@Data
public class SystemPermissionDTO {
    private Long id;
    private Long parentId;
    private String permissionType;
    private String permissionCode;
    private String permissionName;
    private String routePath;
    private String componentKey;
    private String icon;
    private Integer sortOrder;
    private Boolean visible;
    private Boolean enabled;
    private Boolean assignable;
}
