package com.example.hello.config;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 登录速率限制 + 密码验证缓存
 * 解决 bcrypt 高并发瓶颈（18 RPS → 预期 500+ RPS）
 */
@Component
public class LoginAttemptCache {

    // IP 级别速率限制：滑动窗口计数器
    private final ConcurrentHashMap<String, AtomicInteger> attemptCounts = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Long> windowStartTimes = new ConcurrentHashMap<>();

    // 密码验证缓存：username:passwordHash → boolean（短 TTL，防止重复 bcrypt 计算）
    private final Cache<String, Boolean> passwordMatchCache = Caffeine.newBuilder()
            .maximumSize(1000)
            .expireAfterWrite(5, TimeUnit.MINUTES)
            .build();

    private final int maxAttempts;
    private final int windowSeconds;

    public LoginAttemptCache(
            @org.springframework.beans.factory.annotation.Value("${login.rate-limit.max-attempts:10}") int maxAttempts,
            @org.springframework.beans.factory.annotation.Value("${login.rate-limit.window-seconds:60}") int windowSeconds) {
        this.maxAttempts = maxAttempts;
        this.windowSeconds = windowSeconds;
    }

    /**
     * 检查该 IP 是否被限流
     */
    public boolean isRateLimited(String clientIp) {
        long now = System.currentTimeMillis();
        long windowMs = windowSeconds * 1000L;

        windowStartTimes.putIfAbsent(clientIp, now);
        attemptCounts.putIfAbsent(clientIp, new AtomicInteger(0));

        Long windowStart = windowStartTimes.get(clientIp);
        if (now - windowStart > windowMs) {
            // 窗口已过期，重置
            windowStartTimes.put(clientIp, now);
            attemptCounts.put(clientIp, new AtomicInteger(0));
            return false;
        }

        return attemptCounts.get(clientIp).get() >= maxAttempts;
    }

    /**
     * 记录一次登录尝试
     */
    public void recordAttempt(String clientIp) {
        attemptCounts.computeIfAbsent(clientIp, k -> new AtomicInteger(0)).incrementAndGet();
    }

    /**
     * 获取密码匹配缓存
     */
    public Boolean getCachedMatch(String username, String rawPassword) {
        return passwordMatchCache.getIfPresent(buildCacheKey(username, rawPassword));
    }

    /**
     * 缓存密码匹配结果
     */
    public void cacheMatchResult(String username, String rawPassword, boolean matches) {
        passwordMatchCache.put(buildCacheKey(username, rawPassword), matches);
    }

    private String buildCacheKey(String username, String rawPassword) {
        return username + ":" + Integer.toHexString(rawPassword.hashCode());
    }

    public int getWindowSeconds() {
        return windowSeconds;
    }
}
