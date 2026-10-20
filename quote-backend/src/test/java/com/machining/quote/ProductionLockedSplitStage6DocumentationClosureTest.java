package com.machining.quote;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class ProductionLockedSplitStage6DocumentationClosureTest {
    private static final Path DOC_ROOT = Path.of("..", "docs");

    @Test
    void integrationDocsUseProductionItemAsTheOnlyMesProductionUserEntry() throws IOException {
        String interfaceDoc = read(doc("QPO-MES-WMS", "09-"));
        String wmsDoc = read(doc("QPO-MES-WMS", "04-WMS"));
        String dataCenterDoc = read(doc("QPO系统配置", "05-"));

        assertThat(interfaceDoc)
                .contains(
                        "待产零件页面",
                        "POST /api/order-delivery/production-items/mes-production",
                        "market:production-item:mes-production-submit",
                        "只下发 `PRODUCTION_REQUIRED`"
                )
                .doesNotContain(
                        "QPO 锁库零件模块通过数据中心向 MES 下发",
                        "用户入口：锁库零件页面",
                        "锁库 allocation 行"
                );

        assertThat(wmsDoc)
                .contains(
                        "阶段 31 后",
                        "锁库零件模块只展示 `IN_STOCK_LOCK` 和 `IN_PRODUCTION_LOCK`",
                        "`PRODUCTION_REQUIRED` 行进入待产零件模块",
                        "/production-items"
                )
                .doesNotContain(
                        "锁库零件模块同时承担“锁库结果池”和“待生产下发工作台”职责",
                        "锁库零件下发 MES 生产只处理"
                );

        assertThat(dataCenterDoc)
                .contains("待产零件下发 MES 生产")
                .doesNotContain("锁库零件下发 MES 生产");
    }

    @Test
    void historicalStage22DocIsMarkedSupersededAndNoLongerAdvertisesOldLiveApi() throws IOException {
        String stage22Doc = read(doc("QPO报价状态流转", "22-"));

        assertThat(stage22Doc)
                .contains(
                        "已被 `31-待产零件与锁库零件模块解耦开发文档.md` 替代",
                        "/api/order-delivery/production-items/mes-production",
                        "market:production-item:mes-production-submit",
                        "PRODUCTION_ITEM_MES_PRODUCTION_SUBMIT"
                )
                .doesNotContain(
                        "@PostMapping(\"/allocations/mes-production\")",
                        "@RequirePermission(\"market:locked-item:mes-production-submit\")",
                        "v-permission=\"'market:locked-item:mes-production-submit'\"",
                        "LOCKED_ITEM_MES_PRODUCTION_SUBMIT"
                );
    }

    @Test
    void operationsDocsUseProductionItemNamingForMesProductionDispatch() throws IOException {
        String logOpsDoc = read(doc("QPO日志模块", "03-"));
        String fileLogDoc = read(doc("QPO日志模块", "04-"));
        String stage20Doc = read(doc("QPO报价状态流转", "20-"));

        assertThat(logOpsDoc)
                .contains(
                        "待产零件 MES 生产下发",
                        "PRODUCTION_ITEM_MES_PRODUCTION_SUBMIT",
                        "market:production-item:mes-production-submit"
                )
                .doesNotContain("锁库零件MES生产下发");

        assertThat(fileLogDoc)
                .contains("待产零件提交 MES 生产下发批量任务")
                .doesNotContain(
                        "锁库零件提交 MES 生产下发批量任务",
                        "按锁库零件 MES 生产下发失败查："
                );

        assertThat(stage20Doc)
                .contains("锁库结果池只展示已入库锁定和在产锁定结果")
                .doesNotContain("锁库零件模块读取 `order_delivery_ticket_allocation`，展示已入库锁定、在产锁定和待生产结果。");
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