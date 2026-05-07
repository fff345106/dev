package com.example.hello.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.example.hello.entity.User;
import com.example.hello.entity.UserCertification;
import com.example.hello.enums.CertificationStatus;
import com.example.hello.enums.CertificationType;
import com.example.hello.enums.UserRole;
import com.example.hello.repository.UserCertificationRepository;

@ExtendWith(MockitoExtension.class)
class UserPermissionServiceTest {

    @Mock
    private UserCertificationRepository certificationRepository;

    private UserPermissionService userPermissionService;

    @BeforeEach
    void setUp() {
        userPermissionService = new UserPermissionService(certificationRepository);
    }

    @Test
    void canSubmitPattern_guestCannotSubmit() {
        User user = new User("guest", "pass", UserRole.GUEST);
        assertFalse(userPermissionService.canSubmitPattern(user));
    }

    @Test
    void canSubmitPattern_superAdminCanSubmit() {
        User user = new User("admin", "pass", UserRole.SUPER_ADMIN);
        assertTrue(userPermissionService.canSubmitPattern(user));
    }

    @Test
    void canSubmitPattern_adminCanSubmit() {
        User user = new User("admin", "pass", UserRole.ADMIN);
        assertTrue(userPermissionService.canSubmitPattern(user));
    }

    @Test
    void canSubmitPattern_editorCanSubmit() {
        User user = new User("editor", "pass", UserRole.USER);
        assertTrue(userPermissionService.canSubmitPattern(user));
    }

    @Test
    void canSubmitPattern_regularUserNeedsRealNameAuth() {
        User user = new User("user", "pass", UserRole.REGULAR_USER);
        when(certificationRepository.findByUserAndCertificationType(user, CertificationType.REAL_NAME))
                .thenReturn(Optional.empty());
        assertFalse(userPermissionService.canSubmitPattern(user));
    }

    @Test
    void canSubmitPattern_regularUserWithRealNameAuthCanSubmit() {
        User user = new User("user", "pass", UserRole.REGULAR_USER);
        UserCertification cert = new UserCertification(user, CertificationType.REAL_NAME);
        cert.setStatus(CertificationStatus.APPROVED);
        when(certificationRepository.findByUserAndCertificationType(user, CertificationType.REAL_NAME))
                .thenReturn(Optional.of(cert));
        assertTrue(userPermissionService.canSubmitPattern(user));
    }

    @Test
    void canSubmitPattern_enterpriseUserNeedsBothCerts() {
        User user = new User("enterprise", "pass", UserRole.ENTERPRISE_USER);
        UserCertification realNameCert = new UserCertification(user, CertificationType.REAL_NAME);
        realNameCert.setStatus(CertificationStatus.APPROVED);
        when(certificationRepository.findByUserAndCertificationType(user, CertificationType.REAL_NAME))
                .thenReturn(Optional.of(realNameCert));
        when(certificationRepository.findByUserAndCertificationType(user, CertificationType.ENTERPRISE))
                .thenReturn(Optional.empty());
        assertFalse(userPermissionService.canSubmitPattern(user));
    }

    @Test
    void canSubmitPattern_enterpriseUserWithBothCertsCanSubmit() {
        User user = new User("enterprise", "pass", UserRole.ENTERPRISE_USER);
        UserCertification realNameCert = new UserCertification(user, CertificationType.REAL_NAME);
        realNameCert.setStatus(CertificationStatus.APPROVED);
        UserCertification enterpriseCert = new UserCertification(user, CertificationType.ENTERPRISE);
        enterpriseCert.setStatus(CertificationStatus.APPROVED);
        when(certificationRepository.findByUserAndCertificationType(user, CertificationType.REAL_NAME))
                .thenReturn(Optional.of(realNameCert));
        when(certificationRepository.findByUserAndCertificationType(user, CertificationType.ENTERPRISE))
                .thenReturn(Optional.of(enterpriseCert));
        assertTrue(userPermissionService.canSubmitPattern(user));
    }

    @Test
    void canSubmitPattern_masterArtisanNeedsBothCerts() {
        User user = new User("master", "pass", UserRole.MASTER_ARTISAN);
        UserCertification realNameCert = new UserCertification(user, CertificationType.REAL_NAME);
        realNameCert.setStatus(CertificationStatus.APPROVED);
        when(certificationRepository.findByUserAndCertificationType(user, CertificationType.REAL_NAME))
                .thenReturn(Optional.of(realNameCert));
        when(certificationRepository.findByUserAndCertificationType(user, CertificationType.MASTER))
                .thenReturn(Optional.empty());
        assertFalse(userPermissionService.canSubmitPattern(user));
    }

    @Test
    void canSubmitPattern_masterArtisanWithBothCertsCanSubmit() {
        User user = new User("master", "pass", UserRole.MASTER_ARTISAN);
        UserCertification realNameCert = new UserCertification(user, CertificationType.REAL_NAME);
        realNameCert.setStatus(CertificationStatus.APPROVED);
        UserCertification masterCert = new UserCertification(user, CertificationType.MASTER);
        masterCert.setStatus(CertificationStatus.APPROVED);
        when(certificationRepository.findByUserAndCertificationType(user, CertificationType.REAL_NAME))
                .thenReturn(Optional.of(realNameCert));
        when(certificationRepository.findByUserAndCertificationType(user, CertificationType.MASTER))
                .thenReturn(Optional.of(masterCert));
        assertTrue(userPermissionService.canSubmitPattern(user));
    }

    @Test
    void isRealNameVerified_returnsFalseWhenNoCert() {
        User user = new User("user", "pass", UserRole.REGULAR_USER);
        when(certificationRepository.findByUserAndCertificationType(user, CertificationType.REAL_NAME))
                .thenReturn(Optional.empty());
        assertFalse(userPermissionService.isRealNameVerified(user));
    }

    @Test
    void isRealNameVerified_returnsFalseWhenPending() {
        User user = new User("user", "pass", UserRole.REGULAR_USER);
        UserCertification cert = new UserCertification(user, CertificationType.REAL_NAME);
        cert.setStatus(CertificationStatus.PENDING);
        when(certificationRepository.findByUserAndCertificationType(user, CertificationType.REAL_NAME))
                .thenReturn(Optional.of(cert));
        assertFalse(userPermissionService.isRealNameVerified(user));
    }

    @Test
    void isRealNameVerified_returnsTrueWhenApproved() {
        User user = new User("user", "pass", UserRole.REGULAR_USER);
        UserCertification cert = new UserCertification(user, CertificationType.REAL_NAME);
        cert.setStatus(CertificationStatus.APPROVED);
        when(certificationRepository.findByUserAndCertificationType(user, CertificationType.REAL_NAME))
                .thenReturn(Optional.of(cert));
        assertTrue(userPermissionService.isRealNameVerified(user));
    }
}
