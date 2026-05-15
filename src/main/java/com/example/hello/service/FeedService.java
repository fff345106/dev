package com.example.hello.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.hello.dto.FeedItemResponse;
import com.example.hello.entity.Article;
import com.example.hello.entity.Pattern;
import com.example.hello.entity.User;
import com.example.hello.enums.ArticleStatus;
import com.example.hello.repository.ArticleRepository;
import com.example.hello.repository.PatternRepository;
import com.example.hello.repository.UserFollowRepository;
import com.example.hello.repository.UserRepository;

@Service
public class FeedService {
    private final UserFollowRepository userFollowRepository;
    private final PatternRepository patternRepository;
    private final ArticleRepository articleRepository;
    private final UserRepository userRepository;

    public FeedService(UserFollowRepository userFollowRepository,
                       PatternRepository patternRepository,
                       ArticleRepository articleRepository,
                       UserRepository userRepository) {
        this.userFollowRepository = userFollowRepository;
        this.patternRepository = patternRepository;
        this.articleRepository = articleRepository;
        this.userRepository = userRepository;
    }

    public Page<FeedItemResponse> getFollowingFeed(Long userId, Pageable pageable) {
        List<Long> followingIds = userFollowRepository.findFollowingIds(userId);

        if (followingIds.isEmpty()) {
            return Page.empty(pageable);
        }

        List<FeedItemResponse> allItems = new ArrayList<>();

        // 查询关注用户的纹样（已审核通过）
        for (Long authorId : followingIds) {
            List<Pattern> patterns = patternRepository.findByAuthorIdAndStatusOrderByCreatedAtDesc(authorId, "APPROVED");
            for (Pattern p : patterns) {
                FeedItemResponse item = new FeedItemResponse();
                item.setType("PATTERN");
                item.setId(p.getId());
                item.setTitle(p.getDescription() != null ? p.getDescription() : "纹样作品");
                item.setSummary(p.getDescription());
                item.setCoverUrl(p.getWatermarkedUrl() != null ? p.getWatermarkedUrl() : p.getImageUrl());
                item.setAuthorId(p.getAuthorId());
                item.setCreatedAt(p.getCreatedAt());
                // 填充作者信息
                userRepository.findById(p.getAuthorId()).ifPresent(author -> {
                    // 通过 FeedItemResponse 的 setter 设置（需要在循环外处理）
                });
                allItems.add(item);
            }
        }

        // 查询关注用户的文章（已发布）
        for (Long authorId : followingIds) {
            List<Article> articles = articleRepository.findByAuthorIdAndStatus(authorId, ArticleStatus.PUBLISHED, Pageable.unpaged()).getContent();
            for (Article a : articles) {
                FeedItemResponse item = new FeedItemResponse();
                item.setType("ARTICLE");
                item.setId(a.getId());
                item.setTitle(a.getTitle());
                item.setSummary(a.getSummary());
                item.setCoverUrl(a.getCoverUrl());
                item.setAuthorId(a.getAuthor().getId());
                item.setCreatedAt(a.getCreatedAt());
                allItems.add(item);
            }
        }

        // 按创建时间降序排序
        allItems.sort(Comparator.comparing(FeedItemResponse::getCreatedAt).reversed());

        // 填充作者名称
        for (FeedItemResponse item : allItems) {
            if (item.getAuthorId() != null) {
                userRepository.findById(item.getAuthorId()).ifPresent(author -> {
                    item.setAuthorName(author.getUsername());
                    item.setAuthorAvatar(author.getAvatarUrl());
                });
            }
        }

        // 手动分页
        int start = (int) pageable.getOffset();
        int end = Math.min(start + pageable.getPageSize(), allItems.size());

        List<FeedItemResponse> pageContent;
        if (start >= allItems.size()) {
            pageContent = List.of();
        } else {
            pageContent = allItems.subList(start, end);
        }

        return new PageImpl<>(pageContent, pageable, allItems.size());
    }

    @Transactional
    public void follow(Long followerId, Long followingId) {
        if (followerId.equals(followingId)) {
            throw new RuntimeException("不能关注自己");
        }

        if (!userRepository.existsById(followingId)) {
            throw new RuntimeException("用户不存在");
        }

        if (userFollowRepository.existsByFollowerIdAndFollowingId(followerId, followingId)) {
            throw new RuntimeException("已经关注了该用户");
        }

        User follower = userRepository.findById(followerId)
                .orElseThrow(() -> new RuntimeException("用户不存在"));
        User following = userRepository.findById(followingId)
                .orElseThrow(() -> new RuntimeException("目标用户不存在"));

        com.example.hello.entity.UserFollow follow = new com.example.hello.entity.UserFollow();
        follow.setFollower(follower);
        follow.setFollowing(following);
        userFollowRepository.save(follow);
    }

    @Transactional
    public void unfollow(Long followerId, Long followingId) {
        if (!userFollowRepository.existsByFollowerIdAndFollowingId(followerId, followingId)) {
            throw new RuntimeException("未关注该用户");
        }
        userFollowRepository.deleteByFollowerIdAndFollowingId(followerId, followingId);
    }

    public boolean isFollowing(Long followerId, Long followingId) {
        return userFollowRepository.existsByFollowerIdAndFollowingId(followerId, followingId);
    }

    public long getFollowerCount(Long userId) {
        return userFollowRepository.countByFollowingId(userId);
    }
}
