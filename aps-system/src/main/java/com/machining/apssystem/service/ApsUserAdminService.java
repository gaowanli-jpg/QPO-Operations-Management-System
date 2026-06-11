package com.machining.apssystem.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.machining.apssystem.dto.ApsUserDTO;
import com.machining.apssystem.dto.ApsUserSaveReq;
import com.machining.apssystem.entity.ApsUser;
import com.machining.apssystem.mapper.ApsUserMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

@Service
public class ApsUserAdminService {
    private static final Set<String> ROLES = Set.of("ADMIN");
    private static final Set<String> PROTECTED_USERNAMES = Set.of("admin");

    @Autowired
    private ApsUserMapper userMapper;

    @Autowired
    private ApsPasswordService passwordService;

    public List<ApsUserDTO> listUsers() {
        LambdaQueryWrapper<ApsUser> wrapper = new LambdaQueryWrapper<>();
        wrapper.orderByAsc(ApsUser::getId);
        return userMapper.selectList(wrapper).stream()
                .map(ApsUserDTO::from)
                .toList();
    }

    @Transactional
    public ApsUserDTO createUser(ApsUserSaveReq req) {
        String username = requireText(req == null ? null : req.getUsername(), "username");
        String password = requireText(req == null ? null : req.getPassword(), "password");
        assertPasswordLength(password);
        String role = normalizeRole(req.getRole());

        LambdaQueryWrapper<ApsUser> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ApsUser::getUsername, username);
        if (userMapper.selectCount(wrapper) > 0) {
            throw new IllegalStateException("Username already exists.");
        }

        ApsUser user = new ApsUser();
        user.setUsername(username);
        user.setPasswordHash(passwordService.hashPassword(password));
        user.setDisplayName(defaultText(req.getDisplayName(), username));
        user.setRole(role);
        user.setEnabled(req.getEnabled() == null || req.getEnabled());
        user.setForcePasswordChange(true);
        user.setFailedLoginAttempts(0);
        userMapper.insert(user);
        return ApsUserDTO.from(userMapper.selectById(user.getId()));
    }

    @Transactional
    public ApsUserDTO updateUser(Long id, ApsUserSaveReq req) {
        ApsUser user = requireUserForUpdate(id);
        if (req == null) {
            return ApsUserDTO.from(user);
        }
        if (req.getDisplayName() != null) {
            user.setDisplayName(defaultText(req.getDisplayName(), user.getUsername()));
        }
        if (req.getRole() != null) {
            user.setRole(normalizeRole(req.getRole()));
        }
        if (req.getEnabled() != null) {
            user.setEnabled(req.getEnabled());
        }
        if (!text(req.getPassword()).isEmpty()) {
            assertPasswordLength(req.getPassword());
            user.setPasswordHash(passwordService.hashPassword(req.getPassword()));
            user.setForcePasswordChange(true);
            user.setFailedLoginAttempts(0);
            user.setLockedUntil(null);
            user.setPasswordChangedAt(null);
        }
        user.setUpdatedAt(LocalDateTime.now());
        userMapper.updateById(user);
        return ApsUserDTO.from(userMapper.selectById(id));
    }

    @Transactional
    public void deleteUser(Long id) {
        ApsUser user = requireUserForUpdate(id);
        if (isProtectedUser(user)) {
            throw new IllegalStateException("Initial admin account cannot be deleted.");
        }
        userMapper.deleteById(id);
    }

    private ApsUser requireUserForUpdate(Long id) {
        if (id == null || id <= 0) {
            throw new IllegalArgumentException("User id is required.");
        }
        ApsUser user = userMapper.selectByIdForUpdate(id);
        if (user == null) {
            throw new IllegalArgumentException("User not found.");
        }
        return user;
    }

    private boolean isProtectedUser(ApsUser user) {
        return user != null && PROTECTED_USERNAMES.contains(text(user.getUsername()).toLowerCase());
    }

    private String normalizeRole(String role) {
        String safeRole = defaultText(role, "ADMIN").toUpperCase();
        if (!ROLES.contains(safeRole)) {
            throw new IllegalArgumentException("Role must be ADMIN.");
        }
        return safeRole;
    }

    private void assertPasswordLength(String password) {
        if (password == null || password.length() < 8) {
            throw new IllegalArgumentException("Password must be at least 8 characters.");
        }
    }

    private String requireText(String value, String fieldName) {
        String text = text(value);
        if (text.isEmpty()) {
            throw new IllegalArgumentException(fieldName + " is required.");
        }
        return text;
    }

    private String defaultText(String value, String fallback) {
        String text = text(value);
        return text.isEmpty() ? fallback : text;
    }

    private String text(String value) {
        return value == null ? "" : value.trim();
    }
}
