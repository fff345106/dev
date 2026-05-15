package com.example.hello.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.example.hello.entity.Pattern;

public interface PatternRepository extends JpaRepository<Pattern, Long>, JpaSpecificationExecutor<Pattern> {
    Optional<Pattern> findByPatternCode(String patternCode);
    boolean existsByPatternCode(String patternCode);
    List<Pattern> findByMainCategory(String mainCategory);
    Page<Pattern> findByMainCategory(String mainCategory, Pageable pageable);
    
    List<Pattern> findByStyle(String style);
    Page<Pattern> findByStyle(String style, Pageable pageable);
    
    List<Pattern> findByRegion(String region);
    Page<Pattern> findByRegion(String region, Pageable pageable);
    
    List<Pattern> findByPeriod(String period);
    Page<Pattern> findByPeriod(String period, Pageable pageable);
    
    List<Pattern> findByDateCode(String dateCode);
    
    @Query("SELECT COUNT(p) FROM Pattern p WHERE p.patternCode LIKE ?1%")
    long countByPatternCodePrefix(String prefix);
    
    @Query("SELECT MAX(p.sequenceNumber) FROM Pattern p WHERE p.dateCode = ?1")
    Integer findMaxSequenceNumberByDateCode(String dateCode);

    // 作者统计查询
    @Query("SELECT COALESCE(SUM(p.viewCount), 0) FROM Pattern p WHERE p.authorId = :authorId")
    long sumViewCountByAuthorId(@Param("authorId") Long authorId);

    @Query("SELECT COALESCE(SUM(p.likeCount), 0) FROM Pattern p WHERE p.authorId = :authorId")
    long sumLikeCountByAuthorId(@Param("authorId") Long authorId);

    @Query("SELECT COALESCE(SUM(p.downloadCount), 0) FROM Pattern p WHERE p.authorId = :authorId")
    long sumDownloadCountByAuthorId(@Param("authorId") Long authorId);

    long countByAuthorId(Long authorId);

    // 排行榜查询（综合评分 = 浏览*1 + 点赞*3 + 下载*5）
    @Query("SELECT p FROM Pattern p WHERE p.status = 'APPROVED' AND p.createdAt >= :since ORDER BY (COALESCE(p.viewCount, 0) + COALESCE(p.likeCount, 0) * 3 + COALESCE(p.downloadCount, 0) * 5) DESC")
    List<Pattern> findRanking(@Param("since") LocalDateTime since, Pageable pageable);

    @Query("SELECT p FROM Pattern p WHERE p.status = 'APPROVED' AND p.mainCategory = :category AND p.createdAt >= :since ORDER BY (COALESCE(p.viewCount, 0) + COALESCE(p.likeCount, 0) * 3 + COALESCE(p.downloadCount, 0) * 5) DESC")
    List<Pattern> findRankingByCategory(@Param("category") String category, @Param("since") LocalDateTime since, Pageable pageable);

    // 按作者查询已审核通过的纹样
    List<Pattern> findByAuthorIdAndStatusOrderByCreatedAtDesc(Long authorId, String status);
}
