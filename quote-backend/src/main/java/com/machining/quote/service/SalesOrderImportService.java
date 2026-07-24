package com.machining.quote.service;

import com.machining.quote.dto.SalesOrderDryRunResp;
import com.machining.quote.dto.SalesOrderExcelParseResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class SalesOrderImportService {
    private final SalesOrderExcelParser excelParser;

    @Autowired
    public SalesOrderImportService(SalesOrderExcelParser excelParser) {
        this.excelParser = excelParser;
    }

    public SalesOrderDryRunResp dryRun(MultipartFile excelFile, String batchNo) {
        if (excelFile == null || excelFile.isEmpty()) {
            throw new IllegalArgumentException("请上传销售订单 Excel 文件");
        }
        try {
            String effectiveBatchNo = isBlank(batchNo) ? "SOI" + System.currentTimeMillis() : batchNo.trim();
            SalesOrderExcelParseResult parseResult = excelParser.parse(excelFile.getInputStream(), effectiveBatchNo);
            SalesOrderDryRunResp resp = new SalesOrderDryRunResp();
            resp.setSuccess(true);
            resp.setBatchNo(effectiveBatchNo);
            resp.setTotalOrderCount(parseResult.getOrderCount());
            resp.setTotalLineCount(parseResult.getLineCount());
            resp.setOrders(parseResult.getOrders());
            return resp;
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("销售订单导入校验失败：" + e.getMessage(), e);
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
