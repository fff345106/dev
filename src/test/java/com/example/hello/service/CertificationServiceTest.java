package com.example.hello.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.example.hello.dto.EnterpriseAuthRequest;
import com.example.hello.dto.MasterAuthRequest;
import com.example.hello.dto.RealNameAuthRequest;
import com.example.hello.entity.User;
import com.example.hello.entity.UserCertification;
import com.example.hello.enums.CertificationStatus;
import com.example.hello.enums.CertificationType;
import com.example.hello.enums.UserRole;
import com.example.hello.repository.UserCertificationRepository;
import com.example.hello.repository.UserRepository;

@ExtendWith(MockitoExtension.class)
class CertificationServiceTest {

    @Mock
    private UserCertificationRepository certificationRepository;

    @Mock
    private UserRepository userRepository;

    private CertificationService certificationService;

    private User user;
    private User auditor;

    @BeforeEach
    void setUp() {
        certificationService = new CertificationService(certificationRepository, userRepository);
        user = new User("testuser", "pass", UserRole.REGULAR_USER);
        user.setId(1L);
        auditor = new User("auditor", "pass", UserRole.ADMIN);
        auditor.setId(2L);
    }

    @Test
    void submitRealNameAuth_createsNewCertification() {
        RealNameAuthRequest request = new RealNameAuthRequest();
        request.setRealName("张三");
        request.setIdCardNumber("110101199001011234");
        request.setIdCardFrontUrl("https://example.com/front.jpg");
        request.setIdCardBackUrl("https://example.com/back.jpg");

        when(certificationRepository.findByUserAndCertificationType(user, CertificationType.REAL_NAME))
                .thenReturn(Optional.empty());
        when(certificationRepository.save(any(UserCertification.class)))
                .thenAnswer(invocation -> {
                    UserCertification cert = invocation.getArgument(0);
                    cert.setId(1L);
                    return cert;
                });

        UserCertification result = certificationService.submitRealNameAuth(user, request);

        assertNotNull(result);
        assertEquals(CertificationType.REAL_NAME, result.getCertificationType());
        assertEquals(CertificationStatus.PENDING, result.getStatus());
        assertEquals("张三", result.getRealName());
        assertEquals("110101199001011234", result.getIdCardNumber());
        assertEquals("https://example.com/front.jpg", result.getIdCardFrontUrl());
        assertEquals("https://example.com/back.jpg", result.getIdCardBackUrl());
        verify(certificationRepository).save(any(UserCertification.class));
        // 验证用户的认证状态被同步为 PENDING
        verify(userRepository).save(user);
        assertEquals(CertificationStatus.PENDING, user.getCertificationStatus());
    }

    @Test
    void submitRealNameAuth_shouldUpdateExistingRejectedCert() {
        RealNameAuthRequest request = new RealNameAuthRequest();
        request.setRealName("张三");
        request.setIdCardNumber("110101199001011234");
        request.setIdCardFrontUrl("https://example.com/front.jpg");
        request.setIdCardBackUrl("https://example.com/back.jpg");

        // 已有一条被拒绝的认证记录
        UserCertification existingCert = new UserCertification(user, CertificationType.REAL_NAME);
        existingCert.setId(10L);
        existingCert.setStatus(CertificationStatus.REJECTED);
        existingCert.setRejectReason("材料不清晰");
        existingCert.setAuditorId(2L);

        when(certificationRepository.findByUserAndCertificationType(user, CertificationType.REAL_NAME))
                .thenReturn(Optional.of(existingCert));
        when(certificationRepository.save(any(UserCertification.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        UserCertification result = certificationService.submitRealNameAuth(user, request);

        assertNotNull(result);
        assertEquals(10L, result.getId(), "应更新已有记录而非创建新记录");
        assertEquals(CertificationStatus.PENDING, result.getStatus(), "重新提交后状态应为 PENDING");
        assertEquals("张三", result.getRealName());
        assertEquals("110101199001011234", result.getIdCardNumber());
        // 被拒绝的记录重新提交时应清除审核信息
        assertEquals(null, result.getRejectReason(), "应清除拒绝原因");
        assertEquals(null, result.getAuditorId(), "应清除审核人");
        assertEquals(null, result.getAuditTime(), "应清除审核时间");
        verify(certificationRepository).save(existingCert);
    }

    @Test
    void submitRealNameAuth_shouldUpdateExistingPendingCert() {
        RealNameAuthRequest request = new RealNameAuthRequest();
        request.setRealName("李四");
        request.setIdCardNumber("110101199002021234");
        request.setIdCardFrontUrl("https://example.com/front2.jpg");
        request.setIdCardBackUrl("https://example.com/back2.jpg");

        // 已有一条待审核的认证记录
        UserCertification existingCert = new UserCertification(user, CertificationType.REAL_NAME);
        existingCert.setId(11L);
        existingCert.setStatus(CertificationStatus.PENDING);
        existingCert.setRealName("旧名字");

        when(certificationRepository.findByUserAndCertificationType(user, CertificationType.REAL_NAME))
                .thenReturn(Optional.of(existingCert));
        when(certificationRepository.save(any(UserCertification.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        UserCertification result = certificationService.submitRealNameAuth(user, request);

        assertNotNull(result);
        assertEquals(11L, result.getId(), "应更新已有记录");
        assertEquals("李四", result.getRealName(), "应更新为新名字");
        verify(certificationRepository).save(existingCert);
    }

    @Test
    void submitEnterpriseAuth_shouldUpdateExistingRejectedCert() {
        EnterpriseAuthRequest request = new EnterpriseAuthRequest();
        request.setBusinessLicenseUrl("https://example.com/license2.jpg");
        request.setLegalRepresentativeName("王五");
        request.setIsLegalRepresentative(true);

        UserCertification existingCert = new UserCertification(user, CertificationType.ENTERPRISE);
        existingCert.setId(20L);
        existingCert.setStatus(CertificationStatus.REJECTED);

        when(certificationRepository.findByUserAndCertificationType(user, CertificationType.ENTERPRISE))
                .thenReturn(Optional.of(existingCert));
        when(certificationRepository.save(any(UserCertification.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        UserCertification result = certificationService.submitEnterpriseAuth(user, request);

        assertEquals(20L, result.getId());
        assertEquals(CertificationStatus.PENDING, result.getStatus());
        assertEquals("https://example.com/license2.jpg", result.getBusinessLicenseUrl());
        verify(certificationRepository).save(existingCert);
    }

    @Test
    void submitMasterAuth_shouldUpdateExistingRejectedCert() {
        MasterAuthRequest request = new MasterAuthRequest();
        request.setCertificationUrl("https://example.com/cert2.jpg");

        UserCertification existingCert = new UserCertification(user, CertificationType.MASTER);
        existingCert.setId(30L);
        existingCert.setStatus(CertificationStatus.REJECTED);

        when(certificationRepository.findByUserAndCertificationType(user, CertificationType.MASTER))
                .thenReturn(Optional.of(existingCert));
        when(certificationRepository.save(any(UserCertification.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        UserCertification result = certificationService.submitMasterAuth(user, request);

        assertEquals(30L, result.getId());
        assertEquals(CertificationStatus.PENDING, result.getStatus());
        assertEquals("https://example.com/cert2.jpg", result.getCertificationUrl());
        verify(certificationRepository).save(existingCert);
    }

    @Test
    void submitRealNameAuth_throwsWhenAlreadyApproved() {
        RealNameAuthRequest request = new RealNameAuthRequest();
        request.setRealName("张三");
        request.setIdCardNumber("110101199001011234");
        request.setIdCardFrontUrl("https://example.com/front.jpg");
        request.setIdCardBackUrl("https://example.com/back.jpg");

        UserCertification existingCert = new UserCertification(user, CertificationType.REAL_NAME);
        existingCert.setStatus(CertificationStatus.APPROVED);

        when(certificationRepository.findByUserAndCertificationType(user, CertificationType.REAL_NAME))
                .thenReturn(Optional.of(existingCert));

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> certificationService.submitRealNameAuth(user, request));
        assertEquals("该用户已通过实名认证，无需重复提交", ex.getMessage());
        verify(certificationRepository, never()).save(any());
    }

    @Test
    void submitEnterpriseAuth_createsNewCertification() {
        EnterpriseAuthRequest request = new EnterpriseAuthRequest();
        request.setBusinessLicenseUrl("https://example.com/license.jpg");
        request.setLegalRepresentativeName("李四");
        request.setIsLegalRepresentative(true);

        when(certificationRepository.findByUserAndCertificationType(user, CertificationType.ENTERPRISE))
                .thenReturn(Optional.empty());
        when(certificationRepository.save(any(UserCertification.class)))
                .thenAnswer(invocation -> {
                    UserCertification cert = invocation.getArgument(0);
                    cert.setId(2L);
                    return cert;
                });

        UserCertification result = certificationService.submitEnterpriseAuth(user, request);

        assertNotNull(result);
        assertEquals(CertificationType.ENTERPRISE, result.getCertificationType());
        assertEquals(CertificationStatus.PENDING, result.getStatus());
        assertEquals("https://example.com/license.jpg", result.getBusinessLicenseUrl());
        assertEquals("李四", result.getLegalRepresentativeName());
        assertEquals(true, result.getIsLegalRepresentative());
        verify(certificationRepository).save(any(UserCertification.class));
    }

    @Test
    void submitEnterpriseAuth_requiresAuthorizationLetterWhenNotLegalRep() {
        EnterpriseAuthRequest request = new EnterpriseAuthRequest();
        request.setBusinessLicenseUrl("https://example.com/license.jpg");
        request.setLegalRepresentativeName("李四");
        request.setIsLegalRepresentative(false);
        // authorizationLetterUrl is null

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> certificationService.submitEnterpriseAuth(user, request));
        assertEquals("非法人代表需提供授权委托书", ex.getMessage());
        verify(certificationRepository, never()).save(any());
    }

    @Test
    void submitMasterAuth_createsNewCertification() {
        MasterAuthRequest request = new MasterAuthRequest();
        request.setCertificationUrl("https://example.com/cert.jpg");
        request.setRepresentativeWorkUrl("https://example.com/work.jpg");

        when(certificationRepository.findByUserAndCertificationType(user, CertificationType.MASTER))
                .thenReturn(Optional.empty());
        when(certificationRepository.save(any(UserCertification.class)))
                .thenAnswer(invocation -> {
                    UserCertification cert = invocation.getArgument(0);
                    cert.setId(3L);
                    return cert;
                });

        UserCertification result = certificationService.submitMasterAuth(user, request);

        assertNotNull(result);
        assertEquals(CertificationType.MASTER, result.getCertificationType());
        assertEquals(CertificationStatus.PENDING, result.getStatus());
        assertEquals("https://example.com/cert.jpg", result.getCertificationUrl());
        assertEquals("https://example.com/work.jpg", result.getRepresentativeWorkUrl());
        verify(certificationRepository).save(any(UserCertification.class));
    }

    @Test
    void submitMasterAuth_requiresAtLeastOneMaterial() {
        MasterAuthRequest request = new MasterAuthRequest();
        // Both certificationUrl and representativeWorkUrl are null

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> certificationService.submitMasterAuth(user, request));
        assertEquals("技艺认证至少需要提供证书照片或代表作品之一", ex.getMessage());
        verify(certificationRepository, never()).save(any());
    }

    @Test
    void approveCertification_updatesStatusAndAuditor() {
        UserCertification cert = new UserCertification(user, CertificationType.REAL_NAME);
        cert.setId(1L);
        cert.setStatus(CertificationStatus.PENDING);

        when(certificationRepository.findById(1L)).thenReturn(Optional.of(cert));
        when(userRepository.findById(2L)).thenReturn(Optional.of(auditor));
        when(certificationRepository.save(any(UserCertification.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        UserCertification result = certificationService.approveCertification(1L, 2L);

        assertEquals(CertificationStatus.APPROVED, result.getStatus());
        assertEquals(2L, result.getAuditorId());
        assertNotNull(result.getAuditTime());
        // Real-name cert approval should set realNameVerified = true
        assertEquals(true, result.getRealNameVerified());
        verify(certificationRepository).save(cert);
        // 验证用户的认证状态被同步为 APPROVED
        assertEquals(CertificationStatus.APPROVED, user.getCertificationStatus());
    }

    @Test
    void rejectCertification_updatesStatusAndReason() {
        UserCertification cert = new UserCertification(user, CertificationType.ENTERPRISE);
        cert.setId(1L);
        cert.setStatus(CertificationStatus.PENDING);

        when(certificationRepository.findById(1L)).thenReturn(Optional.of(cert));
        when(userRepository.findById(2L)).thenReturn(Optional.of(auditor));
        when(certificationRepository.save(any(UserCertification.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        UserCertification result = certificationService.rejectCertification(1L, 2L, "材料不清晰");

        assertEquals(CertificationStatus.REJECTED, result.getStatus());
        assertEquals(2L, result.getAuditorId());
        assertEquals("材料不清晰", result.getRejectReason());
        assertNotNull(result.getAuditTime());
        verify(certificationRepository).save(cert);
        // 验证用户的认证状态被同步为 REJECTED
        assertEquals(CertificationStatus.REJECTED, user.getCertificationStatus());
    }

    @Test
    void approveCertification_throwsWhenNotFound() {
        when(certificationRepository.findById(99L)).thenReturn(Optional.empty());

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> certificationService.approveCertification(99L, 2L));
        assertEquals("认证记录不存在", ex.getMessage());
        verify(certificationRepository, never()).save(any());
    }

    @Test
    void approveCertification_throwsWhenAlreadyProcessed() {
        UserCertification cert = new UserCertification(user, CertificationType.REAL_NAME);
        cert.setId(1L);
        cert.setStatus(CertificationStatus.APPROVED);

        when(certificationRepository.findById(1L)).thenReturn(Optional.of(cert));

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> certificationService.approveCertification(1L, 2L));
        assertEquals("该认证已处理，无法重复操作", ex.getMessage());
        verify(certificationRepository, never()).save(any());
    }
}
