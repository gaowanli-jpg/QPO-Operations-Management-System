package com.machining.quote;

import com.machining.quote.service.RfqNumberService;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Date;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RfqNumberServiceTests {

    @Test
    void formatsRfqNumberWithDateAndDailySequence() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class), any(Date.class))).thenReturn(7);

        String rfqNo = new RfqNumberService(jdbcTemplate).nextRfqNo();

        assertThat(rfqNo).matches("RFQ-\\d{8}-0007");
    }
}
