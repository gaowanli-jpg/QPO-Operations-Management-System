package com.machining.quote.controller;

import com.machining.quote.config.RequireRole;
import com.machining.quote.entity.SystemTask;
import com.machining.quote.service.SystemTaskService;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

@RestController
@RequestMapping("/api/tasks")
public class SystemTaskController {
    @Autowired
    private SystemTaskService systemTaskService;

    @PostMapping("/engineer/refresh-quotes/{rfqId}")
    @RequireRole({"ENGINEER", "ADMIN"})
    public Map<String, Object> refreshQuotesAsync(@PathVariable Integer rfqId) {
        SystemTask task = systemTaskService.submitRefreshQuotesTask(rfqId);
        return Map.of(
                "success", true,
                "message", "一键刷新核价任务已提交",
                "task", task
        );
    }

    @PostMapping("/market/export-quote/{rfqId}")
    @RequireRole({"MARKET", "ENGINEER", "ADMIN"})
    public Map<String, Object> exportQuoteAsync(
            @PathVariable Integer rfqId,
            @RequestParam(value = "exportType", defaultValue = "DETAIL") String exportType) {
        SystemTask task = systemTaskService.submitQuoteExportTask(rfqId, exportType);
        return Map.of(
                "success", true,
                "message", "报价单导出任务已提交",
                "task", task
        );
    }

    @GetMapping("/{taskId}")
    @RequireRole({"MARKET", "ENGINEER", "ADMIN"})
    public SystemTask getTask(@PathVariable Long taskId) {
        return systemTaskService.getTask(taskId);
    }

    @GetMapping("/{taskId}/download")
    @RequireRole({"MARKET", "ENGINEER", "ADMIN"})
    public void downloadTaskFile(@PathVariable Long taskId, HttpServletResponse response) throws IOException {
        Path file = systemTaskService.getTaskFile(taskId);
        response.setContentType(MediaType.APPLICATION_OCTET_STREAM_VALUE);
        response.setCharacterEncoding("utf-8");
        String encodedName = URLEncoder.encode(file.getFileName().toString(), StandardCharsets.UTF_8).replaceAll("\\+", "%20");
        response.setHeader("Content-Disposition", "attachment; filename*=UTF-8''" + encodedName);
        Files.copy(file, response.getOutputStream());
    }

    @GetMapping
    @RequireRole({"MARKET", "ENGINEER", "ADMIN"})
    public List<SystemTask> recentTasks(
            @RequestParam(value = "taskType", required = false) String taskType,
            @RequestParam(value = "limit", required = false) Integer limit) {
        return systemTaskService.recentTasks(taskType, limit);
    }
}
