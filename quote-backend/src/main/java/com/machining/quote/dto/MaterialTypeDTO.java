package com.machining.quote.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class MaterialTypeDTO {
    private Long id;
    private String typeCode;
    private String typeName;
    private String remarks;
    private Boolean enabled;
    private Integer sortNo;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
