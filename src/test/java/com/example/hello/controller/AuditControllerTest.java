package com.example.hello.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import com.example.hello.dto.AiBatchConfirmItem;
import com.example.hello.dto.AiBatchConfirmRequest;
import com.example.hello.dto.AiBatchPreviewItem;
import com.example.hello.dto.AiBatchPreviewRequest;
import com.example.hello.dto.AiBatchPreviewResponse;
import com.example.hello.dto.AiBatchSubmitItemResult;
import com.example.hello.dto.AiBatchSubmitRequest;
import com.example.hello.dto.AiBatchSubmitResponse;
import com.example.hello.exception.GlobalExceptionHandler;
import com.example.hello.service.AiBatchEntryService;
import com.example.hello.service.AuditService;
import com.example.hello.util.JwtUtil;
import com.fasterxml.jackson.databind.ObjectMapper;

@ExtendWith(MockitoExtension.class)
class AuditControllerTest {

    @Mock
    private AuditService auditService;

    @Mock
    private AiBatchEntryService aiBatchEntryService;

    @Mock
    private JwtUtil jwtUtil;

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        mockMvc = MockMvcBuilders.standaloneSetup(new AuditController(auditService, aiBatchEntryService, jwtUtil))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setValidator(validator)
                .build();
    }

    @Test
    void aiBatchPreview_shouldReturnPreviewResponseWithoutJwt() throws Exception {
        AiBatchPreviewItem item = new AiBatchPreviewItem();
        item.setImageUrl("https://img/u1.png");
        item.setPatternName("凤凰纹");
        item.setValid(true);

        AiBatchPreviewResponse response = new AiBatchPreviewResponse();
        response.setTotal(1);
        response.setValidCount(1);
        response.setInvalidCount(0);
        response.setItems(List.of(item));
        when(aiBatchEntryService.preview(any(AiBatchPreviewRequest.class))).thenReturn(response);

        AiBatchPreviewRequest request = new AiBatchPreviewRequest();
        request.setImageUrls(List.of("https://img/u1.png"));
        request.setDescriptionPrefix("AI预览");

        mockMvc.perform(post("/api/audit/ai-batch-preview")
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.validCount").value(1))
                .andExpect(jsonPath("$.items[0].patternName").value("凤凰纹"));

        verify(aiBatchEntryService).preview(any(AiBatchPreviewRequest.class));
        verifyNoInteractions(jwtUtil);
    }

    @Test
    void aiBatchPreview_shouldReturn400WhenImageUrlsEmpty() throws Exception {
        mockMvc.perform(post("/api/audit/ai-batch-preview")
                        .contentType(APPLICATION_JSON)
                        .content("{\"imageUrls\":[]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("图片URL列表不能为空"));

        verifyNoInteractions(aiBatchEntryService, jwtUtil);
    }

    @Test
    void aiBatchConfirm_shouldExtractUserIdAndDelegateToService() throws Exception {
        when(jwtUtil.extractUserId("test-token")).thenReturn(7L);

        AiBatchSubmitItemResult item = new AiBatchSubmitItemResult();
        item.setSuccess(true);
        item.setPatternCode("PE-FE-MO-CN-XD-260319-001");

        AiBatchSubmitResponse response = new AiBatchSubmitResponse();
        response.setTotal(1);
        response.setSuccessCount(1);
        response.setFailCount(0);
        response.setItems(List.of(item));
        when(aiBatchEntryService.confirm(any(AiBatchConfirmRequest.class), eq(7L))).thenReturn(response);

        AiBatchConfirmItem confirmItem = new AiBatchConfirmItem();
        confirmItem.setImageUrl("https://img/u1.png");
        confirmItem.setPatternName("仕女纹");
        confirmItem.setDescriptionPrefix("AI确认");
        confirmItem.setMainCategory("PE");
        confirmItem.setSubCategory("FE");
        confirmItem.setStyle("MO");
        confirmItem.setRegion("CN");
        confirmItem.setPeriod("XD");
        AiBatchConfirmRequest request = new AiBatchConfirmRequest();
        request.setItems(List.of(confirmItem));

        mockMvc.perform(post("/api/audit/ai-batch-confirm")
                        .header("Authorization", "Bearer test-token")
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.successCount").value(1));

        ArgumentCaptor<AiBatchConfirmRequest> captor = ArgumentCaptor.forClass(AiBatchConfirmRequest.class);
        verify(aiBatchEntryService).confirm(captor.capture(), eq(7L));
        verify(jwtUtil).extractUserId("test-token");
    }

    @Test
    void aiBatchSubmit_shouldKeepLegacyEndpointAndDelegateToService() throws Exception {
        when(jwtUtil.extractUserId("legacy-token")).thenReturn(9L);

        AiBatchSubmitResponse response = new AiBatchSubmitResponse();
        response.setTotal(1);
        response.setSuccessCount(1);
        response.setFailCount(0);
        response.setItems(List.of(new AiBatchSubmitItemResult()));
        when(aiBatchEntryService.submit(any(AiBatchSubmitRequest.class), eq(9L))).thenReturn(response);

        AiBatchSubmitRequest request = new AiBatchSubmitRequest();
        request.setImageUrls(List.of("https://img/u9.png"));
        request.setDescriptionPrefix("兼容入口");

        mockMvc.perform(post("/api/audit/ai-batch-submit")
                        .header("Authorization", "Bearer legacy-token")
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.successCount").value(1));

        verify(aiBatchEntryService).submit(any(AiBatchSubmitRequest.class), eq(9L));
        verify(jwtUtil).extractUserId("legacy-token");
    }
}
