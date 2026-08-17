package com.machining.quote;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.machining.quote.domain.QuoteStatusWorkflow;
import com.machining.quote.dto.DeliveryGenerateReq;
import com.machining.quote.dto.InternalTargetLeadTimeSetReq;
import com.machining.quote.dto.InternalTargetLeadTimeUpdateReq;
import com.machining.quote.dto.PageResult;
import com.machining.quote.dto.QuoteLeadTimeSetReq;
import com.machining.quote.dto.QuoteLeadTimeUpdateReq;
import com.machining.quote.dto.QuoteSubmitReq;
import com.machining.quote.dto.aps.ApsDeliveryItemDTO;
import com.machining.quote.entity.DictMaterial;
import com.machining.quote.entity.DictMaterialType;
import com.machining.quote.entity.CustomerMaster;
import com.machining.quote.entity.DictProcessCategory;
import com.machining.quote.entity.DictProcessDetail;
import com.machining.quote.entity.ItemQuoteVersion;
import com.machining.quote.entity.OrderDeliveryTicket;
import com.machining.quote.dto.ProcessSheetDTO;
import com.machining.quote.entity.OrderRfq;
import com.machining.quote.mapper.ItemQuoteVersionMapper;
import com.machining.quote.mapper.DictMaterialMapper;
import com.machining.quote.mapper.DictMaterialTypeMapper;
import com.machining.quote.mapper.CustomerMasterMapper;
import com.machining.quote.mapper.DictProcessCategoryMapper;
import com.machining.quote.mapper.DictProcessDetailMapper;
import com.machining.quote.mapper.OrderDeliveryTicketMapper;
import com.machining.quote.mapper.OrderRfqMapper;
import com.machining.quote.service.aps.ApsSourceService;
import com.machining.quote.service.AuditLogRetentionService;
import com.machining.quote.service.DeliveryService;
import com.machining.quote.service.EngineerService;
import com.machining.quote.service.InternalTargetLeadTimeService;
import com.machining.quote.service.OrderManagementService;
import com.machining.quote.service.QuoteProcessService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

class RepeatQuoteInfrastructureCharacterizationTest extends CoreWorkflowIntegrationTestSupport {

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void repeatSourceImplementationUsesReleasedItemLookupWithoutDeliveryTicketCandidatePath() throws Exception {
        String service = source("src/main/java/com/machining/quote/service/EngineerService.java");
        String repeatMethod = methodBody(service, "private Map<String, RepeatSource> findRepeatSources", "private boolean hasReusableQuote");

        assertThat(repeatMethod)
                .as("重复件候选入口应直接查 item_quote_version，不能再依赖 order_delivery_ticket")
                .doesNotContain("OrderDeliveryTicket")
                .doesNotContain("ticketMapper");
        assertThat(repeatMethod)
                .as("重复件候选不应使用 QueryWrapper OR 拼接大量图号版本条件")
                .doesNotContain(".or(");
        assertThat(service)
                .as("当前订单图号版本集合需要固定批次拆分，避免单条 SQL 参数过长")
                .contains("REPEAT_SOURCE_KEY_BATCH_SIZE");

        String mapper = source("src/main/java/com/machining/quote/mapper/ItemQuoteVersionMapper.java");
        String provider = optionalSource("src/main/java/com/machining/quote/mapper/RepeatQuoteSourceSqlProvider.java");
        String repeatSqlSource = mapper + "\n" + provider;
        assertThat(repeatSqlSource)
                .contains("selectLatestReleasedRepeatSources")
                .contains("JOIN LATERAL")
                .contains("VALUES");
        assertThat(provider)
                .as("重复件候选必须排除导入默认空 JSON 快照，不能只判断 IS NOT NULL")
                .contains("h.material_data IS NOT NULL")
                .contains("h.material_data <> '{}'::jsonb")
                .contains("h.process_route IS NOT NULL")
                .contains("h.process_route <> '[]'::jsonb")
                .doesNotContain("h.fee_data IS NOT NULL")
                .doesNotContain("h.fee_data <> '[]'::jsonb");
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void repeatReleasedItemLookupUsesPartialIndexWithoutCustomerOrTicketIndex() throws Exception {
        Path migrationPath = Path.of(
                "src", "main", "resources", "db", "migration", "V1__qpo_baseline_schema.sql");
        assertThat(migrationPath)
                .as("阶段 1 必须新增重复件已提单历史零件部分索引迁移")
                .exists();

        String migration = Files.readString(migrationPath, StandardCharsets.UTF_8);
        String schema = source("src/main/resources/db/migration/V1__qpo_baseline_schema.sql");
        String allMigrations;
        try (var files = Files.list(Path.of("src", "main", "resources", "db", "migration"))) {
            allMigrations = String.join("\n", files
                    .filter(path -> path.getFileName().toString().endsWith(".sql"))
                    .sorted()
                    .map(path -> {
                        try {
                            return Files.readString(path, StandardCharsets.UTF_8);
                        } catch (Exception ex) {
                            throw new RuntimeException(ex);
                        }
                    })
                    .toList());
        }
        String combined = migration + "\n" + schema + "\n" + allMigrations;

        assertThat(combined)
                .contains("idx_item_quote_version_repeat_released_key")
                .contains("ON item_quote_version USING btree (part_no, version, id DESC)")
                .contains("'已提单'::text")
                .contains("tax_included_unit_price IS NOT NULL")
                .contains("material_data IS NOT NULL")
                .contains("material_data <> '{}'::jsonb")
                .contains("process_route IS NOT NULL")
                .contains("process_route <> '[]'::jsonb");
        assertThat(combined)
                .as("重复件匹配不能退回普通全量联合索引")
                .doesNotContain("item_quote_version(part_no, version, status, id DESC)");
        String repeatIndex = combined.substring(combined.indexOf("idx_item_quote_version_repeat_released_key"));
        repeatIndex = repeatIndex.substring(0, repeatIndex.indexOf(";"));
        assertThat(repeatIndex)
                .as("重复件匹配索引不能新增客户或提单表专用索引")
                .doesNotContain("customer")
                .doesNotContain("order_delivery_ticket")
                .doesNotContain("fee_data");
    }
}
