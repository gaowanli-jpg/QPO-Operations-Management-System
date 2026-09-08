package com.machining.quote.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.machining.quote.config.DataScopeAccessDeniedException;
import com.machining.quote.entity.ItemQuoteVersion;
import com.machining.quote.entity.OrderDeliveryTicket;
import com.machining.quote.entity.OrderRfq;
import com.machining.quote.entity.SalesOrderHeader;
import com.machining.quote.entity.SalesOrderItem;
import com.machining.quote.mapper.ItemQuoteVersionMapper;
import com.machining.quote.mapper.OrderDeliveryTicketMapper;
import com.machining.quote.mapper.OrderRfqMapper;
import com.machining.quote.mapper.SalesOrderHeaderMapper;
import com.machining.quote.mapper.SalesOrderItemMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class DrawingFolderAccessService {
    @Autowired
    private BusinessFileService businessFileService;
    @Autowired(required = false)
    private OrderRfqMapper orderRfqMapper;
    @Autowired(required = false)
    private SalesOrderHeaderMapper salesOrderHeaderMapper;
    @Autowired(required = false)
    private ItemQuoteVersionMapper itemQuoteVersionMapper;
    @Autowired(required = false)
    private SalesOrderItemMapper salesOrderItemMapper;
    @Autowired(required = false)
    private OrderDeliveryTicketMapper orderDeliveryTicketMapper;
    @Autowired(required = false)
    private DataScopeService dataScopeService;

    public List<BusinessFileService.DrawingFolderFile> listFiles(String rawFolderUrl) {
        if (businessFileService == null) {
            throw new IllegalStateException("图纸文件服务不可用");
        }
        String folderUrl = businessFileService.normalizeDrawingFolderUrl(rawFolderUrl);
        ReferenceAccess access = referencedAndAccessible(folderUrl);
        if (!access.referenced()) {
            throw new IllegalArgumentException("图纸文件夹不存在或未绑定订单");
        }
        if (!access.accessible()) {
            throw new DataScopeAccessDeniedException("当前账号无权访问该图纸文件夹");
        }
        return businessFileService.listDrawingFolderFiles(folderUrl);
    }

    private ReferenceAccess referencedAndAccessible(String folderUrl) {
        ReferenceAccess access = ReferenceAccess.none();
        access = access.merge(checkRfqHeaders(folderUrl));
        if (access.accessible()) {
            return access;
        }
        access = access.merge(checkSalesOrderHeaders(folderUrl));
        if (access.accessible()) {
            return access;
        }
        access = access.merge(checkRfqItems(folderUrl));
        if (access.accessible()) {
            return access;
        }
        access = access.merge(checkSalesOrderItems(folderUrl));
        if (access.accessible()) {
            return access;
        }
        return access.merge(checkDeliveryTickets(folderUrl));
    }

    private ReferenceAccess checkRfqHeaders(String folderUrl) {
        if (orderRfqMapper == null) {
            return ReferenceAccess.none();
        }
        List<OrderRfq> orders = safeList(orderRfqMapper.selectList(new QueryWrapper<OrderRfq>()
                .eq("drawing_folder_url", folderUrl)
                .last("LIMIT 20")));
        ReferenceAccess access = ReferenceAccess.fromReference(!orders.isEmpty());
        for (OrderRfq order : orders) {
            if (canAccessOrder(order)) {
                return ReferenceAccess.granted();
            }
        }
        return access;
    }

    private ReferenceAccess checkSalesOrderHeaders(String folderUrl) {
        if (salesOrderHeaderMapper == null) {
            return ReferenceAccess.none();
        }
        List<SalesOrderHeader> headers = safeList(salesOrderHeaderMapper.selectList(new QueryWrapper<SalesOrderHeader>()
                .eq("drawing_folder_url", folderUrl)
                .last("LIMIT 20")));
        ReferenceAccess access = ReferenceAccess.fromReference(!headers.isEmpty());
        for (SalesOrderHeader header : headers) {
            if (canAccessSalesOrder(header)) {
                return ReferenceAccess.granted();
            }
        }
        return access;
    }

    private ReferenceAccess checkRfqItems(String folderUrl) {
        if (itemQuoteVersionMapper == null || orderRfqMapper == null) {
            return ReferenceAccess.none();
        }
        List<ItemQuoteVersion> items = safeList(itemQuoteVersionMapper.selectList(new QueryWrapper<ItemQuoteVersion>()
                .eq("drawing_url", folderUrl)
                .last("LIMIT 20")));
        ReferenceAccess access = ReferenceAccess.fromReference(!items.isEmpty());
        for (ItemQuoteVersion item : items) {
            if (item == null || item.getRfqId() == null) {
                continue;
            }
            OrderRfq order = orderRfqMapper.selectById(item.getRfqId());
            if (canAccessOrder(order)) {
                return ReferenceAccess.granted();
            }
        }
        return access;
    }

    private ReferenceAccess checkSalesOrderItems(String folderUrl) {
        if (salesOrderItemMapper == null || salesOrderHeaderMapper == null) {
            return ReferenceAccess.none();
        }
        List<SalesOrderItem> items = safeList(salesOrderItemMapper.selectList(new QueryWrapper<SalesOrderItem>()
                .eq("drawing_url", folderUrl)
                .last("LIMIT 20")));
        ReferenceAccess access = ReferenceAccess.fromReference(!items.isEmpty());
        for (SalesOrderItem item : items) {
            if (item == null || item.getSalesOrderId() == null) {
                continue;
            }
            SalesOrderHeader header = salesOrderHeaderMapper.selectById(item.getSalesOrderId());
            if (canAccessSalesOrder(header)) {
                return ReferenceAccess.granted();
            }
        }
        return access;
    }

    private ReferenceAccess checkDeliveryTickets(String folderUrl) {
        if (orderDeliveryTicketMapper == null) {
            return ReferenceAccess.none();
        }
        List<OrderDeliveryTicket> tickets = safeList(orderDeliveryTicketMapper.selectList(new QueryWrapper<OrderDeliveryTicket>()
                .eq("drawing_url", folderUrl)
                .last("LIMIT 20")));
        ReferenceAccess access = ReferenceAccess.fromReference(!tickets.isEmpty());
        for (OrderDeliveryTicket ticket : tickets) {
            if (canAccessDeliveryTicket(ticket)) {
                return ReferenceAccess.granted();
            }
        }
        return access;
    }

    private boolean canAccessOrder(OrderRfq order) {
        if (order == null) {
            return false;
        }
        if (dataScopeService == null) {
            return true;
        }
        try {
            dataScopeService.assertCanAccessOrder(order);
            return true;
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    private boolean canAccessSalesOrder(SalesOrderHeader header) {
        if (header == null) {
            return false;
        }
        if (dataScopeService == null) {
            return true;
        }
        try {
            dataScopeService.assertCanAccessSalesOrder(header);
            return true;
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    private boolean canAccessDeliveryTicket(OrderDeliveryTicket ticket) {
        if (ticket == null) {
            return false;
        }
        if (dataScopeService == null) {
            return true;
        }
        try {
            dataScopeService.assertCanAccessDeliveryTicket(ticket);
            return true;
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    private <T> List<T> safeList(List<T> values) {
        return values == null ? List.of() : values;
    }

    private record ReferenceAccess(boolean referenced, boolean accessible) {
        static ReferenceAccess none() {
            return new ReferenceAccess(false, false);
        }

        static ReferenceAccess fromReference(boolean referenced) {
            return new ReferenceAccess(referenced, false);
        }

        static ReferenceAccess granted() {
            return new ReferenceAccess(true, true);
        }

        ReferenceAccess merge(ReferenceAccess other) {
            if (other == null) {
                return this;
            }
            return new ReferenceAccess(referenced || other.referenced, accessible || other.accessible);
        }
    }
}
