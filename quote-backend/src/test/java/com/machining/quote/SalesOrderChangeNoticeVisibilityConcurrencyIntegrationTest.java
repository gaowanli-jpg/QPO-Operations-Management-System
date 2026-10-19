package com.machining.quote;

import com.machining.quote.config.AuthContext;
import com.machining.quote.dto.SalesOrderChangeNoticeQueryReq;
import com.machining.quote.service.AuditService;
import com.machining.quote.service.SalesOrderChangeNoticeService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(properties = {
        "erp.auth.enabled=false",
        "erp.auth.jwt-secret=test-jwt-secret-for-sales-change-visibility-32chars",
        "erp.upload-dir=${java.io.tmpdir}/quote-test/uploads/",
        "erp.backup-dir=${java.io.tmpdir}/quote-test/backups/",
        "spring.task.scheduling.enabled=false"
})
class SalesOrderChangeNoticeVisibilityConcurrencyIntegrationTest {
    @Autowired
    private SalesOrderChangeNoticeService noticeService;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private PlatformTransactionManager transactionManager;
    @MockBean
    private AuditService auditService;

    private Long salesOrderId;
    private Long salesOrderItemId;
    private String noticeId;

    @AfterEach
    void tearDown() {
        AuthContext.clear();
        if (noticeId != null) jdbcTemplate.update("DELETE FROM sales_order_change_notice WHERE notice_id = ?", noticeId);
        if (salesOrderItemId != null) jdbcTemplate.update("DELETE FROM sales_order_item WHERE id = ?", salesOrderItemId);
        if (salesOrderId != null) jdbcTemplate.update("DELETE FROM sales_order_header WHERE id = ?", salesOrderId);
        jdbcTemplate.update("UPDATE sales_order_change_dispatch_setting SET dispatch_mode = 'MANUAL', auto_dispatch_epoch = 0 WHERE id = 1");
    }

    @Test
    void hiddenAndRestoredNoticesRespectViewsAndAutomaticBacklogRules() {
        createNotice();
        assertThat(noticeService.hide(List.of(noticeId), "不需要通知下游")).isEqualTo(1);
        assertThat(page("ACTIVE").getRecords()).isEmpty();
        assertThat(page("HIDDEN").getRecords()).extracting("noticeId").containsExactly(noticeId);
        jdbcTemplate.update("UPDATE sales_order_change_dispatch_setting SET dispatch_mode = 'AUTO', auto_dispatch_epoch = 4 WHERE id = 1");
        assertThat(noticeService.claimAutoPending()).isEmpty();
        assertThatThrownBy(() -> noticeService.claimManual(List.of(noticeId)))
                .isInstanceOf(IllegalArgumentException.class);

        assertThat(noticeService.restore(List.of(noticeId), null)).isEqualTo(1);
        assertThat(page("ACTIVE").getRecords()).extracting("noticeId").containsExactly(noticeId);
        assertThat(page("HIDDEN").getRecords()).isEmpty();
        assertThat(noticeService.claimAutoPending()).isEmpty();
        assertThat(noticeService.claimManual(List.of(noticeId))).containsExactly(noticeId);
        assertThatThrownBy(() -> noticeService.hide(List.of(noticeId), "发送中不可隐藏"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void manualClaimWaitsForConcurrentHideAndCannotClaimTheHiddenNotice() throws Exception {
        createNotice();
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch hiddenUpdateApplied = new CountDownLatch(1);
        CountDownLatch finishHideTransaction = new CountDownLatch(1);
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        Future<?> hideTransaction = executor.submit(() -> transaction.executeWithoutResult(status -> {
            jdbcTemplate.update("UPDATE sales_order_change_notice SET is_hidden = TRUE, hidden_reason = 'concurrency test' WHERE notice_id = ?", noticeId);
            hiddenUpdateApplied.countDown();
            await(finishHideTransaction);
        }));
        Future<List<String>> claim = null;
        try {
            assertThat(hiddenUpdateApplied.await(5, TimeUnit.SECONDS)).isTrue();
            claim = executor.submit(() -> noticeService.claimManual(List.of(noticeId)));
            Future<List<String>> waitingClaim = claim;
            assertThatThrownBy(() -> waitingClaim.get(300, TimeUnit.MILLISECONDS))
                    .isInstanceOf(TimeoutException.class);
        } finally {
            finishHideTransaction.countDown();
        }
        try {
            hideTransaction.get(5, TimeUnit.SECONDS);
            Future<List<String>> completedClaim = claim;
            assertThatThrownBy(() -> completedClaim.get(5, TimeUnit.SECONDS))
                    .isInstanceOf(ExecutionException.class)
                    .hasCauseInstanceOf(IllegalArgumentException.class);
            assertThat(jdbcTemplate.queryForObject("SELECT status FROM sales_order_change_notice WHERE notice_id = ?", String.class, noticeId))
                    .isEqualTo("PENDING");
            assertThat(jdbcTemplate.queryForObject("SELECT is_hidden FROM sales_order_change_notice WHERE notice_id = ?", Boolean.class, noticeId))
                    .isTrue();
        } finally {
            executor.shutdownNow();
        }
    }

    private void createNotice() {
        String suffix = UUID.randomUUID().toString().replace("-", "");
        String salesOrderNo = "SO-SCV-" + suffix;
        salesOrderId = jdbcTemplate.queryForObject("""
                INSERT INTO sales_order_header (sales_order_no, document_no, status)
                VALUES (?, ?, '已生成提单')
                RETURNING id
                """, Long.class, salesOrderNo, "DOC-" + salesOrderNo);
        salesOrderItemId = jdbcTemplate.queryForObject("""
                INSERT INTO sales_order_item (sales_order_id, line_no, part_no, qty)
                VALUES (?, 1, ?, 1)
                RETURNING id
                """, Long.class, salesOrderId, "PART-" + suffix);
        noticeId = "QPO-SOCN-" + suffix.substring(0, 20).toUpperCase();
        jdbcTemplate.update("""
                INSERT INTO sales_order_change_notice (
                    notice_id, sales_order_id, sales_order_item_id, target_type, target_key,
                    change_type, changed_fields_json, payload_snapshot_json, source_event_key, status, is_hidden
                ) VALUES (?, ?, ?, 'PRODUCTION', ?, 'MODIFY', '[]', '{}', ?, 'PENDING', FALSE)
                """, noticeId, salesOrderId, salesOrderItemId, "ALLOCATION:" + suffix, suffix);
    }

    private com.machining.quote.dto.PageResult<com.machining.quote.dto.SalesOrderChangeNoticeDTO> page(String view) {
        SalesOrderChangeNoticeQueryReq req = new SalesOrderChangeNoticeQueryReq();
        req.setSalesOrderNo("SO-SCV-");
        req.setView(view);
        req.setPage(1);
        req.setPageSize(20);
        return noticeService.page(req);
    }

    private void await(CountDownLatch latch) {
        try {
            if (!latch.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("Timed out waiting for concurrency test release");
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Concurrency test interrupted", error);
        }
    }
}
