package com.example.hello.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 全局 API 限流过滤器
 * 基于滑动窗口的 IP 级别限流，保护后端服务不被压垮
 */
@Component
@Order(1)
public class RateLimitFilter extends OncePerRequestFilter {

    private final ConcurrentHashMap<String, WindowCounter> counters = new ConcurrentHashMap<>();

    private final int globalMaxRequests;
    private final int windowSeconds;

    public RateLimitFilter(
            @Value("${rate-limit.global.max-requests:200}") int globalMaxRequests,
            @Value("${rate-limit.global.window-seconds:10}") int windowSeconds) {
        this.globalMaxRequests = globalMaxRequests;
        this.windowSeconds = windowSeconds;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String clientIp = getClientIp(request);

        WindowCounter counter = counters.computeIfAbsent(clientIp, k -> new WindowCounter());
        if (!counter.tryAcquire()) {
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"error\":\"请求过于频繁，请稍后再试\",\"retryAfter\":" + windowSeconds + "}");
            return;
        }

        filterChain.doFilter(request, response);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        // 只限流 API 请求，放行健康检查和静态资源
        return path.startsWith("/actuator") || !path.startsWith("/api");
    }

    private String getClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip != null && !ip.isEmpty()) {
            return ip.split(",")[0].trim();
        }
        ip = request.getHeader("X-Real-IP");
        if (ip != null && !ip.isEmpty()) {
            return ip;
        }
        return request.getRemoteAddr();
    }

    private class WindowCounter {
        private volatile long windowStart = System.currentTimeMillis();
        private final AtomicInteger count = new AtomicInteger(0);
        private final long windowMs = windowSeconds * 1000L;

        boolean tryAcquire() {
            long now = System.currentTimeMillis();
            if (now - windowStart > windowMs) {
                synchronized (this) {
                    if (now - windowStart > windowMs) {
                        windowStart = now;
                        count.set(0);
                    }
                }
            }
            return count.incrementAndGet() <= globalMaxRequests;
        }
    }
}
