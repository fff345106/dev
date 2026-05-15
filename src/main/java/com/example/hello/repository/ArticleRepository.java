package com.example.hello.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.example.hello.entity.Article;
import com.example.hello.enums.ArticleStatus;

public interface ArticleRepository extends JpaRepository<Article, Long> {
    Page<Article> findByAuthorId(Long authorId, Pageable pageable);

    Page<Article> findByStatus(ArticleStatus status, Pageable pageable);

    Page<Article> findByAuthorIdAndStatus(Long authorId, ArticleStatus status, Pageable pageable);

    Page<Article> findByStatusOrderByCreatedAtDesc(ArticleStatus status, Pageable pageable);

    long countByAuthorId(Long authorId);

    @Modifying
    @Query("UPDATE Article a SET a.viewCount = a.viewCount + 1 WHERE a.id = :id")
    void incrementViewCount(@Param("id") Long id);

    @Query("SELECT COALESCE(SUM(a.viewCount), 0) FROM Article a WHERE a.author.id = :authorId")
    long sumViewCountByAuthorId(@Param("authorId") Long authorId);

    @Query("SELECT COALESCE(SUM(a.likeCount), 0) FROM Article a WHERE a.author.id = :authorId")
    long sumLikeCountByAuthorId(@Param("authorId") Long authorId);
}
