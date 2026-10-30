package com.machining.quote.service.mes;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.machining.quote.dto.ReleaseSourceType;
import com.machining.quote.dto.mes.MesMaterialCallbackReq;
import com.machining.quote.dto.mes.MesMaterialCallbackResp;
import com.machining.quote.dto.mes.MesMaterialResolveReq;
import com.machining.quote.dto.mes.MesMaterialResolveResp;
import com.machining.quote.entity.MesIntegrationBatch;
import com.machining.quote.entity.MesMaterialBatchItem;
import com.machining.quote.entity.ItemQuoteVersion;
import com.machining.quote.entity.OrderDeliveryTicket;
import com.machining.quote.entity.OrderDeliveryTicketMesRequest;
import com.machining.quote.entity.OrderRfq;
import com.machining.quote.entity.SalesOrderHeader;
import com.machining.quote.entity.SalesOrderItem;
import com.machining.quote.mapper.ItemQuoteVersionMapper;
import com.machining.quote.mapper.MesIntegrationBatchMapper;
import com.machining.quote.mapper.MesMaterialBatchItemMapper;
import com.machining.quote.mapper.OrderDeliveryTicketMapper;
import com.machining.quote.mapper.OrderDeliveryTicketMesRequestMapper;
import com.machining.quote.mapper.OrderRfqMapper;
import com.machining.quote.mapper.SalesOrderHeaderMapper;
import com.machining.quote.mapper.SalesOrderItemMapper;
import com.machining.quote.service.AuditService;
import com.machining.quote.service.DataCenterCallException;
import com.machining.quote.service.SourceItemMaterialNoSyncService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.http.converter.HttpMessageNotReadableException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.math.BigInteger;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class MesMaterialSyncService {
    private static final Logger log = LoggerFactory.getLogger(MesMaterialSyncService.class);
    private static final String STATUS_PROCESSING = "PROCESSING";
    private static final String STATUS_CALLBACK_PENDING = "CALLBACK_PENDING";
    private static final String STATUS_SUCCESS = "SUCCESS";
    private static final String STATUS_FAILED = "FAILED";
    private static final String STATUS_RESULT_UNKNOWN = "RESULT_UNKNOWN";
    private static final String BATCH_STATUS_PARTIAL_PENDING = "PARTIAL_PENDING";
    private static final int MATERIAL_CALLBACK_MAX_ITEMS = 100;
    /**
     * 单批次明细上限，必须 ≤ 回调上限。
     *
     * <p>MES 可能把整批结果一次回调回来。若一批超过回调上限，那次回调会被我们以
     * 400 拒收，MES 反复重试也进不来，这批行就永远停在 {@code CALLBACK_PENDING}
     * ——而 {@code assertCanStart} 又会拦住重试，只能人工改库。</p>
     */
    private static final int MATERIAL_BATCH_MAX_ITEMS = MATERIAL_CALLBACK_MAX_ITEMS;
    /*
     * MES 物料接口的错误码，统一存纯数字形态。MES 同一个码在两种格式间摇摆
     * （1_030_900_002 与 1030900008），比较前先去掉非数字，否则换一种写法就漏判。
     *
     * 契约（MES 2026-10-30 确认）分两层：
     *   1. 行级状态：code=0，items 逐行带 status，EXISTING_PRODUCT 直接取料号、PENDING_CREATE 等回调，
     *      一行「已存在」不影响同批其它行。
     *   2. 批级回滚：非 0 code + data=null，MES 侧 @Transactional 整包回滚，一条都没建。
     * 因此 002/003 之外的非 0 code 都意味着「什么都没发生」，重发不会造重复任务。
     */
    /** 旧契约的「已存在物料准备任务」批级码（新契约已改为行状态 EXISTING_PRODUCT）。 */
    private static final String MES_CODE_MATERIAL_EXISTS = "1030900002";
    /** 旧契约的「已有处理中的物料准备任务」批级码。 */
    private static final String MES_CODE_MATERIAL_PROCESSING = "1030900003";
    /** 某行命中已有任务、仍在待建料：整批回滚。 */
    private static final String MES_CODE_MATERIAL_TASK_PENDING = "1030900008";
    /** 同一包内两条明细图号+版本+材质重复：整批回滚。 */
    private static final String MES_CODE_BATCH_DUPLICATE_ITEM = "1030900011";
    /** 某行图纸转存失败：整批回滚。 */
    private static final String MES_CODE_DRAWING_TRANSFER_FAILED = "1030900012";
    private static final DateTimeFormatter BIZ_DATE = DateTimeFormatter.BASIC_ISO_DATE;
    private static final char[] CROCKFORD32 = "0123456789ABCDEFGHJKMNPQRSTVWXYZ".toCharArray();
    private static final SecureRandom RANDOM = new SecureRandom();
    private final MesMaterialRequestAssembler batchRequestAssembler = new MesMaterialRequestAssembler();
    private final MesBatchFingerprintService batchFingerprintService = new MesBatchFingerprintService();

    @Autowired
    private OrderDeliveryTicketMapper ticketMapper;
    @Autowired(required = false)
    private OrderDeliveryTicketMesRequestMapper mesRequestMapper;
    @Autowired(required = false)
    private MesBatchStateService mesBatchStateService;
    @Autowired(required = false)
    private MesIntegrationBatchMapper mesIntegrationBatchMapper;
    @Autowired(required = false)
    private MesMaterialBatchItemMapper mesMaterialBatchItemMapper;
    @Autowired(required = false)
    private OrderRfqMapper orderRfqMapper;
    @Autowired(required = false)
    private ItemQuoteVersionMapper itemMapper;
    @Autowired(required = false)
    private SalesOrderItemMapper salesOrderItemMapper;
    @Autowired(required = false)
    private SalesOrderHeaderMapper salesOrderHeaderMapper;
    @Autowired
    private MesMaterialClient mesMaterialClient;
    @Autowired(required = false)
    private AuditService auditService;
    @Autowired(required = false)
    private MesMaterialDedupService dedupService;
    @Autowired(required = false)
    private ProductionAllocationLifecycleService productionAllocationLifecycleService;
    @Autowired
    private SourceItemMaterialNoSyncService sourceItemMaterialNoSyncService;

    public MesMaterialCallbackResp handleMaterialCallback(MesMaterialCallbackReq req) {
        long startedAt = System.currentTimeMillis();
        String mesRequestId = trimToNull(req == null ? null : req.getMesRequestId());
        if (mesRequestId == null) {
            throw callbackError(HttpStatus.BAD_REQUEST, "MES 请求ID不能为空");
        }
        String externalTraceId = trimToNull(req.getExternalTraceId());
        log.info("MES material callback received mesRequestId={} externalTraceId={}",
                mesRequestId, externalTraceId);
        OrderDeliveryTicketMesRequest request = loadMesRequest(mesRequestId);
        Integer ticketId = request.getTicketId();
        if (ticketId == null) {
            recordCallbackAudit("DELIVERY_MES_MATERIAL_CALLBACK_FAILED", null, request, req, STATUS_FAILED,
                    "MES 请求流水缺少提单行ID");
            throw callbackError(HttpStatus.INTERNAL_SERVER_ERROR, "MES 请求流水缺少提单行ID");
        }
        OrderDeliveryTicket ticket = loadTicket(ticketId);
        if (!mesRequestId.equals(trimToNull(ticket.getMesRequestId()))) {
            if (isRefreshMaterialRequest(request)
                    && STATUS_RESULT_UNKNOWN.equalsIgnoreCase(trimToNull(request.getRequestStatus()))) {
                recordCallbackAudit(
                        "DELIVERY_MES_MATERIAL_CALLBACK_CONFLICT",
                        ticket,
                        request,
                        req,
                        STATUS_RESULT_UNKNOWN,
                        "料号刷新请求结果仍待核对；回调不会自动覆盖已确认料号");
                throw callbackError(HttpStatus.CONFLICT,
                        "料号刷新请求结果仍待核对，请按原 bizId 核对后再处理");
            }
            updateMesRequestFromCallback(mesRequestId, "STALE", null, externalTraceId, "旧 MES 回调已拒绝", false);
            recordCallbackAudit("DELIVERY_MES_MATERIAL_CALLBACK_CONFLICT", ticket, refreshRequestSnapshot(request), req, STATUS_FAILED,
                    "旧 MES 回调不能覆盖当前请求");
            throw callbackError(HttpStatus.CONFLICT, "旧 MES 回调不能覆盖当前请求");
        }
        String materialNo = normalizeMaterialNo(req.getMaterialNo());
        if (!STATUS_SUCCESS.equalsIgnoreCase(trimToNull(req.getStatus()))) {
            markFailed(ticket, mesRequestId, isBlank(req.getMessage()) ? "MES 回调失败" : req.getMessage());
            updateMesRequestFromCallback(mesRequestId, STATUS_FAILED, null, externalTraceId, req.getMessage(), true);
            if (dedupService != null) {
                dedupService.completeMasterFailed(mesRequestId, req.getMessage());
            }
            recordCallbackAudit("DELIVERY_MES_MATERIAL_CALLBACK_FAILED", ticket, refreshRequestSnapshot(request), req, STATUS_FAILED, req.getMessage());
            return callbackResponse(false, "MES 回调失败", mesRequestId, null);
        }
        if (isBlank(materialNo)) {
            throw callbackError(HttpStatus.BAD_REQUEST, "MES 回调物料号不能为空");
        }
        if (STATUS_SUCCESS.equalsIgnoreCase(ticket.getMesSyncStatus()) && !isBlank(ticket.getMaterialNo())) {
            if (materialNo.equals(ticket.getMaterialNo().trim())) {
                SourceItemMaterialNoSyncService.MaterialNoSyncResult sourceSyncResult =
                        syncSourceItemMaterialNoForCallback(
                                ticket, materialNo, req.getMessage(), request, req, false);
                if (dedupService != null) {
                    dedupService.completeMasterSuccess(mesRequestId, materialNo, req.getMessage(), externalTraceId);
                }
                recordCallbackAudit(
                        "DELIVERY_MES_MATERIAL_CALLBACK_SUCCESS",
                        ticket,
                        refreshRequestSnapshot(request),
                        req,
                        STATUS_SUCCESS,
                        null,
                        sourceSyncResult);
                return callbackResponse(true, "MES 回调已处理", mesRequestId, materialNo);
            }
            recordCallbackAudit("DELIVERY_MES_MATERIAL_CALLBACK_CONFLICT", ticket, request, req, STATUS_FAILED,
                    "MES 回调物料号冲突");
            throw callbackError(HttpStatus.CONFLICT, "MES 回调物料号冲突，不能覆盖已确认物料号");
        }
        SourceItemMaterialNoSyncService.MaterialNoSyncResult sourceSyncResult =
                syncSourceItemMaterialNoForCallback(
                        ticket, materialNo, req.getMessage(), request, req, false);
        markCallbackSuccess(ticket, mesRequestId, materialNo, req.getMessage(), externalTraceId);
        updateMesRequestFromCallback(mesRequestId, STATUS_SUCCESS, materialNo, externalTraceId, req.getMessage(), true);
        if (dedupService != null) {
            dedupService.completeMasterSuccess(mesRequestId, materialNo, req.getMessage(), externalTraceId);
        }
        recordCallbackAudit(
                "DELIVERY_MES_MATERIAL_CALLBACK_SUCCESS",
                ticket,
                request,
                req,
                STATUS_SUCCESS,
                null,
                sourceSyncResult);
        log.info("MES material callback completed ticketId={} mesRequestId={} materialNo={} durationMs={}",
                ticketId, mesRequestId, materialNo, System.currentTimeMillis() - startedAt);
        return callbackResponse(true, "QPO已接收物料号回调", mesRequestId, materialNo);
    }

    private boolean isRefreshMaterialRequest(OrderDeliveryTicketMesRequest request) {
        return request != null
                && request.getRequestMessage() != null
                && request.getRequestMessage().startsWith(ProductionItemMaterialNoRefreshService.REQUEST_MESSAGE_PREFIX);
    }

    @Transactional
    public MesMaterialCallbackResp handleMaterialBatchCallback(MesMaterialCallbackReq req) {
        long startedAt = System.currentTimeMillis();
        MaterialBatchCallbackEnvelope envelope = validateMaterialBatchCallbackRequest(req);
        if (mesIntegrationBatchMapper == null || mesMaterialBatchItemMapper == null || mesRequestMapper == null) {
            throw callbackError(HttpStatus.INTERNAL_SERVER_ERROR, "MES material batch callback service is unavailable");
        }
        MesIntegrationBatch batch = loadMaterialBatch(envelope.bizId());
        List<MesMaterialBatchItem> batchItems = loadMaterialBatchItems(batch);
        BatchItemIndex itemIndex = indexBatchItems(batchItems);
        List<MaterialBatchCallbackApplication> applications = new ArrayList<>();
        Map<Long, String> projectedStatuses = projectedStatusMap(batchItems);
        int duplicateCount = 0;

        for (MaterialBatchCallbackRow row : envelope.rows()) {
            MesMaterialBatchItem batchItem = resolveBatchItem(itemIndex, row);
            OrderDeliveryTicketMesRequest request = loadMesRequest(batchItem.getLineRequestId());
            OrderDeliveryTicket ticket = loadTicket(batchItem.getTicketId());
            validateMaterialBatchCallbackAssociation(batch, batchItem, request, ticket, row);
            // 必须先校验完 bizId / taskNo / 行归属，再判断「是否已有料号」：
            // 反过来会把 taskNo 不匹配的脏回调静默当成幂等成功吞掉。
            boolean superseded = isSupersededLineRequest(batchItem, ticket);
            validateCurrentMaterialCallbackRequest(batchItem, request, ticket, row);
            BatchCallbackDisposition disposition = disposition(batchItem, row, ticket, superseded);
            if (disposition.duplicate()) {
                duplicateCount++;
                continue;
            }
            applications.add(new MaterialBatchCallbackApplication(
                    row, batchItem, request, ticket,
                    disposition.reviveFailed(), disposition.overwrite(), disposition.previousMaterialNo()));
            projectedStatuses.put(batchItem.getId(), row.success() ? STATUS_SUCCESS : STATUS_FAILED);
        }

        SourceSyncAccumulator sourceSync = new SourceSyncAccumulator();
        for (MaterialBatchCallbackApplication application : applications) {
            sourceSync.add(applyMaterialBatchCallbackRow(application));
        }
        if (!applications.isEmpty()) {
            updateMaterialBatchAfterCallback(batch, projectedStatuses);
        }
        log.info(
                "MES material batch callback accepted bizId={} receivedCount={} appliedCount={} duplicateCount={} "
                        + "sourceTargetCount={} sourceUpdatedCount={} durationMs={}",
                envelope.bizId(), envelope.rows().size(), applications.size(), duplicateCount,
                sourceSync.targetCount, sourceSync.updatedCount, System.currentTimeMillis() - startedAt);
        return batchCallbackResponse(
                true,
                0,
                "accepted",
                envelope.bizId(),
                envelope.rows().size(),
                applications.size(),
                duplicateCount,
                sourceSync);
    }

    private MaterialBatchCallbackEnvelope validateMaterialBatchCallbackRequest(MesMaterialCallbackReq req) {
        String bizId = trimToNull(req == null ? null : req.getMesRequestId());
        if (bizId == null) {
            throw callbackError(HttpStatus.BAD_REQUEST, "MES material callback bizId cannot be blank");
        }
        if (bizId.length() > 80) {
            throw callbackError(HttpStatus.BAD_REQUEST, "MES material callback bizId is too long");
        }
        List<MesMaterialCallbackReq.Item> items = req.getItems();
        if (items == null || items.isEmpty()) {
            throw callbackError(HttpStatus.BAD_REQUEST, "MES material callback items cannot be empty");
        }
        if (items.size() > MATERIAL_CALLBACK_MAX_ITEMS) {
            throw callbackError(HttpStatus.BAD_REQUEST, "MES material callback items exceed 100 rows");
        }
        List<MaterialBatchCallbackRow> rows = new ArrayList<>();
        Set<String> callbackTaskNos = new HashSet<>();
        Set<String> callbackKeys = new HashSet<>();
        for (int index = 0; index < items.size(); index++) {
            MesMaterialCallbackReq.Item item = items.get(index);
            if (item == null) {
                throw callbackError(HttpStatus.BAD_REQUEST, "MES material callback item cannot be null");
            }
            String taskNo = requireCallbackText(item.getTaskNo(), 120, "items[" + index + "].taskNo");
            String drawingNo = requireCallbackText(item.getDrawingNo(), 128, "items[" + index + "].drawingNo");
            String drawingVersion = optionalCallbackText(item.getDrawingVersion(), 64, "items[" + index + "].drawingVersion");
            String status = requireCallbackStatus(item.getStatus(), index);
            String productCode = optionalCallbackText(item.getProductCode(), 100, "items[" + index + "].productCode");
            String productName = optionalCallbackText(item.getProductName(), 255, "items[" + index + "].productName");
            String message = optionalCallbackText(item.getMessage(), 500, "items[" + index + "].message");
            if (STATUS_SUCCESS.equals(status) && isBlank(productCode)) {
                throw callbackError(HttpStatus.BAD_REQUEST, "items[" + index + "].productCode cannot be blank when status is SUCCESS");
            }
            if (STATUS_FAILED.equals(status) && !isBlank(productCode)) {
                throw callbackError(HttpStatus.BAD_REQUEST, "items[" + index + "].productCode must be blank when status is FAILED");
            }
            if (STATUS_FAILED.equals(status) && isBlank(message)) {
                throw callbackError(HttpStatus.BAD_REQUEST, "items[" + index + "].message cannot be blank when status is FAILED");
            }
            // taskNo 是 callback 定位批次行的唯一键，重复即无法判定归属，必须显式拒绝。
            if (!callbackTaskNos.add(taskNo)) {
                throw callbackError(HttpStatus.BAD_REQUEST, "MES material callback contains duplicate taskNo");
            }
            String itemKey = materialItemKey(drawingNo, drawingVersion);
            if (!callbackKeys.add(itemKey)) {
                throw callbackError(HttpStatus.BAD_REQUEST, "MES material callback contains duplicate drawing/version");
            }
            rows.add(new MaterialBatchCallbackRow(
                    index, taskNo, drawingNo, drawingVersion, status, productCode, productName, message, itemKey));
        }
        return new MaterialBatchCallbackEnvelope(bizId, List.copyOf(rows));
    }

    private String requireCallbackText(String value, int maxLength, String field) {
        String text = trimToNull(value);
        if (text == null) {
            throw callbackError(HttpStatus.BAD_REQUEST, field + " cannot be blank");
        }
        if (text.length() > maxLength) {
            throw callbackError(HttpStatus.BAD_REQUEST, field + " is too long");
        }
        return text;
    }

    private String optionalCallbackText(String value, int maxLength, String field) {
        String text = trimToNull(value);
        if (text != null && text.length() > maxLength) {
            throw callbackError(HttpStatus.BAD_REQUEST, field + " is too long");
        }
        return text;
    }

    private String requireCallbackStatus(String value, int index) {
        String status = trimToNull(value);
        if (status == null) {
            throw callbackError(HttpStatus.BAD_REQUEST, "items[" + index + "].status cannot be blank");
        }
        status = status.toUpperCase(Locale.ROOT);
        if (!STATUS_SUCCESS.equals(status) && !STATUS_FAILED.equals(status)) {
            throw callbackError(HttpStatus.BAD_REQUEST, "items[" + index + "].status must be SUCCESS or FAILED");
        }
        return status;
    }

    private MesIntegrationBatch loadMaterialBatch(String bizId) {
        QueryWrapper<MesIntegrationBatch> wrapper = new QueryWrapper<>();
        wrapper.eq("biz_id", bizId)
                .eq("batch_type", MesBatchStateService.BATCH_TYPE_MATERIAL);
        MesIntegrationBatch batch = mesIntegrationBatchMapper.selectOne(wrapper);
        if (batch == null) {
            throw callbackError(HttpStatus.NOT_FOUND, "MES material batch bizId does not exist");
        }
        return batch;
    }

    private List<MesMaterialBatchItem> loadMaterialBatchItems(MesIntegrationBatch batch) {
        QueryWrapper<MesMaterialBatchItem> wrapper = new QueryWrapper<>();
        wrapper.eq("batch_id", batch.getId()).orderByAsc("id");
        List<MesMaterialBatchItem> items = mesMaterialBatchItemMapper.selectList(wrapper);
        if (items == null || items.isEmpty()) {
            throw callbackError(HttpStatus.CONFLICT, "MES material batch has no QPO rows");
        }
        return items;
    }

    /**
     * 批次行索引：taskNo 是 callback 的定位键，图号+版本键只用于旧批次兼容。
     */
    private BatchItemIndex indexBatchItems(List<MesMaterialBatchItem> items) {
        Map<String, MesMaterialBatchItem> byTaskNo = new LinkedHashMap<>();
        Set<String> duplicateTaskNos = new HashSet<>();
        Map<String, List<MesMaterialBatchItem>> byItemKey = new LinkedHashMap<>();
        for (MesMaterialBatchItem item : items) {
            String taskNo = trimToNull(item.getMesTaskNo());
            if (taskNo != null && byTaskNo.putIfAbsent(taskNo, item) != null) {
                duplicateTaskNos.add(taskNo);
            }
            byItemKey.computeIfAbsent(batchItemKey(item), ignored -> new ArrayList<>()).add(item);
        }
        return new BatchItemIndex(byTaskNo, duplicateTaskNos, byItemKey);
    }

    private String batchItemKey(MesMaterialBatchItem item) {
        String key = trimToNull(item.getItemKey());
        return key == null ? materialItemKey(item.getDrawingNo(), item.getDrawingVersion()) : key;
    }

    /**
     * 先按 taskNo 定位批次行，再用图号/版本交叉校验。
     *
     * <p>旧批次（没有 mes_task_no）走兼容路径：仅当图号+版本候选行唯一、
     * 且该行确实还没有 taskNo 时才匹配；有歧义就安全拒绝，不猜。
     * 该唯一性由 uk_mes_material_batch_key (batch_id, item_key) 保证。</p>
     */
    private MesMaterialBatchItem resolveBatchItem(BatchItemIndex index, MaterialBatchCallbackRow row) {
        String taskNo = trimToNull(row.taskNo());
        if (taskNo != null && index.duplicateTaskNos().contains(taskNo)) {
            throw callbackError(HttpStatus.CONFLICT,
                    "MES material batch contains duplicate taskNo; the row cannot be located");
        }
        MesMaterialBatchItem byTaskNo = taskNo == null ? null : index.byTaskNo().get(taskNo);
        if (byTaskNo != null) {
            if (!row.itemKey().equals(batchItemKey(byTaskNo))) {
                throw callbackError(HttpStatus.CONFLICT,
                        "MES material callback drawing/version does not match the batch row of this taskNo");
            }
            return byTaskNo;
        }
        List<MesMaterialBatchItem> candidates = index.byItemKey().getOrDefault(row.itemKey(), List.of());
        if (candidates.isEmpty()) {
            throw callbackError(HttpStatus.CONFLICT, "MES material callback taskNo does not belong to this batch");
        }
        if (candidates.size() > 1) {
            throw callbackError(HttpStatus.CONFLICT,
                    "MES material callback drawing/version is ambiguous inside this batch");
        }
        MesMaterialBatchItem candidate = candidates.get(0);
        String savedTaskNo = trimToNull(candidate.getMesTaskNo());
        if (savedTaskNo != null && !savedTaskNo.equals(taskNo)) {
            throw callbackError(HttpStatus.CONFLICT, "MES material callback taskNo mismatch");
        }
        return candidate;
    }

    private Map<Long, String> projectedStatusMap(List<MesMaterialBatchItem> items) {
        Map<Long, String> result = new LinkedHashMap<>();
        for (MesMaterialBatchItem item : items) {
            result.put(item.getId(), normalizeCallbackExistingStatus(item.getStatus()));
        }
        return result;
    }

    private void validateMaterialBatchCallbackAssociation(
            MesIntegrationBatch batch,
            MesMaterialBatchItem batchItem,
            OrderDeliveryTicketMesRequest request,
            OrderDeliveryTicket ticket,
            MaterialBatchCallbackRow row) {
        if (batchItem.getBatchId() == null || !batchItem.getBatchId().equals(batch.getId())) {
            throw callbackError(HttpStatus.CONFLICT, "MES material callback row belongs to another batch");
        }
        if (request.getBatchId() != null && !request.getBatchId().equals(batch.getId())) {
            throw callbackError(HttpStatus.CONFLICT, "MES material callback request belongs to another batch");
        }
        if (request.getBatchItemId() != null && !request.getBatchItemId().equals(batchItem.getId())) {
            throw callbackError(HttpStatus.CONFLICT, "MES material callback request belongs to another batch item");
        }
        if (request.getTicketId() == null || !request.getTicketId().equals(batchItem.getTicketId())) {
            throw callbackError(HttpStatus.CONFLICT, "MES material callback request ticket mismatch");
        }
    }

    /**
     * 批次回调只校验「这条回调是否属于本批次行记录的那个 MES 任务」，不再比对 lineRequestId。
     *
     * 原实现要求 batchItem.lineRequestId == ticket.mesRequestId，但 mes_request_id 是提单上的单值字段：
     * 同一张提单被重新下发（或重发失败）时会被新批次行的 lineRequestId 覆盖，于是旧批次的真实回调
     * 会被判为 stale 永久拒绝。MES 对同一「图号+版本+材质」只允许一个物料准备任务，重发必然被拒，
     * 一旦发生「A 失败 → 重发 B 被 MES 拒绝 → A 的成功回调到达」，料号就再也拿不回来。
     *
     * 提单还没有确认料号时接受 MES 的料号不存在「旧结果覆盖新结果」的风险，MES 是料号的权威来源。
     * 提单已有确认料号时，是否允许覆盖由 {@link #disposition} 按「是否已被更新的有效请求取代」判断。
     * 定位链条已保证不会串行：批次行必须属于本批次、请求流水必须指向本批次行、
     * request.ticketId 必须等于 batchItem.ticketId。
     *
     * taskNo 校验保留：它才是「这条回调确实对应本行那个 MES 任务」的证据。
     */
    private void validateCurrentMaterialCallbackRequest(
            MesMaterialBatchItem batchItem,
            OrderDeliveryTicketMesRequest request,
            OrderDeliveryTicket ticket,
            MaterialBatchCallbackRow row) {
        String savedTaskNo = firstNonBlank(batchItem.getMesTaskNo(), request.getMesExternalTaskId());
        if (!isBlank(savedTaskNo) && !savedTaskNo.equals(row.taskNo())) {
            throw callbackError(HttpStatus.CONFLICT, "MES material callback taskNo mismatch");
        }
        String currentLineRequestId = trimToNull(ticket.getMesRequestId());
        if (currentLineRequestId != null && !currentLineRequestId.equals(batchItem.getLineRequestId())) {
            log.info("MES material callback accepted for superseded line request batchItemId={} ticketId={} "
                            + "batchLineRequestId={} currentTicketRequestId={} taskNo={} productCode={}",
                    batchItem.getId(), ticket.getId(), batchItem.getLineRequestId(),
                    currentLineRequestId, row.taskNo(), row.productCode());
        }
    }

    /**
     * 记录「有效 MES 结果覆盖旧料号」。旧值、新值和请求标识必须同时留痕，
     * 否则事后无法判断料号是何时被改的。
     */
    private void recordMaterialOverwriteAudit(
            OrderDeliveryTicket ticket,
            OrderDeliveryTicketMesRequest request,
            MaterialBatchCallbackRow row,
            String previousMaterialNo) {
        if (auditService == null) {
            return;
        }
        Integer ticketId = ticket == null ? null : ticket.getId();
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("ticketId", ticketId);
        summary.put("mesRequestId", request == null ? null : request.getMesRequestId());
        summary.put("taskNo", row.taskNo());
        summary.put("status", STATUS_SUCCESS);
        summary.put("previousMaterialNo", previousMaterialNo);
        summary.put("materialNo", row.productCode());
        if (ticket != null) {
            summary.put("partNo", ticket.getPartNo());
            summary.put("version", ticket.getVersion());
            summary.put("sourceType", ticket.getSourceType());
            summary.put("sourceOrderId", ticket.getSourceOrderId());
            summary.put("sourceItemId", ticket.getSourceItemId());
        }
        auditService.record(
                "DELIVERY_MES_MATERIAL_CALLBACK_OVERWRITE",
                "order_delivery_ticket",
                ticketId,
                null,
                summary,
                "DELIVERY",
                STATUS_SUCCESS,
                AuditService.labelForAction("DELIVERY_MES_MATERIAL_CALLBACK_OVERWRITE")
                        + " ticketId=" + ticketId
                        + " previousMaterialNo=" + previousMaterialNo
                        + " materialNo=" + row.productCode(),
                null
        );
    }

    private boolean isSupersededLineRequest(MesMaterialBatchItem batchItem, OrderDeliveryTicket ticket) {
        String currentLineRequestId = trimToNull(ticket == null ? null : ticket.getMesRequestId());
        return currentLineRequestId != null && !currentLineRequestId.equals(batchItem.getLineRequestId());
    }

    private BatchCallbackDisposition disposition(MesMaterialBatchItem batchItem,
                                                 MaterialBatchCallbackRow row,
                                                 OrderDeliveryTicket ticket,
                                                 boolean superseded) {
        String currentStatus = normalizeCallbackExistingStatus(batchItem.getStatus());
        String currentProductCode = normalizeMaterialNo(batchItem.getProductCode());
        String confirmedMaterialNo = normalizeMaterialNo(ticket == null ? null : ticket.getMaterialNo());
        boolean ticketConfirmed = !isBlank(confirmedMaterialNo);

        if (!row.success()) {
            // 失败回调永远不降级已经成功的结果；重复失败回调按幂等处理。
            if (STATUS_SUCCESS.equals(currentStatus) || STATUS_FAILED.equals(currentStatus)) {
                return BatchCallbackDisposition.duplicateResult();
            }
            return BatchCallbackDisposition.applyResult();
        }

        if (ticketConfirmed && !confirmedMaterialNo.equals(row.productCode())) {
            if (superseded) {
                // 已确认料号属于另一个更新的有效请求，旧请求的迟到回调不得覆盖它。
                throw callbackError(HttpStatus.CONFLICT,
                        "MES material callback comes from a superseded request and cannot overwrite "
                                + "the confirmed material number");
            }
            // 当前有效请求给出了新的有效结果：允许覆盖并审计（MES 是料号的权威来源）。
            return BatchCallbackDisposition.overwriteResult(confirmedMaterialNo);
        }
        if (ticketConfirmed) {
            if (STATUS_SUCCESS.equals(currentStatus)) {
                return BatchCallbackDisposition.duplicateResult();
            }
            // 同值，但批次行还停在待回调/待核对：补一次写入把行状态收口。
            return BatchCallbackDisposition.applyResult();
        }
        if (STATUS_SUCCESS.equals(currentStatus)) {
            if (row.productCode().equals(currentProductCode)) {
                return BatchCallbackDisposition.duplicateResult();
            }
            return BatchCallbackDisposition.overwriteResult(currentProductCode);
        }
        if (STATUS_FAILED.equals(currentStatus)) {
            // MES 是料号的权威来源。批次行先前被判 FAILED（超时、响应解析失败或 MES 当时拒绝），
            // 但 MES 后续仍产出了料号并回调成功。执行到这里之前已校验：批次行属于本批次、
            // 本批次行属于本提单、taskNo 与本行记录的 MES 任务一致，因此不可能是别的任务串进来。
            return BatchCallbackDisposition.reviveResult();
        }
        if (!isBlank(currentProductCode) && !row.productCode().equals(currentProductCode)) {
            return BatchCallbackDisposition.overwriteResult(currentProductCode);
        }
        return BatchCallbackDisposition.applyResult();
    }

    static String normalizeCallbackExistingStatus(String status) {
        if (status == null) {
            return STATUS_PROCESSING;
        }
        String normalized = status.trim();
        return normalized.isEmpty() ? STATUS_PROCESSING : normalized.toUpperCase(Locale.ROOT);
    }

    private SourceItemMaterialNoSyncService.MaterialNoSyncResult applyMaterialBatchCallbackRow(
            MaterialBatchCallbackApplication application) {
        MaterialBatchCallbackRow row = application.row();
        MesMaterialCallbackReq adapter = legacyCallbackAdapter(application.batchItem().getLineRequestId(), row);
        if (row.success()) {
            SourceItemMaterialNoSyncService.MaterialNoSyncResult sourceSyncResult = syncSourceItemMaterialNoForCallback(
                    application.ticket(), row.productCode(), row.message(), application.request(), adapter,
                    application.overwrite());
            markCallbackSuccess(
                    application.ticket(), application.batchItem().getLineRequestId(), row.productCode(),
                    row.message(), row.taskNo(), application.overwrite());
            updateMesRequestFromCallback(
                    application.batchItem().getLineRequestId(), STATUS_SUCCESS, row.productCode(), row.taskNo(), row.message(), true);
            updateMaterialBatchItemFromCallback(application.batchItem(), row, STATUS_SUCCESS, true);
            if (dedupService != null) {
                dedupService.completeMasterSuccess(
                        application.batchItem().getLineRequestId(), row.productCode(), row.message(), row.taskNo());
            }
            // application.request() 是回调处理前加载的快照，requestStatus 仍是被纠正前的 FAILED。
            // 审计要反映落库后的真实状态，否则每次排查都会误判成"回写失败"。
            OrderDeliveryTicketMesRequest auditedRequest = refreshRequestSnapshot(application.request());
            recordCallbackAudit(
                    "DELIVERY_MES_MATERIAL_CALLBACK_SUCCESS",
                    application.ticket(),
                    auditedRequest,
                    adapter,
                    STATUS_SUCCESS,
                    null,
                    sourceSyncResult);
            if (application.reviveFailed()) {
                recordCallbackAudit(
                        "DELIVERY_MES_MATERIAL_CALLBACK_REVIVE",
                        application.ticket(),
                        auditedRequest,
                        adapter,
                        STATUS_SUCCESS,
                        null,
                        sourceSyncResult);
            }
            if (application.overwrite()) {
                recordMaterialOverwriteAudit(
                        application.ticket(), auditedRequest, row, application.previousMaterialNo());
            }
            return sourceSyncResult;
        }
        String failedMessage = isBlank(row.message()) ? "MES material callback failed" : row.message();
        markFailed(application.ticket(), application.batchItem().getLineRequestId(), failedMessage);
        updateMesRequestFromCallback(
                application.batchItem().getLineRequestId(), STATUS_FAILED, null, row.taskNo(), failedMessage, true);
        updateMaterialBatchItemFromCallback(application.batchItem(), row, STATUS_FAILED, true);
        if (dedupService != null) {
            dedupService.completeMasterFailed(application.batchItem().getLineRequestId(), failedMessage);
        }
        recordCallbackAudit(
                "DELIVERY_MES_MATERIAL_CALLBACK_FAILED",
                application.ticket(),
                refreshRequestSnapshot(application.request()),
                adapter,
                STATUS_FAILED,
                failedMessage);
        return SourceItemMaterialNoSyncService.MaterialNoSyncResult.empty();
    }

    private MesMaterialCallbackReq legacyCallbackAdapter(String lineRequestId, MaterialBatchCallbackRow row) {
        MesMaterialCallbackReq adapter = new MesMaterialCallbackReq();
        adapter.setMesRequestId(lineRequestId);
        adapter.setExternalTraceId(row.taskNo());
        adapter.setStatus(row.status());
        adapter.setMaterialNo(row.productCode());
        adapter.setMessage(row.message());
        return adapter;
    }

    private void updateMaterialBatchItemFromCallback(
            MesMaterialBatchItem source,
            MaterialBatchCallbackRow row,
            String status,
            boolean completed) {
        MesMaterialBatchItem update = new MesMaterialBatchItem();
        update.setId(source.getId());
        update.setStatus(status);
        update.setMesTaskNo(row.taskNo());
        update.setProductCode(row.success() ? row.productCode() : null);
        update.setProductName(row.success() ? row.productName() : null);
        update.setResponseStatus(row.status());
        update.setMessage(abbreviate(row.message()));
        update.setUpdatedAt(LocalDateTime.now());
        if (completed) {
            update.setCompletedAt(LocalDateTime.now());
        }
        mesMaterialBatchItemMapper.updateById(update);
    }

    private void updateMaterialBatchAfterCallback(MesIntegrationBatch batch, Map<Long, String> projectedStatuses) {
        String status = summarizeMaterialBatchStatus(projectedStatuses.values().stream().toList());
        MesIntegrationBatch update = new MesIntegrationBatch();
        update.setId(batch.getId());
        update.setStatus(status);
        update.setRespondedAt(LocalDateTime.now());
        update.setResponseCode("0");
        update.setResponseMessage("MES material callback accepted");
        update.setUpdatedAt(LocalDateTime.now());
        if (STATUS_SUCCESS.equals(status) || STATUS_FAILED.equals(status)) {
            update.setCompletedAt(LocalDateTime.now());
        }
        mesIntegrationBatchMapper.updateById(update);
    }

    static String summarizeMaterialBatchStatus(List<String> statuses) {
        boolean hasPending = false;
        boolean hasFailed = false;
        boolean hasUnknown = false;
        boolean allSuccess = !statuses.isEmpty();
        for (String status : statuses) {
            String normalized = normalizeCallbackExistingStatus(status);
            if (!STATUS_SUCCESS.equals(normalized)) {
                allSuccess = false;
            }
            if (STATUS_FAILED.equals(normalized)) {
                hasFailed = true;
            }
            if (STATUS_RESULT_UNKNOWN.equals(normalized)) {
                hasUnknown = true;
            } else if (!STATUS_SUCCESS.equals(normalized) && !STATUS_FAILED.equals(normalized)) {
                hasPending = true;
            }
        }
        if (allSuccess) {
            return STATUS_SUCCESS;
        }
        if (hasUnknown) {
            return STATUS_RESULT_UNKNOWN;
        }
        if (hasPending) {
            return BATCH_STATUS_PARTIAL_PENDING;
        }
        return hasFailed ? STATUS_FAILED : STATUS_RESULT_UNKNOWN;
    }

    private MesMaterialCallbackResp batchCallbackResponse(
            boolean success,
            int code,
            String msg,
            String bizId,
            int receivedCount,
            int appliedCount,
            int duplicateCount,
            SourceSyncAccumulator sourceSync) {
        MesMaterialCallbackResp resp = new MesMaterialCallbackResp();
        resp.setSuccess(success);
        resp.setCode(code);
        resp.setMsg(msg);
        resp.setMesRequestId(bizId);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("bizId", bizId);
        data.put("receivedCount", receivedCount);
        data.put("appliedCount", appliedCount);
        data.put("duplicateCount", duplicateCount);
        data.put("sourceSyncTargetCount", sourceSync == null ? 0 : sourceSync.targetCount);
        data.put("sourceSyncUpdatedCount", sourceSync == null ? 0 : sourceSync.updatedCount);
        data.put("sourceSyncIdempotentCount", sourceSync == null ? 0 : sourceSync.idempotentCount);
        resp.setData(data);
        return resp;
    }

    public Map<String, Object> syncMaterialNo(Integer ticketId, Long taskId) {
        long startedAt = System.currentTimeMillis();
        OrderDeliveryTicket ticket = loadTicket(ticketId);
        assertCanStart(ticket);
        retireUnresolvedRequests(ticket, taskId);
        MesMaterialDedupService.DedupResolveResult dedupResult = dedupService == null
                ? null
                : dedupService.resolveBeforeMes(ticket, taskId);
        if (dedupResult != null && dedupResult.isFollowExistingActive()) {
            return completeDedupFollow(ticket, taskId, dedupResult, startedAt);
        }
        if (dedupResult != null && dedupResult.isActiveRequestExists()) {
            return batchActiveRequestRow(ticket, taskId, dedupResult);
        }
        String mesRequestId = generateMesRequestId(ticket.getId());
        log.info("MES material sync started ticketId={} taskId={} rfqId={} partNo={}",
                ticket.getId(), taskId, ticket.getRfqId(), ticket.getPartNo());
        try {
            insertMesRequest(ticket, taskId, mesRequestId, dedupResult);
        } catch (RuntimeException error) {
            if (isDedupUniqueConflict(error, dedupResult)) {
                MesMaterialDedupService.DedupResolveResult followResult =
                        dedupService.followActiveMaster(ticket, taskId, dedupResult.getDedupKey());
                return completeDedupFollow(ticket, taskId, followResult, startedAt);
            }
            throw error;
        }
        if (dedupResult != null) {
            log.info("MES material dedup master created ticketId={} mesRequestId={} dedupKey={}",
                    ticket.getId(), mesRequestId, dedupResult.getDedupKey());
        }
        markProcessing(ticket, mesRequestId, dedupResult);
        try {
            MesMaterialResolveReq req = buildRequest(ticket, mesRequestId);
            MesMaterialResolveResp resp = mesMaterialClient.resolveMaterial(req);
            validateResponseSuccess(resp, mesRequestId);
            String materialNo = normalizeMaterialNo(resp);
            if (!isBlank(materialNo)) {
                SourceItemMaterialNoSyncService.MaterialNoSyncResult sourceSyncResult = markSuccess(
                        ticket,
                        taskId,
                        mesRequestId,
                        materialNo,
                        resp == null ? null : resp.getMessage(),
                        resp == null ? null : resp.getExternalTraceId());
                updateMesRequest(mesRequestId, STATUS_SUCCESS, materialNo, resp == null ? null : resp.getExternalTraceId(), resp == null ? null : resp.getMessage(), true);
                if (dedupService != null) {
                    dedupService.completeMasterSuccess(
                            mesRequestId,
                            materialNo,
                            resp == null ? null : resp.getMessage(),
                            resp == null ? null : resp.getExternalTraceId()
                    );
                }
                Map<String, Object> summary = summary(ticket, taskId, mesRequestId, materialNo, STATUS_SUCCESS, resp == null ? null : resp.getExternalTraceId());
                appendSourceSyncSummary(summary, sourceSyncResult);
                recordAudit(
                        "DELIVERY_MES_MATERIAL_SYNC_SUCCESS",
                        ticket,
                        taskId,
                        mesRequestId,
                        materialNo,
                        STATUS_SUCCESS,
                        resp == null ? null : resp.getExternalTraceId(),
                        null,
                        sourceSyncResult
                );
                log.info("MES material sync completed ticketId={} taskId={} rfqId={} partNo={} materialNo={} durationMs={}",
                        ticket.getId(), taskId, ticket.getRfqId(), ticket.getPartNo(), materialNo,
                        System.currentTimeMillis() - startedAt);
                return summary;
            }
            if (isCallbackPendingResponse(resp)) {
                markCallbackPending(ticket, mesRequestId, resp.getExternalTraceId(), resp.getMessage());
                updateMesRequest(mesRequestId, STATUS_CALLBACK_PENDING, null, resp.getExternalTraceId(), resp.getMessage(), false);
                if (dedupService != null) {
                    dedupService.markMasterCallbackPending(mesRequestId, resp.getExternalTraceId(), resp.getMessage());
                }
                Map<String, Object> summary = summary(ticket, taskId, mesRequestId, null, STATUS_CALLBACK_PENDING, resp.getExternalTraceId());
                recordAudit(
                        "DELIVERY_MES_MATERIAL_SYNC_ACCEPTED",
                        ticket,
                        taskId,
                        mesRequestId,
                        null,
                        STATUS_CALLBACK_PENDING,
                        resp.getExternalTraceId(),
                        null
                );
                log.info("MES material sync accepted ticketId={} taskId={} mesRequestId={} externalTraceId={} durationMs={}",
                        ticket.getId(), taskId, mesRequestId, resp.getExternalTraceId(), System.currentTimeMillis() - startedAt);
                return summary;
            }
            throw new RuntimeException(isBlank(resp == null ? null : resp.getMessage()) ? "MES 未返回物料号" : resp.getMessage());
        } catch (RuntimeException error) {
            boolean resultUnknown = isResultUnknown(error);
            String resultStatus = resultUnknown ? STATUS_RESULT_UNKNOWN : STATUS_FAILED;
            if (resultUnknown) {
                markResultUnknown(ticket, mesRequestId, error.getMessage());
            } else {
                markFailed(ticket, mesRequestId, error.getMessage());
            }
            updateMesRequest(mesRequestId, resultStatus, null, null, error.getMessage(), !resultUnknown);
            if (!resultUnknown && dedupService != null) {
                dedupService.completeMasterFailed(mesRequestId, error.getMessage());
            }
            recordAudit(resultUnknown ? "DELIVERY_MES_MATERIAL_SYNC_RESULT_UNKNOWN" : "DELIVERY_MES_MATERIAL_SYNC_FAILED",
                    ticket, taskId, mesRequestId, null, resultStatus, null, error.getMessage());
            log.error("MES material sync failed ticketId={} taskId={} rfqId={} partNo={} durationMs={}",
                    ticket.getId(), taskId, ticket.getRfqId(), ticket.getPartNo(),
                    System.currentTimeMillis() - startedAt, error);
            throw error;
        }
    }

    public List<Map<String, Object>> syncMaterialBatch(List<OrderDeliveryTicket> tickets, Long taskId) {
        if (tickets == null || tickets.isEmpty()) {
            return List.of();
        }
        List<Map<String, Object>> rows = new ArrayList<>();
        Map<String, List<MaterialBatchPlan>> groups = new LinkedHashMap<>();
        for (OrderDeliveryTicket ticket : tickets) {
            try {
                OrderDeliveryTicket current = ticket == null ? null : loadTicket(ticket.getId());
                assertCanStart(current);
                // 结果未知的行允许重新获取：MES 按身份全局判重，重发不会造出第二个任务，
                // 只会明确回「已存在带料号」或「任务还在待建料」。重试前先把旧台账行收口。
                retireUnresolvedRequests(current, taskId);
                MaterialBatchPlan plan = buildBatchPlan(current);
                MesMaterialDedupService.DedupResolveResult dedupResult = dedupService == null
                        ? null
                        : dedupService.resolveBeforeMes(current, taskId);
                if (dedupResult != null && dedupResult.isFollowExistingActive()) {
                    rows.add(batchDedupFollowRow(current, taskId, dedupResult));
                    continue;
                }
                if (dedupResult != null && dedupResult.isActiveRequestExists()) {
                    rows.add(batchActiveRequestRow(current, taskId, dedupResult));
                    continue;
                }
                groups.computeIfAbsent(plan.groupKey(), ignored -> new ArrayList<>()).add(plan);
            } catch (RuntimeException error) {
                Integer ticketId = ticket == null ? null : ticket.getId();
                rows.add(batchFailureRow(ticket, taskId, null, error.getMessage(), false));
                log.warn("MES material batch row rejected ticketId={} taskId={} reason={}",
                        ticketId, taskId, error.getMessage());
            }
        }
        for (List<MaterialBatchPlan> group : groups.values()) {
            rows.addAll(syncMaterialPlanGroup(group, taskId));
        }
        return rows;
    }

    private Map<String, Object> batchDedupFollowRow(
            OrderDeliveryTicket ticket,
            Long taskId,
            MesMaterialDedupService.DedupResolveResult dedupResult) {
        Map<String, Object> result = summary(
                ticket,
                taskId,
                dedupResult.getMasterMesRequestId(),
                null,
                dedupResult.getStatus(),
                null);
        appendDedupSummary(result, dedupResult);
        result.put("submittedToMes", false);
        result.put("message", "Follow active MES material request for same part/version/material");
        return result;
    }

    /**
     * 同一物料身份已有在途请求，但它不是本流程的批量主请求（刷新料号台账行，
     * 或本提单自身的旧请求）。不建 follower、不发 POST，只把在途状态回给用户。
     */
    private Map<String, Object> batchActiveRequestRow(
            OrderDeliveryTicket ticket,
            Long taskId,
            MesMaterialDedupService.DedupResolveResult dedupResult) {
        boolean unknown = STATUS_RESULT_UNKNOWN.equals(dedupResult.getStatus());
        Map<String, Object> result = summary(
                ticket,
                taskId,
                dedupResult.getMasterMesRequestId(),
                null,
                dedupResult.getStatus(),
                null);
        appendDedupSummary(result, dedupResult);
        result.put("status", unknown ? STATUS_RESULT_UNKNOWN : STATUS_CALLBACK_PENDING);
        result.put("submittedToMes", false);
        result.put("message", unknown
                ? "同一图号版本材质已有结果待核对的 MES 请求，请按原 bizId 核对后再处理"
                : "同一图号版本材质已有在途 MES 请求，等待其完成后重试");
        return result;
    }

    /**
     * 一个客户/项目分组内的物料请求。
     *
     * <p>组内先按三字段身份（图号+版本+材质）归并 master/follower。MES 创建响应不带材质，
     * 所以同一个请求内「图号+版本」必须唯一，否则响应无法对应到具体材质。同一图号版本
     * 出现多种材质时，拆成多个请求（各自一个 bizId），而不是把不同材质并成 follower。</p>
     */
    private List<Map<String, Object>> syncMaterialPlanGroup(List<MaterialBatchPlan> group, Long taskId) {
        Map<String, MaterialBatchPlan> mastersByIdentity = new LinkedHashMap<>();
        Map<String, List<MaterialBatchPlan>> followersByIdentity = new LinkedHashMap<>();
        for (MaterialBatchPlan plan : group) {
            if (mastersByIdentity.putIfAbsent(plan.identityKey(), plan) != null) {
                followersByIdentity
                        .computeIfAbsent(plan.identityKey(), ignored -> new ArrayList<>())
                        .add(plan);
            }
        }
        List<List<MaterialBatchPlan>> waves = splitIntoIdentitySafeWaves(
                new ArrayList<>(mastersByIdentity.values()));
        Map<Integer, Map<String, Object>> resultByTicketId = new LinkedHashMap<>();
        for (List<MaterialBatchPlan> wave : waves) {
            submitMaterialWave(wave, followersByIdentity, resultByTicketId, taskId);
        }
        // 各波次已把各自的 bizId 写进结果行，这里不能再统一覆盖。
        return orderedBatchRows(group, resultByTicketId, taskId, null);
    }

    /**
     * 按「同一请求内图号+版本唯一」把 master 列表切成若干波次。
     *
     * <p>同图号版本存在多种材质时才会产生多个波次；另外每波不超过
     * {@link #MATERIAL_BATCH_MAX_ITEMS} 条，否则整批回调会超过我们的接收上限。</p>
     */
    private List<List<MaterialBatchPlan>> splitIntoIdentitySafeWaves(List<MaterialBatchPlan> masters) {
        List<List<MaterialBatchPlan>> waves = new ArrayList<>();
        for (MaterialBatchPlan plan : masters) {
            List<MaterialBatchPlan> target = null;
            for (List<MaterialBatchPlan> wave : waves) {
                boolean full = wave.size() >= MATERIAL_BATCH_MAX_ITEMS;
                boolean clashes = wave.stream()
                        .anyMatch(item -> item.itemKey().equals(plan.itemKey()));
                if (!full && !clashes) {
                    target = wave;
                    break;
                }
            }
            if (target == null) {
                target = new ArrayList<>();
                waves.add(target);
            }
            target.add(plan);
        }
        return waves;
    }

    private void submitMaterialWave(
            List<MaterialBatchPlan> masters,
            Map<String, List<MaterialBatchPlan>> followersByIdentity,
            Map<Integer, Map<String, Object>> resultByTicketId,
            Long taskId) {
        String bizId = newMaterialBizId();
        MesMaterialResolveReq req;
        Map<Integer, PreparedMaterialPlan> preparedByTicketId = new LinkedHashMap<>();
        try {
            MaterialBatchPlan first = masters.get(0);
            req = batchRequestAssembler.assemble(
                    bizId,
                    first.customerCode(),
                    first.customerName(),
                    first.internalProjectNo(),
                    masters.stream().map(MaterialBatchPlan::requestItem).toList());
            String requestFingerprint = batchFingerprintService.fingerprint(req);
            // 预留是逐条写库的（每条一个台账行），中途失败时前面几条已经落库。
            // 必须把「已落库的那几条」带回给 catch 收尾，否则它们会永远停在 PROCESSING，
            // 一直占着身份去重位，后续重试全被「已有在途请求」挡住，只能人工改库。
            reserveMaterialMasters(bizId, masters, taskId, requestFingerprint, preparedByTicketId);
        } catch (RuntimeException error) {
            String message = reservationFailureMessage(error);
            log.error("MES material batch reservation failed bizId={} taskId={} itemCount={} reason={}",
                    bizId, taskId, masters.size(), message, error);
            for (MaterialBatchPlan master : masters) {
                PreparedMaterialPlan prepared = preparedByTicketId.get(master.ticket().getId());
                Map<String, Object> masterResult = prepared == null
                        ? batchFailureRow(master.ticket(), taskId, null, message, false)
                        : applyBatchFailure(prepared, taskId, message, false);
                resultByTicketId.put(master.ticket().getId(), withBizId(masterResult, bizId));
                applyFollowerFailures(master, prepared == null ? null : prepared.lineRequestId(),
                        followersByIdentity, resultByTicketId, taskId, bizId, message);
            }
            updateMaterialBatchStatus(batchId(preparedByTicketId), STATUS_FAILED, null, message, true);
            return;
        }

        Long batchId = batchId(preparedByTicketId);
        setMaterialBatchSubmitting(batchId, preparedByTicketId.values());
        MesMaterialResolveResp resp = null;
        try {
            resp = mesMaterialClient.resolveMaterial(req);
            validateBatchResponseSuccess(resp, bizId);
        } catch (RuntimeException error) {
            String message = error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage();
            boolean unknown = isResultUnknown(error);
            String status = unknown ? STATUS_RESULT_UNKNOWN : STATUS_FAILED;
            log.error("MES material batch group failed bizId={} taskId={} itemCount={} status={} reason={}",
                    bizId, taskId, masters.size(), status, message, error);
            for (MaterialBatchPlan master : masters) {
                PreparedMaterialPlan prepared = preparedByTicketId.get(master.ticket().getId());
                Map<String, Object> masterResult = prepared == null
                        ? (unknown
                            ? batchUnknownRow(master.ticket(), taskId, null, message)
                            : batchFailureRow(master.ticket(), taskId, null, message, true))
                        : (unknown
                            ? applyBatchResultUnknown(prepared, taskId, message, true)
                            : applyBatchFailure(prepared, taskId, message, true));
                resultByTicketId.put(master.ticket().getId(), withBizId(masterResult, bizId));
                if (unknown) {
                    applyFollowerUnknown(master, prepared == null ? null : prepared.lineRequestId(),
                            followersByIdentity, resultByTicketId, taskId, bizId, message);
                } else {
                    applyFollowerFailures(master, prepared == null ? null : prepared.lineRequestId(),
                            followersByIdentity, resultByTicketId, taskId, bizId, message);
                }
            }
            updateMaterialBatchStatus(batchId, status, responseCode(resp), message, !unknown);
            return;
        }

        ResponseIndex responseIndex = indexMaterialResponse(resp, masters);
        for (MaterialBatchPlan master : masters) {
            PreparedMaterialPlan prepared = preparedByTicketId.get(master.ticket().getId());
            if (prepared == null) {
                resultByTicketId.put(master.ticket().getId(), withBizId(
                        batchFailureRow(master.ticket(), taskId, null, "MES batch reservation is incomplete", true), bizId));
                continue;
            }
            if (responseIndex.duplicateKeys().contains(master.itemKey())) {
                String message = "MES returned duplicate rows for drawing/version";
                resultByTicketId.put(master.ticket().getId(), withBizId(
                        applyBatchResultUnknown(prepared, taskId, message, true), bizId));
                applyFollowerUnknown(master, prepared.lineRequestId(), followersByIdentity,
                        resultByTicketId, taskId, bizId, message);
                continue;
            }
            if (responseIndex.mismatchedKeys().contains(master.itemKey())) {
                String message = "MES 回传的图号/版本与请求不一致，无法确认这一行归属";
                resultByTicketId.put(master.ticket().getId(), withBizId(
                        applyBatchResultUnknown(prepared, taskId, message, true), bizId));
                applyFollowerUnknown(master, prepared.lineRequestId(), followersByIdentity,
                        resultByTicketId, taskId, bizId, message);
                continue;
            }
            MesMaterialResolveResp.Item item = responseIndex.itemsByKey().get(master.itemKey());
            if (item == null) {
                String message = "MES response missing row for drawing/version";
                resultByTicketId.put(master.ticket().getId(), withBizId(
                        applyBatchResultUnknown(prepared, taskId, message, true), bizId));
                applyFollowerUnknown(master, prepared.lineRequestId(), followersByIdentity,
                        resultByTicketId, taskId, bizId, message);
                continue;
            }
            if (responseIndex.duplicateTaskNos().contains(trimToNull(item.getTaskNo()))) {
                String message = "MES returned duplicate taskNo inside one bizId";
                resultByTicketId.put(master.ticket().getId(), withBizId(
                        applyBatchResultUnknown(prepared, taskId, message, true), bizId));
                applyFollowerUnknown(master, prepared.lineRequestId(), followersByIdentity,
                        resultByTicketId, taskId, bizId, message);
                continue;
            }
            if (isExplicitMaterialFailure(item)) {
                String message = firstNonBlank(item.getMessage(), "MES material row failed");
                applyFollowerFailures(master, prepared.lineRequestId(), followersByIdentity,
                        resultByTicketId, taskId, bizId, message);
                resultByTicketId.put(master.ticket().getId(), withBizId(
                        applyBatchFailure(prepared, taskId, message, true), bizId));
                continue;
            }
            String materialNo = normalizeMaterialNo(item.getProductCode());
            if (materialNo.isEmpty() && !isPendingMaterialItem(item)) {
                String message = "MES response row has no material number or pending status";
                resultByTicketId.put(master.ticket().getId(), withBizId(
                        applyBatchResultUnknown(prepared, taskId, message, true), bizId));
                applyFollowerUnknown(master, prepared.lineRequestId(), followersByIdentity,
                        resultByTicketId, taskId, bizId, message);
                continue;
            }
            try {
                Map<String, Object> masterResult = withBizId(applyBatchItem(prepared, item, taskId, true), bizId);
                resultByTicketId.put(master.ticket().getId(), masterResult);
                applyFollowerResult(prepared, followersByIdentity, item, resultByTicketId, taskId, bizId);
            } catch (RuntimeException error) {
                String message = error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage();
                resultByTicketId.put(master.ticket().getId(), withBizId(
                        applyBatchResultUnknown(prepared, taskId, message, true), bizId));
                applyFollowerUnknown(master, prepared.lineRequestId(), followersByIdentity,
                        resultByTicketId, taskId, bizId, message);
            }
        }
        String batchStatus = summarizeMaterialBatchStatus(masters.stream()
                .map(master -> resultByTicketId.get(master.ticket().getId()))
                .filter(java.util.Objects::nonNull)
                .map(row -> String.valueOf(row.get("status")))
                .toList());
        updateMaterialBatchStatus(batchId, batchStatus, responseCode(resp), resp.getMessage(),
                STATUS_SUCCESS.equals(batchStatus) || STATUS_FAILED.equals(batchStatus));
    }

    /**
     * 预留阶段撞 {@code uk_mes_material_batch_key} 说明拆分逻辑漏了同图号版本多材质，
     * 把唯一键异常翻译成业务原因，不要让它原样冒到前端。
     */
    private String reservationFailureMessage(RuntimeException error) {
        String message = error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage();
        // 身份去重位已被占：可能是并发提交，也可能是同一物料身份的另一张提单正在等回调。
        // 这不是数据错误，要说成业务原因，别把 Postgres 原文丢给用户。
        boolean identityHeld = message.contains("uk_delivery_mes_request_active_dedup");
        if (identityHeld) {
            return "该图号版本材质已有在途 MES 请求，请先按原 bizId 核对结果";
        }
        boolean batchItemKeyClash = message.contains("uk_mes_material_batch_key")
                || (error instanceof DataIntegrityViolationException && message.contains("mes_material_batch_item"));
        if (batchItemKeyClash) {
            return "同一图号版本存在多种材质，MES 批次行键冲突；请确认后按图号版本分别获取物料号";
        }
        return message;
    }

    private List<Map<String, Object>> orderedBatchRows(
            List<MaterialBatchPlan> group,
            Map<Integer, Map<String, Object>> resultByTicketId,
            Long taskId,
            String bizId) {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (MaterialBatchPlan plan : group) {
            rows.add(withBizId(resultByTicketId.getOrDefault(plan.ticket().getId(),
                    batchFailureRow(plan.ticket(), taskId, null, "MES batch result was not produced", false)), bizId));
        }
        return rows;
    }

    private Long batchId(Map<Integer, PreparedMaterialPlan> preparedByTicketId) {
        return preparedByTicketId.values().stream()
                .map(PreparedMaterialPlan::batchId)
                .filter(java.util.Objects::nonNull)
                .findFirst()
                .orElse(null);
    }

    private void setMaterialBatchSubmitting(
            Long batchId,
            java.util.Collection<PreparedMaterialPlan> preparedPlans) {
        updateMaterialBatchStatus(batchId, "SUBMITTING", null,
                "MES material batch submitting", false);
        for (PreparedMaterialPlan prepared : preparedPlans) {
            updateBatchItem(prepared, "SUBMITTING", null, null,
                    "MES material batch submitting", false);
        }
    }

    private void updateMaterialBatchStatus(
            Long batchId,
            String status,
            Object responseCode,
            String message,
            boolean completed) {
        if (batchId == null || mesIntegrationBatchMapper == null) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        MesIntegrationBatch update = new MesIntegrationBatch();
        update.setId(batchId);
        update.setStatus(status);
        if ("SUBMITTING".equals(status)) {
            update.setSubmittedAt(now);
        } else {
            update.setRespondedAt(now);
        }
        if (completed) {
            update.setCompletedAt(now);
        }
        update.setResponseCode(responseCode == null ? null : abbreviate(String.valueOf(responseCode)));
        update.setResponseMessage(abbreviate(message));
        update.setUpdatedAt(now);
        mesIntegrationBatchMapper.updateById(update);
    }

    private MaterialBatchPlan buildBatchPlan(OrderDeliveryTicket ticket) {
        String internalProjectNo = required(ticket.getInternalProjectNo(), "Internal project number cannot be blank");
        OrderRfq order = orderRfqMapper == null || ticket.getRfqId() == null ? null : orderRfqMapper.selectById(ticket.getRfqId());
        String customerCode = resolveCustomerCode(ticket, order);
        String customerName = order == null ? ticket.getCustomerName() : order.getCustomerName();
        String drawingNo = required(ticket.getPartNo(), "Drawing number cannot be blank");
        // MES 不接受空版本（整批 400 退回，同批其它零件一起失败），所以在打包前就拦住这一行。
        String drawingVersion = required(ticket.getVersion(), "版本为空，MES 不接受无版本明细；请先补版本");
        MesMaterialResolveReq.Item item = new MesMaterialResolveReq.Item();
        item.setDrawingNo(drawingNo.trim());
        item.setDrawingVersion(drawingVersion);
        item.setMaterial(trimToNull(ticket.getMaterialName()));
        item.setName(trimToNull(ticket.getDescription()));
        item.setRemark(trimToNull(ticket.getRemark()));
        item.setCustomerRequirements(MesCustomerRequirementsAssembler.assemble(
                ticket.getMaterialName(),
                ticket.getSurfaceTreatment()));
        String groupKey = normalizeKeyPart(customerCode) + "|" + normalizeKeyPart(customerName) + "|" + normalizeKeyPart(internalProjectNo);
        return new MaterialBatchPlan(
                ticket,
                trimToNull(customerCode),
                trimToNull(customerName),
                internalProjectNo.trim(),
                item,
                materialItemKey(drawingNo, drawingVersion),
                MesMaterialDedupService.identityKey(
                        ticket.getPartNo(), ticket.getVersion(), ticket.getMaterialName()),
                groupKey);
    }

    /**
     * 逐条预留台账行，成功的直接写进调用方传进来的 {@code prepared}。
     *
     * <p>不用返回值是为了让中途失败时调用方仍能看到「已经预留成功的那几条」并收尾成
     * {@code FAILED}——否则那些台账行会永远停在 {@code PROCESSING}、占住身份去重位。</p>
     */
    private void reserveMaterialMasters(
            String bizId,
            List<MaterialBatchPlan> masters,
            Long taskId,
            String requestFingerprint,
            Map<Integer, PreparedMaterialPlan> prepared) {
        if (mesBatchStateService != null) {
            MesBatchStateService.MaterialBatchReservation reservation = new MesBatchStateService.MaterialBatchReservation(
                    taskId,
                    null,
                    masters.get(0).groupKey(),
                    requestFingerprint,
                    masters.stream()
                            .map(plan -> new MesBatchStateService.MaterialItemReservation(
                                    plan.ticket().getId(),
                                    null,
                                    plan.requestItem().getDrawingNo(),
                                    plan.requestItem().getDrawingVersion(),
                                    plan.itemKey()))
                            .toList());
            MesBatchStateService.MaterialBatchReservationResult result =
                    mesBatchStateService.reserveMaterialBatch(bizId, reservation);
            Map<Integer, MesMaterialBatchItem> itemByTicketId = new HashMap<>();
            for (MesMaterialBatchItem item : result.items()) {
                itemByTicketId.put(item.getTicketId(), item);
            }
            MesIntegrationBatch batch = result.batch();
            for (MaterialBatchPlan plan : masters) {
                MesMaterialBatchItem item = itemByTicketId.get(plan.ticket().getId());
                if (item == null) {
                    throw new RuntimeException("MES batch reservation did not return item for ticket " + plan.ticket().getId());
                }
                OrderDeliveryTicketMesRequest request = insertBatchMesRequest(
                        plan.ticket(), taskId, item.getLineRequestId(), plan.identityKey(), batch.getId(), item.getId());
                linkBatchItemToRequest(item, request);
                prepared.put(plan.ticket().getId(), new PreparedMaterialPlan(plan, item.getLineRequestId(), item.getId(), batch.getId()));
            }
            return;
        }

        for (MaterialBatchPlan plan : masters) {
            String lineRequestId = newMaterialLineRequestId();
            markProcessing(plan.ticket(), lineRequestId, MesMaterialDedupService.DedupResolveResult.master(plan.identityKey()));
            insertBatchMesRequest(plan.ticket(), taskId, lineRequestId, plan.identityKey(), null, null);
            prepared.put(plan.ticket().getId(), new PreparedMaterialPlan(plan, lineRequestId, null, null));
        }
    }

    private Map<String, Object> applyBatchItem(
            PreparedMaterialPlan prepared,
            MesMaterialResolveResp.Item item,
            Long taskId,
            boolean submittedToMes) {
        String materialNo = normalizeMaterialNo(item == null ? null : item.getProductCode());
        String externalTraceId = responseItemTrace(item);
        String message = "MES material resolved";
        if (!isBlank(materialNo)) {
            SourceItemMaterialNoSyncService.MaterialNoSyncResult sourceSyncResult = markSuccess(
                    prepared.plan().ticket(),
                    taskId,
                    prepared.lineRequestId(),
                    materialNo,
                    message,
                    externalTraceId);
            updateMesRequest(prepared.lineRequestId(), STATUS_SUCCESS, materialNo, externalTraceId, message, true);
            updateBatchItem(prepared, STATUS_SUCCESS, item, materialNo, message, true);
            if (dedupService != null) {
                dedupService.completeMasterSuccess(prepared.lineRequestId(), materialNo, message, externalTraceId);
            }
            Map<String, Object> result = summary(prepared.plan().ticket(), taskId, prepared.lineRequestId(), materialNo, STATUS_SUCCESS, externalTraceId);
            appendSourceSyncSummary(result, sourceSyncResult);
            result.put("submittedToMes", submittedToMes);
            result.put("message", message);
            recordAudit("DELIVERY_MES_MATERIAL_SYNC_SUCCESS", prepared.plan().ticket(), taskId,
                    prepared.lineRequestId(), materialNo, STATUS_SUCCESS, externalTraceId, null, sourceSyncResult);
            return result;
        }
        if (isPendingMaterialItem(item)) {
            // 需要后续回调的行必须有 taskNo：callback 只带 bizId + taskNo，缺了就无法定位到
            // 具体批次行，只能保留原 bizId 等人工核对，不能记成「已受理待回调」。
            String pendingTaskNo = trimToNull(item.getTaskNo());
            if (pendingTaskNo == null) {
                return applyBatchResultUnknown(
                        prepared, taskId,
                        "MES accepted the request without taskNo; reconcile the original bizId",
                        submittedToMes);
            }
            String pendingMessage = "MES accepted, waiting for material callback";
            markCallbackPending(prepared.plan().ticket(), prepared.lineRequestId(), externalTraceId, pendingMessage);
            updateMesRequest(prepared.lineRequestId(), STATUS_CALLBACK_PENDING, null, externalTraceId, pendingMessage, false);
            updateBatchItem(prepared, STATUS_CALLBACK_PENDING, item, null, pendingMessage, false);
            if (dedupService != null) {
                dedupService.markMasterCallbackPending(prepared.lineRequestId(), externalTraceId, pendingMessage);
            }
            Map<String, Object> result = summary(prepared.plan().ticket(), taskId, prepared.lineRequestId(), null, STATUS_CALLBACK_PENDING, externalTraceId);
            result.put("submittedToMes", submittedToMes);
            result.put("message", pendingMessage);
            recordAudit("DELIVERY_MES_MATERIAL_SYNC_ACCEPTED", prepared.plan().ticket(), taskId,
                    prepared.lineRequestId(), null, STATUS_CALLBACK_PENDING, externalTraceId, null);
            return result;
        }
        return applyBatchResultUnknown(
                prepared, taskId, "MES returned no material number or pending status", submittedToMes);
    }

    private void applyFollowerResult(
            PreparedMaterialPlan master,
            Map<String, List<MaterialBatchPlan>> followersByIdentity,
            MesMaterialResolveResp.Item item,
            Map<Integer, Map<String, Object>> resultByTicketId,
            Long taskId,
            String bizId) {
        List<MaterialBatchPlan> followers = followersByIdentity.getOrDefault(master.plan().identityKey(), List.of());
        for (MaterialBatchPlan follower : followers) {
            if (dedupService != null && isPendingMaterialItem(item)) {
                MesMaterialDedupService.DedupResolveResult dedupResult = dedupService.registerBatchFollower(
                        follower.ticket(),
                        taskId,
                        follower.identityKey(),
                        master.plan().ticket().getId(),
                        master.lineRequestId(),
                        STATUS_CALLBACK_PENDING);
                Map<String, Object> result = summary(
                        follower.ticket(),
                        taskId,
                        master.lineRequestId(),
                        null,
                        dedupResult.getStatus(),
                        responseItemTrace(item));
                appendDedupSummary(result, dedupResult);
                result.put("submittedToMes", false);
                result.put("message", "MES accepted, waiting for material callback");
                resultByTicketId.put(follower.ticket().getId(), withBizId(result, bizId));
                continue;
            }
            String followerLineId = newMaterialLineRequestId();
            PreparedMaterialPlan prepared = new PreparedMaterialPlan(follower, followerLineId, null, null);
            Map<String, Object> result = applyBatchItem(prepared, item, taskId, false);
            result.put("dedupResult", MesMaterialDedupService.RESULT_FOLLOW_EXISTING_ACTIVE);
            result.put("dedupKey", follower.identityKey());
            resultByTicketId.put(follower.ticket().getId(), withBizId(result, bizId));
        }
    }

    private void applyFollowerFailures(
            MaterialBatchPlan master,
            String masterLineRequestId,
            Map<String, List<MaterialBatchPlan>> followersByIdentity,
            Map<Integer, Map<String, Object>> resultByTicketId,
            Long taskId,
            String bizId,
            String message) {
        for (MaterialBatchPlan follower : followersByIdentity.getOrDefault(master.identityKey(), List.of())) {
            resultByTicketId.put(follower.ticket().getId(),
                    withBizId(batchFailureRow(follower.ticket(), taskId, newMaterialLineRequestId(), message, false), bizId));
        }
    }

    private void applyFollowerUnknown(
            MaterialBatchPlan master,
            String masterLineRequestId,
            Map<String, List<MaterialBatchPlan>> followersByIdentity,
            Map<Integer, Map<String, Object>> resultByTicketId,
            Long taskId,
            String bizId,
            String message) {
        for (MaterialBatchPlan follower : followersByIdentity.getOrDefault(master.identityKey(), List.of())) {
            MesMaterialDedupService.DedupResolveResult dedupResult = null;
            if (dedupService != null && !isBlank(masterLineRequestId)) {
                dedupResult = dedupService.registerBatchFollower(
                        follower.ticket(), taskId, follower.identityKey(), master.ticket().getId(),
                        masterLineRequestId, STATUS_RESULT_UNKNOWN);
            } else {
                markResultUnknown(follower.ticket(), masterLineRequestId, message);
            }
            Map<String, Object> result = summary(
                    follower.ticket(), taskId, masterLineRequestId, null, STATUS_RESULT_UNKNOWN, null);
            if (dedupResult != null) {
                appendDedupSummary(result, dedupResult);
            }
            result.put("submittedToMes", false);
            result.put("message", abbreviate(message));
            resultByTicketId.put(follower.ticket().getId(), withBizId(result, bizId));
        }
    }

    private Map<String, Object> withBizId(Map<String, Object> result, String bizId) {
        if (result != null && !isBlank(bizId)) {
            result.put("bizId", bizId);
        }
        return result;
    }

    private Map<String, Object> applyBatchFailure(
            PreparedMaterialPlan prepared,
            Long taskId,
            String message,
            boolean submittedToMes) {
        markFailed(prepared.plan().ticket(), prepared.lineRequestId(), message);
        updateMesRequest(prepared.lineRequestId(), STATUS_FAILED, null, null, message, true);
        updateBatchItem(prepared, STATUS_FAILED, null, null, message, true);
        if (dedupService != null) {
            dedupService.completeMasterFailed(prepared.lineRequestId(), message);
        }
        Map<String, Object> result = batchFailureRow(prepared.plan().ticket(), taskId, prepared.lineRequestId(), message, submittedToMes);
        recordAudit("DELIVERY_MES_MATERIAL_SYNC_FAILED", prepared.plan().ticket(), taskId,
                prepared.lineRequestId(), null, STATUS_FAILED, null, message);
        return result;
    }

    private Map<String, Object> applyBatchResultUnknown(
            PreparedMaterialPlan prepared,
            Long taskId,
            String message,
            boolean submittedToMes) {
        markResultUnknown(prepared.plan().ticket(), prepared.lineRequestId(), message);
        updateMesRequest(prepared.lineRequestId(), STATUS_RESULT_UNKNOWN, null, null, message, false);
        updateBatchItem(prepared, STATUS_RESULT_UNKNOWN, null, null, message, false);
        Map<String, Object> result = batchUnknownRow(
                prepared.plan().ticket(), taskId, prepared.lineRequestId(), message);
        result.put("submittedToMes", submittedToMes);
        recordAudit("DELIVERY_MES_MATERIAL_SYNC_RESULT_UNKNOWN", prepared.plan().ticket(), taskId,
                prepared.lineRequestId(), null, STATUS_RESULT_UNKNOWN, null, message);
        return result;
    }

    private Map<String, Object> batchUnknownRow(
            OrderDeliveryTicket ticket,
            Long taskId,
            String lineRequestId,
            String message) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("ticketId", ticket == null ? null : ticket.getId());
        result.put("taskId", taskId);
        result.put("status", STATUS_RESULT_UNKNOWN);
        result.put("mesRequestId", lineRequestId);
        result.put("message", abbreviate(message));
        result.put("submittedToMes", false);
        if (ticket != null) {
            result.put("rfqId", ticket.getRfqId());
            result.put("sourceType", ticket.getSourceType());
            result.put("sourceOrderId", ticket.getSourceOrderId());
            result.put("sourceItemId", ticket.getSourceItemId());
            result.put("partNo", ticket.getPartNo());
            result.put("version", ticket.getVersion());
            result.put("internalProjectNo", ticket.getInternalProjectNo());
        }
        return result;
    }

    private Map<String, Object> batchFailureRow(
            OrderDeliveryTicket ticket,
            Long taskId,
            String lineRequestId,
            String message,
            boolean submittedToMes) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("ticketId", ticket == null ? null : ticket.getId());
        result.put("taskId", taskId);
        result.put("status", STATUS_FAILED);
        result.put("mesRequestId", lineRequestId);
        result.put("message", abbreviate(message));
        result.put("submittedToMes", submittedToMes);
        if (ticket != null) {
            result.put("rfqId", ticket.getRfqId());
            result.put("sourceType", ticket.getSourceType());
            result.put("sourceOrderId", ticket.getSourceOrderId());
            result.put("sourceItemId", ticket.getSourceItemId());
            result.put("partNo", ticket.getPartNo());
            result.put("version", ticket.getVersion());
            result.put("internalProjectNo", ticket.getInternalProjectNo());
        }
        return result;
    }

    private void validateBatchResponseSuccess(MesMaterialResolveResp resp, String bizId) {
        if (resp == null) {
            throw new MesMaterialResponseUnknownException("MES material response is empty");
        }
        if (acceptMaterialPayload(resp, bizId)) {
            return;
        }
        rejectKnownMesErrorCode(resp);
        if (Boolean.FALSE.equals(resp.getSuccess())) {
            throw new RuntimeException(isBlank(resp.getMessage()) ? "MES returned failure" : resp.getMessage());
        }
        if (!Boolean.TRUE.equals(resp.getSuccess())) {
            throw new MesMaterialResponseUnknownException("MES material response has no success code");
        }
        if (isBlank(resp.getMesRequestId())) {
            throw new MesMaterialResponseUnknownException("MES material response bizId is blank");
        }
        if (!resp.matchesRequestId(bizId)) {
            throw new MesMaterialResponseUnknownException("MES material response bizId does not match request bizId");
        }
    }

    /**
     * 把 MES 响应的明细行对回本次请求的批次行。
     *
     * <p>MES 的契约是「{@code items} 与请求 {@code items} 严格一一对应，请用下标对齐」，
     * 所以行数相等时按位置映射，图号/版本只做交叉校验：MES 明确回了但和请求对不上的行
     * 视为不可信（{@code mismatchedKeys}），宁可记结果待核对也不能写错料号。</p>
     *
     * <p>行数不等时（MES 只回了部分行）退回按图号+版本匹配——按位置配会整体错位，
     * 那比丢几行危险得多。匹配不上的行由调用方记结果待核对。</p>
     */
    private ResponseIndex indexMaterialResponse(MesMaterialResolveResp resp, List<MaterialBatchPlan> masters) {
        Map<String, MesMaterialResolveResp.Item> itemsByKey = new LinkedHashMap<>();
        Set<String> duplicateKeys = new HashSet<>();
        Set<String> duplicateTaskNos = new HashSet<>();
        Set<String> mismatchedKeys = new HashSet<>();
        Set<String> seenTaskNos = new HashSet<>();
        Set<String> expectedKeys = masters.stream()
                .map(MaterialBatchPlan::itemKey)
                .collect(java.util.stream.Collectors.toCollection(HashSet::new));
        List<MesMaterialResolveResp.Item> items = resp.getData() == null || resp.getData().getItems() == null
                ? List.of()
                : resp.getData().getItems();
        if (items.isEmpty() && expectedKeys.size() == 1
                && (isCallbackPendingResponse(resp) || !isBlank(resp.getMaterialNo()))) {
            MesMaterialResolveResp.Item synthetic = new MesMaterialResolveResp.Item();
            synthetic.setStatus(resp.getStatus());
            synthetic.setProductCode(resp.getMaterialNo());
            synthetic.setTaskNo(resp.getExternalTraceId());
            items = List.of(synthetic);
        }
        for (MesMaterialResolveResp.Item item : items) {
            String taskNo = trimToNull(item == null ? null : item.getTaskNo());
            if (taskNo != null && !seenTaskNos.add(taskNo)) {
                // 同一 bizId 内 taskNo 必须唯一，否则 callback 无法定位到具体行。
                duplicateTaskNos.add(taskNo);
            }
        }
        if (items.size() == masters.size()) {
            for (int index = 0; index < items.size(); index++) {
                MaterialBatchPlan plan = masters.get(index);
                MesMaterialResolveResp.Item item = items.get(index);
                if (!matchesRequestedIdentity(item, plan)) {
                    mismatchedKeys.add(plan.itemKey());
                    continue;
                }
                itemsByKey.put(plan.itemKey(), item);
            }
            return new ResponseIndex(itemsByKey, duplicateKeys, duplicateTaskNos, mismatchedKeys);
        }
        for (MesMaterialResolveResp.Item item : items) {
            String key = materialItemKey(item == null ? null : item.getDrawingNo(), item == null ? null : item.getDrawingVersion());
            if ("|".equals(key) && expectedKeys.size() == 1) {
                key = expectedKeys.iterator().next();
            }
            if (!expectedKeys.contains(key)) {
                continue;
            }
            if (itemsByKey.putIfAbsent(key, item) != null) {
                duplicateKeys.add(key);
            }
        }
        return new ResponseIndex(itemsByKey, duplicateKeys, duplicateTaskNos, mismatchedKeys);
    }

    /**
     * 交叉校验：MES 回传的图号/版本（声明了才比）是否就是这一行请求的图号/版本。
     *
     * <p>两个都没声明时无法校验，按位置信任——MES 的契约就是下标对齐。</p>
     */
    private boolean matchesRequestedIdentity(MesMaterialResolveResp.Item item, MaterialBatchPlan plan) {
        if (item == null) {
            return false;
        }
        MesMaterialResolveReq.Item requested = plan.requestItem();
        if (!isBlank(item.getDrawingNo())
                && !normalizeKeyPart(item.getDrawingNo()).equals(normalizeKeyPart(requested.getDrawingNo()))) {
            return false;
        }
        return isBlank(item.getDrawingVersion())
                || normalizeKeyPart(item.getDrawingVersion()).equals(normalizeKeyPart(requested.getDrawingVersion()));
    }

    private boolean isPendingMaterialItem(MesMaterialResolveResp.Item item) {
        if (item == null || isBlank(item.getStatus())) {
            return false;
        }
        String status = item.getStatus().trim().toUpperCase(Locale.ROOT);
        return STATUS_CALLBACK_PENDING.equals(status)
                || "PENDING_CREATE".equals(status)
                || "PENDING".equals(status)
                || "ACCEPTED".equals(status);
    }

    private boolean isExplicitMaterialFailure(MesMaterialResolveResp.Item item) {
        if (item == null || isBlank(item.getStatus())) {
            return false;
        }
        String status = item.getStatus().trim().toUpperCase(Locale.ROOT);
        return "FAILED".equals(status)
                || "FAILURE".equals(status)
                || "ERROR".equals(status)
                || "REJECTED".equals(status);
    }

    private Object responseCode(MesMaterialResolveResp resp) {
        return resp == null ? null : resp.getCode();
    }

    private String responseItemTrace(MesMaterialResolveResp.Item item) {
        if (item == null) {
            return null;
        }
        return firstNonBlank(item.getTaskNo(), item.getTaskId() == null ? null : String.valueOf(item.getTaskId()));
    }

    private OrderDeliveryTicketMesRequest insertBatchMesRequest(
            OrderDeliveryTicket ticket,
            Long taskId,
            String mesRequestId,
            String dedupKey,
            Long batchId,
            Long batchItemId) {
        if (mesRequestMapper == null) {
            return null;
        }
        OrderDeliveryTicketMesRequest request = new OrderDeliveryTicketMesRequest();
        request.setTicketId(ticket.getId());
        request.setMesRequestId(mesRequestId);
        request.setSystemTaskId(taskId);
        request.setBatchId(batchId);
        request.setBatchItemId(batchItemId);
        request.setRequestStatus(STATUS_PROCESSING);
        request.setDedupKey(trimToNull(dedupKey));
        request.setRequestRole(MesMaterialDedupService.ROLE_MASTER);
        request.setFollowerCount(0);
        request.setRequestMessage("MES material batch submitted");
        request.setRequestedAt(LocalDateTime.now());
        mesRequestMapper.insert(request);
        return request;
    }

    private void linkBatchItemToRequest(MesMaterialBatchItem item, OrderDeliveryTicketMesRequest request) {
        if (item == null || item.getId() == null || request == null || request.getId() == null || mesMaterialBatchItemMapper == null) {
            return;
        }
        MesMaterialBatchItem update = new MesMaterialBatchItem();
        update.setId(item.getId());
        update.setMesRequestRowId(request.getId());
        update.setUpdatedAt(LocalDateTime.now());
        mesMaterialBatchItemMapper.updateById(update);
    }

    private void updateBatchItem(
            PreparedMaterialPlan prepared,
            String status,
            MesMaterialResolveResp.Item responseItem,
            String materialNo,
            String message,
            boolean completed) {
        if (prepared == null || prepared.batchItemId() == null || mesMaterialBatchItemMapper == null) {
            return;
        }
        MesMaterialBatchItem update = new MesMaterialBatchItem();
        update.setId(prepared.batchItemId());
        update.setStatus(status);
        update.setProductCode(trimToNull(materialNo));
        update.setMesTaskNo(responseItem == null ? null : trimToNull(responseItem.getTaskNo()));
        update.setMesTaskId(responseItem == null || responseItem.getTaskId() == null ? null : String.valueOf(responseItem.getTaskId()));
        update.setProductId(responseItem == null || responseItem.getProductId() == null ? null : String.valueOf(responseItem.getProductId()));
        update.setProductName(responseItem == null ? null : trimToNull(responseItem.getProductName()));
        update.setResponseStatus(responseItem == null ? null : trimToNull(responseItem.getStatus()));
        update.setMessage(abbreviate(message));
        update.setUpdatedAt(LocalDateTime.now());
        if (completed) {
            update.setCompletedAt(LocalDateTime.now());
        }
        mesMaterialBatchItemMapper.updateById(update);
    }

    private String materialItemKey(String drawingNo, String drawingVersion) {
        return normalizeKeyPart(drawingNo) + "|" + normalizeKeyPart(drawingVersion);
    }

    private String normalizeKeyPart(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }

    private String newMaterialBizId() {
        return "QPO-MAT-" + LocalDate.now().format(BIZ_DATE) + "-" + ulid();
    }

    private String newMaterialLineRequestId() {
        return "QPO-MATL-" + ulid();
    }

    private String ulid() {
        byte[] bytes = new byte[16];
        long timestamp = System.currentTimeMillis();
        bytes[0] = (byte) (timestamp >>> 40);
        bytes[1] = (byte) (timestamp >>> 32);
        bytes[2] = (byte) (timestamp >>> 24);
        bytes[3] = (byte) (timestamp >>> 16);
        bytes[4] = (byte) (timestamp >>> 8);
        bytes[5] = (byte) timestamp;
        byte[] random = new byte[10];
        RANDOM.nextBytes(random);
        System.arraycopy(random, 0, bytes, 6, random.length);
        BigInteger value = new BigInteger(1, bytes);
        char[] encoded = new char[26];
        for (int i = encoded.length - 1; i >= 0; i--) {
            encoded[i] = CROCKFORD32[value.and(BigInteger.valueOf(31)).intValue()];
            value = value.shiftRight(5);
        }
        return new String(encoded);
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (!isBlank(value)) {
                return value.trim();
            }
        }
        return null;
    }

    private record MaterialBatchCallbackEnvelope(
            String bizId,
            List<MaterialBatchCallbackRow> rows) {
    }

    private record MaterialBatchCallbackRow(
            int index,
            String taskNo,
            String drawingNo,
            String drawingVersion,
            String status,
            String productCode,
            String productName,
            String message,
            String itemKey) {
        private boolean success() {
            return STATUS_SUCCESS.equals(status);
        }
    }

    private record MaterialBatchCallbackApplication(
            MaterialBatchCallbackRow row,
            MesMaterialBatchItem batchItem,
            OrderDeliveryTicketMesRequest request,
            OrderDeliveryTicket ticket,
            boolean reviveFailed,
            boolean overwrite,
            String previousMaterialNo) {
    }

    private record BatchItemIndex(
            Map<String, MesMaterialBatchItem> byTaskNo,
            Set<String> duplicateTaskNos,
            Map<String, List<MesMaterialBatchItem>> byItemKey) {
    }

    private record BatchCallbackDisposition(
            boolean duplicate,
            boolean reviveFailed,
            boolean overwrite,
            String previousMaterialNo) {
        private static BatchCallbackDisposition applyResult() {
            return new BatchCallbackDisposition(false, false, false, null);
        }

        private static BatchCallbackDisposition duplicateResult() {
            return new BatchCallbackDisposition(true, false, false, null);
        }

        private static BatchCallbackDisposition reviveResult() {
            return new BatchCallbackDisposition(false, true, false, null);
        }

        private static BatchCallbackDisposition overwriteResult(String previousMaterialNo) {
            return new BatchCallbackDisposition(false, false, true, previousMaterialNo);
        }
    }

    private static class SourceSyncAccumulator {
        private int targetCount;
        private int updatedCount;
        private int idempotentCount;

        private void add(SourceItemMaterialNoSyncService.MaterialNoSyncResult result) {
            if (result == null) {
                return;
            }
            targetCount += result.targetCount();
            updatedCount += result.updatedCount();
            idempotentCount += result.idempotentCount();
        }
    }

    private record MaterialBatchPlan(
            OrderDeliveryTicket ticket,
            String customerCode,
            String customerName,
            String internalProjectNo,
            MesMaterialResolveReq.Item requestItem,
            String itemKey,
            String identityKey,
            String groupKey) {
    }

    private record PreparedMaterialPlan(
            MaterialBatchPlan plan,
            String lineRequestId,
            Long batchItemId,
            Long batchId) {
    }

    private record ResponseIndex(
            Map<String, MesMaterialResolveResp.Item> itemsByKey,
            Set<String> duplicateKeys,
            Set<String> duplicateTaskNos,
            /** 按下标对齐时，MES 回传的图号/版本与请求对不上的行：不可信，按结果待核对处理。 */
            Set<String> mismatchedKeys) {
    }

    private OrderDeliveryTicket loadTicket(Integer ticketId) {
        if (ticketId == null) {
            throw new RuntimeException("提单行 ID 不能为空");
        }
        OrderDeliveryTicket ticket = ticketMapper.selectById(ticketId);
        if (ticket == null) {
            throw new RuntimeException("提单行不存在");
        }
        return ticket;
    }

    private void assertCanStart(OrderDeliveryTicket ticket) {
        String status = ticket.getMesSyncStatus();
        if (STATUS_SUCCESS.equalsIgnoreCase(status) && !isBlank(ticket.getMaterialNo())) {
            throw new RuntimeException("MES material sync already completed");
        }
        if (STATUS_PROCESSING.equalsIgnoreCase(status)) {
            throw new RuntimeException("MES material sync is already processing");
        }
        if (STATUS_CALLBACK_PENDING.equalsIgnoreCase(status)) {
            throw new RuntimeException("MES material sync is waiting for callback");
        }
        // RESULT_UNKNOWN 刻意不拦：MES 按「图号+版本+材质」全局判重，换新 bizId 重发不会建出
        // 第二个物料准备任务，只会明确回 EXISTING_PRODUCT（带料号）或 1030900008（仍在待建料）。
        // 所以「结果未知」靠重新获取一次就能得到明确答案，不需要人工核对数据。
    }

    /**
     * 重新获取前把本物料身份下「结果未知」的台账行收口，释放身份去重位。
     *
     * <p>不做这一步，旧行仍然占着 {@code uk_delivery_mes_request_active_dedup}，新请求插不进去。</p>
     */
    private void retireUnresolvedRequests(OrderDeliveryTicket ticket, Long taskId) {
        if (dedupService == null || ticket == null) {
            return;
        }
        List<String> retired = dedupService.retireUnresolvedRequests(ticket);
        if (retired == null || retired.isEmpty()) {
            return;
        }
        recordRetryAfterUnknownAudit(ticket, taskId, retired);
    }

    /** 「结果未知后重新获取」必须留痕，否则事后看到料号变了查不到原因。 */
    private void recordRetryAfterUnknownAudit(
            OrderDeliveryTicket ticket,
            Long taskId,
            List<String> retiredBizIds) {
        if (auditService == null) {
            return;
        }
        Integer ticketId = ticket == null ? null : ticket.getId();
        Map<String, Object> auditSummary = summary(
                ticket, taskId, null, null, STATUS_PROCESSING, null);
        auditSummary.put("retiredMesRequestIds", retiredBizIds);
        auditSummary.put("retiredCount", retiredBizIds.size());
        auditService.record(
                "DELIVERY_MES_MATERIAL_SYNC_RETRY_AFTER_UNKNOWN",
                "order_delivery_ticket",
                ticketId,
                null,
                auditSummary,
                "DELIVERY",
                STATUS_PROCESSING,
                AuditService.labelForAction("DELIVERY_MES_MATERIAL_SYNC_RETRY_AFTER_UNKNOWN")
                        + " ticketId=" + ticketId
                        + " retired=" + String.join(",", retiredBizIds),
                null
        );
    }

    private Map<String, Object> completeDedupFollow(
            OrderDeliveryTicket ticket,
            Long taskId,
            MesMaterialDedupService.DedupResolveResult dedupResult,
            long startedAt) {
        Map<String, Object> result = summary(
                ticket,
                taskId,
                dedupResult.getMasterMesRequestId(),
                null,
                dedupResult.getStatus(),
                null
        );
        appendDedupSummary(result, dedupResult);
        log.info("MES material dedup follow ticketId={} masterTicketId={} masterMesRequestId={} dedupKey={} durationMs={}",
                ticket.getId(), dedupResult.getMasterTicketId(), dedupResult.getMasterMesRequestId(),
                dedupResult.getDedupKey(), System.currentTimeMillis() - startedAt);
        return result;
    }

    private MesMaterialResolveReq buildRequest(OrderDeliveryTicket ticket, String mesRequestId) {
        MesMaterialResolveReq req = new MesMaterialResolveReq();
        req.setMesRequestId(mesRequestId);
        req.setInternalProjectNo(required(ticket.getInternalProjectNo(), "内部项目号不能为空"));
        OrderRfq order = orderRfqMapper == null || ticket.getRfqId() == null ? null : orderRfqMapper.selectById(ticket.getRfqId());
        req.setCustomerCode(resolveCustomerCode(ticket, order));
        req.setCustomerName(order == null ? ticket.getCustomerName() : order.getCustomerName());
        MesMaterialResolveReq.Item item = new MesMaterialResolveReq.Item();
        item.setDrawingNo(required(ticket.getPartNo(), "图号不能为空"));
        item.setDrawingVersion(ticket.getVersion() == null ? "" : ticket.getVersion());
        item.setMaterial(trimToNull(ticket.getMaterialName()));
        item.setName(trimToNull(ticket.getDescription()));
        item.setRemark(trimToNull(ticket.getRemark()));
        item.setCustomerRequirements(MesCustomerRequirementsAssembler.assemble(
                ticket.getMaterialName(),
                ticket.getSurfaceTreatment()));
        req.setItems(List.of(item));
        return req;
    }

    private String resolveCustomerCode(OrderDeliveryTicket ticket, OrderRfq order) {
        if (ReleaseSourceType.SALES_ORDER.name().equals(resolveSourceType(ticket))
                && salesOrderHeaderMapper != null
                && ticket.getSourceOrderId() != null) {
            SalesOrderHeader header = salesOrderHeaderMapper.selectById(ticket.getSourceOrderId());
            if (header != null && !isBlank(header.getCustomerCode())) {
                return header.getCustomerCode().trim();
            }
        }
        if (order != null && !isBlank(order.getCustomerCode())) {
            return order.getCustomerCode().trim();
        }
        return null;
    }

    private void validateResponseSuccess(MesMaterialResolveResp resp, String mesRequestId) {
        if (resp == null) {
            throw new MesMaterialResponseUnknownException("MES 响应为空");
        }
        if (acceptMaterialPayload(resp, mesRequestId)) {
            return;
        }
        rejectKnownMesErrorCode(resp);
        if (Boolean.FALSE.equals(resp.getSuccess())) {
            throw new RuntimeException(isBlank(resp.getMessage()) ? "MES 返回失败" : resp.getMessage());
        }
        if (!Boolean.TRUE.equals(resp.getSuccess())) {
            throw new MesMaterialResponseUnknownException("MES 响应缺少成功状态");
        }
        if (isBlank(resp.getMesRequestId())) {
            throw new MesMaterialResponseUnknownException("MES 请求ID不能为空");
        }
        if (!resp.matchesRequestId(mesRequestId)) {
            throw new MesMaterialResponseUnknownException("MES 请求ID不匹配");
        }
    }

    private String normalizeMaterialNo(MesMaterialResolveResp resp) {
        return normalizeMaterialNo(resp == null ? null : resp.getMaterialNo());
    }

    /**
     * 「取到料号即写回」：料号是本流程唯一要拿的成果物。
     *
     * <p>新契约里「已存在」是行状态 {@code EXISTING_PRODUCT}、code=0，本来就按正常路径写回。
     * 这里只兜旧契约：老版本 MES 用 {@code 1_030_900_002} 这种批级码回「已存在」，
     * 同时把料号带在 {@code data.items[].productCode} 里。除此之外的非 0 code
     * 都是整批回滚、data 为空，即使带了 items 也不可信，一律不写。</p>
     *
     * @return true 表示已按料号放行，调用方不必再判外层状态
     */
    private boolean acceptMaterialPayload(MesMaterialResolveResp resp, String requestId) {
        if (resp == null || Boolean.TRUE.equals(resp.getSuccess()) || !resp.hasMaterialNo()) {
            return false;
        }
        if (!isMesMaterialUncertainCode(resp.getCode())) {
            return false;
        }
        if (resp.contradictsRequestId(requestId)) {
            throw new MesMaterialResponseUnknownException("MES 响应带着料号但 bizId 与请求不一致");
        }
        return true;
    }

    /**
     * 旧契约里「MES 侧已有同一物料身份的任务」的两个码。
     *
     * <p>新契约已把「已存在」改成行状态、把「待建料」改成 {@code 1030900008} 整批回滚，
     * 所以这两个码正常不会再出现。真出现说明对端还是老版本、行为不可知，
     * 只能保守处理：保留原 bizId、占住身份去重位、等人工核对。</p>
     */
    static boolean isMesMaterialUncertainCode(Object code) {
        String normalized = normalizeMesMaterialCode(code);
        return MES_CODE_MATERIAL_EXISTS.equals(normalized)
                || MES_CODE_MATERIAL_PROCESSING.equals(normalized);
    }

    /**
     * 非 0 code 且不是上面两个旧码，代表 MES 已整批回滚（{@code @Transactional}），本批一条都没建。
     *
     * <p>这一点很关键：既然什么都没建，重发就不会造出重复的物料准备任务，
     * 因此可以放心记成「可重试失败」，不必让用户卡在「结果待核对」里手工改状态。</p>
     */
    static boolean isMesMaterialRolledBack(Object code) {
        return normalizeMesMaterialCode(code) != null && !isMesMaterialUncertainCode(code);
    }

    /** 批级回滚给人看的说明：说清 MES 为什么拒、这批有没有留下东西、下一步该做什么。 */
    static String mesMaterialRollbackMessage(Object code) {
        String normalized = normalizeMesMaterialCode(code);
        String reason;
        String nextStep = "可稍后重新获取";
        if (MES_CODE_MATERIAL_TASK_PENDING.equals(normalized)) {
            // MES 确认过：这个码不会自动出料号，必须人工在 MES 建料，所以不能提示「等一等再重试」。
            reason = "MES 侧有零件的物料准备任务停在待建料，不会自动产出料号";
            nextStep = "请到 MES 处理该物料准备任务后再重新获取";
        } else if (MES_CODE_BATCH_DUPLICATE_ITEM.equals(normalized)) {
            reason = "MES 判定同一包内存在重复的图号+版本+材质明细";
            nextStep = "属 QPO 分包问题，请联系开发排查";
        } else if (MES_CODE_DRAWING_TRANSFER_FAILED.equals(normalized)) {
            reason = "MES 图纸转存失败";
        } else {
            reason = "MES 拒绝了本批请求";
        }
        return reason + "（code=" + code + "），本批未提交成功，" + nextStep;
    }

    /**
     * 批级错误码统一出口：能归类的给出人话，归不了类的交给上层按 MES 原文记失败。
     */
    private void rejectKnownMesErrorCode(MesMaterialResolveResp resp) {
        String code = materialErrorCode(resp);
        if (code == null) {
            return;
        }
        String raw = String.valueOf(resp.getCode()).trim();
        if (isMesMaterialUncertainCode(resp.getCode())) {
            if (MES_CODE_MATERIAL_PROCESSING.equals(code)) {
                throw new MesMaterialDuplicateSubmitException(
                        "MES 已有处理中的物料准备任务（" + raw + "），请按原 bizId 核对后再处理");
            }
            throw new MesMaterialDuplicateSubmitException(
                    "MES 已存在同一物料准备任务（" + raw + "），请按原 bizId 核对后再处理");
        }
        // 其余非 0 code 都是整批回滚，一条都没建：记可重试失败，不占身份去重位。
        throw new RuntimeException(mesMaterialRollbackMessage(resp.getCode()));
    }

    /**
     * 取纯数字形态的错误码用于比较；0 或取不到数字时返回 null。
     *
     * <p>{@code 1_030_900_002}、{@code 1030900002}、{@code 1030900002.0} 归一后同码。</p>
     */
    private String materialErrorCode(MesMaterialResolveResp resp) {
        return normalizeMesMaterialCode(resp == null ? null : resp.getCode());
    }

    static String normalizeMesMaterialCode(Object code) {
        if (code == null) {
            return null;
        }
        String digits = String.valueOf(code).replaceAll("[^0-9]", "").replaceFirst("^0+", "");
        return digits.isEmpty() ? null : digits;
    }

    private String normalizeMaterialNo(String value) {
        String materialNo = value == null ? "" : value.trim();
        if (materialNo.isEmpty()) {
            return "";
        }
        if (materialNo.length() > 100) {
            throw new RuntimeException("MES 物料号长度超过 100");
        }
        return materialNo;
    }

    private boolean isCallbackPendingResponse(MesMaterialResolveResp resp) {
        if (resp == null) {
            return false;
        }
        if (Boolean.TRUE.equals(resp.getCallbackRequired())) {
            return true;
        }
        String status = resp.getStatus();
        if (isBlank(status)) {
            return false;
        }
        String normalized = status.trim().toUpperCase();
        return STATUS_CALLBACK_PENDING.equals(normalized)
                || "PENDING_CREATE".equals(normalized)
                || "PENDING".equals(normalized)
                || "ACCEPTED".equals(normalized);
    }

    private void markProcessing(
            OrderDeliveryTicket ticket,
            String mesRequestId,
            MesMaterialDedupService.DedupResolveResult dedupResult) {
        OrderDeliveryTicket update = new OrderDeliveryTicket();
        update.setId(ticket.getId());
        update.setMesRequestId(mesRequestId);
        update.setMesSyncStatus(STATUS_PROCESSING);
        update.setMesSyncMessage("MES material sync processing");
        if (dedupResult != null) {
            update.setMesDedupKey(dedupResult.getDedupKey());
            update.setMesRequestRole(MesMaterialDedupService.ROLE_MASTER);
        }
        UpdateWrapper<OrderDeliveryTicket> wrapper = new UpdateWrapper<>();
        wrapper.eq("id", ticket.getId())
                .and(status -> status
                        .isNull("mes_sync_status")
                        .or()
                        .notIn("mes_sync_status", STATUS_PROCESSING, STATUS_SUCCESS, STATUS_CALLBACK_PENDING));
        int updated = ticketMapper.update(update, wrapper);
        if (updated <= 0) {
            OrderDeliveryTicket current = loadTicket(ticket.getId());
            assertCanStart(current);
            throw new RuntimeException("MES material sync status changed before processing");
        }
    }

    private SourceItemMaterialNoSyncService.MaterialNoSyncResult markSuccess(
            OrderDeliveryTicket ticket,
            Long taskId,
            String mesRequestId,
            String materialNo,
            String message,
            String externalTraceId) {
        String syncMessage = isBlank(message) ? "MES material resolved" : message.trim();
        // 料号以最后一次获取为准（MES 是料号的权威来源），同步响应同样允许覆盖已确认的旧料号。
        // previousMaterialNo 取的是本次提交开始时读到的提单行快照，用于留痕「被谁改成了什么」。
        // 这里用 trimToNull 而不是 normalizeMaterialNo：旧值只用于比较和留痕，
        // 不能因为历史脏数据超长就把一次成功的 MES 结果判成失败。
        String previousMaterialNo = trimToNull(ticket == null ? null : ticket.getMaterialNo());
        SourceItemMaterialNoSyncService.MaterialNoSyncResult sourceSyncResult =
                syncSourceItemMaterialNo(ticket, materialNo, syncMessage, true);
        if (productionAllocationLifecycleService != null) {
            productionAllocationLifecycleService.recordMaterialSuccess(
                    ticket.getId(), materialNo, syncMessage, mesRequestId, externalTraceId, false, true);
        } else {
            OrderDeliveryTicket update = new OrderDeliveryTicket();
            update.setId(ticket.getId());
            update.setMaterialNo(materialNo);
            update.setMesSyncStatus(STATUS_SUCCESS);
            update.setMesSyncMessage(syncMessage);
            update.setMesSyncedAt(LocalDateTime.now());
            update.setMesRequestId(mesRequestId);
            update.setMesExternalTaskId(trimToNull(externalTraceId));
            ticketMapper.updateById(update);
        }
        String currentMaterialNo = trimToNull(materialNo);
        if (previousMaterialNo != null && !previousMaterialNo.equals(currentMaterialNo)) {
            recordSyncOverwriteAudit(ticket, taskId, mesRequestId, previousMaterialNo, currentMaterialNo);
        }
        return sourceSyncResult;
    }

    /** 记录「同步响应覆盖旧料号」：旧值、新值、请求标识同时留痕，否则事后无法判断料号何时被改。 */
    private void recordSyncOverwriteAudit(
            OrderDeliveryTicket ticket,
            Long taskId,
            String mesRequestId,
            String previousMaterialNo,
            String materialNo) {
        if (auditService == null) {
            return;
        }
        Integer ticketId = ticket == null ? null : ticket.getId();
        Map<String, Object> summary = summary(ticket, taskId, mesRequestId, materialNo, STATUS_SUCCESS, null);
        summary.put("previousMaterialNo", previousMaterialNo);
        summary.put("materialNo", materialNo);
        auditService.record(
                "DELIVERY_MES_MATERIAL_SYNC_OVERWRITE",
                "order_delivery_ticket",
                ticketId,
                null,
                summary,
                "DELIVERY",
                STATUS_SUCCESS,
                AuditService.labelForAction("DELIVERY_MES_MATERIAL_SYNC_OVERWRITE")
                        + " ticketId=" + ticketId
                        + " previousMaterialNo=" + previousMaterialNo
                        + " materialNo=" + materialNo,
                null
        );
    }

    private void markCallbackPending(OrderDeliveryTicket ticket, String mesRequestId, String externalTraceId, String message) {
        OrderDeliveryTicket update = new OrderDeliveryTicket();
        update.setId(ticket.getId());
        update.setMesSyncStatus(STATUS_CALLBACK_PENDING);
        update.setMesSyncMessage(isBlank(message) ? "MES accepted, waiting for callback" : message.trim());
        update.setMesRequestId(mesRequestId);
        update.setMesExternalTaskId(trimToNull(externalTraceId));
        ticketMapper.updateById(update);
    }

    private void markCallbackSuccess(OrderDeliveryTicket ticket, String mesRequestId, String materialNo, String message, String externalTraceId) {
        markCallbackSuccess(ticket, mesRequestId, materialNo, message, externalTraceId, false);
    }

    private void markCallbackSuccess(
            OrderDeliveryTicket ticket,
            String mesRequestId,
            String materialNo,
            String message,
            String externalTraceId,
            boolean allowOverwrite) {
        String callbackMessage = isBlank(message) ? "MES material callback resolved" : message.trim();
        if (productionAllocationLifecycleService != null) {
            productionAllocationLifecycleService.recordMaterialSuccess(
                    ticket.getId(), materialNo, callbackMessage, mesRequestId, externalTraceId, true, allowOverwrite);
        } else {
            OrderDeliveryTicket update = new OrderDeliveryTicket();
            update.setId(ticket.getId());
            update.setMaterialNo(materialNo);
            update.setMesSyncStatus(STATUS_SUCCESS);
            update.setMesSyncMessage(callbackMessage);
            update.setMesSyncedAt(LocalDateTime.now());
            update.setMesRequestId(mesRequestId);
            update.setMesExternalTaskId(trimToNull(externalTraceId));
            update.setMesCallbackReceivedAt(LocalDateTime.now());
            update.setMesCallbackMessage(abbreviate(message));
            ticketMapper.updateById(update);
        }
    }

    private SourceItemMaterialNoSyncService.MaterialNoSyncResult syncSourceItemMaterialNoForCallback(
            OrderDeliveryTicket ticket,
            String materialNo,
            String message,
            OrderDeliveryTicketMesRequest request,
            MesMaterialCallbackReq req,
            boolean allowOverwrite) {
        try {
            return syncSourceItemMaterialNo(ticket, materialNo, message, allowOverwrite);
        } catch (SourceItemMaterialNoSyncService.MaterialNoConflictException error) {
            recordCallbackAudit(
                    "DELIVERY_MES_MATERIAL_CALLBACK_CONFLICT",
                    ticket,
                    request,
                    req,
                    STATUS_FAILED,
                    error.getMessage());
            throw callbackError(HttpStatus.CONFLICT, error.getMessage());
        }
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
            return sourceItemMaterialNoSyncService.syncConfirmedMaterialNo(
                    ticket, materialNo, message, true);
        }
        return sourceItemMaterialNoSyncService.syncConfirmedMaterialNo(ticket, materialNo, message);
    }

    private void markFailed(OrderDeliveryTicket ticket, String mesRequestId, String message) {
        OrderDeliveryTicket update = new OrderDeliveryTicket();
        update.setId(ticket.getId());
        update.setMesRequestId(mesRequestId);
        update.setMesSyncStatus(STATUS_FAILED);
        update.setMesSyncMessage(abbreviate(isBlank(message) ? "MES material sync failed" : message));
        ticketMapper.updateById(update);
    }

    private void markResultUnknown(OrderDeliveryTicket ticket, String mesRequestId, String message) {
        OrderDeliveryTicket update = new OrderDeliveryTicket();
        update.setId(ticket.getId());
        update.setMesRequestId(mesRequestId);
        update.setMesSyncStatus(STATUS_RESULT_UNKNOWN);
        update.setMesSyncMessage(abbreviate(isBlank(message)
                ? "MES material sync result is waiting for reconciliation" : message));
        ticketMapper.updateById(update);
    }

    private boolean isResultUnknown(RuntimeException error) {
        if (error instanceof DataCenterCallException dataCenterError) {
            return dataCenterError.getCertainty() == DataCenterCallException.FailureCertainty.RESULT_UNKNOWN;
        }
        for (Throwable current = error; current != null; current = current.getCause()) {
            if (current instanceof HttpMessageNotReadableException) {
                return true;
            }
            if (current instanceof ResourceAccessException) {
                String message = String.valueOf(current.getMessage()).toLowerCase(Locale.ROOT);
                return message.contains("read timed out")
                        || message.contains("connection reset")
                        || message.contains("unexpected end")
                        || message.contains("premature")
                        || message.contains("broken pipe");
            }
            if (current instanceof HttpStatusCodeException statusError
                    && statusError.getStatusCode().is5xxServerError()) {
                return true;
            }
        }
        return error instanceof MesMaterialResponseUnknownException
                || error instanceof MesMaterialDuplicateSubmitException;
    }

    private OrderDeliveryTicketMesRequest loadMesRequest(String mesRequestId) {
        if (mesRequestMapper == null) {
            throw callbackError(HttpStatus.INTERNAL_SERVER_ERROR, "MES 请求流水服务不可用");
        }
        QueryWrapper<OrderDeliveryTicketMesRequest> wrapper = new QueryWrapper<>();
        wrapper.eq("mes_request_id", mesRequestId);
        OrderDeliveryTicketMesRequest request = mesRequestMapper.selectOne(wrapper);
        if (request == null) {
            throw callbackError(HttpStatus.NOT_FOUND, "MES 请求流水不存在");
        }
        return request;
    }

    /**
     * 重新读取请求流水的当前状态。回调应用时先用 loadMesRequest 加载快照，随后才更新状态；
     * 直接把旧快照写进审计会让 requestStatus 永远是回调前的值，看起来像"回写失败"。
     * 这里按主键重查以绕开 MyBatis 一级缓存（selectById 与 selectOne 不是同一条 statement）。
     */
    private OrderDeliveryTicketMesRequest refreshRequestSnapshot(OrderDeliveryTicketMesRequest request) {
        if (mesRequestMapper == null || request == null || request.getId() == null) {
            return request;
        }
        OrderDeliveryTicketMesRequest refreshed = mesRequestMapper.selectById(request.getId());
        return refreshed == null ? request : refreshed;
    }

    private void updateMesRequestFromCallback(
            String mesRequestId,
            String status,
            String materialNo,
            String externalTraceId,
            String message,
            boolean completed) {
        if (mesRequestMapper == null || isBlank(mesRequestId)) {
            return;
        }
        OrderDeliveryTicketMesRequest update = new OrderDeliveryTicketMesRequest();
        update.setRequestStatus(status);
        update.setMaterialNo(trimToNull(materialNo));
        update.setMesExternalTaskId(trimToNull(externalTraceId));
        update.setCallbackMessage(abbreviate(message));
        update.setCallbackReceivedAt(LocalDateTime.now());
        update.setUpdatedAt(LocalDateTime.now());
        if (completed) {
            update.setCompletedAt(LocalDateTime.now());
        }
        UpdateWrapper<OrderDeliveryTicketMesRequest> wrapper = new UpdateWrapper<>();
        wrapper.eq("mes_request_id", mesRequestId);
        mesRequestMapper.update(update, wrapper);
    }

    private void recordAudit(
            String action,
            OrderDeliveryTicket ticket,
            Long taskId,
            String mesRequestId,
            String materialNo,
            String result,
            String externalTraceId,
            String errorMessage) {
        recordAudit(
                action,
                ticket,
                taskId,
                mesRequestId,
                materialNo,
                result,
                externalTraceId,
                errorMessage,
                SourceItemMaterialNoSyncService.MaterialNoSyncResult.empty());
    }

    private void recordAudit(
            String action,
            OrderDeliveryTicket ticket,
            Long taskId,
            String mesRequestId,
            String materialNo,
            String result,
            String externalTraceId,
            String errorMessage,
            SourceItemMaterialNoSyncService.MaterialNoSyncResult sourceSyncResult) {
        if (auditService == null) {
            return;
        }
        Map<String, Object> auditSummary = summary(
                ticket, taskId, mesRequestId, materialNo, result, externalTraceId);
        appendSourceSyncSummary(auditSummary, sourceSyncResult);
        auditService.record(
                action,
                "order_delivery_ticket",
                ticket.getId(),
                null,
                auditSummary,
                "DELIVERY",
                result,
                AuditService.labelForAction(action) + " ticketId=" + ticket.getId() + " taskId=" + taskId + " mesRequestId=" + mesRequestId,
                errorMessage
        );
    }

    private void recordCallbackAudit(
            String action,
            OrderDeliveryTicket ticket,
            OrderDeliveryTicketMesRequest request,
            MesMaterialCallbackReq req,
            String result,
            String errorMessage) {
        recordCallbackAudit(
                action,
                ticket,
                request,
                req,
                result,
                errorMessage,
                SourceItemMaterialNoSyncService.MaterialNoSyncResult.empty());
    }

    private void recordCallbackAudit(
            String action,
            OrderDeliveryTicket ticket,
            OrderDeliveryTicketMesRequest request,
            MesMaterialCallbackReq req,
            String result,
            String errorMessage,
            SourceItemMaterialNoSyncService.MaterialNoSyncResult sourceSyncResult) {
        if (auditService == null) {
            return;
        }
        Integer ticketId = ticket == null ? (request == null ? null : request.getTicketId()) : ticket.getId();
        String mesRequestId = req == null ? null : req.getMesRequestId();
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("ticketId", ticketId);
        summary.put("mesRequestId", mesRequestId);
        summary.put("externalTraceId", req == null ? null : req.getExternalTraceId());
        summary.put("status", result);
        summary.put("materialNo", req == null ? null : req.getMaterialNo());
        if (ticket != null) {
            summary.put("sourceType", ticket.getSourceType());
            summary.put("sourceOrderId", ticket.getSourceOrderId());
            summary.put("sourceItemId", ticket.getSourceItemId());
            summary.put("partNo", ticket.getPartNo());
            summary.put("version", ticket.getVersion());
        }
        if (request != null) {
            summary.put("requestStatus", request.getRequestStatus());
        }
        appendSourceSyncSummary(summary, sourceSyncResult);
        auditService.record(
                action,
                "order_delivery_ticket",
                ticketId,
                null,
                summary,
                "DELIVERY",
                result,
                AuditService.labelForAction(action) + " ticketId=" + ticketId + " mesRequestId=" + mesRequestId,
                errorMessage
        );
    }

    private MesMaterialCallbackResp callbackResponse(
            boolean success,
            String message,
            String mesRequestId,
            String materialNo) {
        MesMaterialCallbackResp resp = new MesMaterialCallbackResp();
        resp.setSuccess(success);
        resp.setMsg(message);
        resp.setMesRequestId(mesRequestId);
        resp.setMaterialNo(trimToNull(materialNo));
        return resp;
    }

    private Map<String, Object> summary(OrderDeliveryTicket ticket, Long taskId, String mesRequestId, String materialNo, String status, String externalTraceId) {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("ticketId", ticket.getId());
        summary.put("taskId", taskId);
        summary.put("status", status);
        summary.put("mesRequestId", mesRequestId);
        summary.put("rfqId", ticket.getRfqId());
        summary.put("sourceType", ticket.getSourceType());
        summary.put("sourceOrderId", ticket.getSourceOrderId());
        summary.put("sourceItemId", ticket.getSourceItemId());
        summary.put("partNo", ticket.getPartNo());
        summary.put("version", ticket.getVersion());
        summary.put("internalProjectNo", ticket.getInternalProjectNo());
        if (!isBlank(materialNo)) {
            summary.put("materialNo", materialNo);
        }
        if (!isBlank(externalTraceId)) {
            summary.put("externalTraceId", externalTraceId.trim());
        }
        return summary;
    }

    private void appendSourceSyncSummary(
            Map<String, Object> summary,
            SourceItemMaterialNoSyncService.MaterialNoSyncResult sourceSyncResult) {
        SourceItemMaterialNoSyncService.MaterialNoSyncResult result = sourceSyncResult == null
                ? SourceItemMaterialNoSyncService.MaterialNoSyncResult.empty()
                : sourceSyncResult;
        summary.put("sourceSyncTargetCount", result.targetCount());
        summary.put("sourceSyncUpdatedCount", result.updatedCount());
        summary.put("sourceSyncIdempotentCount", result.idempotentCount());
        summary.put("rfqSourceUpdatedCount", result.rfqUpdatedCount());
        summary.put("salesOrderSourceUpdatedCount", result.salesOrderUpdatedCount());
    }

    private void appendDedupSummary(
            Map<String, Object> summary,
            MesMaterialDedupService.DedupResolveResult dedupResult) {
        summary.put("dedupResult", dedupResult.getType());
        summary.put("dedupKey", dedupResult.getDedupKey());
        String role = MesMaterialDedupService.RESULT_MASTER_CREATED.equals(dedupResult.getType())
                ? MesMaterialDedupService.ROLE_MASTER
                : MesMaterialDedupService.ROLE_FOLLOWER;
        summary.put("mesRequestRole", role);
        if (dedupResult.getMasterTicketId() != null) {
            summary.put("masterTicketId", dedupResult.getMasterTicketId());
        }
        if (!isBlank(dedupResult.getMasterMesRequestId())) {
            summary.put("masterMesRequestId", dedupResult.getMasterMesRequestId());
        }
    }

    private boolean isDedupUniqueConflict(
            RuntimeException error,
            MesMaterialDedupService.DedupResolveResult dedupResult) {
        if (dedupService == null || dedupResult == null || isBlank(dedupResult.getDedupKey())) {
            return false;
        }
        Throwable current = error;
        while (current != null) {
            if (current instanceof DataIntegrityViolationException) {
                return true;
            }
            String message = current.getMessage();
            if (message != null && message.contains("uk_delivery_mes_request_active_dedup")) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private void insertMesRequest(
            OrderDeliveryTicket ticket,
            Long taskId,
            String mesRequestId,
            MesMaterialDedupService.DedupResolveResult dedupResult) {
        if (mesRequestMapper == null) {
            return;
        }
        OrderDeliveryTicketMesRequest request = new OrderDeliveryTicketMesRequest();
        request.setTicketId(ticket.getId());
        request.setMesRequestId(mesRequestId);
        request.setSystemTaskId(taskId);
        request.setRequestStatus(STATUS_PROCESSING);
        if (dedupResult != null) {
            request.setDedupKey(dedupResult.getDedupKey());
            request.setRequestRole(MesMaterialDedupService.ROLE_MASTER);
            request.setFollowerCount(0);
        }
        request.setRequestMessage("MES material sync submitted");
        request.setRequestedAt(LocalDateTime.now());
        mesRequestMapper.insert(request);
    }

    private void updateMesRequest(String mesRequestId, String status, String materialNo, String externalTraceId, String message, boolean completed) {
        if (mesRequestMapper == null || isBlank(mesRequestId)) {
            return;
        }
        OrderDeliveryTicketMesRequest update = new OrderDeliveryTicketMesRequest();
        update.setRequestStatus(status);
        update.setMaterialNo(trimToNull(materialNo));
        update.setMesExternalTaskId(trimToNull(externalTraceId));
        update.setResponseMessage(abbreviate(message));
        update.setRespondedAt(LocalDateTime.now());
        update.setUpdatedAt(LocalDateTime.now());
        if (completed) {
            update.setCompletedAt(LocalDateTime.now());
        }
        UpdateWrapper<OrderDeliveryTicketMesRequest> wrapper = new UpdateWrapper<>();
        wrapper.eq("mes_request_id", mesRequestId);
        mesRequestMapper.update(update, wrapper);
    }

    private String generateMesRequestId(Integer ticketId) {
        return "MES-" + (ticketId == null ? "0" : ticketId) + "-" + UUID.randomUUID();
    }

    private String resolveSourceType(OrderDeliveryTicket ticket) {
        return isBlank(ticket.getSourceType()) ? ReleaseSourceType.RFQ.name() : ticket.getSourceType().trim();
    }

    private MesMaterialCallbackException callbackError(HttpStatus status, String message) {
        return new MesMaterialCallbackException(status, message);
    }

    private String required(String value, String message) {
        if (isBlank(value)) {
            throw new RuntimeException(message);
        }
        return value.trim();
    }

    private String abbreviate(String value) {
        String text = value == null ? "" : value.trim();
        return text.length() <= 500 ? text : text.substring(0, 500);
    }

    private String trimToNull(String value) {
        return isBlank(value) ? null : value.trim();
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private static final class MesMaterialResponseUnknownException extends RuntimeException {
        private MesMaterialResponseUnknownException(String message) {
            super(message);
        }
    }

    /**
     * MES 明确表示「同一物料身份已有任务」（已存在 / 处理中）。
     *
     * <p>这两种响应不是可以重试的失败：MES 侧确实存在任务，只是不属于本次请求。
     * 必须保留原 bizId 等人工核对，否则会形成「失败 → 重发 → 再失败」的死循环。</p>
     */
    private static final class MesMaterialDuplicateSubmitException extends RuntimeException {
        private MesMaterialDuplicateSubmitException(String message) {
            super(message);
        }
    }
}
