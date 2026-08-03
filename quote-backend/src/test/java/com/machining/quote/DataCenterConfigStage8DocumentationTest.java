package com.machining.quote;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DataCenterConfigStage8DocumentationTest {
    private static final Path ENV_EXAMPLE = Path.of("..", "erp.env.example");
    private static final Path SYSTEM_OUTBOUND_DOC = Path.of(
            "..", "docs", "QPO系统配置", "03-系统对接配置开发文档.md");
    private static final Path DATA_CENTER_DOC = Path.of(
            "..", "docs", "QPO系统配置", "05-数据中心统一对接开发文档.md");
    private static final Path PERMISSION_CATALOG_DOC = Path.of(
            "..", "docs", "QPO权限用户体系", "02-权限点清单与现有角色映射.md");
    private static final Path PERMISSION_ADMIN_DOC = Path.of(
            "..", "docs", "QPO权限用户体系", "03-权限体系部署与管理员使用说明.md");
    private static final Path LOG_OPS_DOC = Path.of(
            "..", "docs", "QPO日志模块", "03-日志运维说明.md");
    private static final Path FILE_LOG_DOC = Path.of(
            "..", "docs", "QPO日志模块", "04-主流程文件日志规范化开发文档.md");
    private static final Path MES_SYNC_DOC = Path.of(
            "..", "docs", "QPO-MES-WMS对接", "03-MES物料号同步开发文档.md");
    private static final Path WMS_DOC = Path.of(
            "..", "docs", "QPO-MES-WMS对接", "04-WMS锁库分配与锁库零件模块开发文档.md");
    private static final Path MES_MATERIAL_INTERFACE_DOC = Path.of(
            "..", "docs", "QPO-MES-WMS对接", "06-提供给MES的物料号接口对接说明.md");
    private static final Path MES_CALLBACK_DOC = Path.of(
            "..", "docs", "QPO-MES-WMS对接", "08-MES物料号回调接口对接说明.md");
    private static final Path MES_PRODUCTION_DOC = Path.of(
            "..", "docs", "QPO-MES-WMS对接", "09-提供给MES的待生产下发接口对接说明.md");
    private static final Path SALES_ORDER_U9_DOC = Path.of(
            "..", "docs", "QPO报价状态流转", "16-销售订单U9同步模块开发文档.md");

    @Test
    void deploymentDocsDescribeDataCenterAsPrimaryOutboundAndEnvAsFallbackOnly() throws IOException {
        assertThat(read(SYSTEM_OUTBOUND_DOC))
                .contains(
                        "数据中心统一对接阶段 8 收口",
                        "QPO 出站调用统一走数据中心",
                        "旧 U9 / MES / WMS 直连配置仅作为 deprecated fallback");

        assertThat(read(PERMISSION_ADMIN_DOC))
                .contains(
                        "QPO 出站调用默认走数据中心",
                        "ERP_DATA_CENTER_TOKEN_URL",
                        "ERP_DATA_CENTER_CLIENT_ID",
                        "ERP_DATA_CENTER_CLIENT_SECRET",
                        "ERP_DATA_CENTER_MES_MATERIAL_PATH",
                        "ERP_DATA_CENTER_WMS_ALLOCATION_PATH",
                        "ERP_DATA_CENTER_U9_SALES_ORDER_SYNC_PATH",
                        "ERP_DATA_CENTER_MES_PRODUCTION_DISPATCH_PATH",
                        "真实 `ERP_DATA_CENTER_CLIENT_SECRET` 只写入真实 `erp.env` 或系统配置密文字段");

        assertThat(read(ENV_EXAMPLE))
                .contains(
                        "ERP_DATA_CENTER_TOKEN_URL=http://192.168.70.250:8080/realms/api-platform/protocol/openid-connect/token",
                        "ERP_DATA_CENTER_CLIENT_ID=app_8ed28b2a1faf4a0b",
                        "ERP_DATA_CENTER_CLIENT_SECRET=",
                        "ERP_DATA_CENTER_MES_MATERIAL_PATH=",
                        "ERP_DATA_CENTER_WMS_ALLOCATION_PATH=",
                        "ERP_DATA_CENTER_U9_SALES_ORDER_SYNC_PATH=",
                        "ERP_DATA_CENTER_MES_PRODUCTION_DISPATCH_PATH=")
                .contains("deprecated direct-system fallback");
    }

    @Test
    void permissionAndLoggingDocsCloseDataCenterGovernanceWithoutCommonPermissionsOrSecrets() throws IOException {
        assertThat(read(PERMISSION_CATALOG_DOC))
                .contains(
                        "出站接口/数据中心配置",
                        "system_data_center_config",
                        "system_data_center_endpoint_config",
                        "MES_MATERIAL_RESOLVE",
                        "WMS_INVENTORY_ALLOCATE",
                        "U9_SALES_ORDER_SYNC",
                        "MES_PRODUCTION_DISPATCH")
                .doesNotContain("common:data-center");

        assertThat(read(LOG_OPS_DOC))
                .contains(
                        "QPO 出站调用统一走数据中心",
                        "SYSTEM_DATA_CENTER_CONFIG_CREATE",
                        "SYSTEM_DATA_CENTER_CONFIG_UPDATE",
                        "SYSTEM_DATA_CENTER_CONFIG_TEST",
                        "SYSTEM_DATA_CENTER_CONFIG_TEST_FAILED",
                        "SYSTEM_DATA_CENTER_ENDPOINT_UPDATE",
                        "SYSTEM_DATA_CENTER_ENDPOINT_TEST",
                        "SYSTEM_DATA_CENTER_ENDPOINT_TEST_FAILED",
                        "不得记录 `client_secret`、access token、QPO 入站 token、Authorization header 或完整请求/响应体");

        assertThat(read(FILE_LOG_DOC))
                .contains(
                        "Data center config created/updated/token test completed/token test failed/activated/disabled",
                        "Data center endpoint updated/test completed/test failed",
                        "统一 client 调用只记录 `endpointCode`、`targetSystemCode`、host、path、HTTP status 或网络错误摘要",
                        "不得记录 `client_secret`、access token、QPO 入站 token、Authorization header、完整 token 响应、完整业务请求体或完整业务响应体");
    }

    @Test
    void mesWmsU9DocsStateBusinessModulesRouteThroughDataCenter() throws IOException {
        assertThat(read(MES_SYNC_DOC))
                .contains(
                        "MES_MATERIAL_RESOLVE",
                        "QPO -> 数据中心 -> MES",
                        "数据中心只需要转发主 `mesRequestId` 的 MES 结果");

        assertThat(read(WMS_DOC))
                .contains(
                        "数据中心统一对接阶段 8 收口",
                        "WMS_INVENTORY_ALLOCATE",
                        "QPO -> 数据中心 -> WMS",
                        "旧 `ERP_WMS_*` 仅作为 deprecated fallback");

        assertThat(read(MES_PRODUCTION_DOC))
                .contains(
                        "提供给数据中心的待生产下发接口对接说明",
                        "MES_PRODUCTION_DISPATCH",
                        "QPO -> 数据中心 -> MES",
                        "数据中心负责转发到 MES 生产接口");

        assertThat(read(SALES_ORDER_U9_DOC))
                .contains(
                        "U9_SALES_ORDER_SYNC",
                        "QPO -> 数据中心 -> U9",
                        "U9 同步底层调用统一走数据中心",
                        "旧 `ERP_U9_*` 仅作为 deprecated fallback");
    }

    @Test
    void dataCenterFacingInterfaceDocsAreDeliverableAndPointInboundCallbackToQpoToken() throws IOException {
        assertThat(read(MES_MATERIAL_INTERFACE_DOC))
                .contains(
                        "本文档只描述 QPO 通过数据中心获取 MES 物料号",
                        "回调接口详情见 `08-MES物料号回调接口对接说明.md`",
                        "真实 token 单独通过受控渠道交付数据中心");

        assertThat(read(MES_CALLBACK_DOC))
                .contains(
                        "QPO 提供给数据中心的 MES 物料号回调接口对接说明",
                        "调用方向：数据中心 -> QPO",
                        "Authorization: Bearer {QPO_INBOUND_CALLBACK_TOKEN}",
                        "真实 token 单独交付数据中心",
                        "数据中心拿到 **回调 URL + 回调 token + 本文档请求/响应字段说明** 后，即可调用本接口");

        assertThat(read(DATA_CENTER_DOC))
                .contains(
                        "QPO 出站调用统一走数据中心",
                        "数据中心可使用 QPO 入站接口文档和 QPO 管理员提供的 token 调用 QPO");
    }

    @Test
    void documentationAndExamplesDoNotContainRealIntegrationSecrets() throws IOException {
        for (Path path : stage8Artifacts()) {
            String content = read(path);
            assertThat(content)
                    .as("secret scan: %s", path)
                    .doesNotContain(
                            "Authorization: Bearer " + "eyJ",
                            "access_token=" + "eyJ",
                            "ERP_DATA_CENTER_CLIENT_SECRET=" + "X",
                            "QPO_INBOUND_CALLBACK_TOKEN=")
                    .doesNotContainPattern("ERP_DATA_CENTER_CLIENT_SECRET=[A-Za-z0-9+/=_-]+")
                    .doesNotContainPattern("client_secret=[A-Za-z0-9+/=_-]{12,}");
        }
    }

    private static List<Path> stage8Artifacts() {
        return List.of(
                ENV_EXAMPLE,
                SYSTEM_OUTBOUND_DOC,
                DATA_CENTER_DOC,
                PERMISSION_CATALOG_DOC,
                PERMISSION_ADMIN_DOC,
                LOG_OPS_DOC,
                FILE_LOG_DOC,
                MES_SYNC_DOC,
                WMS_DOC,
                MES_MATERIAL_INTERFACE_DOC,
                MES_CALLBACK_DOC,
                MES_PRODUCTION_DOC,
                SALES_ORDER_U9_DOC
        );
    }

    private static String read(Path path) throws IOException {
        assertThat(path).exists();
        return Files.readString(path, StandardCharsets.UTF_8).replace("\r\n", "\n");
    }
}
