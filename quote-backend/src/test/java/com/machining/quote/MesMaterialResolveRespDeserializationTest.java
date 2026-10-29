package com.machining.quote;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.machining.quote.dto.mes.MesMaterialResolveResp;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * 生产事故回归：刷新料号报
 * "Error while extracting response for type [MesMaterialResolveResp]"。
 *
 * 根因是 MES 返回体与 DTO 不完全一致（多字段 / data 不是对象）时，
 * Jackson 直接抛异常，QPO 连 MES 的真实错误文案都拿不到。
 */
class MesMaterialResolveRespDeserializationTest {

    /** 故意用严格默认配置，确保 DTO 自身的容错注解生效，不依赖调用方放宽配置。 */
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    @DisplayName("MES 多返回未声明字段时不应解析失败")
    void toleratesUnknownFields() {
        String json = """
                {"code":0,"success":true,"bizId":"B1","materialNo":"M-NEW","traceId":"abc",
                 "data":{"bizId":"B1","extraField":"x","items":[{"taskId":1,"productCode":"M-NEW"}]}}
                """;

        MesMaterialResolveResp resp = read(json);

        assertThat(resp.getSuccess()).isTrue();
        assertThat(resp.getMaterialNo()).isEqualTo("M-NEW");
    }

    @Test
    @DisplayName("data 为空数组时保留 MES 的 msg，不能抛出解析异常")
    void keepsMessageWhenDataIsEmptyArray() {
        String json = "{\"code\":500,\"msg\":\"以下料号不存在：CS2004010009\",\"data\":[]}";

        MesMaterialResolveResp resp = read(json);

        assertThat(resp.getSuccess()).isFalse();
        assertThat(resp.getMessage()).isEqualTo("以下料号不存在：CS2004010009");
        assertThat(resp.getData()).isNull();
    }

    @Test
    @DisplayName("data 为空字符串或数字时同样降级为 null")
    void keepsMessageWhenDataIsScalar() {
        assertThat(read("{\"code\":500,\"msg\":\"图号不存在\",\"data\":\"\"}").getMessage())
                .isEqualTo("图号不存在");
        assertThat(read("{\"code\":500,\"msg\":\"参数错误\",\"data\":0}").getMessage())
                .isEqualTo("参数错误");
    }

    @Test
    @DisplayName("data 缺失时按失败处理，不抛异常")
    void handlesMissingData() {
        assertThatCode(() -> read("{\"code\":500,\"msg\":\"解析失败\"}")).doesNotThrowAnyException();
        assertThat(read("{\"code\":500,\"msg\":\"解析失败\"}").getMessage()).isEqualTo("解析失败");
    }

    @Test
    @DisplayName("正常响应仍能取到料号与 bizId")
    void parsesNormalResponse() {
        String json = """
                {"code":0,"success":true,"bizId":"QPO-MATR-20261028-ABC","materialNo":"M-NEW",
                 "data":{"bizId":"QPO-MATR-20261028-ABC","items":[{"taskId":9,"productCode":"M-NEW"}]}}
                """;

        MesMaterialResolveResp resp = read(json);

        assertThat(resp.getMaterialNo()).isEqualTo("M-NEW");
        assertThat(resp.getMesRequestId()).isEqualTo("QPO-MATR-20261028-ABC");
    }

    @Test
    @DisplayName("顶层 materialNo 缺失时回退到 items[0].productCode")
    void fallsBackToFirstItemProductCode() {
        String json = """
                {"code":0,"success":true,"bizId":"B2",
                 "data":{"items":[{"productCode":"M-FROM-ITEM","status":"SUCCESS"}]}}
                """;

        MesMaterialResolveResp resp = read(json);

        assertThat(resp.getMaterialNo()).isEqualTo("M-FROM-ITEM");
        assertThat(resp.getStatus()).isEqualTo("SUCCESS");
    }

    @Test
    @DisplayName("taskId/productId 为字符串时不应解析失败")
    void toleratesStringIdentifiers() {
        String json = """
                {"code":0,"success":true,"bizId":"B3","materialNo":"M3",
                 "data":{"items":[{"taskId":"T20261028001","productId":"a1b2c3","productCode":"M3"}]}}
                """;

        MesMaterialResolveResp resp = read(json);

        assertThat(resp.getMaterialNo()).isEqualTo("M3");
        assertThat(resp.getExternalTraceId()).isEqualTo("T20261028001");
    }

    @Test
    @DisplayName("单行 items 被上游压成对象且布尔标志返回 0/1 时仍可解析")
    void toleratesUnwrappedSingleItemAndNumericBooleanFlags() {
        String json = """
                {"success":1,"callbackRequired":0,
                 "data":{"bizId":"B4","items":{"taskId":12,"drawingNo":"P-4",
                   "drawingVersion":"A","status":"SUCCESS","productCode":"M4"}}}
                """;

        MesMaterialResolveResp resp = read(json);

        assertThat(resp.getSuccess()).isTrue();
        assertThat(resp.getCallbackRequired()).isFalse();
        assertThat(resp.getMesRequestId()).isEqualTo("B4");
        assertThat(resp.getData().getItems()).hasSize(1);
        assertThat(resp.getMaterialNo()).isEqualTo("M4");
    }

    @Test
    @DisplayName("字符串布尔标志按常见 0/1、true/false 表示解析")
    void toleratesStringBooleanFlags() {
        MesMaterialResolveResp resp = read("""
                {"success":"true","callbackRequired":"1",
                 "data":{"bizId":"B5","items":[]}}
                """);

        assertThat(resp.getSuccess()).isTrue();
        assertThat(resp.getCallbackRequired()).isTrue();
    }

    @Test
    @DisplayName("items 的空标量和非对象行降级为空结果，由业务层标记待核对")
    void toleratesEmptyOrMalformedItemsContainer() {
        assertThat(read("{\"code\":0,\"data\":{\"bizId\":\"B6\",\"items\":\"\"}}")
                .getData().getItems()).isEmpty();
        assertThat(read("{\"code\":0,\"data\":{\"bizId\":\"B7\",\"items\":[null,7,\"bad\"]}}")
                .getData().getItems()).isEmpty();
    }

    @Test
    @DisplayName("字段被错误序列化成对象时不应遮住 bizId 或让整份响应解析失败")
    void toleratesStructuredValuesInTextFields() {
        MesMaterialResolveResp resp = read("""
                {"code":0,"msg":{"detail":"ignored"},"data":{"bizId":"B8",
                 "items":[{"drawingNo":{},"status":[],"productCode":{"code":"M8"}}]}}
                """);

        assertThat(resp.getMesRequestId()).isEqualTo("B8");
        assertThat(resp.getMessage()).isNull();
        assertThat(resp.getData().getItems()).hasSize(1);
        assertThat(resp.getData().getItems().get(0).getProductCode()).isNull();
    }

    @Test
    @DisplayName("非整数数字状态码不能因截断小数被误认成成功")
    void doesNotTreatFractionalCodeAsSuccess() {
        assertThat(read("{\"code\":0.5,\"bizId\":\"B9\"}").getSuccess()).isFalse();
    }

    @Test
    @DisplayName("根级 bizId 与 data.bizId 同时存在时不能互相冲突")
    void rejectsConflictingRequestIdsEvenWhenThePreferredIdMatches() {
        MesMaterialResolveResp resp = read("""
                {"code":0,"bizId":"B10","data":{"bizId":"OTHER"}}
                """);

        assertThat(resp.getMesRequestId()).isEqualTo("B10");
        assertThat(resp.matchesRequestId("B10")).isFalse();
    }

    @Test
    @DisplayName("根级 bizId 与旧字段 mesRequestId 冲突时也必须拒绝")
    void rejectsConflictingRootIdentityAliases() {
        MesMaterialResolveResp resp = read("""
                {"code":0,"bizId":"B10A","mesRequestId":"OTHER"}
                """);

        assertThat(resp.matchesRequestId("B10A")).isFalse();
    }

    @Test
    @DisplayName("兼容只在一个位置返回请求 ID 的响应")
    void acceptsRequestIdFromEitherSupportedLocation() {
        assertThat(read("{\"code\":0,\"bizId\":\"B11\"}").matchesRequestId("B11")).isTrue();
        assertThat(read("{\"code\":0,\"data\":{\"bizId\":\"B12\"}}").matchesRequestId("B12")).isTrue();
    }

    private MesMaterialResolveResp read(String json) {
        try {
            return mapper.readValue(json, MesMaterialResolveResp.class);
        } catch (Exception error) {
            throw new IllegalStateException("反序列化不应失败: " + error.getMessage(), error);
        }
    }
}
