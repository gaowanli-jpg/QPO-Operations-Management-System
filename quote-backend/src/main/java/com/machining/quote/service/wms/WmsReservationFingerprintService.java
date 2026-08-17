package com.machining.quote.service.wms;

import com.machining.quote.dto.wms.WmsSaleLadingReserveReq;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.List;

@Service
public class WmsReservationFingerprintService {
    public String fingerprint(String saleNo, List<WmsSaleLadingReserveReq.Item> items) {
        StringBuilder canonical = new StringBuilder();
        canonical.append(normalize(saleNo)).append('\n');
        if (items != null) {
            items.stream()
                    .sorted(Comparator.comparing(item -> normalize(item.getProductCode())))
                    .forEach(item -> canonical
                            .append(normalize(item.getProductCode())).append('|')
                            .append(quantity(item.getReserveCount())).append('|')
                            .append(normalize(item.getRemark())).append('|')
                            .append(normalize(item.getCustomerProjectNo())).append('|')
                            .append(normalize(item.getCustomerOrderNo())).append('\n'));
        }
        return sha256(canonical.toString());
    }

    private String quantity(BigDecimal value) {
        return value == null ? "" : value.stripTrailingZeros().toPlainString();
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim();
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(bytes.length * 2);
            for (byte b : bytes) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }
}
