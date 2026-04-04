package com.example.hello.controller;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.hello.entity.Pattern;
import com.example.hello.repository.PatternRepository;

import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.List;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/open/patterns")
public class OpenApiController {
    
    private final PatternRepository patternRepository;

    public OpenApiController(PatternRepository patternRepository) {
        this.patternRepository = patternRepository;
    }

    /**
     * 查找纹样图片的接口，用来给其他网站提供纹样图片
     * @param keyword 关键词（搜索描述和纹样编码）
     * @param mainCategory 主分类
     * @param style 风格
     * @param region 地区
     * @param period 时期
     * @param pageable 分页参数
     * @return 纹样图片列表
     */
    @GetMapping("/images")
    public ResponseEntity<Page<Map<String, Object>>> searchImages(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String mainCategory,
            @RequestParam(required = false) String style,
            @RequestParam(required = false) String region,
            @RequestParam(required = false) String period,
            Pageable pageable) {
        
        Specification<Pattern> spec = (root, query, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();
            
            if (keyword != null && !keyword.trim().isEmpty()) {
                String likeKeyword = "%" + keyword.trim() + "%";
                predicates.add(criteriaBuilder.or(
                    criteriaBuilder.like(root.get("description"), likeKeyword),
                    criteriaBuilder.like(root.get("patternCode"), likeKeyword)
                ));
            }
            if (mainCategory != null && !mainCategory.trim().isEmpty()) {
                predicates.add(criteriaBuilder.equal(root.get("mainCategory"), mainCategory.trim()));
            }
            if (style != null && !style.trim().isEmpty()) {
                predicates.add(criteriaBuilder.equal(root.get("style"), style.trim()));
            }
            if (region != null && !region.trim().isEmpty()) {
                predicates.add(criteriaBuilder.equal(root.get("region"), region.trim()));
            }
            if (period != null && !period.trim().isEmpty()) {
                predicates.add(criteriaBuilder.equal(root.get("period"), period.trim()));
            }
            // 只返回存在图片且状态为 APPROVED 的记录
            predicates.add(criteriaBuilder.equal(root.get("status"), "APPROVED"));
            predicates.add(criteriaBuilder.isNotNull(root.get("imageUrl")));
            predicates.add(criteriaBuilder.notEqual(root.get("imageUrl"), ""));
            
            return criteriaBuilder.and(predicates.toArray(new Predicate[0]));
        };

        Page<Pattern> patterns = patternRepository.findAll(spec, pageable);
        
        // 转换为精简的 Map，只提供必要的图片信息，避免暴露内部字段
        Page<Map<String, Object>> result = patterns.map(pattern -> {
            Map<String, Object> map = new HashMap<>();
            map.put("patternCode", pattern.getPatternCode());
            map.put("imageUrl", pattern.getImageUrl());
            map.put("description", pattern.getDescription());
            map.put("mainCategory", pattern.getMainCategory());
            map.put("style", pattern.getStyle());
            map.put("region", pattern.getRegion());
            map.put("period", pattern.getPeriod());
            return map;
        });

        return ResponseEntity.ok(result);
    }
}