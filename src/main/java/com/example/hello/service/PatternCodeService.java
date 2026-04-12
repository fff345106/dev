package com.example.hello.service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.example.hello.dto.PatternRequest;
import com.example.hello.entity.Pattern;
import com.example.hello.entity.PatternPending;
import com.example.hello.enums.PatternCodeEnum;
import com.example.hello.repository.PatternPendingRepository;
import com.example.hello.repository.PatternRepository;

@Service
public class PatternCodeService {
    private static final DateTimeFormatter DATE_CODE_FORMATTER = DateTimeFormatter.ofPattern("yyMMdd");

    private final PatternPendingRepository pendingRepository;
    private final PatternRepository patternRepository;

    public PatternCodeService(PatternPendingRepository pendingRepository, PatternRepository patternRepository) {
        this.pendingRepository = pendingRepository;
        this.patternRepository = patternRepository;
    }

    public String normalizeCode(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim().toUpperCase(Locale.ROOT);
    }

    public NormalizedPatternCodes normalizeSegments(
            String mainCategory,
            String subCategory,
            String style,
            String region,
            String period) {
        return new NormalizedPatternCodes(
                normalizeCode(mainCategory),
                normalizeCode(subCategory),
                normalizeCode(style),
                normalizeCode(region),
                normalizeCode(period));
    }

    public NormalizedPatternCodes normalizeRequest(PatternRequest request) {
        return normalizeSegments(
                request.getMainCategory(),
                request.getSubCategory(),
                request.getStyle(),
                request.getRegion(),
                request.getPeriod());
    }

    public void validateRequest(PatternRequest request) {
        validateNormalized(normalizeRequest(request));
    }

    public void validateSegments(
            String mainCategory,
            String subCategory,
            String style,
            String region,
            String period) {
        validateNormalized(normalizeSegments(mainCategory, subCategory, style, region, period));
    }

    public boolean hasSubCategory(String mainCategory) {
        String normalizedMainCategory = requiredCode("主类别代码", mainCategory);
        return PatternCodeEnum.MainCategory.fromCode(normalizedMainCategory).hasSubCategory();
    }

    public boolean isValidSubCategory(String mainCategory, String subCategory) {
        String normalizedMainCategory = requiredCode("主类别代码", mainCategory);
        String normalizedSubCategory = requiredCode("子类别代码", subCategory);
        return PatternCodeEnum.isValidSubCategory(normalizedMainCategory, normalizedSubCategory);
    }

    public List<Map<String, String>> getSubCategories(String mainCategory) {
        return PatternCodeEnum.getSubCategories(requiredCode("主类别代码", mainCategory));
    }

    public CodeLabels resolveLabels(
            String mainCategory,
            String subCategory,
            String style,
            String region,
            String period) {
        NormalizedPatternCodes codes = normalizeSegments(mainCategory, subCategory, style, region, period);
        validateNormalized(codes);
        return new CodeLabels(
                PatternCodeEnum.MainCategory.fromCode(codes.mainCategory()).getName(),
                resolveSubCategoryName(codes.mainCategory(), codes.subCategory()),
                PatternCodeEnum.Style.fromCode(codes.style()).getName(),
                PatternCodeEnum.Region.fromCode(codes.region()).getName(),
                PatternCodeEnum.Period.fromCode(codes.period()).getName());
    }

    public String generateDateCode(LocalDate date) {
        return date.format(DATE_CODE_FORMATTER);
    }

    public String buildPatternCode(
            String mainCategory,
            String subCategory,
            String style,
            String region,
            String period,
            String dateCode,
            Integer sequenceNumber) {
        NormalizedPatternCodes codes = normalizeSegments(mainCategory, subCategory, style, region, period);
        validateNormalized(codes);
        if (dateCode == null || dateCode.isBlank()) {
            throw new IllegalArgumentException("日期代码不能为空");
        }
        if (sequenceNumber == null || sequenceNumber <= 0) {
            throw new IllegalArgumentException("序列号必须大于0");
        }
        return String.format(
                "%s-%s-%s-%s-%s-%s-%03d",
                codes.mainCategory(),
                codes.subCategory(),
                codes.style(),
                codes.region(),
                codes.period(),
                dateCode,
                sequenceNumber);
    }

    public void assignPendingCode(PatternPending pending) {
        NormalizedPatternCodes codes = normalizeSegments(
                pending.getMainCategory(),
                pending.getSubCategory(),
                pending.getStyle(),
                pending.getRegion(),
                pending.getPeriod());
        validateNormalized(codes);
        applyNormalizedCodes(pending, codes);

        String dateCode = generateDateCode(LocalDate.now());
        pending.setDateCode(dateCode);

        Integer sequenceNumber = findRecyclableSequenceNumber(dateCode);
        if (sequenceNumber == null) {
            sequenceNumber = nextSequenceNumber(dateCode);
        }
        pending.setSequenceNumber(sequenceNumber);
        pending.setPatternCode(buildPatternCode(
                pending.getMainCategory(),
                pending.getSubCategory(),
                pending.getStyle(),
                pending.getRegion(),
                pending.getPeriod(),
                pending.getDateCode(),
                pending.getSequenceNumber()));
    }

    public void ensurePendingCode(PatternPending pending) {
        NormalizedPatternCodes codes = normalizeSegments(
                pending.getMainCategory(),
                pending.getSubCategory(),
                pending.getStyle(),
                pending.getRegion(),
                pending.getPeriod());
        validateNormalized(codes);
        applyNormalizedCodes(pending, codes);

        if (pending.getDateCode() == null || pending.getDateCode().isBlank()) {
            pending.setDateCode(generateDateCode(LocalDate.now()));
        }
        if (pending.getSequenceNumber() == null || pending.getSequenceNumber() <= 0) {
            pending.setSequenceNumber(nextSequenceNumber(pending.getDateCode()));
        }
        if (pending.getPatternCode() == null || pending.getPatternCode().isBlank()) {
            pending.setPatternCode(buildPatternCode(
                    pending.getMainCategory(),
                    pending.getSubCategory(),
                    pending.getStyle(),
                    pending.getRegion(),
                    pending.getPeriod(),
                    pending.getDateCode(),
                    pending.getSequenceNumber()));
        }
    }

    public void assignFormalCode(Pattern pattern) {
        NormalizedPatternCodes codes = normalizeSegments(
                pattern.getMainCategory(),
                pattern.getSubCategory(),
                pattern.getStyle(),
                pattern.getRegion(),
                pattern.getPeriod());
        validateNormalized(codes);
        applyNormalizedCodes(pattern, codes);

        String dateCode = generateDateCode(LocalDate.now());
        pattern.setDateCode(dateCode);
        pattern.setSequenceNumber(nextSequenceNumber(dateCode));
        pattern.setPatternCode(buildPatternCode(
                pattern.getMainCategory(),
                pattern.getSubCategory(),
                pattern.getStyle(),
                pattern.getRegion(),
                pattern.getPeriod(),
                pattern.getDateCode(),
                pattern.getSequenceNumber()));
    }

    private void validateNormalized(NormalizedPatternCodes codes) {
        String mainCategory = requiredCode("主类别代码", codes.mainCategory());
        String subCategory = requiredCode("子类别代码", codes.subCategory());
        String style = requiredCode("风格代码", codes.style());
        String region = requiredCode("地区代码", codes.region());
        String period = requiredCode("时期代码", codes.period());

        if (!PatternCodeEnum.MainCategory.isValid(mainCategory)) {
            throw new IllegalArgumentException("无效的主类别代码: " + mainCategory);
        }
        if (!PatternCodeEnum.isValidSubCategory(mainCategory, subCategory)) {
            if (hasSubCategory(mainCategory)) {
                throw new IllegalArgumentException("无效的子类别代码: " + subCategory + "，主类别: " + mainCategory);
            }
            throw new IllegalArgumentException("无效的风格代码(子类别位置): " + subCategory);
        }
        if (!PatternCodeEnum.Style.isValid(style)) {
            throw new IllegalArgumentException("无效的风格代码: " + style);
        }
        if (!PatternCodeEnum.Region.isValid(region)) {
            throw new IllegalArgumentException("无效的地区代码: " + region);
        }
        if (!PatternCodeEnum.Period.isValid(period)) {
            throw new IllegalArgumentException("无效的时期代码: " + period);
        }
    }

    private Integer findRecyclableSequenceNumber(String dateCode) {
        List<PatternPending> recyclable = pendingRepository.findRecyclableCodes(dateCode);
        if (recyclable.isEmpty()) {
            return null;
        }

        PatternPending recycled = recyclable.get(0);
        Integer sequenceNumber = recycled.getSequenceNumber();
        recycled.setPatternCode(null);
        recycled.setSequenceNumber(null);
        pendingRepository.save(recycled);
        return sequenceNumber;
    }

    private int nextSequenceNumber(String dateCode) {
        Integer maxPendingSequence = pendingRepository.findMaxActiveSequenceNumberByDateCode(dateCode);
        Integer maxPatternSequence = patternRepository.findMaxSequenceNumberByDateCode(dateCode);
        return Math.max(
                maxPendingSequence == null ? 0 : maxPendingSequence,
                maxPatternSequence == null ? 0 : maxPatternSequence) + 1;
    }

    private void applyNormalizedCodes(PatternPending pending, NormalizedPatternCodes codes) {
        pending.setMainCategory(codes.mainCategory());
        pending.setSubCategory(codes.subCategory());
        pending.setStyle(codes.style());
        pending.setRegion(codes.region());
        pending.setPeriod(codes.period());
    }

    private void applyNormalizedCodes(Pattern pattern, NormalizedPatternCodes codes) {
        pattern.setMainCategory(codes.mainCategory());
        pattern.setSubCategory(codes.subCategory());
        pattern.setStyle(codes.style());
        pattern.setRegion(codes.region());
        pattern.setPeriod(codes.period());
    }

    private String resolveSubCategoryName(String mainCategory, String subCategory) {
        if (!hasSubCategory(mainCategory)) {
            return PatternCodeEnum.Style.fromCode(subCategory).getName();
        }
        return switch (mainCategory) {
            case "AN" -> findName(PatternCodeEnum.AnimalSubCategory.values(), subCategory);
            case "PL" -> findName(PatternCodeEnum.PlantSubCategory.values(), subCategory);
            case "PE" -> findName(PatternCodeEnum.PersonSubCategory.values(), subCategory);
            default -> throw new IllegalArgumentException("无效的主类别代码: " + mainCategory);
        };
    }

    private <T extends Enum<T>> String findName(T[] values, String code) {
        for (T value : values) {
            if (value instanceof PatternCodeEnum.AnimalSubCategory animal && animal.getCode().equals(code)) {
                return animal.getName();
            }
            if (value instanceof PatternCodeEnum.PlantSubCategory plant && plant.getCode().equals(code)) {
                return plant.getName();
            }
            if (value instanceof PatternCodeEnum.PersonSubCategory person && person.getCode().equals(code)) {
                return person.getName();
            }
        }
        throw new IllegalArgumentException("无效的子类别代码: " + code);
    }

    private String requiredCode(String fieldName, String code) {
        String normalized = normalizeCode(code);
        if (normalized == null || normalized.isEmpty()) {
            return "OT"; // 默认返回“其他”
        }
        return normalized;
    }

    public record NormalizedPatternCodes(
            String mainCategory,
            String subCategory,
            String style,
            String region,
            String period) {
    }

    public record CodeLabels(
            String mainCategoryName,
            String subCategoryName,
            String styleName,
            String regionName,
            String periodName) {
    }
}
