package com.machining.quote.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class SalesOrderChangeNoticeVisibilityReq {
    private List<String> noticeIds = new ArrayList<>();
    private String reason;
}
