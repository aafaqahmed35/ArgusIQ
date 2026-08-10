package com.argusiq.tracing.interceptor;

import com.argusiq.tracing.service.TraceService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.servlet.HandlerInterceptor;

import java.time.LocalDateTime;
import java.util.List;

@Component
public class TraceInterceptor implements HandlerInterceptor {

    private static final Logger logger = LoggerFactory.getLogger(TraceInterceptor.class);

    private static final String START_TIME_ATTRIBUTE = "requestStartTime";

    private static final List<String> IGNORED_PATH_PATTERNS = List.of(
            "/api/v1/traces",
            "/api/v1/traces/**",
            "/api/v1/metrics",
            "/api/v1/metrics/**",
            "/api/v1/search",
            "/api/v1/search/**",
            "/api/v1/services",
            "/api/v1/services/**",
            "/api/v1/alerts",
            "/api/v1/alerts/**",
            "/v1/traces",
            "/v1/traces/**",
            "/api/v1/health",
            "/api/v1/health/**",
            "/ws",
            "/ws/**",
            "/actuator",
            "/actuator/**",
            "/error",
            "/error/**",
            "/favicon.ico",
            "/",
            "/index.html",
            "/assets/**",
            "/static/**",
            "/css/**",
            "/js/**",
            "/images/**",
            "/webjars/**"
    );

    private final TraceService traceService;

    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    public TraceInterceptor(TraceService traceService) {
        this.traceService = traceService;
    }

    @Override
    public boolean preHandle(
            HttpServletRequest request,
            HttpServletResponse response,
            Object handler
    ) {

        if (shouldIgnore(request)) {
            return true;
        }

        request.setAttribute(
                START_TIME_ATTRIBUTE,
                System.currentTimeMillis()
        );

        return true;
    }

    @Override
    public void afterCompletion(
            HttpServletRequest request,
            HttpServletResponse response,
            Object handler,
            Exception exception
    ) {

        if (shouldIgnore(request)) {
            return;
        }

        Long startTime = (Long) request.getAttribute(
                START_TIME_ATTRIBUTE
        );

        if (startTime == null) {
            return;
        }

        long executionTime =
                System.currentTimeMillis() - startTime;

        LocalDateTime timestamp = LocalDateTime.now();

        traceService.saveHttpRequestTrace(
                request.getMethod(),
                request.getRequestURI(),
                executionTime,
                timestamp,
                response.getStatus()
        );

        logger.info("""
                [TRACE]
                {} {}
                Execution Time: {}ms
                Timestamp: {}
                """,
                request.getMethod(),
                request.getRequestURI(),
                executionTime,
                timestamp
        );
    }

    private boolean shouldIgnore(HttpServletRequest request) {

        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }

        String requestPath = getRequestPath(request);

        return IGNORED_PATH_PATTERNS
                .stream()
                .anyMatch(pattern ->
                        pathMatcher.match(pattern, requestPath)
                );
    }

    private String getRequestPath(HttpServletRequest request) {

        String requestUri = request.getRequestURI();

        String contextPath = request.getContextPath();

        if (
                contextPath != null
                        && !contextPath.isBlank()
                        && requestUri.startsWith(contextPath)
        ) {

            String pathWithoutContext =
                    requestUri.substring(contextPath.length());

            return pathWithoutContext.isBlank()
                    ? "/"
                    : pathWithoutContext;
        }

        return requestUri;
    }
}
