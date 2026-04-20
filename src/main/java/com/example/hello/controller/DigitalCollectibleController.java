package com.example.hello.controller;

import java.util.Map;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.hello.dto.DigitalCollectibleCreateRequest;
import com.example.hello.entity.DigitalCollectible;
import com.example.hello.service.DigitalCollectibleService;
import com.example.hello.util.JwtUtil;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/digital-collectibles")
public class DigitalCollectibleController {

    private final DigitalCollectibleService digitalCollectibleService;
    private final JwtUtil jwtUtil;

    public DigitalCollectibleController(DigitalCollectibleService digitalCollectibleService, JwtUtil jwtUtil) {
        this.digitalCollectibleService = digitalCollectibleService;
        this.jwtUtil = jwtUtil;
    }

    @PostMapping
    public ResponseEntity<DigitalCollectible> create(
            @Valid @RequestBody DigitalCollectibleCreateRequest request,
            @RequestHeader("Authorization") String token) {
        Long userId = getUserIdFromToken(token);
        return ResponseEntity.ok(digitalCollectibleService.create(request, userId));
    }

    @GetMapping("/my")
    public ResponseEntity<Page<DigitalCollectible>> findMy(
            @RequestHeader("Authorization") String token,
            @PageableDefault(size = 20) Pageable pageable) {
        Long userId = getUserIdFromToken(token);
        return ResponseEntity.ok(digitalCollectibleService.findMy(userId, pageable));
    }

    @GetMapping("/my/{id}")
    public ResponseEntity<DigitalCollectible> findMyById(
            @PathVariable Long id,
            @RequestHeader("Authorization") String token) {
        Long userId = getUserIdFromToken(token);
        return ResponseEntity.ok(digitalCollectibleService.findMyById(id, userId));
    }

    @PutMapping("/my/{id}/visibility")
    public ResponseEntity<DigitalCollectible> updateVisibility(
            @PathVariable Long id,
            @RequestParam(value = "visible", required = false) Boolean visible,
            @RequestBody(required = false) Map<String, Object> body,
            @RequestHeader("Authorization") String token) {
        if (visible == null && body != null) {
            Object value = body.get("visible");
            if (value == null) {
                value = body.get("isVisible");
            }
            if (value instanceof Boolean boolValue) {
                visible = boolValue;
            } else if (value instanceof String strValue) {
                visible = Boolean.parseBoolean(strValue);
            }
        }

        if (visible == null) {
            throw new IllegalArgumentException("visible参数不能为空");
        }

        Long userId = getUserIdFromToken(token);
        return ResponseEntity.ok(digitalCollectibleService.updateVisibility(id, userId, visible));
    }

    private Long getUserIdFromToken(String token) {
        String jwt = token.replace("Bearer ", "");
        return jwtUtil.extractUserId(jwt);
    }
}
