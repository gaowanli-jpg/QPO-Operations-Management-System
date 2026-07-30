package com.machining.quote.service;

import com.alibaba.excel.EasyExcel;
import com.machining.quote.dto.SalesOrderExcelParseResult;
import com.machining.quote.dto.u9.U9SalesOrderHeadDTO;
import com.machining.quote.dto.u9.U9SalesOrderLineDTO;
import com.machining.quote.dto.u9.U9SalesOrderShipLineDTO;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class SalesOrderExcelParser {
    private static final List<String> REQUIRED_HEADERS = List.of(
            "客户编码",
            "单据类型编码",
            "客户订单号",
            "业务日期",
            "U9料号",
            "客户料号",
            "客户品名",
            "数量",
            "销售单位",
            "计价单位",
            "单价",
            "交期"
    );

    public SalesOrderExcelParseResult parse(InputStream inputStream, String batchNo) {
        try {
            List<Map<Integer, String>> rows = EasyExcel.read(inputStream)
                    .headRowNumber(0)
                    .sheet()
                    .doReadSync();
            if (rows == null || rows.isEmpty()) {
                throw new IllegalArgumentException("销售订单导入表格为空");
            }

            Map<Integer, String> headerRow = rows.get(0);
            Map<String, Integer> headerIndexes = buildHeaderIndexes(headerRow);
            validateHeaders(headerIndexes);

            Map<String, U9SalesOrderHeadDTO> ordersByCustomerOrderNo = new LinkedHashMap<>();
            Map<String, Integer> orderSequenceByCustomerOrderNo = new LinkedHashMap<>();
            int sourceRowCount = 0;
            int lineCount = 0;

            for (int rowIndex = 1; rowIndex < rows.size(); rowIndex++) {
                Map<Integer, String> row = rows.get(rowIndex);
                if (isBlankRow(row)) {
                    continue;
                }
                sourceRowCount++;
                int excelRowNumber = rowIndex + 1;
                String orderBy = requiredText(row, headerIndexes, "客户编码", excelRowNumber);
                String documentType = requiredText(row, headerIndexes, "单据类型编码", excelRowNumber);
                String customerOrderNo = requiredText(row, headerIndexes, "客户订单号", excelRowNumber);
                String businessDate = requiredText(row, headerIndexes, "业务日期", excelRowNumber);
                String itemCode = requiredText(row, headerIndexes, "U9料号", excelRowNumber);
                String customerItemNo = requiredText(row, headerIndexes, "客户料号", excelRowNumber);
                String customerItemName = requiredText(row, headerIndexes, "客户品名", excelRowNumber);
                BigDecimal qty = requiredDecimal(row, headerIndexes, "数量", excelRowNumber);
                String tu = requiredText(row, headerIndexes, "销售单位", excelRowNumber);
                String pu = requiredText(row, headerIndexes, "计价单位", excelRowNumber);
                BigDecimal unitPrice = requiredDecimal(row, headerIndexes, "单价", excelRowNumber);
                String requireDate = requiredText(row, headerIndexes, "交期", excelRowNumber);

                U9SalesOrderHeadDTO order = ordersByCustomerOrderNo.computeIfAbsent(customerOrderNo, key -> {
                    int orderSequence = orderSequenceByCustomerOrderNo.size() + 1;
                    orderSequenceByCustomerOrderNo.put(key, orderSequence);
                    U9SalesOrderHeadDTO newOrder = new U9SalesOrderHeadDTO();
                    newOrder.setOtherID(safeBatchNo(batchNo) + "-" + orderSequence);
                    newOrder.setCustomerOrderNo(customerOrderNo);
                    newOrder.setOrderBy(orderBy);
                    newOrder.setDocumentType(documentType);
                    newOrder.setBusinessDate(businessDate);
                    newOrder.setSeller(optionalText(row, headerIndexes, "业务员编码"));
                    newOrder.setSaleDepartment(optionalText(row, headerIndexes, "部门编码"));
                    newOrder.setTC(optionalText(row, headerIndexes, "交易币编码"));
                    newOrder.setAC(optionalText(row, headerIndexes, "核币编码"));
                    newOrder.setShipToSite(optionalText(row, headerIndexes, "收货位置编码"));
                    newOrder.setBillToSite(optionalText(row, headerIndexes, "立账位置编码"));
                    newOrder.setPayerCustomer(optionalText(row, headerIndexes, "付款客户编码"));
                    newOrder.setPayerToSite(optionalText(row, headerIndexes, "付款位置编码"));
                    newOrder.setMemo(optionalText(row, headerIndexes, "备注"));
                    return newOrder;
                });

                U9SalesOrderLineDTO line = new U9SalesOrderLineDTO();
                line.setDocLineNo(order.getSOLineDTOList().size() + 1);
                line.setCustomerItemNo(customerItemNo);
                line.setCustomerItemName(customerItemName);
                line.setItemCode(itemCode);
                line.setItemVersion(optionalText(row, headerIndexes, "料品版本"));
                line.setFreeType(-1);
                line.setFreeReason(-1);
                line.setProject(optionalText(row, headerIndexes, "项目编码"));
                line.setTask(optionalText(row, headerIndexes, "任务编码"));
                line.setOrderByQtyTU(qty);
                line.setOrderByQtyPU(qty);
                line.setTU(tu);
                line.setPU(pu);
                line.setTBUToPBURate(BigDecimal.ONE);
                line.setOrderPriceTC(unitPrice);
                line.setFinallyPriceTC(unitPrice);
                line.setTaxSchedule(optionalText(row, headerIndexes, "税组合编码"));
                line.setPriceSource(1);
                line.setShipTogetherFlag(-1);
                line.setShipToSite(optionalText(row, headerIndexes, "收货位置编码"));

                U9SalesOrderShipLineDTO shipLine = new U9SalesOrderShipLineDTO();
                shipLine.setDocSubLineNo(1);
                shipLine.setItemCode(itemCode);
                shipLine.setRequireDate(requireDate);
                shipLine.setShipPlanQtyTU(qty);
                shipLine.setShipPlanQtyPU(qty);
                shipLine.setTU(tu);
                shipLine.setPU(pu);
                shipLine.setTUBaseToPUBaseRate(BigDecimal.ONE);
                shipLine.setShipToSite(optionalText(row, headerIndexes, "收货位置编码"));
                shipLine.setWh(optionalText(row, headerIndexes, "存储地点编码"));
                shipLine.setMemo(optionalText(row, headerIndexes, "备注"));
                line.getSOShiplineDTOList().add(shipLine);
                order.getSOLineDTOList().add(line);
                lineCount++;
            }

            SalesOrderExcelParseResult result = new SalesOrderExcelParseResult();
            result.setSourceRowCount(sourceRowCount);
            result.setLineCount(lineCount);
            result.setOrders(new ArrayList<>(ordersByCustomerOrderNo.values()));
            result.setOrderCount(result.getOrders().size());
            return result;
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("销售订单导入表格解析失败：" + e.getMessage(), e);
        }
    }

    private Map<String, Integer> buildHeaderIndexes(Map<Integer, String> headerRow) {
        Map<String, Integer> indexes = new LinkedHashMap<>();
        for (Map.Entry<Integer, String> entry : headerRow.entrySet()) {
            String header = normalizeHeader(entry.getValue());
            if (!header.isEmpty()) {
                indexes.put(header, entry.getKey());
            }
        }
        return indexes;
    }

    private void validateHeaders(Map<String, Integer> headerIndexes) {
        List<String> missing = REQUIRED_HEADERS.stream()
                .filter(header -> !headerIndexes.containsKey(normalizeHeader(header)))
                .toList();
        if (!missing.isEmpty()) {
            throw new IllegalArgumentException("销售订单导入表格缺失表头：" + String.join("、", missing));
        }
    }

    private String requiredText(Map<Integer, String> row, Map<String, Integer> headerIndexes, String header, int excelRowNumber) {
        String value = optionalText(row, headerIndexes, header);
        if (value.isEmpty()) {
            throw new IllegalArgumentException("销售订单导入表格第" + excelRowNumber + "行缺少必填字段：" + header);
        }
        return value;
    }

    private BigDecimal requiredDecimal(Map<Integer, String> row, Map<String, Integer> headerIndexes, String header, int excelRowNumber) {
        String value = requiredText(row, headerIndexes, header, excelRowNumber);
        try {
            return new BigDecimal(value.replace(",", "")).stripTrailingZeros();
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("销售订单导入表格第" + excelRowNumber + "行字段不是有效数字：" + header);
        }
    }

    private String optionalText(Map<Integer, String> row, Map<String, Integer> headerIndexes, String header) {
        Integer column = headerIndexes.get(normalizeHeader(header));
        if (column == null) {
            return "";
        }
        return clean(row.get(column));
    }

    private boolean isBlankRow(Map<Integer, String> row) {
        if (row == null || row.isEmpty()) {
            return true;
        }
        return row.values().stream().allMatch(value -> clean(value).isEmpty());
    }

    private String normalizeHeader(String value) {
        return clean(value).replaceAll("\\s+", "");
    }

    private String clean(String value) {
        return value == null ? "" : value.replace('\u00A0', ' ').trim();
    }

    private String safeBatchNo(String batchNo) {
        return isBlank(batchNo) ? "SOI" + System.currentTimeMillis() : batchNo.trim();
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
