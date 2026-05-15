package com.example.hello.service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import com.example.hello.dto.PatternRankingResponse;
import com.example.hello.entity.Pattern;
import com.example.hello.repository.PatternRepository;
import com.example.hello.repository.UserRepository;

@Service
public class PatternRankingService {
    private final PatternRepository patternRepository;
    private final UserRepository userRepository;

    public PatternRankingService(PatternRepository patternRepository, UserRepository userRepository) {
        this.patternRepository = patternRepository;
        this.userRepository = userRepository;
    }

    public List<PatternRankingResponse> getRanking(String period, Integer limit, String category) {
        // 计算时间范围
        LocalDateTime since;
        if ("month".equalsIgnoreCase(period)) {
            since = LocalDateTime.now().minusDays(30);
        } else {
            since = LocalDateTime.now().minusDays(7);
        }

        // 限制返回条数
        if (limit == null || limit <= 0) {
            limit = 50;
        }
        if (limit > 100) {
            limit = 100;
        }

        PageRequest pageRequest = PageRequest.of(0, limit);

        List<Pattern> patterns;
        if (category != null && !category.isEmpty()) {
            patterns = patternRepository.findRankingByCategory(category, since, pageRequest);
        } else {
            patterns = patternRepository.findRanking(since, pageRequest);
        }

        // 转换为响应 DTO
        List<PatternRankingResponse> results = new ArrayList<>();
        int rank = 1;
        for (Pattern p : patterns) {
            PatternRankingResponse item = new PatternRankingResponse();
            item.setRank(rank++);
            item.setId(p.getId());
            item.setPatternCode(p.getPatternCode());
            item.setDescription(p.getDescription());
            item.setImageUrl(p.getImageUrl());
            item.setWatermarkedUrl(p.getWatermarkedUrl());
            item.setViewCount(p.getViewCount() != null ? p.getViewCount() : 0L);
            item.setLikeCount(p.getLikeCount() != null ? p.getLikeCount() : 0L);
            item.setDownloadCount(p.getDownloadCount() != null ? p.getDownloadCount() : 0L);

            // 综合评分 = 浏览*1 + 点赞*3 + 下载*5
            double score = item.getViewCount() * 1.0
                    + item.getLikeCount() * 3.0
                    + item.getDownloadCount() * 5.0;
            item.setScore(score);

            // 填充作者名称
            if (p.getAuthorId() != null) {
                userRepository.findById(p.getAuthorId()).ifPresent(author ->
                    item.setAuthorName(author.getUsername())
                );
            }

            results.add(item);
        }

        return results;
    }
}
