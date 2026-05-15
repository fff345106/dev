package com.example.hello.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.hello.dto.AuthorStatsResponse;
import com.example.hello.dto.StatsResponse;
import com.example.hello.service.AuthorStatsService;
import com.example.hello.service.StatsService;
import com.example.hello.util.JwtUtil;

@RestController
@RequestMapping("/api/stats")
public class StatsController {
    private final StatsService statsService;
    private final AuthorStatsService authorStatsService;
    private final JwtUtil jwtUtil;

    public StatsController(StatsService statsService, AuthorStatsService authorStatsService, JwtUtil jwtUtil) {
        this.statsService = statsService;
        this.authorStatsService = authorStatsService;
        this.jwtUtil = jwtUtil;
    }

    @GetMapping
    public ResponseEntity<StatsResponse> getStats() {
        return ResponseEntity.ok(statsService.getStats());
    }

    /**
     * 获取作者统计数据（仅本人可查看）
     */
    @GetMapping("/author/{id}")
    public ResponseEntity<?> getAuthorStats(
            @PathVariable Long id,
            @RequestParam(defaultValue = "30") int days,
            @RequestHeader("Authorization") String token) {
        try {
            Long userId = getUserIdFromToken(token);
            AuthorStatsResponse stats = authorStatsService.getStats(id, days, userId);
            return ResponseEntity.ok(stats);
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(java.util.Map.of("message", e.getMessage()));
        }
    }

    private Long getUserIdFromToken(String token) {
        String jwt = token.replace("Bearer ", "");
        return jwtUtil.extractUserId(jwt);
    }
}
