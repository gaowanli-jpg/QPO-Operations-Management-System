package com.machining.quote.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class MenuTreeDTO {
    private Long id;
    private Long parentId;
    private String title;
    private String permission;
    private String path;
    private String componentKey;
    private String icon;
    private Integer sortOrder;
    private List<MenuTreeDTO> children = new ArrayList<>();
}
