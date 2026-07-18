package com.machining.quote.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class PermissionTreeNodeDTO {
    private String key;
    private String label;
    private String groupKey;
    private String groupName;
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
    private Boolean disabled;
    private List<PermissionTreeNodeDTO> children = new ArrayList<>();
}
