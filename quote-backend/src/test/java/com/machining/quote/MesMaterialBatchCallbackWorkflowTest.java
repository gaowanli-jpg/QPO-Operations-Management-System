package com.machining.quote;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.machining.quote.controller.MesIntegrationCallbackController;
import com.machining.quote.dto.mes.MesMaterialCallbackReq;
import com.machining.quote.dto.mes.MesMaterialCallbackResp;
import com.machining.quote.entity.MesIntegrationBatch;
import com.machining.quote.entity.MesMaterialBatchItem;
import com.machining.quote.entity.OrderDeliveryTicket;
import com.machining.quote.entity.OrderDeliveryTicketMesRequest;
import com.machining.quote.entity.SalesOrderItem;
import com.machining.quote.integration.InboundCallbackCode;
import com.machining.quote.mapper.MesIntegrationBatchMapper;
import com.machining.quote.mapper.MesMaterialBatchItemMapper;
import com.machining.quote.mapper.OrderDeliveryTicketMapper;
import com.machining.quote.mapper.OrderDeliveryTicketMesRequestMapper;
import com.machining.quote.mapper.SalesOrderItemMapper;
import com.machining.quote.service.AuditService;
import com.machining.quote.service.SourceItemMaterialNoSyncService;
import com.machining.quote.service.SystemInboundCallbackConfigService;
import com.machining.quote.service.mes.MesMaterialCallbackException;
import com.machining.quote.service.mes.MesMaterialSyncService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class MesMaterialBatchCallbackWorkflowTest {
    private static final String BIZ_ID = "QPO-MAT-20260812-01J5ABCDEF";
    private static final String LINE_ID = "QPO-MATL-01J5ABCDEF00000000000001";

    @Test
    void batchCallbackSuccessUpdatesPendingRowAndReturnsUnifiedCounts() {
        Fixture fixture = fixture();
        MesMaterialBatchItem item = batchItem("CALLBACK_PENDING", null);
        OrderDeliveryTicket ticket = ticket("CALLBACK_PENDING", null, LINE_ID);
        SalesOrderItem salesItem = new SalesOrderItem();
        salesItem.setId(5001L);
        when(fixture.batchMapper.selectOne(any(Wrapper.class))).thenReturn(batch("PARTIAL_PENDING"));
        when(fixture.batchItemMapper.selectList(any(Wrapper.class))).thenReturn(List.of(item));
        when(fixture.requestMapper.selectOne(any(Wrapper.class))).thenReturn(request("CALLBACK_PENDING"));
        when(fixture.ticketMapper.selectById(123)).thenReturn(ticket);
        when(fixture.salesOrderItemMapper.selectByIdForUpdate(5001L)).thenReturn(salesItem);

        MesMaterialCallbackResp response = fixture.service.handleMaterialBatchCallback(batchCallback(successItem("MAT-NEW")));

        assertThat(response.getSuccess()).isTrue();
        assertThat(response.getCode()).isEqualTo(0);
        assertThat(response.getMsg()).isEqualTo("accepted");
        com.fasterxml.jackson.databind.JsonNode responseBody = new ObjectMapper().valueToTree(response);
        assertThat(responseBody.has("msg")).isTrue();
        assertThat(responseBody.has("message")).isFalse();
        assertThat(response.getData())
                .containsEntry("bizId", BIZ_ID)
                .containsEntry("receivedCount", 1)
                .containsEntry("appliedCount", 1)
                .containsEntry("duplicateCount", 0);

        ArgumentCaptor<MesMaterialBatchItem> batchItemCaptor = ArgumentCaptor.forClass(MesMaterialBatchItem.class);
        verify(fixture.batchItemMapper).updateById(batchItemCaptor.capture());
        assertThat(batchItemCaptor.getValue().getId()).isEqualTo(20L);
        assertThat(batchItemCaptor.getValue().getStatus()).isEqualTo("SUCCESS");
        assertThat(batchItemCaptor.getValue().getProductCode()).isEqualTo("MAT-NEW");
        assertThat(batchItemCaptor.getValue().getProductName()).isEqualTo("Precision plate");
        assertThat(batchItemCaptor.getValue().getMesTaskNo()).isEqualTo("MP202608120001");
        assertThat(batchItemCaptor.getValue().getCompletedAt()).isNotNull();

        ArgumentCaptor<OrderDeliveryTicket> ticketCaptor = ArgumentCaptor.forClass(OrderDeliveryTicket.class);
        verify(fixture.ticketMapper).updateById(ticketCaptor.capture());
        assertThat(ticketCaptor.getValue().getId()).isEqualTo(123);
        assertThat(ticketCaptor.getValue().getMaterialNo()).isEqualTo("MAT-NEW");
        assertThat(ticketCaptor.getValue().getMesSyncStatus()).isEqualTo("SUCCESS");
        assertThat(ticketCaptor.getValue().getMesRequestId()).isEqualTo(LINE_ID);
        assertThat(ticketCaptor.getValue().getMesExternalTaskId()).isEqualTo("MP202608120001");

        ArgumentCaptor<OrderDeliveryTicketMesRequest> requestCaptor = ArgumentCaptor.forClass(OrderDeliveryTicketMesRequest.class);
        verify(fixture.requestMapper).update(requestCaptor.capture(), any(Wrapper.class));
        assertThat(requestCaptor.getValue().getRequestStatus()).isEqualTo("SUCCESS");
        assertThat(requestCaptor.getValue().getMaterialNo()).isEqualTo("MAT-NEW");
        assertThat(requestCaptor.getValue().getMesExternalTaskId()).isEqualTo("MP202608120001");
        assertThat(requestCaptor.getValue().getCompletedAt()).isNotNull();

        ArgumentCaptor<SalesOrderItem> salesCaptor = ArgumentCaptor.forClass(SalesOrderItem.class);
        verify(fixture.salesOrderItemMapper).updateById(salesCaptor.capture());
        assertThat(salesCaptor.getValue().getId()).isEqualTo(5001L);
        assertThat(salesCaptor.getValue().getMaterialNo()).isEqualTo("MAT-NEW");

        ArgumentCaptor<MesIntegrationBatch> batchCaptor = ArgumentCaptor.forClass(MesIntegrationBatch.class);
        verify(fixture.batchMapper).updateById(batchCaptor.capture());
        assertThat(batchCaptor.getValue().getId()).isEqualTo(10L);
        assertThat(batchCaptor.getValue().getStatus()).isEqualTo("SUCCESS");
        assertThat(batchCaptor.getValue().getCompletedAt()).isNotNull();
    }

    @Test
    void duplicateSameSuccessfulResultIsAcceptedWithoutRewritingSourceRows() {
        Fixture fixture = fixture();
        MesMaterialBatchItem item = batchItem("SUCCESS", "MAT-NEW");
        item.setCompletedAt(java.time.LocalDateTime.now().minusMinutes(3));
        OrderDeliveryTicket ticket = ticket("SUCCESS", "MAT-NEW", LINE_ID);
        when(fixture.batchMapper.selectOne(any(Wrapper.class))).thenReturn(batch("SUCCESS"));
        when(fixture.batchItemMapper.selectList(any(Wrapper.class))).thenReturn(List.of(item));
        when(fixture.requestMapper.selectOne(any(Wrapper.class))).thenReturn(request("SUCCESS"));
        when(fixture.ticketMapper.selectById(123)).thenReturn(ticket);

        MesMaterialCallbackResp response = fixture.service.handleMaterialBatchCallback(batchCallback(successItem("MAT-NEW")));

        assertThat(response.getData())
                .containsEntry("receivedCount", 1)
                .containsEntry("appliedCount", 0)
                .containsEntry("duplicateCount", 1);
        verify(fixture.batchItemMapper, never()).updateById(any());
        verify(fixture.ticketMapper, never()).updateById(any());
        verify(fixture.requestMapper, never()).update(any(), any());
        verify(fixture.salesOrderItemMapper, never()).updateById(any());
    }

    /**
     * 已确认料号不能成为「先跳过再校验」的挡箭牌：taskNo 与本行记录的 MES 任务不一致时，
     * 必须报冲突，而不是当成幂等成功吞掉。
     */
    @Test
    void staleSuccessfulCallbackWithMismatchedTaskNoIsRejectedEvenWhenTicketHasMaterialNo() {
        Fixture fixture = fixture();
        MesMaterialBatchItem item = batchItem("FAILED", null);
        item.setMesTaskNo("MP-OLD-TASK");
        OrderDeliveryTicket ticket = ticket("SUCCESS", "MAT-CURRENT", "QPO-MATL-NEWER");
        when(fixture.batchMapper.selectOne(any(Wrapper.class))).thenReturn(batch("FAILED"));
        when(fixture.batchItemMapper.selectList(any(Wrapper.class))).thenReturn(List.of(item));
        when(fixture.requestMapper.selectOne(any(Wrapper.class))).thenReturn(request("FAILED"));
        when(fixture.ticketMapper.selectById(123)).thenReturn(ticket);
        MesMaterialCallbackReq.Item callback = successItem("MAT-OLD");
        callback.setTaskNo("MP-UNRELATED-TASK");

        assertThatThrownBy(() -> fixture.service.handleMaterialBatchCallback(batchCallback(callback)))
                .isInstanceOf(MesMaterialCallbackException.class)
                .hasMessageContaining("taskNo mismatch")
                .satisfies(error -> assertThat(((MesMaterialCallbackException) error).getStatus()).isEqualTo(HttpStatus.CONFLICT));

        verify(fixture.batchMapper, never()).updateById(any());
        verify(fixture.batchItemMapper, never()).updateById(any());
        verify(fixture.ticketMapper, never()).updateById(any());
        verify(fixture.requestMapper, never()).update(any(), any());
        verify(fixture.salesOrderItemMapper, never()).updateById(any());
    }

    @Test
    void staleFailedCallbackIsAcknowledgedWhenTicketAlreadyHasConfirmedMaterialNo() {
        Fixture fixture = fixture();
        MesMaterialBatchItem item = batchItem("FAILED", null);
        OrderDeliveryTicket ticket = ticket("SUCCESS", "MAT-CURRENT", "QPO-MATL-NEWER");
        when(fixture.batchMapper.selectOne(any(Wrapper.class))).thenReturn(batch("FAILED"));
        when(fixture.batchItemMapper.selectList(any(Wrapper.class))).thenReturn(List.of(item));
        when(fixture.requestMapper.selectOne(any(Wrapper.class))).thenReturn(request("FAILED"));
        when(fixture.ticketMapper.selectById(123)).thenReturn(ticket);

        MesMaterialCallbackResp response = fixture.service.handleMaterialBatchCallback(batchCallback(failedItem("Old task failed")));

        assertThat(response.getSuccess()).isTrue();
        assertThat(response.getCode()).isEqualTo(0);
        assertThat(response.getData())
                .containsEntry("appliedCount", 0)
                .containsEntry("duplicateCount", 1);
        verify(fixture.batchMapper, never()).updateById(any());
        verify(fixture.batchItemMapper, never()).updateById(any());
        verify(fixture.ticketMapper, never()).updateById(any());
        verify(fixture.requestMapper, never()).update(any(), any());
        verify(fixture.salesOrderItemMapper, never()).updateById(any());
    }

    @Test
    void singleIncrementalCallbackLeavesBatchPartialWhenOtherRowsRemainPending() {
        Fixture fixture = fixture();
        MesMaterialBatchItem first = batchItem("CALLBACK_PENDING", null);
        MesMaterialBatchItem second = batchItem(21L, 124, "QPO-MATL-OTHER", "P-200|A", "P-200", "A", "CALLBACK_PENDING", null);
        second.setMesTaskNo("MP202608120002");
        when(fixture.batchMapper.selectOne(any(Wrapper.class))).thenReturn(batch("PARTIAL_PENDING"));
        when(fixture.batchItemMapper.selectList(any(Wrapper.class))).thenReturn(List.of(first, second));
        when(fixture.requestMapper.selectOne(any(Wrapper.class))).thenReturn(request("CALLBACK_PENDING"));
        when(fixture.ticketMapper.selectById(123)).thenReturn(ticket("CALLBACK_PENDING", null, LINE_ID));
        when(fixture.salesOrderItemMapper.selectByIdForUpdate(5001L)).thenReturn(new SalesOrderItem());

        MesMaterialCallbackResp response = fixture.service.handleMaterialBatchCallback(batchCallback(successItem("MAT-NEW")));

        assertThat(response.getData()).containsEntry("appliedCount", 1).containsEntry("duplicateCount", 0);
        ArgumentCaptor<MesIntegrationBatch> batchCaptor = ArgumentCaptor.forClass(MesIntegrationBatch.class);
        verify(fixture.batchMapper).updateById(batchCaptor.capture());
        assertThat(batchCaptor.getValue().getStatus()).isEqualTo("PARTIAL_PENDING");
        assertThat(batchCaptor.getValue().getCompletedAt()).isNull();
    }

    @Test
    void resultUnknownRowCanBeClosedByLaterSuccessfulCallback() {
        Fixture fixture = fixture();
        when(fixture.batchMapper.selectOne(any(Wrapper.class))).thenReturn(batch("RESULT_UNKNOWN"));
        when(fixture.batchItemMapper.selectList(any(Wrapper.class))).thenReturn(List.of(batchItem("RESULT_UNKNOWN", null)));
        when(fixture.requestMapper.selectOne(any(Wrapper.class))).thenReturn(request("RESULT_UNKNOWN"));
        when(fixture.ticketMapper.selectById(123)).thenReturn(ticket("RESULT_UNKNOWN", null, LINE_ID));
        when(fixture.salesOrderItemMapper.selectByIdForUpdate(5001L)).thenReturn(new SalesOrderItem());

        MesMaterialCallbackResp response = fixture.service.handleMaterialBatchCallback(batchCallback(successItem("MAT-NEW")));

        assertThat(response.getData()).containsEntry("appliedCount", 1);
        ArgumentCaptor<OrderDeliveryTicket> ticketCaptor = ArgumentCaptor.forClass(OrderDeliveryTicket.class);
        verify(fixture.ticketMapper).updateById(ticketCaptor.capture());
        assertThat(ticketCaptor.getValue().getMesSyncStatus()).isEqualTo("SUCCESS");
        assertThat(ticketCaptor.getValue().getMaterialNo()).isEqualTo("MAT-NEW");
    }

    @Test
    void failedCallbackMarksBatchItemTicketAndRequestFailed() {
        Fixture fixture = fixture();
        MesMaterialCallbackReq.Item failed = failedItem("MES create failed");
        when(fixture.batchMapper.selectOne(any(Wrapper.class))).thenReturn(batch("PARTIAL_PENDING"));
        when(fixture.batchItemMapper.selectList(any(Wrapper.class))).thenReturn(List.of(batchItem("CALLBACK_PENDING", null)));
        when(fixture.requestMapper.selectOne(any(Wrapper.class))).thenReturn(request("CALLBACK_PENDING"));
        when(fixture.ticketMapper.selectById(123)).thenReturn(ticket("CALLBACK_PENDING", null, LINE_ID));

        MesMaterialCallbackResp response = fixture.service.handleMaterialBatchCallback(batchCallback(failed));

        assertThat(response.getData()).containsEntry("appliedCount", 1);
        ArgumentCaptor<MesMaterialBatchItem> batchItemCaptor = ArgumentCaptor.forClass(MesMaterialBatchItem.class);
        verify(fixture.batchItemMapper).updateById(batchItemCaptor.capture());
        assertThat(batchItemCaptor.getValue().getStatus()).isEqualTo("FAILED");
        assertThat(batchItemCaptor.getValue().getProductCode()).isNull();
        ArgumentCaptor<OrderDeliveryTicket> ticketCaptor = ArgumentCaptor.forClass(OrderDeliveryTicket.class);
        verify(fixture.ticketMapper).updateById(ticketCaptor.capture());
        assertThat(ticketCaptor.getValue().getMesSyncStatus()).isEqualTo("FAILED");
        ArgumentCaptor<OrderDeliveryTicketMesRequest> requestCaptor = ArgumentCaptor.forClass(OrderDeliveryTicketMesRequest.class);
        verify(fixture.requestMapper).update(requestCaptor.capture(), any(Wrapper.class));
        assertThat(requestCaptor.getValue().getRequestStatus()).isEqualTo("FAILED");
    }

    @Test
    void successfulCallbackRevivesFailedBatchItemInsteadOfReturningConflict() {
        Fixture fixture = fixture();
        AuditService auditService = mock(AuditService.class);
        ReflectionTestUtils.setField(fixture.service, "auditService", auditService);
        when(fixture.batchMapper.selectOne(any(Wrapper.class))).thenReturn(batch("PARTIAL_PENDING"));
        when(fixture.batchItemMapper.selectList(any(Wrapper.class))).thenReturn(List.of(batchItem("FAILED", null)));
        when(fixture.requestMapper.selectOne(any(Wrapper.class))).thenReturn(request("FAILED"));
        when(fixture.ticketMapper.selectById(123)).thenReturn(ticket("FAILED", null, LINE_ID));
        when(fixture.salesOrderItemMapper.selectByIdForUpdate(5001L)).thenReturn(new SalesOrderItem());

        MesMaterialCallbackResp response = fixture.service.handleMaterialBatchCallback(batchCallback(successItem("MAT-NEW")));

        assertThat(response.getSuccess()).isTrue();
        assertThat(response.getData())
                .containsEntry("appliedCount", 1)
                .containsEntry("duplicateCount", 0);

        ArgumentCaptor<MesMaterialBatchItem> batchItemCaptor = ArgumentCaptor.forClass(MesMaterialBatchItem.class);
        verify(fixture.batchItemMapper).updateById(batchItemCaptor.capture());
        assertThat(batchItemCaptor.getValue().getStatus()).isEqualTo("SUCCESS");
        assertThat(batchItemCaptor.getValue().getProductCode()).isEqualTo("MAT-NEW");

        ArgumentCaptor<OrderDeliveryTicket> ticketCaptor = ArgumentCaptor.forClass(OrderDeliveryTicket.class);
        verify(fixture.ticketMapper).updateById(ticketCaptor.capture());
        assertThat(ticketCaptor.getValue().getMesSyncStatus()).isEqualTo("SUCCESS");
        assertThat(ticketCaptor.getValue().getMaterialNo()).isEqualTo("MAT-NEW");

        ArgumentCaptor<String> actionCaptor = ArgumentCaptor.forClass(String.class);
        verify(auditService, atLeastOnce()).record(
                actionCaptor.capture(), any(), any(), any(), any(), any(), any(), any(), any());
        assertThat(actionCaptor.getAllValues()).contains("DELIVERY_MES_MATERIAL_CALLBACK_REVIVE");
    }

    /**
     * 当前有效请求给出新的有效结果时允许覆盖旧料号，并留下旧值/新值审计。
     */
    @Test
    void successfulCallbackOverwritesTicketMaterialNoWhenCurrentRequestProvesIt() {
        Fixture fixture = fixture();
        AuditService auditService = mock(AuditService.class);
        ReflectionTestUtils.setField(fixture.service, "auditService", auditService);
        when(fixture.batchMapper.selectOne(any(Wrapper.class))).thenReturn(batch("PARTIAL_PENDING"));
        when(fixture.batchItemMapper.selectList(any(Wrapper.class))).thenReturn(List.of(batchItem("FAILED", null)));
        when(fixture.requestMapper.selectOne(any(Wrapper.class))).thenReturn(request("FAILED"));
        when(fixture.ticketMapper.selectById(123)).thenReturn(ticket("FAILED", "MAT-OLD", LINE_ID));
        when(fixture.salesOrderItemMapper.selectByIdForUpdate(5001L)).thenReturn(new SalesOrderItem());

        MesMaterialCallbackResp response = fixture.service.handleMaterialBatchCallback(batchCallback(successItem("MAT-NEW")));

        assertThat(response.getSuccess()).isTrue();
        assertThat(response.getData()).containsEntry("appliedCount", 1);

        ArgumentCaptor<OrderDeliveryTicket> ticketCaptor = ArgumentCaptor.forClass(OrderDeliveryTicket.class);
        verify(fixture.ticketMapper).updateById(ticketCaptor.capture());
        assertThat(ticketCaptor.getValue().getMesSyncStatus()).isEqualTo("SUCCESS");
        assertThat(ticketCaptor.getValue().getMaterialNo()).isEqualTo("MAT-NEW");

        ArgumentCaptor<String> actionCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Object> summaryCaptor = ArgumentCaptor.forClass(Object.class);
        verify(auditService, atLeastOnce()).record(
                actionCaptor.capture(), any(), any(), any(), summaryCaptor.capture(), any(), any(), any(), any());
        int overwriteIndex = actionCaptor.getAllValues().indexOf("DELIVERY_MES_MATERIAL_CALLBACK_OVERWRITE");
        assertThat(overwriteIndex).isGreaterThanOrEqualTo(0);
        @SuppressWarnings("unchecked")
        Map<String, Object> overwriteSummary = (Map<String, Object>) summaryCaptor.getAllValues().get(overwriteIndex);
        assertThat(overwriteSummary)
                .containsEntry("previousMaterialNo", "MAT-OLD")
                .containsEntry("materialNo", "MAT-NEW")
                .containsEntry("mesRequestId", LINE_ID);
    }

    /**
     * 已确认料号属于另一个更新的有效请求时，旧请求的迟到成功回调不得覆盖。
     */
    @Test
    void supersededRequestCannotOverwriteMaterialNoConfirmedByNewerRequest() {
        Fixture fixture = fixture();
        when(fixture.batchMapper.selectOne(any(Wrapper.class))).thenReturn(batch("SUCCESS"));
        when(fixture.batchItemMapper.selectList(any(Wrapper.class))).thenReturn(List.of(batchItem("SUCCESS", "MAT-OLD")));
        when(fixture.requestMapper.selectOne(any(Wrapper.class))).thenReturn(request("SUCCESS"));
        when(fixture.ticketMapper.selectById(123)).thenReturn(ticket("SUCCESS", "MAT-CURRENT", "QPO-MATL-NEWER"));

        assertThatThrownBy(() -> fixture.service.handleMaterialBatchCallback(batchCallback(successItem("MAT-OLD"))))
                .isInstanceOf(MesMaterialCallbackException.class)
                .hasMessageContaining("superseded request")
                .satisfies(error -> assertThat(((MesMaterialCallbackException) error).getStatus()).isEqualTo(HttpStatus.CONFLICT));

        verify(fixture.batchItemMapper, never()).updateById(any());
        verify(fixture.ticketMapper, never()).updateById(any());
        verify(fixture.requestMapper, never()).update(any(), any());
        verify(fixture.salesOrderItemMapper, never()).updateById(any());
    }

    /**
     * 同一有效请求回不同物料号时允许覆盖并审计，不再返回 409。
     */
    @Test
    void differentProductCodeFromCurrentRequestOverwritesConfirmedMaterialNo() {
        Fixture fixture = fixture();
        AuditService auditService = mock(AuditService.class);
        ReflectionTestUtils.setField(fixture.service, "auditService", auditService);
        when(fixture.batchMapper.selectOne(any(Wrapper.class))).thenReturn(batch("SUCCESS"));
        when(fixture.batchItemMapper.selectList(any(Wrapper.class))).thenReturn(List.of(batchItem("SUCCESS", "MAT-OLD")));
        when(fixture.requestMapper.selectOne(any(Wrapper.class))).thenReturn(request("SUCCESS"));
        when(fixture.ticketMapper.selectById(123)).thenReturn(ticket("SUCCESS", "MAT-OLD", LINE_ID));
        when(fixture.salesOrderItemMapper.selectByIdForUpdate(5001L)).thenReturn(new SalesOrderItem());

        MesMaterialCallbackResp response = fixture.service.handleMaterialBatchCallback(batchCallback(successItem("MAT-NEW")));

        assertThat(response.getSuccess()).isTrue();
        assertThat(response.getCode()).isEqualTo(0);
        assertThat(response.getData()).containsEntry("appliedCount", 1).containsEntry("duplicateCount", 0);

        ArgumentCaptor<OrderDeliveryTicket> ticketCaptor = ArgumentCaptor.forClass(OrderDeliveryTicket.class);
        verify(fixture.ticketMapper).updateById(ticketCaptor.capture());
        assertThat(ticketCaptor.getValue().getMaterialNo()).isEqualTo("MAT-NEW");
        assertThat(ticketCaptor.getValue().getMesSyncStatus()).isEqualTo("SUCCESS");

        ArgumentCaptor<String> actionCaptor = ArgumentCaptor.forClass(String.class);
        verify(auditService, atLeastOnce()).record(
                actionCaptor.capture(), any(), any(), any(), any(), any(), any(), any(), any());
        assertThat(actionCaptor.getAllValues()).contains("DELIVERY_MES_MATERIAL_CALLBACK_OVERWRITE");
    }

    /**
     * 回调必须按 bizId + taskNo 定位批次行。taskNo 重复时无法判定归属，
     * 必须在任何数据库写入前整体拒绝，而不是静默覆盖批次索引。
     */
    @Test
    void duplicateTaskNoInSameCallbackIsRejectedBeforeAnyDatabaseLookup() {
        Fixture fixture = fixture();

        assertThatThrownBy(() -> fixture.service.handleMaterialBatchCallback(batchCallback(
                successItem("MAT-NEW"), successItem("MAT-NEW-2"))))
                .isInstanceOf(MesMaterialCallbackException.class)
                .hasMessageContaining("duplicate taskNo")
                .satisfies(error -> assertThat(((MesMaterialCallbackException) error).getStatus()).isEqualTo(HttpStatus.BAD_REQUEST));

        verifyNoInteractions(fixture.batchMapper);
        verifyNoInteractions(fixture.batchItemMapper);
        verifyNoInteractions(fixture.ticketMapper);
        verifyNoInteractions(fixture.requestMapper);
    }

    @Test
    void duplicateDrawingVersionWithDistinctTaskNoIsRejectedBeforeAnyDatabaseLookup() {
        Fixture fixture = fixture();
        MesMaterialCallbackReq.Item other = successItem("MAT-NEW-2");
        other.setTaskNo("MP202608120002");

        assertThatThrownBy(() -> fixture.service.handleMaterialBatchCallback(batchCallback(
                successItem("MAT-NEW"), other)))
                .isInstanceOf(MesMaterialCallbackException.class)
                .hasMessageContaining("duplicate drawing/version")
                .satisfies(error -> assertThat(((MesMaterialCallbackException) error).getStatus()).isEqualTo(HttpStatus.BAD_REQUEST));

        verifyNoInteractions(fixture.batchMapper);
        verifyNoInteractions(fixture.batchItemMapper);
        verifyNoInteractions(fixture.ticketMapper);
        verifyNoInteractions(fixture.requestMapper);
    }

    @Test
    void invalidRowInMultiRowCallbackRejectsWholeBatchBeforeWrites() {
        Fixture fixture = fixture();
        MesMaterialCallbackReq.Item invalid = successItem("   ");
        invalid.setDrawingNo("P-200");

        assertThatThrownBy(() -> fixture.service.handleMaterialBatchCallback(batchCallback(successItem("MAT-NEW"), invalid)))
                .isInstanceOf(MesMaterialCallbackException.class)
                .satisfies(error -> assertThat(((MesMaterialCallbackException) error).getStatus()).isEqualTo(HttpStatus.BAD_REQUEST));

        verifyNoInteractions(fixture.batchMapper);
        verifyNoInteractions(fixture.batchItemMapper);
        verifyNoInteractions(fixture.ticketMapper);
        verifyNoInteractions(fixture.requestMapper);
    }

    @Test
    void callbackForSupersededLineRequestIsAcceptedBecauseTicketHasNoMaterialNoYet() {
        Fixture fixture = fixture();
        when(fixture.batchMapper.selectOne(any(Wrapper.class))).thenReturn(batch("PARTIAL_PENDING"));
        when(fixture.batchItemMapper.selectList(any(Wrapper.class))).thenReturn(List.of(batchItem("CALLBACK_PENDING", null)));
        when(fixture.requestMapper.selectOne(any(Wrapper.class))).thenReturn(request("CALLBACK_PENDING"));
        when(fixture.ticketMapper.selectById(123)).thenReturn(ticket("CALLBACK_PENDING", null, "QPO-MATL-NEWER"));
        when(fixture.salesOrderItemMapper.selectByIdForUpdate(5001L)).thenReturn(new SalesOrderItem());

        MesMaterialCallbackResp response = fixture.service.handleMaterialBatchCallback(batchCallback(successItem("MAT-NEW")));

        assertThat(response.getSuccess()).isTrue();
        assertThat(response.getCode()).isEqualTo(0);
        assertThat(response.getData()).containsEntry("appliedCount", 1).containsEntry("duplicateCount", 0);

        ArgumentCaptor<OrderDeliveryTicket> ticketCaptor = ArgumentCaptor.forClass(OrderDeliveryTicket.class);
        verify(fixture.ticketMapper).updateById(ticketCaptor.capture());
        assertThat(ticketCaptor.getValue().getMesSyncStatus()).isEqualTo("SUCCESS");
        assertThat(ticketCaptor.getValue().getMaterialNo()).isEqualTo("MAT-NEW");
        assertThat(ticketCaptor.getValue().getMesRequestId()).isEqualTo(LINE_ID);
    }

    @Test
    void failedBatchItemAcceptsLateSuccessAfterTicketWasSupersededByRetry() {
        Fixture fixture = fixture();
        AuditService auditService = mock(AuditService.class);
        ReflectionTestUtils.setField(fixture.service, "auditService", auditService);
        when(fixture.batchMapper.selectOne(any(Wrapper.class))).thenReturn(batch("FAILED"));
        when(fixture.batchItemMapper.selectList(any(Wrapper.class))).thenReturn(List.of(batchItem("FAILED", null)));
        when(fixture.requestMapper.selectOne(any(Wrapper.class))).thenReturn(request("FAILED"));
        when(fixture.ticketMapper.selectById(123)).thenReturn(ticket("FAILED", null, "QPO-MATL-RETRY"));
        when(fixture.salesOrderItemMapper.selectByIdForUpdate(5001L)).thenReturn(new SalesOrderItem());

        MesMaterialCallbackResp response = fixture.service.handleMaterialBatchCallback(batchCallback(successItem("MAT-NEW")));

        assertThat(response.getSuccess()).isTrue();
        assertThat(response.getData()).containsEntry("appliedCount", 1);

        ArgumentCaptor<OrderDeliveryTicket> ticketCaptor = ArgumentCaptor.forClass(OrderDeliveryTicket.class);
        verify(fixture.ticketMapper).updateById(ticketCaptor.capture());
        assertThat(ticketCaptor.getValue().getMesSyncStatus()).isEqualTo("SUCCESS");
        assertThat(ticketCaptor.getValue().getMaterialNo()).isEqualTo("MAT-NEW");

        ArgumentCaptor<String> actionCaptor = ArgumentCaptor.forClass(String.class);
        verify(auditService, atLeastOnce()).record(
                actionCaptor.capture(), any(), any(), any(), any(), any(), any(), any(), any());
        assertThat(actionCaptor.getAllValues()).contains("DELIVERY_MES_MATERIAL_CALLBACK_REVIVE");
    }

    @Test
    void supersededLineRequestIsStillRejectedWhenTaskNoBelongsToAnotherMesTask() {
        Fixture fixture = fixture();
        MesMaterialCallbackReq.Item item = successItem("MAT-NEW");
        item.setTaskNo("MP-OTHER-TASK");
        when(fixture.batchMapper.selectOne(any(Wrapper.class))).thenReturn(batch("PARTIAL_PENDING"));
        when(fixture.batchItemMapper.selectList(any(Wrapper.class))).thenReturn(List.of(batchItem("CALLBACK_PENDING", null)));
        when(fixture.requestMapper.selectOne(any(Wrapper.class))).thenReturn(request("CALLBACK_PENDING"));
        when(fixture.ticketMapper.selectById(123)).thenReturn(ticket("CALLBACK_PENDING", null, "QPO-MATL-NEWER"));

        assertThatThrownBy(() -> fixture.service.handleMaterialBatchCallback(batchCallback(item)))
                .isInstanceOf(MesMaterialCallbackException.class)
                .hasMessageContaining("taskNo mismatch");

        verify(fixture.batchItemMapper, never()).updateById(any());
        verify(fixture.ticketMapper, never()).updateById(any());
        verify(fixture.requestMapper, never()).update(any(), any());
    }

    @Test
    void taskNoMismatchIsRejectedWithConflictAndNoWrites() {
        Fixture fixture = fixture();
        MesMaterialCallbackReq.Item item = successItem("MAT-NEW");
        item.setTaskNo("MP-DIFFERENT");
        when(fixture.batchMapper.selectOne(any(Wrapper.class))).thenReturn(batch("PARTIAL_PENDING"));
        when(fixture.batchItemMapper.selectList(any(Wrapper.class))).thenReturn(List.of(batchItem("CALLBACK_PENDING", null)));
        when(fixture.requestMapper.selectOne(any(Wrapper.class))).thenReturn(request("CALLBACK_PENDING"));
        when(fixture.ticketMapper.selectById(123)).thenReturn(ticket("CALLBACK_PENDING", null, LINE_ID));

        assertThatThrownBy(() -> fixture.service.handleMaterialBatchCallback(batchCallback(item)))
                .isInstanceOf(MesMaterialCallbackException.class)
                .satisfies(error -> assertThat(((MesMaterialCallbackException) error).getStatus()).isEqualTo(HttpStatus.CONFLICT));

        verify(fixture.batchItemMapper, never()).updateById(any());
        verify(fixture.ticketMapper, never()).updateById(any());
        verify(fixture.requestMapper, never()).update(any(), any());
    }

    @Test
    void moreThanOneHundredItemsAreRejectedBeforeAnyDatabaseLookup() {
        Fixture fixture = fixture();
        MesMaterialCallbackReq req = new MesMaterialCallbackReq();
        req.setMesRequestId(BIZ_ID);
        req.setItems(java.util.stream.IntStream.rangeClosed(1, 101)
                .mapToObj(index -> {
                    MesMaterialCallbackReq.Item item = successItem("MAT-" + index);
                    item.setDrawingNo("P-" + index);
                    return item;
                })
                .toList());

        assertThatThrownBy(() -> fixture.service.handleMaterialBatchCallback(req))
                .isInstanceOf(MesMaterialCallbackException.class)
                .satisfies(error -> assertThat(((MesMaterialCallbackException) error).getStatus()).isEqualTo(HttpStatus.BAD_REQUEST));

        verifyNoInteractions(fixture.batchMapper);
        verifyNoInteractions(fixture.batchItemMapper);
    }

    @Test
    void controllerAlwaysReturnsHttpOkWithBusinessCodeForBatchCallbackFailures() {
        MesMaterialSyncService service = mock(MesMaterialSyncService.class);
        SystemInboundCallbackConfigService configService = mock(SystemInboundCallbackConfigService.class);
        MesIntegrationCallbackController controller = new MesIntegrationCallbackController();
        ReflectionTestUtils.setField(controller, "mesMaterialSyncService", service);
        ReflectionTestUtils.setField(controller, "inboundCallbackConfigService", configService);
        when(configService.hasValidOpenApiBearerToken(InboundCallbackCode.MES_MATERIAL_CALLBACK, "Bearer qpo-open-api-token")).thenReturn(true);
        when(service.handleMaterialBatchCallback(any(MesMaterialCallbackReq.class)))
                .thenThrow(new MesMaterialCallbackException(HttpStatus.CONFLICT, "MES material callback conflict"));

        ResponseEntity<MesMaterialCallbackResp> response = controller.materialCallback(
                "Bearer qpo-open-api-token", batchCallback(successItem("MAT-NEW")));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getSuccess()).isFalse();
        assertThat(response.getBody().getCode()).isEqualTo(HttpStatus.CONFLICT.value());
        assertThat(response.getBody().getMsg()).contains("conflict");
        assertThat(response.getBody().getData())
                .containsEntry("bizId", BIZ_ID)
                .containsEntry("appliedCount", 0);
    }

    @Test
    void controllerDoesNotExposeUnexpectedExceptionTextInBatchFailureBody() {
        MesMaterialSyncService service = mock(MesMaterialSyncService.class);
        SystemInboundCallbackConfigService configService = mock(SystemInboundCallbackConfigService.class);
        MesIntegrationCallbackController controller = new MesIntegrationCallbackController();
        ReflectionTestUtils.setField(controller, "mesMaterialSyncService", service);
        ReflectionTestUtils.setField(controller, "inboundCallbackConfigService", configService);
        when(configService.hasValidOpenApiBearerToken(InboundCallbackCode.MES_MATERIAL_CALLBACK, "Bearer qpo-open-api-token")).thenReturn(true);
        String sensitiveExceptionText = "Author" + "ization: Bearer " + "secret" + "-token full " + "request body";
        when(service.handleMaterialBatchCallback(any(MesMaterialCallbackReq.class)))
                .thenThrow(new RuntimeException(sensitiveExceptionText));

        ResponseEntity<MesMaterialCallbackResp> response = controller.materialCallback(
                "Bearer qpo-open-api-token", batchCallback(successItem("MAT-NEW")));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR.value());
        assertThat(response.getBody().getMsg()).isEqualTo("MES material callback handling failed");
        assertThat(response.getBody().getMsg())
                .doesNotContain("secret" + "-token")
                .doesNotContain("Author" + "ization")
                .doesNotContain("request" + " body");
    }

    @Test
    void controllerRejectsFlatMaterialCallbackInsteadOfFallingBackToLineCallback() {
        MesMaterialSyncService service = mock(MesMaterialSyncService.class);
        SystemInboundCallbackConfigService configService = mock(SystemInboundCallbackConfigService.class);
        MesIntegrationCallbackController controller = new MesIntegrationCallbackController();
        ReflectionTestUtils.setField(controller, "mesMaterialSyncService", service);
        ReflectionTestUtils.setField(controller, "inboundCallbackConfigService", configService);
        when(configService.hasValidOpenApiBearerToken(InboundCallbackCode.MES_MATERIAL_CALLBACK, "Bearer qpo-open-api-token")).thenReturn(true);
        when(service.handleMaterialBatchCallback(any(MesMaterialCallbackReq.class)))
                .thenThrow(new MesMaterialCallbackException(HttpStatus.BAD_REQUEST,
                        "MES material callback items cannot be empty"));
        MesMaterialCallbackReq req = new MesMaterialCallbackReq();
        req.setMesRequestId(BIZ_ID);
        req.setStatus("SUCCESS");
        req.setMaterialNo("MAT-NEW");

        ResponseEntity<MesMaterialCallbackResp> response = controller.materialCallback("Bearer qpo-open-api-token", req);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getCode()).isEqualTo(HttpStatus.BAD_REQUEST.value());
        assertThat(response.getBody().getMsg()).contains("items cannot be empty");
        verify(service).handleMaterialBatchCallback(req);
        verify(service, never()).handleMaterialCallback(any(MesMaterialCallbackReq.class));
    }

    @Test
    void batchCallbackRequestAcceptsBizIdItemsShape() throws Exception {
        MesMaterialCallbackReq req = new ObjectMapper().readValue("""
                {
                  "bizId": "QPO-MAT-20260812-01J5ABCDEF",
                  "items": [
                    {
                      "taskNo": "MP202608120001",
                      "drawingNo": "P-100",
                      "drawingVersion": "A",
                      "status": "SUCCESS",
                      "productCode": "MAT-NEW",
                      "productName": "Precision plate",
                      "msg": "MES completed"
                    }
                  ]
                }
                """, MesMaterialCallbackReq.class);

        assertThat(req.getMesRequestId()).isEqualTo(BIZ_ID);
        assertThat(req.getItems()).hasSize(1);
        assertThat(req.getItems().get(0).getProductCode()).isEqualTo("MAT-NEW");
        assertThat(req.getItems().get(0).getMessage()).isEqualTo("MES completed");
    }

    private Fixture fixture() {
        Fixture fixture = new Fixture();
        fixture.service = new MesMaterialSyncService();
        fixture.batchMapper = mock(MesIntegrationBatchMapper.class);
        fixture.batchItemMapper = mock(MesMaterialBatchItemMapper.class);
        fixture.ticketMapper = mock(OrderDeliveryTicketMapper.class);
        fixture.requestMapper = mock(OrderDeliveryTicketMesRequestMapper.class);
        fixture.salesOrderItemMapper = mock(SalesOrderItemMapper.class);
        fixture.sourceSyncService = new SourceItemMaterialNoSyncService();
        ReflectionTestUtils.setField(fixture.sourceSyncService, "salesOrderItemMapper", fixture.salesOrderItemMapper);
        ReflectionTestUtils.setField(fixture.service, "mesIntegrationBatchMapper", fixture.batchMapper);
        ReflectionTestUtils.setField(fixture.service, "mesMaterialBatchItemMapper", fixture.batchItemMapper);
        ReflectionTestUtils.setField(fixture.service, "ticketMapper", fixture.ticketMapper);
        ReflectionTestUtils.setField(fixture.service, "mesRequestMapper", fixture.requestMapper);
        ReflectionTestUtils.setField(fixture.service, "salesOrderItemMapper", fixture.salesOrderItemMapper);
        ReflectionTestUtils.setField(fixture.service, "sourceItemMaterialNoSyncService", fixture.sourceSyncService);
        return fixture;
    }

    private MesMaterialCallbackReq batchCallback(MesMaterialCallbackReq.Item... items) {
        MesMaterialCallbackReq req = new MesMaterialCallbackReq();
        req.setMesRequestId(BIZ_ID);
        req.setItems(List.of(items));
        return req;
    }

    private MesMaterialCallbackReq.Item successItem(String productCode) {
        MesMaterialCallbackReq.Item item = new MesMaterialCallbackReq.Item();
        item.setTaskNo("MP202608120001");
        item.setDrawingNo("P-100");
        item.setDrawingVersion("A");
        item.setStatus("SUCCESS");
        item.setProductCode(productCode);
        item.setProductName("Precision plate");
        item.setMessage("MES completed");
        return item;
    }

    private MesMaterialCallbackReq.Item failedItem(String message) {
        MesMaterialCallbackReq.Item item = new MesMaterialCallbackReq.Item();
        item.setTaskNo("MP202608120001");
        item.setDrawingNo("P-100");
        item.setDrawingVersion("A");
        item.setStatus("FAILED");
        item.setMessage(message);
        return item;
    }

    private MesIntegrationBatch batch(String status) {
        MesIntegrationBatch batch = new MesIntegrationBatch();
        batch.setId(10L);
        batch.setBatchType("MATERIAL_PREPARATION");
        batch.setBizId(BIZ_ID);
        batch.setItemCount(1);
        batch.setStatus(status);
        return batch;
    }

    private MesMaterialBatchItem batchItem(String status, String productCode) {
        return batchItem(20L, 123, LINE_ID, "P-100|A", "P-100", "A", status, productCode);
    }

    private MesMaterialBatchItem batchItem(
            Long id,
            Integer ticketId,
            String lineRequestId,
            String itemKey,
            String drawingNo,
            String drawingVersion,
            String status,
            String productCode) {
        MesMaterialBatchItem item = new MesMaterialBatchItem();
        item.setId(id);
        item.setBatchId(10L);
        item.setTicketId(ticketId);
        item.setLineRequestId(lineRequestId);
        item.setMesRequestRowId(9001L);
        item.setDrawingNo(drawingNo);
        item.setDrawingVersion(drawingVersion);
        item.setItemKey(itemKey);
        item.setStatus(status);
        item.setMesTaskNo("MP202608120001");
        item.setProductCode(productCode);
        return item;
    }

    private OrderDeliveryTicketMesRequest request(String status) {
        OrderDeliveryTicketMesRequest request = new OrderDeliveryTicketMesRequest();
        request.setId(9001L);
        request.setTicketId(123);
        request.setMesRequestId(LINE_ID);
        request.setBatchId(10L);
        request.setBatchItemId(20L);
        request.setRequestStatus(status);
        request.setMesExternalTaskId("MP202608120001");
        return request;
    }

    private OrderDeliveryTicket ticket(String status, String materialNo, String lineRequestId) {
        OrderDeliveryTicket ticket = new OrderDeliveryTicket();
        ticket.setId(123);
        ticket.setSourceType("SALES_ORDER");
        ticket.setSourceOrderId(9001L);
        ticket.setSourceItemId(5001L);
        ticket.setPartNo("P-100");
        ticket.setVersion("A");
        ticket.setInternalProjectNo("IP-9");
        ticket.setMesRequestId(lineRequestId);
        ticket.setMesSyncStatus(status);
        ticket.setMaterialNo(materialNo);
        return ticket;
    }

    private static class Fixture {
        private MesMaterialSyncService service;
        private MesIntegrationBatchMapper batchMapper;
        private MesMaterialBatchItemMapper batchItemMapper;
        private OrderDeliveryTicketMapper ticketMapper;
        private OrderDeliveryTicketMesRequestMapper requestMapper;
        private SalesOrderItemMapper salesOrderItemMapper;
        private SourceItemMaterialNoSyncService sourceSyncService;
    }
}
