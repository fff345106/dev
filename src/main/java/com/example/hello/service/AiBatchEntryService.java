package com.example.hello.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.example.hello.config.AiProperties;
import com.example.hello.dto.AiBatchConfirmItem;
import com.example.hello.dto.AiBatchConfirmRequest;
import com.example.hello.dto.AiBatchPreviewItem;
import com.example.hello.dto.AiBatchPreviewRequest;
import com.example.hello.dto.AiBatchPreviewResponse;
import com.example.hello.dto.AiBatchSubmitItemResult;
import com.example.hello.dto.AiBatchSubmitRequest;
import com.example.hello.dto.AiBatchSubmitResponse;
import com.example.hello.dto.PatternRequest;
import com.example.hello.entity.PatternPending;

@Service
public class AiBatchEntryService {
    private final AiPatternRecognitionService aiPatternRecognitionService;
    private final AuditService auditService;
    private final AiProperties aiProperties;
    private final PatternCodeService patternCodeService;

    public AiBatchEntryService(
            AiPatternRecognitionService aiPatternRecognitionService,
            AuditService auditService,
            AiProperties aiProperties,
            PatternCodeService patternCodeService) {
        this.aiPatternRecognitionService = aiPatternRecognitionService;
        this.auditService = auditService;
        this.aiProperties = aiProperties;
        this.patternCodeService = patternCodeService;
    }

    public AiBatchPreviewResponse preview(AiBatchPreviewRequest request) {
        String styleOverride = patternCodeService.normalizeCode(request.getStyle());
        String regionOverride = patternCodeService.normalizeCode(request.getRegion());
        String periodOverride = patternCodeService.normalizeCode(request.getPeriod());

        String defaultStyle = patternCodeService.normalizeCode(aiProperties.getDefaultStyle());
        String defaultRegion = patternCodeService.normalizeCode(aiProperties.getDefaultRegion());
        String defaultPeriod = patternCodeService.normalizeCode(aiProperties.getDefaultPeriod());

        List<AiBatchPreviewItem> items = new ArrayList<>();
        Map<String, AiPatternRecognitionService.RecognitionResult> cache = new HashMap<>();
        for (String imageUrl : request.getImageUrls()) {
            items.add(buildPreviewItem(
                    imageUrl,
                    request.getDescriptionPrefix(),
                    styleOverride,
                    regionOverride,
                    periodOverride,
                    defaultStyle,
                    defaultRegion,
                    defaultPeriod,
                    cache));
        }

        int validCount = (int) items.stream().filter(AiBatchPreviewItem::isValid).count();

        AiBatchPreviewResponse response = new AiBatchPreviewResponse();
        response.setTotal(items.size());
        response.setValidCount(validCount);
        response.setInvalidCount(items.size() - validCount);
        response.setItems(items);
        return response;
    }

    public AiBatchSubmitResponse confirm(AiBatchConfirmRequest request, Long submitterId) {
        List<AiBatchSubmitItemResult> items = new ArrayList<>();
        for (AiBatchConfirmItem confirmItem : request.getItems()) {
            items.add(submitConfirmedItem(confirmItem, submitterId));
        }
        return buildSubmitResponse(items);
    }

    public AiBatchSubmitResponse submit(AiBatchSubmitRequest request, Long submitterId) {
        AiBatchPreviewResponse previewResponse = preview(toPreviewRequest(request));
        List<AiBatchSubmitItemResult> items = new ArrayList<>();
        for (AiBatchPreviewItem previewItem : previewResponse.getItems()) {
            if (!previewItem.isValid()) {
                items.add(buildPreviewFailure(previewItem));
                continue;
            }
            items.add(submitConfirmedItem(toConfirmItem(previewItem, request.getDescriptionPrefix()), submitterId));
        }
        return buildSubmitResponse(items);
    }

    private AiBatchPreviewItem buildPreviewItem(
            String imageUrl,
            String descriptionPrefix,
            String styleOverride,
            String regionOverride,
            String periodOverride,
            String defaultStyle,
            String defaultRegion,
            String defaultPeriod,
            Map<String, AiPatternRecognitionService.RecognitionResult> cache) {
        AiBatchPreviewItem item = new AiBatchPreviewItem();
        item.setImageUrl(imageUrl);

        try {
            AiPatternRecognitionService.RecognitionResult recognitionResult = cache.computeIfAbsent(
                    imageUrl,
                    aiPatternRecognitionService::recognizeByImageUrl);

            PatternCodeService.NormalizedPatternCodes codes = patternCodeService.normalizeSegments(
                    recognitionResult.getMainCategory(),
                    recognitionResult.getSubCategory(),
                    chooseCode(styleOverride, recognitionResult.getStyle(), defaultStyle),
                    chooseCode(regionOverride, recognitionResult.getRegion(), defaultRegion),
                    chooseCode(periodOverride, recognitionResult.getPeriod(), defaultPeriod));

            String patternName = normalizePatternName(recognitionResult.getPatternName());
            item.setPatternName(patternName);
            item.setDescription(buildDescription(descriptionPrefix, patternName, imageUrl));
            item.setKeywords(recognitionResult.getKeywords());
            item.setMainCategory(codes.mainCategory());
            item.setSubCategory(codes.subCategory());
            item.setStyle(codes.style());
            item.setRegion(codes.region());
            item.setPeriod(codes.period());

            List<String> validationErrors = new ArrayList<>();
            try {
                patternCodeService.validateSegments(
                        codes.mainCategory(),
                        codes.subCategory(),
                        codes.style(),
                        codes.region(),
                        codes.period());
            } catch (IllegalArgumentException e) {
                validationErrors.add(e.getMessage());
            }

            if (validationErrors.isEmpty()) {
                PatternCodeService.CodeLabels labels = patternCodeService.resolveLabels(
                        codes.mainCategory(),
                        codes.subCategory(),
                        codes.style(),
                        codes.region(),
                        codes.period());
                item.setMainCategoryName(labels.mainCategoryName());
                item.setSubCategoryName(labels.subCategoryName());
                item.setStyleName(labels.styleName());
                item.setRegionName(labels.regionName());
                item.setPeriodName(labels.periodName());
                item.setValid(true);
            } else {
                item.setValid(false);
                item.setValidationErrors(validationErrors);
            }
        } catch (Exception e) {
            item.setValid(false);
            item.setValidationErrors(List.of(e.getMessage()));
            item.setError(e.getMessage());
        }
        return item;
    }

    private AiBatchSubmitItemResult submitConfirmedItem(AiBatchConfirmItem confirmItem, Long submitterId) {
        AiBatchSubmitItemResult itemResult = new AiBatchSubmitItemResult();
        itemResult.setImageUrl(confirmItem.getImageUrl());

        try {
            PatternCodeService.NormalizedPatternCodes codes = patternCodeService.normalizeSegments(
                    confirmItem.getMainCategory(),
                    confirmItem.getSubCategory(),
                    confirmItem.getStyle(),
                    confirmItem.getRegion(),
                    confirmItem.getPeriod());
            patternCodeService.validateSegments(
                    codes.mainCategory(),
                    codes.subCategory(),
                    codes.style(),
                    codes.region(),
                    codes.period());

            String patternName = resolvePatternName(confirmItem);
            String description = buildDescription(confirmItem.getDescriptionPrefix(), patternName, confirmItem.getImageUrl());

            PatternRequest patternRequest = new PatternRequest();
            patternRequest.setDescription(description);
            patternRequest.setMainCategory(codes.mainCategory());
            patternRequest.setSubCategory(codes.subCategory());
            patternRequest.setStyle(codes.style());
            patternRequest.setRegion(codes.region());
            patternRequest.setPeriod(codes.period());
            patternRequest.setImageUrl(confirmItem.getImageUrl());

            PatternPending pending = auditService.submit(patternRequest, submitterId);
            itemResult.setSuccess(true);
            itemResult.setPendingId(pending.getId());
            itemResult.setPatternCode(pending.getPatternCode());
            itemResult.setPatternName(patternName);
            itemResult.setDescription(description);
            itemResult.setMainCategory(pending.getMainCategory());
            itemResult.setSubCategory(pending.getSubCategory());
            itemResult.setStyle(pending.getStyle());
            itemResult.setRegion(pending.getRegion());
            itemResult.setPeriod(pending.getPeriod());
        } catch (Exception e) {
            itemResult.setSuccess(false);
            itemResult.setPatternName(normalizePatternName(confirmItem.getPatternName()));
            itemResult.setDescription(buildDescription(
                    confirmItem.getDescriptionPrefix(),
                    normalizePatternName(confirmItem.getPatternName()),
                    confirmItem.getImageUrl()));
            itemResult.setMainCategory(patternCodeService.normalizeCode(confirmItem.getMainCategory()));
            itemResult.setSubCategory(patternCodeService.normalizeCode(confirmItem.getSubCategory()));
            itemResult.setStyle(patternCodeService.normalizeCode(confirmItem.getStyle()));
            itemResult.setRegion(patternCodeService.normalizeCode(confirmItem.getRegion()));
            itemResult.setPeriod(patternCodeService.normalizeCode(confirmItem.getPeriod()));
            itemResult.setValidationErrors(List.of(e.getMessage()));
            itemResult.setError(e.getMessage());
        }
        return itemResult;
    }

    private AiBatchSubmitItemResult buildPreviewFailure(AiBatchPreviewItem previewItem) {
        AiBatchSubmitItemResult itemResult = new AiBatchSubmitItemResult();
        itemResult.setImageUrl(previewItem.getImageUrl());
        itemResult.setSuccess(false);
        itemResult.setPatternName(previewItem.getPatternName());
        itemResult.setDescription(previewItem.getDescription());
        itemResult.setMainCategory(previewItem.getMainCategory());
        itemResult.setSubCategory(previewItem.getSubCategory());
        itemResult.setStyle(previewItem.getStyle());
        itemResult.setRegion(previewItem.getRegion());
        itemResult.setPeriod(previewItem.getPeriod());
        itemResult.setValidationErrors(previewItem.getValidationErrors());
        itemResult.setError(firstError(previewItem.getValidationErrors(), previewItem.getError()));
        return itemResult;
    }

    private AiBatchPreviewRequest toPreviewRequest(AiBatchSubmitRequest request) {
        AiBatchPreviewRequest previewRequest = new AiBatchPreviewRequest();
        previewRequest.setImageUrls(request.getImageUrls());
        previewRequest.setStyle(request.getStyle());
        previewRequest.setRegion(request.getRegion());
        previewRequest.setPeriod(request.getPeriod());
        previewRequest.setDescriptionPrefix(request.getDescriptionPrefix());
        return previewRequest;
    }

    private AiBatchConfirmItem toConfirmItem(AiBatchPreviewItem previewItem, String descriptionPrefix) {
        AiBatchConfirmItem confirmItem = new AiBatchConfirmItem();
        confirmItem.setImageUrl(previewItem.getImageUrl());
        confirmItem.setPatternName(previewItem.getPatternName());
        confirmItem.setDescription(previewItem.getDescription());
        confirmItem.setDescriptionPrefix(descriptionPrefix);
        confirmItem.setMainCategory(previewItem.getMainCategory());
        confirmItem.setSubCategory(previewItem.getSubCategory());
        confirmItem.setStyle(previewItem.getStyle());
        confirmItem.setRegion(previewItem.getRegion());
        confirmItem.setPeriod(previewItem.getPeriod());
        return confirmItem;
    }

    private AiBatchSubmitResponse buildSubmitResponse(List<AiBatchSubmitItemResult> items) {
        int successCount = (int) items.stream().filter(AiBatchSubmitItemResult::isSuccess).count();

        AiBatchSubmitResponse response = new AiBatchSubmitResponse();
        response.setTotal(items.size());
        response.setSuccessCount(successCount);
        response.setFailCount(items.size() - successCount);
        response.setItems(items);
        return response;
    }

    private String chooseCode(String override, String recognized, String fallback) {
        String normalizedRecognized = patternCodeService.normalizeCode(recognized);
        if (override != null) {
            return override;
        }
        if (normalizedRecognized != null) {
            return normalizedRecognized;
        }
        return fallback;
    }

    private String normalizePatternName(String value) {
        if (value == null || value.isBlank()) {
            return "其他纹样";
        }
        return value.trim();
    }

    private String resolvePatternName(AiBatchConfirmItem confirmItem) {
        String value = confirmItem.getPatternName();
        if (value == null || value.isBlank()) {
            value = confirmItem.getDescription();
        }
        return normalizePatternName(value);
    }

    private String buildDescription(String prefix, String patternName, String imageUrl) {
        return normalizePatternName(patternName);
    }

    private String firstError(List<String> validationErrors, String fallback) {
        if (validationErrors != null && !validationErrors.isEmpty()) {
            return validationErrors.get(0);
        }
        return fallback;
    }
}
