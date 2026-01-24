package com.example.hello.service;

import java.time.LocalDate;
import java.time.LocalDateTime;

import org.springframework.stereotype.Service;

import com.example.hello.dto.StatsResponse;
import com.example.hello.enums.AuditStatus;
import com.example.hello.repository.PatternPendingRepository;
import com.example.hello.repository.PatternRepository;

@Service
public class StatsService {
    private final PatternPendingRepository pendingRepository;
    private final PatternRepository patternRepository;

    public StatsService(PatternPendingRepository pendingRepository, PatternRepository patternRepository) {
        this.pendingRepository = pendingRepository;
        this.patternRepository = patternRepository;
    }

    public StatsResponse getStats() {
        // 今日0点到明日0点
        LocalDateTime todayStart = LocalDate.now().atStartOfDay();
        LocalDateTime todayEnd = todayStart.plusDays(1);

        long todaySubmitCount = pendingRepository.countByCreatedAtBetween(todayStart, todayEnd);
        long pendingCount = pendingRepository.countByStatus(AuditStatus.PENDING);
        long approvedCount = pendingRepository.countByStatus(AuditStatus.APPROVED);
        long totalCount = patternRepository.count();

        return new StatsResponse(todaySubmitCount, pendingCount, approvedCount, totalCount);
    }
}
