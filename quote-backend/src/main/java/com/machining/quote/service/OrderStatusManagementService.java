package com.machining.quote.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.machining.quote.domain.QuoteStatusWorkflow;
import com.machining.quote.dto.OrderStatusChangeReq;
import com.machining.quote.dto.OrderStatusChangeResult;
import com.machining.quote.entity.ItemQuoteVersion;
import com.machining.quote.entity.OrderDeliveryTicket;
import com.machining.quote.entity.OrderRfq;
import com.machining.quote.mapper.ItemQuoteVersionMapper;
import com.machining.quote.mapper.OrderDeliveryTicketMapper;
import com.machining.quote.mapper.OrderRfqMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;

@Service
public class OrderStatusManagementService {
    private static final Logger log = LoggerFactory.getLogger(OrderStatusManagementService.class);

    @Autowired
    private OrderRfqMapper orderMapper;
    @Autowired
    private ItemQuoteVersionMapper itemMapper;
    @Autowired
    private OrderDeliveryTicketMapper ticketMapper;
    @Autowired(required = false)
    private DataScopeService dataScopeService;
    @Autowired
    private AuditService auditService;

    @Transactional
    public OrderStatusChangeResult rollbackQuoted(Integer rfqId, OrderStatusChangeReq req) {
        return changeStatus(
                rfqId,
                req,
                "ORDER_STATUS_ROLLBACK_QUOTED",
                "已报价退回已核价",
                QuoteStatusWorkflow::nextAfterRollbackQuoted
        );
    }

    @Transactional
    public OrderStatusChangeResult rollbackPriced(Integer rfqId, OrderStatusChangeReq req) {
        return changeStatus(
                rfqId,
                req,
                "ORDER_STATUS_ROLLBACK_PRICED",
                "已核价退回未核价",
                QuoteStatusWorkflow::nextAfterRollbackPriced
        );
    }

    @Transactional
    public OrderStatusChangeResult discard(Integer rfqId, OrderStatusChangeReq req) {
        return changeStatus(
                rfqId,
                req,
                "ORDER_DISCARD",
                "废弃订单",
                QuoteStatusWorkflow::nextAfterDiscard
        );
    }

    @Transactional
    public OrderStatusChangeResult restore(Integer rfqId, OrderStatusChangeReq req) {
        return changeStatus(
                rfqId,
                req,
                "ORDER_RESTORE",
                "恢复废弃订单",
                (currentStatus, hasDeliveryTickets) -> QuoteStatusWorkflow.nextAfterRestore(currentStatus)
        );
    }

    private OrderStatusChangeResult changeStatus(
            Integer rfqId,
            OrderStatusChangeReq req,
            String auditAction,
            String actionName,
            BiFunction<String, Boolean, String> transition) {
        if (rfqId == null) {
            throw new RuntimeException("订单 ID 不能为空");
        }
        long startedAt = System.nanoTime();
        String fromStatus = null;
        String toStatus = null;
        Long ticketCount = null;
        try {
            log.info("Order status change started action={} rfqId={}", auditAction, rfqId);
            OrderRfq order = orderMapper.selectByIdForUpdate(rfqId);
            if (order == null) {
                throw new IllegalStateException("订单不存在");
            }
            fromStatus = order.getStatus();
            if (dataScopeService != null) {
                dataScopeService.assertCanAccessOrder(order);
            }

            ticketCount = countDeliveryTickets(rfqId);
            boolean hasDeliveryTickets = ticketCount != null && ticketCount > 0;
            toStatus = transition.apply(fromStatus, hasDeliveryTickets);

            List<ItemQuoteVersion> items = listItems(rfqId);
            if (items == null) {
                items = List.of();
            }
            Map<String, Long> beforeItemSummary = statusSummary(items);

            order.setStatus(toStatus);
            orderMapper.updateById(order);

            int updatedItemCount = 0;
            for (ItemQuoteVersion item : items) {
                if (item == null || isReleasedItem(item.getStatus())) {
                    continue;
                }
                item.setStatus(toStatus);
                itemMapper.updateById(item);
                updatedItemCount++;
            }

            String message = buildMessage(actionName, order, fromStatus, toStatus, req);
            auditService.record(
                    auditAction,
                    "order_rfq",
                    rfqId,
                    beforeData(fromStatus, beforeItemSummary, ticketCount, req),
                    afterData(toStatus, updatedItemCount, statusSummary(items)),
                    "ORDER",
                    "SUCCESS",
                    message,
                    null
            );

            OrderStatusChangeResult result = new OrderStatusChangeResult();
            result.setRfqId(order.getId());
            result.setRfqNo(order.getRfqNo());
            result.setFromStatus(fromStatus);
            result.setToStatus(toStatus);
            result.setUpdatedItemCount(updatedItemCount);
            result.setMessage(message);
            log.info("Order status change completed action={} rfqId={} rfqNo={} fromStatus={} toStatus={} updatedItemCount={} ticketCount={} durationMs={}",
                    auditAction, rfqId, order.getRfqNo(), fromStatus, toStatus, updatedItemCount,
                    ticketCount, elapsedMs(startedAt));
            return result;
        } catch (RuntimeException e) {
            if (isExpectedRejection(e)) {
                log.warn("Order status change rejected action={} rfqId={} fromStatus={} toStatus={} ticketCount={} reason={}",
                        auditAction, rfqId, fromStatus, toStatus, ticketCount, safeMessage(e));
            } else {
                log.error("Order status change failed action={} rfqId={} fromStatus={} toStatus={} ticketCount={}",
                        auditAction, rfqId, fromStatus, toStatus, ticketCount, e);
            }
            throw e;
        }
    }

    private Long countDeliveryTickets(Integer rfqId) {
        QueryWrapper<OrderDeliveryTicket> query = new QueryWrapper<>();
        query.eq("rfq_id", rfqId);
        return ticketMapper.selectCount(query);
    }

    private List<ItemQuoteVersion> listItems(Integer rfqId) {
        QueryWrapper<ItemQuoteVersion> query = new QueryWrapper<>();
        query.eq("rfq_id", rfqId);
        return itemMapper.selectList(query);
    }

    private boolean isReleasedItem(String status) {
        return QuoteStatusWorkflow.ITEM_RELEASED.equals(status);
    }

    private Map<String, Long> statusSummary(List<ItemQuoteVersion> items) {
        Map<String, Long> summary = new LinkedHashMap<>();
        if (items == null) {
            return summary;
        }
        for (ItemQuoteVersion item : items) {
            String status = item == null || item.getStatus() == null ? "未填写" : item.getStatus();
            summary.put(status, summary.getOrDefault(status, 0L) + 1);
        }
        return summary;
    }

    private Map<String, Object> beforeData(
            String fromStatus,
            Map<String, Long> itemStatusSummary,
            Long ticketCount,
            OrderStatusChangeReq req) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("status", fromStatus);
        data.put("itemStatusSummary", itemStatusSummary);
        data.put("ticketCount", ticketCount == null ? 0 : ticketCount);
        String reason = safeReason(req);
        if (reason != null) {
            data.put("reason", reason);
        }
        return data;
    }

    private Map<String, Object> afterData(String toStatus, int updatedItemCount, Map<String, Long> itemStatusSummary) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("status", toStatus);
        data.put("updatedItemCount", updatedItemCount);
        data.put("itemStatusSummary", itemStatusSummary);
        return data;
    }

    private String buildMessage(String actionName, OrderRfq order, String fromStatus, String toStatus, OrderStatusChangeReq req) {
        String rfqNo = order.getRfqNo() == null ? String.valueOf(order.getId()) : order.getRfqNo();
        String message = actionName + "：RFQ " + rfqNo + "，" + fromStatus + " -> " + toStatus;
        String reason = safeReason(req);
        if (reason != null) {
            message += "；原因：" + reason;
        }
        return message;
    }

    private String safeReason(OrderStatusChangeReq req) {
        if (req == null || req.getReason() == null) {
            return null;
        }
        String reason = req.getReason().trim();
        if (reason.isEmpty()) {
            return null;
        }
        return reason.length() > 200 ? reason.substring(0, 200) : reason;
    }

    private boolean isExpectedRejection(RuntimeException e) {
        String message = e.getMessage();
        return e instanceof IllegalStateException
                || (message != null && (message.contains("No data permission") || message.contains("不存在")));
    }

    private String safeMessage(RuntimeException e) {
        return e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
    }

    private long elapsedMs(long startedAt) {
        return (System.nanoTime() - startedAt) / 1_000_000;
    }
}
