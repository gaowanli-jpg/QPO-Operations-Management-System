package com.machining.quote.service;

import com.machining.quote.domain.QuoteStatusWorkflow;
import com.machining.quote.dto.OrderStatusChangeReq;
import com.machining.quote.dto.OrderStatusChangeResult;
import org.springframework.stereotype.Service;

@Service
public class OrderStatusManagementService {
    public OrderStatusChangeResult rollbackQuoted(Integer rfqId, OrderStatusChangeReq req) {
        return skeletonResult(rfqId, QuoteStatusWorkflow.QUOTED, QuoteStatusWorkflow.PRICED, "已报价退回已核价");
    }

    public OrderStatusChangeResult rollbackPriced(Integer rfqId, OrderStatusChangeReq req) {
        return skeletonResult(rfqId, QuoteStatusWorkflow.PRICED, QuoteStatusWorkflow.UNPRICED, "已核价退回未核价");
    }

    public OrderStatusChangeResult discard(Integer rfqId, OrderStatusChangeReq req) {
        return skeletonResult(rfqId, null, QuoteStatusWorkflow.DISCARDED, "废弃订单");
    }

    public OrderStatusChangeResult restore(Integer rfqId, OrderStatusChangeReq req) {
        return skeletonResult(rfqId, QuoteStatusWorkflow.DISCARDED, QuoteStatusWorkflow.PRICED, "恢复废弃订单");
    }

    private OrderStatusChangeResult skeletonResult(Integer rfqId, String fromStatus, String toStatus, String actionName) {
        OrderStatusChangeResult result = new OrderStatusChangeResult();
        result.setRfqId(rfqId);
        result.setFromStatus(fromStatus);
        result.setToStatus(toStatus);
        result.setUpdatedItemCount(0);
        result.setMessage(actionName + "接口已就绪，实际状态变更将在阶段 2 实现");
        return result;
    }
}
