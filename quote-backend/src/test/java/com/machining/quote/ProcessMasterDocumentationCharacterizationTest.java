package com.machining.quote;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class ProcessMasterDocumentationCharacterizationTest {
    private static final Path DOC_ROOT = Path.of("..", "docs");

    @Test
    void stageFifteenPlanRecordsFinalPermissionLogAndIntegrationClosure() throws IOException {
        String plan = read(doc("QPO报价状态流转", "38-"));

        assertThat(plan)
                .contains(
                        "文档状态：阶段 15 已完成",
                        "阶段 15 收口",
                        "不新增用户权限",
                        "engineer:dict:view",
                        "engineer:dict:process-manage",
                        "processRouteCount",
                        "processDetailId + processCode + categoryName + detailName",
                        "按当前有效小时价格重新计算工艺金额",
                        "复用时复制历史标准工艺路线快照，不按当前字典价格重算",
                        "MES 生产下发只对外传 `processCode / categoryName / detailName / unitWorkHours / sortNo`"
                );
    }

    @Test
    void permissionAndLogDocsDescribeTwoLevelProcessWithoutNewPermissionsOrNoisyLogs() throws IOException {
        String permissionDoc = read(doc("QPO权限用户体系", "02-"));
        String logOpsDoc = read(doc("QPO日志模块", "03-"));
        String fileLogDoc = read(doc("QPO日志模块", "04-"));

        assertThat(permissionDoc)
                .contains(
                        "两级工艺主数据阶段 15 收口",
                        "本次不新增用户权限",
                        "不修改默认角色映射或数据范围",
                        "工艺大类和具体工艺查询继续复用 `engineer:dict:view`",
                        "维护动作继续复用 `engineer:dict:process-manage`",
                        "具体工艺小时价格维护继续复用 `engineer:dict:process-manage`，不新增权限点。"
                );
        assertThat(logOpsDoc)
                .contains(
                        "DICT_PROCESS_CATEGORY_CREATE",
                        "DICT_PROCESS_DETAIL_CREATE",
                        "具体工艺价格保存走 `DICT_PROCESS_DETAIL_UPDATE`，读取和实时预估不记高频日志。",
                        "普通查询、工艺选择器加载、核价页读取和页面刷新不写操作日志",
                        "processRouteCount",
                        "不记录完整 `processRoute` JSON"
                );
        assertThat(fileLogDoc)
                .contains(
                        "Process route priced itemId={}",
                        "processDetailId={} processCode={} categoryName={} detailName={}",
                        "Process detail unavailable itemId={}",
                        "不记录完整 `processRoute` JSON"
                )
                .doesNotContain("Process rate matched itemId={} processName={}");
    }

    @Test
    void integrationDocsKeepSnapshotsInternalAndSendOnlyStructuredMesProductionRoute() throws IOException {
        String demandDoc = read(doc("QPO-MES-WMS", "01-"));
        String deliveredDoc = read(doc("QPO-MES-WMS", "02-"));
        String mesMaterialDoc = read(doc("QPO-MES-WMS", "03-"));
        String wmsDoc = read(doc("QPO-MES-WMS", "04-WMS"));
        String mesProductionDoc = read(doc("QPO-MES-WMS", "09-"));

        assertThat(demandDoc + deliveredDoc)
                .contains(
                        "processDetailId / processCode / categoryName / detailName / hourlyRate / unitWorkHours / lineCost / sortNo",
                        "后续字典价格变化不改写历史提单事实"
                );
        assertThat(wmsDoc)
                .contains(
                        "WMS 锁库不重新查询当前工艺字典",
                        "复制原始提单行的标准工艺快照",
                        "不得记录完整工艺 JSON"
                );
        assertThat(mesProductionDoc)
                .contains(
                        "processCode / categoryName / detailName / unitWorkHours / sortNo",
                        "不传 QPO 内部工艺成本字段"
                );
        assertThat(mesMaterialDoc)
                .contains(
                        "不重新计算工艺路线",
                        "不记录完整 `processRoute` JSON",
                        "如果没有活跃主请求：当前行成为 MASTER"
                )
                .doesNotContain("如果可复用已确认物料号：不调用 MES，直接写 SUCCESS");
    }

    @Test
    void businessDocsUseNewProcessIdentityAcrossPricingRepeatReleaseAndSalesOrders() throws IOException {
        String pricingDoc = read(doc("QPO报价状态流转", "04-"));
        String repeatDoc = read(doc("QPO报价状态流转", "05-"));
        String salesOrderDoc = read(doc("QPO报价状态流转", "14-"));
        String importDoc = read(doc("QPO报价状态流转", "33-"));
        String rfqReleaseDoc = read(doc("QPO报价状态流转", "37-"));

        assertThat(pricingDoc)
                .contains(
                        "核价工艺选择使用 `processDetailId` 作为稳定身份",
                        "小时价格使用有效小时价格：具体工艺 `dict_process_detail.hourly_rate` 非空（含 0）优先，否则继承工艺大类 `dict_process_category.hourly_rate`",
                        "不再按数量档位取工艺价格"
                );
        assertThat(repeatDoc)
                .contains(
                        "重复件复用复制历史路线中的 `processDetailId`、`unitWorkHours`、`hourlyRate` 和 `lineCost` 快照",
                        "当前具体工艺或大类缺失、停用时不按名称模糊替代",
                        "复用成功后的新路线必须包含 `processCode / categoryName / detailName`"
                );
        assertThat(salesOrderDoc + importDoc + rfqReleaseDoc)
                .contains(
                        "标准工艺路线快照",
                        "不按当前字典重新计价",
                        "自动提单把该标准工艺快照写入已提零件需求池",
                        "工艺路线不发送给 U9"
                );
    }

    @Test
    void updatedDocsDoNotEmbedRealSecretsOrAuthorizationValues() throws IOException {
        String docs = read(doc("QPO报价状态流转", "38-")) + "\n"
                + read(doc("QPO权限用户体系", "02-")) + "\n"
                + read(doc("QPO日志模块", "03-")) + "\n"
                + read(doc("QPO日志模块", "04-")) + "\n"
                + read(doc("QPO-MES-WMS", "09-"));

        assertThat(docs)
                .doesNotContain(
                        "Authorization: Bearer ey",
                        "access_token\":\"",
                        "client_secret\":\"",
                        "ERP_MES_AUTH_TOKEN=ey",
                        "ERP_WMS_AUTH_TOKEN=ey",
                        "ERP_U9_AUTH_TOKEN=ey"
                );
    }

    private static Path doc(String dirPrefix, String filePrefix) throws IOException {
        try (Stream<Path> paths = Files.walk(DOC_ROOT)) {
            return paths
                    .filter(Files::isRegularFile)
                    .filter(path -> path.getParent() != null)
                    .filter(path -> path.getParent().getFileName().toString().startsWith(dirPrefix))
                    .filter(path -> path.getFileName().toString().startsWith(filePrefix))
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("Missing doc prefix " + dirPrefix + "/" + filePrefix));
        }
    }

    private static String read(Path path) throws IOException {
        assertThat(Files.exists(path))
                .as("%s should exist", path)
                .isTrue();
        return Files.readString(path, StandardCharsets.UTF_8).replace("\r\n", "\n");
    }
}
