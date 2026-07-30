package com.machining.quote.service;

import com.machining.quote.dto.SalesOrderQpoImportResp;
import com.machining.quote.entity.CustomerMaster;
import com.machining.quote.mapper.CustomerMasterMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class SalesOrderQpoImportService {
    private static final Logger log = LoggerFactory.getLogger(SalesOrderQpoImportService.class);
    private static final String DEFAULT_DOCUMENT_TYPE_CODE = "SO01";
    private static final String INDEPENDENT_IMPORT_PENDING_MESSAGE =
            "销售订单导入已切换为独立主表，阶段2接入写入前不会写入 RFQ 主表";

    @Autowired
    private CustomerMasterMapper customerMapper;

    @Transactional
    public SalesOrderQpoImportResp importQpo(MultipartFile excelFile, Long customerId, String documentTypeCode) {
        validateFile(excelFile);
        CustomerMaster customer = requireCustomer(customerId);
        String effectiveDocumentType = effectiveDocumentType(documentTypeCode);
        log.warn("Sales order independent import writer pending customerId={} customerCode={} documentTypeCode={} sourceFileName={}",
                customer.getId(), customer.getCustomerCode(), effectiveDocumentType, safeFileName(excelFile.getOriginalFilename()));
        throw new IllegalStateException(INDEPENDENT_IMPORT_PENDING_MESSAGE);
    }

    private CustomerMaster requireCustomer(Long customerId) {
        if (customerId == null) {
            throw new RuntimeException("请选择客户");
        }
        CustomerMaster customer = customerMapper.selectById(customerId);
        if (customer == null || Boolean.FALSE.equals(customer.getEnabled())) {
            throw new RuntimeException("客户不存在或已禁用");
        }
        if (isBlank(customer.getCustomerCode()) || isBlank(customer.getCustomerName())) {
            throw new RuntimeException("客户资料缺少客户编码或客户名称");
        }
        return customer;
    }

    private void validateFile(MultipartFile excelFile) {
        if (excelFile == null || excelFile.isEmpty()) {
            throw new IllegalArgumentException("请上传销售订单 Excel 文件");
        }
    }

    private String effectiveDocumentType(String documentTypeCode) {
        return isBlank(documentTypeCode) ? DEFAULT_DOCUMENT_TYPE_CODE : documentTypeCode.trim();
    }

    private String safeFileName(String fileName) {
        if (isBlank(fileName)) {
            return "";
        }
        return fileName.replace('\\', '_').replace('/', '_').trim();
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
