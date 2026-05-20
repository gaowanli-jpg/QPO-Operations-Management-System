package com.machining.quote.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.machining.quote.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;
import java.util.Arrays;
import java.util.Map;
import java.util.Set;

@Component
public class AuthInterceptor implements HandlerInterceptor {
    private final ObjectMapper objectMapper = new ObjectMapper();
    private static final Set<String> PASSWORD_CHANGE_ALLOWED_PATHS = Set.of(
            "/api/admin/users/me/password",
            "/api/auth/change-password",
            "/api/auth/logout",
            "/api/auth/me"
    );

    @Value("${erp.auth.enabled:true}")
    private boolean authEnabled;

    @Autowired
    private AuthService authService;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        AuthContext.clear();
        if ("OPTIONS".equalsIgnoreCase(request.getMethod()) || !authEnabled || isPublicPath(request.getRequestURI())) {
            return true;
        }

        String token = resolveToken(request);
        AuthContext.SessionUser user = authService.findByToken(token);
        if (user == null) {
            writeError(response, HttpServletResponse.SC_UNAUTHORIZED, "请先登录");
            return false;
        }
        AuthContext.set(user);

        if (authService.mustChangePassword(user.id()) && !isPasswordChangeAllowedPath(request.getRequestURI())) {
            writeError(response, 428, "请先修改初始密码");
            return false;
        }

        RequireRole requireRole = resolveRequireRole(handler);
        if (requireRole != null && !hasRole(user, requireRole.value())) {
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
                || uri.startsWith("/uploads/")
                || uri.equals("/error");
    }

    private boolean isPasswordChangeAllowedPath(String uri) {
        return PASSWORD_CHANGE_ALLOWED_PATHS.contains(uri);
    }

    private String resolveToken(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            return header.substring("Bearer ".length());
        }
        return request.getHeader("X-Auth-Token");
    }

    private RequireRole resolveRequireRole(Object handler) {
        if (!(handler instanceof HandlerMethod handlerMethod)) {
            return null;
        }
        RequireRole methodRole = handlerMethod.getMethodAnnotation(RequireRole.class);
        if (methodRole != null) {
            return methodRole;
        }
        return handlerMethod.getBeanType().getAnnotation(RequireRole.class);
    }

    private boolean hasRole(AuthContext.SessionUser user, String[] roles) {
        if ("ADMIN".equalsIgnoreCase(user.role())) {
            return true;
        }
        return Arrays.stream(roles).anyMatch(role -> role.equalsIgnoreCase(user.role()));
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
