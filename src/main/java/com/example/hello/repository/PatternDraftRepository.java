package com.example.hello.repository;

import com.example.hello.entity.PatternDraft;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PatternDraftRepository extends JpaRepository<PatternDraft, Long> {
    List<PatternDraft> findByUserIdOrderByUpdatedAtDesc(Long userId);
    long countByUserId(Long userId);
}
