package com.example.hello.service;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;

import com.example.hello.dto.AuthorStatsResponse;
import com.example.hello.dto.DailyTrendItem;
import com.example.hello.repository.ArticleRepository;
import com.example.hello.repository.PatternRepository;
import com.example.hello.repository.UserFollowRepository;

@Service
public class AuthorStatsService {
    private final PatternRepository patternRepository;
    private final ArticleRepository articleRepository;
    private final UserFollowRepository userFollowRepository;

    public AuthorStatsService(PatternRepository patternRepository,
                              ArticleRepository articleRepository,
                              UserFollowRepository userFollowRepository) {
        this.patternRepository = patternRepository;
        this.articleRepository = articleRepository;
        this.userFollowRepository = userFollowRepository;
    }

    public AuthorStatsResponse getStats(Long authorId, int days, Long requestUserId) {
        // 权限校验：仅本人可查看
        if (!authorId.equals(requestUserId)) {
            throw new RuntimeException("只能查看自己的统计数据");
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

        return response;
    }
}
