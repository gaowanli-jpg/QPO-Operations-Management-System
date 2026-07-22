package com.machining.quote.config;

public final class RequestTraceContext {
    private static final ThreadLocal<RequestTrace> CURRENT_TRACE = new ThreadLocal<>();

    private RequestTraceContext() {
    }

    public static void set(RequestTrace trace) {
        CURRENT_TRACE.set(trace);
    }

    public static RequestTrace get() {
        return CURRENT_TRACE.get();
    }

    public static void clear() {
        CURRENT_TRACE.remove();
    }

    public record RequestTrace(String requestId, String ipAddress, String userAgent, String method, String path) {
    }
}
