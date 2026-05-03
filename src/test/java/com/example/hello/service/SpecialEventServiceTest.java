package com.example.hello.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.example.hello.dto.SpecialEventCreateRequest;
import com.example.hello.dto.SpecialEventListItemResponse;
import com.example.hello.entity.SpecialEvent;
import com.example.hello.repository.SpecialEventRepository;

@ExtendWith(MockitoExtension.class)
class SpecialEventServiceTest {

    @Mock
    private SpecialEventRepository specialEventRepository;

    @Mock
    private RedisCacheService redisCacheService;

    private SpecialEventService specialEventService;

    @BeforeEach
    void setUp() {
        specialEventService = new SpecialEventService(specialEventRepository, redisCacheService);
    }

    @Test
    void create_shouldTrimInputAndPersist_thenReturnUnifiedFields() {
        SpecialEventCreateRequest request = new SpecialEventCreateRequest();
        request.setTitle("  剪纸艺术非遗体验周  ");
        request.setDesc(" 现场体验传统剪纸技艺 ");
        request.setImage("   ");
        request.setUrl("  https://example.com/events/papercut  ");

        when(specialEventRepository.save(any(SpecialEvent.class))).thenAnswer(invocation -> {
            SpecialEvent event = invocation.getArgument(0);
            event.setId(1L);
            return event;
        });

        SpecialEventListItemResponse response = specialEventService.create(request);

        ArgumentCaptor<SpecialEvent> captor = ArgumentCaptor.forClass(SpecialEvent.class);
        verify(specialEventRepository).save(captor.capture());
        SpecialEvent saved = captor.getValue();

        assertEquals("剪纸艺术非遗体验周", saved.getTitle());
        assertEquals("现场体验传统剪纸技艺", saved.getDescription());
        assertNull(saved.getImageUrl());
        assertEquals("https://example.com/events/papercut", saved.getUrl());

        assertEquals(1L, response.getId());
        assertEquals("剪纸艺术非遗体验周", response.getTitle());
        assertEquals("现场体验传统剪纸技艺", response.getDesc());
        assertNull(response.getImage());
        assertEquals("https://example.com/events/papercut", response.getUrl());
    }

    @Test
    void listEvents_shouldApplyFallbackValues() {
        SpecialEvent event = new SpecialEvent();
        event.setId(2L);
        event.setTitle("  ");
        event.setDescription(null);
        event.setImageUrl("  ");
        event.setUrl("  https://example.com/events/fallback  ");

        when(specialEventRepository.findAllByOrderByCreatedAtDesc()).thenReturn(List.of(event));

        List<SpecialEventListItemResponse> list = specialEventService.listEvents();

        assertEquals(1, list.size());
        SpecialEventListItemResponse item = list.get(0);
        assertEquals(2L, item.getId());
        assertEquals("未命名活动", item.getTitle());
        assertEquals("暂无活动简介。", item.getDesc());
        assertNull(item.getImage());
        assertEquals("https://example.com/events/fallback", item.getUrl());
        verify(specialEventRepository).findAllByOrderByCreatedAtDesc();
    }

    @Test
    void create_shouldAllowNullDesc() {
        SpecialEventCreateRequest request = new SpecialEventCreateRequest();
        request.setTitle("活动只填标题");
        request.setDesc(null);
        request.setImage(null);
        request.setUrl("https://example.com/events/no-desc");

        when(specialEventRepository.save(any(SpecialEvent.class))).thenAnswer(invocation -> {
            SpecialEvent event = invocation.getArgument(0);
            event.setId(9L);
            return event;
        });

        SpecialEventListItemResponse response = specialEventService.create(request);

        ArgumentCaptor<SpecialEvent> captor = ArgumentCaptor.forClass(SpecialEvent.class);
        verify(specialEventRepository).save(captor.capture());
        SpecialEvent saved = captor.getValue();

        assertNull(saved.getDescription());
        assertEquals("https://example.com/events/no-desc", saved.getUrl());
        assertEquals("暂无活动简介。", response.getDesc());
        assertEquals("https://example.com/events/no-desc", response.getUrl());
    }

    @Test
    void delete_shouldCallRepositoryDeleteById() {
        specialEventService.delete(3L);

        verify(specialEventRepository).deleteById(3L);
    }
}
