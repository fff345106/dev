package com.example.hello.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.example.hello.entity.PatternPending;
import com.example.hello.enums.AuditStatus;

public interface PatternPendingRepository extends JpaRepository<PatternPending, Long> {
    List<PatternPending> findByStatus(AuditStatus status);
    Page<PatternPending> findByStatus(AuditStatus status, Pageable pageable);

    List<PatternPending> findBySubmitterId(Long submitterId);
    Page<PatternPending> findBySubmitterId(Long submitterId, Pageable pageable);

    List<PatternPending> findBySubmitterIdAndStatus(Long submitterId, AuditStatus status);
    Page<PatternPending> findBySubmitterIdAndStatus(Long submitterId, AuditStatus status, Pageable pageable);
    
    long countByStatus(AuditStatus status);
    
    @Query("SELECT COUNT(p) FROM PatternPending p WHERE p.createdAt >= :startTime AND p.createdAt < :endTime")
    long countByCreatedAtBetween(@Param("startTime") LocalDateTime startTime, @Param("endTime") LocalDateTime endTime);
    
    List<PatternPending> findBySubmitterIdOrderByCreatedAtDesc(Long submitterId, Pageable pageable);

    // 查询被驳回的可回收编码（按序列号升序，优先回收小序号）
    @Query("SELECT p FROM PatternPending p WHERE p.dateCode = :dateCode AND p.status = 'REJECTED' AND p.patternCode IS NOT NULL ORDER BY p.sequenceNumber ASC")
    List<PatternPending> findRecyclableCodes(@Param("dateCode") String dateCode);

    // 查询当天待审核和已通过的最大序列号
    @Query("SELECT MAX(p.sequenceNumber) FROM PatternPending p WHERE p.dateCode = :dateCode AND p.status IN ('PENDING', 'APPROVED')")
    Integer findMaxActiveSequenceNumberByDateCode(@Param("dateCode") String dateCode);

    Optional<PatternPending> findByPatternCode(String patternCode);

    // 查询指定时间之前创建的记录（用于定时清理）
    List<PatternPending> findByCreatedAtBefore(LocalDateTime cutoffTime);

    // 查询已审核通过且在指定时间之前的记录
    @Query("SELECT p FROM PatternPending p WHERE p.status = 'APPROVED' AND p.createdAt < :cutoffTime")
    List<PatternPending> findApprovedBeforeTime(@Param("cutoffTime") LocalDateTime cutoffTime);
}
