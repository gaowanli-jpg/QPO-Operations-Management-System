package com.machining.quote;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.machining.quote.dto.ReleaseSourceType;
import com.machining.quote.dto.mes.MesMaterialResolveReq;
import com.machining.quote.entity.MesIntegrationBatch;
import com.machining.quote.entity.MesMaterialBatchItem;
import com.machining.quote.entity.OrderDeliveryTicket;
import com.machining.quote.entity.OrderDeliveryTicketMesRequest;
import com.machining.quote.entity.OrderRfq;
import com.machining.quote.entity.SalesOrderHeader;
import com.machining.quote.mapper.MesIntegrationBatchMapper;
import com.machining.quote.mapper.MesMaterialBatchItemMapper;
import com.machining.quote.mapper.OrderDeliveryTicketMapper;
import com.machining.quote.mapper.OrderDeliveryTicketMesRequestMapper;
import com.machining.quote.mapper.OrderRfqMapper;
import com.machining.quote.mapper.SalesOrderHeaderMapper;
import com.machining.quote.service.mes.MesBatchFingerprintService;
import com.machining.quote.service.mes.MesCustomerRequirementsAssembler;
import com.machining.quote.service.mes.MesMaterialRequestAssembler;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * V71 三字段身份回填前的「材质可否证明」核对工具（只读，不做任何写操作）。
 *
 * <p>背景：批次行没有材质快照，{@code mes_integration_batch.request_fingerprint} 是唯一旁证。
 * 它是 Jackson（属性名排序 + NON_NULL）序列化后的 SHA-256，SQL 里算不出来，只能在 Java 侧重算。
 * 重算一致 ⇒ 出站材质与当前提单材质一致，可安全回填三字段键；
 * 重算不一致 ⇒ 无法证明，保留原两字段键并进人工核对，绝不猜材质。
 *
 * <p>这是打包前的一次性检查，不是 CI 断言，所以用系统属性做门禁：
 * <pre>
 *   set DB_URL=jdbc:postgresql://localhost:15432/postgres
 *   mvn -q test -Dtest=MesMaterialIdentityBackfillPreflightTest -Dqpo.mes.preflight=true ^
 *       -DargLine="-Djdk.attach.allowAttachSelf=true"
 * </pre>
 * 只对「本地恢复出来的库」执行，不要指向生产库——本测试会启动完整 Spring 上下文。
 *
 * <p>配套的只读 SQL 在 {@code docs/ops/mes-material-identity-preflight.sql}：Q2 查重复身份，
 * Q3 用同一套重建逻辑 + PostgreSQL 内置 {@code sha256()} 直接在正式库算指纹，不需要恢复库。
 * 两者已用两组数据交叉验证、哈希逐字节一致，所以生产核验优先用 SQL；本测试保留作交叉验证
 * 和「怀疑 SQL 重建与代码脱节」时的对照基准。
 */
@SpringBootTest(properties = {
        "erp.auth.enabled=false",
        "erp.upload-dir=${java.io.tmpdir}/quote-test/uploads/",
        "erp.backup-dir=${java.io.tmpdir}/quote-test/backups/",
        "spring.task.scheduling.enabled=false"
})
@EnabledIfSystemProperty(named = "qpo.mes.preflight", matches = "true")
class MesMaterialIdentityBackfillPreflightTest {

    /** 与 V70 之后的活动状态口径一致；改这里必须同步改 SQL 脚本与去重服务。 */
    private static final Set<String> ACTIVE_STATUSES = Set.of("PROCESSING", "CALLBACK_PENDING", "RESULT_UNKNOWN");
    private static final String BATCH_TYPE_MATERIAL = "MATERIAL_PREPARATION";

    @Autowired
    private OrderDeliveryTicketMesRequestMapper requestMapper;
    @Autowired
    private MesIntegrationBatchMapper batchMapper;
    @Autowired
    private MesMaterialBatchItemMapper batchItemMapper;
    @Autowired
    private OrderDeliveryTicketMapper ticketMapper;
    @Autowired
    private OrderRfqMapper orderMapper;
    @Autowired
    private SalesOrderHeaderMapper salesOrderHeaderMapper;
    @Autowired
    private Environment environment;

    /** 复用生产实现，保证重算与当初出站序列化完全一致。 */
    private final MesBatchFingerprintService fingerprintService = new MesBatchFingerprintService();
    private final MesMaterialRequestAssembler requestAssembler = new MesMaterialRequestAssembler();

    @Test
    void reportBackfillReadiness() {
        // 报告正文一律 ASCII：Windows 控制台默认 cp936，中文会乱码；说明见类注释。
        line("=== MES material identity backfill preflight (read-only) ===");
        line("datasource: " + environment.getProperty("spring.datasource.url"));
        line("");

        List<OrderDeliveryTicketMesRequest> activeRequests = requestMapper.selectList(
                new QueryWrapper<OrderDeliveryTicketMesRequest>()
                        .in("request_status", ACTIVE_STATUSES)
                        .orderByAsc("id"));

        Map<Long, List<OrderDeliveryTicketMesRequest>> byBatch = new LinkedHashMap<>();
        List<OrderDeliveryTicketMesRequest> presumed = new ArrayList<>();
        for (OrderDeliveryTicketMesRequest request : activeRequests) {
            if (request.getBatchId() != null) {
                byBatch.computeIfAbsent(request.getBatchId(), ignored -> new ArrayList<>()).add(request);
            } else {
                presumed.add(request);
            }
        }

        line("active ledger rows: " + activeRequests.size()
                + " (batch requests in " + byBatch.size() + " batch(es), refresh rows " + presumed.size() + ")");
        line("");

        Set<Integer> provable = new LinkedHashSet<>();
        Set<Integer> unprovable = new LinkedHashSet<>();

        line("--- batch fingerprint check ---");
        for (Map.Entry<Long, List<OrderDeliveryTicketMesRequest>> entry : byBatch.entrySet()) {
            verifyBatch(entry.getKey(), entry.getValue(), provable, unprovable);
        }

        line("");
        line("--- refresh ledger rows (no batch fingerprint, presumed from current ticket) ---");
        for (OrderDeliveryTicketMesRequest request : presumed) {
            OrderDeliveryTicket ticket = ticketMapper.selectById(request.getTicketId());
            line("  [PRESUMED] ticket=" + request.getTicketId()
                    + " biz=" + request.getMesRequestId()
                    + " status=" + request.getRequestStatus()
                    + " identity=" + identityOf(ticket));
        }

        line("");
        line("--- verdict ---");
        line("provable   (backfill 3-field key)   : " + provable.size() + " tickets " + provable);
        line("unprovable (keep old 2-field key)   : " + unprovable.size() + " tickets " + unprovable);
        line("presumed   (refresh rows)           : " + presumed.size() + " tickets");
        line("");
        line("NOTE: MISMATCH is not a data error. It only means the ticket changed after submission");
        line("      (including remark/description, which the fingerprint also covers). Do not guess");
        line("      the material for these rows; keep the old key and let ops reconcile.");
    }

    private void verifyBatch(Long batchId,
                             List<OrderDeliveryTicketMesRequest> requests,
                             Set<Integer> provable,
                             Set<Integer> unprovable) {
        MesIntegrationBatch batch = batchMapper.selectById(batchId);
        if (batch == null) {
            for (OrderDeliveryTicketMesRequest request : requests) {
                unprovable.add(request.getTicketId());
            }
            line("  [MISMATCH] batch=" + batchId + " batch record missing, tickets=" + ticketIds(requests));
            return;
        }
        if (batch.getRequestFingerprint() == null || batch.getRequestFingerprint().isBlank()) {
            for (OrderDeliveryTicketMesRequest request : requests) {
                unprovable.add(request.getTicketId());
            }
            line("  [MISMATCH] batch=" + batchId + " biz=" + batch.getBizId() + " request_fingerprint missing");
            return;
        }

        List<MesMaterialBatchItem> items = batchItemMapper.selectList(
                new QueryWrapper<MesMaterialBatchItem>()
                        .eq("batch_id", batchId)
                        .orderByAsc("id"));
        if (items.isEmpty()) {
            for (OrderDeliveryTicketMesRequest request : requests) {
                unprovable.add(request.getTicketId());
            }
            line("  [MISMATCH] batch=" + batchId + " biz=" + batch.getBizId() + " batch has no rows");
            return;
        }

        String computed;
        try {
            computed = fingerprintService.fingerprint(rebuildRequest(batch, items));
        } catch (RuntimeException error) {
            for (MesMaterialBatchItem item : items) {
                unprovable.add(item.getTicketId());
            }
            line("  [MISMATCH] batch=" + batchId + " biz=" + batch.getBizId()
                    + " request cannot be rebuilt: " + error.getMessage());
            return;
        }

        Set<Integer> batchTickets = new LinkedHashSet<>();
        for (MesMaterialBatchItem item : items) {
            batchTickets.add(item.getTicketId());
        }
        if (batch.getRequestFingerprint().equals(computed)) {
            provable.addAll(batchTickets);
            line("  [MATCH]    batch=" + batchId + " biz=" + batch.getBizId()
                    + " rows=" + items.size() + " tickets=" + batchTickets);
        } else {
            unprovable.addAll(batchTickets);
            line("  [MISMATCH] batch=" + batchId + " biz=" + batch.getBizId()
                    + " rows=" + items.size() + " tickets=" + batchTickets
                    + " stored=" + shortHash(batch.getRequestFingerprint())
                    + " computed=" + shortHash(computed));
        }
    }

    /**
     * 按 {@code MesMaterialSyncService#syncMaterialPlanGroup} 的组装顺序重建出站请求：
     * 根字段取首个主请求的提单/订单，items 按批次行 id 顺序（即当初 masters 的提交顺序）。
     * 任何字段对不上都会导致指纹不等，所以这里宁可少猜、不补齐默认值。
     */
    private MesMaterialResolveReq rebuildRequest(MesIntegrationBatch batch, List<MesMaterialBatchItem> items) {
        List<MesMaterialResolveReq.Item> requestItems = new ArrayList<>();
        String customerCode = null;
        String customerName = null;
        String internalProjectNo = null;
        boolean first = true;

        for (MesMaterialBatchItem item : items) {
            OrderDeliveryTicket ticket = ticketMapper.selectById(item.getTicketId());
            if (ticket == null) {
                throw new IllegalStateException("批次行 ticket 不存在: " + item.getTicketId());
            }
            OrderRfq order = ticket.getRfqId() == null ? null : orderMapper.selectById(ticket.getRfqId());
            if (first) {
                customerCode = resolveCustomerCode(ticket, order);
                customerName = order == null ? ticket.getCustomerName() : order.getCustomerName();
                internalProjectNo = ticket.getInternalProjectNo() == null
                        ? null
                        : ticket.getInternalProjectNo().trim();
                first = false;
            }
            MesMaterialResolveReq.Item requestItem = new MesMaterialResolveReq.Item();
            requestItem.setDrawingNo(required(ticket.getPartNo(), "图号为空"));
            requestItem.setDrawingVersion(ticket.getVersion() == null ? "" : ticket.getVersion().trim());
            requestItem.setMaterial(trimToNull(ticket.getMaterialName()));
            requestItem.setName(trimToNull(ticket.getDescription()));
            requestItem.setRemark(trimToNull(ticket.getRemark()));
            requestItem.setCustomerRequirements(MesCustomerRequirementsAssembler.assemble(
                    ticket.getMaterialName(), ticket.getSurfaceTreatment()));
            requestItems.add(requestItem);
        }
        return requestAssembler.assemble(
                batch.getBizId(), customerCode, customerName, internalProjectNo, requestItems);
    }

    /** 与 MesMaterialSyncService#resolveCustomerCode 保持一致。 */
    private String resolveCustomerCode(OrderDeliveryTicket ticket, OrderRfq order) {
        String sourceType = ticket.getSourceType() == null || ticket.getSourceType().isBlank()
                ? ReleaseSourceType.RFQ.name()
                : ticket.getSourceType().trim();
        if (ReleaseSourceType.SALES_ORDER.name().equals(sourceType)
                && ticket.getSourceOrderId() != null) {
            SalesOrderHeader header = salesOrderHeaderMapper.selectById(ticket.getSourceOrderId());
            if (header != null && header.getCustomerCode() != null && !header.getCustomerCode().isBlank()) {
                return header.getCustomerCode().trim();
            }
        }
        if (order != null && order.getCustomerCode() != null && !order.getCustomerCode().isBlank()) {
            return order.getCustomerCode().trim();
        }
        return null;
    }

    private String identityOf(OrderDeliveryTicket ticket) {
        if (ticket == null) {
            return "(ticket missing)";
        }
        return normalizeKeyPart(ticket.getPartNo())
                + "|" + normalizeKeyPart(ticket.getVersion())
                + "|" + (ticket.getMaterialName() == null ? "" : ticket.getMaterialName().trim());
    }

    private String normalizeKeyPart(String value) {
        return value == null ? "" : value.trim().toUpperCase(java.util.Locale.ROOT);
    }

    private String required(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(message);
        }
        return value.trim();
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private Set<Integer> ticketIds(List<OrderDeliveryTicketMesRequest> requests) {
        Set<Integer> ids = new LinkedHashSet<>();
        for (OrderDeliveryTicketMesRequest request : requests) {
            ids.add(request.getTicketId());
        }
        return ids;
    }

    private String shortHash(String value) {
        return value == null ? "null" : value;
    }

    private void line(String text) {
        System.out.println(text);
    }
}
