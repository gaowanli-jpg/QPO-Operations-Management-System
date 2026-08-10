package com.machining.quote;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.machining.quote.config.DataScopeAccessDeniedException;
import com.machining.quote.config.RequirePermission;
import com.machining.quote.controller.ReleaseItemDataReplacementController;
import com.machining.quote.domain.QuoteStatusWorkflow;
import com.machining.quote.dto.ReleaseItemDataReplacementSummaryDTO;
import com.machining.quote.dto.ReleaseOrderSource;
import com.machining.quote.entity.OrderRfq;
import com.machining.quote.mapper.ItemQuoteVersionMapper;
import com.machining.quote.mapper.OrderDeliveryTicketMapper;
import com.machining.quote.mapper.OrderRfqMapper;
import com.machining.quote.service.AuditService;
import com.machining.quote.service.DataScopeService;
import com.machining.quote.service.InternalTargetLeadTimeService;
import com.machining.quote.service.ReleaseItemDataReplacementExcelParser;
import com.machining.quote.service.ReleaseItemDataReplacementService;
import com.machining.quote.service.ReleaseItemMutationPolicy;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.PostMapping;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ReleaseItemDataReplacementStage7GovernanceTest {
    private static final Path MAIN_JAVA = Path.of("src", "main", "java", "com", "machining", "quote");
    private static final Path MAIN_RESOURCES = Path.of("src", "main", "resources");
    private static final Path DOCS = Path.of("..", "docs");

    @Test
    void previewAndConfirmKeepBackendReleaseCreateAsFinalPermissionBoundary() throws Exception {
        Method preview = ReleaseItemDataReplacementController.class.getDeclaredMethod(
                "preview", String.class, org.springframework.web.multipart.MultipartFile.class);
        Method confirm = ReleaseItemDataReplacementController.class.getDeclaredMethod(
                "confirm", String.class, String.class, org.springframework.web.multipart.MultipartFile.class);

        assertThat(preview.getAnnotation(PostMapping.class).value()).containsExactly("/preview");
        assertThat(confirm.getAnnotation(PostMapping.class).value()).containsExactly("/confirm");
        assertThat(preview.getAnnotation(RequirePermission.class).value()).containsExactly("market:release:create");
        assertThat(confirm.getAnnotation(RequirePermission.class).value()).containsExactly("market:release:create");

        String controller = read(MAIN_JAVA.resolve(Path.of("controller", "ReleaseItemDataReplacementController.java")));
        assertThat(countOccurrences(controller, "@RequirePermission(\"market:release:create\")")).isEqualTo(2);
        assertThat(permissionCatalogSource())
                .doesNotContain("market:release:data-replacement")
                .doesNotContain("market:release:replace-data")
                .doesNotContain("common:item-data-replacement")
                .doesNotContain("common:release");
    }

    @Test
    void noDataScopeCannotPreviewByConstructingRfqIdAndPreviewWritesNoAuditNoise() {
        ReleaseItemDataReplacementExcelParser parser = mock(ReleaseItemDataReplacementExcelParser.class);
        OrderRfqMapper orderMapper = mock(OrderRfqMapper.class);
        ItemQuoteVersionMapper itemMapper = mock(ItemQuoteVersionMapper.class);
        OrderDeliveryTicketMapper ticketMapper = mock(OrderDeliveryTicketMapper.class);
        DataScopeService dataScopeService = mock(DataScopeService.class);
        AuditService auditService = mock(AuditService.class);
        InternalTargetLeadTimeService rowService = mock(InternalTargetLeadTimeService.class);
        OrderRfq secret = order(101, "RFQ-SECRET");

        when(orderMapper.selectBatchIds(any())).thenReturn(List.of(secret));
        doThrow(new RuntimeException("No data permission for RFQ-SECRET"))
                .when(dataScopeService).assertCanAccessOrder(secret);
        ReleaseItemDataReplacementService service = new ReleaseItemDataReplacementService(
                parser,
                orderMapper,
                itemMapper,
                ticketMapper,
                dataScopeService,
                new ReleaseItemMutationPolicy(),
                auditService,
                rowService);

        assertThatThrownBy(() -> service.preview(
                List.of(ReleaseOrderSource.rfq(101)),
                new ByteArrayInputStream(new byte[]{1, 2, 3})))
                .isInstanceOf(DataScopeAccessDeniedException.class);

        verify(parser, never()).parse(any());
        verify(itemMapper, never()).selectList(any(Wrapper.class));
        verify(ticketMapper, never()).selectList(any(Wrapper.class));
        verify(auditService, never()).record(any(), any(), any(), any(), any());
    }

    @Test
    void confirmAuditSummaryAndFileLogsUseOnlySafeSummaryFields() throws Exception {
        ReleaseItemDataReplacementService service = new ReleaseItemDataReplacementService(
                mock(ReleaseItemDataReplacementExcelParser.class),
                mock(OrderRfqMapper.class),
                mock(ItemQuoteVersionMapper.class),
                mock(OrderDeliveryTicketMapper.class),
                mock(DataScopeService.class),
                new ReleaseItemMutationPolicy(),
                mock(AuditService.class),
                mock(InternalTargetLeadTimeService.class));
        ReleaseItemDataReplacementSummaryDTO summary = new ReleaseItemDataReplacementSummaryDTO();
        summary.setImportedCount(20);
        summary.setMatchedCount(18);
        summary.setUpdatedItemCount(4);
        summary.setQuantityAppliedCount(3);
        summary.setUnmatchedCount(1);
        summary.setSkippedCount(2);
        Map<String, Integer> changedFieldCounts = new LinkedHashMap<>();
        changedFieldCounts.put("description", 2);
        changedFieldCounts.put("customerOrderNo", 2);

        Method auditSummary = ReleaseItemDataReplacementService.class.getDeclaredMethod(
                "auditSummary", List.class, ReleaseItemDataReplacementSummaryDTO.class, Map.class);
        auditSummary.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<String, Object> audit = (Map<String, Object>) auditSummary.invoke(
                service,
                List.of(101, 102),
                summary,
                changedFieldCounts);

        assertThat(audit.keySet()).containsExactly(
                "rfqIds",
                "importedCount",
                "matchedCount",
                "updatedItemCount",
                "quantityAppliedCount",
                "unmatchedCount",
                "skippedCount",
                "changedFieldCounts");
        assertThat(audit.toString())
                .doesNotContain("excel")
                .doesNotContain("xlsx")
                .doesNotContain("rows")
                .doesNotContain("changes")
                .doesNotContain("beforeValue")
                .doesNotContain("afterValue")
                .doesNotContain("Authorization")
                .doesNotContain("token")
                .doesNotContain("multipart")
                .doesNotContain("requestBody")
                .doesNotContain("responseBody");

        String serviceSource = read(MAIN_JAVA.resolve(Path.of("service", "ReleaseItemDataReplacementService.java")));
        assertThat(serviceSource)
                .contains("Release item data replacement started")
                .contains("Release item data replacement completed")
                .contains("Release item data replacement rejected")
                .contains("Release item data replacement failed");
        assertThat(linesContaining(serviceSource, "Release item data replacement"))
                .allSatisfy(line -> assertThat(line)
                        .doesNotContain("excelInput")
                        .doesNotContain("excelFile")
                        .doesNotContain("getValues")
                        .doesNotContain("getChanges")
                        .doesNotContain("getBeforeValue")
                        .doesNotContain("getAfterValue")
                        .doesNotContain("Authorization")
                        .doesNotContain("token"));
        assertThat(AuditService.inferModule("RELEASE_ITEM_DATA_REPLACEMENT")).isEqualTo("MARKET");
        assertThat(AuditService.labelForAction("RELEASE_ITEM_DATA_REPLACEMENT"))
                .isNotEqualTo("RELEASE_ITEM_DATA_REPLACEMENT");
    }

    @Test
    void stageSevenDocumentsPermissionsOperationLogsAndFileLogRunbook() throws IOException {
        String derivedOrderDoc = readDoc("37-", "acceptedQty", "market:release:create", "U9");
        String permissionDoc = readDoc("02-", "SUPER_ADMIN", "market:release:create");
        String auditDoc = readDoc("01-", "audit_log", "DELIVERY_TICKET_GENERATE");
        String opsDoc = readDoc("03-", "requestId + taskId + audit_log", "Authorization header");
        String fileLogDoc = readDoc("04-", "Delivery ticket generation", "Authorization header");
        String implementationDoc = readDoc("40-", "item-data-replacement", "market:release:create");

        assertThat(derivedOrderDoc)
                .contains("/api/market/release/item-data-replacement/preview")
                .contains("/api/market/release/item-data-replacement/confirm")
                .contains("affectedRows")
                .contains("acceptedQtyUpdates")
                .contains("RELEASE_ITEM_DATA_REPLACEMENT")
                .contains("market:release:create");
        assertThat(permissionDoc)
                .contains("item-data-replacement")
                .contains("market:release:create")
                .contains("market:release:data-replacement")
                .contains("common:*");
        assertThat(implementationDoc)
                .doesNotContain("market:release:data-replacement")
                .doesNotContain("market:release:replace-data");
        assertThat(auditDoc)
                .contains("RELEASE_ITEM_DATA_REPLACEMENT")
                .contains("item_quote_version")
                .contains("changedFieldCounts")
                .contains("quantityAppliedCount")
                .contains("preview")
                .contains("Excel")
                .contains("Authorization header");
        assertThat(opsDoc)
                .contains("RELEASE_ITEM_DATA_REPLACEMENT")
                .contains("item-data-replacement")
                .contains("preview")
                .contains("Release item data replacement completed")
                .contains("Excel")
                .contains("Authorization header");
        assertThat(fileLogDoc)
                .contains("Release item data replacement started")
                .contains("Release item data replacement completed")
                .contains("Release item data replacement rejected")
                .contains("Release item data replacement failed")
                .contains("importedCount")
                .contains("updatedItemCount")
                .contains("quantityAppliedCount")
                .contains("durationMs")
                .contains("Excel")
                .contains("Authorization header");
    }

    private static OrderRfq order(int id, String rfqNo) {
        OrderRfq order = new OrderRfq();
        order.setId(id);
        order.setRfqNo(rfqNo);
        order.setCustomerName("ACME");
        order.setStatus(QuoteStatusWorkflow.QUOTED);
        return order;
    }

    private static String permissionCatalogSource() throws IOException {
        return read(MAIN_JAVA.resolve(Path.of("service", "PermissionService.java")))
                + "\n"
                + read(MAIN_RESOURCES.resolve(Path.of("db", "migration", "V2__qpo_baseline_seed_data.sql")));
    }

    private static String readDoc(String fileNamePrefix, String... requiredNeedles) throws IOException {
        try (Stream<Path> files = Files.walk(DOCS)) {
            List<Path> candidates = files
                    .filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().startsWith(fileNamePrefix))
                    .filter(path -> path.getFileName().toString().endsWith(".md"))
                    .toList();
            for (Path candidate : candidates) {
                String content = read(candidate);
                boolean matches = true;
                for (String needle : requiredNeedles) {
                    if (!content.contains(needle)) {
                        matches = false;
                        break;
                    }
                }
                if (matches) {
                    return content;
                }
            }
            throw new AssertionError("No documentation file starting with " + fileNamePrefix
                    + " contained all anchors " + List.of(requiredNeedles));
        }
    }

    private static String read(Path path) throws IOException {
        return Files.readString(path, StandardCharsets.UTF_8);
    }

    private static long countOccurrences(String text, String needle) {
        long count = 0;
        int index = 0;
        while ((index = text.indexOf(needle, index)) >= 0) {
            count++;
            index += needle.length();
        }
        return count;
    }

    private static List<String> linesContaining(String text, String needle) {
        return text.lines()
                .filter(line -> line.contains(needle))
                .toList();
    }
}
