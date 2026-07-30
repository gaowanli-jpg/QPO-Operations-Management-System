package com.machining.quote.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class CustomerMasterDTO {
    private Long id;
    private String customerCode;
    private String customerShortName;
    private String customerName;
    private Boolean enabled;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
