package com.machining.quote.service;

import com.machining.quote.entity.OrderRfq;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.file.Path;

@Service
public class OrderFilePathService {
    @Value("${erp.upload-dir}")
    private String uploadDir;

    public Path uploadRoot() {
        return Path.of(uploadDir).toAbsolutePath().normalize();
    }

    public Path orderDrawingDirectory(OrderRfq order) {
        if (order == null || isBlank(order.getCustomerName()) || isBlank(order.getProjectName())) {
            return uploadRoot().resolve("orders").resolve(String.valueOf(order == null ? "unknown" : order.getId())).normalize();
        }
        return uploadRoot()
                .resolve(safePathSegment(order.getCustomerName()))
                .resolve(safePathSegment(order.getProjectName()))
                .normalize();
    }

    public String drawingUrl(OrderRfq order, String filename) {
        String safeFilename = safePathSegment(filename);
        if (order == null || isBlank(order.getCustomerName()) || isBlank(order.getProjectName())) {
            return "/uploads/orders/" + (order == null ? "unknown" : order.getId()) + "/" + safeFilename;
        }
        return "/uploads/"
                + safePathSegment(order.getCustomerName())
                + "/"
                + safePathSegment(order.getProjectName())
                + "/"
                + safeFilename;
    }

    public String safePathSegment(String value) {
        if (value == null) {
            return "";
        }
        String cleaned = value.trim()
                .replaceAll("[\\\\/:*?\"<>|]", "_")
                .replace("..", "_")
                .replaceAll("\\s+", " ");
        while (cleaned.startsWith(".")) {
            cleaned = cleaned.substring(1);
        }
        while (cleaned.endsWith(".")) {
            cleaned = cleaned.substring(0, cleaned.length() - 1);
        }
        return cleaned.isBlank() ? "未命名" : cleaned;
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
