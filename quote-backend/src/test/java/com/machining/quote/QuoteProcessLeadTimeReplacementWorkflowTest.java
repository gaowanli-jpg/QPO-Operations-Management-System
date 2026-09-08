package com.machining.quote;

import com.machining.quote.domain.QuoteStatusWorkflow;
import com.machining.quote.dto.QuoteLeadTimeBatchUpdateReq;
import com.machining.quote.dto.QuoteLeadTimeReplacementConfirmDTO;
import com.machining.quote.dto.QuoteLeadTimeReplacementPreviewDTO;
import com.machining.quote.dto.QuoteLeadTimeReplacementRowDTO;
import com.machining.quote.entity.ItemQuoteVersion;
import com.machining.quote.entity.OrderRfq;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class QuoteProcessLeadTimeReplacementWorkflowTest extends CoreWorkflowIntegrationTestSupport {

    @Test
    void batchSavePersistsMultipleDraftLeadTimesAndAllowsClearing() {
        OrderRfq order = pricedOrder("QP-BATCH");
        ItemQuoteVersion first = quoteProcessItem(order, "QP-BATCH-001", "A", "2026-03-20");
        ItemQuoteVersion second = quoteProcessItem(order, "QP-BATCH-002", "A", "2026-03-21");

        QuoteLeadTimeBatchUpdateReq req = new QuoteLeadTimeBatchUpdateReq();
        req.setRfqId(order.getId());
        QuoteLeadTimeBatchUpdateReq.ItemUpdate firstUpdate = new QuoteLeadTimeBatchUpdateReq.ItemUpdate();
        firstUpdate.setItemId(first.getId());
        firstUpdate.setQuoteLeadTime("2026.04.05");
        QuoteLeadTimeBatchUpdateReq.ItemUpdate secondUpdate = new QuoteLeadTimeBatchUpdateReq.ItemUpdate();
        secondUpdate.setItemId(second.getId());
        secondUpdate.setQuoteLeadTime("");
        req.setItems(List.of(firstUpdate, secondUpdate));

        Map<String, Object> result = quoteProcessService.batchUpdateLeadTimes(req);

        assertThat(result).containsEntry("success", true);
        assertThat(result).containsEntry("adjustedCount", 2);
        assertThat(result).containsEntry("updatedCount", 2);
        assertThat(itemMapper.selectById(first.getId()).getQuoteLeadTime()).isEqualTo("2026-04-05");
        assertThat(itemMapper.selectById(second.getId()).getQuoteLeadTime()).isNull();
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> affectedRows = (List<Map<String, Object>>) result.get("affectedRows");
        assertThat(affectedRows).extracting(row -> row.get("id"))
                .containsExactly(first.getId(), second.getId());
    }

    @Test
    void leadTimeReplacementPreviewAndConfirmUpdateOnlyMatchedNonBlankRows() throws Exception {
        OrderRfq order = pricedOrder("QP-REPLACE");
        ItemQuoteVersion changed = quoteProcessItem(order, "QP-REPLACE-001", "A", "2026-03-20");
        ItemQuoteVersion blankSkipped = quoteProcessItem(order, "QP-REPLACE-002", "B", "2026-03-21");

        byte[] workbook = workbook(List.of(
                List.of("图号", "版本", "报价交期"),
                List.of("QP-REPLACE-001", "A", "2026/4/5"),
                List.of("QP-REPLACE-002", "B", ""),
                List.of("QP-REPLACE-404", "A", "2026-04-08")
        ));

        QuoteLeadTimeReplacementPreviewDTO preview = quoteProcessService.previewLeadTimeReplacement(
                order.getId(), new ByteArrayInputStream(workbook));

        assertThat(preview.isCanConfirm()).isTrue();
        assertThat(preview.getRecognizedFields()).containsExactly("partNo", "version", "quoteLeadTime");
        assertThat(preview.getSummary().getImportedCount()).isEqualTo(3);
        assertThat(preview.getSummary().getMatchedCount()).isEqualTo(2);
        assertThat(preview.getSummary().getChangedCount()).isEqualTo(1);
        assertThat(preview.getSummary().getEmptyValueCount()).isEqualTo(1);
        assertThat(preview.getSummary().getUnmatchedCount()).isEqualTo(1);
        assertThat(preview.getRows()).extracting(QuoteLeadTimeReplacementRowDTO::getStatus)
                .containsExactly("CHANGED", "EMPTY_VALUE", "UNMATCHED");

        QuoteLeadTimeReplacementConfirmDTO confirmed = quoteProcessService.confirmLeadTimeReplacement(
                order.getId(), new ByteArrayInputStream(workbook), preview.getPreviewFingerprint());

        assertThat(confirmed.isSuccess()).isTrue();
        assertThat(confirmed.getSummary().getUpdatedItemCount()).isEqualTo(1);
        assertThat(itemMapper.selectById(changed.getId()).getQuoteLeadTime()).isEqualTo("2026-04-05");
        assertThat(itemMapper.selectById(blankSkipped.getId()).getQuoteLeadTime()).isEqualTo("2026-03-21");
        assertThat(confirmed.getAffectedRows()).extracting(row -> row.get("id"))
                .containsExactly(changed.getId());
    }

    @Test
    void leadTimeReplacementWithoutLineIdentifierUpdatesAllMatchingPartVersionRows() throws Exception {
        OrderRfq order = pricedOrder("QP-DUP");
        ItemQuoteVersion first = quoteProcessItem(order, "QP-DUP-001", "A", "2026-03-20");
        ItemQuoteVersion second = quoteProcessItem(order, "QP-DUP-001", "A", "2026-03-21");

        byte[] workbook = workbook(List.of(
                List.of("图号", "版本", "报价交期"),
                List.of("QP-DUP-001", "A", "2026/4/5")
        ));

        QuoteLeadTimeReplacementPreviewDTO preview = quoteProcessService.previewLeadTimeReplacement(
                order.getId(), new ByteArrayInputStream(workbook));

        assertThat(preview.isCanConfirm()).isTrue();
        assertThat(preview.getSummary().getImportedCount()).isEqualTo(1);
        assertThat(preview.getSummary().getMatchedCount()).isEqualTo(2);
        assertThat(preview.getSummary().getChangedCount()).isEqualTo(2);
        assertThat(preview.getSummary().getErrorCount()).isZero();
        assertThat(preview.getRows()).extracting(QuoteLeadTimeReplacementRowDTO::getItemId)
                .containsExactly(first.getId(), second.getId());

        QuoteLeadTimeReplacementConfirmDTO confirmed = quoteProcessService.confirmLeadTimeReplacement(
                order.getId(), new ByteArrayInputStream(workbook), preview.getPreviewFingerprint());

        assertThat(confirmed.isSuccess()).isTrue();
        assertThat(confirmed.getSummary().getUpdatedItemCount()).isEqualTo(2);
        assertThat(itemMapper.selectById(first.getId()).getQuoteLeadTime()).isEqualTo("2026-04-05");
        assertThat(itemMapper.selectById(second.getId()).getQuoteLeadTime()).isEqualTo("2026-04-05");
        assertThat(confirmed.getAffectedRows()).extracting(row -> row.get("id"))
                .containsExactly(first.getId(), second.getId());
    }

    @Test
    void leadTimeReplacementSkipsInvalidRowsAndAppliesValidRows() throws Exception {
        OrderRfq order = pricedOrder("QP-PARTIAL");
        ItemQuoteVersion invalid = quoteProcessItem(order, "QP-PARTIAL-ERR", "A", "2026-03-20");
        ItemQuoteVersion valid = quoteProcessItem(order, "QP-PARTIAL-OK", "A", "2026-03-21");

        byte[] workbook = workbook(List.of(
                List.of("图号", "版本", "报价交期"),
                List.of("QP-PARTIAL-ERR", "A", "not-a-date"),
                List.of("QP-PARTIAL-OK", "A", "2026/4/5")
        ));

        QuoteLeadTimeReplacementPreviewDTO preview = quoteProcessService.previewLeadTimeReplacement(
                order.getId(), new ByteArrayInputStream(workbook));

        assertThat(preview.isCanConfirm()).isTrue();
        assertThat(preview.getSummary().getImportedCount()).isEqualTo(2);
        assertThat(preview.getSummary().getChangedCount()).isEqualTo(1);
        assertThat(preview.getSummary().getErrorCount()).isEqualTo(1);

        QuoteLeadTimeReplacementConfirmDTO confirmed = quoteProcessService.confirmLeadTimeReplacement(
                order.getId(), new ByteArrayInputStream(workbook), preview.getPreviewFingerprint());

        assertThat(confirmed.isSuccess()).isTrue();
        assertThat(confirmed.getSummary().getUpdatedItemCount()).isEqualTo(1);
        assertThat(confirmed.getSummary().getErrorCount()).isEqualTo(1);
        assertThat(itemMapper.selectById(invalid.getId()).getQuoteLeadTime()).isEqualTo("2026-03-20");
        assertThat(itemMapper.selectById(valid.getId()).getQuoteLeadTime()).isEqualTo("2026-04-05");
        assertThat(confirmed.getAffectedRows()).extracting(row -> row.get("id"))
                .containsExactly(valid.getId());
    }

    @Test
    void leadTimeReplacementRejectsInvalidDateWithoutWriting() throws Exception {
        OrderRfq order = pricedOrder("QP-BAD-DATE");
        ItemQuoteVersion item = quoteProcessItem(order, "QP-BAD-DATE-001", "A", "2026-03-20");

        byte[] workbook = workbook(List.of(
                List.of("图号", "版本", "报价交期"),
                List.of("QP-BAD-DATE-001", "A", "not-a-date")
        ));

        QuoteLeadTimeReplacementPreviewDTO preview = quoteProcessService.previewLeadTimeReplacement(
                order.getId(), new ByteArrayInputStream(workbook));

        assertThat(preview.isCanConfirm()).isFalse();
        assertThat(preview.getSummary().getErrorCount()).isEqualTo(1);
        assertThat(preview.getRows()).extracting(QuoteLeadTimeReplacementRowDTO::getStatus).containsExactly("ERROR");
        assertThat(preview.getRows().get(0).getReason()).contains("不是有效日期");
        assertThatThrownBy(() -> quoteProcessService.confirmLeadTimeReplacement(
                order.getId(), new ByteArrayInputStream(workbook), preview.getPreviewFingerprint()))
                .hasMessageContaining("存在错误行");
        assertThat(itemMapper.selectById(item.getId()).getQuoteLeadTime()).isEqualTo("2026-03-20");
    }

    private OrderRfq pricedOrder(String prefix) {
        OrderRfq order = new OrderRfq();
        order.setRfqNo(prefix + "-" + System.nanoTime());
        order.setCustomerName(prefix + "客户");
        order.setStatus(QuoteStatusWorkflow.PRICED);
        orderMapper.insert(order);
        return order;
    }

    private ItemQuoteVersion quoteProcessItem(OrderRfq order, String partNo, String version, String quoteLeadTime) {
        ItemQuoteVersion item = new ItemQuoteVersion();
        item.setRfqId(order.getId());
        item.setPartNo(partNo);
        item.setVersion(version);
        item.setDescription(partNo + "描述");
        item.setQty(1);
        item.setUnit("件");
        item.setDrawingUrl("/uploads/test/" + partNo + ".pdf");
        item.setLeadTime("2026-03-18");
        item.setQuoteLeadTime(quoteLeadTime);
        item.setTaxIncludedUnitPrice(10.0);
        item.setTaxIncludedTotalPrice(10.0);
        item.setStatus(QuoteStatusWorkflow.PRICED);
        itemMapper.insert(item);
        return itemMapper.selectById(item.getId());
    }

    private byte[] workbook(List<List<String>> rows) throws Exception {
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("quote-process");
            for (int rowIndex = 0; rowIndex < rows.size(); rowIndex++) {
                Row row = sheet.createRow(rowIndex);
                List<String> values = rows.get(rowIndex);
                for (int columnIndex = 0; columnIndex < values.size(); columnIndex++) {
                    row.createCell(columnIndex).setCellValue(values.get(columnIndex));
                }
            }
            workbook.write(output);
            return output.toByteArray();
        }
    }
}
