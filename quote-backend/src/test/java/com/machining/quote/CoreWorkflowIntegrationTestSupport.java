package com.machining.quote;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.machining.quote.domain.QuoteStatusWorkflow;
import com.machining.quote.dto.DeliveryGenerateReq;
import com.machining.quote.dto.InternalTargetLeadTimeSetReq;
import com.machining.quote.dto.InternalTargetLeadTimeUpdateReq;
import com.machining.quote.dto.PageResult;
import com.machining.quote.dto.QuoteLeadTimeSetReq;
import com.machining.quote.dto.QuoteLeadTimeUpdateReq;
import com.machining.quote.dto.QuoteSubmitReq;
import com.machining.quote.dto.aps.ApsDeliveryItemDTO;
import com.machining.quote.entity.DictMaterial;
import com.machining.quote.entity.DictMaterialType;
import com.machining.quote.entity.CustomerMaster;
import com.machining.quote.entity.DictProcessCategory;
import com.machining.quote.entity.DictProcessDetail;
import com.machining.quote.entity.ItemQuoteVersion;
import com.machining.quote.entity.OrderDeliveryTicket;
import com.machining.quote.dto.ProcessSheetDTO;
import com.machining.quote.entity.OrderRfq;
import com.machining.quote.mapper.ItemQuoteVersionMapper;
import com.machining.quote.mapper.DictMaterialMapper;
import com.machining.quote.mapper.DictMaterialTypeMapper;
import com.machining.quote.mapper.CustomerMasterMapper;
import com.machining.quote.mapper.DictProcessCategoryMapper;
import com.machining.quote.mapper.DictProcessDetailMapper;
import com.machining.quote.mapper.OrderDeliveryTicketMapper;
import com.machining.quote.mapper.OrderRfqMapper;
import com.machining.quote.service.aps.ApsSourceService;
import com.machining.quote.service.AuditLogRetentionService;
import com.machining.quote.service.DeliveryService;
import com.machining.quote.service.EngineerService;
import com.machining.quote.service.InternalTargetLeadTimeService;
import com.machining.quote.service.OrderManagementService;
import com.machining.quote.service.QuoteProcessService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

@SpringBootTest(properties = {
        "erp.auth.enabled=false",
        "erp.upload-dir=${java.io.tmpdir}/quote-test/uploads/",
        "erp.backup-dir=${java.io.tmpdir}/quote-test/backups/",
        "spring.task.scheduling.enabled=false"
})
@Transactional
abstract class CoreWorkflowIntegrationTestSupport {
    @Autowired
    protected OrderRfqMapper orderMapper;
    @Autowired
    protected DictMaterialMapper materialMapper;
    @Autowired
    protected DictMaterialTypeMapper materialTypeMapper;
    @Autowired
    protected ItemQuoteVersionMapper itemMapper;
    @Autowired
    protected OrderDeliveryTicketMapper ticketMapper;
    @Autowired
    protected DeliveryService deliveryService;
    @Autowired
    protected EngineerService engineerService;
    @Autowired
    protected QuoteProcessService quoteProcessService;
    @Autowired
    protected InternalTargetLeadTimeService internalTargetLeadTimeService;
    @Autowired
    protected OrderManagementService orderManagementService;
    @Autowired
    protected ApsSourceService apsSourceService;

    @MockBean
    protected AuditLogRetentionService auditLogRetentionService;
    @Autowired
    protected CustomerMasterMapper customerMapper;
    @Autowired
    protected DictProcessCategoryMapper processCategoryMapper;
    @Autowired
    protected DictProcessDetailMapper processDetailMapper;

    protected ItemQuoteVersion quotedItem(OrderRfq order, String partNo) {
        ItemQuoteVersion item = new ItemQuoteVersion();
        item.setRfqId(order.getId());
        item.setPartNo(partNo);
        item.setVersion("A");
        item.setDescription(partNo + "描述");
        item.setQty(1);
        item.setDrawingUrl("/uploads/test/" + partNo + ".pdf");
        item.setTaxIncludedUnitPrice(10.0);
        item.setTaxIncludedTotalPrice(10.0);
        item.setStatus("已报价");
        itemMapper.insert(item);
        return itemMapper.selectById(item.getId());
    }

    protected ItemQuoteVersion pendingItem(OrderRfq order, String partNo, String version) {
        ItemQuoteVersion item = new ItemQuoteVersion();
        item.setRfqId(order.getId());
        item.setPartNo(partNo);
        item.setVersion(version);
        item.setDescription(partNo + "描述");
        item.setQty(1);
        item.setDrawingUrl("/uploads/test/" + partNo + ".pdf");
        item.setStatus("待核价");
        itemMapper.insert(item);
        return itemMapper.selectById(item.getId());
    }

    protected ItemQuoteVersion releasedReusableItem(OrderRfq order, String partNo, String version, double unitPrice) {
        return reusableItem(order, partNo, version, unitPrice, QuoteStatusWorkflow.ITEM_RELEASED);
    }

    protected ItemQuoteVersion reusableItem(OrderRfq order, String partNo, String version, double unitPrice, String status) {
        ItemQuoteVersion item = new ItemQuoteVersion();
        item.setRfqId(order.getId());
        item.setPartNo(partNo);
        item.setVersion(version);
        item.setDescription(partNo + "历史描述");
        item.setQty(1);
        item.setDrawingUrl("/uploads/test/" + partNo + ".pdf");
        applyStandardMaterialSnapshot(item, unitPrice);
        item.setProcessRoute(List.of(standardProcessRoute("TEST_CNC", "测试数控", "CNC", 10.0, 1.0)));
        item.setTaxIncludedUnitPrice(unitPrice);
        item.setTaxIncludedTotalPrice(unitPrice);
        item.setStatus(status);
        itemMapper.insert(item);
        return itemMapper.selectById(item.getId());
    }

    protected void applyStandardMaterialSnapshot(ItemQuoteVersion item, double materialCost) {
        DictMaterial material = ensureRepeatMaterial();
        item.setMaterialName(material.getMaterialName());
        item.setEngineeringMaterialCode(material.getMaterialCode());
        item.setEngineeringMaterialName(material.getMaterialName());
        item.setEngineeringMaterialTypeCode("RAW_MATERIAL");
        item.setEngineeringMaterialTypeName("\u539f\u6750\u6599");
        item.setEngineeringMaterialCategory(material.getMaterialCategory());
        item.setMaterialShape("\u677f\u6750");
        item.setMaterialData(standardMaterialData(material, materialCost));
    }

    protected Map<String, Object> standardMaterialData(DictMaterial material, double materialCost) {
        Map<String, Object> materialData = new LinkedHashMap<>();
        materialData.put("materialCode", material.getMaterialCode());
        materialData.put("materialName", material.getMaterialName());
        materialData.put("name", material.getMaterialName());
        materialData.put("materialTypeCode", "RAW_MATERIAL");
        materialData.put("materialTypeName", "\u539f\u6750\u6599");
        materialData.put("materialCategory", material.getMaterialCategory());
        materialData.put("shape", "\u677f\u6750");
        materialData.put("weight", materialCost);
        materialData.put("cost", materialCost);
        return materialData;
    }

    protected DictMaterial ensureRepeatMaterial() {
        DictMaterial existing = materialMapper.selectOne(new QueryWrapper<DictMaterial>()
                .eq("material_code", "MAT-CORE-REPEAT"));
        if (existing != null) {
            return existing;
        }
        DictMaterialType materialType = ensureRepeatMaterialType();
        DictMaterial material = new DictMaterial();
        material.setMaterialCode("MAT-CORE-REPEAT");
        material.setMaterialName("Core repeat test material");
        material.setMaterialTypeId(materialType.getId());
        material.setMaterialCategory("\u94a2\u94c1");
        material.setDensity(1.0);
        material.setPlateDensity(1.0);
        material.setPlatePrice(1.0);
        material.setBarDensity(1.0);
        material.setBarPrice(1.0);
        material.setTubeDensity(1.0);
        material.setTubePrice(1.0);
        material.setEnabled(true);
        material.setSortNo(0);
        material.setRemarks("test material");
        materialMapper.insert(material);
        return material;
    }

    protected DictMaterialType ensureRepeatMaterialType() {
        DictMaterialType existing = materialTypeMapper.selectOne(new QueryWrapper<DictMaterialType>()
                .eq("type_code", "RAW_MATERIAL")
                .or()
                .eq("type_name", "\u539f\u6750\u6599")
                .last("LIMIT 1"));
        if (existing != null) {
            return existing;
        }
        DictMaterialType materialType = new DictMaterialType();
        materialType.setTypeCode("RAW_MATERIAL");
        materialType.setTypeName("\u539f\u6750\u6599");
        materialType.setEnabled(true);
        materialType.setSortNo(1);
        materialType.setRemarks("Test default material type");
        materialTypeMapper.insert(materialType);
        return materialType;
    }

    protected Map<String, Object> standardProcessRoute(String processCode,
                                                     String categoryName,
                                                     String detailName,
                                                     double hourlyRate,
                                                     double unitWorkHours) {
        TestProcess process = ensureTestProcess(processCode, categoryName, detailName, hourlyRate);
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("routeType", "PROCESS");
        row.put("sourceType", "process");
        row.put("sortNo", 10);
        row.put("processCategoryId", process.categoryId());
        row.put("processDetailId", process.detailId());
        row.put("processCode", processCode);
        row.put("categoryName", categoryName);
        row.put("detailName", detailName);
        row.put("processName", detailName);
        row.put("calcType", "工时");
        row.put("hourlyRate", hourlyRate);
        row.put("unitWorkHours", unitWorkHours);
        row.put("inputVal", unitWorkHours);
        row.put("unitRate", hourlyRate);
        row.put("lineCost", hourlyRate * unitWorkHours);
        return row;
    }

    protected TestProcess ensureTestProcess(String processCode, String categoryName, String detailName, double hourlyRate) {
        DictProcessCategory category = processCategoryMapper.selectOne(new QueryWrapper<DictProcessCategory>()
                .eq("process_code", processCode));
        if (category == null) {
            category = new DictProcessCategory();
            category.setProcessCode(processCode);
            category.setCategoryName(categoryName);
            category.setHourlyRate(BigDecimal.valueOf(hourlyRate));
            category.setRemarks("test process category");
            category.setSourceType("LOCAL");
            category.setEnabled(true);
            category.setSortNo(0);
            processCategoryMapper.insert(category);
        }

        DictProcessDetail detail = processDetailMapper.selectOne(new QueryWrapper<DictProcessDetail>()
                .eq("category_id", category.getId())
                .eq("detail_name", detailName));
        if (detail == null) {
            detail = new DictProcessDetail();
            detail.setCategoryId(category.getId());
            detail.setDetailName(detailName);
            detail.setDetailRemarks("test process detail");
            detail.setEnabled(true);
            detail.setSortNo(0);
            processDetailMapper.insert(detail);
        }
        return new TestProcess(category.getId(), detail.getId());
    }

    protected DeliveryGenerateReq.DeliveryItemReq deliveryLine(ItemQuoteVersion item) {
        DeliveryGenerateReq.DeliveryItemReq line = new DeliveryGenerateReq.DeliveryItemReq();
        line.setItemId(item.getId());
        line.setAcceptedQty(item.getQty());
        return line;
    }

    protected void ensureCustomer(String customerName) {
        CustomerMaster customer = customerMapper.selectOne(new QueryWrapper<CustomerMaster>()
                .eq("customer_name", customerName)
                .last("LIMIT 1"));
        if (customer == null) {
            customer = new CustomerMaster();
            customer.setCustomerCode("TEST-" + System.nanoTime());
            customer.setCustomerShortName(customerName);
            customer.setCustomerName(customerName);
            customer.setEnabled(true);
            customerMapper.insert(customer);
        }
        UpdateWrapper<OrderRfq> update = new UpdateWrapper<>();
        update.eq("customer_name", customerName)
                .and(wrapper -> wrapper.isNull("customer_code").or().eq("customer_code", ""))
                .set("customer_code", customer.getCustomerCode())
                .set("customer_short_name", customer.getCustomerShortName());
        orderMapper.update(null, update);
    }

    protected DeliveryGenerateReq deliveryReq(OrderRfq order, ItemQuoteVersion item, int acceptedQty) {
        return deliveryReq(order, item, acceptedQty, "正式订单");
    }

    protected DeliveryGenerateReq deliveryReq(OrderRfq order, ItemQuoteVersion item, int acceptedQty, String orderType) {
        DeliveryGenerateReq req = new DeliveryGenerateReq();
        req.setRfqIds(List.of(order.getId()));
        req.setOrderType(orderType);
        req.setInternalProjectNo("IP-" + order.getId() + "-" + acceptedQty);
        req.setCustomerOrderNo("CO-" + order.getId() + "-" + acceptedQty);
        req.setCustomerProjectNo("CP-" + order.getId());
        req.setArrangeType("计划");
        req.setSalesOrderDate(LocalDate.of(2026, 4, 1));
        DeliveryGenerateReq.DeliveryItemReq line = new DeliveryGenerateReq.DeliveryItemReq();
        line.setItemId(item.getId());
        line.setAcceptedQty(acceptedQty);
        req.setItems(List.of(line));
        return req;
    }

    protected OrderRfq orderWithItemsTicketsAndSheets(
            String customerName,
            String rfqPrefix,
            String status,
            int itemCount,
            int quotedCount,
            int missingDrawingCount,
            int ticketCount,
            int processSheetCount) {
        OrderRfq order = new OrderRfq();
        order.setRfqNo(rfqPrefix + "-" + System.nanoTime());
        order.setCustomerName(customerName);
        order.setStatus(status);
        orderMapper.insert(order);

        for (int i = 0; i < itemCount; i++) {
            ItemQuoteVersion item = new ItemQuoteVersion();
            item.setRfqId(order.getId());
            item.setPartNo(rfqPrefix + "-P" + i);
            item.setVersion("A");
            item.setDescription("分页测试零件");
            item.setQty(1);
            item.setStatus(i < quotedCount ? "已报价" : "待核价");
            if (i < quotedCount) {
                item.setTaxIncludedUnitPrice(10.0);
                item.setTaxIncludedTotalPrice(10.0);
            }
            if (i >= missingDrawingCount) {
                item.setDrawingUrl("/uploads/test/" + rfqPrefix + "-P" + i + ".pdf");
            }
            itemMapper.insert(item);
        }

        for (int i = 0; i < ticketCount; i++) {
            OrderDeliveryTicket ticket = new OrderDeliveryTicket();
            ticket.setRfqId(order.getId());
            ticket.setPartNo(rfqPrefix + "-T" + i);
            ticket.setQty(3);
            ticketMapper.insert(ticket);
        }

        return order;
    }

    protected String source(String relativePath) throws Exception {
        return Files.readString(Path.of(relativePath), StandardCharsets.UTF_8);
    }

    protected String optionalSource(String relativePath) throws Exception {
        Path path = Path.of(relativePath);
        return Files.exists(path) ? Files.readString(path, StandardCharsets.UTF_8) : "";
    }

    protected String methodBody(String source, String startToken, String endToken) {
        int start = source.indexOf(startToken);
        if (start < 0) {
            return "";
        }
        int end = source.indexOf(endToken, start);
        return end < 0 ? source.substring(start) : source.substring(start, end);
    }

    protected record TestProcess(Long categoryId, Long detailId) {
    }
}
