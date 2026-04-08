package com.example.hello.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.example.hello.config.AiProperties;
import com.example.hello.dto.AiBatchConfirmItem;
import com.example.hello.dto.AiBatchConfirmRequest;
import com.example.hello.dto.AiBatchPreviewRequest;
import com.example.hello.dto.AiBatchPreviewResponse;
import com.example.hello.dto.AiBatchSubmitRequest;
import com.example.hello.dto.AiBatchSubmitResponse;
import com.example.hello.dto.AiBatchTaskProgressResponse;
import com.example.hello.dto.AiBatchTaskStartResponse;
import com.example.hello.dto.PatternRequest;
import com.example.hello.entity.PatternPending;
import com.example.hello.repository.PatternPendingRepository;
import com.example.hello.repository.PatternRepository;

@ExtendWith(MockitoExtension.class)
class AiBatchEntryServiceTest {

    @Mock
    private AiPatternRecognitionService aiPatternRecognitionService;

    @Mock
    private AuditService auditService;

    @Mock
    private AiProperties aiProperties;

    @Mock
    private PatternPendingRepository patternPendingRepository;

    @Mock
    private PatternRepository patternRepository;

    private AiBatchEntryService aiBatchEntryService;
    private ExecutorService batchExecutor;

    @BeforeEach
    void setUp() {
        PatternCodeService patternCodeService = new PatternCodeService(patternPendingRepository, patternRepository);
        batchExecutor = Executors.newFixedThreadPool(4);
        aiBatchEntryService = new AiBatchEntryService(
                aiPatternRecognitionService,
                auditService,
                aiProperties,
                patternCodeService,
                batchExecutor);
    }

    @AfterEach
    void tearDown() {
        batchExecutor.shutdownNow();
    }

    @Test
    void preview_shouldReturnValidatedItemsAndDescriptionWithPatternName() {
        when(aiProperties.getDefaultStyle()).thenReturn("OT");
        when(aiProperties.getDefaultRegion()).thenReturn("OT");
        when(aiProperties.getDefaultPeriod()).thenReturn("OT");

        AiBatchPreviewRequest request = new AiBatchPreviewRequest();
        request.setImageUrls(List.of("https://img/u1.png"));
        request.setDescriptionPrefix("AI识别预览");

        when(aiPatternRecognitionService.recognizeByImageUrl("https://img/u1.png"))
                .thenReturn(new AiPatternRecognitionService.RecognitionResult(
                        "凤凰纹",
                        "AN",
                        "MY",
                        "TR",
                        "CN",
                        "QG",
                        List.of("phoenix", "traditional"),
                        List.of()));

        AiBatchPreviewResponse response = aiBatchEntryService.preview(request);

        assertEquals(1, response.getTotal());
        assertEquals(1, response.getValidCount());
        assertEquals(0, response.getInvalidCount());
        assertTrue(response.getItems().get(0).isValid());
        assertEquals("凤凰纹", response.getItems().get(0).getPatternName());
        assertEquals("凤凰纹", response.getItems().get(0).getDescription());
        assertEquals("动物", response.getItems().get(0).getMainCategoryName());
        assertEquals("神话动物", response.getItems().get(0).getSubCategoryName());
    }

    @Test
    void preview_shouldRecognizeEachItemIndependentlyEvenForSameUrl() {
        AiBatchPreviewRequest request = new AiBatchPreviewRequest();
        request.setImageUrls(List.of("https://img/u1.png", "https://img/u1.png"));

        when(aiPatternRecognitionService.recognizeByImageUrl("https://img/u1.png"))
                .thenReturn(new AiPatternRecognitionService.RecognitionResult(
                        "花卉纹",
                        "PL",
                        "FL",
                        "FO",
                        "OT",
                        "OT",
                        List.of("flower"),
                        List.of()));

        AiBatchPreviewResponse response = aiBatchEntryService.preview(request);

        assertEquals(2, response.getTotal());
        assertEquals(2, response.getValidCount());
        verify(aiPatternRecognitionService, times(2)).recognizeByImageUrl("https://img/u1.png");
    }

    @Test
    void confirm_shouldSubmitUsingConfirmedCodesAndPatternNameInDescription() {
        AiBatchConfirmItem confirmItem = new AiBatchConfirmItem();
        confirmItem.setImageUrl("https://img/u3.png");
        confirmItem.setPatternName("仕女纹");
        confirmItem.setDescriptionPrefix("AI确认");
        confirmItem.setMainCategory("pe");
        confirmItem.setSubCategory("fe");
        confirmItem.setStyle("mo");
        confirmItem.setRegion("cn");
        confirmItem.setPeriod("xd");

        AiBatchConfirmRequest request = new AiBatchConfirmRequest();
        request.setItems(List.of(confirmItem));

        PatternPending pending = new PatternPending();
        pending.setId(3L);
        pending.setPatternCode("PE-FE-MO-CN-XD-260319-001");
        pending.setMainCategory("PE");
        pending.setSubCategory("FE");
        pending.setStyle("MO");
        pending.setRegion("CN");
        pending.setPeriod("XD");
        when(auditService.submit(any(PatternRequest.class), eq(7L))).thenReturn(pending);

        AiBatchSubmitResponse response = aiBatchEntryService.confirm(request, 7L);

        assertEquals(1, response.getSuccessCount());
        assertEquals(0, response.getFailCount());
        assertEquals("仕女纹", response.getItems().get(0).getPatternName());
        assertEquals("仕女纹", response.getItems().get(0).getDescription());

        ArgumentCaptor<PatternRequest> captor = ArgumentCaptor.forClass(PatternRequest.class);
        verify(auditService).submit(captor.capture(), eq(7L));
        PatternRequest submitted = captor.getValue();
        assertEquals("PE", submitted.getMainCategory());
        assertEquals("FE", submitted.getSubCategory());
        assertEquals("MO", submitted.getStyle());
        assertEquals("CN", submitted.getRegion());
        assertEquals("XD", submitted.getPeriod());
        assertEquals("仕女纹", submitted.getDescription());
    }

    @Test
    void confirm_shouldFallbackToDescriptionWhenPatternNameMissing() {
        AiBatchConfirmItem confirmItem = new AiBatchConfirmItem();
        confirmItem.setImageUrl("https://img/u4.png");
        confirmItem.setDescription("凤鸟纹");
        confirmItem.setMainCategory("an");
        confirmItem.setSubCategory("bd");
        confirmItem.setStyle("tr");
        confirmItem.setRegion("cn");
        confirmItem.setPeriod("mg");

        AiBatchConfirmRequest request = new AiBatchConfirmRequest();
        request.setItems(List.of(confirmItem));

        PatternPending pending = new PatternPending();
        pending.setId(4L);
        pending.setPatternCode("AN-BD-TR-CN-MG-260319-002");
        pending.setMainCategory("AN");
        pending.setSubCategory("BD");
        pending.setStyle("TR");
        pending.setRegion("CN");
        pending.setPeriod("MG");
        when(auditService.submit(any(PatternRequest.class), eq(9L))).thenReturn(pending);

        AiBatchSubmitResponse response = aiBatchEntryService.confirm(request, 9L);

        assertEquals(1, response.getSuccessCount());
        assertEquals("凤鸟纹", response.getItems().get(0).getPatternName());
        assertEquals("凤鸟纹", response.getItems().get(0).getDescription());

        ArgumentCaptor<PatternRequest> captor = ArgumentCaptor.forClass(PatternRequest.class);
        verify(auditService).submit(captor.capture(), eq(9L));
        assertEquals("凤鸟纹", captor.getValue().getDescription());
    }

    @Test
    void submit_shouldApplyOverridesAndKeepFailuresPerItem() {
        when(aiProperties.getDefaultStyle()).thenReturn("OT");
        when(aiProperties.getDefaultRegion()).thenReturn("OT");
        when(aiProperties.getDefaultPeriod()).thenReturn("OT");

        AiBatchSubmitRequest request = new AiBatchSubmitRequest();
        request.setImageUrls(List.of("https://img/u1.png", "https://img/u2.png"));
        request.setDescriptionPrefix("AI批量录入");
        request.setStyle("DE");
        request.setRegion("GD");
        request.setPeriod("MG");

        when(aiPatternRecognitionService.recognizeByImageUrl("https://img/u1.png"))
                .thenReturn(new AiPatternRecognitionService.RecognitionResult(
                        "鸟纹",
                        "AN",
                        "BD",
                        "TR",
                        "CN",
                        "QG",
                        List.of("bird"),
                        List.of()));
        when(aiPatternRecognitionService.recognizeByImageUrl("https://img/u2.png"))
                .thenThrow(new RuntimeException("AI识别失败"));

        PatternPending pending = new PatternPending();
        pending.setId(101L);
        pending.setPatternCode("AN-BD-DE-GD-MG-260319-001");
        pending.setMainCategory("AN");
        pending.setSubCategory("BD");
        pending.setStyle("DE");
        pending.setRegion("GD");
        pending.setPeriod("MG");
        when(auditService.submit(any(PatternRequest.class), eq(88L))).thenReturn(pending);

        AiBatchSubmitResponse response = aiBatchEntryService.submit(request, 88L);

        assertEquals(2, response.getTotal());
        assertEquals(1, response.getSuccessCount());
        assertEquals(1, response.getFailCount());
        assertTrue(response.getItems().get(0).isSuccess());
        assertFalse(response.getItems().get(1).isSuccess());
        assertEquals("AI识别失败", response.getItems().get(1).getError());
        assertEquals("鸟纹", response.getItems().get(0).getPatternName());
        assertEquals("DE", response.getItems().get(0).getStyle());
        assertEquals("GD", response.getItems().get(0).getRegion());
        assertEquals("MG", response.getItems().get(0).getPeriod());

        ArgumentCaptor<PatternRequest> captor = ArgumentCaptor.forClass(PatternRequest.class);
        verify(auditService).submit(captor.capture(), eq(88L));
        PatternRequest submitted = captor.getValue();
        assertEquals("DE", submitted.getStyle());
        assertEquals("GD", submitted.getRegion());
        assertEquals("MG", submitted.getPeriod());
        assertEquals("鸟纹", submitted.getDescription());
    }

    @Test
    void startSubmitTask_shouldExposeProgressUntilCompleted() throws Exception {
        when(aiProperties.getDefaultStyle()).thenReturn("OT");
        when(aiProperties.getDefaultRegion()).thenReturn("OT");
        when(aiProperties.getDefaultPeriod()).thenReturn("OT");

        AiBatchSubmitRequest request = new AiBatchSubmitRequest();
        request.setImageUrls(List.of("https://img/u1.png", "https://img/u2.png"));

        when(aiPatternRecognitionService.recognizeByImageUrl("https://img/u1.png"))
                .thenReturn(new AiPatternRecognitionService.RecognitionResult(
                        "鸟纹",
                        "AN",
                        "BD",
                        "TR",
                        "CN",
                        "QG",
                        List.of("bird"),
                        List.of()));
        when(aiPatternRecognitionService.recognizeByImageUrl("https://img/u2.png"))
                .thenThrow(new RuntimeException("AI识别失败"));

        PatternPending pending = new PatternPending();
        pending.setId(201L);
        pending.setPatternCode("AN-BD-TR-CN-QG-260319-001");
        pending.setMainCategory("AN");
        pending.setSubCategory("BD");
        pending.setStyle("TR");
        pending.setRegion("CN");
        pending.setPeriod("QG");
        when(auditService.submit(any(PatternRequest.class), eq(100L))).thenReturn(pending);

        AiBatchTaskStartResponse startResponse = aiBatchEntryService.startSubmitTask(request, 100L);
        assertNotNull(startResponse.getTaskId());
        assertEquals(2, startResponse.getTotal());

        AiBatchTaskProgressResponse progress = null;
        for (int i = 0; i < 80; i++) {
            progress = aiBatchEntryService.getSubmitTaskProgress(startResponse.getTaskId());
            if (progress.isCompleted()) {
                break;
            }
            Thread.sleep(50L);
        }

        assertNotNull(progress);
        assertTrue(progress.isCompleted());
        assertEquals("COMPLETED", progress.getStatus());
        assertEquals(2, progress.getTotal());
        assertEquals(2, progress.getProcessed());
        assertEquals(1, progress.getSuccessCount());
        assertEquals(1, progress.getFailCount());
        assertEquals(100, progress.getProgressPercent());
        assertEquals(2, progress.getItems().size());
    }

    @Test
    void startSubmitTask_shouldKeepInputOrderWhenCompletedOutOfOrder() throws Exception {
        when(aiProperties.getDefaultStyle()).thenReturn("OT");
        when(aiProperties.getDefaultRegion()).thenReturn("OT");
        when(aiProperties.getDefaultPeriod()).thenReturn("OT");

        AiBatchSubmitRequest request = new AiBatchSubmitRequest();
        request.setImageUrls(List.of("https://img/slow.png", "https://img/fast.png"));

        when(aiPatternRecognitionService.recognizeByImageUrl("https://img/slow.png"))
                .thenAnswer(invocation -> {
                    Thread.sleep(250L);
                    return new AiPatternRecognitionService.RecognitionResult(
                            "慢图纹",
                            "AN",
                            "BD",
                            "TR",
                            "CN",
                            "QG",
                            List.of("slow"),
                            List.of());
                });
        when(aiPatternRecognitionService.recognizeByImageUrl("https://img/fast.png"))
                .thenAnswer(invocation -> {
                    Thread.sleep(20L);
                    return new AiPatternRecognitionService.RecognitionResult(
                            "快图纹",
                            "PL",
                            "FL",
                            "TR",
                            "CN",
                            "QG",
                            List.of("fast"),
                            List.of());
                });

        when(auditService.submit(any(PatternRequest.class), eq(101L))).thenAnswer(invocation -> {
            PatternRequest req = invocation.getArgument(0);
            PatternPending pending = new PatternPending();
            pending.setId("慢图纹".equals(req.getDescription()) ? 301L : 302L);
            pending.setPatternCode("AN-BD-TR-CN-QG-260319-001");
            pending.setMainCategory(req.getMainCategory());
            pending.setSubCategory(req.getSubCategory());
            pending.setStyle(req.getStyle());
            pending.setRegion(req.getRegion());
            pending.setPeriod(req.getPeriod());
            return pending;
        });

        AiBatchTaskStartResponse startResponse = aiBatchEntryService.startSubmitTask(request, 101L);

        AiBatchTaskProgressResponse progress = null;
        for (int i = 0; i < 80; i++) {
            progress = aiBatchEntryService.getSubmitTaskProgress(startResponse.getTaskId());
            if (progress.isCompleted()) {
                break;
            }
            Thread.sleep(50L);
        }

        assertNotNull(progress);
        assertTrue(progress.isCompleted());
        assertEquals(2, progress.getItems().size());
        assertEquals("慢图纹", progress.getItems().get(0).getPatternName());
        assertEquals("快图纹", progress.getItems().get(1).getPatternName());
    }

    @Test
    void startSubmitTask_shouldExposePartialProgressBeforeCompletion() throws Exception {
        when(aiProperties.getDefaultStyle()).thenReturn("OT");
        when(aiProperties.getDefaultRegion()).thenReturn("OT");
        when(aiProperties.getDefaultPeriod()).thenReturn("OT");

        AiBatchSubmitRequest request = new AiBatchSubmitRequest();
        request.setImageUrls(List.of("https://img/slow2.png", "https://img/fast2.png"));

        when(aiPatternRecognitionService.recognizeByImageUrl("https://img/slow2.png"))
                .thenAnswer(invocation -> {
                    Thread.sleep(300L);
                    return new AiPatternRecognitionService.RecognitionResult(
                            "慢图二",
                            "AN",
                            "BD",
                            "TR",
                            "CN",
                            "QG",
                            List.of("slow2"),
                            List.of());
                });
        when(aiPatternRecognitionService.recognizeByImageUrl("https://img/fast2.png"))
                .thenReturn(new AiPatternRecognitionService.RecognitionResult(
                        "快图二",
                        "PL",
                        "FL",
                        "TR",
                        "CN",
                        "QG",
                        List.of("fast2"),
                        List.of()));

        when(auditService.submit(any(PatternRequest.class), eq(102L))).thenAnswer(invocation -> {
            PatternRequest req = invocation.getArgument(0);
            PatternPending pending = new PatternPending();
            pending.setId("慢图二".equals(req.getDescription()) ? 401L : 402L);
            pending.setPatternCode("AN-BD-TR-CN-QG-260319-001");
            pending.setMainCategory(req.getMainCategory());
            pending.setSubCategory(req.getSubCategory());
            pending.setStyle(req.getStyle());
            pending.setRegion(req.getRegion());
            pending.setPeriod(req.getPeriod());
            return pending;
        });

        AiBatchTaskStartResponse startResponse = aiBatchEntryService.startSubmitTask(request, 102L);

        boolean sawPartial = false;
        for (int i = 0; i < 20; i++) {
            AiBatchTaskProgressResponse progress = aiBatchEntryService.getSubmitTaskProgress(startResponse.getTaskId());
            if (!progress.isCompleted() && progress.getProcessed() > 0 && progress.getProcessed() < progress.getTotal()) {
                sawPartial = true;
                break;
            }
            Thread.sleep(30L);
        }

        assertTrue(sawPartial);
    }

    @Test
    void getSubmitTaskProgress_shouldThrowWhenTaskNotFound() {
        RuntimeException exception = assertThrows(RuntimeException.class,
                () -> aiBatchEntryService.getSubmitTaskProgress("not-exists"));
        assertTrue(exception.getMessage().contains("任务不存在"));
    }
}
