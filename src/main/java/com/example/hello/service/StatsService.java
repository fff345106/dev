package com.example.hello.service;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;

import org.springframework.stereotype.Service;

import com.example.hello.dto.StatsResponse;
import com.example.hello.enums.AuditStatus;
import com.example.hello.repository.PatternPendingRepository;
import com.example.hello.repository.PatternRepository;

@Service
public class StatsService {
    private static final String CACHE_KEY = "stats::overview";
    private static final Duration CACHE_TTL = Duration.ofMinutes(2);

    private final PatternPendingRepository pendingRepository;
    private final PatternRepository patternRepository;
    private final RedisCacheService redisCacheService;

    public StatsService(PatternPendingRepository pendingRepository, PatternRepository patternRepository, RedisCacheService redisCacheService) {
        this.pendingRepository = pendingRepository;
        this.patternRepository = patternRepository;
        this.redisCacheService = redisCacheService;
    }

    public StatsResponse getStats() {
        StatsResponse cached = redisCacheService.get(CACHE_KEY, StatsResponse.class);
        if (cached != null) {
            return cached;
        }

        LocalDateTime todayStart = LocalDate.now().atStartOfDay();
        LocalDateTime todayEnd = todayStart.plusDays(1);

        long todaySubmitCount = pendingRepository.countByCreatedAtBetween(todayStart, todayEnd);
        long pendingCount = pendingRepository.countByStatus(AuditStatus.PENDING);
        long approvedCount = pendingRepository.countByStatus(AuditStatus.APPROVED);
        long totalCount = patternRepository.count();

        StatsResponse result = new StatsResponse(todaySubmitCount, pendingCount, approvedCount, totalCount);
        redisCacheService.put(CACHE_KEY, result, CACHE_TTL);
        return result;
    }
}
