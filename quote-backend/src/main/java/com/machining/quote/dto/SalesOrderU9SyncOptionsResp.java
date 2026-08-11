package com.machining.quote.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
public class SalesOrderU9SyncOptionsResp {
    private String orgCode;
    private Boolean stale = false;
    private LocalDateTime refreshedAt;
    private String warning;
    private List<SalesOrderU9SellerOptionDTO> sellers = new ArrayList<>();
    private List<SalesOrderU9DepartmentOptionDTO> departments = new ArrayList<>();
}
