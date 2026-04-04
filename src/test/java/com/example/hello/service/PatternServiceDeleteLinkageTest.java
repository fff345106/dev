package com.example.hello.service;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.example.hello.entity.Pattern;
import com.example.hello.enums.UserRole;
import com.example.hello.repository.PatternPendingRepository;
import com.example.hello.repository.PatternRepository;

@ExtendWith(MockitoExtension.class)
class PatternServiceDeleteLinkageTest {

    @Mock
    private PatternRepository patternRepository;

    @Mock
    private PatternPendingRepository patternPendingRepository;

    @Mock
    private ImageService imageService;

    @Mock
    private PatternCodeService patternCodeService;

    private PatternService patternService;

    @BeforeEach
    void setUp() {
        patternService = new PatternService(patternRepository, patternPendingRepository, imageService, patternCodeService);
    }

    @Test
    void deleteByAdmin_shouldDeletePendingByPatternCode_thenDeleteFormal() throws Exception {
        Pattern pattern = buildPattern(1L, "PC-001", "formal/a.jpg");
        when(patternRepository.findById(1L)).thenReturn(Optional.of(pattern));

        patternService.delete(1L, UserRole.ADMIN);

        verify(patternPendingRepository).deleteByPatternCode("PC-001");
        verify(imageService).delete("formal/a.jpg");
        verify(patternRepository).deleteById(1L);
    }

    @Test
    void deleteByAdmin_whenPendingNotExists_shouldStillDeleteFormal() throws Exception {
        Pattern pattern = buildPattern(2L, "PC-002", "formal/b.jpg");
        when(patternRepository.findById(2L)).thenReturn(Optional.of(pattern));
        when(patternPendingRepository.deleteByPatternCode("PC-002")).thenReturn(0L);

        patternService.delete(2L, UserRole.SUPER_ADMIN);

        verify(patternPendingRepository).deleteByPatternCode("PC-002");
        verify(imageService).delete("formal/b.jpg");
        verify(patternRepository).deleteById(2L);
    }

    @Test
    void deleteByUser_shouldThrowAndNotDeletePendingOrFormal() {
        Pattern pattern = buildPattern(3L, "PC-003", "formal/c.jpg");
        when(patternRepository.findById(3L)).thenReturn(Optional.of(pattern));

        assertThrows(RuntimeException.class, () -> patternService.delete(3L, UserRole.USER));

        verifyNoInteractions(patternPendingRepository, imageService);
        verify(patternRepository, never()).deleteById(anyLong());
    }

    @Test
    void batchDelete_shouldContinueOnSingleFailure_andKeepLinkageForOthers() throws IOException {
        Pattern p1 = buildPattern(11L, "PC-011", "formal/11.jpg");
        Pattern p3 = buildPattern(13L, "PC-013", "formal/13.jpg");

        when(patternRepository.findById(11L)).thenReturn(Optional.of(p1));
        when(patternRepository.findById(12L)).thenReturn(Optional.empty());
        when(patternRepository.findById(13L)).thenReturn(Optional.of(p3));

        patternService.batchDelete(List.of(11L, 12L, 13L), UserRole.ADMIN);

        verify(patternPendingRepository).deleteByPatternCode("PC-011");
        verify(patternPendingRepository).deleteByPatternCode("PC-013");
        verify(patternRepository).deleteById(11L);
        verify(patternRepository).deleteById(13L);
        verify(patternRepository, never()).deleteById(12L);
    }

    private Pattern buildPattern(Long id, String patternCode, String imageUrl) {
        Pattern pattern = new Pattern();
        pattern.setId(id);
        pattern.setPatternCode(patternCode);
        pattern.setImageUrl(imageUrl);
        return pattern;
    }
}
