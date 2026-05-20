package com.machining.quote;

import com.machining.quote.service.DeliveryService;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class DeliveryServiceDateParsingTests {
    private final DeliveryService deliveryService = new DeliveryService();

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

    private LocalDate resolveDeliveryDate(String leadTime) {
        return ReflectionTestUtils.invokeMethod(
                deliveryService,
                "resolveDeliveryDate",
                LocalDate.of(2026, 5, 10),
                leadTime
        );
    }
}
