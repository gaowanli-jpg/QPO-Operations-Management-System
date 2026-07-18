package com.machining.quote.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.machining.quote.config.AuthContext;
import com.machining.quote.dto.PasswordChangeReq;
import com.machining.quote.dto.UserSaveReq;
import com.machining.quote.entity.ErpUser;
import com.machining.quote.entity.SystemRole;
import com.machining.quote.entity.SystemUser;
import com.machining.quote.mapper.ErpUserMapper;
import com.machining.quote.mapper.SystemRoleMapper;
import com.machining.quote.mapper.SystemUserMapper;
import com.machining.quote.mapper.SystemUserRoleMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
public class UserAdminService {
    private static final Set<String> LEGACY_ROLES = Set.of("ADMIN", "MARKET", "ENGINEER");
    private static final Set<String> PROTECTED_USERNAMES = Set.of("admin", "market", "engineer");

    @Autowired
    private ErpUserMapper userMapper;
    @Autowired
    private AuthService authService;
    @Autowired
    private AuditService auditService;
    @Autowired(required = false)
    private SystemUserMapper systemUserMapper;
    @Autowired(required = false)
    private SystemRoleMapper systemRoleMapper;
    @Autowired(required = false)
    private SystemUserRoleMapper systemUserRoleMapper;

    public List<Map<String, Object>> listUsers() {
        QueryWrapper<ErpUser> qw = new QueryWrapper<>();
        qw.orderByAsc("id");
        return userMapper.selectList(qw).stream().map(this::publicUser).toList();
    }

    @Transactional
    public Map<String, Object> createUser(UserSaveReq req) {
        String username = normalizeUsername(req.getUsername());
        if (username.isEmpty()) {
            throw new RuntimeException("用户名不能为空");
        }
        if (isBlank(req.getPassword())) {
            throw new RuntimeException("新增用户必须设置初始密码");
        }
        assertPasswordLength(req.getPassword());

        List<String> roleCodes = requestedRoleCodes(req, true);
        String legacyRole = resolveLegacyRole(req.getRole(), roleCodes);

        QueryWrapper<ErpUser> qw = new QueryWrapper<>();
        qw.eq("username", username);
        if (userMapper.selectCount(qw) > 0) {
            throw new RuntimeException("用户名已存在");
        }

        ErpUser user = new ErpUser();
        user.setUsername(username);
        user.setPasswordHash(authService.hashPassword(req.getPassword()));
        user.setDisplayName(isBlank(req.getDisplayName()) ? username : req.getDisplayName().trim());
        user.setRole(legacyRole);
        user.setEnabled(req.getEnabled() == null || req.getEnabled());
        user.setForcePasswordChange(true);
        user.setFailedLoginAttempts(0);
        userMapper.insert(user);

        SystemUser systemUser = syncSystemUser(user, req, true);
        replaceUserRoles(systemUser, roleCodes, true);

        Map<String, Object> after = publicUser(user, systemUser);
        after.put("roleCodes", roleCodes);
        after.put("roles", roleCodes);
        auditService.record("SYSTEM_USER_CREATE", "system_user", systemUser == null ? user.getId() : systemUser.getId(), null, after);
        return after;
    }

    @Transactional
    public Map<String, Object> updateUser(Integer id, UserSaveReq req) {
        ErpUser user = requireUserForUpdate(id);
        Map<String, Object> before = publicUser(user);

        List<String> roleCodes = requestedRoleCodes(req, false);
        if (!roleCodes.isEmpty() || !isBlank(req.getRole())) {
            user.setRole(resolveLegacyRole(req.getRole(), roleCodes));
        }
        if (!isBlank(req.getDisplayName())) {
            user.setDisplayName(req.getDisplayName().trim());
        }
        if (req.getEnabled() != null) {
            user.setEnabled(req.getEnabled());
        }
        if (!isBlank(req.getPassword())) {
            assertPasswordLength(req.getPassword());
            user.setPasswordHash(authService.hashPassword(req.getPassword()));
            user.setForcePasswordChange(true);
            user.setFailedLoginAttempts(0);
            user.setLockedUntil(null);
        }
        userMapper.updateById(user);

        SystemUser systemUser = syncSystemUser(user, req, false);
        if (!roleCodes.isEmpty()) {
            replaceUserRoles(systemUser, roleCodes, false);
        }

        Map<String, Object> after = publicUser(user, systemUser);
        if (!roleCodes.isEmpty()) {
            after.put("roleCodes", roleCodes);
            after.put("roles", roleCodes);
        }
        auditService.record("SYSTEM_USER_UPDATE", "system_user", systemUser == null ? id : systemUser.getId(), before, after);
        return after;
    }

    @Transactional
    public Map<String, Object> updateStatus(Integer id, Boolean enabled) {
        UserSaveReq req = new UserSaveReq();
        req.setEnabled(enabled);
        Map<String, Object> result = updateUser(id, req);
        auditService.record(Boolean.TRUE.equals(enabled) ? "SYSTEM_USER_ENABLE" : "SYSTEM_USER_DISABLE",
                "system_user", result.get("systemUserId"), null, result);
        return result;
    }

    @Transactional
    public Map<String, Object> resetPassword(Integer id, UserSaveReq req) {
        if (req == null || isBlank(req.getPassword())) {
            throw new RuntimeException("新密码不能为空");
        }
        UserSaveReq update = new UserSaveReq();
        update.setPassword(req.getPassword());
        return updateUser(id, update);
    }

    @Transactional
    public void changeMyPassword(PasswordChangeReq req) {
        AuthContext.SessionUser current = AuthContext.get();
        if (current == null) {
            throw new RuntimeException("请先登录");
        }
        ErpUser user = requireUserForUpdate(current.id());
        if (!authService.matchesPassword(req.getOldPassword(), user.getPasswordHash())) {
            throw new RuntimeException("原密码不正确");
        }
        assertPasswordLength(req.getNewPassword());
        if (!isBlank(req.getConfirmPassword()) && !req.getNewPassword().equals(req.getConfirmPassword())) {
            throw new RuntimeException("两次输入的新密码不一致");
        }
        user.setPasswordHash(authService.hashPassword(req.getNewPassword()));
        user.setForcePasswordChange(false);
        user.setFailedLoginAttempts(0);
        user.setLockedUntil(null);
        user.setPasswordChangedAt(LocalDateTime.now());
        userMapper.updateById(user);
        syncSystemUser(user, null, false);
        auditService.record("USER_CHANGE_PASSWORD", "erp_user", user.getId(), null, publicUser(user));
    }

    @Transactional
    public void deleteUser(Integer id) {
        ErpUser user = requireUserForUpdate(id);
        if (isProtectedUser(user)) {
            throw new RuntimeException("初始账号不可删除");
        }
        Map<String, Object> before = publicUser(user);
        SystemUser systemUser = findSystemUser(user.getId());
        if (systemUser != null) {
            systemUser.setDeleted(true);
            systemUser.setEnabled(false);
            systemUser.setUpdatedAt(LocalDateTime.now());
            systemUserMapper.updateById(systemUser);
        }
        userMapper.deleteById(id);
        auditService.record("SYSTEM_USER_DELETE", "system_user", systemUser == null ? id : systemUser.getId(), before, null);
    }

    private ErpUser requireUserForUpdate(Integer id) {
        if (id == null) {
            throw new RuntimeException("用户 ID 不能为空");
        }
        ErpUser user = userMapper.selectByIdForUpdate(id);
        if (user == null) {
            throw new RuntimeException("用户不存在");
        }
        return user;
    }

    private SystemUser syncSystemUser(ErpUser user, UserSaveReq req, boolean creating) {
        if (systemUserMapper == null || user == null) {
            return null;
        }
        SystemUser systemUser = findSystemUser(user.getId());
        boolean insert = systemUser == null;
        if (insert) {
            systemUser = new SystemUser();
            systemUser.setCreatedAt(LocalDateTime.now());
        }
        systemUser.setLegacyUserId(user.getId());
        systemUser.setUsername(user.getUsername());
        systemUser.setPasswordHash(user.getPasswordHash());
        systemUser.setDisplayName(user.getDisplayName());
        systemUser.setLegacyRole(user.getRole());
        systemUser.setEnabled(user.getEnabled());
        systemUser.setForcePasswordChange(user.getForcePasswordChange());
        systemUser.setFailedLoginAttempts(user.getFailedLoginAttempts());
        systemUser.setLockedUntil(user.getLockedUntil());
        systemUser.setLastLoginAt(user.getLastLoginAt());
        systemUser.setPasswordChangedAt(user.getPasswordChangedAt());
        systemUser.setDeleted(false);
        systemUser.setUpdatedAt(LocalDateTime.now());
        if (req != null) {
            if (!isBlank(req.getRealName())) systemUser.setRealName(req.getRealName().trim());
            if (!isBlank(req.getPhone())) systemUser.setPhone(req.getPhone().trim());
            if (!isBlank(req.getEmail())) systemUser.setEmail(req.getEmail().trim());
            if (req.getDeptId() != null) systemUser.setDeptId(req.getDeptId());
            if (req.getPostId() != null) systemUser.setPostId(req.getPostId());
        }
        if (insert) {
            systemUserMapper.insert(systemUser);
        } else {
            systemUserMapper.updateById(systemUser);
        }
        return systemUser;
    }

    private void replaceUserRoles(SystemUser systemUser, List<String> roleCodes, boolean creating) {
        if (systemUser == null || systemUserRoleMapper == null || systemRoleMapper == null) {
            return;
        }
        List<String> nextCodes = normalizeRoleCodes(roleCodes);
        if (nextCodes.isEmpty()) {
            throw new RuntimeException("至少选择一个角色");
        }
        List<SystemRole> roles = systemRoleMapper.selectByRoleCodes(nextCodes);
        if (roles == null || roles.size() != nextCodes.size()) {
            throw new RuntimeException("角色不存在或已删除");
        }
        List<String> beforeCodes = creating ? List.of() : safeList(systemUserRoleMapper.selectRoleCodesByUserId(systemUser.getId()));
        systemUserRoleMapper.deleteByUserId(systemUser.getId());
        for (SystemRole role : roles) {
            systemUserRoleMapper.insertIgnore(systemUser.getId(), role.getId());
        }
        Map<String, Object> before = new LinkedHashMap<>();
        before.put("roleCodes", beforeCodes);
        before.put("roleCount", beforeCodes.size());
        Map<String, Object> after = new LinkedHashMap<>();
        after.put("roleCodes", nextCodes);
        after.put("roleCount", nextCodes.size());
        auditService.record("SYSTEM_USER_ASSIGN_ROLE", "system_user", systemUser.getId(), before, after);
    }

    private Map<String, Object> publicUser(ErpUser user) {
        return publicUser(user, findSystemUser(user.getId()));
    }

    private Map<String, Object> publicUser(ErpUser user, SystemUser systemUser) {
        List<String> roleCodes = systemUser == null
                ? List.of(mapLegacyToRoleCode(user.getRole()))
                : safeList(systemUserRoleMapper == null ? null : systemUserRoleMapper.selectRoleCodesByUserId(systemUser.getId()));
        List<String> roleNames = systemUser == null
                ? roleCodes
                : safeList(systemUserRoleMapper == null ? null : systemUserRoleMapper.selectRoleNamesByUserId(systemUser.getId()));
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", user.getId());
        row.put("systemUserId", systemUser == null ? null : systemUser.getId());
        row.put("username", user.getUsername());
        row.put("displayName", user.getDisplayName());
        row.put("realName", systemUser == null ? null : systemUser.getRealName());
        row.put("phone", systemUser == null ? null : systemUser.getPhone());
        row.put("email", systemUser == null ? null : systemUser.getEmail());
        row.put("deptId", systemUser == null ? null : systemUser.getDeptId());
        row.put("postId", systemUser == null ? null : systemUser.getPostId());
        row.put("role", user.getRole());
        row.put("roleCodes", roleCodes);
        row.put("roles", roleCodes);
        row.put("roleNames", roleNames);
        row.put("enabled", user.getEnabled());
        row.put("forcePasswordChange", user.getForcePasswordChange());
        row.put("failedLoginAttempts", user.getFailedLoginAttempts());
        row.put("lockedUntil", user.getLockedUntil());
        row.put("lastLoginAt", user.getLastLoginAt());
        row.put("passwordChangedAt", user.getPasswordChangedAt());
        row.put("createdAt", user.getCreatedAt());
        return row;
    }

    private SystemUser findSystemUser(Integer legacyUserId) {
        if (systemUserMapper == null || legacyUserId == null) {
            return null;
        }
        return systemUserMapper.selectByLegacyUserId(legacyUserId);
    }

    private List<String> requestedRoleCodes(UserSaveReq req, boolean creating) {
        List<String> codes = req == null ? null : req.getRoleCodes();
        if (codes != null) {
            return normalizeRoleCodes(codes);
        }
        String role = req == null ? null : req.getRole();
        if (!isBlank(role)) {
            return List.of(mapLegacyToRoleCode(role));
        }
        return creating ? List.of("MARKET_USER") : List.of();
    }

    private List<String> normalizeRoleCodes(List<String> roleCodes) {
        LinkedHashSet<String> result = new LinkedHashSet<>();
        for (String code : roleCodes == null ? List.<String>of() : roleCodes) {
            String normalized = code == null ? "" : code.trim().toUpperCase(Locale.ROOT);
            if (!normalized.isEmpty()) {
                result.add(normalized);
            }
        }
        return new ArrayList<>(result);
    }

    private String resolveLegacyRole(String requestedRole, List<String> roleCodes) {
        if (!isBlank(requestedRole)) {
            String safeRole = requestedRole.trim().toUpperCase(Locale.ROOT);
            assertLegacyRole(safeRole);
            return safeRole;
        }
        List<String> codes = normalizeRoleCodes(roleCodes);
        if (codes.contains("SUPER_ADMIN")) return "ADMIN";
        if (codes.stream().anyMatch(code -> code.startsWith("MARKET"))) return "MARKET";
        if (codes.stream().anyMatch(code -> code.startsWith("ENGINEER"))) return "ENGINEER";
        if (codes.contains("AUDITOR")) return "ADMIN";
        return "MARKET";
    }

    private String mapLegacyToRoleCode(String role) {
        String safeRole = role == null ? "" : role.trim().toUpperCase(Locale.ROOT);
        return switch (safeRole) {
            case "ADMIN" -> "SUPER_ADMIN";
            case "MARKET" -> "MARKET_USER";
            case "ENGINEER" -> "ENGINEER_USER";
            default -> "READONLY";
        };
    }

    private boolean isProtectedUser(ErpUser user) {
        return user != null && PROTECTED_USERNAMES.contains(normalizeUsername(user.getUsername()).toLowerCase(Locale.ROOT));
    }

    private void assertLegacyRole(String role) {
        if (!LEGACY_ROLES.contains(role)) {
            throw new RuntimeException("角色不正确");
        }
    }

    private void assertPasswordLength(String password) {
        if (isBlank(password) || password.length() < 8) {
            throw new RuntimeException("密码至少 8 位");
        }
    }

    private <T> List<T> safeList(List<T> values) {
        return values == null ? List.of() : values;
    }

    private String normalizeUsername(String username) {
        return username == null ? "" : username.trim();
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
