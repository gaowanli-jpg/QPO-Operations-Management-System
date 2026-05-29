package com.machining.quote;

import com.machining.quote.service.LeadTimeService;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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

    @Test
    void manualDateNormalizesToIsoDate() {
        assertThat(leadTimeService.normalizeRequiredDate("2026.03.20")).isEqualTo("2026-03-20");
        assertThat(leadTimeService.normalizeRequiredDate("2026-3-8")).isEqualTo("2026-03-08");
    }

    @Test
    void manualDateRejectsDurationText() {
        assertThatThrownBy(() -> leadTimeService.normalizeRequiredDate("7天"))
                .hasMessageContaining("日期格式不正确");
    }

    private LocalDate resolveDeliveryDate(String leadTime) {
        return leadTimeService.resolveDeliveryDate(LocalDate.of(2026, 5, 10), leadTime);
    }
}
