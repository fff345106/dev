package com.example.hello.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

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

    @BeforeEach
    void setUp() {
        PatternCodeService patternCodeService = new PatternCodeService(patternPendingRepository, patternRepository);
        aiBatchEntryService = new AiBatchEntryService(
                aiPatternRecognitionService,
                auditService,
                aiProperties,
                patternCodeService);
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
    void preview_shouldCacheRecognitionResultForSameUrl() {
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
        verify(aiPatternRecognitionService, times(1)).recognizeByImageUrl("https://img/u1.png");
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
}
