package com.example.hello.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.hello.dto.EventStatsResponse;
import com.example.hello.service.EventStatsService;

@RestController
@RequestMapping("/api/events")
public class EventStatsController {

    private final EventStatsService eventStatsService;

    public EventStatsController(EventStatsService eventStatsService) {
        this.eventStatsService = eventStatsService;
    }

    @GetMapping("/{id}/stats")
    public ResponseEntity<EventStatsResponse> getEventStats(@PathVariable Long id) {
        return ResponseEntity.ok(eventStatsService.getStats(id));
    }
}
