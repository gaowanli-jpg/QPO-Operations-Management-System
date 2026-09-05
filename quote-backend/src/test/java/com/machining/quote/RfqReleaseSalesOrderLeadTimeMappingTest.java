package com.machining.quote;

import com.machining.quote.entity.ItemQuoteVersion;
import com.machining.quote.entity.SalesOrderItem;
import com.machining.quote.mapper.SalesOrderItemMapper;
import com.machining.quote.service.LeadTimeService;
import com.machining.quote.service.RfqReleaseSalesOrderService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RfqReleaseSalesOrderLeadTimeMappingTest {
    @Test
    void derivedSalesOrderItemStoresQuoteAndInternalTargetLeadTimesAsCustomerAndInternalDeliveryDates() throws Exception {
        RfqReleaseSalesOrderService service = new RfqReleaseSalesOrderService();
        SalesOrderItemMapper mapper = mock(SalesOrderItemMapper.class);
        ReflectionTestUtils.setField(service, "salesItemMapper", mapper);
        ReflectionTestUtils.setField(service, "leadTimeService", new LeadTimeService());
        when(mapper.insert(any(SalesOrderItem.class))).thenReturn(1);

        ItemQuoteVersion source = new ItemQuoteVersion();
        source.setId(501);
        source.setRfqId(301);
        source.setPartNo("RFQ-P-001");
        source.setVersion("A");
        source.setQty(2);
        source.setQuoteLeadTime("2026.09.30");
        source.setInternalTargetLeadTime("2026-09-25");

        SalesOrderItem inserted = insertSalesItem(service, source, LocalDate.of(2026, 9, 4));

        ArgumentCaptor<SalesOrderItem> itemCaptor = ArgumentCaptor.forClass(SalesOrderItem.class);
        org.mockito.Mockito.verify(mapper).insert(itemCaptor.capture());
        assertThat(itemCaptor.getValue()).isSameAs(inserted);
        assertThat(inserted.getCustomerDeliveryDate()).isEqualTo(LocalDate.of(2026, 9, 30));
        assertThat(inserted.getInternalDeliveryDate()).isEqualTo(LocalDate.of(2026, 9, 25));
    }

    private SalesOrderItem insertSalesItem(
            RfqReleaseSalesOrderService service,
            ItemQuoteVersion source,
            LocalDate salesOrderDate) throws Exception {
        String serviceName = RfqReleaseSalesOrderService.class.getName();
        Class<?> fieldsClass = Class.forName(serviceName + "$EffectiveOrderFields");
        Constructor<?> fieldsCtor = fieldsClass.getDeclaredConstructor(
                LocalDate.class, String.class, String.class, String.class, String.class, String.class);
        fieldsCtor.setAccessible(true);
        Object fields = fieldsCtor.newInstance(
                salesOrderDate,
                "PO-001",
                "CP-001",
                "Customer title",
                "Formal order",
                "IP-001");

        Class<?> releaseLineClass = Class.forName(serviceName + "$ReleaseLine");
        Constructor<?> lineCtor = releaseLineClass.getDeclaredConstructor(ItemQuoteVersion.class, int.class, fieldsClass);
        lineCtor.setAccessible(true);
        Object line = lineCtor.newInstance(source, 2, fields);

        Method method = RfqReleaseSalesOrderService.class.getDeclaredMethod(
                "insertSalesItem",
                Long.class,
                int.class,
                releaseLineClass,
                Integer.class,
                Set.class,
                LocalDateTime.class);
        method.setAccessible(true);
        return (SalesOrderItem) method.invoke(
                service,
                901L,
                1,
                line,
                7001,
                Set.of(),
                LocalDateTime.of(2026, 9, 4, 10, 0));
    }
}
