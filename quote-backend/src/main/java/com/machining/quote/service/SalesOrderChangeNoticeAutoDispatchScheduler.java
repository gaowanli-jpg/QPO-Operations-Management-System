package com.machining.quote.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class SalesOrderChangeNoticeAutoDispatchScheduler {
    @Autowired
    private SalesOrderChangeNoticeService noticeService;
    @Autowired
    private SystemTaskService systemTaskService;

    @Scheduled(fixedDelayString = "${erp.sales-change.auto-dispatch-ms:5000}")
    public void dispatchNewAutomaticNotices() {
        List<String> noticeIds = noticeService.claimAutoPending();
        if (noticeIds == null || noticeIds.isEmpty()) {
            return;
        }
        systemTaskService.submitSalesOrderChangeNoticeTaskClaimed(noticeIds);
    }
}
