package com.example.hello.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.example.hello.entity.InvitationCode;
import com.example.hello.repository.InvitationCodeRepository;
import com.example.hello.repository.UserRepository;
import com.example.hello.service.AppInvitationCodeVerifier.VerificationResult;

@ExtendWith(MockitoExtension.class)
class InvitationCodeServiceTest {

    @Mock
    private InvitationCodeRepository invitationCodeRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private AppInvitationCodeVerifier appInvitationCodeVerifier;

    private InvitationCodeService invitationCodeService;

    @BeforeEach
    void setUp() {
        invitationCodeService = new InvitationCodeService(invitationCodeRepository, userRepository,
                appInvitationCodeVerifier);
    }

    @Test
    void consumeCode_shouldConsumeLocalInvitationCodeWithoutCallingAppVerifier() {
        InvitationCode invitationCode = new InvitationCode("123456", 1L);
        when(invitationCodeRepository.findByCodeForUpdate("123456")).thenReturn(Optional.of(invitationCode));
        when(invitationCodeRepository.save(invitationCode)).thenReturn(invitationCode);

        assertDoesNotThrow(() -> invitationCodeService.consumeCode("123456"));

        assertTrue(invitationCode.isUsed());
        assertNotNull(invitationCode.getUsedAt());
        verify(invitationCodeRepository).save(invitationCode);
        verifyNoInteractions(appInvitationCodeVerifier);
    }

    @Test
    void consumeCode_shouldKeepLocalUsedCodePriority() {
        InvitationCode invitationCode = new InvitationCode("123456", 1L);
        invitationCode.setUsed(true);
        when(invitationCodeRepository.findByCodeForUpdate("123456")).thenReturn(Optional.of(invitationCode));

        RuntimeException ex = assertThrows(RuntimeException.class, () -> invitationCodeService.consumeCode("123456"));

        assertEquals("邀请码已使用", ex.getMessage());
        verifyNoInteractions(appInvitationCodeVerifier);
    }

    @Test
    void consumeCode_shouldFallbackToAppVerifierWhenLocalCodeDoesNotExist() {
        when(invitationCodeRepository.findByCodeForUpdate("654321")).thenReturn(Optional.empty());
        when(appInvitationCodeVerifier.verifyAndConsume("654321")).thenReturn(VerificationResult.consumed());

        assertDoesNotThrow(() -> invitationCodeService.consumeCode("654321"));

        verify(appInvitationCodeVerifier).verifyAndConsume("654321");
    }

    @Test
    void consumeCode_shouldReportUsedWhenAppCodeHasBeenConsumed() {
        when(invitationCodeRepository.findByCodeForUpdate("654321")).thenReturn(Optional.empty());
        when(appInvitationCodeVerifier.verifyAndConsume("654321"))
                .thenReturn(VerificationResult.used("剪艺码已使用"));

        RuntimeException ex = assertThrows(RuntimeException.class, () -> invitationCodeService.consumeCode("654321"));

        assertEquals("剪艺码已使用", ex.getMessage());
    }

    @Test
    void consumeCode_shouldReportInvalidWhenAppCodeIsUnknown() {
        when(invitationCodeRepository.findByCodeForUpdate("654321")).thenReturn(Optional.empty());
        when(appInvitationCodeVerifier.verifyAndConsume("654321"))
                .thenReturn(VerificationResult.invalid("邀请码或剪艺码无效"));

        RuntimeException ex = assertThrows(RuntimeException.class, () -> invitationCodeService.consumeCode("654321"));

        assertEquals("邀请码或剪艺码无效", ex.getMessage());
    }
}
