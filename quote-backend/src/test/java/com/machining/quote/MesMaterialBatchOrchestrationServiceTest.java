package com.machining.quote;

import com.machining.quote.dto.mes.MesMaterialResolveReq;
import com.machining.quote.dto.mes.MesMaterialResolveResp;
import com.machining.quote.entity.ItemQuoteVersion;
import com.machining.quote.entity.OrderDeliveryTicket;
import com.machining.quote.entity.OrderDeliveryTicketMesRequest;
import com.machining.quote.entity.OrderRfq;
import com.machining.quote.mapper.ItemQuoteVersionMapper;
import com.machining.quote.mapper.OrderDeliveryTicketMapper;
import com.machining.quote.mapper.OrderDeliveryTicketMesRequestMapper;
import com.machining.quote.mapper.OrderRfqMapper;
import com.machining.quote.service.DeliveryIntegrationBatchService;
import com.machining.quote.service.AuditService;
import com.machining.quote.service.SourceItemMaterialNoSyncService;
import com.machining.quote.service.mes.MesMaterialClient;
import com.machining.quote.service.mes.MesMaterialDedupService;
import com.machining.quote.service.mes.MesMaterialSyncService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MesMaterialBatchOrchestrationServiceTest {
    @Test
    @SuppressWarnings("unchecked")
    void materialBatchCallsMesOnceForSameCustomerProjectAndKeepsPendingCreateOpen() {
        Fixture fixture = fixture();
        OrderDeliveryTicket existing = ticket(601, "P-EXIST", "A", "IP-BATCH-1");
        OrderDeliveryTicket pending = ticket(602, "P-PENDING", "B", "IP-BATCH-1");
        fixture.withTicket(existing);
        fixture.withTicket(pending);
        when(fixture.client.resolveMaterial(any())).thenAnswer(invocation -> {
            MesMaterialResolveReq req = invocation.getArgument(0);
            return response(req,
                    item("P-EXIST", "A", "EXISTING_PRODUCT", "MAT-EXIST-601", "TASK-601"),
                    item("P-PENDING", "B", "PENDING_CREATE", null, "TASK-602"));
        });
        when(fixture.sourceSync.syncConfirmedMaterialNo(any(), anyString(), anyString(), eq(true)))
                .thenReturn(SourceItemMaterialNoSyncService.MaterialNoSyncResult.empty());

        Map<String, Object> result = fixture.batchService.syncMesMaterialBatch(List.of(601, 602), 9601L);

        assertThat(result)
                .containsEntry("totalCount", 2)
                .containsEntry("successCount", 1)
                .containsEntry("callbackPendingCount", 1)
                .containsEntry("failedCount", 0)
                .containsEntry("batchCount", 1)
                .containsEntry("submittedToMesCount", 2);
        List<Map<String, Object>> rows = (List<Map<String, Object>>) result.get("results");
        assertThat(rows).extracting(row -> row.get("status"))
                .containsExactly("SUCCESS", "CALLBACK_PENDING");
        Map<String, Object> firstRowResult = (Map<String, Object>) rows.get(0).get("result");
        Object bizId = firstRowResult.get("bizId");
        assertThat(firstRowResult)
                .containsEntry("materialNo", "MAT-EXIST-601")
                .containsKey("bizId");
        assertThat(String.valueOf(bizId)).startsWith("QPO-MAT-");
        assertThat(rows.get(1).get("result")).asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.MAP)
                .containsEntry("status", "CALLBACK_PENDING")
                .containsEntry("bizId", bizId)
                .doesNotContainKey("materialNo");

        ArgumentCaptor<MesMaterialResolveReq> reqCaptor = ArgumentCaptor.forClass(MesMaterialResolveReq.class);
        verify(fixture.client, times(1)).resolveMaterial(reqCaptor.capture());
        MesMaterialResolveReq req = reqCaptor.getValue();
        assertThat(req.getBizId()).isEqualTo(bizId);
        assertThat(req.getBizId()).startsWith("QPO-MAT-");
        assertThat(req.getItems()).hasSize(2);
        assertThat(req.getItems()).extracting(MesMaterialResolveReq.Item::getDrawingNo)
                .containsExactly("P-EXIST", "P-PENDING");
        assertThat(req.getItems().get(0).getCustomerRequirements())
                .isEqualTo("材料名称：Material note P-EXIST\n表面处理：Anodized");
        assertThat(req.getItems()).extracting(MesMaterialResolveReq.Item::getMaterial)
                .containsExactly("Material note P-EXIST", "Material note P-PENDING");
        assertThat(req.getItems()).extracting(MesMaterialResolveReq.Item::getName)
                .containsExactly("Precision bracket P-EXIST", "Precision bracket P-PENDING");
        verify(fixture.sourceSync).syncConfirmedMaterialNo(existing, "MAT-EXIST-601", "MES material resolved", true);
    }

    @Test
    @SuppressWarnings("unchecked")
    void materialBatchCallsMesWithoutDrawingUrlsWhenTicketAndSourceItemHaveNoDrawing() {
        Fixture fixture = fixture();
        OrderDeliveryTicket noDrawing = ticket(605, "P-NO-DRAWING", "A", "IP-BATCH-NO-DRAWING");
        noDrawing.setDrawingUrl(null);
        fixture.withTicket(noDrawing, null);
        when(fixture.client.resolveMaterial(any())).thenAnswer(invocation -> {
            MesMaterialResolveReq req = invocation.getArgument(0);
            return response(req, item("P-NO-DRAWING", "A", "EXISTING_PRODUCT", "MAT-NO-DRAWING-605", "TASK-605"));
        });
        when(fixture.sourceSync.syncConfirmedMaterialNo(any(), anyString(), anyString(), eq(true)))
                .thenReturn(SourceItemMaterialNoSyncService.MaterialNoSyncResult.empty());

        Map<String, Object> result = fixture.batchService.syncMesMaterialBatch(List.of(605), 9605L);

        assertThat(result)
                .containsEntry("totalCount", 1)
                .containsEntry("successCount", 1)
                .containsEntry("failedCount", 0);
        List<Map<String, Object>> rows = (List<Map<String, Object>>) result.get("results");
        assertThat(rows).singleElement()
                .satisfies(row -> assertThat(row)
                        .containsEntry("ticketId", 605)
                        .containsEntry("status", "SUCCESS"));
        ArgumentCaptor<MesMaterialResolveReq> reqCaptor = ArgumentCaptor.forClass(MesMaterialResolveReq.class);
        verify(fixture.client).resolveMaterial(reqCaptor.capture());
        MesMaterialResolveReq.Item sentItem = reqCaptor.getValue().getItems().get(0);
        assertThat(sentItem.getDrawingNo()).isEqualTo("P-NO-DRAWING");
        assertThat(sentItem.getDrawingVersion()).isEqualTo("A");
        assertThat(sentItem.getDrawingUrls()).isNull();
        assertThat(sentItem.getMaterial()).isEqualTo("Material note P-NO-DRAWING");
        assertThat(sentItem.getName()).isEqualTo("Precision bracket P-NO-DRAWING");
    }

    @Test
    @SuppressWarnings("unchecked")
    void materialBatchDoesNotLetOneFailedGroupBlockAnotherGroup() {
        Fixture fixture = fixture();
        OrderDeliveryTicket failedGroup = ticket(611, "P-FAIL", "A", "IP-FAIL");
        OrderDeliveryTicket okGroup = ticket(612, "P-OK", "A", "IP-OK");
        fixture.withTicket(failedGroup);
        fixture.withTicket(okGroup);
        when(fixture.client.resolveMaterial(any())).thenAnswer(invocation -> {
            MesMaterialResolveReq req = invocation.getArgument(0);
            if ("IP-FAIL".equals(req.getInternalProjectNo())) {
                throw new RuntimeException("MES group failed");
            }
            return response(req, item("P-OK", "A", "EXISTING_PRODUCT", "MAT-OK-612", "TASK-612"));
        });
        when(fixture.sourceSync.syncConfirmedMaterialNo(any(), anyString(), anyString(), eq(true)))
                .thenReturn(SourceItemMaterialNoSyncService.MaterialNoSyncResult.empty());

        Map<String, Object> result = fixture.batchService.syncMesMaterialBatch(List.of(611, 612), 9602L);

        assertThat(result)
                .containsEntry("totalCount", 2)
                .containsEntry("successCount", 1)
                .containsEntry("failedCount", 1);
        List<Map<String, Object>> rows = (List<Map<String, Object>>) result.get("results");
        assertThat(rows).extracting(row -> row.get("ticketId"))
                .containsExactly(611, 612);
        assertThat(rows).extracting(row -> row.get("status"))
                .containsExactly("FAILED", "SUCCESS");
        verify(fixture.client, times(2)).resolveMaterial(any());
        verify(fixture.sourceSync).syncConfirmedMaterialNo(okGroup, "MAT-OK-612", "MES material resolved", true);
    }

    @Test
    @SuppressWarnings("unchecked")
    void materialBatchFailsOnlyTheItemWhoseResponseRowIsMissing() {
        Fixture fixture = fixture();
        OrderDeliveryTicket ok = ticket(621, "P-OK-MISSING", "A", "IP-BATCH-MISSING");
        OrderDeliveryTicket missing = ticket(622, "P-MISSING", "A", "IP-BATCH-MISSING");
        fixture.withTicket(ok);
        fixture.withTicket(missing);
        when(fixture.client.resolveMaterial(any())).thenAnswer(invocation -> {
            MesMaterialResolveReq req = invocation.getArgument(0);
            return response(req, item("P-OK-MISSING", "A", "EXISTING_PRODUCT", "MAT-OK-621", "TASK-621"));
        });
        when(fixture.sourceSync.syncConfirmedMaterialNo(any(), anyString(), anyString(), eq(true)))
                .thenReturn(SourceItemMaterialNoSyncService.MaterialNoSyncResult.empty());

        Map<String, Object> result = fixture.batchService.syncMesMaterialBatch(List.of(621, 622), 9603L);

        assertThat(result)
                .containsEntry("successCount", 1)
                .containsEntry("failedCount", 0)
                .containsEntry("unknownCount", 1);
        List<Map<String, Object>> rows = (List<Map<String, Object>>) result.get("results");
        assertThat(rows).extracting(row -> row.get("ticketId"))
                .containsExactly(621, 622);
        assertThat(rows).extracting(row -> row.get("status"))
                .containsExactly("SUCCESS", "RESULT_UNKNOWN");
        assertThat(rows.get(1).get("message")).asString().contains("missing row");
        verify(fixture.sourceSync).syncConfirmedMaterialNo(ok, "MAT-OK-621", "MES material resolved", true);
        verify(fixture.sourceSync, times(1)).syncConfirmedMaterialNo(any(), anyString(), anyString(), eq(true));
    }

    @Test
    @SuppressWarnings("unchecked")
    void materialBatchFailsOnlyTheItemWhoseResponseRowIsDuplicated() {
        Fixture fixture = fixture();
        OrderDeliveryTicket duplicated = ticket(631, "P-DUP-RESP", "A", "IP-BATCH-DUP");
        OrderDeliveryTicket ok = ticket(632, "P-OK-DUP", "A", "IP-BATCH-DUP");
        fixture.withTicket(duplicated);
        fixture.withTicket(ok);
        when(fixture.client.resolveMaterial(any())).thenAnswer(invocation -> {
            MesMaterialResolveReq req = invocation.getArgument(0);
            return response(req,
                    item("P-DUP-RESP", "A", "EXISTING_PRODUCT", "MAT-DUP-FIRST", "TASK-631-A"),
                    item("P-DUP-RESP", "A", "EXISTING_PRODUCT", "MAT-DUP-SECOND", "TASK-631-B"),
                    item("P-OK-DUP", "A", "EXISTING_PRODUCT", "MAT-OK-632", "TASK-632"));
        });
        when(fixture.sourceSync.syncConfirmedMaterialNo(any(), anyString(), anyString(), eq(true)))
                .thenReturn(SourceItemMaterialNoSyncService.MaterialNoSyncResult.empty());

        Map<String, Object> result = fixture.batchService.syncMesMaterialBatch(List.of(631, 632), 9604L);

        assertThat(result)
                .containsEntry("successCount", 1)
                .containsEntry("failedCount", 0)
                .containsEntry("unknownCount", 1);
        List<Map<String, Object>> rows = (List<Map<String, Object>>) result.get("results");
        assertThat(rows).extracting(row -> row.get("ticketId"))
                .containsExactly(631, 632);
        assertThat(rows).extracting(row -> row.get("status"))
                .containsExactly("RESULT_UNKNOWN", "SUCCESS");
        assertThat(rows.get(0).get("message")).asString().contains("duplicate rows");
        verify(fixture.sourceSync).syncConfirmedMaterialNo(ok, "MAT-OK-632", "MES material resolved", true);
        verify(fixture.sourceSync, times(1)).syncConfirmedMaterialNo(any(), anyString(), anyString(), eq(true));
    }

    @Test
    @SuppressWarnings("unchecked")
    void conflictingRootAndDataBizIdsMakeMaterialResultUnknown() {
        Fixture fixture = fixture();
        OrderDeliveryTicket ticket = ticket(641, "P-CONFLICT-BIZ", "A", "IP-BIZ-CONFLICT");
        fixture.withTicket(ticket);
        when(fixture.client.resolveMaterial(any())).thenAnswer(invocation -> {
            MesMaterialResolveReq req = invocation.getArgument(0);
            MesMaterialResolveResp response = response(req,
                    item("P-CONFLICT-BIZ", "A", "EXISTING_PRODUCT", "MAT-CONFLICT", "TASK-CONFLICT"));
            response.setMesRequestId(req.getBizId());
            response.getData().setBizId("OTHER-BIZ-ID");
            return response;
        });

        Map<String, Object> result = fixture.batchService.syncMesMaterialBatch(List.of(641), 9641L);

        assertThat(result).containsEntry("successCount", 0).containsEntry("failedCount", 0).containsEntry("unknownCount", 1);
        List<Map<String, Object>> rows = (List<Map<String, Object>>) result.get("results");
        assertThat(rows).singleElement().satisfies(row -> assertThat(row.get("status")).isEqualTo("RESULT_UNKNOWN"));
        verify(fixture.sourceSync, org.mockito.Mockito.never()).syncConfirmedMaterialNo(any(), anyString(), anyString(), eq(true));
    }

    /**
     * MES 明确返回「同一物料身份已有处理中的任务」时，不是可重试失败：
     * 必须保留原 bizId 等人工核对，否则会形成失败-重发-再失败的死循环。
     */
    @Test
    @SuppressWarnings("unchecked")
    void mesDuplicateSubmitErrorCodeIsTreatedAsResultUnknownInsteadOfRetryableFailure() {
        Fixture fixture = fixture();
        OrderDeliveryTicket ticket = ticket(651, "P-DUPCODE", "A", "IP-DUP");
        fixture.withTicket(ticket);
        when(fixture.client.resolveMaterial(any())).thenAnswer(invocation -> {
            MesMaterialResolveReq req = invocation.getArgument(0);
            MesMaterialResolveResp resp = new MesMaterialResolveResp();
            resp.setCode("1_030_900_003");
            resp.setBizId(req.getBizId());
            resp.setMessage("物料准备任务处理中");
            return resp;
        });

        Map<String, Object> result = fixture.batchService.syncMesMaterialBatch(List.of(651), 9651L);

        assertThat(result).containsEntry("failedCount", 0).containsEntry("unknownCount", 1);
        List<Map<String, Object>> rows = (List<Map<String, Object>>) result.get("results");
        assertThat(rows).singleElement().satisfies(row -> {
            assertThat(row.get("status")).isEqualTo("RESULT_UNKNOWN");
            assertThat(String.valueOf(row.get("message"))).contains("1_030_900_003");
        });
        verify(fixture.sourceSync, org.mockito.Mockito.never())
                .syncConfirmedMaterialNo(any(), anyString(), anyString(), eq(true));
    }

    /**
     * MES 对重复提交返回「已存在」时，响应体仍带 data.items[].productCode。
     * 料号就是本流程要的结果，必须按成功写回，不能因为外层 code 非 0 就整条丢掉。
     */
    @Test
    @SuppressWarnings("unchecked")
    void mesExistingCodeWithProductCodeIsWrittenBackInsteadOfResultUnknown() {
        Fixture fixture = fixture();
        OrderDeliveryTicket ticket = ticket(653, "P-EXISTPAY", "A", "IP-EXISTPAY");
        fixture.withTicket(ticket);
        when(fixture.client.resolveMaterial(any())).thenAnswer(invocation -> {
            MesMaterialResolveResp resp = response(invocation.getArgument(0),
                    item("P-EXISTPAY", "A", "EXISTING", "MAT-EXISTPAY", "TASK-EXISTPAY"));
            resp.setCode("1_030_900_002");
            resp.setMessage("已存在物料准备任务");
            // 「已存在」响应常常不带 bizId，不能因此把行内料号丢掉。
            resp.getData().setBizId(null);
            return resp;
        });

        Map<String, Object> result = fixture.batchService.syncMesMaterialBatch(List.of(653), 9653L);

        assertThat(result)
                .containsEntry("successCount", 1)
                .containsEntry("failedCount", 0)
                .containsEntry("unknownCount", 0);
        List<Map<String, Object>> rows = (List<Map<String, Object>>) result.get("results");
        assertThat(rows).singleElement().satisfies(row -> {
            assertThat(row.get("status")).isEqualTo("SUCCESS");
            Map<String, Object> detail = (Map<String, Object>) row.get("result");
            assertThat(detail.get("materialNo")).isEqualTo("MAT-EXISTPAY");
        });
        verify(fixture.sourceSync).syncConfirmedMaterialNo(any(), eq("MAT-EXISTPAY"), anyString(), eq(true));
    }

    /**
     * 1030900008 = 某行命中已有任务、仍在待建料。MES 对此整批回滚（@Transactional），
     * 一条都没建，所以可以安全重试：记可重试失败 + 说清在等什么，不要卡成「结果待核对」。
     */
    @Test
    @SuppressWarnings("unchecked")
    void mesTaskPendingCodeRollsBackWholeBatchAsRetryableFailure() {
        Fixture fixture = fixture();
        OrderDeliveryTicket ticket = ticket(654, "P-TASKPEND", "A", "IP-TASKPEND");
        fixture.withTicket(ticket);
        when(fixture.client.resolveMaterial(any())).thenAnswer(invocation -> {
            MesMaterialResolveReq req = invocation.getArgument(0);
            MesMaterialResolveResp resp = new MesMaterialResolveResp();
            resp.setCode("1030900008");
            resp.setBizId(req.getBizId());
            resp.setMessage("");
            return resp;
        });

        Map<String, Object> result = fixture.batchService.syncMesMaterialBatch(List.of(654), 9654L);

        assertThat(result).containsEntry("failedCount", 1).containsEntry("unknownCount", 0);
        List<Map<String, Object>> rows = (List<Map<String, Object>>) result.get("results");
        assertThat(rows).singleElement().satisfies(row -> {
            assertThat(row.get("status")).isEqualTo("FAILED");
            assertThat(String.valueOf(row.get("message")))
                    .contains("MES 处理")
                    .contains("1030900008");
        });
        verify(fixture.sourceSync, org.mockito.Mockito.never())
                .syncConfirmedMaterialNo(any(), anyString(), anyString(), eq(true));
    }

    /**
     * 1030900011 = 同一包内两条明细图号+版本+材质重复。QPO 按身份拆波次后不该出现，
     * 真出现说明分包有 bug，消息必须指向 QPO 侧而不是让用户反复重试。
     */
    @Test
    @SuppressWarnings("unchecked")
    void mesBatchDuplicateItemCodeReportsQpoSplittingBug() {
        Fixture fixture = fixture();
        OrderDeliveryTicket ticket = ticket(656, "P-BATCHDUP", "A", "IP-BATCHDUP");
        fixture.withTicket(ticket);
        when(fixture.client.resolveMaterial(any())).thenAnswer(invocation -> {
            MesMaterialResolveReq req = invocation.getArgument(0);
            MesMaterialResolveResp resp = new MesMaterialResolveResp();
            resp.setCode("1030900011");
            resp.setBizId(req.getBizId());
            return resp;
        });

        Map<String, Object> result = fixture.batchService.syncMesMaterialBatch(List.of(656), 9656L);

        assertThat(result).containsEntry("failedCount", 1).containsEntry("unknownCount", 0);
        List<Map<String, Object>> rows = (List<Map<String, Object>>) result.get("results");
        assertThat(rows).singleElement().satisfies(row -> {
            assertThat(row.get("status")).isEqualTo("FAILED");
            assertThat(String.valueOf(row.get("message"))).contains("QPO 分包问题");
        });
    }

    /**
     * 旧契约的 1_030_900_002（带下划线写法）没带料号时仍是「对端版本不可知」，
     * 保守记结果待核对；同时验证码值归一后能识别下划线写法。
     */
    @Test
    @SuppressWarnings("unchecked")
    void legacyDuplicateCodeWithoutProductCodeStaysResultUnknown() {
        Fixture fixture = fixture();
        OrderDeliveryTicket ticket = ticket(655, "P-LEGACY", "A", "IP-LEGACY");
        fixture.withTicket(ticket);
        when(fixture.client.resolveMaterial(any())).thenAnswer(invocation -> {
            MesMaterialResolveReq req = invocation.getArgument(0);
            MesMaterialResolveResp resp = new MesMaterialResolveResp();
            resp.setCode("1_030_900_002");
            resp.setBizId(req.getBizId());
            resp.setMessage("已存在物料准备任务");
            return resp;
        });

        Map<String, Object> result = fixture.batchService.syncMesMaterialBatch(List.of(655), 9655L);

        assertThat(result).containsEntry("failedCount", 0).containsEntry("unknownCount", 1);
        List<Map<String, Object>> rows = (List<Map<String, Object>>) result.get("results");
        assertThat(rows).singleElement().satisfies(row -> {
            assertThat(row.get("status")).isEqualTo("RESULT_UNKNOWN");
            assertThat(String.valueOf(row.get("message"))).contains("已存在");
        });
    }

    /**
     * MES 正常契约：code=0，一个包里「已存在」和「待创建」混着回，items 逐行带自己的状态。
     * 已存在的直接取 productCode 写回，待创建的挂起等回调。
     */
    @Test
    @SuppressWarnings("unchecked")
    void mixedExistingAndPendingRowsInOneWaveAreHandledPerRow() {
        Fixture fixture = fixture();
        OrderDeliveryTicket existing = ticket(661, "P-MIX-EXIST", "A", "IP-MIX");
        OrderDeliveryTicket pending = ticket(662, "P-MIX-PENDING", "B", "IP-MIX");
        fixture.withTicket(existing);
        fixture.withTicket(pending);
        when(fixture.client.resolveMaterial(any())).thenAnswer(invocation -> {
            MesMaterialResolveReq req = invocation.getArgument(0);
            List<MesMaterialResolveResp.Item> items = new ArrayList<>();
            for (MesMaterialResolveReq.Item requestItem : req.getItems()) {
                if ("P-MIX-EXIST".equals(requestItem.getDrawingNo())) {
                    items.add(item(requestItem.getDrawingNo(), requestItem.getDrawingVersion(),
                            "EXISTING_PRODUCT", "M-EXIST-661", null));
                } else {
                    items.add(item(requestItem.getDrawingNo(), requestItem.getDrawingVersion(),
                            "PENDING_CREATE", null, "MT-20261030-001"));
                }
            }
            return response(req, items.toArray(new MesMaterialResolveResp.Item[0]));
        });

        Map<String, Object> result = fixture.batchService.syncMesMaterialBatch(List.of(661, 662), 9661L);

        assertThat(result).containsEntry("successCount", 1).containsEntry("callbackPendingCount", 1);
        List<Map<String, Object>> rows = (List<Map<String, Object>>) result.get("results");
        assertThat(rows).hasSize(2);
        Map<String, Object> existingRow = rowOf(rows, 661);
        assertThat(existingRow.get("status")).isEqualTo("SUCCESS");
        assertThat(((Map<String, Object>) existingRow.get("result")).get("materialNo")).isEqualTo("M-EXIST-661");
        assertThat(rowOf(rows, 662).get("status")).isEqualTo("CALLBACK_PENDING");
        verify(fixture.sourceSync).syncConfirmedMaterialNo(any(), eq("M-EXIST-661"), anyString(), eq(true));
    }

    /**
     * 预留是逐条写台账行的。第二条撞上身份去重位（并发提交）时，第一条已经落库，
     * 必须被收尾成 FAILED——否则它永远停在 PROCESSING、占住去重位，后续重试全被挡。
     */
    @Test
    @SuppressWarnings("unchecked")
    void partialReservationFailureMarksAlreadyReservedRowsFailed() {
        Fixture fixture = fixture();
        OrderDeliveryTicket first = ticket(671, "P-RESERVE-A", "A", "IP-RESERVE");
        OrderDeliveryTicket second = ticket(672, "P-RESERVE-B", "A", "IP-RESERVE");
        fixture.withTicket(first);
        fixture.withTicket(second);
        when(fixture.mesRequestMapper.insert(any(OrderDeliveryTicketMesRequest.class)))
                .thenReturn(1)
                .thenThrow(new DuplicateKeyException(
                        "duplicate key value violates unique constraint \"uk_delivery_mes_request_active_dedup\""));

        Map<String, Object> result = fixture.batchService.syncMesMaterialBatch(List.of(671, 672), 9671L);

        assertThat(result).containsEntry("failedCount", 2).containsEntry("unknownCount", 0);
        List<Map<String, Object>> rows = (List<Map<String, Object>>) result.get("results");
        assertThat(String.valueOf(rowOf(rows, 671).get("message"))).contains("已有在途 MES 请求");
        assertThat(String.valueOf(rowOf(rows, 672).get("message"))).contains("已有在途 MES 请求");
        ArgumentCaptor<OrderDeliveryTicketMesRequest> captor =
                ArgumentCaptor.forClass(OrderDeliveryTicketMesRequest.class);
        verify(fixture.mesRequestMapper, atLeastOnce()).update(captor.capture(), any());
        assertThat(captor.getAllValues()).anySatisfy(row ->
                assertThat(row.getRequestStatus()).isEqualTo("FAILED"));
        verify(fixture.client, never()).resolveMaterial(any());
    }

    /**
     * 单批不能超过回调接收上限（100 行）。超了 MES 整批回调会被我们 400 拒收，
     * 那批行会永远停在待回调，而待回调状态又拦住重试——只能人工改库。
     */
    @Test
    @SuppressWarnings("unchecked")
    void oversizedGroupIsSplitIntoCallbackSizedWaves() {
        Fixture fixture = fixture();
        List<OrderDeliveryTicket> tickets = new ArrayList<>();
        List<Integer> ticketIds = new ArrayList<>();
        for (int i = 0; i < 101; i++) {
            OrderDeliveryTicket ticket = ticket(700 + i, "P-CAP-" + i, "A", "IP-CAP");
            tickets.add(ticket);
            ticketIds.add(ticket.getId());
            fixture.withTicket(ticket);
        }
        when(fixture.client.resolveMaterial(any())).thenAnswer(invocation -> {
            MesMaterialResolveReq req = invocation.getArgument(0);
            List<MesMaterialResolveResp.Item> items = new ArrayList<>();
            for (MesMaterialResolveReq.Item requestItem : req.getItems()) {
                items.add(item(requestItem.getDrawingNo(), requestItem.getDrawingVersion(),
                        "EXISTING_PRODUCT", "M-" + requestItem.getDrawingNo(), null));
            }
            return response(req, items.toArray(new MesMaterialResolveResp.Item[0]));
        });

        Map<String, Object> result = fixture.batchService.syncMesMaterialBatch(ticketIds, 9700L);

        assertThat(result).containsEntry("successCount", 101);
        // 101 条必须拆成 2 个 bizId，单批不超过 100 条。
        assertThat(result).containsEntry("batchCount", 2);
        ArgumentCaptor<MesMaterialResolveReq> captor = ArgumentCaptor.forClass(MesMaterialResolveReq.class);
        verify(fixture.client, times(2)).resolveMaterial(captor.capture());
        assertThat(captor.getAllValues()).allSatisfy(req ->
                assertThat(req.getItems().size()).isLessThanOrEqualTo(100));
    }

    private Map<String, Object> rowOf(List<Map<String, Object>> rows, Integer ticketId) {
        return rows.stream()
                .filter(row -> ticketId.equals(row.get("ticketId")))
                .findFirst()
                .orElseThrow(() -> new AssertionError("缺少 ticketId=" + ticketId + " 的结果行"));
    }

    /**
     * 待回调响应缺 taskNo 时 callback 无法定位批次行，只能按结果待核对处理。
     */
    @Test
    @SuppressWarnings("unchecked")
    void pendingResponseWithoutTaskNoIsTreatedAsResultUnknown() {
        Fixture fixture = fixture();
        OrderDeliveryTicket ticket = ticket(652, "P-NOTASK", "A", "IP-NOTASK");
        fixture.withTicket(ticket);
        when(fixture.client.resolveMaterial(any())).thenAnswer(invocation -> {
            MesMaterialResolveReq req = invocation.getArgument(0);
            return response(req, item("P-NOTASK", "A", "PENDING_CREATE", null, null));
        });

        Map<String, Object> result = fixture.batchService.syncMesMaterialBatch(List.of(652), 9652L);

        assertThat(result).containsEntry("unknownCount", 1).containsEntry("callbackPendingCount", 0);
        List<Map<String, Object>> rows = (List<Map<String, Object>>) result.get("results");
        assertThat(rows).singleElement().satisfies(row -> {
            assertThat(row.get("status")).isEqualTo("RESULT_UNKNOWN");
            assertThat(String.valueOf(row.get("message"))).contains("taskNo");
        });
    }

    /**
     * MES 契约：items 与请求 items 严格一一对应，请用下标对齐。
     * 行数相等但某行图号/版本对不上时，按位置配会写错料号，只能记结果待核对。
     */
    @Test
    @SuppressWarnings("unchecked")
    void responseRowsAreMatchedByIndexAndIdentityMismatchStaysResultUnknown() {
        Fixture fixture = fixture();
        OrderDeliveryTicket first = ticket(681, "P-IDX-1", "A", "IP-IDX");
        OrderDeliveryTicket second = ticket(682, "P-IDX-2", "B", "IP-IDX");
        fixture.withTicket(first);
        fixture.withTicket(second);
        when(fixture.client.resolveMaterial(any())).thenAnswer(invocation -> {
            MesMaterialResolveReq req = invocation.getArgument(0);
            return response(req,
                    item("P-IDX-1", "A", "EXISTING_PRODUCT", "MAT-IDX-1", "TASK-IDX-1"),
                    item("P-OTHER", "B", "EXISTING_PRODUCT", "MAT-OTHER", "TASK-IDX-2"));
        });
        when(fixture.sourceSync.syncConfirmedMaterialNo(any(), anyString(), anyString(), eq(true)))
                .thenReturn(SourceItemMaterialNoSyncService.MaterialNoSyncResult.empty());

        Map<String, Object> result = fixture.batchService.syncMesMaterialBatch(List.of(681, 682), 9681L);

        assertThat(result).containsEntry("successCount", 1).containsEntry("unknownCount", 1);
        List<Map<String, Object>> rows = (List<Map<String, Object>>) result.get("results");
        assertThat(rowOf(rows, 681).get("status")).isEqualTo("SUCCESS");
        assertThat(rowOf(rows, 682).get("status")).isEqualTo("RESULT_UNKNOWN");
        assertThat(String.valueOf(rowOf(rows, 682).get("message"))).contains("不一致");
        verify(fixture.sourceSync, times(1))
                .syncConfirmedMaterialNo(any(), anyString(), anyString(), eq(true));
    }

    /**
     * 料号以最后一次获取为准：同步响应同样覆盖已确认的旧料号，且旧值/新值必须留痕。
     */
    @Test
    @SuppressWarnings("unchecked")
    void syncResponseOverwritesConfirmedMaterialNoAndAuditsPreviousValue() {
        Fixture fixture = fixture();
        OrderDeliveryTicket ticket = ticket(683, "P-OVERWRITE", "A", "IP-OVERWRITE");
        ticket.setMaterialNo("MAT-OLD");
        fixture.withTicket(ticket);
        when(fixture.client.resolveMaterial(any())).thenAnswer(invocation -> {
            MesMaterialResolveReq req = invocation.getArgument(0);
            return response(req, item("P-OVERWRITE", "A", "EXISTING_PRODUCT", "MAT-NEW", "TASK-683"));
        });
        when(fixture.sourceSync.syncConfirmedMaterialNo(any(), anyString(), anyString(), eq(true)))
                .thenReturn(SourceItemMaterialNoSyncService.MaterialNoSyncResult.empty());

        Map<String, Object> result = fixture.batchService.syncMesMaterialBatch(List.of(683), 9683L);

        assertThat(result).containsEntry("successCount", 1);
        verify(fixture.sourceSync)
                .syncConfirmedMaterialNo(ticket, "MAT-NEW", "MES material resolved", true);
        ArgumentCaptor<String> actionCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Object> dataCaptor = ArgumentCaptor.forClass(Object.class);
        verify(fixture.auditService, atLeastOnce()).record(
                actionCaptor.capture(), any(), any(), any(), dataCaptor.capture(), any(), any(), any(), any());
        int overwriteIndex = actionCaptor.getAllValues().indexOf("DELIVERY_MES_MATERIAL_SYNC_OVERWRITE");
        assertThat(overwriteIndex).isGreaterThanOrEqualTo(0);
        assertThat((Map<String, Object>) dataCaptor.getAllValues().get(overwriteIndex))
                .containsEntry("previousMaterialNo", "MAT-OLD")
                .containsEntry("materialNo", "MAT-NEW");
    }

    /**
     * MES 不接受空版本（整批 400 退回，同批其它零件一起失败），所以打包前就要拦住这一行。
     */
    @Test
    @SuppressWarnings("unchecked")
    void ticketWithoutVersionIsRejectedBeforeSubmittingToMes() {
        Fixture fixture = fixture();
        OrderDeliveryTicket ticket = ticket(684, "P-NOVERSION", null, "IP-NOVERSION");
        fixture.withTicket(ticket);

        Map<String, Object> result = fixture.batchService.syncMesMaterialBatch(List.of(684), 9684L);

        assertThat(result)
                .containsEntry("failedCount", 1)
                .containsEntry("submittedToMesCount", 0);
        List<Map<String, Object>> rows = (List<Map<String, Object>>) result.get("results");
        assertThat(String.valueOf(rowOf(rows, 684).get("message"))).contains("版本");
        verify(fixture.client, never()).resolveMaterial(any());
    }

    /**
     * 结果未知不再拦重试：MES 按身份全局判重，重发不会造出第二个任务，
     * 只会明确回「已存在带料号」或「还在待建料」。重试前必须先收口旧台账行，否则唯一索引挡住新请求。
     */
    @Test
    @SuppressWarnings("unchecked")
    void resultUnknownTicketIsResubmittedInsteadOfBlocked() {
        Fixture fixture = fixture();
        OrderDeliveryTicket ticket = ticket(691, "P-UNKNOWN-RETRY", "A", "IP-UNKNOWN");
        ticket.setMesSyncStatus("RESULT_UNKNOWN");
        ticket.setMesRequestId("QPO-MATL-OLD");
        fixture.withTicket(ticket);
        when(fixture.dedupService.retireUnresolvedRequests(ticket)).thenReturn(List.of("QPO-MATL-OLD"));
        when(fixture.client.resolveMaterial(any())).thenAnswer(invocation -> {
            MesMaterialResolveReq req = invocation.getArgument(0);
            return response(req, item("P-UNKNOWN-RETRY", "A", "EXISTING_PRODUCT", "MAT-RETRY", "TASK-RETRY"));
        });
        when(fixture.sourceSync.syncConfirmedMaterialNo(any(), anyString(), anyString(), eq(true)))
                .thenReturn(SourceItemMaterialNoSyncService.MaterialNoSyncResult.empty());

        Map<String, Object> result = fixture.batchService.syncMesMaterialBatch(List.of(691), 9691L);

        assertThat(result)
                .containsEntry("successCount", 1)
                .containsEntry("unknownCount", 0)
                .containsEntry("skippedCount", 0);
        verify(fixture.dedupService).retireUnresolvedRequests(ticket);
        verify(fixture.client).resolveMaterial(any());
        ArgumentCaptor<String> actionCaptor = ArgumentCaptor.forClass(String.class);
        verify(fixture.auditService, atLeastOnce()).record(
                actionCaptor.capture(), any(), any(), any(), any(), any(), any(), any(), any());
        assertThat(actionCaptor.getAllValues())
                .contains("DELIVERY_MES_MATERIAL_SYNC_RETRY_AFTER_UNKNOWN");
    }

    private Fixture fixture() {
        Fixture fixture = new Fixture();
        fixture.ticketMapper = mock(OrderDeliveryTicketMapper.class);
        fixture.mesRequestMapper = mock(OrderDeliveryTicketMesRequestMapper.class);
        fixture.orderMapper = mock(OrderRfqMapper.class);
        fixture.itemMapper = mock(ItemQuoteVersionMapper.class);
        fixture.client = mock(MesMaterialClient.class);
        fixture.sourceSync = mock(SourceItemMaterialNoSyncService.class);
        fixture.auditService = mock(AuditService.class);
        fixture.dedupService = mock(MesMaterialDedupService.class);
        fixture.syncService = new MesMaterialSyncService();
        fixture.batchService = new DeliveryIntegrationBatchService();

        OrderRfq order = new OrderRfq();
        order.setId(88);
        order.setCustomerCode("GSS1");
        order.setCustomerName("Gold Sample");
        when(fixture.orderMapper.selectById(88)).thenReturn(order);
        when(fixture.ticketMapper.update(any(OrderDeliveryTicket.class), any())).thenReturn(1);
        when(fixture.ticketMapper.updateById(any(OrderDeliveryTicket.class))).thenReturn(1);
        ReflectionTestUtils.setField(fixture.syncService, "ticketMapper", fixture.ticketMapper);
        ReflectionTestUtils.setField(fixture.syncService, "mesRequestMapper", fixture.mesRequestMapper);
        ReflectionTestUtils.setField(fixture.syncService, "orderRfqMapper", fixture.orderMapper);
        ReflectionTestUtils.setField(fixture.syncService, "itemMapper", fixture.itemMapper);
        ReflectionTestUtils.setField(fixture.syncService, "mesMaterialClient", fixture.client);
        ReflectionTestUtils.setField(fixture.syncService, "sourceItemMaterialNoSyncService", fixture.sourceSync);
        ReflectionTestUtils.setField(fixture.syncService, "auditService", fixture.auditService);
        ReflectionTestUtils.setField(fixture.syncService, "dedupService", fixture.dedupService);

        ReflectionTestUtils.setField(fixture.batchService, "ticketMapper", fixture.ticketMapper);
        ReflectionTestUtils.setField(fixture.batchService, "mesMaterialSyncService", fixture.syncService);
        return fixture;
    }

    private OrderDeliveryTicket ticket(Integer id, String partNo, String version, String projectNo) {
        OrderDeliveryTicket ticket = new OrderDeliveryTicket();
        ticket.setId(id);
        ticket.setRfqId(88);
        ticket.setItemId(id + 1000);
        ticket.setSourceType("RFQ");
        ticket.setPartNo(partNo);
        ticket.setVersion(version);
        ticket.setInternalProjectNo(projectNo);
        ticket.setDescription("Precision bracket " + partNo);
        ticket.setMaterialName("Material note " + partNo);
        ticket.setSurfaceTreatment("Anodized");
        ticket.setRemark("Remark " + partNo);
        ticket.setMesSyncStatus("PENDING");
        ticket.setQty(1);
        return ticket;
    }

    private MesMaterialResolveResp response(MesMaterialResolveReq req, MesMaterialResolveResp.Item... items) {
        MesMaterialResolveResp resp = new MesMaterialResolveResp();
        resp.setCode(0);
        MesMaterialResolveResp.Data data = new MesMaterialResolveResp.Data();
        data.setBizId(req.getBizId());
        data.setItems(List.of(items));
        resp.setData(data);
        return resp;
    }

    private MesMaterialResolveResp.Item item(
            String drawingNo,
            String drawingVersion,
            String status,
            String productCode,
            String taskNo) {
        MesMaterialResolveResp.Item item = new MesMaterialResolveResp.Item();
        item.setDrawingNo(drawingNo);
        item.setDrawingVersion(drawingVersion);
        item.setStatus(status);
        item.setProductCode(productCode);
        item.setProductId(productCode == null ? null : "100");
        item.setProductName(productCode == null ? null : "Material " + productCode);
        item.setTaskNo(taskNo);
        return item;
    }

    private final class Fixture {
        private OrderDeliveryTicketMapper ticketMapper;
        private OrderDeliveryTicketMesRequestMapper mesRequestMapper;
        private OrderRfqMapper orderMapper;
        private ItemQuoteVersionMapper itemMapper;
        private MesMaterialClient client;
        private SourceItemMaterialNoSyncService sourceSync;
        private AuditService auditService;
        private MesMaterialDedupService dedupService;
        private MesMaterialSyncService syncService;
        private DeliveryIntegrationBatchService batchService;

        private void withTicket(OrderDeliveryTicket ticket) {
            withTicket(ticket, "/drawings/" + ticket.getPartNo() + ".pdf");
        }

        private void withTicket(OrderDeliveryTicket ticket, String drawingUrl) {
            when(ticketMapper.selectById(ticket.getId())).thenReturn(ticket);
            when(ticketMapper.selectByIdForUpdate(ticket.getId())).thenReturn(ticket);
            ItemQuoteVersion item = new ItemQuoteVersion();
            item.setId(ticket.getItemId());
            item.setDrawingUrl(drawingUrl);
            when(itemMapper.selectById(ticket.getItemId())).thenReturn(item);
            when(itemMapper.selectByIdForUpdate(ticket.getItemId())).thenReturn(item);
        }
    }
}
