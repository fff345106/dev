package com.example.hello.controller;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.HtmlUtils;

import com.example.hello.entity.Pattern;
import com.example.hello.repository.PatternRepository;

import jakarta.persistence.criteria.Predicate;

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
            map.put("description", resolveDisplayDescription(pattern));
            map.put("mainCategory", pattern.getMainCategory());
            map.put("style", pattern.getStyle());
            map.put("region", pattern.getRegion());
            map.put("period", pattern.getPeriod());
            return map;
        });

        return ResponseEntity.ok(result);
    }

    @GetMapping(value = "/{code}/table", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> getPatternTable(@PathVariable String code) {
        Pattern pattern = patternRepository.findByPatternCode(code).orElse(null);
        if (pattern == null || !"APPROVED".equalsIgnoreCase(pattern.getStatus())) {
            String notFoundHtml = """
                    <!doctype html>
                    <html lang=\"zh-CN\">
                    <head>
                      <meta charset=\"UTF-8\" />
                      <meta name=\"viewport\" content=\"width=device-width, initial-scale=1\" />
                      <title>纹样信息不存在</title>
                      <style>
                        body { font-family: -apple-system, BlinkMacSystemFont, \"Segoe UI\", Roboto, \"PingFang SC\", \"Microsoft YaHei\", sans-serif; margin: 24px; color: #222; }
                        .card { max-width: 720px; margin: 0 auto; border: 1px solid #eee; border-radius: 12px; padding: 20px; box-shadow: 0 6px 24px rgba(0,0,0,.06); }
                        h1 { margin: 0 0 8px; font-size: 20px; }
                        p { margin: 0; color: #666; }
                      </style>
                    </head>
                    <body>
                      <div class=\"card\">
                        <h1>未找到纹样信息</h1>
                        <p>请确认二维码是否有效，或联系管理员。</p>
                      </div>
                    </body>
                    </html>
                    """;
            return ResponseEntity.status(404)
                    .contentType(MediaType.TEXT_HTML)
                    .body(notFoundHtml);
        }

        DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

String tableRows = String.join("",
        row("纹样编码", pattern.getPatternCode()),
        "<tr><th>描述</th><td class=\"desc\">" + safe(resolveDisplayDescription(pattern)) + "</td></tr>",
        row("主分类", pattern.getMainCategory()),
        row("子分类", pattern.getSubCategory()),
        row("风格", pattern.getStyle()),
        row("地区", pattern.getRegion()),
        row("时期", pattern.getPeriod()),
        row("日期码", pattern.getDateCode()),
        row("序号", pattern.getSequenceNumber()),
        row("审核状态", pattern.getStatus()),
        row("创建时间", pattern.getCreatedAt() == null ? null : dtf.format(pattern.getCreatedAt())),
        row("更新时间", pattern.getUpdatedAt() == null ? null : dtf.format(pattern.getUpdatedAt())));

        String html = """
                <!doctype html>
                <html lang=\"zh-CN\">
                <head>
                  <meta charset=\"UTF-8\" />
                  <meta name=\"viewport\" content=\"width=device-width, initial-scale=1\" />
                  <title>纹样信息</title>
                  <style>
                    :root { color-scheme: light; }
                    body { font-family: -apple-system, BlinkMacSystemFont, \"Segoe UI\", Roboto, \"PingFang SC\", \"Microsoft YaHei\", sans-serif; margin: 20px; color: #222; background: #f7f8fa; }
                    .container { max-width: 960px; margin: 0 auto; }
                    .card { background: #fff; border: 1px solid #eee; border-radius: 14px; padding: 18px; box-shadow: 0 6px 24px rgba(0,0,0,.05); }
                    h1 { margin: 0 0 14px; font-size: 24px; }
                    table { width: 100%%; border-collapse: collapse; }
                    th, td { padding: 10px 12px; border-bottom: 1px solid #eee; vertical-align: top; }
                    th { width: 160px; text-align: left; color: #555; background: #fafafa; }
                    .desc { white-space: pre-wrap; line-height: 1.6; }
                    @media (max-width: 820px) { th { width: 120px; } }
                  </style>
                </head>
                <body>
                  <div class=\"container\">
                    <div class=\"card\">
                      <h1>纹样信息</h1>
                      <table>
                        %s
                      </table>
                    </div>
                  </div>
                </body>
                </html>
                """.formatted(tableRows);

        return ResponseEntity.ok()
                .contentType(MediaType.TEXT_HTML)
                .body(html);
    }

    private String row(String key, Object value) {
        return "<tr><th>" + safe(key) + "</th><td>" + safe(value) + "</td></tr>";
    }

    private String resolveDisplayDescription(Pattern pattern) {
        if (pattern == null) {
            return null;
        }
        if (hasVisibleText(pattern.getDescription())) {
            return pattern.getDescription();
        }
        return pattern.getStoryText();
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

    private String safe(Object value) {
        if (value == null) {
            return "-";
        }
        String text = String.valueOf(value);
        if (!hasVisibleText(text)) {
            return "-";
        }
        return HtmlUtils.htmlEscape(text);
    }
}
