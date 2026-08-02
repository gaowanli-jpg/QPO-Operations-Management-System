package com.machining.quote.service.mes;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.machining.quote.entity.OrderDeliveryTicket;
import com.machining.quote.entity.OrderDeliveryTicketMesRequest;
import com.machining.quote.entity.OrderDeliveryTicketMesWaiter;
import com.machining.quote.mapper.OrderDeliveryTicketMapper;
import com.machining.quote.mapper.OrderDeliveryTicketMesRequestMapper;
import com.machining.quote.mapper.OrderDeliveryTicketMesWaiterMapper;
import com.machining.quote.service.AuditService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class MesMaterialDedupService {
    public static final String STATUS_PROCESSING = "PROCESSING";
    public static final String STATUS_CALLBACK_PENDING = "CALLBACK_PENDING";
    public static final String STATUS_SUCCESS = "SUCCESS";
    public static final String ROLE_MASTER = "MASTER";
    public static final String ROLE_FOLLOWER = "FOLLOWER";
    public static final String ROLE_REUSED = "REUSED";
    public static final String RESULT_MASTER_CREATED = "MASTER_CREATED";
    public static final String RESULT_FOLLOW_EXISTING_ACTIVE = "FOLLOW_EXISTING_ACTIVE";
    public static final String RESULT_REUSE_SUCCESS = "REUSE_SUCCESS";

    @Autowired(required = false)
    private OrderDeliveryTicketMapper ticketMapper;
    @Autowired(required = false)
    private OrderDeliveryTicketMesRequestMapper mesRequestMapper;
    @Autowired(required = false)
    private OrderDeliveryTicketMesWaiterMapper waiterMapper;
    @Autowired(required = false)
    private AuditService auditService;

    public String buildDedupKey(OrderDeliveryTicket ticket) {
        String partNo = normalizeKeyPart(ticket == null ? null : ticket.getPartNo());
        if (partNo.isEmpty()) {
            throw new RuntimeException("Part number cannot be blank");
        }
        String version = normalizeKeyPart(ticket.getVersion());
        return partNo + "|" + (version.isEmpty() ? "-" : version);
    }

    public DedupResolveResult resolveBeforeMes(OrderDeliveryTicket ticket, Long taskId) {
        String dedupKey = buildDedupKey(ticket);
        ReusableMaterial reusable = findReusableMaterial(ticket, dedupKey);
        if (reusable != null) {
            return DedupResolveResult.reuse(dedupKey, reusable.ticketId(), reusable.mesRequestId(), reusable.materialNo());
        }
        OrderDeliveryTicketMesRequest activeMaster = findActiveMaster(dedupKey);
        if (activeMaster != null) {
            return createFollower(ticket, taskId, dedupKey, activeMaster);
        }
        return DedupResolveResult.master(dedupKey);
    }

    public DedupResolveResult followActiveMaster(OrderDeliveryTicket ticket, Long taskId, String dedupKey) {
        OrderDeliveryTicketMesRequest activeMaster = findActiveMaster(dedupKey);
        if (activeMaster == null) {
            throw new RuntimeException("Active MES master request not found for dedup key");
        }
        return createFollower(ticket, taskId, dedupKey, activeMaster);
    }

    private ReusableMaterial findReusableMaterial(OrderDeliveryTicket ticket, String dedupKey) {
        if (ticketMapper == null) {
            return null;
        }
        String partNo = normalizeKeyPart(ticket.getPartNo());
        String normalizedVersion = normalizeKeyPart(ticket.getVersion());
        final String version = normalizedVersion.isEmpty() ? "-" : normalizedVersion;
        QueryWrapper<OrderDeliveryTicket> wrapper = new QueryWrapper<>();
        wrapper.eq("mes_sync_status", STATUS_SUCCESS)
                .isNotNull("material_no")
                .and(match -> match.eq("mes_dedup_key", dedupKey)
                        .or()
                        .apply(
                                "upper(trim(part_no)) = {0} AND coalesce(nullif(upper(trim(version)), ''), '-') = {1}",
                                partNo,
                                version
                        ));
        if (ticket.getId() != null) {
            wrapper.ne("id", ticket.getId());
        }
        List<OrderDeliveryTicket> tickets = ticketMapper.selectList(wrapper);
        Map<String, ReusableMaterial> materials = new LinkedHashMap<>();
        for (OrderDeliveryTicket candidate : tickets) {
            String materialNo = normalizeMaterialNo(candidate.getMaterialNo());
            if (!materialNo.isEmpty()) {
                materials.putIfAbsent(
                        materialNo,
                        new ReusableMaterial(candidate.getId(), candidate.getMesRequestId(), materialNo)
                );
            }
        }
        return materials.size() == 1 ? materials.values().iterator().next() : null;
    }

    private OrderDeliveryTicketMesRequest findActiveMaster(String dedupKey) {
        if (mesRequestMapper == null || isBlank(dedupKey)) {
            return null;
        }
        QueryWrapper<OrderDeliveryTicketMesRequest> wrapper = new QueryWrapper<>();
        wrapper.eq("dedup_key", dedupKey)
                .eq("request_role", ROLE_MASTER)
                .in("request_status", STATUS_PROCESSING, STATUS_CALLBACK_PENDING)
                .orderByDesc("id")
                .last("LIMIT 1");
        return mesRequestMapper.selectOne(wrapper);
    }

    private DedupResolveResult createFollower(
            OrderDeliveryTicket ticket,
            Long taskId,
            String dedupKey,
            OrderDeliveryTicketMesRequest activeMaster) {
        String masterStatus = normalizeStatus(activeMaster.getRequestStatus());
        String waiterStatus = STATUS_CALLBACK_PENDING.equals(masterStatus) ? STATUS_CALLBACK_PENDING : "WAITING";
        insertWaiter(ticket, dedupKey, activeMaster, waiterStatus);
        updateFollowerTicket(ticket, dedupKey, activeMaster, masterStatus);
        incrementFollowerCount(activeMaster.getMesRequestId());
        if (auditService != null) {
            Map<String, Object> summary = new LinkedHashMap<>();
            summary.put("ticketId", ticket.getId());
            summary.put("taskId", taskId);
            summary.put("dedupKey", dedupKey);
            summary.put("masterTicketId", activeMaster.getTicketId());
            summary.put("masterMesRequestId", activeMaster.getMesRequestId());
            summary.put("status", masterStatus);
            auditService.record(
                    "DELIVERY_MES_MATERIAL_DEDUP_FOLLOW",
                    "order_delivery_ticket",
                    ticket.getId(),
                    null,
                    summary,
                    "DELIVERY",
                    masterStatus,
                    AuditService.labelForAction("DELIVERY_MES_MATERIAL_DEDUP_FOLLOW")
                            + " ticketId=" + ticket.getId()
                            + " taskId=" + taskId
                            + " dedupKey=" + dedupKey,
                    null
            );
        }
        return DedupResolveResult.follow(
                dedupKey,
                activeMaster.getTicketId(),
                activeMaster.getMesRequestId(),
                masterStatus
        );
    }

    private void insertWaiter(
            OrderDeliveryTicket ticket,
            String dedupKey,
            OrderDeliveryTicketMesRequest activeMaster,
            String waiterStatus) {
        if (waiterMapper == null) {
            return;
        }
        OrderDeliveryTicketMesWaiter waiter = new OrderDeliveryTicketMesWaiter();
        waiter.setDedupKey(dedupKey);
        waiter.setMasterTicketId(activeMaster.getTicketId());
        waiter.setMasterMesRequestId(activeMaster.getMesRequestId());
        waiter.setWaiterTicketId(ticket.getId());
        waiter.setWaitStatus(waiterStatus);
        waiter.setWaitMessage("Follow active MES material request for same part/version");
        waiter.setCreatedAt(LocalDateTime.now());
        waiter.setUpdatedAt(LocalDateTime.now());
        waiterMapper.insert(waiter);
    }

    private void updateFollowerTicket(
            OrderDeliveryTicket ticket,
            String dedupKey,
            OrderDeliveryTicketMesRequest activeMaster,
            String masterStatus) {
        OrderDeliveryTicket update = new OrderDeliveryTicket();
        update.setId(ticket.getId());
        update.setMesSyncStatus(masterStatus);
        update.setMesSyncMessage("Follow active MES material request for same part/version");
        update.setMesDedupKey(dedupKey);
        update.setMesRequestRole(ROLE_FOLLOWER);
        update.setMesMasterTicketId(activeMaster.getTicketId());
        update.setMesMasterRequestId(activeMaster.getMesRequestId());
        ticketMapper.updateById(update);
    }

    private void incrementFollowerCount(String mesRequestId) {
        if (mesRequestMapper == null || isBlank(mesRequestId)) {
            return;
        }
        UpdateWrapper<OrderDeliveryTicketMesRequest> wrapper = new UpdateWrapper<>();
        wrapper.eq("mes_request_id", mesRequestId)
                .setSql("follower_count = COALESCE(follower_count, 0) + 1")
                .set("updated_at", LocalDateTime.now());
        mesRequestMapper.update(null, wrapper);
    }

    private String normalizeKeyPart(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }

    private String normalizeMaterialNo(String value) {
        return value == null ? "" : value.trim();
    }

    private String normalizeStatus(String value) {
        if (isBlank(value)) {
            return STATUS_PROCESSING;
        }
        String status = value.trim().toUpperCase(Locale.ROOT);
        return STATUS_CALLBACK_PENDING.equals(status) ? STATUS_CALLBACK_PENDING : STATUS_PROCESSING;
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private record ReusableMaterial(Integer ticketId, String mesRequestId, String materialNo) {
    }

    public static class DedupResolveResult {
        private final String type;
        private final String dedupKey;
        private final Integer masterTicketId;
        private final String masterMesRequestId;
        private final String status;
        private final String materialNo;

        private DedupResolveResult(
                String type,
                String dedupKey,
                Integer masterTicketId,
                String masterMesRequestId,
                String status,
                String materialNo) {
            this.type = type;
            this.dedupKey = dedupKey;
            this.masterTicketId = masterTicketId;
            this.masterMesRequestId = masterMesRequestId;
            this.status = status;
            this.materialNo = materialNo;
        }

        public static DedupResolveResult master(String dedupKey) {
            return new DedupResolveResult(RESULT_MASTER_CREATED, dedupKey, null, null, STATUS_PROCESSING, null);
        }

        public static DedupResolveResult follow(
                String dedupKey,
                Integer masterTicketId,
                String masterMesRequestId,
                String status) {
            return new DedupResolveResult(
                    RESULT_FOLLOW_EXISTING_ACTIVE,
                    dedupKey,
                    masterTicketId,
                    masterMesRequestId,
                    status,
                    null
            );
        }

        public static DedupResolveResult reuse(
                String dedupKey,
                Integer sourceTicketId,
                String sourceMesRequestId,
                String materialNo) {
            return new DedupResolveResult(
                    RESULT_REUSE_SUCCESS,
                    dedupKey,
                    sourceTicketId,
                    sourceMesRequestId,
                    STATUS_SUCCESS,
                    materialNo
            );
        }

        public String getType() {
            return type;
        }

        public String getDedupKey() {
            return dedupKey;
        }

        public Integer getMasterTicketId() {
            return masterTicketId;
        }

        public String getMasterMesRequestId() {
            return masterMesRequestId;
        }

        public String getStatus() {
            return status;
        }

        public String getMaterialNo() {
            return materialNo;
        }

        public boolean isReuseSuccess() {
            return RESULT_REUSE_SUCCESS.equals(type);
        }

        public boolean isFollowExistingActive() {
            return RESULT_FOLLOW_EXISTING_ACTIVE.equals(type);
        }
    }
}
