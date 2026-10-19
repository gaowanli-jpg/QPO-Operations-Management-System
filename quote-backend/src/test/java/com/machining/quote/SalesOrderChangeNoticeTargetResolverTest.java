package com.machining.quote;

import com.machining.quote.entity.MesProductionBatchItem;
import com.machining.quote.entity.MesIntegrationBatch;
import com.machining.quote.entity.OrderDeliveryTicketAllocation;
import com.machining.quote.mapper.MesIntegrationBatchMapper;
import com.machining.quote.mapper.MesProductionBatchItemMapper;
import com.machining.quote.mapper.OrderDeliveryTicketAllocationMapper;
import com.machining.quote.service.SalesOrderChangeNoticeTargetResolver;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SalesOrderChangeNoticeTargetResolverTest {
    @Test
    void prefersMesRecordIdOverLegacyPlanNumberOnAllocation() {
        SalesOrderChangeNoticeTargetResolver resolver = new SalesOrderChangeNoticeTargetResolver();
        OrderDeliveryTicketAllocationMapper allocationMapper = mock(OrderDeliveryTicketAllocationMapper.class);
        MesProductionBatchItemMapper batchItemMapper = mock(MesProductionBatchItemMapper.class);
        MesIntegrationBatchMapper batchMapper = mock(MesIntegrationBatchMapper.class);
        ReflectionTestUtils.setField(resolver, "allocationMapper", allocationMapper);
        ReflectionTestUtils.setField(resolver, "productionBatchItemMapper", batchItemMapper);
        ReflectionTestUtils.setField(resolver, "integrationBatchMapper", batchMapper);

        OrderDeliveryTicketAllocation allocation = new OrderDeliveryTicketAllocation();
        allocation.setId(21);
        allocation.setAllocationType("PRODUCTION_REQUIRED");
        allocation.setMesProductionStatus("SUCCESS");
        allocation.setMesProductionExternalId("PLAN-001");
        allocation.setMesProductionRequestId("QPO-DEM-001");

        MesProductionBatchItem batchItem = new MesProductionBatchItem();
        batchItem.setBatchId(31L);
        batchItem.setAllocationId(21);
        batchItem.setMesRecordId("1001");
        batchItem.setMesPlanNo("PLAN-001");
        when(batchItemMapper.selectLatestByAllocationIds(List.of(21))).thenReturn(List.of(batchItem));
        MesIntegrationBatch batch = new MesIntegrationBatch();
        batch.setId(31L);
        batch.setBizId("QPO-BATCH-001");
        when(batchMapper.selectBatchIds(List.of(31L))).thenReturn(List.of(batch));

        List<SalesOrderChangeNoticeTargetResolver.Target> targets = resolver.resolveAllocations(List.of(allocation));

        assertThat(targets).singleElement().satisfies(target -> {
            assertThat(target.targetExternalId()).isEqualTo("1001");
            assertThat(target.targetKey()).isEqualTo("1001");
            assertThat(target.sourceBizId()).isEqualTo("QPO-BATCH-001");
        });
    }
}
