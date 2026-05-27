package com.machining.quote;

import com.machining.quote.service.LeadTimeService;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class DeliveryServiceDateParsingTests {
    private final LeadTimeService leadTimeService = new LeadTimeService();

    @Test
    void dotSeparatedLeadTimeIsTreatedAsExactDeliveryDate() {
        LocalDate deliveryDate = resolveDeliveryDate("2026.03.20");

        assertThat(deliveryDate).isEqualTo(LocalDate.of(2026, 3, 20));
    }

    @Test
    void dashSeparatedLeadTimeIsTreatedAsExactDeliveryDate() {
        LocalDate deliveryDate = resolveDeliveryDate("2026-3-8");

        assertThat(deliveryDate).isEqualTo(LocalDate.of(2026, 3, 8));
    }

    @Test
    void durationLeadTimeStillAddsDaysToStartDate() {
        LocalDate deliveryDate = resolveDeliveryDate("7天");

        assertThat(deliveryDate).isEqualTo(LocalDate.of(2026, 5, 17));
    }

    @Test
    void excelSerialDateIsTreatedAsExactDeliveryDate() {
        LocalDate deliveryDate = resolveDeliveryDate("46089");

        assertThat(deliveryDate).isEqualTo(LocalDate.of(2026, 3, 8));
    }

    @Test
    void shiftExactDateNormalizesQuoteLeadTime() {
        assertThat(leadTimeService.shiftLeadTime("2026.03.20", 3)).isEqualTo("2026-03-23");
        assertThat(leadTimeService.shiftLeadTime("2026-3-20", -2)).isEqualTo("2026-03-18");
    }

    @Test
    void shiftDurationLeadTimeAdjustsDays() {
        assertThat(leadTimeService.shiftLeadTime("7天", 3)).isEqualTo("10天");
    }

    private LocalDate resolveDeliveryDate(String leadTime) {
        return leadTimeService.resolveDeliveryDate(LocalDate.of(2026, 5, 10), leadTime);
    }
}
