package com.example.hello.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.hello.dto.SpecialEventListItemResponse;
import com.example.hello.service.SpecialEventService;

@RestController
@RequestMapping("/api/open/events")
public class OpenEventApiController {

    private final SpecialEventService specialEventService;

    public OpenEventApiController(SpecialEventService specialEventService) {
        this.specialEventService = specialEventService;
    }

    @GetMapping
    public ResponseEntity<List<SpecialEventListItemResponse>> listEvents() {
        return ResponseEntity.ok(specialEventService.listEvents());
    }
}
