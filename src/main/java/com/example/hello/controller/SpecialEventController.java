package com.example.hello.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.lang.NonNull;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.hello.dto.SpecialEventCreateRequest;
import com.example.hello.dto.SpecialEventListItemResponse;
import com.example.hello.service.SpecialEventService;

@RestController
@RequestMapping("/api/events")
public class SpecialEventController {

    private final SpecialEventService specialEventService;

    public SpecialEventController(SpecialEventService specialEventService) {
        this.specialEventService = specialEventService;
    }

    @GetMapping
    public ResponseEntity<List<SpecialEventListItemResponse>> listEvents() {
        return ResponseEntity.ok(specialEventService.listEvents());
    }

    @PostMapping
    public ResponseEntity<SpecialEventListItemResponse> create(@NonNull @RequestBody SpecialEventCreateRequest request) {
        return ResponseEntity.ok(specialEventService.create(request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@NonNull @PathVariable Long id) {
        specialEventService.delete(id);
        return ResponseEntity.ok().build();
    }
}
