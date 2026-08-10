package com.machining.quote;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.machining.quote.dto.ReleaseItemDataReplacementAcceptedQtyDTO;
import com.machining.quote.dto.ReleaseItemDataReplacementChangeDTO;
import com.machining.quote.dto.ReleaseItemDataReplacementConfirmDTO;
import com.machining.quote.dto.ReleaseItemDataReplacementField;
import com.machining.quote.dto.ReleaseItemDataReplacementPreviewDTO;
import com.machining.quote.dto.ReleaseItemDataReplacementRowDTO;
import com.machining.quote.dto.ReleaseItemDataReplacementRowStatus;
import com.machining.quote.dto.ReleaseItemDataReplacementSummaryDTO;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static com.machining.quote.dto.ReleaseItemDataReplacementField.ValueType.DATE;
import static com.machining.quote.dto.ReleaseItemDataReplacementField.ValueType.MATCH_KEY;
import static com.machining.quote.dto.ReleaseItemDataReplacementField.ValueType.ORDER_TYPE;
import static com.machining.quote.dto.ReleaseItemDataReplacementField.ValueType.POSITIVE_INTEGER;
import static com.machining.quote.dto.ReleaseItemDataReplacementField.ValueType.TEXT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

class ReleaseItemDataReplacementStage1ContractTest {
    @Test
    void fieldMappingDefinesAllRequiredKeysAndOptionalReplacementColumns() {
        assertThat(ReleaseItemDataReplacementField.values())
                .extracting(
                        ReleaseItemDataReplacementField::getHeader,
                        ReleaseItemDataReplacementField::getPropertyName,
                        ReleaseItemDataReplacementField::getValueType,
                        ReleaseItemDataReplacementField::getMaxLength,
                        ReleaseItemDataReplacementField::isPersistent)
                .containsExactly(
                        tuple("图号", "partNo", MATCH_KEY, 100, false),
                        tuple("版本", "version", MATCH_KEY, 20, false),
                        tuple("内部目标交期", "internalTargetLeadTime", DATE, null, true),
                        tuple("通过数量", "acceptedQty", POSITIVE_INTEGER, null, false),
                        tuple("物料描述", "description", TEXT, 500, true),
                        tuple("表面处理", "surfaceTreatment", TEXT, 200, true),
                        tuple("备注", "remark", TEXT, 500, true),
                        tuple("下单日期", "salesOrderDate", DATE, null, true),
                        tuple("客户订单号", "customerOrderNo", TEXT, 120, true),
                        tuple("客户项目号", "customerProjectNo", TEXT, 120, true),
                        tuple("客户下单抬头", "customerOrderTitle", TEXT, 255, true),
                        tuple("订单类型", "orderType", ORDER_TYPE, 20, true),
                        tuple("内部项目号", "internalProjectNo", TEXT, 120, true));

        assertThat(ReleaseItemDataReplacementField.requiredMatchKeys())
                .containsExactly(
                        ReleaseItemDataReplacementField.PART_NO,
                        ReleaseItemDataReplacementField.VERSION);
        assertThat(ReleaseItemDataReplacementField.replaceableFields()).hasSize(11);
        assertThat(ReleaseItemDataReplacementField.persistentFields()).hasSize(10);
        assertThat(ReleaseItemDataReplacementField.ACCEPTED_QTY.isReplaceable()).isTrue();
        assertThat(ReleaseItemDataReplacementField.ACCEPTED_QTY.isPersistent()).isFalse();
    }

    @Test
    void fieldMappingNormalizesWhitespaceWithoutAcceptingUnknownAliases() {
        assertThat(ReleaseItemDataReplacementField.fromHeader(" 客户\n订单号 "))
                .contains(ReleaseItemDataReplacementField.CUSTOMER_ORDER_NO);
        assertThat(ReleaseItemDataReplacementField.fromHeader("内部 目标 交期"))
                .contains(ReleaseItemDataReplacementField.INTERNAL_TARGET_LEAD_TIME);
        assertThat(ReleaseItemDataReplacementField.fromHeader("客户订单编号")).isEmpty();
        assertThat(ReleaseItemDataReplacementField.fromHeader(null)).isEmpty();
    }

    @Test
    void previewAndConfirmDtosExposeTypedSummaryRowsChangesAndQuantityDrafts() throws Exception {
        ReleaseItemDataReplacementChangeDTO change = new ReleaseItemDataReplacementChangeDTO();
        change.setField("description");
        change.setLabel("物料描述");
        change.setBeforeValue("旧描述");
        change.setAfterValue("新描述");

        ReleaseItemDataReplacementRowDTO row = new ReleaseItemDataReplacementRowDTO();
        row.setExcelRowNumber(5);
        row.setPartNo("P-001");
        row.setVersion("A");
        row.setRfqId(101);
        row.setRfqNo("RFQ-101");
        row.setItemId(201);
        row.setStatus(ReleaseItemDataReplacementRowStatus.CHANGED);
        row.setChanges(List.of(change));
        row.setAcceptedQty(3);
        row.setReason("");

        ReleaseItemDataReplacementSummaryDTO summary = new ReleaseItemDataReplacementSummaryDTO();
        summary.setImportedCount(1);
        summary.setMatchedCount(1);
        summary.setChangedCount(1);
        summary.setQuantityAppliedCount(1);

        ReleaseItemDataReplacementPreviewDTO preview = new ReleaseItemDataReplacementPreviewDTO();
        preview.setSuccess(true);
        preview.setCanConfirm(true);
        preview.setPreviewFingerprint("fingerprint");
        preview.setRecognizedFields(List.of("description", "acceptedQty"));
        preview.setSummary(summary);
        preview.setRows(List.of(row));

        ReleaseItemDataReplacementAcceptedQtyDTO quantity = new ReleaseItemDataReplacementAcceptedQtyDTO();
        quantity.setReleaseItemKey("RFQ:201");
        quantity.setItemId(201);
        quantity.setAcceptedQty(3);

        ReleaseItemDataReplacementConfirmDTO confirm = new ReleaseItemDataReplacementConfirmDTO();
        confirm.setSuccess(true);
        confirm.setSummary(summary);
        confirm.setAffectedRows(List.of(Map.of("releaseItemKey", "RFQ:201", "id", 201)));
        confirm.setAcceptedQtyUpdates(List.of(quantity));

        ObjectMapper objectMapper = new ObjectMapper();
        JsonNode previewJson = objectMapper.readTree(objectMapper.writeValueAsString(preview));
        JsonNode confirmJson = objectMapper.readTree(objectMapper.writeValueAsString(confirm));

        assertThat(previewJson.path("canConfirm").asBoolean()).isTrue();
        assertThat(previewJson.path("previewFingerprint").asText()).isEqualTo("fingerprint");
        assertThat(previewJson.path("rows").get(0).path("status").asText()).isEqualTo("CHANGED");
        assertThat(previewJson.path("rows").get(0).path("changes").get(0).path("field").asText())
                .isEqualTo("description");
        assertThat(confirmJson.path("affectedRows").get(0).path("releaseItemKey").asText())
                .isEqualTo("RFQ:201");
        assertThat(confirmJson.path("acceptedQtyUpdates").get(0).path("acceptedQty").asInt())
                .isEqualTo(3);
    }

    @Test
    void rowStatusContractContainsEveryPreviewOutcome() {
        assertThat(ReleaseItemDataReplacementRowStatus.values())
                .containsExactly(
                        ReleaseItemDataReplacementRowStatus.CHANGED,
                        ReleaseItemDataReplacementRowStatus.QUANTITY_ONLY,
                        ReleaseItemDataReplacementRowStatus.UNCHANGED,
                        ReleaseItemDataReplacementRowStatus.UNMATCHED,
                        ReleaseItemDataReplacementRowStatus.SKIPPED,
                        ReleaseItemDataReplacementRowStatus.ERROR);
    }
}
