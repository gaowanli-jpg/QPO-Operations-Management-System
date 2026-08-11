package com.machining.quote.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class RoleDataScopeAssignReq {
    private String dataScope;
    private List<Long> deptIds = new ArrayList<>();
    private List<String> customerCodes = new ArrayList<>();
    private List<String> customerNames = new ArrayList<>();
}
