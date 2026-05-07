package com.example.hello.service;

import java.time.Duration;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.hello.dto.AppReviewStatsResponse;
import com.example.hello.entity.AppReview;
import com.example.hello.entity.User;
import com.example.hello.enums.UserRole;
import com.example.hello.repository.AppReviewRepository;
import com.example.hello.repository.UserRepository;

@Service
public class AppReviewService {

    private static final String STATS_CACHE_KEY = "reviews::stats";
    private static final Duration CACHE_TTL = Duration.ofMinutes(5);

    private final AppReviewRepository appReviewRepository;
    private final UserRepository userRepository;
    private final RedisCacheService redisCacheService;

    public AppReviewService(AppReviewRepository appReviewRepository,
                            UserRepository userRepository,
                            RedisCacheService redisCacheService) {
        this.appReviewRepository = appReviewRepository;
        this.userRepository = userRepository;
        this.redisCacheService = redisCacheService;
    }

    @Transactional
    public AppReview submitReview(@NonNull Long userId, @NonNull Integer rating, String comment) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("用户不存在"));

        if (rating < 1 || rating > 5) {
            throw new RuntimeException("评分必须在 1-5 之间");
        }

        AppReview review = new AppReview(user, rating, comment);
        AppReview saved = appReviewRepository.save(review);

        // 清除统计缓存
        redisCacheService.evict(STATS_CACHE_KEY);

        return saved;
    }

    public Page<AppReview> getReviews(Pageable pageable) {
        return appReviewRepository.findAllByOrderByCreatedAtDesc(pageable);
    }

    public AppReviewStatsResponse getStats() {
        // 尝试从缓存获取
        AppReviewStatsResponse cached = redisCacheService.get(STATS_CACHE_KEY, AppReviewStatsResponse.class);
        if (cached != null) {
            return cached;
        }

        // 从数据库计算
        Double average = appReviewRepository.findAverageRating();
        long total = appReviewRepository.countAll();

        AppReviewStatsResponse stats = new AppReviewStatsResponse(
                average != null ? Math.round(average * 10.0) / 10.0 : 0.0,
                total
        );

        // 写入缓存
        if (stats != null && CACHE_TTL != null) {
            redisCacheService.put(STATS_CACHE_KEY, stats, CACHE_TTL);
        }

        return stats;
    }

    public Page<AppReview> getMyReviews(Long userId, Pageable pageable) {
        return appReviewRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable);
    }

    @Transactional
    public void deleteReview(@NonNull Long reviewId, @NonNull UserRole operatorRole) {
        if (operatorRole != UserRole.ADMIN && operatorRole != UserRole.SUPER_ADMIN) {
            throw new RuntimeException("仅管理员可以删除评价");
        }

        AppReview review = appReviewRepository.findById(reviewId)
                .orElseThrow(() -> new RuntimeException("评价不存在"));

        if (review != null) {
            appReviewRepository.delete(review);
        }

        // 清除统计缓存
        redisCacheService.evict(STATS_CACHE_KEY);
    }
}
