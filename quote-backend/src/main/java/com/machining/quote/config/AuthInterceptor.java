package com.machining.quote.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.machining.quote.dto.PermissionInfoDTO;
import com.machining.quote.service.AuthService;
import com.machining.quote.service.PermissionService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Map;

@Component
public class AuthInterceptor implements HandlerInterceptor {
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${erp.auth.enabled:true}")
    private boolean authEnabled;

    @Value("${erp.aps.source-token:}")
    private String apsSourceToken;

    @Autowired
    private AuthService authService;

    @Autowired(required = false)
    private PermissionService permissionService;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        AuthContext.clear();
        if ("OPTIONS".equalsIgnoreCase(request.getMethod()) || !authEnabled || isPublicPath(request.getRequestURI())) {
            return true;
        }
        if (isApsSourcePath(request.getRequestURI()) && hasValidApsSourceToken(request)) {
            return true;
        }

        String token = resolveToken(request);
        AuthContext.SessionUser user = authService.findByToken(token);
        if (user == null) {
            writeError(response, HttpServletResponse.SC_UNAUTHORIZED, "请先登录");
            return false;
        }
        AuthContext.set(user);

        RequirePermission requirePermission = resolveRequirePermission(handler);
        if (requirePermission != null && !hasPermission(user, requirePermission.value())) {
            AuthContext.clear();
            writeError(response, HttpServletResponse.SC_FORBIDDEN, "当前账号没有该操作权限");
            return false;
        }
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        AuthContext.clear();
    }

    private boolean isPublicPath(String uri) {
        return uri.startsWith("/api/auth/login")
                || uri.startsWith("/api/health")
                || uri.startsWith("/api/integrations/mes/material-callback")
                || uri.startsWith("/api/integrations/sales-orders")
                || uri.equals("/error");
    }

    private boolean isApsSourcePath(String uri) {
        return uri.startsWith("/api/aps/source/");
    }

    private boolean hasValidApsSourceToken(HttpServletRequest request) {
        String expected = apsSourceToken == null ? "" : apsSourceToken.trim();
        if (expected.isEmpty()) {
            return false;
        }
        String actual = resolveToken(request);
        if (actual == null || actual.trim().isEmpty()) {
            actual = request.getHeader("X-APS-Auth-Token");
        }
        if (actual == null) {
            return false;
        }
        byte[] expectedBytes = expected.getBytes(StandardCharsets.UTF_8);
        byte[] actualBytes = actual.trim().getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(expectedBytes, actualBytes);
    }

    private String resolveToken(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            return header.substring("Bearer ".length());
        }
        return request.getHeader("X-Auth-Token");
    }

    private RequirePermission resolveRequirePermission(Object handler) {
        if (!(handler instanceof HandlerMethod handlerMethod)) {
            return null;
        }
        RequirePermission methodPermission = handlerMethod.getMethodAnnotation(RequirePermission.class);
        if (methodPermission != null) {
            return methodPermission;
        }
        return handlerMethod.getBeanType().getAnnotation(RequirePermission.class);
    }
    private boolean hasPermission(AuthContext.SessionUser user, String[] permissions) {
        PermissionInfoDTO info = permissionService == null ? null : permissionService.getPermissionInfo(user.id(), user.role());
        if (info != null && info.isSuperAdmin()) {
            return true;
        }
        if (info == null || info.getPermissions() == null) {
            return false;
        }
        return Arrays.stream(permissions).anyMatch(info.getPermissions()::contains);
    }
    private void writeError(HttpServletResponse response, int status, String message) throws IOException {
        response.setStatus(status);
        response.setCharacterEncoding("UTF-8");
        response.setContentType("application/json;charset=UTF-8");
        objectMapper.writeValue(response.getWriter(), Map.of(
                "success", false,
                "message", message
        ));
    }
}
