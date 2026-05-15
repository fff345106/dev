package com.example.hello.service;

import java.time.Duration;
import java.util.ArrayList;

import org.springframework.stereotype.Service;

import com.example.hello.dto.AuthorStatsResponse;
import com.example.hello.repository.ArticleRepository;
import com.example.hello.repository.PatternRepository;
import com.example.hello.repository.UserFollowRepository;

@Service
public class AuthorStatsService {
    private static final Duration CACHE_TTL = Duration.ofMinutes(3);

    private final PatternRepository patternRepository;
    private final ArticleRepository articleRepository;
    private final UserFollowRepository userFollowRepository;
    private final RedisCacheService redisCacheService;

    public AuthorStatsService(PatternRepository patternRepository,
                              ArticleRepository articleRepository,
                              UserFollowRepository userFollowRepository,
                              RedisCacheService redisCacheService) {
        this.patternRepository = patternRepository;
        this.articleRepository = articleRepository;
        this.userFollowRepository = userFollowRepository;
        this.redisCacheService = redisCacheService;
    }

    public AuthorStatsResponse getStats(Long authorId, int days, Long requestUserId) {
        // 权限校验：仅本人可查看
        if (!authorId.equals(requestUserId)) {
            throw new RuntimeException("只能查看自己的统计数据");
        }

        // 缓存键
        String cacheKey = "author-stats:" + authorId + ":" + days;
        AuthorStatsResponse cached = redisCacheService.get(cacheKey, AuthorStatsResponse.class);
        if (cached != null) {
            return cached;
        }

        AuthorStatsResponse response = new AuthorStatsResponse();

        // 聚合纹样统计
        long patternViews = patternRepository.sumViewCountByAuthorId(authorId);
        long patternLikes = patternRepository.sumLikeCountByAuthorId(authorId);
        long patternDownloads = patternRepository.sumDownloadCountByAuthorId(authorId);
        long patternCount = patternRepository.countByAuthorId(authorId);

        // 聚合文章统计
        long articleViews = articleRepository.sumViewCountByAuthorId(authorId);
        long articleLikes = articleRepository.sumLikeCountByAuthorId(authorId);
        long articleCount = articleRepository.countByAuthorId(authorId);

        // 粉丝数
        long followerCount = userFollowRepository.countByFollowingId(authorId);

        response.setTotalViews(patternViews + articleViews);
        response.setTotalLikes(patternLikes + articleLikes);
        response.setTotalDownloads(patternDownloads);
        response.setTotalFollowers(followerCount);
        response.setTotalPatterns(patternCount);
        response.setTotalArticles(articleCount);

        // v1: dailyTrend 返回空列表，后续迭代需要 daily_stats 表
        response.setDailyTrend(new ArrayList<>());

        // 写入缓存
        redisCacheService.put(cacheKey, response, CACHE_TTL);

        return response;
    }
}
