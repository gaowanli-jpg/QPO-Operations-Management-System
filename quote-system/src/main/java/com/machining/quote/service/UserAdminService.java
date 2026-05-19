package com.machining.quote.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.machining.quote.config.AuthContext;
import com.machining.quote.dto.PasswordChangeReq;
import com.machining.quote.dto.UserSaveReq;
import com.machining.quote.entity.ErpUser;
import com.machining.quote.mapper.ErpUserMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class UserAdminService {
    private static final Set<String> ROLES = Set.of("ADMIN", "MARKET", "ENGINEER", "WAREHOUSE");

    @Autowired
    private ErpUserMapper userMapper;
    @Autowired
    private AuthService authService;
    @Autowired
    private AuditService auditService;

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
        assertRole(req.getRole());

        QueryWrapper<ErpUser> qw = new QueryWrapper<>();
        qw.eq("username", username);
        if (userMapper.selectCount(qw) > 0) {
            throw new RuntimeException("用户名已存在");
        }

        ErpUser user = new ErpUser();
        user.setUsername(username);
        user.setPasswordHash(authService.hashPassword(req.getPassword()));
        user.setDisplayName(isBlank(req.getDisplayName()) ? username : req.getDisplayName().trim());
        user.setRole(req.getRole().trim().toUpperCase());
        user.setEnabled(req.getEnabled() == null || req.getEnabled());
        userMapper.insert(user);
        auditService.record("USER_CREATE", "erp_user", user.getId(), null, publicUser(user));
        return publicUser(user);
    }

    @Transactional
    public Map<String, Object> updateUser(Integer id, UserSaveReq req) {
        ErpUser user = requireUser(id);
        Map<String, Object> before = publicUser(user);
        if (!isBlank(req.getDisplayName())) {
            user.setDisplayName(req.getDisplayName().trim());
        }
        if (!isBlank(req.getRole())) {
            assertRole(req.getRole());
            user.setRole(req.getRole().trim().toUpperCase());
        }
        if (req.getEnabled() != null) {
            user.setEnabled(req.getEnabled());
        }
        if (!isBlank(req.getPassword())) {
            user.setPasswordHash(authService.hashPassword(req.getPassword()));
        }
        userMapper.updateById(user);
        Map<String, Object> after = publicUser(user);
        auditService.record("USER_UPDATE", "erp_user", id, before, after);
        return after;
    }

    @Transactional
    public void changeMyPassword(PasswordChangeReq req) {
        AuthContext.SessionUser current = AuthContext.get();
        if (current == null) {
            throw new RuntimeException("请先登录");
        }
        ErpUser user = requireUser(current.id());
        if (!authService.hashPassword(req.getOldPassword()).equalsIgnoreCase(user.getPasswordHash())) {
            throw new RuntimeException("原密码不正确");
        }
        if (isBlank(req.getNewPassword()) || req.getNewPassword().length() < 6) {
            throw new RuntimeException("新密码至少 6 位");
        }
        user.setPasswordHash(authService.hashPassword(req.getNewPassword()));
        userMapper.updateById(user);
        auditService.record("USER_CHANGE_PASSWORD", "erp_user", user.getId(), null, publicUser(user));
    }

    private ErpUser requireUser(Integer id) {
        if (id == null) {
            throw new RuntimeException("用户 ID 不能为空");
        }
        ErpUser user = userMapper.selectById(id);
        if (user == null) {
            throw new RuntimeException("用户不存在");
        }
        return user;
    }

    private Map<String, Object> publicUser(ErpUser user) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", user.getId());
        row.put("username", user.getUsername());
        row.put("displayName", user.getDisplayName());
        row.put("role", user.getRole());
        row.put("enabled", user.getEnabled());
        row.put("createdAt", user.getCreatedAt());
        return row;
    }

    private void assertRole(String role) {
        String safeRole = role == null ? "" : role.trim().toUpperCase();
        if (!ROLES.contains(safeRole)) {
            throw new RuntimeException("角色不正确");
        }
    }

    private String normalizeUsername(String username) {
        return username == null ? "" : username.trim();
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
