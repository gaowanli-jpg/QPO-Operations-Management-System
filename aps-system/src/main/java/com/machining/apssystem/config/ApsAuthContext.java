package com.machining.apssystem.config;

public final class ApsAuthContext {
    private static final ThreadLocal<SessionUser> CURRENT_USER = new ThreadLocal<>();

    private ApsAuthContext() {
    }

    public static void set(SessionUser user) {
        CURRENT_USER.set(user);
    }

    public static SessionUser get() {
        return CURRENT_USER.get();
    }

    public static void clear() {
        CURRENT_USER.remove();
    }

    public record SessionUser(Long id, String username, String displayName, String role) {
    }
}
