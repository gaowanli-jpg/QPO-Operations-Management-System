package com.machining.quote;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MesMasterDataDocumentationCharacterizationTest {
    private static final Path MES_MASTER_DATA_DOC = Path.of(
            "..", "docs", "QPO-MES-WMS对接", "10-MES工艺材料主数据同步说明.md");
    private static final Path MES_PRODUCTION_DOC = Path.of(
            "..", "docs", "QPO-MES-WMS对接", "09-提供给MES的待生产下发接口对接说明.md");
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
    private static final Path DESIGN_DOC = Path.of(
            "..", "docs", "superpowers", "specs", "2026-08-13-mes-master-data-sync-design.md");

    @Test
    void dataCenterDocsListMesMasterDataEndpointsAndFallbackEnvVars() throws IOException {
        String dataCenter = read(DATA_CENTER_DOC);
        String masterData = read(MES_MASTER_DATA_DOC);

        assertThat(dataCenter)
                .contains(
                        "MES 主数据同步阶段 9 收口",
                        "MES_PROCESS_TYPE_LIST",
                        "MES_MATERIAL_PAGE",
                        "ERP_DATA_CENTER_MES_PROCESS_TYPE_LIST_PATH",
                        "ERP_DATA_CENTER_MES_MATERIAL_PAGE_PATH",
                        "GET",
                        "targetSystem = MES",
                        "timeout 不低于 `60000ms`",
                        "pageNo=1&pageSize=200");

        assertThat(masterData)
                .contains(
                        "MES_PROCESS_TYPE_LIST",
                        "MES_MATERIAL_PAGE",
                        "ERP_DATA_CENTER_MES_PROCESS_TYPE_LIST_PATH=/api/v1/mes/openapi/process-type/list",
                        "ERP_DATA_CENTER_MES_MATERIAL_PAGE_PATH=/api/v1/mes/openapi/material/page");
    }

    @Test
    void permissionDocsReuseDictionaryManagePermissionsWithoutCommonPermissions() throws IOException {
        String catalog = read(PERMISSION_CATALOG_DOC);
        String admin = read(PERMISSION_ADMIN_DOC);

        assertThat(catalog)
                .contains(
                        "POST /api/dict/process-categories/sync-mes",
                        "POST /api/dict/materials/sync-mes",
                        "engineer:dict:process-manage",
                        "engineer:dict:material-manage",
                        "DICT_PROCESS_MES_SYNC",
                        "DICT_MATERIAL_MES_SYNC")
                .doesNotContain("common:mes-master-data-sync");

        assertThat(admin)
                .contains(
                        "从 MES 同步工艺",
                        "从 MES 同步材料",
                        "engineer:dict:process-manage",
                        "engineer:dict:material-manage");
    }

    @Test
    void mesAndLogDocsCloseSyncGovernanceAndProductionPayloadContract() throws IOException {
        String masterData = read(MES_MASTER_DATA_DOC);
        String production = read(MES_PRODUCTION_DOC);
        String logOps = read(LOG_OPS_DOC);
        String fileLog = read(FILE_LOG_DOC);

        assertThat(masterData).contains("deletedCount");
        assertThat(logOps).contains("deletedCount");
        assertThat(fileLog).contains("deletedCount");

        assertThat(masterData)
                .contains(
                        "MES 无材料单价",
                        "QPO 继续维护材料单价",
                        "材料单价缺失时禁止完成核价",
                        "真实写 MES 仍使用独立显式开关",
                        "不记录完整请求、完整响应、token、Authorization、Cookie 或 client_secret");

        assertThat(production)
                .contains(
                        "processRoute[].processTypeCode = QPO processCode = MES code",
                        "processRoute[].processName = QPO detailName",
                        "bom[].itemCode = QPO engineeringMaterialCode = MES material code",
                        "bom[].quantity = QPO materialGrossWeight kg");

        assertThat(logOps)
                .contains(
                        "DICT_PROCESS_MES_SYNC",
                        "DICT_MATERIAL_MES_SYNC",
                        "mes_master_data_sync_run",
                        "syncRunId",
                        "fetchedCount",
                        "upsertedCount",
                        "不得记录完整 MES 主数据响应体");

        assertThat(fileLog)
                .contains(
                        "MES master data sync started",
                        "MES master data sync completed",
                        "MES master data sync failed",
                        "endpointCode",
                        "syncRunId",
                        "不包含完整 MES 主数据响应体");
    }

    @Test
    void stageNinePlanAndDocsDoNotContainRealIntegrationSecrets() throws IOException {
        for (Path path : docsToScan()) {
            String content = read(path);
            assertThat(content)
                    .as("secret scan: %s", path)
                    .doesNotContain("Authorization: Bearer " + "eyJ")
                    .doesNotContain("access_token=" + "eyJ")
                    .doesNotContainPattern("(?i)(client_secret|access_token|authorization|cookie|password)[ \\t]*[:=][ \\t]*(?!\\{)[A-Za-z0-9+/=_\\-.]{12,}")
                    .doesNotContainPattern("(?i)bearer[ \\t]+(?!\\{)[A-Za-z0-9._~+/=-]{20,}");
        }
    }

    private static List<Path> docsToScan() {
        return List.of(
                MES_MASTER_DATA_DOC,
                MES_PRODUCTION_DOC,
                DATA_CENTER_DOC,
                PERMISSION_CATALOG_DOC,
                PERMISSION_ADMIN_DOC,
                LOG_OPS_DOC,
                FILE_LOG_DOC,
                DESIGN_DOC
        );
    }

    private static String read(Path path) throws IOException {
        assertThat(path).exists();
        return Files.readString(path, StandardCharsets.UTF_8).replace("\r\n", "\n");
    }
}
