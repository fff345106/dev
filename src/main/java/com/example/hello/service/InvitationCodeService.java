package com.example.hello.service;

import java.security.SecureRandom;
import java.time.LocalDateTime;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.hello.entity.InvitationCode;
import com.example.hello.entity.User;
import com.example.hello.enums.UserRole;
import com.example.hello.repository.InvitationCodeRepository;
import com.example.hello.repository.UserRepository;
import com.example.hello.service.AppInvitationCodeVerifier.VerificationResult;
import com.example.hello.service.AppInvitationCodeVerifier.VerificationStatus;

@Service
public class InvitationCodeService {

    public enum CodeSource {
        LOCAL,
        APP
    }

    public record CodeConsumeResult(CodeSource source) {
        public static CodeConsumeResult local() {
            return new CodeConsumeResult(CodeSource.LOCAL);
        }

        public static CodeConsumeResult app() {
            return new CodeConsumeResult(CodeSource.APP);
        }
    }

    private static final int CODE_LENGTH = 6;
    private static final String INVALID_CODE_MESSAGE = "邀请码或剪艺码无效";
    private static final String APP_CODE_USED_MESSAGE = "剪艺码已使用";
    private static final String APP_CODE_UNAVAILABLE_MESSAGE = "剪艺码校验服务暂不可用";

    private final InvitationCodeRepository invitationCodeRepository;
    private final UserRepository userRepository;
    private final AppInvitationCodeVerifier appInvitationCodeVerifier;
    private final SecureRandom secureRandom = new SecureRandom();

    public InvitationCodeService(InvitationCodeRepository invitationCodeRepository, UserRepository userRepository,
            AppInvitationCodeVerifier appInvitationCodeVerifier) {
        this.invitationCodeRepository = invitationCodeRepository;
        this.userRepository = userRepository;
        this.appInvitationCodeVerifier = appInvitationCodeVerifier;
    }

    @Transactional
    public String generateCode(Long operatorUserId) {
        User operator = userRepository.findById(operatorUserId)
                .orElseThrow(() -> new RuntimeException("操作者不存在"));

        if (operator.getRole() != UserRole.SUPER_ADMIN) {
            throw new RuntimeException("无权限生成邀请码");
        }

        String code = nextUniqueCode();
        invitationCodeRepository.save(new InvitationCode(code, operatorUserId));
        return code;
    }

    @Transactional
    public CodeConsumeResult consumeCode(String code) {
        InvitationCode invitationCode = invitationCodeRepository.findByCodeForUpdate(code)
                .orElse(null);
        if (invitationCode != null) {
            consumeLocalCode(invitationCode);
            return CodeConsumeResult.local();
        }

        VerificationResult verificationResult = appInvitationCodeVerifier.verifyAndConsume(code);
        handleAppVerificationResult(verificationResult);
        return CodeConsumeResult.app();
    }

    private void consumeLocalCode(InvitationCode invitationCode) {
        if (invitationCode.isUsed()) {
            throw new RuntimeException("邀请码已使用");
        }

        invitationCode.setUsed(true);
        invitationCode.setUsedAt(LocalDateTime.now());
        invitationCodeRepository.save(invitationCode);
    }

    private void handleAppVerificationResult(VerificationResult verificationResult) {
        if (verificationResult == null || verificationResult.status() == null) {
            throw new RuntimeException(INVALID_CODE_MESSAGE);
        }

        switch (verificationResult.status()) {
            case CONSUMED:
                return;
            case USED:
                throw new RuntimeException(defaultMessage(verificationResult.message(), APP_CODE_USED_MESSAGE));
            case UNAVAILABLE:
                throw new RuntimeException(defaultMessage(verificationResult.message(), APP_CODE_UNAVAILABLE_MESSAGE));
            case INVALID:
            default:
                throw new RuntimeException(defaultMessage(verificationResult.message(), INVALID_CODE_MESSAGE));
        }
    }

    private String defaultMessage(String message, String defaultMessage) {
        return hasText(message) ? message : defaultMessage;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private String nextUniqueCode() {
        String code;
        do {
            code = randomCode();
        } while (invitationCodeRepository.existsByCode(code));
        return code;
    }

    private String randomCode() {
        StringBuilder builder = new StringBuilder(CODE_LENGTH);
        for (int i = 0; i < CODE_LENGTH; i++) {
            builder.append(secureRandom.nextInt(10));
        }
        return builder.toString();
    }
}
