package com.machining.apssystem.service;

import com.machining.apssystem.config.ApsAuthContext;
import com.machining.apssystem.entity.ApsUser;
import com.machining.apssystem.mapper.ApsUserMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Map;

@Service
public class ApsAuthService {
    private static final int MAX_FAILED_ATTEMPTS = 5;
    private static final int LOCK_MINUTES = 10;

    @Autowired
    private ApsUserMapper userMapper;

    @Autowired
    private ApsPasswordService passwordService;

    @Autowired
    private ApsJwtService jwtService;

    @Transactional
    public Map<String, Object> login(String username, String password) {
        String safeUsername = username == null ? "" : username.trim();
        if (safeUsername.isEmpty() || password == null || password.isEmpty()) {
            throw new IllegalArgumentException("请输入用户名和密码");
        }

        ApsUser user = userMapper.selectByUsernameForUpdate(safeUsername);
        if (user == null || Boolean.FALSE.equals(user.getEnabled())) {
            throw new IllegalArgumentException("用户名或密码错误");
        }
        if (isLocked(user)) {
            throw new IllegalStateException("登录失败次数过多，请稍后再试");
        }
        if (!passwordService.matchesPassword(password, user.getPasswordHash())) {
            recordFailedLogin(user);
            throw new IllegalArgumentException("用户名或密码错误");
        }

        if (!isBcryptHash(user.getPasswordHash())) {
            user.setPasswordHash(passwordService.hashPassword(password));
        }
        user.setFailedLoginAttempts(0);
        user.setLockedUntil(null);
        user.setLastLoginAt(LocalDateTime.now());
        userMapper.updateById(user);

        ApsAuthContext.SessionUser sessionUser = new ApsAuthContext.SessionUser(
                user.getId(),
                user.getUsername(),
                user.getDisplayName(),
                user.getRole()
        );
        String token = jwtService.createToken(sessionUser);
        return Map.of(
                "success", true,
                "message", "登录成功",
                "token", token,
                "user", Map.of(
                        "username", sessionUser.username(),
                        "displayName", sessionUser.displayName() == null ? sessionUser.username() : sessionUser.displayName(),
                        "role", sessionUser.role(),
                        "forcePasswordChange", Boolean.TRUE.equals(user.getForcePasswordChange())
                )
        );
    }

    public ApsAuthContext.SessionUser findByToken(String token) {
        return jwtService.parseToken(token);
    }

    public boolean mustChangePassword(Long userId) {
        if (userId == null) {
            return false;
        }
        ApsUser user = userMapper.selectById(userId);
        return user != null && Boolean.TRUE.equals(user.getForcePasswordChange());
    }

    public void logout(String token) {
        // JWT is stateless.
    }

    private void recordFailedLogin(ApsUser user) {
        int attempts = user.getFailedLoginAttempts() == null ? 0 : user.getFailedLoginAttempts();
        attempts += 1;
        user.setFailedLoginAttempts(attempts);
        if (attempts >= MAX_FAILED_ATTEMPTS) {
            user.setLockedUntil(LocalDateTime.now().plusMinutes(LOCK_MINUTES));
        }
        userMapper.updateById(user);
    }

    private boolean isLocked(ApsUser user) {
        LocalDateTime lockedUntil = user.getLockedUntil();
        if (lockedUntil == null) {
            return false;
        }
        if (lockedUntil.isAfter(LocalDateTime.now())) {
            return true;
        }
        user.setLockedUntil(null);
        user.setFailedLoginAttempts(0);
        userMapper.updateById(user);
        return false;
    }

    private boolean isBcryptHash(String passwordHash) {
        return passwordHash != null && passwordHash.startsWith("$2");
    }
}
