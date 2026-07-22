package com.machining.quote;

import com.machining.quote.config.RequestTraceContext;
import com.machining.quote.config.RequestTraceFilter;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.MDC;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(OutputCaptureExtension.class)
class RequestTraceFilterTests {

    @AfterEach
    void tearDown() {
        RequestTraceContext.clear();
        MDC.clear();
    }

    @Test
    void exposesRequestIdToTraceContextResponseHeaderLogMdcAndAccessLogDuringRequest(CapturedOutput output) throws Exception {
        RequestTraceFilter filter = new RequestTraceFilter();
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/health");
        MockHttpServletResponse response = new MockHttpServletResponse();
        request.addHeader("X-Request-Id", "req-log-123");
        request.addHeader("X-Forwarded-For", "10.0.0.7, 10.0.0.8");
        request.addHeader("User-Agent", "QPO-Test");

        FilterChain chain = (servletRequest, servletResponse) -> {
            RequestTraceContext.RequestTrace trace = RequestTraceContext.get();
            assertThat(trace).isNotNull();
            assertThat(trace.requestId()).isEqualTo("req-log-123");
            assertThat(trace.ipAddress()).isEqualTo("10.0.0.7");
            assertThat(MDC.get("requestId")).isEqualTo("req-log-123");
        };

        filter.doFilter(request, response, chain);

        assertThat(response.getHeader("X-Request-Id")).isEqualTo("req-log-123");
        assertThat(RequestTraceContext.get()).isNull();
        assertThat(MDC.get("requestId")).isNull();
        assertThat(output).contains("HTTP GET /api/health -> 200");
    }
}
