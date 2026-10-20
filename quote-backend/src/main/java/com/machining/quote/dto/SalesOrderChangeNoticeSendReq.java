package com.machining.quote.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class SalesOrderChangeNoticeSendReq {
    private List<String> noticeIds = new ArrayList<>();
}
