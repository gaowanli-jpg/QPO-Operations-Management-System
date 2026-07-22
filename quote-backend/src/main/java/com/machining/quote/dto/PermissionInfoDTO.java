package com.machining.quote.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class PermissionInfoDTO {
    private List<String> roles = new ArrayList<>();
    private List<String> permissions = new ArrayList<>();
    private List<MenuTreeDTO> menus = new ArrayList<>();
    private boolean superAdmin;
}
