package com.machining.quote.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SalesOrderU9SellerOptionDTO {
    private String code;
    private String name;
    private String departmentCode;
    private String departmentName;
}
