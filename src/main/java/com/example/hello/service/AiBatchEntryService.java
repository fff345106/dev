package com.example.hello.service;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Executor;

import org.springframework.beans.factory.annotation.Qualifier;
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
import com.example.hello.dto.AiBatchTaskProgressResponse;
import com.example.hello.dto.AiBatchTaskStartResponse;
import com.example.hello.dto.PatternRequest;
import com.example.hello.entity.PatternPending;

@Service
public class AiBatchEntryService {
    private final AiPatternRecognitionService aiPatternRecognitionService;
    private final AuditService auditService;
    private static final String TASK_RUNNING = "RUNNING";
    private static final String TASK_COMPLETED = "COMPLETED";
    private static final String TASK_FAILED = "FAILED";

    private final AiProperties aiProperties;
    private final PatternCodeService patternCodeService;
    private final Executor aiBatchExecutor;
    private final ConcurrentMap<String, BatchSubmitTaskState> taskStates = new ConcurrentHashMap<>();

    public AiBatchEntryService(
            AiPatternRecognitionService aiPatternRecognitionService,
            AuditService auditService,
            AiProperties aiProperties,
            PatternCodeService patternCodeService,
            @Qualifier("aiBatchExecutor") Executor aiBatchExecutor) {
        this.aiPatternRecognitionService = aiPatternRecognitionService;
        this.auditService = auditService;
        this.aiProperties = aiProperties;
        this.patternCodeService = patternCodeService;
        this.aiBatchExecutor = aiBatchExecutor;
    }

    public AiBatchPreviewResponse preview(AiBatchPreviewRequest request) {
        String styleOverride = patternCodeService.normalizeCode(request.getStyle());
        String regionOverride = patternCodeService.normalizeCode(request.getRegion());
        String periodOverride = patternCodeService.normalizeCode(request.getPeriod());

        String defaultStyle = patternCodeService.normalizeCode(aiProperties.getDefaultStyle());
        String defaultRegion = patternCodeService.normalizeCode(aiProperties.getDefaultRegion());
        String defaultPeriod = patternCodeService.normalizeCode(aiProperties.getDefaultPeriod());

        List<AiBatchPreviewItem> items = new ArrayList<>();
        for (String imageUrl : request.getImageUrls()) {
            items.add(buildPreviewItem(
                    imageUrl,
                    request.getDescriptionPrefix(),
                    styleOverride,
                    regionOverride,
                    periodOverride,
                    defaultStyle,
                    defaultRegion,
                    defaultPeriod));
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
        AiBatchSubmitRequest requestCopy = copySubmitRequest(request);
        return submitInternalSync(requestCopy, submitterId);
    }

    public AiBatchTaskStartResponse startSubmitTask(AiBatchSubmitRequest request, Long submitterId) {
        AiBatchSubmitRequest requestCopy = copySubmitRequest(request);
        int total = requestCopy.getImageUrls() == null ? 0 : requestCopy.getImageUrls().size();
        String taskId = UUID.randomUUID().toString().replace("-", "");

        BatchSubmitTaskState state = new BatchSubmitTaskState(taskId, total);
        taskStates.put(taskId, state);

        CompletableFuture.runAsync(() -> executeSubmitTask(taskId, requestCopy, submitterId), aiBatchExecutor);

        AiBatchTaskStartResponse response = new AiBatchTaskStartResponse();
        response.setTaskId(taskId);
        response.setTotal(total);
        response.setStatus(TASK_RUNNING);
        return response;
    }

    public AiBatchTaskProgressResponse getSubmitTaskProgress(String taskId) {
        BatchSubmitTaskState state = taskStates.get(taskId);
        if (state == null) {
            throw new RuntimeException("任务不存在: " + taskId);
        }

        AiBatchTaskProgressResponse response = new AiBatchTaskProgressResponse();
        response.setTaskId(state.getTaskId());
        response.setStatus(state.getStatus());
        response.setTotal(state.getTotal());
        response.setProcessed(state.getProcessed());
        response.setSuccessCount(state.getSuccessCount());
        response.setFailCount(state.getFailCount());
        response.setProgressPercent(state.getProgressPercent());
        response.setCompleted(state.isCompleted());
        response.setError(state.getError());
        response.setStartedAtEpochMillis(state.getStartedAtEpochMillis());
        response.setFinishedAtEpochMillis(state.getFinishedAtEpochMillis());
        response.setItems(state.copyItems());
        return response;
    }

    private void executeSubmitTask(String taskId, AiBatchSubmitRequest request, Long submitterId) {
        BatchSubmitTaskState state = taskStates.get(taskId);
        if (state == null) {
            return;
        }
        try {
            List<String> imageUrls = request.getImageUrls() == null ? List.of() : request.getImageUrls();
            List<CompletableFuture<Void>> futures = new ArrayList<>();
            for (int index = 0; index < imageUrls.size(); index++) {
                final int currentIndex = index;
                final String imageUrl = imageUrls.get(index);
                CompletableFuture<Void> future = CompletableFuture
                        .supplyAsync(() -> processSingleItem(request, submitterId, imageUrl), aiBatchExecutor)
                        .thenAccept(itemResult -> state.onItemProcessed(currentIndex, itemResult));
                futures.add(future);
            }

            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
            state.markCompleted();
        } catch (Exception e) {
            state.markFailed(e.getMessage());
        }
    }

    private AiBatchSubmitResponse submitInternalSync(AiBatchSubmitRequest request, Long submitterId) {
        List<String> imageUrls = request.getImageUrls() == null ? List.of() : request.getImageUrls();
        List<AiBatchSubmitItemResult> items = new ArrayList<>();
        for (String imageUrl : imageUrls) {
            items.add(processSingleItem(request, submitterId, imageUrl));
        }
        return buildSubmitResponse(items);
    }

    private AiBatchSubmitItemResult processSingleItem(AiBatchSubmitRequest request, Long submitterId, String imageUrl) {
        String styleOverride = patternCodeService.normalizeCode(request.getStyle());
        String regionOverride = patternCodeService.normalizeCode(request.getRegion());
        String periodOverride = patternCodeService.normalizeCode(request.getPeriod());

        String defaultStyle = patternCodeService.normalizeCode(aiProperties.getDefaultStyle());
        String defaultRegion = patternCodeService.normalizeCode(aiProperties.getDefaultRegion());
        String defaultPeriod = patternCodeService.normalizeCode(aiProperties.getDefaultPeriod());

        AiBatchPreviewItem previewItem = buildPreviewItem(
                imageUrl,
                request.getDescriptionPrefix(),
                styleOverride,
                regionOverride,
                periodOverride,
                defaultStyle,
                defaultRegion,
                defaultPeriod);

        if (!previewItem.isValid()) {
            return buildPreviewFailure(previewItem);
        }
        return submitConfirmedItem(toConfirmItem(previewItem, request.getDescriptionPrefix()), submitterId);
    }

    private AiBatchSubmitRequest copySubmitRequest(AiBatchSubmitRequest request) {
        AiBatchSubmitRequest requestCopy = new AiBatchSubmitRequest();
        if (request.getImageUrls() != null) {
            requestCopy.setImageUrls(new ArrayList<>(request.getImageUrls()));
        }
        requestCopy.setStyle(request.getStyle());
        requestCopy.setRegion(request.getRegion());
        requestCopy.setPeriod(request.getPeriod());
        requestCopy.setDescriptionPrefix(request.getDescriptionPrefix());
        return requestCopy;
    }

    private AiBatchPreviewItem buildPreviewItem(
            String imageUrl,
            String descriptionPrefix,
            String styleOverride,
            String regionOverride,
            String periodOverride,
            String defaultStyle,
            String defaultRegion,
            String defaultPeriod) {
        AiBatchPreviewItem item = new AiBatchPreviewItem();
        item.setImageUrl(imageUrl);

        try {
            AiPatternRecognitionService.RecognitionResult recognitionResult =
                    aiPatternRecognitionService.recognizeByImageUrl(imageUrl);

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

    private static final class BatchSubmitTaskState {
        private final String taskId;
        private final long startedAtEpochMillis;
        private volatile Long finishedAtEpochMillis;
        private volatile String status;
        private final int total;
        private volatile int processed;
        private volatile int successCount;
        private volatile int failCount;
        private volatile String error;
        private final AiBatchSubmitItemResult[] items;

        private BatchSubmitTaskState(String taskId, int total) {
            this.taskId = taskId;
            this.total = total;
            this.status = TASK_RUNNING;
            this.startedAtEpochMillis = System.currentTimeMillis();
            this.items = new AiBatchSubmitItemResult[total];
        }

        private synchronized void onItemProcessed(int index, AiBatchSubmitItemResult itemResult) {
            if (index < 0 || index >= total || items[index] != null) {
                return;
            }
            items[index] = itemResult;
            processed++;
            if (itemResult.isSuccess()) {
                successCount++;
            } else {
                failCount++;
            }
        }

        private synchronized List<AiBatchSubmitItemResult> copyItems() {
            List<AiBatchSubmitItemResult> copiedItems = new ArrayList<>();
            for (AiBatchSubmitItemResult item : items) {
                if (item != null) {
                    copiedItems.add(item);
                }
            }
            return copiedItems;
        }

        private void markCompleted() {
            this.status = TASK_COMPLETED;
            this.finishedAtEpochMillis = System.currentTimeMillis();
        }

        private void markFailed(String error) {
            this.status = TASK_FAILED;
            this.error = error;
            this.finishedAtEpochMillis = System.currentTimeMillis();
        }

        private String getTaskId() {
            return taskId;
        }

        private String getStatus() {
            return status;
        }

        private int getTotal() {
            return total;
        }

        private int getProcessed() {
            return processed;
        }

        private int getSuccessCount() {
            return successCount;
        }

        private int getFailCount() {
            return failCount;
        }

        private int getProgressPercent() {
            if (total <= 0) {
                return 100;
            }
            return Math.min(100, (int) ((processed * 100L) / total));
        }

        private boolean isCompleted() {
            return TASK_COMPLETED.equals(status) || TASK_FAILED.equals(status);
        }

        private String getError() {
            return error;
        }

        private long getStartedAtEpochMillis() {
            return startedAtEpochMillis;
        }

        private Long getFinishedAtEpochMillis() {
            return finishedAtEpochMillis;
        }
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
