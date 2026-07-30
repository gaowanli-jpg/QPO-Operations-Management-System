package com.machining.quote.controller;

import com.machining.quote.service.BusinessFileService;
import com.machining.quote.service.BusinessFileService.FileReadResult;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.util.StreamUtils;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

@Controller
public class UploadFileController {
    private static final Logger log = LoggerFactory.getLogger(UploadFileController.class);

    private final BusinessFileService businessFileService;

    public UploadFileController(BusinessFileService businessFileService) {
        this.businessFileService = businessFileService;
    }

    @GetMapping("/uploads/**")
    public void getUploadFile(HttpServletRequest request, HttpServletResponse response) throws IOException {
        String publicPath = publicPath(request);
        if (!publicPath.startsWith("/uploads/")) {
            log.warn("Upload file access rejected path={} reason={}", request.getRequestURI(), "outside_upload_mapping");
        }
        Optional<FileReadResult> file = businessFileService.openByPublicPath(publicPath);
        if (file.isEmpty()) {
            log.warn("Upload file missing path={}", request.getRequestURI());
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }

        FileReadResult result = file.get();
        try {
            response.setContentType(result.contentType() == null ? MediaType.APPLICATION_OCTET_STREAM_VALUE : result.contentType());
            if (result.contentLength() >= 0) {
                response.setContentLengthLong(result.contentLength());
            }
            response.setHeader(HttpHeaders.CACHE_CONTROL, "private, no-store");
            response.setHeader("X-Content-Type-Options", "nosniff");
            response.setHeader(HttpHeaders.CONTENT_DISPOSITION, inlineDisposition(result.filename()));
            StreamUtils.copy(result.resource().getInputStream(), response.getOutputStream());
        } catch (IOException e) {
            log.error("Upload file read failed path={}", request.getRequestURI(), e);
            throw e;
        }
    }

    private String publicPath(HttpServletRequest request) {
        String path = request.getRequestURI();
        String contextPath = request.getContextPath();
        if (contextPath != null && !contextPath.isBlank() && path.startsWith(contextPath)) {
            path = path.substring(contextPath.length());
        }
        return path;
    }

    private String inlineDisposition(String filename) {
        String encodedName = URLEncoder.encode(filename, StandardCharsets.UTF_8).replaceAll("\\+", "%20");
        return "inline; filename*=UTF-8''" + encodedName;
    }
}
