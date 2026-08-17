package com.machining.quote;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MaterialMasterDocumentationCharacterizationTest {
    private static final Path DOCS = Path.of("..", "docs");
    private static final Path STATUS_DOCS = DOCS.resolve("QPO报价状态流转");

    @Test
    void relatedStatusDocumentsUseMaterialCodeTypeCategoryAndShapeVocabulary() throws IOException {
        for (String name : List.of(
                "04-零件核价预览与重复件复用职责拆分开发文档.md",
                "05-重复件匹配仅使用已提单历史零件开发文档.md",
                "14-销售订单独立主表开发文档.md",
                "26-销售匹配材料字段口径重构开发文档.md",
                "33-销售订单导入即提单与异常治理重构开发文档.md",
                "36-重复匹配复用MES已确认物料号并收窄已提零件MES去重开发文档.md",
                "37-RFQ议价提单派生销售订单并接入U9开发文档.md",
                "38-数据库基线重建与两级工艺主数据全链路重构开发文档.md")) {
            String doc = read(STATUS_DOCS.resolve(name));
            assertThat(doc)
                    .as(name + " should describe the new material identity vocabulary")
                    .contains("材料编码", "材料类型", "材质分类", "材料形态")
                    .doesNotContain("旧 `material_type` 描述为业务材料类型");
        }
    }

    @Test
    void permissionAndLoggingDocsCloseMaterialMasterStage10WithoutNewPermission() throws IOException {
        assertThat(read(DOCS.resolve(Path.of("QPO权限用户体系", "02-权限点清单与现有角色映射.md"))))
                .contains(
                        "材料主数据编码与类型阶段 10 收口",
                        "本次不新增用户权限",
                        "材料类型维护复用 `engineer:dict:material-manage`",
                        "材料类型查询复用 `engineer:dict:view`");

        for (Path path : List.of(
                DOCS.resolve(Path.of("QPO日志模块", "03-日志运维说明.md")),
                DOCS.resolve(Path.of("QPO日志模块", "04-主流程文件日志规范化开发文档.md")))) {
            assertThat(read(path))
                    .as(path + " should document material logging redaction")
                    .contains("材料主数据", "完整材料价格矩阵", "完整 `materialData`")
                    .contains("摘要")
                    .doesNotContain("记录完整材料价格矩阵", "记录完整 `materialData`");
        }
    }

    @Test
    void mesWmsDocsExposeMaterialIdentityWithoutFullMaterialDataPayloads() throws IOException {
        for (Path path : List.of(
                DOCS.resolve(Path.of("QPO-MES-WMS对接", "04-WMS锁库分配与锁库零件模块开发文档.md")),
                DOCS.resolve(Path.of("QPO-MES-WMS对接", "07-提供给WMS的库存锁库接口对接说明.md")),
                DOCS.resolve(Path.of("QPO-MES-WMS对接", "09-提供给MES的待生产下发接口对接说明.md")))) {
            assertThat(read(path))
                    .as(path + " should expose material identity snapshots")
                    .contains("engineeringMaterialCode", "engineeringMaterialTypeName", "engineeringMaterialCategory")
                    .contains("材料编码", "材料类型", "材质分类", "材料形态")
                    .doesNotContain("`materialData` | 否 | 材料核价快照");
        }
    }

    @Test
    void stagePlanRecordsCompletedRegressionCoverageThroughStage12() throws IOException {
        assertThat(read(STATUS_DOCS.resolve("39-材料主数据编码与类型全链路重构开发文档.md")))
                .contains(
                        "文档状态：阶段 1-12 已完成",
                        "阶段 10 完成记录",
                        "阶段 12 完成记录",
                        "材料类型维护复用 `engineer:dict:material-manage`",
                        "材料类型 API",
                        "已提/待产/锁库文案")
                .doesNotContain("文档状态：阶段 1-9 已完成", "阶段 12 待开发");
    }

    private String read(Path path) throws IOException {
        assertThat(Files.exists(path)).as("%s should exist", path).isTrue();
        return Files.readString(path, StandardCharsets.UTF_8).replace("\r\n", "\n");
    }
}
