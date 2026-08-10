package com.machining.quote.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "qpo.repair.sales-order-dirty-snapshot")
@ConditionalOnProperty(prefix = "qpo.repair.sales-order-dirty-snapshot", name = "enabled", havingValue = "true")
public class SalesOrderDirtySnapshotRepairRunner implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(SalesOrderDirtySnapshotRepairRunner.class);

    private final SalesOrderDirtySnapshotRepairService repairService;
    private final ConfigurableApplicationContext applicationContext;
    private Long salesOrderId;
    private String executor;
    private boolean apply;
    private boolean exitAfterRun = true;

    public SalesOrderDirtySnapshotRepairRunner(
            SalesOrderDirtySnapshotRepairService repairService,
            ConfigurableApplicationContext applicationContext) {
        this.repairService = repairService;
        this.applicationContext = applicationContext;
    }

    @Override
    public void run(ApplicationArguments args) {
        SalesOrderDirtySnapshotRepairService.RepairResult preview = repairService.preview(salesOrderId, executor);
        log.info("Sales order dirty snapshot repair preview sourceOrderId={} orderCount={} candidateItemCount={} candidateTicketCount={} skippedReasons={}",
                salesOrderId, preview.orderCount(), preview.candidateItemCount(), preview.candidateTicketCount(),
                preview.skippedReasons());
        if (apply) {
            SalesOrderDirtySnapshotRepairService.RepairResult result = repairService.repair(salesOrderId, executor);
            log.info("Sales order dirty snapshot repair applied sourceOrderId={} orderCount={} salesOrderItemCount={} ticketCount={} skippedReasons={}",
                    salesOrderId, result.orderCount(), result.repairedItemCount(), result.repairedTicketCount(),
                    result.skippedReasons());
        } else {
            log.info("Sales order dirty snapshot repair dry-run only sourceOrderId={}", salesOrderId);
        }
        if (exitAfterRun) {
            SpringApplication.exit(applicationContext, () -> 0);
        }
    }

    public Long getSalesOrderId() {
        return salesOrderId;
    }

    public void setSalesOrderId(Long salesOrderId) {
        this.salesOrderId = salesOrderId;
    }

    public String getExecutor() {
        return executor;
    }

    public void setExecutor(String executor) {
        this.executor = executor;
    }

    public boolean isApply() {
        return apply;
    }

    public void setApply(boolean apply) {
        this.apply = apply;
    }

    public boolean isExitAfterRun() {
        return exitAfterRun;
    }

    public void setExitAfterRun(boolean exitAfterRun) {
        this.exitAfterRun = exitAfterRun;
    }
}
