package com.machining.quote;

import com.machining.quote.entity.ItemQuoteVersion;
import com.machining.quote.entity.SalesOrderItem;
import com.machining.quote.service.ConfirmedMaterialNoReuseService;
import com.machining.quote.service.MaterialNoReuseResult;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ConfirmedMaterialNoReuseServiceTest {
    private static final String CONFIRMED = "\u5df2\u786e\u8ba4";
    private static final String UNCONFIRMED = "\u672a\u786e\u8ba4";

    private final ConfirmedMaterialNoReuseService service = new ConfirmedMaterialNoReuseService();

    @Test
    void confirmedTupleIsNotCopiedToRfqTarget() {
        ItemQuoteVersion source = confirmedSource();
        ItemQuoteVersion target = new ItemQuoteVersion();
        target.setId(101);

        MaterialNoReuseResult result = service.copyToRfqItem(target, source);

        assertThat(result.status()).isEqualTo(MaterialNoReuseResult.Status.NOT_APPLICABLE);
        assertThat(target.getMaterialNo()).isNull();
        assertThat(target.getMaterialNoStatus()).isNull();
        assertThat(target.getMaterialNoSource()).isNull();
        assertThat(target.getMaterialNoSourceTicketId()).isNull();
        assertThat(target.getMaterialNoSyncedAt()).isNull();
        assertThat(target.getMaterialNoMessage()).isNull();
    }

    @Test
    void confirmedTupleIsNotCopiedToSalesOrderTarget() {
        SalesOrderItem target = new SalesOrderItem();
        target.setId(201L);

        MaterialNoReuseResult result = service.copyToSalesOrderItem(target, confirmedSource());

        assertThat(result.status()).isEqualTo(MaterialNoReuseResult.Status.NOT_APPLICABLE);
        assertThat(target.getMaterialNo()).isNull();
        assertThat(target.getMaterialNoStatus()).isNull();
        assertThat(target.getMaterialNoSource()).isNull();
        assertThat(target.getMaterialNoSourceTicketId()).isNull();
        assertThat(target.getMaterialNoSyncedAt()).isNull();
        assertThat(target.getMaterialNoMessage()).isNull();
    }

    @Test
    void sameMaterialNoIsLeftForMesInsteadOfMarkingIdempotent() {
        ItemQuoteVersion target = new ItemQuoteVersion();
        target.setMaterialNo("MES-001");
        target.setMaterialNoStatus(UNCONFIRMED);

        MaterialNoReuseResult result = service.copyToRfqItem(target, confirmedSource());

        assertThat(result.status()).isEqualTo(MaterialNoReuseResult.Status.NOT_APPLICABLE);
        assertThat(target.getMaterialNo()).isEqualTo("MES-001");
        assertThat(target.getMaterialNoStatus()).isEqualTo(UNCONFIRMED);
        assertThat(target.getMaterialNoSource()).isNull();
        assertThat(target.getMaterialNoSourceTicketId()).isNull();
        assertThat(target.getMaterialNoMessage()).isNull();
    }

    @Test
    void sameMaterialNoPreservesExistingMetadataAndOnlyFillsMissingValues() {
        ItemQuoteVersion source = confirmedSource();
        source.setMaterialNoSource(null);
        source.setMaterialNoSourceTicketId(null);
        source.setMaterialNoSyncedAt(null);
        source.setMaterialNoMessage(null);

        ItemQuoteVersion target = new ItemQuoteVersion();
        target.setMaterialNo("MES-001");
        target.setMaterialNoStatus(UNCONFIRMED);
        target.setMaterialNoSource("EXISTING_SOURCE");
        target.setMaterialNoSourceTicketId(7001L);
        target.setMaterialNoMessage("existing message");

        MaterialNoReuseResult result = service.copyToRfqItem(target, source);

        assertThat(result.status()).isEqualTo(MaterialNoReuseResult.Status.NOT_APPLICABLE);
        assertThat(target.getMaterialNo()).isEqualTo("MES-001");
        assertThat(target.getMaterialNoStatus()).isEqualTo(UNCONFIRMED);
        assertThat(target.getMaterialNoSource()).isEqualTo("EXISTING_SOURCE");
        assertThat(target.getMaterialNoSourceTicketId()).isEqualTo(7001L);
        assertThat(target.getMaterialNoSyncedAt()).isNull();
        assertThat(target.getMaterialNoMessage()).isEqualTo("existing message");
    }

    @Test
    void differentMaterialNoIsNotConsideredAReuseConflict() {
        ItemQuoteVersion target = new ItemQuoteVersion();
        target.setMaterialNo("MES-OLD");
        target.setMaterialNoStatus(UNCONFIRMED);

        MaterialNoReuseResult result = service.copyToRfqItem(target, confirmedSource());

        assertThat(result.status()).isEqualTo(MaterialNoReuseResult.Status.NOT_APPLICABLE);
        assertThat(target.getMaterialNo()).isEqualTo("MES-OLD");
        assertThat(target.getMaterialNoStatus()).isEqualTo(UNCONFIRMED);
    }

    @Test
    void unconfirmedSourceDoesNotChangeTargetOrBlockOtherSnapshotReuse() {
        ItemQuoteVersion source = confirmedSource();
        source.setMaterialNo(null);
        source.setMaterialNoStatus(UNCONFIRMED);
        ItemQuoteVersion target = new ItemQuoteVersion();
        target.setMaterialNo(null);
        target.setMaterialNoStatus(UNCONFIRMED);
        target.setEngineeringMaterialName("7075");

        MaterialNoReuseResult result = service.copyToRfqItem(target, source);

        assertThat(result.status()).isEqualTo(MaterialNoReuseResult.Status.NOT_APPLICABLE);
        assertThat(target.getMaterialNo()).isNull();
        assertThat(target.getMaterialNoStatus()).isEqualTo(UNCONFIRMED);
        assertThat(target.getEngineeringMaterialName()).isEqualTo("7075");
    }

    private ItemQuoteVersion confirmedSource() {
        ItemQuoteVersion source = new ItemQuoteVersion();
        source.setId(1);
        source.setMaterialNo("MES-001");
        source.setMaterialNoStatus(CONFIRMED);
        source.setMaterialNoSource("MES_TICKET");
        source.setMaterialNoSourceTicketId(9001L);
        source.setMaterialNoSyncedAt(LocalDateTime.of(2026, 8, 7, 12, 0));
        source.setMaterialNoMessage("confirmed by MES");
        return source;
    }
}
