package com.example.hello.repository;

import com.example.hello.entity.AppReview;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface AppReviewRepository extends JpaRepository<AppReview, Long> {

    Page<AppReview> findAllByOrderByCreatedAtDesc(Pageable pageable);

    Page<AppReview> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);

    @Query("SELECT AVG(r.rating) FROM AppReview r")
    Double findAverageRating();

    @Query("SELECT COUNT(r) FROM AppReview r")
    long countAll();
}
