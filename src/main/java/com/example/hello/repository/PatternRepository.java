package com.example.hello.repository;

import com.example.hello.entity.Pattern;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.util.List;
import java.util.Optional;

public interface PatternRepository extends JpaRepository<Pattern, Long> {
    Optional<Pattern> findByPatternCode(String patternCode);
    boolean existsByPatternCode(String patternCode);
    List<Pattern> findByMainCategory(String mainCategory);
    List<Pattern> findByStyle(String style);
    List<Pattern> findByRegion(String region);
    List<Pattern> findByPeriod(String period);
    List<Pattern> findByDateCode(String dateCode);
    
    @Query("SELECT COUNT(p) FROM Pattern p WHERE p.patternCode LIKE ?1%")
    long countByPatternCodePrefix(String prefix);
    
    @Query("SELECT MAX(p.sequenceNumber) FROM Pattern p WHERE p.dateCode = ?1")
    Integer findMaxSequenceNumberByDateCode(String dateCode);
}
