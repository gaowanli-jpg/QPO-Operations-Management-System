package com.machining.quote.service.auth;

import com.machining.quote.config.AuthContext;
import com.machining.quote.dto.PermissionInfoDTO;
import com.machining.quote.dto.auth.DataCenterSsoExchangeReq;
import com.machining.quote.entity.AuthDataCenterSsoNonce;
import com.machining.quote.entity.ErpUser;
import com.machining.quote.mapper.AuthDataCenterSsoNonceMapper;
import com.machining.quote.mapper.ErpUserMapper;
import com.machining.quote.service.AuditService;
import com.machining.quote.service.AuthService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class DataCenterSsoAuthService {
    @Autowired
    private DataCenterSsoConfigResolver configResolver;
    @Autowired
    private DataCenterSsoSignatureService signatureService;
    @Autowired
    private AuthDataCenterSsoNonceMapper nonceMapper;
    @Autowired
    private ErpUserMapper userMapper;
    @Autowired
    private AuthService authService;
    @Autowired
    private AuditService auditService;

    @Transactional
    public Map<String, Object> exchange(DataCenterSsoExchangeReq req) {
        DataCenterSsoRuntimeConfig config = null;
        String nonceHash = null;
        try {
            config = configResolver.resolve();
            signatureService.validate(req, config, Instant.now());
            nonceHash = signatureService.nonceHash(req.getAppCode(), req.getNonce());
            reserveNonce(req, config, nonceHash);

            String username = requireText(req.getUsername(), "user username cannot be blank");
            ErpUser user = userMapper.selectByUsernameForUpdate(username);
            if (user == null) {
                throw new IllegalStateException("user not found");
            }
            if (Boolean.FALSE.equals(user.getEnabled())) {
                throw new IllegalStateException("user disabled");
            }

            AuthContext.SessionUser sessionUser = new AuthContext.SessionUser(
                    user.getId(),
                    user.getUsername(),
                    user.getDisplayName(),
                    user.getRole()
            );
            PermissionInfoDTO permissionInfo = authService.permissionInfo(sessionUser);
            if (!hasAnyPermission(permissionInfo)) {
                throw new IllegalStateException("user has no permission");
            }

            user.setFailedLoginAttempts(0);
            user.setLockedUntil(null);
            user.setLastLoginAt(LocalDateTime.now());
            userMapper.updateById(user);
            return authService.issueLoginToken(user, "AUTH_DATACENTER_SSO_SUCCESS", successSummary(req, config, user, nonceHash));
        } catch (RuntimeException e) {
            String reason = safeReason(e);
            recordFailure(req, config, nonceHash, reason);
            throw new RuntimeException("Data center SSO login failed: " + reason, e);
        }
    }

    private void reserveNonce(DataCenterSsoExchangeReq req, DataCenterSsoRuntimeConfig config, String nonceHash) {
        AuthDataCenterSsoNonce row = new AuthDataCenterSsoNonce();
        row.setNonce(req.getNonce());
        row.setNonceHash(nonceHash);
        row.setUsername(requireText(req.getUsername(), "user username cannot be blank"));
        row.setAppCode(requireText(req.getAppCode(), "appCode cannot be blank"));
        row.setRequestTimestamp(req.getTimestamp());
        row.setUsedAt(LocalDateTime.now());
        row.setExpiresAt(LocalDateTime.now().plusMinutes(Math.max(1, config.nonceTtlMinutes())));
        row.setCreatedAt(LocalDateTime.now());
        int inserted = nonceMapper.insertIgnore(row);
        if (inserted == 0) {
            throw new IllegalStateException("nonce already used");
        }
    }

    private boolean hasAnyPermission(PermissionInfoDTO info) {
        if (info == null) {
            return false;
        }
        if (info.isSuperAdmin()) {
            return true;
        }
        return (info.getPermissions() != null && !info.getPermissions().isEmpty())
                || (info.getMenus() != null && !info.getMenus().isEmpty());
    }

    private Map<String, Object> successSummary(
            DataCenterSsoExchangeReq req,
            DataCenterSsoRuntimeConfig config,
            ErpUser user,
            String nonceHash) {
        LinkedHashMap<String, Object> summary = baseSummary(req, config, nonceHash, "success");
        summary.put("userId", user.getId());
        summary.put("role", user.getRole());
        return summary;
    }

    private void recordFailure(DataCenterSsoExchangeReq req, DataCenterSsoRuntimeConfig config, String nonceHash, String reason) {
        if (auditService == null) {
            return;
        }
        auditService.record(
                "AUTH_DATACENTER_SSO_FAILED",
                "auth",
                null,
                null,
                baseSummary(req, config, nonceHash, reason),
                "AUTH",
                "FAILED",
                "AUTH_DATACENTER_SSO_FAILED " + defaultText(req == null ? null : req.getUsername(), "unknown"),
                reason
        );
    }

    private LinkedHashMap<String, Object> baseSummary(
            DataCenterSsoExchangeReq req,
            DataCenterSsoRuntimeConfig config,
            String nonceHash,
            String reason) {
        LinkedHashMap<String, Object> summary = new LinkedHashMap<>();
        summary.put("username", req == null ? null : trimToNull(req.getUsername()));
        summary.put("displayName", req == null ? null : trimToNull(req.getDisplayName()));
        summary.put("appCode", req == null ? null : trimToNull(req.getAppCode()));
        summary.put("source", config == null ? null : config.source());
        summary.put("reason", reason);
        summary.put("timestamp", req == null ? null : req.getTimestamp());
        summary.put("nonceHash", nonceHash);
        summary.put("redirect", req == null ? null : trimToNull(req.getRedirect()));
        return summary;
    }

    private String safeReason(RuntimeException e) {
        String message = e == null ? null : e.getMessage();
        if (message == null || message.isBlank()) {
            return "unknown";
        }
        return message.replaceAll("\\s+", " ").trim();
    }

    private String requireText(String value, String message) {
        String normalized = trimToNull(value);
        if (normalized == null) {
            throw new IllegalArgumentException(message);
        }
        return normalized;
    }

    private String defaultText(String value, String fallback) {
        String normalized = trimToNull(value);
        return normalized == null ? fallback : normalized;
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
