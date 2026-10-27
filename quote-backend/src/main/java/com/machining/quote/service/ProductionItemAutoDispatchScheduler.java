package com.machining.quote.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 待产零件每日定时自动下发调度器。
 *
 * <p>采用 fixedDelay 轮询 + 配置表 shouldRun/claim 判定，与 MesMasterDataSyncService 保持同一套范式：
 * 每分钟检查一次"今天的目标时间是否已过且今天还没跑过"，改时间不需要重启。
 */
@Component
public class ProductionItemAutoDispatchScheduler {
    private static final Logger log = LoggerFactory.getLogger(ProductionItemAutoDispatchScheduler.class);

    @Autowired
    private ProductionItemAutoDispatchConfigService configService;
    @Autowired
    private ProductionItemAutoDispatchService autoDispatchService;

    @Scheduled(fixedDelayString = "${erp.production-auto-dispatch.check-ms:60000}")
    public void dispatchScheduled() {
        LocalDateTime now = configService.now();
        if (!configService.shouldRun(now)) {
            return;
        }
        if (autoDispatchService.isRunning()) {
            log.info("Production auto dispatch skipped because previous run is still active");
            return;
        }
        if (!configService.claimDailyRun(now)) {
            return;
        }
        try {
            autoDispatchService.submitRun(ProductionItemAutoDispatchService.TRIGGER_SCHEDULED);
        } catch (RuntimeException error) {
            configService.resetDailyRunClaim();
            log.warn("Production auto dispatch submit failed, daily claim rolled back", error);
        }
    }
}
