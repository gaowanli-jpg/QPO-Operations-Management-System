package com.machining.quote;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.machining.quote.entity.OrderDeliveryTicketAllocation;
import com.machining.quote.entity.SalesOrderChangeDispatchSetting;
import com.machining.quote.entity.SalesOrderChangeNotice;
import com.machining.quote.entity.SalesOrderHeader;
import com.machining.quote.entity.SalesOrderItem;
import com.machining.quote.mapper.OrderDeliveryTicketAllocationMapper;
import com.machining.quote.mapper.SalesOrderChangeDispatchSettingMapper;
import com.machining.quote.mapper.SalesOrderChangeNoticeMapper;
import com.machining.quote.service.DataCenterIntegrationClient;
import com.machining.quote.service.SalesOrderChangeNoticePayloadFactory;
import com.machining.quote.service.SalesOrderChangeNoticeService;
import com.machining.quote.service.SalesOrderChangeNoticeTargetResolver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SalesOrderChangeNoticeServiceTest {
    private SalesOrderChangeNoticeMapper noticeMapper;
    private SalesOrderChangeDispatchSettingMapper settingMapper;
    private SalesOrderChangeNoticeTargetResolver targetResolver;
    private SalesOrderChangeNoticeService service;

    @BeforeEach
    void setUp() {
        noticeMapper = mock(SalesOrderChangeNoticeMapper.class);
        settingMapper = mock(SalesOrderChangeDispatchSettingMapper.class);
        targetResolver = mock(SalesOrderChangeNoticeTargetResolver.class);
        service = new SalesOrderChangeNoticeService();
        ReflectionTestUtils.setField(service, "noticeMapper", noticeMapper);
        ReflectionTestUtils.setField(service, "settingMapper", settingMapper);
        ReflectionTestUtils.setField(service, "targetResolver", targetResolver);
        ReflectionTestUtils.setField(service, "payloadFactory", payloadFactory());
        ReflectionTestUtils.setField(service, "allocationMapper", mock(OrderDeliveryTicketAllocationMapper.class));
        ReflectionTestUtils.setField(service, "objectMapper", new ObjectMapper());
    }

    @Test
    void automaticModeAllowsManualSendingOfOldBacklogAndRetryableNotices() {
        SalesOrderChangeDispatchSetting setting = setting("AUTO", 3L);
        when(settingMapper.selectSingletonForUpdate()).thenReturn(setting);
        SalesOrderChangeNotice backlog = notice("PENDING-BACKLOG", "PENDING");
        backlog.setAutoDispatchEpoch(2L);
        SalesOrderChangeNotice currentEpoch = notice("PENDING-AUTO", "PENDING");
        currentEpoch.setAutoDispatchEpoch(3L);
        when(noticeMapper.selectList(any(Wrapper.class))).thenReturn(List.of(
                backlog,
                currentEpoch,
                notice("FAILED-1", "FAILED"),
                notice("UNKNOWN-1", "RESULT_UNKNOWN")));
        when(noticeMapper.claimManual(anyList())).thenReturn(3);

        List<String> claimed = service.claimManual(List.of("PENDING-BACKLOG", "PENDING-AUTO", "FAILED-1", "UNKNOWN-1"));

        assertThat(claimed).containsExactly("PENDING-BACKLOG", "FAILED-1", "UNKNOWN-1");
        verify(noticeMapper).claimManual(claimed);
    }

    @Test
    void automaticModeDoesNotAllowManuallyClaimingCurrentEpochPendingNotices() {
        when(settingMapper.selectSingletonForUpdate()).thenReturn(setting("AUTO", 3L));
        SalesOrderChangeNotice currentEpoch = notice("PENDING-1", "PENDING");
        currentEpoch.setAutoDispatchEpoch(3L);
        when(noticeMapper.selectList(any(Wrapper.class))).thenReturn(List.of(currentEpoch));

        assertThatThrownBy(() -> service.claimManual(List.of("PENDING-1")))
                .isInstanceOf(IllegalArgumentException.class);

        verify(noticeMapper, never()).claimManual(anyList());
    }

    @Test
    void priceOnlyModificationDoesNotResolveTargetsOrCreateNotices() {
        SalesOrderHeader order = new SalesOrderHeader();
        order.setId(7L);
        SalesOrderItem item = new SalesOrderItem();
        item.setId(11L);

        int captured = service.captureModify(order, List.of(item), Map.of(item.getId(), Set.of("untaxedUnitPrice")));

        assertThat(captured).isZero();
        verify(targetResolver, never()).resolve(anyList());
        verify(noticeMapper, never()).insert(any(SalesOrderChangeNotice.class));
    }

    @Test
    void modifyingAnExistingPendingNoticeMovesItIntoTheCurrentAutomaticEpoch() throws Exception {
        SalesOrderHeader order = new SalesOrderHeader();
        order.setId(7L);
        order.setSalesOrderNo("SO-7");
        SalesOrderItem item = new SalesOrderItem();
        item.setId(11L);
        item.setSalesOrderId(7L);
        item.setLineNo(4);
        item.setPartNo("PART-11");
        item.setVersion("A");
        OrderDeliveryTicketAllocation allocation = new OrderDeliveryTicketAllocation();
        allocation.setId(101);
        SalesOrderChangeNoticeTargetResolver.Target target = new SalesOrderChangeNoticeTargetResolver.Target(
                allocation, "PRODUCTION", "MES-PLAN-1", "MES-PLAN-1", "BATCH-1");
        SalesOrderChangeNotice existing = notice("NOTICE-1", "PENDING");
        existing.setChangedFieldsJson("[\"remark\"]");
        when(settingMapper.selectSingletonForShare()).thenReturn(setting("AUTO", 8L));
        when(targetResolver.resolve(List.of(item))).thenReturn(Map.of(item.getId(), List.of(target)));
        when(noticeMapper.selectOne(any(Wrapper.class))).thenReturn(existing);
        when(noticeMapper.updateById(existing)).thenReturn(1);

        int captured = service.captureModify(order, List.of(item), Map.of(item.getId(), Set.of("qty")));

        assertThat(captured).isEqualTo(1);
        assertThat(existing.getAutoDispatchEpoch()).isEqualTo(8L);
        assertThat(existing.getChangedFieldsJson()).contains("remark", "qty");
        Map<String, Object> snapshot = new ObjectMapper().readValue(existing.getPayloadSnapshotJson(), Map.class);
        assertThat(snapshot.get("changedFields")).asList().contains("remark", "qty");
        ArgumentCaptor<Wrapper<SalesOrderChangeNotice>> query = ArgumentCaptor.forClass(Wrapper.class);
        verify(noticeMapper).selectOne(query.capture());
        assertThat(query.getValue().getCustomSqlSegment()).contains("LIMIT 1 FOR UPDATE");
    }

    @Test
    void successfulTopLevelResponseWithoutItemReceiptsIsResultUnknown() {
        SalesOrderChangeNotice row = sendingNotice("NOTICE-UNKNOWN");
        when(noticeMapper.selectList(any(Wrapper.class))).thenReturn(List.of(row));
        DataCenterIntegrationClient client = mock(DataCenterIntegrationClient.class);
        ReflectionTestUtils.setField(service, "dataCenterIntegrationClient", client);
        when(client.call(any(String.class), any(), any())).thenReturn(Map.of("code", "0", "data", Map.of()));

        Map<String, Object> result = service.sendClaimed(List.of(row.getNoticeId()), 9L);

        assertThat(result).containsEntry("unknownCount", 1).containsEntry("failedCount", 0);
        assertThat(row.getStatus()).isEqualTo("RESULT_UNKNOWN");
    }

    @Test
    void explicitTopLevelBusinessRejectionIsFailed() {
        SalesOrderChangeNotice row = sendingNotice("NOTICE-REJECTED");
        when(noticeMapper.selectList(any(Wrapper.class))).thenReturn(List.of(row));
        DataCenterIntegrationClient client = mock(DataCenterIntegrationClient.class);
        ReflectionTestUtils.setField(service, "dataCenterIntegrationClient", client);
        when(client.call(any(String.class), any(), any())).thenReturn(Map.of("code", "BUSINESS_REJECTED", "msg", "rejected"));

        Map<String, Object> result = service.sendClaimed(List.of(row.getNoticeId()), 10L);

        assertThat(result).containsEntry("failedCount", 1).containsEntry("unknownCount", 0);
        assertThat(row.getStatus()).isEqualTo("FAILED");
    }

    private SalesOrderChangeNoticePayloadFactory payloadFactory() {
        SalesOrderChangeNoticePayloadFactory factory = new SalesOrderChangeNoticePayloadFactory();
        ReflectionTestUtils.setField(factory, "objectMapper", new ObjectMapper());
        return factory;
    }

    private SalesOrderChangeDispatchSetting setting(String mode, Long epoch) {
        SalesOrderChangeDispatchSetting setting = new SalesOrderChangeDispatchSetting();
        setting.setId(1L);
        setting.setDispatchMode(mode);
        setting.setAutoDispatchEpoch(epoch);
        return setting;
    }

    private SalesOrderChangeNotice notice(String noticeId, String status) {
        SalesOrderChangeNotice notice = new SalesOrderChangeNotice();
        notice.setId(1L);
        notice.setNoticeId(noticeId);
        notice.setStatus(status);
        notice.setChangedFieldsJson("[]");
        notice.setPayloadSnapshotJson("{}");
        return notice;
    }

    private SalesOrderChangeNotice sendingNotice(String noticeId) {
        SalesOrderChangeNotice notice = notice(noticeId, "SENDING");
        notice.setPayloadSnapshotJson("{}");
        notice.setChangedFieldsJson("[]");
        return notice;
    }
}
