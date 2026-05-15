package com.example.hello.controller;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.hello.dto.FeedItemResponse;
import com.example.hello.service.FeedService;
import com.example.hello.util.JwtUtil;

@RestController
@RequestMapping("/api/feed")
public class FeedController {
    private final FeedService feedService;
    private final JwtUtil jwtUtil;

    public FeedController(FeedService feedService, JwtUtil jwtUtil) {
        this.feedService = feedService;
        this.jwtUtil = jwtUtil;
    }

    @GetMapping("/following")
    public ResponseEntity<Page<FeedItemResponse>> getFollowingFeed(
            @RequestHeader("Authorization") String token,
            @PageableDefault(size = 20) Pageable pageable) {
        Long userId = getUserIdFromToken(token);
        return ResponseEntity.ok(feedService.getFollowingFeed(userId, pageable));
    }

    private Long getUserIdFromToken(String token) {
        String jwt = token.replace("Bearer ", "");
        return jwtUtil.extractUserId(jwt);
    }
}
