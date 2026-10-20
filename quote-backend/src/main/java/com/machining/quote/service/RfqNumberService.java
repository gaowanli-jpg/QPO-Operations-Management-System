package com.machining.quote.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Date;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

@Service
public class RfqNumberService {
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Shanghai");
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.BASIC_ISO_DATE;

    private final JdbcTemplate jdbcTemplate;

    public RfqNumberService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public String nextRfqNo() {
        LocalDate today = LocalDate.now(BUSINESS_ZONE);
        Integer sequence = jdbcTemplate.queryForObject("""
                INSERT INTO rfq_no_sequence (rfq_date, last_seq)
                VALUES (?, 1)
                ON CONFLICT (rfq_date)
                DO UPDATE SET
                    last_seq = rfq_no_sequence.last_seq + 1,
                    updated_at = CURRENT_TIMESTAMP
                RETURNING last_seq
                """, Integer.class, Date.valueOf(today));

        int safeSequence = sequence == null ? 1 : sequence;
        return "RFQ-" + today.format(DATE_FORMAT) + "-" + String.format("%04d", safeSequence);
    }
}
