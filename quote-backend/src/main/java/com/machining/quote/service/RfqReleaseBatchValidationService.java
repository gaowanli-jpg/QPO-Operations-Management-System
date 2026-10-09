package com.machining.quote.service;

import com.machining.quote.domain.QuoteStatusWorkflow;
import com.machining.quote.dto.DeliveryGenerateReq;
import com.machining.quote.dto.ReleaseSourceType;
import com.machining.quote.entity.ItemQuoteVersion;
import com.machining.quote.entity.OrderRfq;
import com.machining.quote.mapper.OrderRfqMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
public class RfqReleaseBatchValidationService {
    private static final int MAX_BATCH_KEY_LENGTH = 100;
    private static final int MAX_DOCUMENT_TYPE_CODE_LENGTH = 50;
    private static final int MAX_TEXT_LENGTH = 120;
    private static final int MAX_TITLE_LENGTH = 255;
    private static final String DEFAULT_DOCUMENT_TYPE_CODE = "SO06";

    @Autowired
    private OrderRfqMapper orderRfqMapper;
    @Autowired(required = false)
    private DataScopeService dataScopeService;

    public BatchContext validate(DeliveryGenerateReq req) {
        return validate(req, false, false);
    }

    public BatchContext validateForAppend(DeliveryGenerateReq req) {
        return validate(req, false, true);
    }

    public BatchContext validateReplay(DeliveryGenerateReq req) {
        return validate(req, true, false);
    }

    public String requireReleaseBatchKey(DeliveryGenerateReq req) {
        if (req == null) {
            throw new RuntimeException("提单参数不能为空");
        }
        return normalizeRequired(req.getReleaseBatchKey(), "提单批次键", MAX_BATCH_KEY_LENGTH);
    }

    private BatchContext validate(DeliveryGenerateReq req, boolean replay, boolean allowItemLevelIdentifiers) {
        if (req == null) {
            throw new RuntimeException("提单参数不能为空");
        }
        List<Integer> rfqIds = normalizeRfqIds(req.getRfqIds());
        if (rfqIds.isEmpty()) {
            throw new RuntimeException("至少选择一个询价单");
        }
        if (req.getItems() == null || req.getItems().isEmpty()) {
            throw new RuntimeException("请至少选择一个通过议价的零件");
        }
        String releaseBatchKey = requireReleaseBatchKey(req);
        String documentTypeCode = normalizeRequired(
                defaultText(req.getDocumentTypeCode(), DEFAULT_DOCUMENT_TYPE_CODE), "单据类型编码", MAX_DOCUMENT_TYPE_CODE_LENGTH);
        LocalDate salesOrderDate = req.getSalesOrderDate();
        if (salesOrderDate == null) {
            throw new RuntimeException("下单日期不能为空");
        }
        String orderType = normalizeRequired(req.getOrderType(), "订单类型", MAX_TEXT_LENGTH);
        String internalProjectNo = allowItemLevelIdentifiers
                ? normalizeOptional(req.getInternalProjectNo(), "内部项目号", MAX_TEXT_LENGTH)
                : normalizeRequired(req.getInternalProjectNo(), "内部项目号", MAX_TEXT_LENGTH);
        String customerOrderNo = allowItemLevelIdentifiers
                ? normalizeOptional(req.getCustomerOrderNo(), "客户订单号", MAX_TEXT_LENGTH)
                : normalizeRequired(req.getCustomerOrderNo(), "客户订单号", MAX_TEXT_LENGTH);
        String customerProjectNo = normalizeOptional(req.getCustomerProjectNo(), "客户项目号", MAX_TEXT_LENGTH);
        String customerOrderTitle = normalizeOptional(req.getCustomerOrderTitle(), "客户下单抬头", MAX_TITLE_LENGTH);
        String projectNo = normalizeOptional(req.getProjectNo(), "项目号", MAX_TEXT_LENGTH);
        String arrangeType = defaultText(req.getArrangeType(), "计划");
        if (!SetHolder.ALLOWED_ORDER_TYPES.contains(orderType)) {
            throw new RuntimeException("订单类型不合法：" + orderType);
        }

        Map<Integer, OrderRfq> orders = loadAccessibleOrders(rfqIds);
        String customerCode = null;
        String customerShortName = null;
        String customerName = null;
        for (OrderRfq order : orders.values()) {
            if (!replay && !QuoteStatusWorkflow.canGenerateDeliveryTicket(order.getStatus())) {
                if (QuoteStatusWorkflow.PRICED.equals(order.getStatus())) {
                    throw new RuntimeException("请先在报价处理模块保存报价单后再生成提单");
                }
                throw new RuntimeException("只有已报价订单可以生成提单");
            }
            String currentCustomer = normalizeOptional(order.getCustomerName(), "客户名称", MAX_TITLE_LENGTH);
            if (currentCustomer == null) {
                throw new RuntimeException("询价单客户不能为空");
            }
            String currentCustomerCode = normalizeOptional(order.getCustomerCode(), "customerCode", MAX_TEXT_LENGTH);
            if (currentCustomerCode == null) {
                throw new RuntimeException("RFQ customerCode cannot be blank");
            }
            if (customerCode == null) {
                customerCode = currentCustomerCode;
                customerShortName = normalizeOptional(order.getCustomerShortName(), "customerShortName", MAX_TITLE_LENGTH);
                customerName = currentCustomer;
            } else if (!Objects.equals(customerCode, currentCustomerCode)) {
                throw new RuntimeException("一次议价提单只能包含同一客户的询价单");
            }
        }

        validateItemReferences(req.getItems(), rfqIds);
        String requestHash = calculateRequestHash(
                customerCode, rfqIds, req, documentTypeCode, salesOrderDate, customerOrderTitle,
                orderType, internalProjectNo, customerOrderNo, customerProjectNo, projectNo, arrangeType);
        String releaseBatchKeySummary = sha256(releaseBatchKey).substring(0, 12);
        return new BatchContext(
                rfqIds,
                orders,
                customerCode,
                customerShortName,
                customerName,
                documentTypeCode,
                salesOrderDate,
                customerOrderTitle,
                orderType,
                internalProjectNo,
                customerOrderNo,
                customerProjectNo,
                projectNo,
                arrangeType,
                releaseBatchKey,
                releaseBatchKeySummary,
                requestHash);
    }

    public void validateLockedItem(
            DeliveryGenerateReq.DeliveryItemReq line,
            ItemQuoteVersion item,
            BatchContext context) {
        if (line == null || item == null) {
            throw new RuntimeException("提单零件不存在");
        }
        if (line.getSourceType() != ReleaseSourceType.RFQ) {
            throw new RuntimeException("议价提单只支持 RFQ 询价订单");
        }
        Integer sourceOrderId = toInteger(line.getSourceOrderId());
        Integer sourceItemId = toInteger(line.getSourceItemId());
        if (sourceOrderId == null || !context.orders().containsKey(sourceOrderId)) {
            throw new RuntimeException("提单零件来源询价单无效");
        }
        if (sourceItemId == null || !Objects.equals(sourceItemId, item.getId())) {
            throw new RuntimeException("提单零件来源不一致");
        }
        if (!Objects.equals(sourceOrderId, item.getRfqId())) {
            throw new RuntimeException("提单零件不属于来源询价单");
        }
    }

    private Map<Integer, OrderRfq> loadAccessibleOrders(List<Integer> rfqIds) {
        Map<Integer, OrderRfq> loaded = new LinkedHashMap<>();
        orderRfqMapper.selectBatchIds(rfqIds).forEach(order -> loaded.put(order.getId(), order));
        if (loaded.isEmpty() && rfqIds.size() == 1) {
            OrderRfq order = orderRfqMapper.selectById(rfqIds.get(0));
            if (order != null) {
                loaded.put(order.getId(), order);
            }
        }
        if (loaded.size() != rfqIds.size()) {
            throw new RuntimeException("议价提单批次包含不存在或无权访问的询价单");
        }
        for (Integer rfqId : rfqIds) {
            OrderRfq order = loaded.get(rfqId);
            try {
                if (dataScopeService != null) {
                    dataScopeService.assertCanAccessOrder(order);
                }
            } catch (RuntimeException ex) {
                throw new RuntimeException("议价提单批次包含无权访问的询价单");
            }
        }
        return loaded;
    }

    private void validateItemReferences(List<DeliveryGenerateReq.DeliveryItemReq> lines, List<Integer> rfqIds) {
        LinkedHashSet<Integer> referencedRfqIds = new LinkedHashSet<>();
        for (DeliveryGenerateReq.DeliveryItemReq line : lines) {
            if (line == null || line.getSourceType() != ReleaseSourceType.RFQ) {
                throw new RuntimeException("议价提单只支持 RFQ 询价订单");
            }
            Integer sourceOrderId = toInteger(line.getSourceOrderId());
            Integer sourceItemId = toInteger(line.getSourceItemId());
            if (sourceOrderId == null || !rfqIds.contains(sourceOrderId) || sourceItemId == null) {
                throw new RuntimeException("提单零件来源不属于本次选择的询价单");
            }
            Integer itemId = line.getItemId();
            if (itemId != null && !Objects.equals(itemId, sourceItemId)) {
                throw new RuntimeException("提单零件来源不一致");
            }
            referencedRfqIds.add(sourceOrderId);
        }
        if (!referencedRfqIds.containsAll(rfqIds)) {
            throw new RuntimeException("每个询价单至少选择一个提单零件");
        }
    }

    private String calculateRequestHash(
            String customerCode,
            List<Integer> rfqIds,
            DeliveryGenerateReq req,
            String documentTypeCode,
            LocalDate salesOrderDate,
            String customerOrderTitle,
            String orderType,
            String internalProjectNo,
            String customerOrderNo,
            String customerProjectNo,
            String projectNo,
            String arrangeType) {
        String lines = req.getItems().stream()
                .map(line -> String.join(":",
                        String.valueOf(line.getSourceOrderId()),
                        String.valueOf(line.getSourceItemId()),
                        String.valueOf(line.getAcceptedQty())))
                .sorted()
                .collect(Collectors.joining("|"));
        String canonical = String.join("|",
                safe(customerCode),
                rfqIds.stream().map(String::valueOf).sorted().collect(Collectors.joining(",")),
                documentTypeCode,
                salesOrderDate.toString(),
                safe(customerOrderTitle),
                orderType,
                internalProjectNo,
                customerOrderNo,
                safe(customerProjectNo),
                safe(projectNo),
                arrangeType,
                lines);
        return sha256(canonical);
    }

    private List<Integer> normalizeRfqIds(List<Integer> rfqIds) {
        return new ArrayList<>(new LinkedHashSet<>(rfqIds == null ? List.of() : rfqIds.stream()
                .filter(Objects::nonNull)
                .toList()));
    }

    private String normalizeRequired(String value, String field, int maxLength) {
        String normalized = normalizeOptional(value, field, maxLength);
        if (normalized == null) {
            throw new RuntimeException(field + "不能为空");
        }
        return normalized;
    }

    private String normalizeOptional(String value, String field, int maxLength) {
        String normalized = value == null ? null : value.trim();
        if (normalized != null && normalized.isEmpty()) {
            normalized = null;
        }
        if (normalized != null && normalized.length() > maxLength) {
            throw new RuntimeException(field + "不能超过 " + maxLength + " 个字符");
        }
        return normalized;
    }

    private String defaultText(String value, String fallback) {
        return value == null || value.trim().isEmpty() ? fallback : value.trim();
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private Integer toInteger(Long value) {
        if (value == null || value > Integer.MAX_VALUE || value < Integer.MIN_VALUE) {
            return null;
        }
        return value.intValue();
    }

    private String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder(digest.length * 2);
            for (byte item : digest) {
                result.append(String.format("%02x", item));
            }
            return result.toString();
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is unavailable", ex);
        }
    }

    public record BatchContext(
            List<Integer> rfqIds,
            Map<Integer, OrderRfq> orders,
            String customerCode,
            String customerShortName,
            String customerName,
            String documentTypeCode,
            LocalDate salesOrderDate,
            String customerOrderTitle,
            String orderType,
            String internalProjectNo,
            String customerOrderNo,
            String customerProjectNo,
            String projectNo,
            String arrangeType,
            String releaseBatchKey,
            String releaseBatchKeySummary,
            String requestHash) {
    }

    private static final class SetHolder {
        private static final java.util.Set<String> ALLOWED_ORDER_TYPES =
                java.util.Set.of("备货订单", "正式订单");
    }
}
