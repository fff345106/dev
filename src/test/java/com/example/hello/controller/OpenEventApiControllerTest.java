package com.example.hello.controller;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.example.hello.dto.SpecialEventListItemResponse;
import com.example.hello.exception.GlobalExceptionHandler;
import com.example.hello.service.SpecialEventService;

@ExtendWith(MockitoExtension.class)
class OpenEventApiControllerTest {

    @Mock
    private SpecialEventService specialEventService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new OpenEventApiController(specialEventService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void listEvents_shouldReturnUnifiedResponseFields() throws Exception {
        SpecialEventListItemResponse item = new SpecialEventListItemResponse();
        item.setId(1L);
        item.setTitle("剪纸艺术非遗体验周");
        item.setDesc("邀请传承人现场展示并指导体验。");
        item.setImage("https://example.com/images/event-cover.jpg");
        item.setUrl("https://example.com/events/papercut");

        when(specialEventService.listEvents()).thenReturn(List.of(item));

        mockMvc.perform(get("/api/open/events"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[0].title").value("剪纸艺术非遗体验周"))
                .andExpect(jsonPath("$[0].desc").value("邀请传承人现场展示并指导体验。"))
                .andExpect(jsonPath("$[0].image").value("https://example.com/images/event-cover.jpg"))
                .andExpect(jsonPath("$[0].url").value("https://example.com/events/papercut"));

        verify(specialEventService).listEvents();
    }
}
