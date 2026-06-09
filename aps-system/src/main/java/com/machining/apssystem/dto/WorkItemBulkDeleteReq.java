package com.machining.apssystem.dto;

import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

@Data
public class WorkItemBulkDeleteReq {
    @NotEmpty(message = "ids must not be empty.")
    private List<Long> ids;
}
