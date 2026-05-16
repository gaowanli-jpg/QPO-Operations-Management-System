package com.machining.quote.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.machining.quote.config.AuthContext;
import com.machining.quote.entity.AuditLog;
import com.machining.quote.mapper.AuditLogMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
public class AuditService {
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    private AuditLogMapper auditLogMapper;

    public void record(String action, String targetType, Object targetId, Object beforeData, Object afterData) {
        AuthContext.SessionUser user = AuthContext.get();
        AuditLog log = new AuditLog();
        log.setUsername(user == null ? "system" : user.username());
        log.setRole(user == null ? "SYSTEM" : user.role());
        log.setAction(action);
        log.setTargetType(targetType);
        log.setTargetId(targetId == null ? null : String.valueOf(targetId));
        log.setBeforeData(toText(beforeData));
        log.setAfterData(toText(afterData));
        log.setCreatedAt(LocalDateTime.now());
        auditLogMapper.insert(log);
    }

    private String toText(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            return String.valueOf(value);
        }
    }
}
