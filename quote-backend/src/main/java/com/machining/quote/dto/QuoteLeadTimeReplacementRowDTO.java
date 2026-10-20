package com.machining.quote.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class QuoteLeadTimeReplacementRowDTO {
    private Integer excelRowNumber;
    private String partNo;
    private String version;
    private String normalizedPartNo;
    private String normalizedVersion;
    private Integer rfqId;
    private String rfqNo;
    private Integer itemId;
    private String currentQuoteLeadTime;
    private String importedQuoteLeadTime;
    private String status;
    private String reason;
    private List<String> errors = new ArrayList<>();

    public boolean hasErrors() {
        return errors != null && !errors.isEmpty();
    }
}
