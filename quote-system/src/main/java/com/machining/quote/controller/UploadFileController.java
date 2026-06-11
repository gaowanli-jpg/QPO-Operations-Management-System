package com.machining.quote.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.util.UriUtils;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

@Controller
public class UploadFileController {
    @Value("${erp.upload-dir}")
    private String uploadDir;

    @GetMapping("/uploads/**")
    public void getUploadFile(HttpServletRequest request, HttpServletResponse response) throws IOException {
        Path uploadRoot = Path.of(uploadDir).toAbsolutePath().normalize();
        Path file = resolveRequestedFile(request, uploadRoot);
        if (file == null || !Files.isRegularFile(file)) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }

        String contentType = Files.probeContentType(file);
        response.setContentType(contentType == null ? MediaType.APPLICATION_OCTET_STREAM_VALUE : contentType);
        response.setContentLengthLong(Files.size(file));
        response.setHeader(HttpHeaders.CACHE_CONTROL, "private, no-store");
        response.setHeader("X-Content-Type-Options", "nosniff");
        response.setHeader(HttpHeaders.CONTENT_DISPOSITION, inlineDisposition(file.getFileName().toString()));
        Files.copy(file, response.getOutputStream());
    }

    private Path resolveRequestedFile(HttpServletRequest request, Path uploadRoot) {
        String path = request.getRequestURI();
        String contextPath = request.getContextPath();
        if (contextPath != null && !contextPath.isBlank() && path.startsWith(contextPath)) {
            path = path.substring(contextPath.length());
        }
        if (!path.startsWith("/uploads/")) {
            return null;
        }

        String relative = UriUtils.decode(path.substring("/uploads/".length()), StandardCharsets.UTF_8);
        if (relative.isBlank()) {
            return null;
        }

        Path file = uploadRoot.resolve(relative).toAbsolutePath().normalize();
        return file.startsWith(uploadRoot) ? file : null;
    }

    private String inlineDisposition(String filename) {
        String encodedName = URLEncoder.encode(filename, StandardCharsets.UTF_8).replaceAll("\\+", "%20");
        return "inline; filename*=UTF-8''" + encodedName;
    }
}
