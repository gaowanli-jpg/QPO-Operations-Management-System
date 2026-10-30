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
import com.machining.quote.service.SourceItemMaterialNoSyncService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class MesMaterialDedupService {
    private static final Logger log = LoggerFactory.getLogger(MesMaterialDedupService.class);
    public static final String STATUS_PROCESSING = "PROCESSING";
    public static final String STATUS_CALLBACK_PENDING = "CALLBACK_PENDING";
    public static final String STATUS_SUCCESS = "SUCCESS";
    public static final String STATUS_FAILED = "FAILED";
    public static final String STATUS_RESULT_UNKNOWN = "RESULT_UNKNOWN";
    /** 已被「重新获取」收口的未知结果。不占身份去重位，只作为追溯记录留在台账里。 */
    public static final String STATUS_STALE = "STALE";
    public static final String ROLE_MASTER = "MASTER";
    public static final String ROLE_FOLLOWER = "FOLLOWER";
    public static final String RESULT_MASTER_CREATED = "MASTER_CREATED";
    public static final String RESULT_FOLLOW_EXISTING_ACTIVE = "FOLLOW_EXISTING_ACTIVE";
    /**
     * 同一物料身份已有在途 MES 请求，但它不是本流程的批量主请求（例如「刷新料号」写入的台账行，
     * 或本提单自身的旧请求）。此时不创建 follower，只把在途状态回给调用方。
     */
    public static final String RESULT_ACTIVE_REQUEST_EXISTS = "ACTIVE_REQUEST_EXISTS";
    private static final String STATUS_WAITING = "WAITING";
    /**
     * 三字段身份键前缀。旧两字段键是「图号|版本」，图号/版本都经 uppercase 归一化，
     * 不可能以小写 "v3" 开头，因此新旧键在格式上互斥，不需要额外区分。
     */
    private static final String IDENTITY_PREFIX = "v3|";
    private static final String LEGACY_KEY_SEPARATOR = "|";
    private static final String LEGACY_EMPTY_VERSION = "-";

    @Autowired(required = false)
    private OrderDeliveryTicketMapper ticketMapper;
    @Autowired(required = false)
    private OrderDeliveryTicketMesRequestMapper mesRequestMapper;
    @Autowired(required = false)
    private OrderDeliveryTicketMesWaiterMapper waiterMapper;
    private AuditService auditService;
    @Autowired
    private SourceItemMaterialNoSyncService sourceItemMaterialNoSyncService;
    @Autowired(required = false)
    private ProductionAllocationLifecycleService productionAllocationLifecycleService;

    /**
     * 活动请求身份 = 图号 + 版本 + 材质。
     *
     * <p>MES 的物料准备任务按「图号 + 版本 + 材质」创建，只按图号+版本去重会把
     * 同图号同版本但材质不同的零件并成 follower，后者永远拿不到自己的料号。</p>
     *
     * <p>编码使用长度前缀，避免图号/版本/材质本身含分隔符时产生歧义
     * （例如图号 "A|B" 与 图号 "A" + 版本 "B|C"）。</p>
     */
    public String buildDedupKey(OrderDeliveryTicket ticket) {
        return identityKey(
                ticket == null ? null : ticket.getPartNo(),
                ticket == null ? null : ticket.getVersion(),
                ticket == null ? null : ticket.getMaterialName());
    }

    /**
     * 纯函数形式的三字段身份键，供批量组装、刷新料号等旁路复用，
     * 避免各处各写一份归一化规则。
     */
    public static String identityKey(String partNo, String version, String materialName) {
        String normalizedPartNo = normalizeKeyPart(partNo);
        if (normalizedPartNo.isEmpty()) {
            throw new RuntimeException("Part number cannot be blank");
        }
        return encodeIdentity(
                normalizedPartNo,
                normalizeKeyPart(version),
                normalizeMaterialKeyPart(materialName));
    }

    /**
     * V71 之前写入的两字段键，只用于兼容尚未回填的活动台账行。
     * 图号为空时返回 null（旧键同样要求图号非空）。
     */
    public String buildLegacyDedupKey(OrderDeliveryTicket ticket) {
        String partNo = normalizeKeyPart(ticket == null ? null : ticket.getPartNo());
        if (partNo.isEmpty()) {
            return null;
        }
        String version = normalizeKeyPart(ticket.getVersion());
        return partNo + LEGACY_KEY_SEPARATOR
                + (version.isEmpty() ? LEGACY_EMPTY_VERSION : version);
    }

    /**
     * 长度前缀编码，避免图号/版本/材质本身含 {@code |} 或 {@code :} 时产生歧义。
     *
     * <p>长度按码点计数（{@link String#codePointCount}），与 PostgreSQL {@code length()}
     * 口径一致——V71 迁移用同一个函数重算存量键，两侧必须逐字节相同。</p>
     */
    static String encodeIdentity(String partNo, String version, String material) {
        return IDENTITY_PREFIX + codePointLength(partNo) + ":" + partNo
                + LEGACY_KEY_SEPARATOR + codePointLength(version) + ":" + version
                + LEGACY_KEY_SEPARATOR + codePointLength(material) + ":" + material;
    }

    private static int codePointLength(String value) {
        return value.codePointCount(0, value.length());
    }

    public DedupResolveResult resolveBeforeMes(OrderDeliveryTicket ticket, Long taskId) {
        String dedupKey = buildDedupKey(ticket);
        String legacyKey = buildLegacyDedupKey(ticket);

        // 先按三字段身份查。命中说明这条在途请求的材质与本提单可证明一致，
        // 才能安全地把本提单挂成 follower。
        OrderDeliveryTicketMesRequest active = findActiveRequest(dedupKey);
        if (active != null) {
            if (!ROLE_MASTER.equals(normalizeRole(active.getRequestRole()))
                    || isSameTicket(ticket, active)) {
                // 刷新料号等非批量请求、或本提单自身已有的在途请求：不能再建 follower，
                // 否则会出现「自己等自己」的等待关系，且掩盖了在途请求仍然有效这件事。
                return DedupResolveResult.activeRequestExists(
                        dedupKey, active.getMesRequestId(), normalizeStatus(active.getRequestStatus()));
            }
            return createFollower(ticket, taskId, dedupKey, active);
        }

        // 再按旧两字段键兜底。旧键只有图号+版本，无法证明这条在途请求是同一物料身份
        // （同图号版本不同材质在 MES 里是两个独立任务）。把本提单挂成 follower 会让它
        // 永远等不到自己的料号，所以这里只回在途状态，不建立任何等待关系。
        OrderDeliveryTicketMesRequest legacyActive = legacyKey == null ? null : findActiveRequest(legacyKey);
        if (legacyActive != null) {
            return DedupResolveResult.activeRequestExists(
                    dedupKey, legacyActive.getMesRequestId(), normalizeStatus(legacyActive.getRequestStatus()));
        }

        OrderDeliveryTicketMesRequest ownActive = findActiveRequestByTicketId(ticket == null ? null : ticket.getId());
        if (ownActive == null) {
            return DedupResolveResult.master(dedupKey);
        }
        return DedupResolveResult.activeRequestExists(
                dedupKey, ownActive.getMesRequestId(), normalizeStatus(ownActive.getRequestStatus()));
    }

    /**
     * 把本物料身份下仍停在 {@code RESULT_UNKNOWN} 的台账行收口成 {@code STALE}，释放身份去重位。
     *
     * <p>MES 按「图号 + 版本 + 材质」全局唯一判重：换一个新 bizId 重发不会建出第二个物料准备任务，
     * 只会明确返回 {@code EXISTING_PRODUCT}（带料号）或 {@code 1030900008}（任务还停在待建料）。
     * 所以「结果未知」不需要人工核对数据，重新获取一次就能拿到明确答案。</p>
     *
     * <p>旧行必须显式收口：{@code uk_delivery_mes_request_active_dedup} 把 RESULT_UNKNOWN 算作活跃，
     * 不收口的话新请求会撞唯一索引。收口只改状态、保留整行，事后仍能看到原 bizId 与当时的失败原因。</p>
     *
     * @return 被收口的 bizId 列表，供调用方记审计；已被回调抢先写成 SUCCESS 的行不会被改，也不返回。
     */
    public List<String> retireUnresolvedRequests(OrderDeliveryTicket ticket) {
        if (mesRequestMapper == null || ticket == null || ticket.getId() == null) {
            return List.of();
        }
        List<String> keys = new ArrayList<>();
        String identityKey = isBlank(ticket.getPartNo()) ? null : buildDedupKey(ticket);
        if (!isBlank(identityKey)) {
            keys.add(identityKey);
        }
        String legacyKey = isBlank(ticket.getPartNo()) ? null : buildLegacyDedupKey(ticket);
        if (!isBlank(legacyKey) && !keys.contains(legacyKey)) {
            keys.add(legacyKey);
        }
        // 一次查全：本提单的未知行 + 同身份键下别的提单的未知行。
        // 后者也必须收口，否则唯一索引会挡住本次提交。批量入口每个 ticket 都要走一遍，
        // 所以这里刻意只发一条 SQL。
        QueryWrapper<OrderDeliveryTicketMesRequest> wrapper = new QueryWrapper<>();
        wrapper.eq("request_status", STATUS_RESULT_UNKNOWN)
                .and(scope -> {
                    scope.eq("ticket_id", ticket.getId());
                    if (!keys.isEmpty()) {
                        scope.or().in("dedup_key", keys);
                    }
                })
                .orderByAsc("id");
        List<OrderDeliveryTicketMesRequest> unresolved = selectListOrEmpty(wrapper);
        if (unresolved.isEmpty()) {
            return List.of();
        }
        List<String> retired = new ArrayList<>();
        for (OrderDeliveryTicketMesRequest row : unresolved) {
            // 条件更新：同一时刻回调把它写成 SUCCESS 时命中 0 行，直接跳过，绝不覆盖真结果。
            UpdateWrapper<OrderDeliveryTicketMesRequest> updateWrapper = new UpdateWrapper<>();
            updateWrapper.eq("id", row.getId())
                    .eq("request_status", STATUS_RESULT_UNKNOWN);
            OrderDeliveryTicketMesRequest update = new OrderDeliveryTicketMesRequest();
            update.setRequestStatus(STATUS_STALE);
            update.setCompletedAt(LocalDateTime.now());
            update.setUpdatedAt(LocalDateTime.now());
            if (mesRequestMapper.update(update, updateWrapper) > 0) {
                retired.add(row.getMesRequestId());
            }
        }
        if (!retired.isEmpty()) {
            log.info("MES material unresolved requests retired ticketId={} bizIds={}", ticket.getId(), retired);
        }
        return retired;
    }

    private List<OrderDeliveryTicketMesRequest> selectListOrEmpty(QueryWrapper<OrderDeliveryTicketMesRequest> wrapper) {
        List<OrderDeliveryTicketMesRequest> rows = mesRequestMapper.selectList(wrapper);
        return rows == null ? List.of() : rows;
    }

    public DedupResolveResult followActiveMaster(OrderDeliveryTicket ticket, Long taskId, String dedupKey) {
        // 唯一冲突后重新关联胜者：只接受三字段键命中的请求。旧两字段键不含材质，
        // 无法证明是同一物料身份，宁可让调用方按失败处理，也不建错误的 follower。
        OrderDeliveryTicketMesRequest activeMaster = findActiveRequest(dedupKey);
        if (activeMaster == null) {
            throw new RuntimeException("Active MES master request not found for dedup key");
        }
        return createFollower(ticket, taskId, dedupKey, activeMaster);
    }

    public DedupResolveResult registerBatchFollower(
            OrderDeliveryTicket ticket,
            Long taskId,
            String dedupKey,
            Integer masterTicketId,
            String masterMesRequestId,
            String masterStatus) {
        if (isBlank(masterMesRequestId)) {
            throw new RuntimeException("Active MES master request not found for dedup key");
        }
        OrderDeliveryTicketMesRequest activeMaster = new OrderDeliveryTicketMesRequest();
        activeMaster.setTicketId(masterTicketId);
        activeMaster.setMesRequestId(masterMesRequestId);
        activeMaster.setRequestStatus(normalizeStatus(masterStatus));
        return createFollower(ticket, taskId, dedupKey, activeMaster);
    }

    public void markMasterCallbackPending(String masterMesRequestId, String externalTraceId, String message) {
        fanOut(masterMesRequestId, STATUS_CALLBACK_PENDING, null, message, externalTraceId);
    }

    public void completeMasterSuccess(String masterMesRequestId, String materialNo, String message, String externalTraceId) {
        if (isBlank(materialNo)) {
            return;
        }
        fanOut(masterMesRequestId, STATUS_SUCCESS, normalizeMaterialNo(materialNo), message, externalTraceId);
    }

    public void completeMasterFailed(String masterMesRequestId, String message) {
        fanOut(masterMesRequestId, STATUS_FAILED, null, message, null);
    }

    private void fanOut(
            String masterMesRequestId,
            String targetStatus,
            String materialNo,
            String message,
            String externalTraceId) {
        if (waiterMapper == null || ticketMapper == null || isBlank(masterMesRequestId)) {
            return;
        }
        try {
            List<OrderDeliveryTicketMesWaiter> waiters = loadActiveWaiters(masterMesRequestId);
            if (waiters == null || waiters.isEmpty()) {
                return;
            }
            int successCount = 0;
            int failedCount = 0;
            SourceSyncAccumulator sourceSync = new SourceSyncAccumulator();
            for (OrderDeliveryTicketMesWaiter waiter : waiters) {
                try {
                    sourceSync.add(updateWaiterFromMaster(
                            waiter, targetStatus, materialNo, message, externalTraceId));
                    successCount++;
                } catch (RuntimeException error) {
                    failedCount++;
                    log.error(
                            "MES material dedup fanout failed masterTicketId={} mesRequestId={} waiterTicketId={} reason={}",
                            waiter.getMasterTicketId(),
                            masterMesRequestId,
                            waiter.getWaiterTicketId(),
                            error.getMessage(),
                            error
                    );
                }
            }
            recordFanoutAudit(
                    waiters.get(0),
                    masterMesRequestId,
                    targetStatus,
                    materialNo,
                    waiters.size(),
                    successCount,
                    failedCount,
                    sourceSync);
            if (failedCount == 0) {
                log.info(
                        "MES material dedup fanout completed masterTicketId={} mesRequestId={} waiterCount={} "
                                + "materialNo={} sourceTargetCount={} sourceUpdatedCount={}",
                        waiters.get(0).getMasterTicketId(),
                        masterMesRequestId,
                        waiters.size(),
                        materialNo,
                        sourceSync.targetCount,
                        sourceSync.updatedCount
                );
            }
        } catch (RuntimeException error) {
            log.error(
                    "MES material dedup fanout failed masterTicketId={} mesRequestId={} waiterCount={} reason={}",
                    null,
                    masterMesRequestId,
                    0,
                    error.getMessage(),
                    error
            );
            recordFanoutFailureAudit(masterMesRequestId, targetStatus, materialNo, error);
        }
    }

    private List<OrderDeliveryTicketMesWaiter> loadActiveWaiters(String masterMesRequestId) {
        QueryWrapper<OrderDeliveryTicketMesWaiter> wrapper = new QueryWrapper<>();
        wrapper.eq("master_mes_request_id", masterMesRequestId)
                .in("wait_status", STATUS_WAITING, STATUS_PROCESSING, STATUS_CALLBACK_PENDING)
                .orderByAsc("id");
        return waiterMapper.selectList(wrapper);
    }

    private SourceItemMaterialNoSyncService.MaterialNoSyncResult updateWaiterFromMaster(
            OrderDeliveryTicketMesWaiter waiter,
            String targetStatus,
            String materialNo,
            String message,
            String externalTraceId) {
        if (waiter == null || waiter.getWaiterTicketId() == null) {
            return SourceItemMaterialNoSyncService.MaterialNoSyncResult.empty();
        }
        OrderDeliveryTicket follower = ticketMapper.selectById(waiter.getWaiterTicketId());
        if (follower == null) {
            throw new RuntimeException("Follower delivery ticket not found: " + waiter.getWaiterTicketId());
        }
        String followerMessage = fanoutMessage(targetStatus, message);
        SourceItemMaterialNoSyncService.MaterialNoSyncResult sourceSyncResult =
                SourceItemMaterialNoSyncService.MaterialNoSyncResult.empty();
        if (STATUS_SUCCESS.equals(targetStatus)) {
            // follower 的料号本来就该由主请求结果决定：允许覆盖它自己残留的旧值，
            // 否则写回会抛冲突，fan-out 静默失败、料号永远留在旧值上。
            sourceSyncResult = syncSourceItemMaterialNo(
                    follower, materialNo, followerMessage, true);
            if (productionAllocationLifecycleService != null) {
                // 同上：follower 提单行自己残留的旧料号也要能被主请求结果覆盖，
                // 否则这里会抛「confirmed material number changed」，fan-out 直接中断。
                productionAllocationLifecycleService.recordMaterialSuccess(
                        follower.getId(), materialNo, followerMessage, follower.getMesRequestId(),
                        externalTraceId, true, true);
            } else {
                OrderDeliveryTicket ticketUpdate = new OrderDeliveryTicket();
                ticketUpdate.setId(follower.getId());
                ticketUpdate.setMesSyncStatus(targetStatus);
                ticketUpdate.setMesSyncMessage(followerMessage);
                ticketUpdate.setMesExternalTaskId(trimToNull(externalTraceId));
                ticketUpdate.setMaterialNo(materialNo);
                ticketUpdate.setMesSyncedAt(LocalDateTime.now());
                ticketMapper.updateById(ticketUpdate);
            }
        } else {
            OrderDeliveryTicket ticketUpdate = new OrderDeliveryTicket();
            ticketUpdate.setId(follower.getId());
            ticketUpdate.setMesSyncStatus(targetStatus);
            ticketUpdate.setMesSyncMessage(followerMessage);
            ticketUpdate.setMesExternalTaskId(trimToNull(externalTraceId));
            ticketMapper.updateById(ticketUpdate);
        }
        OrderDeliveryTicketMesWaiter waiterUpdate = new OrderDeliveryTicketMesWaiter();
        waiterUpdate.setWaitStatus(targetStatus);
        waiterUpdate.setWaitMessage(followerMessage);
        waiterUpdate.setUpdatedAt(LocalDateTime.now());
        if (STATUS_SUCCESS.equals(targetStatus)) {
            waiterUpdate.setMaterialNo(materialNo);
            waiterUpdate.setCompletedAt(LocalDateTime.now());
        } else if (STATUS_FAILED.equals(targetStatus)) {
            waiterUpdate.setCompletedAt(LocalDateTime.now());
        }
        UpdateWrapper<OrderDeliveryTicketMesWaiter> wrapper = new UpdateWrapper<>();
        wrapper.eq("id", waiter.getId());
        waiterMapper.update(waiterUpdate, wrapper);
        return sourceSyncResult;
    }

    private SourceItemMaterialNoSyncService.MaterialNoSyncResult syncSourceItemMaterialNo(
            OrderDeliveryTicket ticket,
            String materialNo,
            String message,
            boolean allowOverwrite) {
        if (sourceItemMaterialNoSyncService == null) {
            return SourceItemMaterialNoSyncService.MaterialNoSyncResult.empty();
        }
        if (allowOverwrite) {
            return sourceItemMaterialNoSyncService.syncConfirmedMaterialNo(ticket, materialNo, message, true);
        }
        return sourceItemMaterialNoSyncService.syncConfirmedMaterialNo(ticket, materialNo, message);
    }

    private void recordFanoutAudit(
            OrderDeliveryTicketMesWaiter firstWaiter,
            String masterMesRequestId,
            String targetStatus,
            String materialNo,
            int waiterCount,
            int successCount,
            int failedCount,
            SourceSyncAccumulator sourceSync) {
        if (auditService == null || firstWaiter == null) {
            return;
        }
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("masterTicketId", firstWaiter.getMasterTicketId());
        summary.put("mesRequestId", masterMesRequestId);
        summary.put("dedupKey", firstWaiter.getDedupKey());
        summary.put("status", targetStatus);
        summary.put("waiterCount", waiterCount);
        summary.put("successCount", successCount);
        summary.put("failedCount", failedCount);
        appendSourceSyncSummary(summary, sourceSync);
        if (!isBlank(materialNo)) {
            summary.put("materialNo", materialNo);
        }
        String result = failedCount > 0 ? STATUS_FAILED : targetStatus;
        String errorMessage = failedCount > 0 ? "MES material dedup fanout partially failed" : null;
        try {
            auditService.record(
                    "DELIVERY_MES_MATERIAL_DEDUP_FANOUT",
                    "order_delivery_ticket",
                    firstWaiter.getMasterTicketId(),
                    null,
                    summary,
                    "DELIVERY",
                    result,
                    AuditService.labelForAction("DELIVERY_MES_MATERIAL_DEDUP_FANOUT")
                            + " masterTicketId=" + firstWaiter.getMasterTicketId()
                            + " mesRequestId=" + masterMesRequestId,
                    errorMessage
            );
        } catch (RuntimeException error) {
            log.error(
                    "MES material dedup fanout audit failed masterTicketId={} mesRequestId={} reason={}",
                    firstWaiter.getMasterTicketId(),
                    masterMesRequestId,
                    error.getMessage(),
                    error
            );
        }
    }

    private void recordFanoutFailureAudit(
            String masterMesRequestId,
            String targetStatus,
            String materialNo,
            RuntimeException error) {
        if (auditService == null) {
            return;
        }
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("mesRequestId", masterMesRequestId);
        summary.put("status", targetStatus);
        summary.put("waiterCount", 0);
        summary.put("failedCount", 1);
        appendSourceSyncSummary(summary, new SourceSyncAccumulator());
        if (!isBlank(materialNo)) {
            summary.put("materialNo", materialNo);
        }
        try {
            auditService.record(
                    "DELIVERY_MES_MATERIAL_DEDUP_FANOUT",
                    "order_delivery_ticket",
                    null,
                    null,
                    summary,
                    "DELIVERY",
                    STATUS_FAILED,
                    AuditService.labelForAction("DELIVERY_MES_MATERIAL_DEDUP_FANOUT")
                            + " mesRequestId=" + masterMesRequestId,
                    abbreviate(error == null ? null : error.getMessage())
            );
        } catch (RuntimeException auditError) {
            log.error(
                    "MES material dedup fanout failure audit failed mesRequestId={} reason={}",
                    masterMesRequestId,
                    auditError.getMessage(),
                    auditError
            );
        }
    }

    private String fanoutMessage(String targetStatus, String message) {
        if (!isBlank(message)) {
            return abbreviate(message);
        }
        if (STATUS_SUCCESS.equals(targetStatus)) {
            return "MES material dedup fanout resolved";
        }
        if (STATUS_CALLBACK_PENDING.equals(targetStatus)) {
            return "Follow active MES material request, waiting for callback";
        }
        if (STATUS_FAILED.equals(targetStatus)) {
            return "Master MES material request failed";
        }
        return "MES material dedup fanout updated";
    }

    /**
     * 按身份键查活动请求。
     *
     * <p>刻意不按 {@code request_role} 过滤：刷新料号写入的台账行没有角色，
     * 若保留 MASTER 过滤，批量入口就看不见刷新请求，共享身份保护等于没做。
     * 角色差异改由调用方分支处理（批量主请求转 follower，其余只返回在途状态）。</p>
     *
     * <p>调用方必须明确自己查的是哪一类键：三字段键可以安全建立 follower，
     * 旧两字段键不含材质、只能回在途状态。不要在这里混着查——混查会让调用方
     * 无法判断命中的键是否足以证明物料身份。</p>
     */
    private OrderDeliveryTicketMesRequest findActiveRequest(String... dedupKeys) {
        if (mesRequestMapper == null || dedupKeys == null) {
            return null;
        }
        List<String> keys = new ArrayList<>();
        for (String key : dedupKeys) {
            if (!isBlank(key)) {
                keys.add(key);
            }
        }
        if (keys.isEmpty()) {
            return null;
        }
        QueryWrapper<OrderDeliveryTicketMesRequest> wrapper = new QueryWrapper<>();
        wrapper.in("dedup_key", keys)
                .in("request_status", STATUS_PROCESSING, STATUS_CALLBACK_PENDING, STATUS_RESULT_UNKNOWN)
                .orderByDesc("id")
                .last("LIMIT 1");
        return mesRequestMapper.selectOne(wrapper);
    }

    /**
     * 同一提单自身的在途请求。身份键在 V71 回填后与本提单一致，但材质被编辑过时
     * 键会不同；此时仍必须拦住重复提交，所以额外按 ticket 查一次。
     */
    private OrderDeliveryTicketMesRequest findActiveRequestByTicketId(Integer ticketId) {
        if (mesRequestMapper == null || ticketId == null) {
            return null;
        }
        QueryWrapper<OrderDeliveryTicketMesRequest> wrapper = new QueryWrapper<>();
        wrapper.eq("ticket_id", ticketId)
                .in("request_status", STATUS_PROCESSING, STATUS_CALLBACK_PENDING, STATUS_RESULT_UNKNOWN)
                .orderByDesc("id")
                .last("LIMIT 1");
        return mesRequestMapper.selectOne(wrapper);
    }

    private boolean isSameTicket(OrderDeliveryTicket ticket, OrderDeliveryTicketMesRequest active) {
        return ticket != null
                && ticket.getId() != null
                && ticket.getId().equals(active.getTicketId());
    }

    private DedupResolveResult createFollower(
            OrderDeliveryTicket ticket,
            Long taskId,
            String dedupKey,
            OrderDeliveryTicketMesRequest activeMaster) {
        String masterStatus = normalizeStatus(activeMaster.getRequestStatus());
        // waiter 表的历史约束只允许 WAITING/CALLBACK_PENDING/SUCCESS/FAILED；
        // RESULT_UNKNOWN 要保留在 follower 提单行上，但 waiter 本身继续以 WAITING
        // 活跃，待原 bizId 核对完成后再由主请求 fan-out 结束等待关系。
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
        waiter.setWaitMessage("Follow active MES material request for same part/version/material");
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
        update.setMesSyncMessage("Follow active MES material request for same part/version/material");
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

    private static String normalizeKeyPart(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }

    /**
     * 材质只做 trim，不做大小写折叠。
     *
     * <p>出站请求的 material 也是 trim 后的原大小写；在确认 MES 是否区分材质大小写之前，
     * QPO 自行折叠会造成「本地认为同一身份、MES 认为是两个任务」的错配。</p>
     */
    private static String normalizeMaterialKeyPart(String value) {
        return value == null ? "" : value.trim();
    }

    private String normalizeRole(String value) {
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
        if (STATUS_CALLBACK_PENDING.equals(status)) {
            return STATUS_CALLBACK_PENDING;
        }
        if (STATUS_RESULT_UNKNOWN.equals(status)) {
            return STATUS_RESULT_UNKNOWN;
        }
        return STATUS_PROCESSING;
    }

    private String trimToNull(String value) {
        return isBlank(value) ? null : value.trim();
    }

    private String abbreviate(String value) {
        String text = value == null ? "" : value.trim();
        return text.length() <= 500 ? text : text.substring(0, 500);
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private void appendSourceSyncSummary(Map<String, Object> summary, SourceSyncAccumulator sourceSync) {
        SourceSyncAccumulator result = sourceSync == null ? new SourceSyncAccumulator() : sourceSync;
        summary.put("sourceSyncTargetCount", result.targetCount);
        summary.put("sourceSyncUpdatedCount", result.updatedCount);
        summary.put("sourceSyncIdempotentCount", result.idempotentCount);
        summary.put("rfqSourceUpdatedCount", result.rfqUpdatedCount);
        summary.put("salesOrderSourceUpdatedCount", result.salesOrderUpdatedCount);
    }

    private static final class SourceSyncAccumulator {
        private int targetCount;
        private int updatedCount;
        private int idempotentCount;
        private int rfqUpdatedCount;
        private int salesOrderUpdatedCount;

        private void add(SourceItemMaterialNoSyncService.MaterialNoSyncResult result) {
            if (result == null) {
                return;
            }
            targetCount += result.targetCount();
            updatedCount += result.updatedCount();
            idempotentCount += result.idempotentCount();
            rfqUpdatedCount += result.rfqUpdatedCount();
            salesOrderUpdatedCount += result.salesOrderUpdatedCount();
        }
    }

    public static class DedupResolveResult {
        private final String type;
        private final String dedupKey;
        private final Integer masterTicketId;
        private final String masterMesRequestId;
        private final String status;

        private DedupResolveResult(
                String type,
                String dedupKey,
                Integer masterTicketId,
                String masterMesRequestId,
                String status) {
            this.type = type;
            this.dedupKey = dedupKey;
            this.masterTicketId = masterTicketId;
            this.masterMesRequestId = masterMesRequestId;
            this.status = status;
        }

        public static DedupResolveResult master(String dedupKey) {
            return new DedupResolveResult(RESULT_MASTER_CREATED, dedupKey, null, null, STATUS_PROCESSING);
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
                    status
            );
        }

        public static DedupResolveResult activeRequestExists(
                String dedupKey,
                String mesRequestId,
                String status) {
            return new DedupResolveResult(
                    RESULT_ACTIVE_REQUEST_EXISTS,
                    dedupKey,
                    null,
                    mesRequestId,
                    status
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

        public boolean isFollowExistingActive() {
            return RESULT_FOLLOW_EXISTING_ACTIVE.equals(type);
        }

        public boolean isActiveRequestExists() {
            return RESULT_ACTIVE_REQUEST_EXISTS.equals(type);
        }
    }
}
