package com.machining.quote;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MesMaterialDedupDocumentationCharacterizationTest {
    private static final Path REPEAT_MATCH_DOC = statusDoc("05-重复件匹配仅使用已提单历史零件开发文档.md");
    private static final Path CALLBACK_DOC = statusDoc("18-MES物料号异步回调开发文档.md");
    private static final Path DEDUP_DOC = statusDoc("19-MES物料号同图号版本去重开发文档.md");
    private static final Path RFQ_MATERIAL_DOC = statusDoc("32-RFQ询价订单物料号确认字段与MES回写开发文档.md");
    private static final Path SALES_IMPORT_DOC = statusDoc("33-销售订单导入即提单与异常治理重构开发文档.md");
    private static final Path MES_SYNC_DOC = Path.of(
            "..", "docs", "QPO-MES-WMS对接", "03-MES物料号同步开发文档.md");
    private static final Path MES_INTERFACE_DOC = Path.of(
            "..", "docs", "QPO-MES-WMS对接", "06-提供给MES的物料号接口对接说明.md");
    private static final Path LOG_OPS_DOC = Path.of(
            "..", "docs", "QPO日志模块", "03-日志运维说明.md");
    private static final Path FILE_LOG_DOC = Path.of(
            "..", "docs", "QPO日志模块", "04-主流程文件日志规范化开发文档.md");
    private static final Path PERMISSION_DOC = Path.of(
            "..", "docs", "QPO权限用户体系", "02-权限点清单与现有角色映射.md");
    private static final Path PERMISSION_SERVICE = Path.of(
            "src", "main", "java", "com", "machining", "quote", "service", "PermissionService.java");

    @Test
    void matchingAndMesDocsUseFrontendReuseAndActiveRequestDedupContract() throws IOException {
        assertThat(read(REPEAT_MATCH_DOC))
                .contains("物料号确认元组", "material_no_status = 已确认", "来源未确认不阻断核价快照复用");
        assertThat(read(RFQ_MATERIAL_DOC))
                .contains("前置重复件核价", "提单行继承", "material_no_status = 已确认");
        assertThat(read(SALES_IMPORT_DOC))
                .contains("销售订单导入自动快照补全", "物料号确认元组", "自动提单");

        for (Path path : List.of(CALLBACK_DOC, DEDUP_DOC, MES_SYNC_DOC, MES_INTERFACE_DOC)) {
            assertThat(read(path))
                    .contains("活动主请求", "follower", "QPO fan-out")
                    .doesNotContain("REUSE_SUCCESS", "ROLE_REUSED", "reusedMaterialCount");
        }
        assertThat(read(DEDUP_DOC))
                .contains("MES 去重只负责活动主请求和 follower", "不查询 `order_delivery_ticket` 历史成功行");
    }

    @Test
    void logAndPermissionDocsKeepActiveDedupWithoutNewPermissionOrSensitivePayloads() throws IOException {
        assertThat(read(LOG_OPS_DOC))
                .contains(
                        "DELIVERY_MES_MATERIAL_DEDUP_FOLLOW",
                        "DELIVERY_MES_MATERIAL_DEDUP_FANOUT",
                        "dedupFollowerCount",
                        "submittedToMesCount",
                        "MES 去重只负责活动主请求和 follower",
                        "不得记录 MES token、QPO 入站开放接口统一 token、Authorization header、完整请求体或完整回调体")
                .doesNotContain("DELIVERY_MES_MATERIAL_DEDUP_REUSE", "reusedMaterialCount");

        assertThat(read(FILE_LOG_DOC))
                .contains(
                        "MES material dedup master created",
                        "MES material dedup follow",
                        "MES material dedup fanout completed",
                        "MES material dedup fanout failed",
                        "dedupKey",
                        "不记录完整 MES 请求 / 响应 / 回调 JSON")
                .doesNotContain("MES material dedup reuse");

        assertThat(read(PERMISSION_DOC))
                .contains(
                        "MES 同图号版本去重不新增用户权限",
                        "MES 去重只负责活动主请求和 follower",
                        "`market:delivered-item:mes-sync`")
                .doesNotContain("market:delivered-item:mes-dedup", "market:delivered-item:dedup");
        assertThat(read(PERMISSION_SERVICE))
                .doesNotContain("market:delivered-item:mes-dedup", "market:delivered-item:dedup");
    }

    private static Path statusDoc(String fileName) {
        return Path.of("..", "docs", "QPO报价状态流转", fileName);
    }

    private static String read(Path path) throws IOException {
        return Files.readString(path, StandardCharsets.UTF_8).replace("\r\n", "\n");
    }
}
