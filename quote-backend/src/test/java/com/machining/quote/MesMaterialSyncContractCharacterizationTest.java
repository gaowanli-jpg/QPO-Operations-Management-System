package com.machining.quote;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.machining.quote.controller.OrderDeliveryController;
import com.machining.quote.dto.mes.MesMaterialResolveReq;
import com.machining.quote.dto.mes.MesMaterialResolveResp;
import com.machining.quote.entity.ItemQuoteVersion;
import com.machining.quote.entity.OrderDeliveryTicket;
import com.machining.quote.entity.OrderDeliveryTicketMesRequest;
import com.machining.quote.entity.OrderRfq;
import com.machining.quote.entity.SalesOrderHeader;
import com.machining.quote.entity.SalesOrderItem;
import com.machining.quote.mapper.ItemQuoteVersionMapper;
import com.machining.quote.mapper.OrderDeliveryTicketMapper;
import com.machining.quote.mapper.OrderDeliveryTicketMesRequestMapper;
import com.machining.quote.mapper.OrderRfqMapper;
import com.machining.quote.mapper.SalesOrderHeaderMapper;
import com.machining.quote.mapper.SalesOrderItemMapper;
import com.machining.quote.service.AuditService;
import com.machining.quote.service.BusinessFileService;
import com.machining.quote.service.SourceItemMaterialNoSyncService;
import com.machining.quote.service.mes.MesMaterialClient;
import com.machining.quote.service.mes.MesMaterialSyncService;
import com.machining.quote.service.SystemTaskService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.bind.annotation.PostMapping;

import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MesMaterialSyncContractCharacterizationTest extends MesMaterialSyncTestSupport {

    @Test
    void deliveredItemMesSyncOnlyExposesBatchEndpoint() {
        List<String> controllerMethods = Arrays.stream(OrderDeliveryController.class.getDeclaredMethods())
                .map(Method::getName)
                .toList();
        List<String> postMappings = Arrays.stream(OrderDeliveryController.class.getDeclaredMethods())
                .map(method -> method.getAnnotation(PostMapping.class))
                .filter(annotation -> annotation != null)
                .flatMap(annotation -> Arrays.stream(annotation.value()))
                .toList();
        List<String> taskMethods = Arrays.stream(SystemTaskService.class.getDeclaredMethods())
                .map(Method::getName)
                .toList();

        assertThat(controllerMethods)
                .contains("syncMesMaterialBatch")
                .doesNotContain("syncMesMaterial");
        assertThat(postMappings)
                .contains("/tickets/mes-material/batch")
                .doesNotContain("/tickets/{ticketId}/mes-material");
        assertThat(taskMethods)
                .contains("submitMesMaterialBatchSyncTask")
                .doesNotContain("submitMesMaterialSyncTask");
    }

    @Test
    void mesMaterialSyncTaskFlowFreezesStatusAndAuditNames() throws IOException {
        String taskService = read(SYSTEM_TASK_SERVICE);
        String syncService = read(MES_SYNC_SERVICE);

        assertThat(taskService)
                .as("System tasks must submit MES material requests through the batch entry")
                .contains(
                        "MES_MATERIAL_BATCH_SYNC",
                        "submitMesMaterialBatchSyncTask",
                        "DELIVERY_MES_MATERIAL_SYNC_SUBMIT",
                        "deliveryIntegrationBatchService.syncMesMaterialBatch"
                )
                .doesNotContain("submitMesMaterialSyncTask");
        assertThat(syncService)
                .as("Ticket status transitions and audit actions must be stable before implementation continues")
                .contains(
                        "PROCESSING",
                        "SUCCESS",
                        "FAILED",
                        "setMesSyncStatus",
                        "DELIVERY_MES_MATERIAL_SYNC_SUCCESS",
                        "DELIVERY_MES_MATERIAL_SYNC_ACCEPTED",
                        "DELIVERY_MES_MATERIAL_SYNC_FAILED",
                        "MES material sync started",
                        "MES material sync completed",
                        "MES material sync accepted",
                        "MES material sync failed",
                        "log.error(\"MES material sync failed ticketId={} taskId={} rfqId={} partNo={} durationMs={}\""
                );
    }

    @Test
    void mesMaterialDtosCarryTheExternalContractFields() {
        MesMaterialResolveReq req = new MesMaterialResolveReq();
        req.setMesRequestId("QPO-MAT-1");
        req.setCustomerCode("GSS1");
        req.setCustomerName("测试客户");
        req.setInternalProjectNo("IP-1");
        MesMaterialResolveReq.Item item = new MesMaterialResolveReq.Item();
        item.setDrawingNo("P-100");
        item.setDrawingVersion("A");
        item.setDrawingUrls(List.of("http://qpo.local/p100.pdf"));
        item.setRemark("Rush");
        item.setCustomerRequirements("物料描述：Bracket\n表面处理：Anodized");
        req.setItems(List.of(item));

        JsonNode payload = serialize(req);
        assertThat(payload.path("bizId").asText()).isEqualTo("QPO-MAT-1");
        assertThat(payload.path("customerCode").asText()).isEqualTo("GSS1");
        assertThat(payload.path("items")).hasSize(1);
        assertThat(payload.path("items").get(0).path("drawingNo").asText()).isEqualTo("P-100");
        assertThat(payload.path("items").get(0).path("drawingVersion").asText()).isEqualTo("A");
        assertThat(payload.path("items").get(0).path("drawingUrls").get(0).asText())
                .isEqualTo("http://qpo.local/p100.pdf");
        assertThat(payload.path("items").get(0).path("remark").asText()).isEqualTo("Rush");
        assertThat(payload.path("items").get(0).path("customerRequirements").asText())
                .isEqualTo("物料描述：Bracket\n表面处理：Anodized");
        assertThat(payload.has("mesRequestId")).isFalse();
        assertThat(payload.path("items").get(0).has("partNo")).isFalse();
        assertThat(payload.path("items").get(0).has("drawingUrl")).isFalse();

        MesMaterialResolveResp numericCode = new MesMaterialResolveResp();
        numericCode.setCode(0);
        assertThat(numericCode.getSuccess()).isTrue();
        MesMaterialResolveResp stringCode = new MesMaterialResolveResp();
        stringCode.setCode("0");
        assertThat(stringCode.getSuccess()).isTrue();
    }
}
