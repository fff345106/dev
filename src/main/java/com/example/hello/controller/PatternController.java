package com.example.hello.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.hello.dto.PatternRequest;
import com.example.hello.entity.Pattern;
import com.example.hello.service.PatternService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/patterns")
public class PatternController {
    private final PatternService patternService;

    public PatternController(PatternService patternService) {
        this.patternService = patternService;
    }

    @PostMapping
    public ResponseEntity<Pattern> create(@Valid @RequestBody PatternRequest request) {
        return ResponseEntity.ok(patternService.create(request));
    }

    @GetMapping
    public ResponseEntity<List<Pattern>> findAll() {
        return ResponseEntity.ok(patternService.findAll());
    }

    @GetMapping("/{id}")
    public ResponseEntity<Pattern> findById(@PathVariable Long id) {
        return ResponseEntity.ok(patternService.findById(id));
    }

    @GetMapping("/code/{code}")
    public ResponseEntity<Pattern> findByCode(@PathVariable String code) {
        return ResponseEntity.ok(patternService.findByCode(code));
    }

    @PutMapping("/{id}")
    public ResponseEntity<Pattern> update(@PathVariable Long id, @Valid @RequestBody PatternRequest request) {
        return ResponseEntity.ok(patternService.update(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        patternService.delete(id);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/category/{mainCategory}")
    public ResponseEntity<List<Pattern>> findByMainCategory(@PathVariable String mainCategory) {
        return ResponseEntity.ok(patternService.findByMainCategory(mainCategory));
    }

    @GetMapping("/style/{style}")
    public ResponseEntity<List<Pattern>> findByStyle(@PathVariable String style) {
        return ResponseEntity.ok(patternService.findByStyle(style));
    }

    @GetMapping("/region/{region}")
    public ResponseEntity<List<Pattern>> findByRegion(@PathVariable String region) {
        return ResponseEntity.ok(patternService.findByRegion(region));
    }

    @GetMapping("/period/{period}")
    public ResponseEntity<List<Pattern>> findByPeriod(@PathVariable String period) {
        return ResponseEntity.ok(patternService.findByPeriod(period));
    }
}
