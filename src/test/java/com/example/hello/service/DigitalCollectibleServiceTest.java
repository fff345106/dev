package com.example.hello.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.example.hello.dto.DigitalCollectibleCreateRequest;
import com.example.hello.entity.DigitalCollectible;
import com.example.hello.entity.User;
import com.example.hello.enums.CollectibleEntryMode;
import com.example.hello.enums.ImageSourceType;
import com.example.hello.repository.DigitalCollectibleRepository;
import com.example.hello.repository.PatternRepository;
import com.example.hello.repository.UserRepository;

@ExtendWith(MockitoExtension.class)
class DigitalCollectibleServiceTest {

    @Mock
    private DigitalCollectibleRepository digitalCollectibleRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private PatternRepository patternRepository;

    @Mock
    private ImageService imageService;

    private DigitalCollectibleService digitalCollectibleService;

    @BeforeEach
    void setUp() {
        digitalCollectibleService = new DigitalCollectibleService(
                digitalCollectibleRepository,
                userRepository,
                patternRepository,
                imageService);
    }

    @Test
    void create_libraryMode_shouldRequireOnlyPatternImageAndPersistSourcePatternId() {
        Long userId = 1L;
        Long sourcePatternId = 100L;
        User user = buildUser(userId);
        DigitalCollectibleCreateRequest request = new DigitalCollectibleCreateRequest();
        request.setEntryMode(CollectibleEntryMode.LIBRARY);
        request.setPatternImageUrl("https://example.com/bucket/library/abc.jpg");
        request.setSourcePatternId(sourcePatternId);

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(patternRepository.existsById(sourcePatternId)).thenReturn(true);
        when(digitalCollectibleRepository.save(any(DigitalCollectible.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        DigitalCollectible saved = digitalCollectibleService.create(request, userId);

        assertEquals(CollectibleEntryMode.LIBRARY.name(), saved.getEntryMode());
        assertEquals(ImageSourceType.LIBRARY.name(), saved.getPatternImageSourceType());
        assertEquals(sourcePatternId, saved.getSourcePatternId());
        assertNull(saved.getStoryText());
        assertNull(saved.getStoryFileUrl());
        verify(imageService).validateLibraryUrl("https://example.com/bucket/library/abc.jpg");
    }

    @Test
    void create_uploadMode_shouldRejectWhenStoryFieldsMissing() {
        Long userId = 1L;
        User user = buildUser(userId);
        DigitalCollectibleCreateRequest request = new DigitalCollectibleCreateRequest();
        request.setEntryMode(CollectibleEntryMode.UPLOAD);
        request.setPatternImageUrl("https://example.com/bucket/temp/xyz.jpg");

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(imageService.resolveImageSourceType(null, "https://example.com/bucket/temp/xyz.jpg"))
                .thenReturn(ImageSourceType.TEMP_UPLOAD);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> digitalCollectibleService.create(request, userId));

        assertEquals("UPLOAD模式下藏品故事文本不能为空", ex.getMessage());
        verify(digitalCollectibleRepository, never()).save(any(DigitalCollectible.class));
    }

    @Test
    void create_uploadMode_shouldPersistWhenRequiredFieldsProvided() {
        Long userId = 1L;
        User user = buildUser(userId);
        DigitalCollectibleCreateRequest request = new DigitalCollectibleCreateRequest();
        request.setEntryMode(CollectibleEntryMode.UPLOAD);
        request.setPatternImageUrl("https://example.com/bucket/temp/xyz.jpg");
        request.setStoryText("故事文本");
        request.setStoryFileUrl("https://example.com/bucket/temp/story.pdf");

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(imageService.resolveImageSourceType(null, "https://example.com/bucket/temp/xyz.jpg"))
                .thenReturn(ImageSourceType.TEMP_UPLOAD);
        when(digitalCollectibleRepository.save(any(DigitalCollectible.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        DigitalCollectible saved = digitalCollectibleService.create(request, userId);

        assertEquals(CollectibleEntryMode.UPLOAD.name(), saved.getEntryMode());
        assertEquals(ImageSourceType.TEMP_UPLOAD.name(), saved.getPatternImageSourceType());
        assertEquals("故事文本", saved.getStoryText());
        assertEquals("https://example.com/bucket/temp/story.pdf", saved.getStoryFileUrl());
        assertNull(saved.getSourcePatternId());

        ArgumentCaptor<DigitalCollectible> captor = ArgumentCaptor.forClass(DigitalCollectible.class);
        verify(digitalCollectibleRepository).save(captor.capture());
        assertEquals(userId, captor.getValue().getCreatedBy().getId());
    }

    private User buildUser(Long id) {
        User user = new User("tester", "pwd");
        user.setId(id);
        return user;
    }
}
