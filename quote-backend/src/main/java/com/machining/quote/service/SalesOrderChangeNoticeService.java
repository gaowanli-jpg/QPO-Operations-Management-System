package com.machining.quote.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.machining.quote.config.AuthContext;
import com.machining.quote.entity.OrderDeliveryTicketAllocation;
import com.machining.quote.entity.SalesOrderChangeDispatchSetting;
import com.machining.quote.entity.SalesOrderChangeNotice;
import com.machining.quote.entity.SalesOrderHeader;
import com.machining.quote.entity.SalesOrderItem;
import com.machining.quote.dto.PageResult;
import com.machining.quote.dto.SalesOrderChangeDispatchModeReq;
import com.machining.quote.dto.SalesOrderChangeDispatchSettingDTO;
import com.machining.quote.dto.SalesOrderChangeNoticeDTO;
import com.machining.quote.dto.SalesOrderChangeNoticeQueryReq;
import com.machining.quote.dto.mes.MesSalesOrderChangeNoticeResp;
import com.machining.quote.mapper.OrderDeliveryTicketAllocationMapper;
import com.machining.quote.mapper.SalesOrderHeaderMapper;
import com.machining.quote.mapper.SalesOrderItemMapper;
import com.machining.quote.mapper.SalesOrderChangeDispatchSettingMapper;
import com.machining.quote.mapper.SalesOrderChangeNoticeMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

@Service
public class SalesOrderChangeNoticeService {
    public static final String MODIFY = "MODIFY";
    public static final String DISCARD = "DISCARD";
    public static final String RESTORE = "RESTORE";
    public static final String PENDING = "PENDING";
    public static final String SENDING = "SENDING";
    public static final String SENT = "SENT";
    public static final String FAILED = "FAILED";
    public static final String RESULT_UNKNOWN = "RESULT_UNKNOWN";
    private static final String MANUAL = "MANUAL";
    private static final String AUTO = "AUTO";
    private static final String ACTION_CREATE = "SALES_ORDER_CHANGE_NOTICE_CREATE";
    private static final String ACTION_MERGE = "SALES_ORDER_CHANGE_NOTICE_MERGE";
    private static final String ACTION_HIDE = "SALES_ORDER_CHANGE_NOTICE_HIDE";
    private static final String ACTION_RESTORE = "SALES_ORDER_CHANGE_NOTICE_RESTORE";

    @Autowired
    private SalesOrderChangeNoticeMapper noticeMapper;
    @Autowired
    private SalesOrderChangeDispatchSettingMapper settingMapper;
    @Autowired
    private SalesOrderChangeNoticeTargetResolver targetResolver;
    @Autowired
    private SalesOrderChangeNoticePayloadFactory payloadFactory;
    @Autowired
    private OrderDeliveryTicketAllocationMapper allocationMapper;
    @Autowired
    private SalesOrderHeaderMapper headerMapper;
    @Autowired
    private SalesOrderItemMapper itemMapper;
    @Autowired
    private DataCenterIntegrationClient dataCenterIntegrationClient;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired(required = false)
    private DataScopeService dataScopeService;
    @Autowired(required = false)
    private AuditService auditService;

    @Transactional
    public int captureModify(SalesOrderHeader order, List<SalesOrderItem> items,
                             Map<Long, Set<String>> changedFieldsByItem) {
        if (order == null || items == null || changedFieldsByItem == null) return 0;
        Map<Long, Set<String>> nonPriceFieldsByItem = new LinkedHashMap<>();
        List<SalesOrderItem> changedItems = new ArrayList<>();
        for (SalesOrderItem item : items) {
            if (item == null || item.getId() == null) continue;
            Set<String> changed = payloadFactory.nonPriceFields(changedFieldsByItem.get(item.getId()));
            if (changed.isEmpty()) continue;
            nonPriceFieldsByItem.put(item.getId(), changed);
            changedItems.add(item);
        }
        if (changedItems.isEmpty()) return 0;
        int count = 0;
        String eventKey = UUID.randomUUID().toString();
        Map<Long, List<SalesOrderChangeNoticeTargetResolver.Target>> targetsByItem =
                targetResolver.resolve(changedItems);
        for (SalesOrderItem item : changedItems) {
            Set<String> changed = nonPriceFieldsByItem.get(item.getId());
            for (SalesOrderChangeNoticeTargetResolver.Target target : targetsByItem
                    .getOrDefault(item.getId(), List.of())) {
                upsert(order, item, target, MODIFY, null, changed, eventKey);
                count++;
            }
        }
        return count;
    }

    @Transactional
    public int captureLinked(SalesOrderHeader order, SalesOrderItem item,
                             List<OrderDeliveryTicketAllocation> allocations,
                             String changeType, String reason) {
        if (order == null || item == null || allocations == null) return 0;
        if (!DISCARD.equals(changeType) && !RESTORE.equals(changeType)) {
            throw new IllegalArgumentException("销售变更关联事件类型不正确");
        }
        String normalizedReason = normalizeReason(reason);
        if (RESTORE.equals(changeType)) {
            Map<Long, List<OrderDeliveryTicketAllocation>> allocationsByItem = Map.of(item.getId(), allocations);
            List<LinkedNoticeTarget> targets = latestLinkedDiscardTargets(order, List.of(item), allocationsByItem);
            String eventKey = UUID.randomUUID().toString();
            for (LinkedNoticeTarget linkedTarget : targets) {
                upsert(order, item, linkedTarget.target(), RESTORE, normalizedReason, Set.of(), eventKey);
            }
            return targets.size();
        }
        List<OrderDeliveryTicketAllocation> candidates = allocations.stream()
                .filter(Objects::nonNull)
                .filter(row -> row.getId() != null)
                .filter(row -> !Boolean.TRUE.equals(row.getDiscarded()))
                .filter(row -> "PRODUCTION_REQUIRED".equalsIgnoreCase(text(row.getAllocationType()))
                        || Set.of("IN_STOCK_LOCK", "IN_PRODUCTION_LOCK")
                        .contains(text(row.getAllocationType()).toUpperCase()))
                .toList();
        if (candidates.isEmpty()) return 0;
        List<SalesOrderChangeNoticeTargetResolver.Target> targets = resolveAllocations(candidates);
        String eventKey = UUID.randomUUID().toString();
        for (SalesOrderChangeNoticeTargetResolver.Target target : targets) {
            upsert(order, item, target, changeType, normalizedReason, Set.of(), eventKey);
        }
        return targets.size();
    }

    @Transactional
    public int captureLinked(SalesOrderHeader order, List<SalesOrderItem> items,
                             Map<Long, List<OrderDeliveryTicketAllocation>> allocationsByItem,
                             String changeType, String reason) {
        if (order == null || items == null || items.isEmpty()) return 0;
        if (!DISCARD.equals(changeType) && !RESTORE.equals(changeType)) {
            throw new IllegalArgumentException("销售变更关联事件类型不正确");
        }
        String normalizedReason = normalizeReason(reason);
        if (RESTORE.equals(changeType)) {
            List<LinkedNoticeTarget> targets = latestLinkedDiscardTargets(order, items, allocationsByItem);
            Map<Long, SalesOrderItem> itemsById = new LinkedHashMap<>();
            for (SalesOrderItem item : items) {
                if (item != null && item.getId() != null) itemsById.put(item.getId(), item);
            }
            String eventKey = UUID.randomUUID().toString();
            for (LinkedNoticeTarget linkedTarget : targets) {
                SalesOrderItem item = itemsById.get(linkedTarget.salesOrderItemId());
                if (item != null) {
                    upsert(order, item, linkedTarget.target(), RESTORE, normalizedReason, Set.of(), eventKey);
                }
            }
            return targets.size();
        }
        Map<Long, SalesOrderItem> itemsById = new LinkedHashMap<>();
        Map<Integer, Long> itemIdByAllocationId = new LinkedHashMap<>();
        List<OrderDeliveryTicketAllocation> candidates = new ArrayList<>();
        for (SalesOrderItem item : items) {
            if (item == null || item.getId() == null) continue;
            itemsById.put(item.getId(), item);
            List<OrderDeliveryTicketAllocation> allocations = allocationsByItem == null
                    ? List.of() : allocationsByItem.getOrDefault(item.getId(), List.of());
            for (OrderDeliveryTicketAllocation allocation : allocations) {
                if (allocation == null || allocation.getId() == null) continue;
                boolean eligibleEvent = !Boolean.TRUE.equals(allocation.getDiscarded());
                boolean supportedType = "PRODUCTION_REQUIRED".equalsIgnoreCase(text(allocation.getAllocationType()))
                        || Set.of("IN_STOCK_LOCK", "IN_PRODUCTION_LOCK")
                        .contains(text(allocation.getAllocationType()).toUpperCase());
                if (eligibleEvent && supportedType) {
                    candidates.add(allocation);
                    itemIdByAllocationId.put(allocation.getId(), item.getId());
                }
            }
        }
        if (candidates.isEmpty()) return 0;
        List<SalesOrderChangeNoticeTargetResolver.Target> targets = resolveAllocations(candidates);
        String eventKey = UUID.randomUUID().toString();
        for (SalesOrderChangeNoticeTargetResolver.Target target : targets) {
            SalesOrderItem item = itemsById.get(itemIdByAllocationId.get(target.allocation().getId()));
            if (item != null) upsert(order, item, target, changeType, normalizedReason, Set.of(), eventKey);
        }
        return targets.size();
    }

    private List<SalesOrderChangeNoticeTargetResolver.Target> resolveAllocations(List<OrderDeliveryTicketAllocation> allocations) {
        return targetResolver.resolveAllocations(allocations);
    }

    private List<LinkedNoticeTarget> latestLinkedDiscardTargets(
            SalesOrderHeader order,
            List<SalesOrderItem> items,
            Map<Long, List<OrderDeliveryTicketAllocation>> allocationsByItem) {
        Map<Long, SalesOrderItem> itemsById = new LinkedHashMap<>();
        Map<Long, OrderDeliveryTicketAllocation> allocationsById = new LinkedHashMap<>();
        Map<Long, Set<Long>> allocationIdsByItem = new LinkedHashMap<>();
        for (SalesOrderItem item : items) {
            if (item == null || item.getId() == null || !Objects.equals(order.getId(), item.getSalesOrderId())) continue;
            itemsById.put(item.getId(), item);
            List<OrderDeliveryTicketAllocation> allocations = allocationsByItem == null
                    ? List.of() : allocationsByItem.getOrDefault(item.getId(), List.of());
            for (OrderDeliveryTicketAllocation allocation : allocations) {
                if (allocation != null && allocation.getId() != null && isSupportedAllocation(allocation)) {
                    allocationsById.put(allocation.getId().longValue(), allocation);
                    allocationIdsByItem.computeIfAbsent(item.getId(), ignored -> new LinkedHashSet<>())
                            .add(allocation.getId().longValue());
                }
            }
        }
        if (itemsById.isEmpty() || allocationsById.isEmpty()) return List.of();

        List<SalesOrderChangeNotice> events = noticeMapper.selectLinkedLifecycleEvents(
                order.getId(), List.copyOf(itemsById.keySet()), List.copyOf(allocationsById.keySet()));
        Map<LinkedNoticeKey, SalesOrderChangeNotice> latestByTarget = new LinkedHashMap<>();
        for (SalesOrderChangeNotice event : events == null ? List.<SalesOrderChangeNotice>of() : events) {
            if (event == null || event.getSalesOrderItemId() == null || event.getAllocationId() == null
                    || !itemsById.containsKey(event.getSalesOrderItemId())
                    || !allocationIdsByItem.getOrDefault(event.getSalesOrderItemId(), Set.of())
                    .contains(event.getAllocationId())
                    || (!DISCARD.equals(event.getChangeType()) && !RESTORE.equals(event.getChangeType()))
                    || !hasText(event.getTargetType()) || !hasText(event.getTargetKey())) {
                continue;
            }
            LinkedNoticeKey key = new LinkedNoticeKey(event.getSalesOrderItemId(), event.getAllocationId(),
                    event.getTargetType(), event.getTargetKey());
            latestByTarget.put(key, event);
        }

        List<LinkedNoticeTarget> targets = new ArrayList<>();
        for (Map.Entry<LinkedNoticeKey, SalesOrderChangeNotice> entry : latestByTarget.entrySet()) {
            SalesOrderChangeNotice latest = entry.getValue();
            if (!DISCARD.equals(latest.getChangeType())) continue;
            LinkedNoticeKey key = entry.getKey();
            OrderDeliveryTicketAllocation allocation = allocationsById.get(key.allocationId());
            SalesOrderChangeNoticeTargetResolver.Target target = new SalesOrderChangeNoticeTargetResolver.Target(
                    allocation,
                    latest.getTargetType(),
                    latest.getTargetKey(),
                    latest.getTargetExternalId(),
                    latest.getSourceBizId());
            targets.add(new LinkedNoticeTarget(key.salesOrderItemId(), target));
        }
        return targets;
    }

    private boolean isSupportedAllocation(OrderDeliveryTicketAllocation allocation) {
        return "PRODUCTION_REQUIRED".equalsIgnoreCase(text(allocation.getAllocationType()))
                || Set.of("IN_STOCK_LOCK", "IN_PRODUCTION_LOCK")
                .contains(text(allocation.getAllocationType()).toUpperCase());
    }

    private record LinkedNoticeKey(Long salesOrderItemId, Long allocationId, String targetType, String targetKey) { }

    private record LinkedNoticeTarget(Long salesOrderItemId, SalesOrderChangeNoticeTargetResolver.Target target) { }

    private String text(String value) {
        return value == null ? "" : value.trim();
    }

    private void upsert(SalesOrderHeader order, SalesOrderItem item,
                        SalesOrderChangeNoticeTargetResolver.Target target,
                        String changeType, String reason, Set<String> changedFields, String eventKey) {
        LocalDateTime now = LocalDateTime.now();
        String fieldsJson = json(new ArrayList<>(changedFields));
        String snapshot = payloadFactory.snapshotJson(order, item, target, changeType, reason, changedFields);
        SalesOrderChangeDispatchSetting setting = settingMapper.selectSingletonForShare();
        long autoDispatchEpoch = setting != null && AUTO.equals(setting.getDispatchMode())
                ? safeEpoch(setting.getAutoDispatchEpoch()) : 0L;
        if (MODIFY.equals(changeType)) {
            QueryWrapper<SalesOrderChangeNotice> query = new QueryWrapper<>();
            query.eq("sales_order_item_id", item.getId())
                    .eq("target_type", target.targetType())
                    .eq("target_key", target.targetKey())
                    .eq("change_type", MODIFY)
                    .eq("status", PENDING)
                    .last("LIMIT 1 FOR UPDATE");
            SalesOrderChangeNotice existing = noticeMapper.selectOne(query);
            if (existing != null) {
                Set<String> mergedFields = new LinkedHashSet<>(readStringList(existing.getChangedFieldsJson()));
                mergedFields.addAll(changedFields);
                existing.setChangedFieldsJson(json(new ArrayList<>(mergedFields)));
                existing.setPayloadSnapshotJson(payloadFactory.snapshotJson(order, item, target, MODIFY, null, mergedFields));
                existing.setSourceEventKey(eventKey);
                existing.setAutoDispatchEpoch(autoDispatchEpoch);
                if (Boolean.TRUE.equals(existing.getIsHidden())) {
                    existing.setIsHidden(false);
                    existing.setRestoredAt(now);
                    existing.setRestoredBy(currentUserId());
                }
                existing.setUpdatedAt(now);
                noticeMapper.updateById(existing);
                audit(ACTION_MERGE, existing, Map.of("mergedFields", mergedFields));
                return;
            }
        }
        SalesOrderChangeNotice notice = new SalesOrderChangeNotice();
        notice.setNoticeId("QPO-SOCN-" + UUID.randomUUID().toString().replace("-", "").substring(0, 20).toUpperCase());
        notice.setSalesOrderId(order.getId());
        notice.setSalesOrderItemId(item.getId());
        notice.setAllocationId(target.allocation().getId() == null ? null : target.allocation().getId().longValue());
        notice.setTargetType(target.targetType());
        notice.setTargetKey(target.targetKey());
        notice.setTargetExternalId(target.targetExternalId());
        notice.setSourceBizId(target.sourceBizId());
        notice.setChangeType(changeType);
        notice.setReason(reason);
        notice.setChangedFieldsJson(fieldsJson);
        notice.setPayloadSnapshotJson(snapshot);
        notice.setStatus(PENDING);
        notice.setAutoDispatchEpoch(autoDispatchEpoch);
        AuthContext.SessionUser user = AuthContext.get();
        notice.setCreatedBy(user == null || user.id() == null ? null : user.id().longValue());
        notice.setCreatedByName(user == null ? null : user.displayName());
        notice.setSourceEventKey(eventKey);
        notice.setCreatedAt(now);
        notice.setUpdatedAt(now);
        noticeMapper.insert(notice);
        audit(ACTION_CREATE, notice, Map.of("changedFields", changedFields));
    }

    public String normalizeReason(String reason) {
        if (reason == null || reason.trim().isEmpty()) throw new IllegalArgumentException("关联变更理由不能为空");
        String normalized = reason.trim();
        if (normalized.length() > 500) throw new IllegalArgumentException("关联变更理由不能超过 500 个字符");
        return normalized;
    }

    @Transactional
    public int hide(List<String> requestedIds, String reason) {
        String normalizedReason = normalizeVisibilityReason(reason, true);
        List<SalesOrderChangeNotice> rows = lockVisibleRows(requestedIds);
        if (rows.stream().anyMatch(row -> Boolean.TRUE.equals(row.getIsHidden()) || !isHideableStatus(row.getStatus()))) {
            throw new IllegalArgumentException("只能隐藏未发送、失败或结果未知的可见通知");
        }
        LocalDateTime now = LocalDateTime.now();
        AuthContext.SessionUser user = AuthContext.get();
        Long operatorId = currentUserId();
        String operatorName = user == null ? null : user.displayName();
        for (SalesOrderChangeNotice row : rows) {
            row.setIsHidden(true);
            row.setHiddenReason(normalizedReason);
            row.setHiddenAt(now);
            row.setHiddenBy(operatorId);
            row.setHiddenByName(operatorName);
            row.setUpdatedAt(now);
            if (noticeMapper.updateById(row) != 1) throw new IllegalStateException("销售变更通知状态已变化，请刷新后重试");
        }
        auditVisibilityBatch(ACTION_HIDE, rows, normalizedReason);
        return rows.size();
    }

    @Transactional
    public int restore(List<String> requestedIds, String reason) {
        String normalizedReason = normalizeVisibilityReason(reason, false);
        List<SalesOrderChangeNotice> rows = lockVisibleRows(requestedIds);
        if (rows.stream().anyMatch(row -> !Boolean.TRUE.equals(row.getIsHidden()) || !isHideableStatus(row.getStatus()))) {
            throw new IllegalArgumentException("只能恢复已隐藏且未发送、失败或结果未知的通知");
        }
        LocalDateTime now = LocalDateTime.now();
        Long operatorId = currentUserId();
        for (SalesOrderChangeNotice row : rows) {
            row.setIsHidden(false);
            row.setRestoredAt(now);
            row.setRestoredBy(operatorId);
            row.setUpdatedAt(now);
            if (PENDING.equals(row.getStatus())) row.setAutoDispatchEpoch(0L);
            if (noticeMapper.updateById(row) != 1) throw new IllegalStateException("销售变更通知状态已变化，请刷新后重试");
        }
        auditVisibilityBatch(ACTION_RESTORE, rows, normalizedReason);
        return rows.size();
    }

    private List<SalesOrderChangeNotice> lockVisibleRows(List<String> requestedIds) {
        List<String> ids = normalizeIds(requestedIds);
        QueryWrapper<SalesOrderChangeNotice> query = new QueryWrapper<>();
        query.in("notice_id", ids);
        applyScope(query);
        query.orderByAsc("id").last("FOR UPDATE");
        List<SalesOrderChangeNotice> rows = noticeMapper.selectList(query);
        if (rows == null || rows.size() != ids.size()) {
            throw new IllegalArgumentException("部分销售变更通知不存在或无权操作");
        }
        return rows;
    }

    private String normalizeVisibilityReason(String reason, boolean required) {
        String normalized = reason == null ? "" : reason.trim();
        if (required && normalized.isEmpty()) throw new IllegalArgumentException("隐藏理由不能为空");
        if (normalized.length() > 500) throw new IllegalArgumentException("理由不能超过 500 个字符");
        return normalized.isEmpty() ? null : normalized;
    }

    private boolean isHideableStatus(String status) {
        return Set.of(PENDING, FAILED, RESULT_UNKNOWN).contains(status);
    }

    private void auditVisibilityBatch(String action, List<SalesOrderChangeNotice> rows, String reason) {
        if (auditService == null) return;
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("noticeIds", rows.stream().map(SalesOrderChangeNotice::getNoticeId).toList());
        summary.put("noticeCount", rows.size());
        summary.put("salesOrderIds", rows.stream().map(SalesOrderChangeNotice::getSalesOrderId).filter(Objects::nonNull).distinct().toList());
        summary.put("salesOrderItemIds", rows.stream().map(SalesOrderChangeNotice::getSalesOrderItemId).filter(Objects::nonNull).distinct().toList());
        summary.put("allocationIds", rows.stream().map(SalesOrderChangeNotice::getAllocationId).filter(Objects::nonNull).distinct().toList());
        if (reason != null) summary.put("reason", safeError(reason));
        auditService.record(action, "sales_order_change_notice", null, null, summary,
                "CHANGE", "SUCCESS", AuditService.labelForAction(action), null);
    }

    private Set<String> readStringList(String value) {
        if (value == null || value.isBlank()) return new LinkedHashSet<>();
        try {
            String[] values = new ObjectMapper().readValue(value, String[].class);
            return new LinkedHashSet<>(List.of(values));
        } catch (Exception ignored) {
            return new LinkedHashSet<>();
        }
    }

    private String json(Object value) {
        try { return new ObjectMapper().writeValueAsString(value); }
        catch (JsonProcessingException e) { throw new IllegalStateException("销售变更字段快照生成失败", e); }
    }

    private long safeEpoch(Long value) { return value == null ? 0L : value; }

    private void audit(String action, SalesOrderChangeNotice notice, Map<String, Object> extra) {
        if (auditService == null) return;
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("noticeId", notice.getNoticeId());
        summary.put("salesOrderId", notice.getSalesOrderId());
        summary.put("salesOrderItemId", notice.getSalesOrderItemId());
        summary.put("allocationId", notice.getAllocationId());
        summary.put("changeType", notice.getChangeType());
        summary.put("targetType", notice.getTargetType());
        if (notice.getReason() != null) summary.put("reason", safeError(notice.getReason()));
        summary.putAll(extra);
        auditService.record(action, "sales_order_change_notice", notice.getId(), null, summary,
                "CHANGE", "SUCCESS", AuditService.labelForAction(action), null);
    }

    public PageResult<SalesOrderChangeNoticeDTO> page(SalesOrderChangeNoticeQueryReq req) {
        SalesOrderChangeNoticeQueryReq safe = req == null ? new SalesOrderChangeNoticeQueryReq() : req;
        int page = safe.getPage() == null || safe.getPage() < 1 ? 1 : safe.getPage();
        int pageSize = safe.getPageSize() == null || safe.getPageSize() < 1 ? 20 : Math.min(safe.getPageSize(), 100);
        QueryWrapper<SalesOrderChangeNotice> query = noticeQuery(safe);
        Long total = noticeMapper.selectCount(query);
        if (total == null || total == 0) return PageResult.of(List.of(), 0, page, pageSize);
        query.orderByDesc("created_at").orderByDesc("id")
                .last("LIMIT " + pageSize + " OFFSET " + ((page - 1L) * pageSize));
        return PageResult.of(toDtos(noticeMapper.selectList(query)), total, page, pageSize);
    }

    public SalesOrderChangeNoticeDTO detail(String noticeId) {
        QueryWrapper<SalesOrderChangeNotice> query = new QueryWrapper<>();
        query.eq("notice_id", noticeId);
        applyScope(query);
        SalesOrderChangeNotice notice = noticeMapper.selectOne(query);
        if (notice == null) throw new IllegalArgumentException("销售变更通知不存在或无权查看");
        return toDtos(List.of(notice)).get(0);
    }

    public SalesOrderChangeDispatchSettingDTO setting() {
        SalesOrderChangeDispatchSetting setting = settingMapper.selectSingletonForShare();
        if (setting == null) throw new IllegalStateException("销售变更发送模式未初始化");
        return toSettingDto(setting);
    }

    @Transactional
    public SalesOrderChangeDispatchSettingDTO updateMode(SalesOrderChangeDispatchModeReq req) {
        String mode = req == null || req.getDispatchMode() == null ? null : req.getDispatchMode().trim().toUpperCase();
        if (!MANUAL.equals(mode) && !AUTO.equals(mode)) throw new IllegalArgumentException("销售变更发送模式只能是 MANUAL 或 AUTO");
        SalesOrderChangeDispatchSetting setting = settingMapper.selectSingletonForUpdate();
        if (setting == null) throw new IllegalStateException("销售变更发送模式未初始化");
        String old = setting.getDispatchMode();
        if (AUTO.equals(mode) && !AUTO.equals(old)) {
            setting.setAutoDispatchEpoch(safeEpoch(setting.getAutoDispatchEpoch()) + 1);
            setting.setAutoEnabledAt(LocalDateTime.now());
        }
        setting.setDispatchMode(mode);
        setting.setUpdatedBy(currentUserId());
        setting.setUpdatedAt(LocalDateTime.now());
        settingMapper.updateById(setting);
        if (auditService != null) {
            auditService.record("SALES_ORDER_CHANGE_MODE_UPDATE", "sales_order_change_dispatch_setting", 1L,
                    Map.of("dispatchMode", old), Map.of("dispatchMode", mode, "autoDispatchEpoch", setting.getAutoDispatchEpoch()),
                    "CHANGE", "SUCCESS", AuditService.labelForAction("SALES_ORDER_CHANGE_MODE_UPDATE"), null);
        }
        return toSettingDto(setting);
    }

    @Transactional
    public List<String> claimManual(List<String> requestedIds) {
        List<String> ids = normalizeIds(requestedIds);
        SalesOrderChangeDispatchSetting setting = settingMapper.selectSingletonForUpdate();
        boolean automaticMode = setting != null && AUTO.equals(setting.getDispatchMode());
        long currentAutoEpoch = setting == null ? 0L : safeEpoch(setting.getAutoDispatchEpoch());
        QueryWrapper<SalesOrderChangeNotice> query = new QueryWrapper<>();
        query.in("notice_id", ids).eq("is_hidden", false).in("status", List.of(PENDING, FAILED, RESULT_UNKNOWN));
        applyScope(query);
        query.last("FOR UPDATE");
        List<SalesOrderChangeNotice> rows = noticeMapper.selectList(query);
        List<String> eligible = rows.stream()
                .filter(row -> !automaticMode
                        || !PENDING.equals(row.getStatus())
                        || safeEpoch(row.getAutoDispatchEpoch()) < currentAutoEpoch)
                .filter(row -> !Boolean.TRUE.equals(row.getIsHidden()))
                .map(SalesOrderChangeNotice::getNoticeId)
                .filter(Objects::nonNull)
                .toList();
        if (eligible.isEmpty()) throw new IllegalArgumentException("没有可发送的销售变更通知");
        int updated = noticeMapper.claimManual(eligible);
        if (updated != eligible.size()) throw new IllegalStateException("部分销售变更通知已变化，请刷新后重试");
        return eligible;
    }

    @Transactional
    public void markSubmissionFailed(List<String> noticeIds, String error) {
        List<String> ids = normalizeIds(noticeIds);
        UpdateWrapper<SalesOrderChangeNotice> update = new UpdateWrapper<>();
        update.in("notice_id", ids).eq("status", SENDING)
                .set("status", FAILED)
                .set("last_error", abbreviate(error))
                .set("updated_at", LocalDateTime.now());
        noticeMapper.update(null, update);
        if (auditService != null) {
            auditService.record("SALES_ORDER_CHANGE_NOTICE_SEND_FAILED", "sales_order_change_notice", null,
                    null, Map.of("noticeCount", ids.size(), "submissionFailure", true), "CHANGE", "FAILED",
                    AuditService.labelForAction("SALES_ORDER_CHANGE_NOTICE_SEND_FAILED"), abbreviate(error));
        }
    }

    @Transactional
    public List<String> claimAutoPending() {
        SalesOrderChangeDispatchSetting setting = settingMapper.selectSingletonForShare();
        if (setting == null || !AUTO.equals(setting.getDispatchMode())) return List.of();
        List<SalesOrderChangeNotice> rows = noticeMapper.selectAutoPendingForUpdate(safeEpoch(setting.getAutoDispatchEpoch()), 100);
        if (rows == null || rows.isEmpty()) return List.of();
        rows = rows.stream().filter(row -> !Boolean.TRUE.equals(row.getIsHidden())).toList();
        if (rows.isEmpty()) return List.of();
        List<Long> ids = rows.stream().map(SalesOrderChangeNotice::getId).toList();
        int updated = noticeMapper.claimAuto(ids, safeEpoch(setting.getAutoDispatchEpoch()));
        if (updated != ids.size()) throw new IllegalStateException("自动发送通知状态已变化，请稍后重试");
        return rows.stream().map(SalesOrderChangeNotice::getNoticeId).toList();
    }

    @Transactional
    public Map<String, Object> sendClaimed(List<String> noticeIds, Long taskId) {
        List<String> ids = normalizeIds(noticeIds);
        QueryWrapper<SalesOrderChangeNotice> query = new QueryWrapper<>();
        query.in("notice_id", ids).eq("status", SENDING);
        List<SalesOrderChangeNotice> rows = noticeMapper.selectList(query);
        if (rows == null || rows.isEmpty()) return Map.of("taskId", taskId, "totalCount", 0);
        String bizId = "QPO-SOC-" + LocalDateTime.now().toString().replaceAll("[^0-9]", "") + "-"
                + UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase();
        Map<String, Object> request;
        try {
            request = new LinkedHashMap<>();
            request.put("bizId", bizId);
            List<Map<String, Object>> items = new ArrayList<>();
            for (SalesOrderChangeNotice row : rows) {
                Map<String, Object> item = parseSnapshot(row.getPayloadSnapshotJson());
                item.put("noticeId", row.getNoticeId());
                item.put("changeType", row.getChangeType());
                item.put("targetType", row.getTargetType());
                item.put("targetExternalId", row.getTargetExternalId());
                item.put("sourceBizId", row.getSourceBizId());
                item.put("allocationId", row.getAllocationId());
                item.put("reason", row.getReason());
                item.put("changedFields", readStringList(row.getChangedFieldsJson()).stream().toList());
                items.add(item);
            }
            request.put("items", items);
        } catch (RuntimeException e) {
            updateClaimed(rows, FAILED, "Unable to build sales change notice payload");
            return sendSummary(rows, taskId, bizId, 0L);
        }
        long startedAt = System.currentTimeMillis();
        Object raw;
        try {
            raw = dataCenterIntegrationClient.call(
                    DataCenterEndpointRegistry.MES_SALES_ORDER_CHANGE_NOTICE, request, Map.class);
        } catch (DataCenterCallException e) {
            updateClaimed(rows, e.getCertainty() == DataCenterCallException.FailureCertainty.RESULT_UNKNOWN
                    ? RESULT_UNKNOWN : FAILED, abbreviate(e.getMessage()));
            return sendSummary(rows, taskId, bizId, System.currentTimeMillis() - startedAt);
        } catch (RuntimeException e) {
            updateClaimed(rows, FAILED, abbreviate(e.getMessage()));
            return sendSummary(rows, taskId, bizId, System.currentTimeMillis() - startedAt);
        }
        try {
            applyResponse(rows, raw, bizId);
        } catch (RuntimeException e) {
            updateClaimed(rows, RESULT_UNKNOWN, "Unable to process sales change notice response");
        }
        return sendSummary(rows, taskId, bizId, System.currentTimeMillis() - startedAt);
    }

    private void applyResponse(List<SalesOrderChangeNotice> rows, Object raw, String bizId) {
        MesSalesOrderChangeNoticeResp response;
        try {
            response = objectMapper.convertValue(raw, MesSalesOrderChangeNoticeResp.class);
        } catch (RuntimeException error) {
            updateClaimed(rows, RESULT_UNKNOWN, "sales change notice response structure cannot be parsed");
            return;
        }
        if (response == null || response.getCode() == null) {
            updateClaimed(rows, RESULT_UNKNOWN, "sales change notice response is missing its result code");
            return;
        }
        if (!response.successCode()) {
            updateClaimed(rows, FAILED, safeError(response.getMessage()));
            return;
        }
        if (response.getData() == null || response.getData().getItems() == null) {
            updateClaimed(rows, RESULT_UNKNOWN, "sales change notice response is missing item results");
            return;
        }
        Map<String, MesSalesOrderChangeNoticeResp.ItemResult> returned = new LinkedHashMap<>();
        boolean duplicate = false;
        for (MesSalesOrderChangeNoticeResp.ItemResult item : response.getData().getItems()) {
            if (item == null || item.getNoticeId() == null || returned.put(item.getNoticeId(), item) != null) duplicate = true;
        }
        Set<String> expected = rows.stream().map(SalesOrderChangeNotice::getNoticeId)
                .filter(Objects::nonNull).collect(java.util.stream.Collectors.toSet());
        if (duplicate || returned.size() != rows.size() || !returned.keySet().equals(expected)) {
            updateClaimed(rows, RESULT_UNKNOWN, "销售变更通知响应缺少或重复 noticeId");
            return;
        }
        for (SalesOrderChangeNotice row : rows) {
            MesSalesOrderChangeNoticeResp.ItemResult result = returned.get(row.getNoticeId());
            String itemStatus = result == null || result.getStatus() == null
                    ? "" : result.getStatus().trim().toUpperCase();
            boolean accepted = Set.of("ACCEPTED", "SUCCESS", "OK").contains(itemStatus);
            boolean rejected = Set.of("REJECTED", "FAILED", "ERROR", "DENIED", "NOT_ACCEPTED")
                    .contains(itemStatus);
            if (hasText(row.getTargetExternalId()) && hasText(result.getTargetExternalId())
                    && !row.getTargetExternalId().equals(result.getTargetExternalId())) {
                accepted = false;
                rejected = false;
            }
            String status = accepted ? SENT : rejected ? FAILED : RESULT_UNKNOWN;
            String error = accepted ? null : safeError(result == null ? null : result.getMessage());
            updateStatus(row, status, error, accepted ? LocalDateTime.now() : null);
            String action = accepted ? "SALES_ORDER_CHANGE_NOTICE_SEND_SUCCESS"
                    : rejected ? "SALES_ORDER_CHANGE_NOTICE_SEND_FAILED"
                    : "SALES_ORDER_CHANGE_NOTICE_RESULT_UNKNOWN";
            if (auditService != null) auditService.record(action,
                    "sales_order_change_notice", row.getId(), null, Map.of("noticeId", row.getNoticeId(), "bizId", bizId),
                    "CHANGE", status, AuditService.labelForAction(action), error);
        }
    }

    private Map<String, Object> sendSummary(List<SalesOrderChangeNotice> rows, Long taskId,
                                           String bizId, Long durationMs) {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("taskId", taskId);
        summary.put("bizId", bizId);
        summary.put("totalCount", rows.size());
        summary.put("sentCount", countStatus(rows, SENT));
        summary.put("failedCount", countStatus(rows, FAILED));
        summary.put("unknownCount", countStatus(rows, RESULT_UNKNOWN));
        summary.put("durationMs", durationMs);
        if (auditService != null) {
            String action = countStatus(rows, RESULT_UNKNOWN) > 0
                    ? "SALES_ORDER_CHANGE_NOTICE_RESULT_UNKNOWN"
                    : countStatus(rows, FAILED) > 0
                    ? "SALES_ORDER_CHANGE_NOTICE_SEND_FAILED"
                    : "SALES_ORDER_CHANGE_NOTICE_SEND_SUCCESS";
            auditService.record(action, "sales_order_change_notice", null, null, summary,
                    "CHANGE", countStatus(rows, RESULT_UNKNOWN) > 0 ? RESULT_UNKNOWN
                            : countStatus(rows, FAILED) > 0 ? FAILED : SENT,
                    AuditService.labelForAction(action), null);
        }
        return summary;
    }

    private Map<String, Object> parseSnapshot(String snapshot) {
        try { return objectMapper.readValue(snapshot, LinkedHashMap.class); }
        catch (Exception e) { throw new IllegalStateException("销售变更通知快照无法读取", e); }
    }

    private void updateClaimed(List<SalesOrderChangeNotice> rows, String status, String error) {
        for (SalesOrderChangeNotice row : rows) {
            updateStatus(row, status, error, SENT.equals(status) ? LocalDateTime.now() : null);
            if (auditService != null) auditService.record(
                    RESULT_UNKNOWN.equals(status) ? "SALES_ORDER_CHANGE_NOTICE_RESULT_UNKNOWN" : "SALES_ORDER_CHANGE_NOTICE_SEND_FAILED",
                    "sales_order_change_notice", row.getId(), null,
                    Map.of("noticeId", row.getNoticeId(), "errorLength", error == null ? 0 : error.length()),
                    "CHANGE", status, AuditService.labelForAction(RESULT_UNKNOWN.equals(status)
                            ? "SALES_ORDER_CHANGE_NOTICE_RESULT_UNKNOWN" : "SALES_ORDER_CHANGE_NOTICE_SEND_FAILED"), error);
        }
    }

    private void updateStatus(SalesOrderChangeNotice row, String status, String error, LocalDateTime sentAt) {
        UpdateWrapper<SalesOrderChangeNotice> update = new UpdateWrapper<>();
        update.eq("id", row.getId()).eq("status", SENDING)
                .set("status", status).set("last_error", error).set("sent_at", sentAt).set("updated_at", LocalDateTime.now());
        noticeMapper.update(null, update);
        row.setStatus(status);
        row.setLastError(error);
        row.setSentAt(sentAt);
    }

    private QueryWrapper<SalesOrderChangeNotice> noticeQuery(SalesOrderChangeNoticeQueryReq req) {
        QueryWrapper<SalesOrderChangeNotice> query = new QueryWrapper<>();
        String view = hasText(req.getView()) ? req.getView().trim().toUpperCase() : "ACTIVE";
        switch (view) {
            case "ACTIVE" -> query.eq("is_hidden", false).ne("status", SENT);
            case "HIDDEN" -> query.eq("is_hidden", true);
            case "SENT" -> query.eq("is_hidden", false).eq("status", SENT);
            default -> throw new IllegalArgumentException("销售变更视图只能是 ACTIVE、HIDDEN 或 SENT");
        }
        if (req.getCustomerId() != null) query.apply("sales_order_id IN (SELECT id FROM sales_order_header WHERE customer_id = {0})", req.getCustomerId());
        if (hasText(req.getSalesOrderNo())) query.apply("sales_order_id IN (SELECT id FROM sales_order_header WHERE sales_order_no ILIKE {0} OR document_no ILIKE {0})", "%" + req.getSalesOrderNo().trim() + "%");
        if (req.getLineNo() != null) query.apply("sales_order_item_id IN (SELECT id FROM sales_order_item WHERE line_no = {0})", req.getLineNo());
        if (hasText(req.getChangeType())) query.eq("change_type", req.getChangeType().trim().toUpperCase());
        if (hasText(req.getTargetType())) query.eq("target_type", req.getTargetType().trim().toUpperCase());
        if (hasText(req.getStatus())) query.eq("status", req.getStatus().trim().toUpperCase());
        if (req.getDateFrom() != null) query.ge("created_at", req.getDateFrom().atStartOfDay());
        if (req.getDateTo() != null) query.lt("created_at", req.getDateTo().plusDays(1).atStartOfDay());
        applyScope(query);
        return query;
    }

    private void applyScope(QueryWrapper<SalesOrderChangeNotice> query) {
        if (dataScopeService != null) dataScopeService.applySalesOrderIdScope(query, "sales_order_id");
    }

    private List<SalesOrderChangeNoticeDTO> toDtos(List<SalesOrderChangeNotice> rows) {
        if (rows == null || rows.isEmpty()) return List.of();
        Map<Long, SalesOrderHeader> headers = new LinkedHashMap<>();
        Map<Long, SalesOrderItem> items = new LinkedHashMap<>();
        List<Long> headerIds = rows.stream().map(SalesOrderChangeNotice::getSalesOrderId).filter(Objects::nonNull).distinct().toList();
        List<Long> itemIds = rows.stream().map(SalesOrderChangeNotice::getSalesOrderItemId).filter(Objects::nonNull).distinct().toList();
        headerMapper.selectBatchIds(headerIds).forEach(row -> headers.put(row.getId(), row));
        itemMapper.selectBatchIds(itemIds).forEach(row -> items.put(row.getId(), row));
        List<SalesOrderChangeNoticeDTO> result = new ArrayList<>();
        for (SalesOrderChangeNotice row : rows) {
            SalesOrderHeader header = headers.get(row.getSalesOrderId());
            SalesOrderItem item = items.get(row.getSalesOrderItemId());
            SalesOrderChangeNoticeDTO dto = new SalesOrderChangeNoticeDTO();
            dto.setId(row.getId()); dto.setNoticeId(row.getNoticeId()); dto.setSalesOrderId(row.getSalesOrderId());
            dto.setSalesOrderNo(header == null ? null : firstNonBlank(header.getSalesOrderNo(), header.getDocumentNo()));
            dto.setSalesOrderItemId(row.getSalesOrderItemId()); dto.setLineNo(item == null ? null : item.getLineNo());
            dto.setCustomerName(header == null ? null : firstNonBlank(header.getCustomerName(), header.getCustomerShortName()));
            dto.setPartNo(item == null ? null : item.getPartNo()); dto.setVersion(item == null ? null : item.getVersion());
            dto.setAllocationId(row.getAllocationId() == null ? null : Math.toIntExact(row.getAllocationId()));
            dto.setTargetType(row.getTargetType()); dto.setTargetKey(row.getTargetKey()); dto.setTargetExternalId(row.getTargetExternalId());
            dto.setSourceBizId(row.getSourceBizId()); dto.setChangeType(row.getChangeType()); dto.setReason(row.getReason());
            dto.setChangedFields(new ArrayList<>(readStringList(row.getChangedFieldsJson()))); dto.setSnapshot(parseSnapshot(row.getPayloadSnapshotJson()));
            dto.setStatus(row.getStatus()); dto.setAutoDispatchEpoch(safeEpoch(row.getAutoDispatchEpoch()));
            dto.setAttemptCount(row.getAttemptCount()); dto.setLastAttemptAt(row.getLastAttemptAt()); dto.setSentAt(row.getSentAt()); dto.setLastError(row.getLastError()); dto.setCreatedByName(row.getCreatedByName()); dto.setCreatedAt(row.getCreatedAt());
             dto.setHidden(Boolean.TRUE.equals(row.getIsHidden())); dto.setHiddenReason(row.getHiddenReason()); dto.setHiddenAt(row.getHiddenAt()); dto.setHiddenByName(row.getHiddenByName()); dto.setRestoredAt(row.getRestoredAt()); dto.setRestoredBy(row.getRestoredBy());
            result.add(dto);
        }
        return result;
    }

    private SalesOrderChangeDispatchSettingDTO toSettingDto(SalesOrderChangeDispatchSetting setting) {
        SalesOrderChangeDispatchSettingDTO dto = new SalesOrderChangeDispatchSettingDTO();
        dto.setDispatchMode(setting.getDispatchMode()); dto.setAutoDispatchEpoch(setting.getAutoDispatchEpoch()); dto.setAutoEnabledAt(setting.getAutoEnabledAt()); dto.setUpdatedAt(setting.getUpdatedAt());
        return dto;
    }

    private List<String> normalizeIds(Collection<String> ids) {
        List<String> result = ids == null ? List.of() : ids.stream().filter(Objects::nonNull).map(String::trim).filter(value -> !value.isEmpty()).distinct().toList();
        if (result.isEmpty() || result.size() > 1000) throw new IllegalArgumentException("销售变更通知选择数量必须为 1 至 1000 条");
        return result;
    }

    private int countStatus(List<SalesOrderChangeNotice> rows, String status) { return (int) rows.stream().filter(row -> status.equals(row.getStatus())).count(); }
    private Long currentUserId() { AuthContext.SessionUser user = AuthContext.get(); return user == null || user.id() == null ? null : user.id().longValue(); }
    private boolean hasText(String value) { return value != null && !value.trim().isEmpty(); }
    private String firstNonBlank(String... values) { for (String value : values) if (hasText(value)) return value.trim(); return null; }
    private String safeError(String value) {
        if (!hasText(value)) return "下游拒绝销售变更通知";
        String redacted = value
                .replaceAll("(?i)(token|authorization|cookie|password|secret)\\s*[:=]\\s*[^\\s,;]+", "$1=[REDACTED]")
                .replaceAll("https?://\\S+", "[URL]");
        return abbreviate(redacted);
    }
    private String abbreviate(String value) { if (value == null) return ""; String text = value.replaceAll("\\s+", " ").trim(); return text.length() <= 500 ? text : text.substring(0, 500); }
}
