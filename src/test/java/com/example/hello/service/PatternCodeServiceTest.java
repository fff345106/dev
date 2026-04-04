package com.example.hello.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.example.hello.entity.PatternPending;
import com.example.hello.repository.PatternPendingRepository;
import com.example.hello.repository.PatternRepository;

@ExtendWith(MockitoExtension.class)
class PatternCodeServiceTest {

    @Mock
    private PatternPendingRepository patternPendingRepository;

    @Mock
    private PatternRepository patternRepository;

    private PatternCodeService patternCodeService;

    @BeforeEach
    void setUp() {
        patternCodeService = new PatternCodeService(patternPendingRepository, patternRepository);
    }

    @Test
    void validateSegments_shouldSupportMainCategoriesWithoutChildrenUsingStyleInSubCategorySlot() {
        assertEquals("TR", patternCodeService.normalizeCode(" tr "));
        assertDoesNotThrow(() -> patternCodeService.validateSegments("la", "tr", "mo", "cn", "xd"));
        assertEquals("风景", patternCodeService.resolveLabels("LA", "TR", "MO", "CN", "XD").mainCategoryName());
        assertEquals("传统", patternCodeService.resolveLabels("LA", "TR", "MO", "CN", "XD").subCategoryName());
        verifyNoInteractions(patternPendingRepository, patternRepository);
    }

    @Test
    void validateSegments_shouldThrowWhenSubCategoryDoesNotMatchMainCategory() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> patternCodeService.validateSegments("AN", "FL", "TR", "CN", "QG"));
        assertEquals("无效的子类别代码: FL，主类别: AN", ex.getMessage());
    }

    @Test
    void assignPendingCode_shouldReuseRejectedSequenceNumberFirst() {
        when(patternPendingRepository.save(any(PatternPending.class))).thenAnswer(invocation -> invocation.getArgument(0));

        String dateCode = patternCodeService.generateDateCode(LocalDate.now());
        PatternPending recycled = new PatternPending();
        recycled.setDateCode(dateCode);
        recycled.setSequenceNumber(2);
        recycled.setPatternCode("AN-BD-TR-CN-QG-" + dateCode + "-002");

        when(patternPendingRepository.findRecyclableCodes(dateCode)).thenReturn(List.of(recycled));

        PatternPending pending = new PatternPending();
        pending.setMainCategory("AN");
        pending.setSubCategory("BD");
        pending.setStyle("TR");
        pending.setRegion("CN");
        pending.setPeriod("QG");

        patternCodeService.assignPendingCode(pending);

        assertEquals(dateCode, pending.getDateCode());
        assertEquals(2, pending.getSequenceNumber());
        assertEquals("AN-BD-TR-CN-QG-" + dateCode + "-002", pending.getPatternCode());
        assertNull(recycled.getPatternCode());
        assertNull(recycled.getSequenceNumber());
        verify(patternPendingRepository).save(recycled);
    }

    @Test
    void assignPendingCode_shouldUseMaxSequenceAcrossPendingAndFormalTables() {
        String dateCode = patternCodeService.generateDateCode(LocalDate.now());
        when(patternPendingRepository.findRecyclableCodes(dateCode)).thenReturn(List.of());
        when(patternPendingRepository.findMaxActiveSequenceNumberByDateCode(dateCode)).thenReturn(3);
        when(patternRepository.findMaxSequenceNumberByDateCode(dateCode)).thenReturn(5);

        PatternPending pending = new PatternPending();
        pending.setMainCategory("AN");
        pending.setSubCategory("BD");
        pending.setStyle("TR");
        pending.setRegion("CN");
        pending.setPeriod("QG");

        patternCodeService.assignPendingCode(pending);

        assertEquals(6, pending.getSequenceNumber());
        assertEquals("AN-BD-TR-CN-QG-" + dateCode + "-006", pending.getPatternCode());
    }

    @Test
    void ensurePendingCode_shouldBackfillMissingPatternCodeWithoutAllocatingNewSequence() {
        PatternPending pending = new PatternPending();
        pending.setMainCategory("AN");
        pending.setSubCategory("BD");
        pending.setStyle("TR");
        pending.setRegion("CN");
        pending.setPeriod("QG");
        pending.setDateCode("260319");
        pending.setSequenceNumber(8);

        patternCodeService.ensurePendingCode(pending);

        assertEquals("AN-BD-TR-CN-QG-260319-008", pending.getPatternCode());
        verifyNoInteractions(patternPendingRepository, patternRepository);
    }
}
