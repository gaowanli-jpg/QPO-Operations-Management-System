package com.machining.quote.service.u9;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.machining.quote.dto.SalesOrderU9PriceMode;
import com.machining.quote.dto.u9.U9DescFlexFieldDTO;
import com.machining.quote.dto.u9.U9SalesOrderHeadDTO;
import com.machining.quote.dto.u9.U9SalesOrderLineDTO;
import com.machining.quote.dto.u9.U9SalesOrderShipLineDTO;
import com.machining.quote.entity.SalesOrderHeader;
import com.machining.quote.entity.SalesOrderItem;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;

@Component
public class U9SalesOrderRequestAssembler {
    private final ObjectMapper fingerprintMapper = new ObjectMapper()
            .findAndRegisterModules()
            .setSerializationInclusion(JsonInclude.Include.NON_NULL)
            .configure(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY, true)
            .configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true)
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    public U9SalesOrderHeadDTO assemble(SalesOrderHeader header, List<SalesOrderItem> items) {
        return assemble(header, items, SalesOrderU9PriceMode.defaultMode(null));
    }

    public U9SalesOrderHeadDTO assemble(SalesOrderHeader header, List<SalesOrderItem> items, SalesOrderU9PriceMode priceMode) {
        priceMode = SalesOrderU9PriceMode.defaultMode(priceMode);
        U9SalesOrderHeadDTO head = new U9SalesOrderHeadDTO();
        head.setOtherID(trimToNull(header.getSalesOrderNo()));
        head.setDocNo(trimToNull(header.getDocumentNo()));
        head.setBusinessDate(header.getSalesOrderDate() == null ? null : header.getSalesOrderDate().toString());
        head.setOrderBy(trimToNull(header.getCustomerCode()));
        head.setSeller(trimToNull(header.getU9SellerCode()));
        head.setSaleDepartment(trimToNull(header.getU9SaleDepartmentCode()));
        head.setDocumentType(trimToNull(header.getDocumentTypeCode()));
        head.setIsPriceIncludeTax(priceMode.isPriceIncludeTax());

        boolean renumber = shouldRenumber(items);
        int sequence = 1;
        for (SalesOrderItem item : items == null ? List.<SalesOrderItem>of() : items) {
            head.getSOLineDTOList().add(toLine(header, item, renumber ? sequence : item.getLineNo(), sequence, priceMode));
            sequence++;
        }
        return head;
    }

    public String fingerprint(U9SalesOrderHeadDTO request) {
        try {
            String json = fingerprintMapper.writeValueAsString(request);
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(json.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder(hash.length * 2);
            for (byte value : hash) {
                result.append(String.format("%02x", value));
            }
            return result.toString();
        } catch (Exception error) {
            throw new IllegalStateException("U9 request fingerprint failed", error);
        }
    }

    private U9SalesOrderLineDTO toLine(
            SalesOrderHeader header,
            SalesOrderItem item,
            Integer docLineNo,
            int sequence,
            SalesOrderU9PriceMode priceMode) {
        BigDecimal qty = BigDecimal.valueOf(item.getQty());
        U9SalesOrderLineDTO line = new U9SalesOrderLineDTO();
        line.setDocLineNo(docLineNo == null || docLineNo <= 0 ? sequence : docLineNo);
        line.setItemCode(trimToNull(item.getMaterialNo()));
        line.setOrderByQtyTU(qty);
        line.setOrderByQtyPU(qty);
        line.setFinallyPriceTC(priceFor(item, priceMode));
        line.setFreeType(-1);
        line.setShipTogetherFlag(-1);
        line.setDescFlexField(descFlex(header, item, priceMode));
        line.getSOShiplineDTOList().add(toShipLine(item, qty));
        return line;
    }

    private U9SalesOrderShipLineDTO toShipLine(SalesOrderItem item, BigDecimal qty) {
        U9SalesOrderShipLineDTO shipLine = new U9SalesOrderShipLineDTO();
        shipLine.setDocSubLineNo(1);
        shipLine.setItemCode(trimToNull(item.getMaterialNo()));
        shipLine.setRequireDate(item.getCustomerDeliveryDate() == null ? null : item.getCustomerDeliveryDate().toString());
        shipLine.setDeliveryDate(item.getInternalDeliveryDate() == null ? null : item.getInternalDeliveryDate().toString());
        shipLine.setShipPlanQtyTU(qty);
        shipLine.setShipPlanQtyPU(qty);
        return shipLine;
    }

    private U9DescFlexFieldDTO descFlex(SalesOrderHeader header, SalesOrderItem item, SalesOrderU9PriceMode priceMode) {
        U9DescFlexFieldDTO desc = new U9DescFlexFieldDTO();
        desc.setPrivateDescSeg1(firstNonBlank(item.getCustomerProjectNo(), header.getCustomerProjectNo()));
        desc.setPrivateDescSeg2(firstNonBlank(item.getCustomerOrderNo(), header.getCustomerOrderNo()));
        desc.setPrivateDescSeg3(firstNonBlank(item.getInternalProjectNo(), header.getInternalProjectNo()));
        desc.setPrivateDescSeg4(item.getCustomerDeliveryDate() == null ? null : item.getCustomerDeliveryDate().toString());
        desc.setPrivateDescSeg5(item.getInternalDeliveryDate() == null ? null : item.getInternalDeliveryDate().toString());
        desc.setPrivateDescSeg9(formatDecimal(priceFor(item, priceMode)));
        desc.setPubDescSeg2(trimToNull(item.getRemark()));
        return desc;
    }

    private BigDecimal priceFor(SalesOrderItem item, SalesOrderU9PriceMode priceMode) {
        return SalesOrderU9PriceMode.defaultMode(priceMode).isPriceIncludeTax()
                ? item.getTaxIncludedUnitPrice()
                : item.getUntaxedUnitPrice();
    }

    private boolean shouldRenumber(List<SalesOrderItem> items) {
        int expectedLineNo = 1;
        for (SalesOrderItem item : items == null ? List.<SalesOrderItem>of() : items) {
            Integer lineNo = item.getLineNo();
            if (lineNo == null || lineNo != expectedLineNo) {
                return true;
            }
            expectedLineNo++;
        }
        return false;
    }

    private String formatDecimal(BigDecimal value) {
        return value == null ? null : value.stripTrailingZeros().toPlainString();
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            String trimmed = trimToNull(value);
            if (trimmed != null) {
                return trimmed;
            }
        }
        return null;
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
