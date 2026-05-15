package com.example.hello.service;

import java.util.ArrayList;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.hello.dto.EventStatsResponse;
import com.example.hello.entity.EventStats;
import com.example.hello.entity.SpecialEvent;
import com.example.hello.repository.EventStatsRepository;
import com.example.hello.repository.SpecialEventRepository;

@Service
public class EventStatsService {

    private final EventStatsRepository eventStatsRepository;
    private final SpecialEventRepository specialEventRepository;

    public EventStatsService(EventStatsRepository eventStatsRepository,
                             SpecialEventRepository specialEventRepository) {
        this.eventStatsRepository = eventStatsRepository;
        this.specialEventRepository = specialEventRepository;
    }

    public EventStatsResponse getStats(Long eventId) {
        EventStats stats = ensureStatsExist(eventId);
        SpecialEvent event = specialEventRepository.findById(eventId)
                .orElseThrow(() -> new RuntimeException("Event not found with id: " + eventId));

        EventStatsResponse response = new EventStatsResponse();
        response.setEventId(eventId);
        response.setEventName(event.getTitle());
        response.setViewCount(stats.getViewCount());
        response.setParticipantCount(stats.getParticipantCount());
        response.setSubmissionCount(stats.getSubmissionCount());
        response.setShareCount(stats.getShareCount());
        response.setDailyTrend(new ArrayList<>());
        return response;
    }

    @Transactional
    public void incrementView(Long eventId) {
        ensureStatsExist(eventId);
        eventStatsRepository.incrementViewCount(eventId);
    }

    @Transactional
    public void incrementParticipant(Long eventId) {
        ensureStatsExist(eventId);
        eventStatsRepository.incrementParticipantCount(eventId);
    }

    @Transactional
    public void incrementSubmission(Long eventId) {
        ensureStatsExist(eventId);
        eventStatsRepository.incrementSubmissionCount(eventId);
    }

    @Transactional
    public void incrementShare(Long eventId) {
        ensureStatsExist(eventId);
        eventStatsRepository.incrementShareCount(eventId);
    }

    private EventStats ensureStatsExist(Long eventId) {
        return eventStatsRepository.findByEventId(eventId).orElseGet(() -> {
            SpecialEvent event = specialEventRepository.findById(eventId)
                    .orElseThrow(() -> new RuntimeException("Event not found with id: " + eventId));
            EventStats stats = new EventStats();
            stats.setEvent(event);
            return eventStatsRepository.save(stats);
        });
    }
}
