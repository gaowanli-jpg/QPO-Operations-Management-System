package com.machining.quote.dto;

import lombok.Data;

@Data
public class SystemPostDTO {
    private Long id;
    private String postCode;
    private String postName;
    private Integer sortOrder;
    private Boolean enabled;
}
