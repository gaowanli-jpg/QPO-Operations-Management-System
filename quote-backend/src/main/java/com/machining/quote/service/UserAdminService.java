package com.machining.quote.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.machining.quote.config.AuthContext;
import com.machining.quote.dto.MyProfileUpdateReq;
import com.machining.quote.dto.PasswordChangeReq;
import com.machining.quote.dto.UserSaveReq;
import com.machining.quote.entity.ErpUser;
import com.machining.quote.entity.SystemDept;
import com.machining.quote.entity.SystemPost;
import com.machining.quote.entity.SystemRole;
import com.machining.quote.entity.SystemUser;
import com.machining.quote.mapper.ErpUserMapper;
import com.machining.quote.mapper.SystemDeptMapper;
import com.machining.quote.mapper.SystemPostMapper;
import com.machining.quote.mapper.SystemRoleMapper;
import com.machining.quote.mapper.SystemUserMapper;
import com.machining.quote.mapper.SystemUserRoleMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

@Service
public class UserAdminService {
    private static final Logger log = LoggerFactory.getLogger(UserAdminService.class);
    private static final Set<String> LEGACY_ROLES = Set.of("ADMIN", "MARKET", "ENGINEER");
    private static final Set<String> PROTECTED_USERNAMES = Set.of("admin", "market", "engineer");
    private static final Pattern PHONE_PATTERN = Pattern.compile("^[0-9+\\-\\s()]{6,30}$");
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");

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
    @Autowired(required = false)
    private SystemDeptMapper deptMapper;
    @Autowired(required = false)
    private SystemPostMapper postMapper;

    public List<Map<String, Object>> listUsers() {
        QueryWrapper<ErpUser> qw = new QueryWrapper<>();
        qw.orderByAsc("id");
        return userMapper.selectList(qw).stream().map(this::publicUser).toList();
    }

    public Map<String, Object> currentUserProfile(Integer legacyUserId) {
        if (legacyUserId == null) {
            throw new RuntimeException("User ID is required");
        }
        ErpUser user = userMapper.selectById(legacyUserId);
        if (user == null) {
            throw new RuntimeException("User does not exist");
        }
        return publicUser(user);
    }

    @Transactional
    public Map<String, Object> updateMyProfile(MyProfileUpdateReq req) {
        AuthContext.SessionUser current = AuthContext.get();
        if (current == null) {
            throw new RuntimeException("Please login first");
        }
        String phone = normalizeProfilePhone(req == null ? null : req.getPhone());
        String email = normalizeProfileEmail(req == null ? null : req.getEmail());

        ErpUser user = requireUserForUpdate(current.id());
        if (systemUserMapper == null) {
            throw new IllegalStateException("System user storage is unavailable");
        }
        SystemUser systemUser = findSystemUser(user.getId());
        if (systemUser == null) {
            systemUser = syncSystemUser(user, null, false);
        }

        Map<String, Object> before = contactSnapshot(systemUser);
        systemUser.setPhone(phone);
        systemUser.setEmail(email);
        systemUser.setUpdatedAt(LocalDateTime.now());
        systemUserMapper.updateById(systemUser);

        Map<String, Object> after = contactSnapshot(systemUser);
        Object targetId = systemUser.getId() == null ? user.getId() : systemUser.getId();
        if (auditService != null) {
            auditService.record("USER_PROFILE_UPDATE", "system_user", targetId, before, after);
        }
        log.info("User profile updated userId={} systemUserId={}", user.getId(), systemUser.getId());
        return publicUser(user, systemUser);
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
        validateOrganizationReferences(req, true);

        ErpUser user = new ErpUser();
        user.setUsername(username);
        user.setPasswordHash(authService.hashPassword(req.getPassword()));
        user.setDisplayName(isBlank(req.getDisplayName()) ? username : req.getDisplayName().trim());
        user.setRole(legacyRole);
        user.setEnabled(req.getEnabled() == null || req.getEnabled());
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
        if (req == null) {
            req = new UserSaveReq();
        }
        ErpUser user = requireUserForUpdate(id);
        Map<String, Object> before = publicUser(user);
        validateOrganizationReferences(req, false);

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
        recordOrganizationChangeIfNeeded(systemUser == null ? id : systemUser.getId(), before, after);
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
        systemUser.setFailedLoginAttempts(user.getFailedLoginAttempts());
        systemUser.setLockedUntil(user.getLockedUntil());
        systemUser.setLastLoginAt(user.getLastLoginAt());
        systemUser.setPasswordChangedAt(user.getPasswordChangedAt());
        systemUser.setDeleted(false);
        systemUser.setUpdatedAt(LocalDateTime.now());
        if (shouldApplyProfileFields(req, creating)) {
            systemUser.setRealName(trimToNull(req.getRealName()));
            systemUser.setPhone(trimToNull(req.getPhone()));
            systemUser.setEmail(trimToNull(req.getEmail()));
            systemUser.setDeptId(req.getDeptId());
            systemUser.setPostId(req.getPostId());
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
        SystemDept dept = systemUser == null ? null : findDept(systemUser.getDeptId());
        SystemPost post = systemUser == null ? null : findPost(systemUser.getPostId());
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", user.getId());
        row.put("systemUserId", systemUser == null ? null : systemUser.getId());
        row.put("username", user.getUsername());
        row.put("displayName", user.getDisplayName());
        row.put("realName", systemUser == null ? null : systemUser.getRealName());
        row.put("phone", systemUser == null ? null : systemUser.getPhone());
        row.put("email", systemUser == null ? null : systemUser.getEmail());
        row.put("deptId", systemUser == null ? null : systemUser.getDeptId());
        row.put("deptName", dept == null ? null : dept.getDeptName());
        row.put("postId", systemUser == null ? null : systemUser.getPostId());
        row.put("postName", post == null ? null : post.getPostName());
        row.put("role", user.getRole());
        row.put("roleCodes", roleCodes);
        row.put("roles", roleCodes);
        row.put("roleNames", roleNames);
        row.put("enabled", user.getEnabled());
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

    private void validateOrganizationReferences(UserSaveReq req, boolean creating) {
        if (!shouldApplyProfileFields(req, creating)) {
            return;
        }
        requireUsableDept(req.getDeptId());
        requireUsablePost(req.getPostId());
    }

    private boolean shouldApplyProfileFields(UserSaveReq req, boolean creating) {
        if (req == null) {
            return false;
        }
        return creating
                || req.getUsername() != null
                || req.getDisplayName() != null
                || req.getRealName() != null
                || req.getPhone() != null
                || req.getEmail() != null
                || req.getDeptId() != null
                || req.getPostId() != null;
    }

    private SystemDept requireUsableDept(Long deptId) {
        if (deptId == null) {
            return null;
        }
        SystemDept dept = findDept(deptId);
        if (dept == null || Boolean.TRUE.equals(dept.getDeleted()) || Boolean.FALSE.equals(dept.getEnabled())) {
            log.warn("User organization update rejected because department is unavailable deptId={}", deptId);
            throw new RuntimeException("部门不存在或已禁用");
        }
        return dept;
    }

    private SystemPost requireUsablePost(Long postId) {
        if (postId == null) {
            return null;
        }
        SystemPost post = findPost(postId);
        if (post == null || Boolean.TRUE.equals(post.getDeleted()) || Boolean.FALSE.equals(post.getEnabled())) {
            log.warn("User organization update rejected because post is unavailable postId={}", postId);
            throw new RuntimeException("岗位不存在或已禁用");
        }
        return post;
    }

    private SystemDept findDept(Long deptId) {
        if (deptMapper == null || deptId == null) {
            return null;
        }
        return deptMapper.selectById(deptId);
    }

    private SystemPost findPost(Long postId) {
        if (postMapper == null || postId == null) {
            return null;
        }
        return postMapper.selectById(postId);
    }

    private void recordOrganizationChangeIfNeeded(Object targetId, Map<String, Object> before, Map<String, Object> after) {
        Map<String, Object> beforeOrg = organizationSnapshot(before);
        Map<String, Object> afterOrg = organizationSnapshot(after);
        if (Objects.equals(beforeOrg, afterOrg)) {
            return;
        }
        auditService.record("SYSTEM_USER_ORG_UPDATE", "system_user", targetId, beforeOrg, afterOrg);
        log.info("User organization updated userId={} deptId={} postId={}",
                targetId, after.get("deptId"), after.get("postId"));
    }

    private Map<String, Object> organizationSnapshot(Map<String, Object> source) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("realName", source == null ? null : source.get("realName"));
        row.put("phone", source == null ? null : source.get("phone"));
        row.put("email", source == null ? null : source.get("email"));
        row.put("deptId", source == null ? null : source.get("deptId"));
        row.put("deptName", source == null ? null : source.get("deptName"));
        row.put("postId", source == null ? null : source.get("postId"));
        row.put("postName", source == null ? null : source.get("postName"));
        return row;
    }

    private Map<String, Object> contactSnapshot(SystemUser user) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("phone", user == null ? null : user.getPhone());
        row.put("email", user == null ? null : user.getEmail());
        return row;
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

    private String trimToNull(String value) {
        String trimmed = value == null ? "" : value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String normalizeProfilePhone(String value) {
        String phone = trimToNull(value);
        if (phone != null && !PHONE_PATTERN.matcher(phone).matches()) {
            log.warn("User profile update rejected userId={} reason=invalid_phone",
                    AuthContext.get() == null ? null : AuthContext.get().id());
            throw new IllegalArgumentException("Invalid phone number");
        }
        return phone;
    }

    private String normalizeProfileEmail(String value) {
        String email = trimToNull(value);
        if (email != null && (email.length() > 120 || !EMAIL_PATTERN.matcher(email).matches())) {
            log.warn("User profile update rejected userId={} reason=invalid_email",
                    AuthContext.get() == null ? null : AuthContext.get().id());
            throw new IllegalArgumentException("Invalid email address");
        }
        return email;
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
