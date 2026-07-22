package com.machining.quote.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.machining.quote.entity.SystemTask;
import com.machining.quote.mapper.SystemTaskMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class SystemTaskGuardService {
    private static final String REFRESH_ORDER_QUOTES = "REFRESH_ORDER_QUOTES";
    private static final List<String> ACTIVE_STATUSES = List.of("PENDING", "RUNNING");

    @Autowired
    private SystemTaskMapper taskMapper;

    public void assertNoActiveRefreshQuotesTask(Integer rfqId) {
        if (hasActiveRefreshQuotesTask(rfqId)) {
            throw new RuntimeException("一键刷新核价任务正在执行，请等待刷新核价任务完成后再继续操作");
        }
    }

    public boolean hasActiveRefreshQuotesTask(Integer rfqId) {
        if (rfqId == null) {
            return false;
        }
        QueryWrapper<SystemTask> qw = new QueryWrapper<>();
        qw.eq("task_type", REFRESH_ORDER_QUOTES)
                .in("status", ACTIVE_STATUSES)
                .orderByDesc("id");
        return taskMapper.selectList(qw).stream()
                .anyMatch(task -> requestMatchesRfq(task.getRequestJson(), rfqId));
    }

    private boolean requestMatchesRfq(Object requestJson, Integer rfqId) {
        if (requestJson instanceof Map<?, ?> request) {
            return valueMatchesRfq(request.get("rfqId"), rfqId);
        }
        return false;
    }

    private boolean valueMatchesRfq(Object value, Integer rfqId) {
        if (value instanceof Number number) {
            return number.intValue() == rfqId;
        }
        if (value instanceof String text) {
            try {
                return Integer.parseInt(text.trim()) == rfqId;
            } catch (NumberFormatException ignored) {
                return false;
            }
        }
        return false;
    }
}
