package com.example.hello.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

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
}
