package com.argusiq.tracing.interceptor;

import com.argusiq.tracing.service.TraceService;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class TraceInterceptorTest {

    private final TraceService traceService = mock(TraceService.class);
    private final TraceInterceptor traceInterceptor = new TraceInterceptor(traceService);

    @Test
    void excludesTraceEndpointsFromTracing() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/traces");
        MockHttpServletResponse response = new MockHttpServletResponse();

        traceInterceptor.preHandle(request, response, new Object());
        traceInterceptor.afterCompletion(request, response, new Object(), null);

        verify(traceService, never()).saveHttpRequestTrace(any(), any(), any(), any(), any());
    }

    @Test
    void excludesTraceAnalyticsEndpointsFromTracing() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest(
                "GET",
                "/api/v1/traces/analytics/count"
        );
        MockHttpServletResponse response = new MockHttpServletResponse();

        traceInterceptor.preHandle(request, response, new Object());
        traceInterceptor.afterCompletion(request, response, new Object(), null);

        verify(traceService, never()).saveHttpRequestTrace(any(), any(), any(), any(), any());
    }

    @Test
    void excludesHealthEndpointFromTracing() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/health");
        MockHttpServletResponse response = new MockHttpServletResponse();

        traceInterceptor.preHandle(request, response, new Object());
        traceInterceptor.afterCompletion(request, response, new Object(), null);

        verify(traceService, never()).saveHttpRequestTrace(any(), any(), any(), any(), any());
    }

    @Test
    void excludesCorsPreflightRequestsFromTracing() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("OPTIONS", "/api/v1/orders");
        MockHttpServletResponse response = new MockHttpServletResponse();

        traceInterceptor.preHandle(request, response, new Object());
        traceInterceptor.afterCompletion(request, response, new Object(), null);

        verify(traceService, never()).saveHttpRequestTrace(any(), any(), any(), any(), any());
    }

    @Test
    void tracesApplicationEndpoints() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/orders");
        MockHttpServletResponse response = new MockHttpServletResponse();

        traceInterceptor.preHandle(request, response, new Object());
        traceInterceptor.afterCompletion(request, response, new Object(), null);

        verify(traceService).saveHttpRequestTrace(
                eq("GET"),
                eq("/api/v1/orders"),
                anyLong(),
                any(LocalDateTime.class),
                eq(200)
        );
    }
}
