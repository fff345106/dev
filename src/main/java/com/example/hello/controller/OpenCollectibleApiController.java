package com.example.hello.controller;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.hello.dto.PatternDetailResponse;
import com.example.hello.entity.DigitalCollectible;
import com.example.hello.repository.DigitalCollectibleRepository;

@RestController
@RequestMapping("/api/open/collectibles")
public class OpenCollectibleApiController {

    private final DigitalCollectibleRepository digitalCollectibleRepository;

    public OpenCollectibleApiController(DigitalCollectibleRepository digitalCollectibleRepository) {
        this.digitalCollectibleRepository = digitalCollectibleRepository;
    }

    @GetMapping
    public ResponseEntity<List<Map<String, Object>>> listCollectibles() {
        List<DigitalCollectible> collectibles = digitalCollectibleRepository.findByIsVisibleTrue(
                Sort.by(Sort.Direction.DESC, "createdAt"));

        List<Map<String, Object>> response = collectibles.stream().map(collectible -> {
            Map<String, Object> item = new HashMap<>();
            String title = resolveTitle(collectible);
            item.put("id", collectible.getId());
            item.put("title", title);
            item.put("image", collectible.getPatternImageUrl());
            item.put("desc", resolveShortDescription(collectible));
            return item;
        }).toList();

        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}/detail")
    public ResponseEntity<PatternDetailResponse> getCollectibleDetail(@PathVariable Long id) {
        DigitalCollectible collectible = digitalCollectibleRepository.findByIdAndIsVisibleTrue(id).orElse(null);
        if (collectible == null) {
            return ResponseEntity.notFound().build();
        }

        String title = resolveTitle(collectible);

        PatternDetailResponse response = new PatternDetailResponse();
        response.setId(collectible.getId());
        response.setTitle(title);
        response.setPatternCode("");
        response.setImage(collectible.getPatternImageUrl());
        response.setDesc(resolveShortDescription(collectible));
        response.setStory(splitStoryParagraphs(collectible.getStoryText()));

        return ResponseEntity.ok(response);
    }

    private String resolveTitle(DigitalCollectible collectible) {
        String titleFromDescription = firstNonEmptyLine(collectible == null ? null : collectible.getDescription());
        if (hasVisibleText(titleFromDescription)) {
            return titleFromDescription;
        }

        String titleFromStory = firstNonEmptyLine(collectible == null ? null : collectible.getStoryText());
        if (hasVisibleText(titleFromStory)) {
            return titleFromStory;
        }

        return "无标题作品";
    }

    private String resolveShortDescription(DigitalCollectible collectible) {
        String line = firstNonEmptyLine(collectible == null ? null : collectible.getDescription());
        if (hasVisibleText(line)) {
            return line;
        }
        return resolveTitle(collectible);
    }

    private List<String> splitStoryParagraphs(String storyText) {
        List<String> paragraphs = new ArrayList<>();
        if (!hasVisibleText(storyText)) {
            return paragraphs;
        }

        String[] lines = storyText.split("\\r?\\n");
        for (String line : lines) {
            if (hasVisibleText(line)) {
                paragraphs.add(line.trim());
            }
        }
        return paragraphs;
    }

    private String firstNonEmptyLine(String text) {
        if (!hasVisibleText(text)) {
            return null;
        }

        String[] lines = text.split("\\r?\\n");
        for (String line : lines) {
            if (hasVisibleText(line)) {
                return line.trim();
            }
        }
        return null;
    }

    private boolean hasVisibleText(String text) {
        if (text == null) {
            return false;
        }
        String normalized = text
                .replace('\u00A0', ' ')
                .replaceAll("[\\u200B-\\u200D\\uFEFF]", "")
                .trim();
        return !normalized.isEmpty();
    }
}
