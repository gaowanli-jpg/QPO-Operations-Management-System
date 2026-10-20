package com.machining.quote;

import com.machining.quote.service.SalesOrderNumberService;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SalesOrderNumberServiceTest {
    private final SalesOrderNumberService service = new SalesOrderNumberService();

    @Test
    void directImportUsesSalesOrderNumberAsStableDocumentNumber() {
        SalesOrderNumberService.SalesOrderNumbers numbers = service.forDirectImport(" SOQPO20260811001-001 ");

        assertThat(numbers.salesOrderNo()).isEqualTo("SOQPO20260811001-001");
        assertThat(numbers.documentNo()).isEqualTo(numbers.salesOrderNo());
    }

    @Test
    void directImportRejectsBlankSalesOrderNumber() {
        assertThatThrownBy(() -> service.forDirectImport("  "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("销售订单编号");
    }

    @Test
    void rfqReleaseUsesSalesOrderNumberAsStableDocumentNumber() {
        SalesOrderNumberService.SalesOrderNumbers numbers = service.forRfqRelease(
                LocalDate.of(2026, 8, 11),
                "RFQ:42:BATCH:abc");

        assertThat(numbers.salesOrderNo()).isEqualTo("SORFQ-20260811-600D09B4A774CE40");
        assertThat(numbers.documentNo()).isEqualTo(numbers.salesOrderNo());
        assertThat(service.forRfqRelease(LocalDate.of(2026, 8, 11), "RFQ:42:BATCH:abc"))
                .isEqualTo(numbers);
    }
}
