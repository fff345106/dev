package com.example.hello.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.example.hello.entity.EventStats;

public interface EventStatsRepository extends JpaRepository<EventStats, Long> {

    Optional<EventStats> findByEventId(Long eventId);

    @Modifying
    @Query("UPDATE EventStats e SET e.viewCount = e.viewCount + 1 WHERE e.event.id = :eventId")
    void incrementViewCount(@Param("eventId") Long eventId);

    @Modifying
    @Query("UPDATE EventStats e SET e.participantCount = e.participantCount + 1 WHERE e.event.id = :eventId")
    void incrementParticipantCount(@Param("eventId") Long eventId);

    @Modifying
    @Query("UPDATE EventStats e SET e.submissionCount = e.submissionCount + 1 WHERE e.event.id = :eventId")
    void incrementSubmissionCount(@Param("eventId") Long eventId);

    @Modifying
    @Query("UPDATE EventStats e SET e.shareCount = e.shareCount + 1 WHERE e.event.id = :eventId")
    void incrementShareCount(@Param("eventId") Long eventId);
}
