package com.example.hello.service;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.hello.dto.ArticleCreateRequest;
import com.example.hello.dto.ArticleResponse;
import com.example.hello.dto.ArticleUpdateRequest;
import com.example.hello.entity.Article;
import com.example.hello.entity.User;
import com.example.hello.enums.ArticleStatus;
import com.example.hello.repository.ArticleRepository;
import com.example.hello.repository.UserRepository;

@Service
public class ArticleService {
    private final ArticleRepository articleRepository;
    private final UserRepository userRepository;

    public ArticleService(ArticleRepository articleRepository, UserRepository userRepository) {
        this.articleRepository = articleRepository;
        this.userRepository = userRepository;
    }

    public Page<ArticleResponse> list(Long authorId, ArticleStatus status, Pageable pageable) {
        Page<Article> articles;
        if (authorId != null && status != null) {
            articles = articleRepository.findByAuthorIdAndStatus(authorId, status, pageable);
        } else if (authorId != null) {
            articles = articleRepository.findByAuthorId(authorId, pageable);
        } else if (status != null) {
            articles = articleRepository.findByStatus(status, pageable);
        } else {
            articles = articleRepository.findAll(pageable);
        }
        return articles.map(ArticleResponse::fromEntity);
    }

    @Transactional
    public ArticleResponse create(ArticleCreateRequest request, Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("用户不存在"));

        if (user.getRole() != com.example.hello.enums.UserRole.MASTER_ARTISAN
                && user.getRole() != com.example.hello.enums.UserRole.SUPER_ADMIN) {
            throw new RuntimeException("仅技艺大师角色可以发布文章");
        }

        Article article = new Article();
        article.setTitle(request.getTitle());
        article.setContent(request.getContent());
        article.setSummary(request.getSummary());
        article.setCoverUrl(request.getCoverUrl());
        article.setAuthor(user);

        if (request.getStatus() != null && !request.getStatus().isEmpty()) {
            article.setStatus(ArticleStatus.valueOf(request.getStatus()));
        }

        Article saved = articleRepository.save(article);
        return ArticleResponse.fromEntity(saved);
    }

    @Transactional
    public ArticleResponse getDetail(Long id) {
        articleRepository.incrementViewCount(id);
        Article article = articleRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("文章不存在"));
        return ArticleResponse.fromEntity(article);
    }

    @Transactional
    public ArticleResponse update(Long id, ArticleUpdateRequest request, Long userId) {
        Article article = articleRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("文章不存在"));

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("用户不存在"));
        if (!article.getAuthor().getId().equals(userId) && user.getRole() != com.example.hello.enums.UserRole.SUPER_ADMIN) {
            throw new RuntimeException("无权修改他人文章");
        }

        if (request.getTitle() != null) {
            article.setTitle(request.getTitle());
        }
        if (request.getContent() != null) {
            article.setContent(request.getContent());
        }
        if (request.getSummary() != null) {
            article.setSummary(request.getSummary());
        }
        if (request.getCoverUrl() != null) {
            article.setCoverUrl(request.getCoverUrl());
        }
        if (request.getStatus() != null && !request.getStatus().isEmpty()) {
            article.setStatus(ArticleStatus.valueOf(request.getStatus()));
        }

        Article saved = articleRepository.save(article);
        return ArticleResponse.fromEntity(saved);
    }

    @Transactional
    public void delete(Long id, Long userId) {
        Article article = articleRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("文章不存在"));

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("用户不存在"));
        if (!article.getAuthor().getId().equals(userId) && user.getRole() != com.example.hello.enums.UserRole.SUPER_ADMIN) {
            throw new RuntimeException("无权删除他人文章");
        }

        articleRepository.delete(article);
    }
}
