package com.machining.quote.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class U9RuntimeConfigDTO {
    private String orgCode;
    private String source;
    private LocalDateTime updatedAt;
    private boolean fallback;
}
