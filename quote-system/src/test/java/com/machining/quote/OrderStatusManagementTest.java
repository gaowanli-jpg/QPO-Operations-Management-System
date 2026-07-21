package com.machining.quote;

import com.machining.quote.domain.QuoteStatusWorkflow;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OrderStatusManagementTest {
    private static final Path MAIN_JAVA = Path.of("src", "main", "java", "com", "machining", "quote");
    private static final Path MIGRATION = Path.of("src", "main", "resources", "db", "migration",
            "V42__order_status_management_permissions.sql");

    @Test
    void freezesAllowedStatusManagementTransitions() {
        assertThat(QuoteStatusWorkflow.nextAfterRollbackQuoted(QuoteStatusWorkflow.QUOTED, false))
                .as("已报价可以退回已核价，保留报价数据作为草稿")
                .isEqualTo(QuoteStatusWorkflow.PRICED);
        assertThat(QuoteStatusWorkflow.nextAfterRollbackPriced(QuoteStatusWorkflow.PRICED, false))
                .as("已核价可以退回未核价，保留核价数据作为草稿")
                .isEqualTo(QuoteStatusWorkflow.UNPRICED);
        assertThat(QuoteStatusWorkflow.nextAfterDiscard(QuoteStatusWorkflow.UNPRICED, false)).isEqualTo(QuoteStatusWorkflow.DISCARDED);
        assertThat(QuoteStatusWorkflow.nextAfterDiscard(QuoteStatusWorkflow.PRICED, false)).isEqualTo(QuoteStatusWorkflow.DISCARDED);
        assertThat(QuoteStatusWorkflow.nextAfterDiscard(QuoteStatusWorkflow.QUOTED, false)).isEqualTo(QuoteStatusWorkflow.DISCARDED);
        assertThat(QuoteStatusWorkflow.nextAfterRestore(QuoteStatusWorkflow.DISCARDED))
                .as("废弃恢复统一进入已核价，由后续报价保存校验数据完整性")
                .isEqualTo(QuoteStatusWorkflow.PRICED);
    }

    @Test
    void rejectsDiscardForReleasedOrders() {
        assertThatThrownBy(() -> QuoteStatusWorkflow.nextAfterDiscard(QuoteStatusWorkflow.PARTIALLY_RELEASED, false))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("cannot be discarded");
        assertThatThrownBy(() -> QuoteStatusWorkflow.nextAfterDiscard(QuoteStatusWorkflow.RELEASED, false))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("cannot be discarded");
    }

    @Test
    void rejectsRollbackForReleasedOrders() {
        assertThatThrownBy(() -> QuoteStatusWorkflow.nextAfterRollbackQuoted(QuoteStatusWorkflow.PARTIALLY_RELEASED, false))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Only quoted orders");
        assertThatThrownBy(() -> QuoteStatusWorkflow.nextAfterRollbackPriced(QuoteStatusWorkflow.RELEASED, false))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Only priced orders");
    }

    @Test
    void rejectsDiscardOrRollbackWhenDeliveryTicketsExist() {
        assertThatThrownBy(() -> QuoteStatusWorkflow.nextAfterDiscard(QuoteStatusWorkflow.QUOTED, true))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("delivery tickets");
        assertThatThrownBy(() -> QuoteStatusWorkflow.nextAfterRollbackQuoted(QuoteStatusWorkflow.QUOTED, true))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("delivery tickets");
        assertThatThrownBy(() -> QuoteStatusWorkflow.nextAfterRollbackPriced(QuoteStatusWorkflow.PRICED, true))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("delivery tickets");
    }

    @Test
    void permissionMigrationAddsOrderStatusActionsUnderOrderManagement() throws IOException {
        assertThat(MIGRATION).exists();
        String sql = Files.readString(MIGRATION, StandardCharsets.UTF_8);

        assertThat(sql)
                .contains("market:order:view")
                .contains("market:order:rollback-quoted")
                .contains("market:order:rollback-priced")
                .contains("market:order:discard")
                .contains("market:order:restore")
                .contains("已报价退回已核价")
                .contains("已核价退回未核价")
                .contains("废弃订单")
                .contains("恢复废弃订单")
                .contains("SUPER_ADMIN")
                .contains("MARKET_MANAGER")
                .doesNotContain("MARKET_USER")
                .doesNotContain("READONLY");
    }

    @Test
    void orderManagementControllerExposesSecuredStatusActionSkeletons() throws IOException {
        String controller = source("controller/OrderManagementController.java");

        assertThat(controller)
                .contains("OrderStatusManagementService")
                .contains("OrderStatusChangeReq")
                .contains("@PostMapping(\"/orders/{rfqId}/rollback-quoted\")")
                .contains("@RequirePermission(\"market:order:rollback-quoted\")")
                .contains("@PostMapping(\"/orders/{rfqId}/rollback-priced\")")
                .contains("@RequirePermission(\"market:order:rollback-priced\")")
                .contains("@PostMapping(\"/orders/{rfqId}/discard\")")
                .contains("@RequirePermission(\"market:order:discard\")")
                .contains("@PostMapping(\"/orders/{rfqId}/restore\")")
                .contains("@RequirePermission(\"market:order:restore\")");
    }

    @Test
    void statusManagementDtoAndServiceSkeletonsExist() throws IOException {
        String req = source("dto/OrderStatusChangeReq.java");
        String result = source("dto/OrderStatusChangeResult.java");
        String service = source("service/OrderStatusManagementService.java");

        assertThat(req)
                .contains("class OrderStatusChangeReq")
                .contains("private String reason");
        assertThat(result)
                .contains("class OrderStatusChangeResult")
                .contains("private Integer rfqId")
                .contains("private String fromStatus")
                .contains("private String toStatus")
                .contains("private Integer updatedItemCount")
                .contains("private String message");
        assertThat(service)
                .contains("class OrderStatusManagementService")
                .contains("rollbackQuoted")
                .contains("rollbackPriced")
                .contains("discard")
                .contains("restore")
                .contains("阶段 2");
    }

    private String source(String relativePath) throws IOException {
        return Files.readString(MAIN_JAVA.resolve(relativePath), StandardCharsets.UTF_8);
    }
}
