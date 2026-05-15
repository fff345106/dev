package com.example.hello.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import com.example.hello.entity.Collaboration;
import com.example.hello.enums.CollaborationStatus;

public interface CollaborationRepository extends JpaRepository<Collaboration, Long> {

    Page<Collaboration> findByEnterpriseIdOrderByCreatedAtDesc(Long enterpriseId, Pageable pageable);

    Page<Collaboration> findByMasterIdOrderByCreatedAtDesc(Long masterId, Pageable pageable);

    Page<Collaboration> findByStatusOrderByCreatedAtDesc(CollaborationStatus status, Pageable pageable);

    boolean existsByEnterpriseIdAndMasterIdAndStatus(Long enterpriseId, Long masterId, CollaborationStatus status);
}
