package com.example.hello.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import com.example.hello.entity.DigitalCollectible;

public interface DigitalCollectibleRepository extends JpaRepository<DigitalCollectible, Long> {

    Page<DigitalCollectible> findByCreatedByIdOrderByCreatedAtDesc(Long userId, Pageable pageable);
}
