package com.machining.quote.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class CustomerMasterDTO {
    private Long id;
    private String u9OrgCode;
    private String u9CustomerId;
    private String customerCode;
    private String customerShortName;
    private String customerName;
    private String supplierCode;
    private Boolean enabled;
    private LocalDateTime lastSyncedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
