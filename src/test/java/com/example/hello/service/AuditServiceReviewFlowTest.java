package com.example.hello.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import com.example.hello.dto.AuditRequest;
import com.example.hello.entity.Pattern;
import com.example.hello.entity.PatternPending;
import com.example.hello.entity.User;
import com.example.hello.enums.AuditStatus;
import com.example.hello.enums.UserRole;
import com.example.hello.repository.PatternPendingRepository;
import com.example.hello.repository.PatternRepository;
import com.example.hello.repository.UserRepository;

@ExtendWith(MockitoExtension.class)
class AuditServiceReviewFlowTest {

    @org.mockito.Mock
    private PatternPendingRepository pendingRepository;

    @org.mockito.Mock
    private PatternRepository patternRepository;

    @org.mockito.Mock
    private UserRepository userRepository;

    @org.mockito.Mock
    private ImageService imageService;

    @org.mockito.Mock
    private PatternHashService patternHashService;

    @org.mockito.Mock
    private BlockchainAnchorService blockchainAnchorService;

    @org.mockito.Mock
    private PatternCodeService patternCodeService;

    private AuditService auditService;

    @BeforeEach
    void setUp() {
        auditService = new AuditService(
                pendingRepository,
                patternRepository,
                userRepository,
                imageService,
                patternHashService,
                blockchainAnchorService,
                patternCodeService);

        when(pendingRepository.save(any(PatternPending.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void auditApprove_shouldPersistHashAndNotBlockWhenAnchorFails() throws Exception {
        Long pendingId = 86L;
        Long auditorId = 1L;
        PatternPending pending = buildPending(pendingId);

        when(userRepository.findById(auditorId)).thenReturn(Optional.of(buildAdmin(auditorId)));
        when(pendingRepository.findById(pendingId)).thenReturn(Optional.of(pending));
        when(patternRepository.save(any(Pattern.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(imageService.moveToFormal("https://img/temp/a.png", "AN-BD-TR-CN-QG-260319-001"))
                .thenReturn("https://img/AN-BD-TR-CN-QG-260319-001.png");
        when(patternHashService.computeSha256ByImageUrl("https://img/AN-BD-TR-CN-QG-260319-001.png"))
                .thenReturn("abc123hash");
        when(patternHashService.hashAlgorithm()).thenReturn("SHA-256");
        when(blockchainAnchorService.isEnabled()).thenReturn(true);
        when(blockchainAnchorService.anchor(any(), any(), any()))
                .thenThrow(new RuntimeException("缺少区块链配置: blockchain.to-address"));

        AuditRequest request = new AuditRequest();
        request.setApproved(true);

        Object result = auditService.audit(pendingId, request, auditorId);

        Pattern pattern = assertInstanceOf(Pattern.class, result);
        assertEquals("https://img/AN-BD-TR-CN-QG-260319-001.png", pattern.getImageUrl());
        assertEquals("abc123hash", pattern.getImageHash());
        assertEquals("SHA-256", pattern.getHashAlgorithm());
        assertEquals("FAILED", pattern.getChainStatus());

        verify(blockchainAnchorService).anchor(
                "AN-BD-TR-CN-QG-260319-001",
                "abc123hash",
                "https://img/AN-BD-TR-CN-QG-260319-001.png");
    }

    @Test
    void auditApprove_shouldSkipAnchorWhenBlockchainDisabled() throws Exception {
        Long pendingId = 860L;
        Long auditorId = 10L;
        PatternPending pending = buildPending(pendingId);

        when(userRepository.findById(auditorId)).thenReturn(Optional.of(buildAdmin(auditorId)));
        when(pendingRepository.findById(pendingId)).thenReturn(Optional.of(pending));
        when(patternRepository.save(any(Pattern.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(imageService.moveToFormal("https://img/temp/a.png", "AN-BD-TR-CN-QG-260319-001"))
                .thenReturn("https://img/AN-BD-TR-CN-QG-260319-001.png");
        when(patternHashService.computeSha256ByImageUrl("https://img/AN-BD-TR-CN-QG-260319-001.png"))
                .thenReturn("abc123hash");
        when(patternHashService.hashAlgorithm()).thenReturn("SHA-256");
        when(blockchainAnchorService.isEnabled()).thenReturn(false);

        AuditRequest request = new AuditRequest();
        request.setApproved(true);

        Object result = auditService.audit(pendingId, request, auditorId);

        Pattern pattern = assertInstanceOf(Pattern.class, result);
        assertEquals("abc123hash", pattern.getImageHash());
        assertEquals("SKIPPED", pattern.getChainStatus());
        verify(blockchainAnchorService, never()).anchor(any(), any(), any());
    }

    @Test
    void auditApprove_shouldThrowWhenHashComputeFails() throws Exception {
        Long pendingId = 87L;
        Long auditorId = 2L;
        PatternPending pending = buildPending(pendingId);

        when(userRepository.findById(auditorId)).thenReturn(Optional.of(buildAdmin(auditorId)));
        when(pendingRepository.findById(pendingId)).thenReturn(Optional.of(pending));
        when(imageService.moveToFormal(any(), any())).thenReturn("https://img/AN-BD-TR-CN-QG-260319-001.png");
        when(patternHashService.computeSha256ByImageUrl(any()))
                .thenThrow(new RuntimeException("计算图片哈希失败"));

        AuditRequest request = new AuditRequest();
        request.setApproved(true);

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> auditService.audit(pendingId, request, auditorId));

        assertTrue(ex.getMessage().contains("计算图片哈希失败"));
        verify(blockchainAnchorService, never()).anchor(any(), any(), any());
    }

    @Test
    void auditApprove_shouldThrowWhenImageMoveFails() throws Exception {
        Long pendingId = 88L;
        Long auditorId = 3L;
        PatternPending pending = buildPending(pendingId);

        when(userRepository.findById(auditorId)).thenReturn(Optional.of(buildAdmin(auditorId)));
        when(pendingRepository.findById(pendingId)).thenReturn(Optional.of(pending));
        when(imageService.moveToFormal(any(), any())).thenThrow(new IOException("The specified key does not exist"));

        AuditRequest request = new AuditRequest();
        request.setApproved(true);

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> auditService.audit(pendingId, request, auditorId));

        assertTrue(ex.getMessage().contains("移动正式图片失败"));
        verify(patternRepository, never()).save(any(Pattern.class));
        verify(blockchainAnchorService, never()).anchor(any(), any(), any());
    }

    private PatternPending buildPending(Long id) {
        PatternPending pending = new PatternPending();
        pending.setId(id);
        pending.setStatus(AuditStatus.PENDING);
        pending.setDescription("纹样描述");
        pending.setMainCategory("AN");
        pending.setSubCategory("BD");
        pending.setStyle("TR");
        pending.setRegion("CN");
        pending.setPeriod("QG");
        pending.setDateCode("260319");
        pending.setSequenceNumber(1);
        pending.setPatternCode("AN-BD-TR-CN-QG-260319-001");
        pending.setImageUrl("https://img/temp/a.png");
        pending.setSubmitter(buildSubmitter(100L));
        return pending;
    }

    private User buildAdmin(Long id) {
        User admin = new User();
        admin.setId(id);
        admin.setRole(UserRole.ADMIN);
        return admin;
    }

    private User buildSubmitter(Long id) {
        User user = new User();
        user.setId(id);
        user.setRole(UserRole.USER);
        return user;
    }
}
