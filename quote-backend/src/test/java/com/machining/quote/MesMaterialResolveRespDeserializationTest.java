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

    private MesMaterialResolveResp read(String json) {
        try {
            return mapper.readValue(json, MesMaterialResolveResp.class);
        } catch (Exception error) {
            throw new IllegalStateException("反序列化不应失败: " + error.getMessage(), error);
        }
    }
}
