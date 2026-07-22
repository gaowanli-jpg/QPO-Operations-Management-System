package com.machining.quote.dto;

import lombok.Data;

@Data
public class LockedItemPageDTO {
    private java.util.List<LockedItemDTO> records;
    private long total;
    private int page;
    private int pageSize;

    public static LockedItemPageDTO of(java.util.List<LockedItemDTO> records, long total, int page, int pageSize) {
        LockedItemPageDTO dto = new LockedItemPageDTO();
        dto.setRecords(records);
        dto.setTotal(total);
        dto.setPage(page);
        dto.setPageSize(pageSize);
        return dto;
    }
}
