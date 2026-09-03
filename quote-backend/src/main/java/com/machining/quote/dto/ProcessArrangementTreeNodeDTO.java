package com.machining.quote.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Data
public class ProcessArrangementTreeNodeDTO {
    private String treeKey;
    private String nodeType;
    private Long id;
    private Long nodeId;
    private Long itemId;
    private Long parentId;
    private Long parentNodeId;
    private String label;
    private String nodeName;
    private String sourceType;
    private String optionKey;
    private Long processDetailId;
    private Long processCategoryId;
    private String processCode;
    private String categoryName;
    private String detailName;
    private Integer feeItemId;
    private String feeCode;
    private String feeName;
    private String calcType;
    private BigDecimal detailHourlyRate;
    private BigDecimal categoryHourlyRate;
    private BigDecimal effectiveHourlyRate;
    private BigDecimal hourlyRate;
    private Boolean enabled;
    private Boolean detailEnabled;
    private Boolean categoryEnabled;
    private Boolean effectiveEnabled;
    private Integer sortNo;
    private List<ProcessArrangementTreeNodeDTO> children = new ArrayList<>();
}
