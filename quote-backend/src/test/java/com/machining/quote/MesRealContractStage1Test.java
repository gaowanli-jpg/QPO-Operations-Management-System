package com.machining.quote;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.machining.quote.dto.mes.MesMaterialResolveReq;
import com.machining.quote.dto.mes.MesMaterialResolveResp;
import com.machining.quote.dto.mes.MesProductionBomDTO;
import com.machining.quote.dto.mes.MesProductionDispatchReq;
import com.machining.quote.dto.mes.MesProductionDispatchResp;
import com.machining.quote.dto.mes.MesProductionProcessRouteDTO;
import com.machining.quote.entity.SystemDataCenterEndpointConfig;
import com.machining.quote.service.DataCenterEndpointRegistry;
import com.machining.quote.service.mes.MesCustomerRequirementsAssembler;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class MesRealContractStage1Test {
    private final ObjectMapper objectMapper = JsonMapper.builder()
            .addModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .build();

    @Test
    void materialPreparationRequestUsesBizIdItemsAndCustomerRequirementsOnly() throws Exception {
        MesMaterialResolveReq req = new MesMaterialResolveReq();
        req.setBizId("QPO-MAT-20260812-01J5ABCDEF");
        req.setCustomerCode("GSS1");
        req.setCustomerName("测试客户");
        req.setInternalProjectNo("QPO-MES-E2E-20260812");

        MesMaterialResolveReq.Item first = new MesMaterialResolveReq.Item();
        first.setDrawingNo("QPO-MES-E2E-P001");
        first.setDrawingVersion("A");
        first.setDrawingUrls(List.of("http://qpo-test.example/api/files/P001.pdf"));
        first.setRemark("QPO MES E2E material preparation");
        first.setCustomerRequirements(MesCustomerRequirementsAssembler.assemble(
                "  精密安装板\r\n二级  ",
                " 本色阳极氧化 "));

        MesMaterialResolveReq.Item second = new MesMaterialResolveReq.Item();
        second.setDrawingNo("QPO-MES-E2E-P002");
        second.setDrawingVersion("");
        second.setDrawingUrls(List.of("http://qpo-test.example/api/files/P002.pdf"));

        req.setItems(List.of(first, second));

        String json = objectMapper.writeValueAsString(req);
        Map<String, Object> payload = readMap(json);
        assertThat(payload.keySet()).containsExactlyInAnyOrder("bizId", "customerCode", "customerName", "internalProjectNo", "items");
        assertThat(payload.get("bizId")).isEqualTo("QPO-MAT-20260812-01J5ABCDEF");
        assertThat(items(payload)).hasSize(2);
        assertThat(items(payload).get(0))
                .containsEntry("drawingNo", "QPO-MES-E2E-P001")
                .containsEntry("drawingVersion", "A")
                .containsEntry("remark", "QPO MES E2E material preparation")
                .containsEntry("customerRequirements", "物料描述：精密安装板\n二级\n表面处理：本色阳极氧化")
                .doesNotContainKeys("ticketId", "sourceType", "sourceOrderId", "sourceItemId", "rfqId", "itemId", "standard");
        assertThat(items(payload).get(1))
                .containsEntry("drawingVersion", "")
                .doesNotContainKey("customerRequirements");
        assertThat(json)
                .doesNotContain("\"mesRequestId\"", "\"partNo\"", "\"version\"", "\"drawingUrl\"", "\"sourceType\"", "\"ticketId\"", "\"rfqId\"", "\"sourceOrderId\"", "\"sourceItemId\"")
                .contains("\"items\":[");
    }

    @Test
    void productionDemandRequestUsesRealBatchContractWithoutQpoInternalFields() throws Exception {
        MesProductionDispatchReq req = new MesProductionDispatchReq();
        req.setBizId("QPO-DEM-20260812-01J5ABCDEF");
        req.setCustomerCode("GSS1");
        req.setCustomerName("测试客户");
        req.setInternalProjectNo("QPO-MES-E2E-20260812");

        MesProductionDispatchReq.Item item = new MesProductionDispatchReq.Item();
        item.setSaleOrderNo("QPO-MES-E2E-SO-20260812-001");
        item.setSaleOrderItemNo("10");
        item.setProductCode("SY100399000001");
        item.setDemandQty(5);
        item.setMakeQty(5);
        item.setBuyQty(0);
        item.setStockQty(0);
        item.setRequiredDate(LocalDateTime.of(2026, 8, 20, 23, 59, 59));
        item.setRemark("QPO MES E2E production demand");
        item.setCustomerRequirements(MesCustomerRequirementsAssembler.assemble("精密安装板", "本色阳极氧化"));
        item.setBom(List.of(new MesProductionBomDTO("CHILD-MATERIAL-001", new BigDecimal("2.5"))));
        item.setProcessRoute(List.of(new MesProductionProcessRouteDTO(
                "10",
                "CNC加工",
                "CNC",
                new BigDecimal("1.5"),
                "精加工")));
        req.setItems(List.of(item));

        String json = objectMapper.writeValueAsString(req);
        Map<String, Object> payload = readMap(json);
        assertThat(payload.keySet()).containsExactlyInAnyOrder("bizId", "customerCode", "customerName", "internalProjectNo", "items");
        Map<String, Object> row = items(payload).get(0);
        assertThat(row)
                .containsEntry("saleOrderNo", "QPO-MES-E2E-SO-20260812-001")
                .containsEntry("saleOrderItemNo", "10")
                .containsEntry("productCode", "SY100399000001")
                .containsEntry("demandQty", 5)
                .containsEntry("makeQty", 5)
                .containsEntry("buyQty", 0)
                .containsEntry("stockQty", 0)
                .containsEntry("requiredDate", "2026-08-20T23:59:59")
                .containsEntry("customerRequirements", "物料描述：精密安装板\n表面处理：本色阳极氧化")
                .containsEntry("remark", "QPO MES E2E production demand")
                .doesNotContainKeys("allocationId", "ticketId", "sourceType", "sourceOrderId", "sourceItemId", "partNo", "version", "materialNo", "productionQty", "ticketQty", "assemblyRelations");
        assertThat(row.get("bom")).asList().singleElement().satisfies(bom -> assertThat(castMap(bom))
                .containsEntry("itemCode", "CHILD-MATERIAL-001")
                .containsEntry("quantity", 2.5));
        assertThat(row.get("processRoute")).asList().singleElement().satisfies(route -> assertThat(castMap(route))
                .containsEntry("programNum", "10")
                .containsEntry("processName", "CNC加工")
                .containsEntry("processTypeCode", "CNC")
                .containsEntry("workHour", 1.5)
                .containsEntry("processDescription", "精加工"));
        assertThat(json).doesNotContain("+08:00", "Z", "categoryName", "detailName", "unitWorkHours", "sortNo", "materialShape", "engineeringMaterialCode");
    }

    @Test
    void mesSuccessCodeAcceptsNumbersAndStringsForBothResponses() throws Exception {
        MesMaterialResolveResp material = objectMapper.readValue("""
                {"code":"0","msg":"成功","data":{"bizId":"MAT-BIZ","items":[{"taskNo":"MP001","status":"EXISTING_PRODUCT","productCode":"MAT-001"}]}}
                """, MesMaterialResolveResp.class);
        assertThat(material.getSuccess()).isTrue();
        assertThat(material.getMesRequestId()).isEqualTo("MAT-BIZ");
        assertThat(material.getMaterialNo()).isEqualTo("MAT-001");

        MesProductionDispatchResp production = objectMapper.readValue("""
                {"code":0,"msg":"成功","data":{"bizId":"DEM-BIZ","items":[{"id":1001,"planNo":"PLAN001","productCode":"MAT-001","status":0}]}}
                """, MesProductionDispatchResp.class);
        assertThat(production.getSuccess()).isTrue();
        assertThat(production.getRequestId()).isEqualTo("DEM-BIZ");
        assertThat(production.getExternalId()).isEqualTo("PLAN001");
    }

    @Test
    void mesDataCenterEndpointsHaveSixtySecondMinimumTimeout() {
        DataCenterEndpointRegistry registry = new DataCenterEndpointRegistry();

        assertThat(registry.newEndpoint(DataCenterEndpointRegistry.MES_MATERIAL_RESOLVE).getTimeoutMs()).isEqualTo(60000);
        assertThat(registry.newEndpoint(DataCenterEndpointRegistry.MES_PRODUCTION_DISPATCH).getTimeoutMs()).isEqualTo(60000);

        SystemDataCenterEndpointConfig existing = registry.newEndpoint(DataCenterEndpointRegistry.MES_MATERIAL_RESOLVE);
        existing.setTimeoutMs(1000);
        registry.applyFixedDefinition(existing);

        assertThat(existing.getTimeoutMs()).isEqualTo(60000);
    }

    private Map<String, Object> readMap(String json) throws Exception {
        return objectMapper.readValue(json, new TypeReference<>() {
        });
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> items(Map<String, Object> payload) {
        return (List<Map<String, Object>>) payload.get("items");
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> castMap(Object value) {
        return (Map<String, Object>) value;
    }
}
