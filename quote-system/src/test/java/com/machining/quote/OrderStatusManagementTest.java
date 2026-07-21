package com.machining.quote;

import com.machining.quote.domain.QuoteStatusWorkflow;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OrderStatusManagementTest {
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
}
