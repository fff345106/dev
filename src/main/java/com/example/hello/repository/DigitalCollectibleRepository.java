package com.example.hello.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;

import com.example.hello.entity.DigitalCollectible;

public interface DigitalCollectibleRepository extends JpaRepository<DigitalCollectible, Long> {

    Page<DigitalCollectible> findByCreatedByIdOrderByCreatedAtDesc(Long userId, Pageable pageable);

    List<DigitalCollectible> findByIsVisibleTrue(Sort sort);

    Optional<DigitalCollectible> findByIdAndIsVisibleTrue(Long id);
}
